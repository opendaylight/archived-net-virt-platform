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

package org.sdnplatform.netvirt.core;

import static org.junit.Assert.assertEquals;
import static org.easymock.EasyMock.*;


import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.devicegroup.AbstractDeviceGroupContractTEST;
import org.sdnplatform.devicegroup.IDeviceGroup;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNS.ARPMode;
import org.sdnplatform.netvirt.core.VNS.BroadcastMode;
import org.sdnplatform.netvirt.core.VNS.DHCPMode;


public class NetVirtTest extends AbstractDeviceGroupContractTEST {
    protected VNS netVirt;
    
    // TODO: test getters/setters
    
    @Before
    public void setUp() {
        netVirt = new VNS("netVirtFoobar");
    }

    @Override
    protected IDeviceGroup allocateDeviceGroup() {
        return new VNS("netVirt");
    }
    
    @Test 
    public void testMarked() {
        netVirt.setMarked(false);
        assertEquals(false, netVirt.isMarked());
        netVirt.setMarked(true);
        assertEquals(true, netVirt.isMarked());
    }
    
    @Test
    public void testDefaultValues() {
        assertEquals(DHCPMode.FLOOD_IF_UNKNOWN, netVirt.getDhcpManagerMode());
        assertEquals(ARPMode.FLOOD_IF_UNKNOWN, netVirt.getArpManagerMode());
        assertEquals(BroadcastMode.FORWARD_TO_KNOWN, netVirt.getBroadcastMode());
        assertEquals(0, netVirt.getDhcpIp());
        assertEquals(0, netVirt.getKnownDevices().size());
    }
    
    @Test
    public void knownDevices() {
        IDevice d1 = createMock(IDevice.class);
        IDevice d2 = createMock(IDevice.class);
        IDevice d3 = createMock(IDevice.class);
        IDevice d1b = createMock(IDevice.class);
        expect(d1.getDeviceKey()).andReturn(1L).atLeastOnce();
        expect(d2.getDeviceKey()).andReturn(2L).atLeastOnce();
        expect(d3.getDeviceKey()).andReturn(3L).atLeastOnce();
        expect(d1b.getDeviceKey()).andReturn(1L).atLeastOnce();
        
        replay(d1, d2, d3, d1b);
        
        netVirt.addDevice(d1);
        assertEquals(1, netVirt.getKnownDevices().size());
        assertEquals(true, netVirt.getKnownDevices().contains(d1.getDeviceKey()));
        assertEquals(false, netVirt.getKnownDevices().contains(d2.getDeviceKey()));
        
        netVirt.addDevice(d1);  // no-op
        assertEquals(1, netVirt.getKnownDevices().size());
        netVirt.addDevice(d1b);  // no-op
        assertEquals(1, netVirt.getKnownDevices().size());
        
        netVirt.addDevice(d2);
        assertEquals(2, netVirt.getKnownDevices().size());
        assertEquals(true, netVirt.getKnownDevices().contains(d1.getDeviceKey()));
        assertEquals(true, netVirt.getKnownDevices().contains(d2.getDeviceKey()));
        
        netVirt.addDevice(d3);
        assertEquals(3, netVirt.getKnownDevices().size());
        assertEquals(true, netVirt.getKnownDevices().contains(d1.getDeviceKey()));
        assertEquals(true, netVirt.getKnownDevices().contains(d2.getDeviceKey()));
        assertEquals(true, netVirt.getKnownDevices().contains(d3.getDeviceKey()));
        
        netVirt.removeDevice(d1b.getDeviceKey());
        assertEquals(2, netVirt.getKnownDevices().size());
        assertEquals(false, netVirt.getKnownDevices().contains(d1.getDeviceKey()));
        assertEquals(true, netVirt.getKnownDevices().contains(d2.getDeviceKey()));
        assertEquals(true, netVirt.getKnownDevices().contains(d3.getDeviceKey()));
        
        netVirt.removeAllDevices();
        assertEquals(0, netVirt.getKnownDevices().size());
        verify(d1, d2, d3, d1b);
    }
}
