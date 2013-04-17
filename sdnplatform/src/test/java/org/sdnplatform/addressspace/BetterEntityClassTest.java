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

package org.sdnplatform.addressspace;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sdnplatform.addressspace.BetterEntityClass;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.devicegroup.AbstractDeviceGroupContractTEST;
import org.sdnplatform.devicegroup.IDeviceGroup;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;



public class BetterEntityClassTest extends AbstractDeviceGroupContractTEST {
    @Override
    protected IDeviceGroup allocateDeviceGroup() {
        return new BetterEntityClass("base", null);
    }
    @Test
    public void testBetterEntityClass() {
        BetterEntityClass bec = new BetterEntityClass("AddressSpace42", (short)42);
        assertEquals(Short.valueOf((short)42), bec.vlan);
        assertEquals("AddressSpace42", bec.getName());
        assertEquals(1, bec.getKeyFields().size());
        assertEquals(true, bec.getKeyFields().contains(DeviceField.MAC));
        assertEquals("BetterEntityClass[AddressSpace42, vlan=42]", 
                     bec.toString());
        
        bec = (BetterEntityClass) allocateDeviceGroup();
        assertEquals(null, bec.vlan);
        assertEquals("base", bec.getName());
        assertEquals(1, bec.getKeyFields().size());
        assertEquals(true, bec.getKeyFields().contains(DeviceField.MAC));
        assertEquals("BetterEntityClass[base, vlan=null]", 
                     bec.toString());
    }
    
    @Test
    public void testBetterEntityClassDefault() {
        BetterEntityClass bec = new BetterEntityClass(
                IAddressSpaceManagerService.DEFAULT_ADDRESS_SPACE_NAME,
                null);
        assertNull(bec.vlan);
        assertEquals(IAddressSpaceManagerService.DEFAULT_ADDRESS_SPACE_NAME,
                     bec.getName());
        assertEquals(2, bec.getKeyFields().size());
        assertEquals(true, bec.getKeyFields().contains(DeviceField.MAC));
        assertEquals(true, bec.getKeyFields().contains(DeviceField.VLAN));
        assertEquals("BetterEntityClass[default]", bec.toString());
        
        bec = new BetterEntityClass();
        assertEquals(IAddressSpaceManagerService.DEFAULT_ADDRESS_SPACE_NAME,
                     bec.getName());
        assertNull(bec.vlan);
        assertEquals(2, bec.getKeyFields().size());
        assertEquals(true, bec.getKeyFields().contains(DeviceField.MAC));
        assertEquals(true, bec.getKeyFields().contains(DeviceField.VLAN));
        assertEquals("BetterEntityClass[default]", bec.toString());
        
    }
}
