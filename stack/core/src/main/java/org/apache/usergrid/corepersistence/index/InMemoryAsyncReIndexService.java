/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.usergrid.corepersistence.index;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.usergrid.persistence.collection.EntityCollectionManagerFactory;
import org.apache.usergrid.persistence.collection.serialization.impl.migration.EntityIdScope;
import org.apache.usergrid.persistence.core.metrics.MetricsFactory;
import org.apache.usergrid.persistence.core.rx.RxTaskScheduler;
import org.apache.usergrid.persistence.core.scope.ApplicationScope;
import org.apache.usergrid.persistence.index.impl.IndexOperationMessage;
import org.apache.usergrid.persistence.model.entity.Entity;
import org.apache.usergrid.persistence.model.entity.Id;

import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import rx.Observable;


@Singleton
public class InMemoryAsyncReIndexService implements AsyncReIndexService {

    private static final Logger log = LoggerFactory.getLogger( InMemoryAsyncReIndexService.class );
    private final IndexService indexService;
    private final RxTaskScheduler rxTaskScheduler;
    private final EntityCollectionManagerFactory entityCollectionManagerFactory;
    private final Timer timer;


    @Inject
    public InMemoryAsyncReIndexService( final IndexService indexService, final RxTaskScheduler rxTaskScheduler,
                                        final EntityCollectionManagerFactory entityCollectionManagerFactory, final
                                        MetricsFactory metricsFactory ) {
        this.indexService = indexService;
        this.rxTaskScheduler = rxTaskScheduler;
        this.entityCollectionManagerFactory = entityCollectionManagerFactory;

        timer = metricsFactory.getTimer( InMemoryAsyncReIndexService.class, "IndexTimer" );
    }


    @Override
    public void queueEntityIndexUpdate( final ApplicationScope applicationScope, final Entity entity ) {

        //process the entity immediately
        //only process the same version, otherwise ignore


        log.debug( "Indexing entity {} in app scope {} ", entity, applicationScope );

        final Observable<IndexOperationMessage> edgeObservable = indexService.indexEntity( applicationScope, entity );



        edgeObservable.subscribeOn( rxTaskScheduler.getAsyncIOScheduler() ).subscribe();

         //now start it
//        final Timer.Context time = timer.time();
//
//        edgeObservable.connect();
//
//        time.stop();


    }


    @Override
    public void index( final EntityIdScope entityIdScope ) {

        final ApplicationScope applicationScope = entityIdScope.getApplicationScope();

        final Id entityId = entityIdScope.getId();

        final Entity entity =
            entityCollectionManagerFactory.createCollectionManager( applicationScope ).load( entityId ).toBlocking()
                                          .lastOrDefault( null );


        if ( entity == null ) {
            log.warn( "Could not find entity with id {} in app scope {} ", entityId, applicationScope );
        }

        indexService.indexEntity( applicationScope, entity );
    }
}
