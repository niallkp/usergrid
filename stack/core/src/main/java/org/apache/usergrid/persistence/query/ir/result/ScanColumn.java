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
import java.util.UUID;

import org.apache.usergrid.persistence.cassandra.CursorCache;
import org.apache.usergrid.persistence.cassandra.index.DynamicCompositeComparator;


/** An interface that represents a column */
public interface ScanColumn extends Comparable<ScanColumn> {

    /** Get the uuid from the column */
    UUID getUUID();

    /** Get the cursor value of this column */
    ByteBuffer getCursorValue();

    /**
     * Append the child column used in tree iterator to this column, along with the comparator used to compare them
     *
     * for instance, a term search of A = 1 AND B = 2 would generate a ScanColumn of A-- child -> B
     * @param childColumn
     */
    void setChild( final ScanColumn childColumn );


    /**
     * Use the generator to add this value to the cursor cache
     * @param cache
     */
    void addToCursor( final CursorCache cache );


    /**
     * Returns the childl column if present, can return null
     * @return
     */
    ScanColumn getChild();

}
