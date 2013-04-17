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

import org.sdnplatform.devicemanager.IDevice;


/**
 * This class matches an IDevice to a set of matching DeviceGroup membership
 * rules. The returned DeviceGroup rules are ordered by priority and the
 * highest priority rule can this be considered as defining this Device's 
 * device group. We only return multiple matching rules if allow multiple
 * is true for these rules.
 * 
 * A NetVirt or a BetterEntityClass are concrete examples of a device group class.
 * 
 * <b>NOTE:</b> This interface is not synchronized. Concurrent read 
 * operations are allowed, but must be isolated from operations that
 * modify state (@see clear(), addRuleIfActive()). 
 * 
 * @author gregor
 *
 * @param <T> The concrete DeviceGroup class for which we provide 
 * matching. T must implement IDeviceGroup. A single matcher can only 
 * match for a single class of DeviceGroups
 */
public interface IDeviceGroupMatcher<T extends IDeviceGroup> {
    /**
     * Returns whether this matcher has any Mac based rules
     * @return
     */
    public boolean hasMacRules();

    /**
     * Returns whether this matcher has any Mac based rules
     * @return
     */
    public boolean hasIpSubnetRules();

    /**
     * Returns whether this matcher has any switch/port based rules
     * @return
     */
    public boolean hasSwitchPortRules();

    /**
     * Returns whether this matcher has any VLAN based rules
     * @return
     */
    public boolean hasVlanRules();

    /**
     * Returns whether this matcher has any tag based rules
     * @return
     */
    public boolean hasTagRules();

    /** 
     * Clear all rules
     */
    public void clear();

    /**
     * Add a rule to this matcher if the rule and the rule's parent
     * DeviceGroup are both active (@see isActive)
     * @param rule
     */
    public void addRuleIfActive(MembershipRule<T> rule);

    /**
     * Match a device against the interface rules and return the list of
     * matching interface rules (highest priority first)
     * 
     * @param d
     * @return
     */
    public List<MembershipRule<T>> matchDevice(IDevice d) throws Exception;

}
