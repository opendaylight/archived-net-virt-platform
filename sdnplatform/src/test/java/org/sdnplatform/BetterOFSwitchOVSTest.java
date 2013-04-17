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

package org.sdnplatform;

import static org.junit.Assert.*;


import org.junit.Test;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.sdnplatform.BetterOFSwitchOVS;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.core.IOFSwitch;

public class BetterOFSwitchOVSTest {
    
    @Test
    public void testSetSwitchPropoerties() {
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        IOFSwitch sw = new BetterOFSwitchOVS();
        
        desc.setDatapathDescription("My Data Path");
        desc.setSerialNumber("");
        desc.setManufacturerDescription("Nicira Networks, Inc");
        desc.setHardwareDescription("Open vSwitch");
        
        sw = new BetterOFSwitchOVS();
        desc.setSoftwareDescription("1.4.0");
        sw.setSwitchProperties(desc);
        assertNull(sw.getAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT));
        
        assertEquals(desc.getDatapathDescription(), 
                     sw.getAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA));
        assertEquals(true,
                     sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(true,
                     sw.getAttribute(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP));
        
        sw = new BetterOFSwitchOVS();
        desc.setSoftwareDescription("1.4.3");
        sw.setSwitchProperties(desc);
        assertNull(sw.getAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT));
        
        sw = new BetterOFSwitchOVS();
        desc.setSoftwareDescription("1.6.0");
        sw.setSwitchProperties(desc);
        assertEquals(true, 
                     sw.getAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT));
        
        sw = new BetterOFSwitchOVS();
        desc.setSoftwareDescription("1.7.0");
        sw.setSwitchProperties(desc);
        assertEquals(true, 
                     sw.getAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT));
        
        // invalid version 
        // don't allocate new switch object.
        desc.setSoftwareDescription("xxxxx");
        sw.setSwitchProperties(desc);
        assertNull(sw.getAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT));
        
    }

    @Test
    public void testFastPort() {
        IOFSwitch sw = new BetterOFSwitchOVS();

        // Any port with "bond" prefix is not a fastport.
        // The current features is explicitly set to 0 to ensure that
        // it will match the fastport settings.
        short port = 1;
        OFPhysicalPort phyPort = new OFPhysicalPort();
        phyPort.setName("bond0");
        phyPort.setPortNumber(port);
        phyPort.setCurrentFeatures(0);
        sw.setPort(phyPort);
        assertFalse(sw.isFastPort(port));
    }

}
