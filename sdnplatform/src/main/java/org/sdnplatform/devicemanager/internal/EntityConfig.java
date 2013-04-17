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

/**
 * 
 */
package org.sdnplatform.devicemanager.internal;

import java.util.Map;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * EntityConfig is a configuration object to tag entities. This class refers to
 * entity parameters as seen by the user. Having said this, the only difference
 * between Entity class and this EntityConfig class is port is not a number but
 * a OF Named switch-interface. 
 */
@LogMessageCategory("Device Management")
public class EntityConfig {
    String mac;
    String vlan;
    String dpid;
    String interfaceName;
    
    protected static Logger log = 
            LoggerFactory.getLogger(EntityConfig.class);
    public EntityConfig(String mac, String vlan, String dpid, 
                        String interfaceName) {
        this.mac = null;
        this.vlan = null;
        this.dpid = null;
        this.interfaceName = null;
        if (mac != null && !mac.isEmpty())
            this.mac = mac;
        if (vlan != null && !vlan.isEmpty())
            this.vlan = vlan;
        if (dpid != null && !dpid.isEmpty())
            this.dpid = dpid;
        if (interfaceName != null && !interfaceName.isEmpty())
            this.interfaceName = interfaceName;
    }
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="On Switch - {dpid} Port- {port} does not exist yet",
                explanation="Port on a switch does not exist yet",
                recommendation=LogMessageDoc.TRANSIENT_CONDITION),
        @LogMessageDoc(level="ERROR",
                message="Switch - {dpid} does not exist yet",
                explanation="Switch does not exist yet",
                recommendation=LogMessageDoc.TRANSIENT_CONDITION)
    })
            
    static public EntityConfig convertEntityToEntityConfig(
                                IControllerService controllerProvider, 
                                Entity entity) {
        if (entity == null)
            return null;
        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
        
        String portName = null;
        String dpid = null;
        String vlan = null;
        if (entity.getVlan() != null 
                && entity.getVlan() != Ethernet.VLAN_UNTAGGED)
            vlan = entity.getVlan().toString();
        if (entity.getSwitchDPID() != null) {
            dpid = HexString.toHexString(entity.getSwitchDPID());
        }
        String mac = null;
        if (entity.getMacAddress() != 0) {
            mac = HexString.toHexString(entity.getMacAddress(), 6);
        }
        if (switches == null) {
            if (entity.getSwitchPort() != null) {
                return null;
            }
        } else {
            
            if (entity.getSwitchPort() != null) {
                IOFSwitch sw = switches.get(entity.getSwitchDPID());
                OFPhysicalPort port = null;
                if (sw == null) {
                    log.error("Switch - " + entity.getSwitchDPID() + 
                            " does not exist yet");
                    return null;
                }

                port = sw.getPort(entity.getSwitchPort().shortValue());
                if (port == null) {
                    log.error("On Switch - " + entity.getSwitchDPID() + " Port-" + 
                            entity.getSwitchPort() + " does not exist yet");
                    return null;
                }
                portName = port.getName();
            }
        }
        return new EntityConfig(mac, vlan, dpid, 
                                portName);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dpid == null) ? 0 : dpid.hashCode());
        result = prime * result + ((interfaceName == null) ? 
                0 : interfaceName.hashCode());
        result = prime * result + ((mac == null) ? 0 : mac.hashCode());
        result = prime * result + ((vlan == null) ? 0 : vlan.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "EntityConfig [mac=" + mac + ", vlan=" + vlan + ", dpid="
                + dpid + ", interfaceName=" + interfaceName + "]";
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof EntityConfig)) return false;
        EntityConfig other = (EntityConfig) obj;
        if (dpid == null) {
            if (other.dpid != null) return false;
        } else if (!dpid.equals(other.dpid)) return false;
        if (interfaceName == null) {
            if (other.interfaceName != null) return false;
        } else if (!interfaceName.equals(other.interfaceName)) return false;
        if (mac == null) {
            if (other.mac != null) return false;
        } else if (!mac.equals(other.mac)) return false;
        if (vlan == null) {
            if (other.vlan != null) return false;
        } else if (!vlan.equals(other.vlan)) return false;
        return true;
    }    
}
