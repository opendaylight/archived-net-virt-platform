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

package org.sdnplatform.devicegroup;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.SwitchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Match the switch and port rules
 * @author readams
 */
public class SwitchPortMatcher<T extends IDeviceGroup> 
extends AbstractRuleMatcher<T> {
    protected static Logger logger = LoggerFactory.getLogger(SwitchPortMatcher.class);


    public SwitchPortMatcher(DeviceGroupMatcher<T> deviceGroupMatcher) {
        super(deviceGroupMatcher);
    }

    @Override
    public String getName() {
        return "switchPort";
    }

    /**
     * Get the hash table key for looking up a switch port rule
     * @param spt
     * @return the key
     */
    protected String getSwitchPortKey(SwitchPort spt) {
        Map<Long, IOFSwitch> switches =
                deviceGroupMatcher.controllerProvider.getSwitches();
        IOFSwitch sw = switches.get(spt.getSwitchDPID());
        if (sw == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot generate SwitchPortKey. Switch {} not "
                             + "connected",
                             HexString.toHexString(spt.getSwitchDPID()));
            }
            return null;
        }
        
        String portName = null;
        OFPhysicalPort pp = sw.getPort((short)spt.getPort());
        if (pp != null && sw.portEnabled(pp)) {
            portName = pp.getName();
        }
        if (portName == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot generate SwitchPortKey. Port {} ({}) on "
                             + "switch {} not found or enabled",
                             new Object[] { spt.getPort(), pp,
                                 HexString.toHexString(spt.getSwitchDPID()) }
                         );
            }
            return null;
        }
        return spt.getSwitchDPID() + "-" + portName;
    }
    
    @Override
    public Set<MembershipRule<T>> match(IDevice d) {
        if (deviceGroupMatcher.switchPortMap.size() == 0) return null;

        Set<MembershipRule<T>> resultSet = null;

        for (SwitchPort dap : d.getAttachmentPoints()) {
            // Check for switch rule
            long id = dap.getSwitchDPID();
            Set<MembershipRule<T>> matches = 
                    deviceGroupMatcher.switchPortMap.get(Long.toString(id));
            if (matches != null && matches.size() > 0) {
                if (resultSet == null) { 
                    resultSet = new TreeSet<MembershipRule<T>>();
                }
                resultSet.addAll(matches);
            }

            // Check for port rules
            String key = getSwitchPortKey(dap);
            if (key == null) continue;

            matches = deviceGroupMatcher.switchPortMap.get(key);
            if (matches != null && matches.size() > 0) {
                if (resultSet == null) { 
                    resultSet = new TreeSet<MembershipRule<T>>();
                }
                resultSet.addAll(matches);
            }
        }
        return resultSet;
    }

    @Override
    public boolean ruleHasField(MembershipRule<T> rule) {
        return (rule.getSwitchId() != null);
    }

}
