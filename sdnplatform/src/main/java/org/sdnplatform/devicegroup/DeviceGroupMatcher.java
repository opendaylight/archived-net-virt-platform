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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.util.IPV4Subnet;
import org.sdnplatform.util.IPV4SubnetTrie;


/** 
 * Concrete implementation of IDeviceGroupMatcher
 */
public class DeviceGroupMatcher<T extends IDeviceGroup>
implements IDeviceGroupMatcher<T> {
    protected ITagManagerService tagManager;
    protected IControllerService controllerProvider;

    
    // Rule lookup data structures
    /**
     * Data structure for efficiently matching against MAC rules
     */
    protected Map<String,Set<MembershipRule<T>>> macRuleMap;

    /**
     * Trie does prefix matching for IP subnets
     */
    protected IPV4SubnetTrie<Set<MembershipRule<T>>> ipRuleTrie;

    /**
     * Data structure for matching against switch and port rules
     */
    protected Map<String,Set<MembershipRule<T>>> switchPortMap;

    /**
     * Data structure for matching against vlan rules
     */
    protected ArrayList<Set<MembershipRule<T>>> vlanList;
    protected boolean hasVlanRulesFlag;

    /**
     * Data structure for matching against tag rules
     * tagRuleMap: tag -> set<MembershipRule<T>>
     */
    protected Map<String,Set<MembershipRule<T>>> tagRuleMap;

    /**
     * This is the list of rule matchers that will be used to
     * match against interface rules
     */
    protected final ArrayList<AbstractRuleMatcher<T>> RULE_MATCHERS;
    
    /** 
     * Add the rule to the match map
     */
    protected void updateMatchMap(Map<String, Set<MembershipRule<T>>> matchMap,
                                  String key,
                                  MembershipRule<T> rule) {
        Set<MembershipRule<T>> rules = matchMap.get(key);
        if (rules == null) {
            matchMap.put(key, rules = new TreeSet<MembershipRule<T>>());
        }
        rules.add(rule);
    }
    
    // Check if we have a particular kind of rules
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#hasMacRules()
     */
    @Override
    public boolean hasMacRules() {
        return macRuleMap.size() != 0;
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#hasIpSubnetRules()
     */
    @Override
    public boolean hasIpSubnetRules() {
        return ipRuleTrie.size() != 0;
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#hasSwitchPortRules()
     */
    @Override
    public boolean hasSwitchPortRules() {
        return switchPortMap.size() != 0;
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#hasVlanRules()
     */
    @Override
    public boolean hasVlanRules() {
        return hasVlanRulesFlag;
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#hasTagRules()
     */
    @Override
    public boolean hasTagRules() {
        return tagRuleMap.size() != 0;
    }
    
    public DeviceGroupMatcher(ITagManagerService tagManager, 
                              IControllerService controllerProvider) {
        RULE_MATCHERS = new ArrayList<AbstractRuleMatcher<T>>();
        RULE_MATCHERS.add(new MacMatcher<T>(this));
        RULE_MATCHERS.add(new IpSubnetMatcher<T>(this));
        RULE_MATCHERS.add(new SwitchPortMatcher<T>(this));
        RULE_MATCHERS.add(new VlanMatcher<T>(this));
        RULE_MATCHERS.add(new TagMatcher<T>(this));
        
        macRuleMap = new HashMap<String,Set<MembershipRule<T>>>();
        ipRuleTrie = new IPV4SubnetTrie<Set<MembershipRule<T>>>();
        switchPortMap = new HashMap<String,Set<MembershipRule<T>>>();
        tagRuleMap = new HashMap<String,Set<MembershipRule<T>>>();
        vlanList = new ArrayList<Set<MembershipRule<T>>>(4096);
        for (int i=0; i<4096; i++)
            vlanList.add(null);
        
        this.controllerProvider = controllerProvider;
        this.tagManager = tagManager;
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#clear()
     */
    @Override
    public void clear() {
        // Flush rule-matching data structures
        macRuleMap.clear();
        ipRuleTrie.clear();
        switchPortMap.clear();
        vlanList.clear();
        for (int i=0; i<4096; i++)
            vlanList.add(null);
        hasVlanRulesFlag = false; 
        tagRuleMap.clear();
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#addRuleIfActive(org.sdnplatform.devicegroup.MembershipRule)
     */
    @Override
    public void addRuleIfActive(MembershipRule<T> rule) {              
        if (!rule.isActive() || !rule.getParentDeviceGroup().isActive())
            return;

        // Setup MAC matching data structure
        if (rule.getMac() != null) {
            updateMatchMap(macRuleMap, rule.getMac(), rule);
        }

        // Setup IP subnet matching data structure
        if (rule.getIpSubnet() != null) {
            IPV4Subnet s = new IPV4Subnet(rule.getIpSubnet());
            Set<MembershipRule<T>> rules = ipRuleTrie.get(s);
            if (rules == null) {
                ipRuleTrie.put(s, rules = new TreeSet<MembershipRule<T>>());
            }
            rules.add(rule);
        }

        // Setup switch/port matching data structure
        if (rule.getSwitchId() != null) {
            long switchId = HexString.toLong(rule.getSwitchId());
            if (rule.getPorts() != null) {
                for (String p : rule.getPortList()) {
                    String k = switchId + "-" + p;
                    updateMatchMap(switchPortMap, k, rule);
                }
            } else {
                updateMatchMap(switchPortMap, 
                               Long.toString(switchId),
                               rule);
            }
        }

        // Setup vlan matching data structure
        if (rule.getVlans() != null) {
            List<Integer> vlans = rule.getVlanList();
            if (vlans != null) {
                for (Integer vlan : vlans) {
                    Set<MembershipRule<T>> rules = vlanList.get(vlan);
                    if (rules == null) {
                        rules = new TreeSet<MembershipRule<T>>();
                        vlanList.set(vlan, rules);
                    }
                    rules.add(rule);
                }
                hasVlanRulesFlag = true;
            }
        }

        // Setup tag matching data structure
        if (rule.getTagList() != null) {
            for (String tag: rule.getTagList()) {
                updateMatchMap(tagRuleMap, tag, rule);
            }
        }
    }
    
    /* (non-Javadoc)
     * @see org.sdnplatform.devicegroup.IDeviceGroupMatcher#matchDevice(org.sdnplatform.devicemanager.IDevice)
     */
    @Override
    public List<MembershipRule<T>> matchDevice(IDevice d) 
            throws Exception {
        Map<AbstractRuleMatcher<T>, Set<MembershipRule<T>>> possibleMatches = null;

        // First generate a set of candidate matches by matching each of 
        // the fields in the rules.
        for (AbstractRuleMatcher<T> matcher : RULE_MATCHERS) {
            Set<MembershipRule<T>> r = matcher.match(d);
            if (r != null && r.size() > 0) {
                if (possibleMatches == null)
                    possibleMatches = 
                        new HashMap<AbstractRuleMatcher<T>, Set<MembershipRule<T>>>();
                possibleMatches.put(matcher, r);
            }
        }

        if (possibleMatches == null) {
            // no matching interfaces; it will be assigned to the default NetVirt
            return null;
        }

        // Validate candidate matches by checking that all fields for a rule
        // are matched
        TreeSet<MembershipRule<T>> matches = new TreeSet<MembershipRule<T>>();
        TreeSet<MembershipRule<T>> notmatches = new TreeSet<MembershipRule<T>>();
        for (AbstractRuleMatcher<T> mname : possibleMatches.keySet()) {
            Set<MembershipRule<T>> candidates = possibleMatches.get(mname);
            for (MembershipRule<T> candidate : candidates) {
                if (matches.contains(candidate) || 
                    notmatches.contains(candidate))
                    continue;

                boolean allmatched = true;
                for (AbstractRuleMatcher<T> matcher : RULE_MATCHERS) {
                    if (matcher.ruleHasField(candidate)) {
                        Set<MembershipRule<T>> fieldcandidates = 
                            possibleMatches.get(matcher);
                        if (fieldcandidates == null || 
                            !fieldcandidates.contains(candidate)) {
                            allmatched = false;
                            break;
                        }
                    }
                }
                if (allmatched) {
                    matches.add(candidate);
                } else {
                    notmatches.add(candidate);
                }
            }
        }

        if (matches.size() == 0) {
            // no matching interfaces; it will be assigned to the default 
            // device group
            return null;
        }

        // Generate the final set of matched interfaces by traversing the 
        // matching rules in priority order
        ArrayList<MembershipRule<T>> deviceRules = 
            new ArrayList<MembershipRule<T>>();
        T current = null;
        for (MembershipRule<T> match : matches) {
            if (match.isMultipleAllowed()) {
                if (!match.getParentDeviceGroup().equals(current)) {
                    deviceRules.add(match); 
                    current = match.getParentDeviceGroup();
                }
            } else if (deviceRules.size() == 0) {
                // Exactly one interface rule matches and we don't allow 
                // multiple matches to a device group
                deviceRules.add(match); 
                break;
            }
        }

        return deviceRules;
    }

}
