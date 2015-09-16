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
package org.apache.usergrid.testapp;

import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.xml.ws.spi.http.HttpContext;


public class AbstractResource {

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpContext hc;

    @Context
    protected ResourceContext resourceContext;
    
    private AbstractResource parent;

    public <T extends AbstractResource> T getSubResource(Class<T> t) {
        T subResource = resourceContext.getResource(t);
        subResource.setParent(this);
        return subResource;
    }

    public void setParent(AbstractResource parent) {
        this.parent = parent;
    }

    public AbstractResource getParent() {
        return parent;
    }
}
