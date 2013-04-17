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

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.OFSwitchBase;



/**
 * OFDescriptionStatistics
 *   Vendor (Manufacturer Desc.): Big Switch Networks
 *   Make (Hardware Desc.)      : Xenon
 *   Model (Datapath Desc.)     : None
 *   Software                   : Indigo 2
 *   Serial                     : None
 *
 *   @author rlane
 */
public class BetterOFSwitchXenon extends OFSwitchBase {

    @JsonIgnore
    @Override
    public void setSwitchProperties(OFDescriptionStatistics description) {
        setAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA,
                description.getDatapathDescription());
        setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        setAttribute(IBetterOFSwitch.SUPPORTS_BSN_SET_TUNNEL_DST_ACTION, true);
        removeAttribute(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP);
        setAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true);
    }

    @Override
    public OFPortType getPortType(short port_num) {
        OFPhysicalPort ofp = getPort(port_num);
        if (ofp != null) {

            // Tunnel manager checks for configured tunnel port with names
            // 'tun-bsn' and 'tun-loopback'.
            // In addition, any interface with "bond" prefix will be
            // viewed as uplink port.
            if (ofp.getName().startsWith("tun-bsn")) {
                return OFPortType.TUNNEL;
            }  else if (ofp.getName().startsWith("tun-loopback")) {
                return OFPortType.TUNNEL_LOOPBACK;
            } else if (ofp.getName().startsWith("bond")) {
                return OFPortType.UPLINK;
            }

            // if the AUTONEG feature is turned ON, then it is an uplink port.
            int currentFeatures = ofp.getCurrentFeatures();
            int autoNegMask = OFPortFeatures.OFPPF_AUTONEG.getValue();
            if ((currentFeatures & autoNegMask) != 0) {
                return OFPortType.UPLINK;
            }
        }
        return OFPortType.NORMAL;
    }

    @Override
    public boolean isFastPort(short port_num) {
        return getPortType(port_num) == OFPortType.NORMAL;
    }

    @Override
    public List<Short> getUplinkPorts() {
        // Extract uplink ports?
        return null;
    }
}
