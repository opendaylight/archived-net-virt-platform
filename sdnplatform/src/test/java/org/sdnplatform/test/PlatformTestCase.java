/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University 
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

package org.sdnplatform.test;

import junit.framework.TestCase;

import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.packet.Ethernet;

/**
 * This class gets a handle on the application context which is used to
 * retrieve Spring beans from during tests
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class PlatformTestCase extends TestCase {
    protected MockControllerProvider mockControllerProvider;

    public MockControllerProvider getMockControllerProvider() {
        return mockControllerProvider;
    }

    public void setMockControllerProvider(MockControllerProvider mockControllerProvider) {
        this.mockControllerProvider = mockControllerProvider;
    }

    public ListenerContext parseAndAnnotate(OFMessage m,
                                              IDevice srcDevice,
                                              IDevice dstDevice) {
        ListenerContext bc = new ListenerContext();
        return parseAndAnnotate(bc, m, srcDevice, dstDevice);
    }

    public ListenerContext parseAndAnnotate(OFMessage m) {
        return parseAndAnnotate(m, null, null);
    }

    public ListenerContext parseAndAnnotate(ListenerContext bc,
                                              OFMessage m,
                                              IDevice srcDevice,
                                              IDevice dstDevice) {
        if (OFType.PACKET_IN.equals(m.getType())) {
            OFPacketIn pi = (OFPacketIn)m;
            Ethernet eth = new Ethernet();
            eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
            IControllerService.bcStore.put(bc, 
                    IControllerService.CONTEXT_PI_PAYLOAD, 
                    eth);
        }
        if (srcDevice != null) {
            IDeviceService.fcStore.put(bc, 
                    IDeviceService.CONTEXT_SRC_DEVICE, 
                    srcDevice);
        }
        if (dstDevice != null) {
            IDeviceService.fcStore.put(bc, 
                    IDeviceService.CONTEXT_DST_DEVICE, 
                    dstDevice);
        }
        return bc;
    }
    
    @Override
    public void setUp() throws Exception {
        mockControllerProvider = new MockControllerProvider();
    }
    
    @Test
    public void testSanity() throws Exception {
    	assertTrue(true);
    }
}
