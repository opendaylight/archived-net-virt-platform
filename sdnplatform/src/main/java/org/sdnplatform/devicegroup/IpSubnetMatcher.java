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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.util.IPV4Subnet;



/**
 * Match the IP Subnet field
 * @author readams
 */
public class IpSubnetMatcher<T extends IDeviceGroup> 
extends AbstractRuleMatcher<T> {

    public IpSubnetMatcher(DeviceGroupMatcher<T> deviceGroupMatcher) {
        super(deviceGroupMatcher);
    }

    @Override
    public String getName() {
        return "ipSubnet";
    }

    @Override
    public Set<MembershipRule<T>> match(IDevice d) {
        if (deviceGroupMatcher.ipRuleTrie.size() == 0) return null;
        
        Set<MembershipRule<T>> resultSet = null;
        IPV4Subnet s = new IPV4Subnet();
        s.maskBits = (short)32;
        for (Integer dna : d.getIPv4Addresses()) {
            s.address = dna;
            
            List<Entry<IPV4Subnet, Set<MembershipRule<T>>>> resultList = 
                    deviceGroupMatcher.ipRuleTrie.prefixSearch(s);
            if (resultList == null) continue;
            
            for (Entry<IPV4Subnet, Set<MembershipRule<T>>> result : resultList) {
                if (resultSet == null) {
                    resultSet = new TreeSet<MembershipRule<T>>();
                }
                resultSet.addAll(result.getValue());
            }
        }
        return resultSet;
    }

    @Override
    public boolean ruleHasField(MembershipRule<T> rule) {
        return (rule.getIpSubnet() != null);
    }

}
