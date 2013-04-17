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
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.sdnplatform.BetterOFSwitchXenon;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.core.IOFSwitch;

public class BetterOFSwitchXenonTest {

    @Test
    public void testSetSwitchProperties() {
        OFDescriptionStatistics desc = new OFDescriptionStatistics();

        desc.setDatapathDescription("My Data Path");
        desc.setSerialNumber("");
        desc.setManufacturerDescription("Big Switch Networks");
        desc.setHardwareDescription("Xenon");
        desc.setSoftwareDescription("Indigo 2");

        IOFSwitch sw = new BetterOFSwitchXenon();
        sw.setSwitchProperties(desc);
        assertEquals(desc.getDatapathDescription(),
                     sw.getAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA));
        assertEquals(true,
                     sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertNull(sw.getAttribute(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP));
        assertEquals(true,
                     sw.getAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT));
    }

}
