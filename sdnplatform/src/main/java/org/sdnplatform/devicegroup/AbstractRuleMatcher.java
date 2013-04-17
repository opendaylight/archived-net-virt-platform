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
 * Matches against a type of device group rule.
 * @author readams
 */
public abstract class AbstractRuleMatcher<T extends IDeviceGroup> {
    protected DeviceGroupMatcher<T> deviceGroupMatcher;
    
    public AbstractRuleMatcher(DeviceGroupMatcher<T> deviceGroupMatcher) {
        super();
        this.deviceGroupMatcher = deviceGroupMatcher;
    }
    
    /**
     * Get the name of the matcher
     * @return a string name
     */
    public abstract String getName();
    
    /**
     * Match against the device and return candidate matches.
     * Must be called with a config read lock held.
     * @param d The device to match against
     * @return a set of candidate rule matches
     */
    public abstract Set<MembershipRule<T>> match(IDevice d);
    
    /**
     * Check the given interface rule to find out whether it
     * has a given field
     * @param rule
     * @return
     */
    public abstract boolean ruleHasField(MembershipRule<T> rule);
}
