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

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.OFSwitchBase;


public class BetterOFSwitchImpl extends OFSwitchBase {
    
    public BetterOFSwitchImpl() {
        super();
    }

    @Override
    public void setSwitchProperties(OFDescriptionStatistics description) {
        // Move it to a different class of Switch properties database
        // only change features that differ from the "ideal switch"

        log.trace("Switch {} identified as Manufacturer - {}", this, 
                  description.getManufacturerDescription());

        if (description.getManufacturerDescription().startsWith("HP")) {
            // HP Switch
            log.trace("Switch {} identified as HP Switch", this);
            setAttribute(IOFSwitch.PROP_FASTWILDCARDS, (Integer)
                    OFMatch.OFPFW_IN_PORT |
                    OFMatch.OFPFW_NW_TOS | OFMatch.OFPFW_NW_PROTO |
                    OFMatch.OFPFW_NW_SRC_ALL | OFMatch.OFPFW_NW_DST_ALL |
                    OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST);
            setAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH, new Boolean(true));
        }
        else if (description.getManufacturerDescription().startsWith("FORCE10")) {
            // Force 10 Switch
            log.trace("Switch {} identified as Force10 Switch - {}", this);
            removeAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE);
        }
        else if (description.getManufacturerDescription().startsWith("Extreme")) {
            // Extreme
            log.trace("Switch {} identified as Extreme Switch - {}", this);
            // For now, no delta
        }
        else if (description.getManufacturerDescription().startsWith("Indigo")) {
            // Indigo (Pronto or Netgear running Indigo)
            log.trace("Switch {} identified as Indigo Switch - {}", this);
            // For now, no delta
        }
        else if (description.getManufacturerDescription().startsWith("Nicira")) {
            // openvswitch
            log.trace("Switch {} identified as openvswitch Switch - {}", this);
            // For now, no delta
        } 
        else if (description.getManufacturerDescription().startsWith("Arista")) {
            // Arista switch supports programmable netmask table to use
            // values of 33-62 in the OFMatch for IP address masks
            log.trace("Switch {} identified as Arista Switch", this);
            setAttribute(IOFSwitch.PROP_SUPPORTS_NETMASK_TBL, new Boolean(true));
        }
        
        setAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA, 
                description.getDatapathDescription());
        
    }

    @Override
    public OFPortType getPortType(short port_num) {
        return OFPortType.NORMAL;
    }

    @Override
    public boolean isFastPort(short port_num) {
        return false;
    }

    @Override
    public List<Short> getUplinkPorts() {
        return null;
    }
    
    @Override
    public void setFeaturesReply(OFFeaturesReply featuresReply) {
        synchronized(portLock) {
            if (stringId == null) {
                /* ports are updated via port status message, so we
                 * only fill in ports on initial connection.
                 */
                for (OFPhysicalPort port : featuresReply.getPorts()) {
                    setPort(port);
                }
            }

            for (OFPhysicalPort port : featuresReply.getPorts()) {
                if (port.getConfig() == 0x80000000) {
                    setPort(port);
                }
            }
            
            this.datapathId = featuresReply.getDatapathId();
            this.capabilities = featuresReply.getCapabilities();
            this.buffers = featuresReply.getBuffers();
            this.actions = featuresReply.getActions();
            this.tables = featuresReply.getTables();
            this.stringId = HexString.toHexString(this.datapathId);
        }
    }

}
