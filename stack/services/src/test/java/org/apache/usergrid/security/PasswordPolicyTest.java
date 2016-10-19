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

package org.apache.usergrid.security;

import org.apache.usergrid.ServiceITSetup;
import org.apache.usergrid.ServiceITSetupImpl;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;


public class PasswordPolicyTest {

    @ClassRule
    public static ServiceITSetup setup = new ServiceITSetupImpl();


    @Test
    public void testBasicOperation() {

        PasswordPolicyImpl passwordPolicy =
            (PasswordPolicyImpl)setup.getInjector().getInstance( PasswordPolicy.class );

        Assert.assertEquals( 4, passwordPolicy.policyCheck( "secret", 12, 1, 1, 1 ).size() );
        Assert.assertEquals( 3, passwordPolicy.policyCheck( "Secret", 12, 1, 1, 1 ).size() );
        Assert.assertEquals( 2, passwordPolicy.policyCheck( "Secr3t", 12, 1, 1, 1 ).size() );
        Assert.assertEquals( 1, passwordPolicy.policyCheck( "Secr3t!", 12, 1, 1, 1 ).size() );
        Assert.assertEquals( 0, passwordPolicy.policyCheck( "Secr3t!longer", 12, 1, 1, 1 ).size() );

    }

}
