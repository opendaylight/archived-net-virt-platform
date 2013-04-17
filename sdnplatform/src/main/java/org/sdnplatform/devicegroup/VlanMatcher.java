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

import java.util.Set;

import org.sdnplatform.devicemanager.IDevice;



/**
 * Match against VLAN rules
 * @author readams
 */
public class VlanMatcher<T extends IDeviceGroup>
extends AbstractRuleMatcher<T> {

    public VlanMatcher(DeviceGroupMatcher<T> deviceGroupMatcher) {
        super(deviceGroupMatcher);
    }

    @Override
    public String getName() {
        return "vlan";
    }

    @Override
    public Set<MembershipRule<T>> match(IDevice d) {
        Short[] vlans = d.getVlanId();
        
        for (Short vlan : vlans) {
            if ((vlan != null) && (vlan >= 0) && (vlan < 4096)) {
                Set<MembershipRule<T>> r =
                        deviceGroupMatcher.vlanList.get(vlan);
                if (r != null) return r; 
            }
        }

        return null;
    }

    @Override
    public boolean ruleHasField(MembershipRule<T> rule) {
        return (rule.getVlans() != null);
    }

}
