/*
 * Copyright (c) 2013 Big Switch Networks, Inc.
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.openflow.protocol.action;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openflow.protocol.factory.OFVendorActionRegistry;

public class OFVendorActionRegistryTest {

    @Test
    public void test() {
        MockVendorActionFactory factory = new MockVendorActionFactory();
        OFVendorActionRegistry.getInstance().register(MockVendorAction.VENDOR_ID, factory);
        assertEquals(factory, OFVendorActionRegistry.getInstance().get(MockVendorAction.VENDOR_ID));
    }

}
