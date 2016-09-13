/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.usergrid.persistence.qakka.serialization.queuemessages.impl;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Clause;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.usergrid.persistence.actorsystem.ActorSystemFig;
import org.apache.usergrid.persistence.core.astyanax.MultiTenantColumnFamilyDefinition;
import org.apache.usergrid.persistence.core.datastax.TableDefinition;
import org.apache.usergrid.persistence.core.datastax.impl.TableDefinitionStringImpl;
import org.apache.usergrid.persistence.qakka.core.CassandraClient;
import org.apache.usergrid.persistence.qakka.core.QakkaUtils;
import org.apache.usergrid.persistence.qakka.serialization.queuemessages.DatabaseQueueMessage;
import org.apache.usergrid.persistence.qakka.serialization.queuemessages.DatabaseQueueMessageBody;
import org.apache.usergrid.persistence.qakka.serialization.queuemessages.QueueMessageSerialization;
import org.apache.usergrid.persistence.qakka.serialization.sharding.Shard;
import org.apache.usergrid.persistence.qakka.serialization.sharding.ShardCounterSerialization;
import org.apache.usergrid.persistence.qakka.serialization.sharding.ShardStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;


public class QueueMessageSerializationImpl implements QueueMessageSerialization {

    private static final Logger logger = LoggerFactory.getLogger( QueueMessageSerializationImpl.class );

    private final CassandraClient cassandraClient;

    private final ActorSystemFig            actorSystemFig;
    private final ShardStrategy             shardStrategy;
    private final ShardCounterSerialization shardCounterSerialization;

    public final static String COLUMN_QUEUE_NAME       = "queue_name";
    public final static String COLUMN_REGION           = "region";
    public final static String COLUMN_SHARD_ID         = "shard_id";
    public final static String COLUMN_QUEUED_AT        = "queued_at";
    public final static String COLUMN_INFLIGHT_AT      = "inflight_at";
    public final static String COLUMN_QUEUE_MESSAGE_ID = "queue_message_id";
    public final static String COLUMN_MESSAGE_ID       = "message_id";
    public final static String COLUMN_CONTENT_TYPE     = "content_type";
    public final static String COLUMN_MESSAGE_DATA     = "data";

    public final static String TABLE_MESSAGES_AVAILABLE = "messages_available";

    public final static String TABLE_MESSAGES_INFLIGHT = "messages_inflight";

    public final static String TABLE_MESSAGE_DATA = "message_data";

    static final String MESSAGES_AVAILABLE =
        "CREATE TABLE IF NOT EXISTS messages_available ( " +
                "queue_name       text, " +
                "region           text, " +
                "shard_id         bigint, " +
                "queue_message_id timeuuid, " +
                "message_id       uuid, " +
                "queued_at        bigint, " +
                "inflight_at      bigint, " +
                "PRIMARY KEY ((queue_name, region, shard_id), queue_message_id ) " +
                ") WITH CLUSTERING ORDER BY (queue_message_id ASC); ";

    static final String MESSAGES_INFLIGHT =
        "CREATE TABLE IF NOT EXISTS messages_inflight ( " +
                "queue_name       text, " +
                "region           text, " +
                "shard_id         bigint, " +
                "queue_message_id timeuuid, " +
                "message_id       uuid, " +
                "queued_at        bigint, " +
                "inflight_at      bigint, " +
                "PRIMARY KEY ((queue_name, region, shard_id), queue_message_id ) " +
                ") WITH CLUSTERING ORDER BY (queue_message_id ASC); ";

    static final String MESSAGE_DATA =
        "CREATE TABLE IF NOT EXISTS message_data ( " +
                "message_id uuid, " +
                "data blob, " +
                "content_type text, " +
                "PRIMARY KEY ((message_id)) " +
                "); ";

    @Inject
    public QueueMessageSerializationImpl(
            ActorSystemFig            actorSystemFig,
            ShardStrategy             shardStrategy,
            ShardCounterSerialization shardCounterSerialization,
            CassandraClient           cassandraClient
        ) {
        this.actorSystemFig            = actorSystemFig;
        this.shardStrategy             = shardStrategy;
        this.shardCounterSerialization = shardCounterSerialization;
        this.cassandraClient = cassandraClient;
    }


    @Override
    public UUID writeMessage(final DatabaseQueueMessage message) {

        final UUID queueMessageId =  message.getQueueMessageId() == null ?
                QakkaUtils.getTimeUuid() : message.getQueueMessageId();

        long queuedAt = message.getQueuedAt() == null ?
                System.currentTimeMillis() : message.getQueuedAt();

        long inflightAt = message.getInflightAt() == null ?
                message.getQueuedAt() : message.getInflightAt();

        Shard.Type shardType = DatabaseQueueMessage.Type.DEFAULT.equals( message.getType() ) ?
                Shard.Type.DEFAULT : Shard.Type.INFLIGHT;

        if ( message.getShardId() == null ) {
            Shard shard = shardStrategy.selectShard(
                    message.getQueueName(), actorSystemFig.getRegionLocal(), shardType, queueMessageId );
            message.setShardId( shard.getShardId() );
        }

        Statement insert = QueryBuilder.insertInto(getTableName(message.getType()))
                .value( COLUMN_QUEUE_NAME,       message.getQueueName())
                .value( COLUMN_REGION,           message.getRegion())
                .value( COLUMN_SHARD_ID,         message.getShardId())
                .value( COLUMN_MESSAGE_ID,       message.getMessageId())
                .value( COLUMN_QUEUE_MESSAGE_ID, queueMessageId)
                .value( COLUMN_INFLIGHT_AT,      inflightAt )
                .value( COLUMN_QUEUED_AT,        queuedAt);

        cassandraClient.getSession().execute(insert);

        shardCounterSerialization.incrementCounter( message.getQueueName(), shardType, message.getShardId(), 1 );

        return queueMessageId;
    }


    @Override
    public DatabaseQueueMessage loadMessage(
            final String queueName,
            final String region,
            final Long shardIdOrNull,
            final DatabaseQueueMessage.Type type,
            final UUID queueMessageId ) {

        if ( queueMessageId == null ) {
            return null;
        }

        final long shardId;
        if ( shardIdOrNull == null ) {
            Shard.Type shardType = DatabaseQueueMessage.Type.DEFAULT.equals( type ) ?
                    Shard.Type.DEFAULT : Shard.Type.INFLIGHT;
            Shard shard = shardStrategy.selectShard(
                    queueName, actorSystemFig.getRegionLocal(), shardType, queueMessageId );
            shardId = shard.getShardId();
        } else {
            shardId = shardIdOrNull;
        }

        Clause queueNameClause = QueryBuilder.eq(      COLUMN_QUEUE_NAME, queueName );
        Clause regionClause = QueryBuilder.eq(         COLUMN_REGION, region );
        Clause shardIdClause = QueryBuilder.eq(        COLUMN_SHARD_ID, shardId );
        Clause queueMessageIdClause = QueryBuilder.eq( COLUMN_QUEUE_MESSAGE_ID, queueMessageId);

        Statement select = QueryBuilder.select().from(getTableName( type ))
                .where(queueNameClause)
                .and(regionClause)
                .and(shardIdClause)
                .and(queueMessageIdClause);

        Row row = cassandraClient.getSession().execute(select).one();

        if (row == null) {
            return null;
        }

        return new DatabaseQueueMessage(
            row.getUUID(   COLUMN_MESSAGE_ID),
            type,
            row.getString( COLUMN_QUEUE_NAME),
            row.getString( COLUMN_REGION),
            row.getLong(   COLUMN_SHARD_ID),
            row.getLong(   COLUMN_QUEUED_AT),
            row.getLong(   COLUMN_INFLIGHT_AT),
            row.getUUID(   COLUMN_QUEUE_MESSAGE_ID)
        );
    }


    @Override
    public void deleteMessage(
            final String queueName,
            final String region,
            final Long shardIdOrNull,
            final DatabaseQueueMessage.Type type,
            final UUID queueMessageId ) {

        final long shardId;
        if ( shardIdOrNull == null ) {
            Shard.Type shardType = DatabaseQueueMessage.Type.DEFAULT.equals( type ) ?
                    Shard.Type.DEFAULT : Shard.Type.INFLIGHT;
            Shard shard = shardStrategy.selectShard(
                    queueName, actorSystemFig.getRegionLocal(), shardType, queueMessageId );
            shardId = shard.getShardId();
        } else {
            shardId = shardIdOrNull;
        }

        Clause queueNameClause = QueryBuilder.eq(      COLUMN_QUEUE_NAME, queueName );
        Clause regionClause = QueryBuilder.eq(         COLUMN_REGION, region );
        Clause shardIdClause = QueryBuilder.eq(        COLUMN_SHARD_ID, shardId );
        Clause queueMessageIdClause = QueryBuilder.eq( COLUMN_QUEUE_MESSAGE_ID, queueMessageId);

        Statement delete = QueryBuilder.delete().from(getTableName( type ))
                .where(queueNameClause)
                .and(regionClause)
                .and(shardIdClause)
                .and(queueMessageIdClause);

        ResultSet resultSet = cassandraClient.getSession().execute( delete );

        String s = "s";
    }


    @Override
    public DatabaseQueueMessageBody loadMessageData(final UUID messageId ){

        Clause messageIdClause = QueryBuilder.eq( COLUMN_MESSAGE_ID, messageId );

        Statement select = QueryBuilder.select().from( TABLE_MESSAGE_DATA).where(messageIdClause);

        Row row = cassandraClient.getSession().execute(select).one();
        if ( row == null ) {
            return null;
        }

        return new DatabaseQueueMessageBody(
                row.getBytes( COLUMN_MESSAGE_DATA),
                row.getString( COLUMN_CONTENT_TYPE));
    }


    @Override
    public void writeMessageData( final UUID messageId, final DatabaseQueueMessageBody messageBody ) {
        Preconditions.checkArgument(QakkaUtils.isTimeUuid(messageId), "MessageId is not a type 1 UUID");

        Statement insert = QueryBuilder.insertInto(TABLE_MESSAGE_DATA)
                .value( COLUMN_MESSAGE_ID, messageId)
                .value( COLUMN_MESSAGE_DATA, messageBody.getBlob())
                .value( COLUMN_CONTENT_TYPE, messageBody.getContentType());

        cassandraClient.getSession().execute(insert);
    }


    @Override
    public void deleteMessageData( final UUID messageId ) {

        Clause messageIdClause = QueryBuilder.eq(COLUMN_MESSAGE_ID, messageId);

        Statement delete = QueryBuilder.delete().from(TABLE_MESSAGE_DATA)
                .where(messageIdClause);

        cassandraClient.getSession().execute(delete);
    }


    public static String getTableName(DatabaseQueueMessage.Type messageType){

        String table;
        if( messageType.equals(DatabaseQueueMessage.Type.DEFAULT)) {
            table = TABLE_MESSAGES_AVAILABLE;
        }else if (messageType.equals(DatabaseQueueMessage.Type.INFLIGHT)) {
            table = TABLE_MESSAGES_INFLIGHT;
        }else{
            throw new IllegalArgumentException("Unknown DatabaseQueueMessage Type");
        }

        return table;
    }

    @Override
    public Collection<MultiTenantColumnFamilyDefinition> getColumnFamilies() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Collection<TableDefinition> getTables() {
        return Lists.newArrayList(
                new TableDefinitionStringImpl( TABLE_MESSAGES_AVAILABLE, MESSAGES_AVAILABLE ),
                new TableDefinitionStringImpl( TABLE_MESSAGES_INFLIGHT, MESSAGES_INFLIGHT ),
                new TableDefinitionStringImpl( TABLE_MESSAGE_DATA, MESSAGE_DATA )
        );
    }

}
