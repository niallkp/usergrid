/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.persistence.query.ir.result;


import java.nio.ByteBuffer;

import static org.apache.usergrid.persistence.cassandra.Serializers.*;

/**
 * Parser for reading and writing secondary index composites
 *
 * @author tnine
 */
public class UUIDIndexSliceParser implements SliceParser {


    private final CursorGenerator<UUIDColumn> uuidCursorGenerator;


    public UUIDIndexSliceParser( final UUIDCursorGenerator<UUIDColumn> uuidCursorGenerator ) {
        this.uuidCursorGenerator = uuidCursorGenerator;
    }


    @Override
    public ScanColumn parse( final ByteBuffer columnNameBytes, final boolean isReversed ) {
        final int compare = isReversed? -1: 1;
        return new UUIDColumn( ue.fromByteBuffer( columnNameBytes.duplicate() ),  compare, uuidCursorGenerator);
    }
}
