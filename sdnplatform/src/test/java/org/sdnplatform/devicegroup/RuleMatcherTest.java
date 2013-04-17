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

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.openflow.util.HexString;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.devicegroup.AbstractRuleMatcher;
import org.sdnplatform.devicegroup.DeviceGroupBase;
import org.sdnplatform.devicegroup.DeviceGroupMatcher;
import org.sdnplatform.devicegroup.IpSubnetMatcher;
import org.sdnplatform.devicegroup.MacMatcher;
import org.sdnplatform.devicegroup.MembershipRule;
import org.sdnplatform.devicegroup.SwitchPortMatcher;
import org.sdnplatform.devicegroup.TagMatcher;
import org.sdnplatform.devicegroup.VlanMatcher;
import org.sdnplatform.tagmanager.ITagManagerService;


public class RuleMatcherTest {
    protected class MyDG extends DeviceGroupBase {
        // We really just want to type less that's why we create this 
        // class
        public MyDG(String name) {
            super(name);
        }
    }
    protected ITagManagerService mockTagManager;
    protected MockControllerProvider mockControllerProvider;
    protected DeviceGroupMatcher<MyDG> dgMatcher;
    protected MacMatcher<MyDG> macMatcher;
    protected IpSubnetMatcher<MyDG> ipSubnetMatcher;
    protected SwitchPortMatcher<MyDG> switchPortMatcher;
    protected TagMatcher<MyDG> tagMatcher;
    protected VlanMatcher<MyDG> vlanMatcher;
    
    protected ArrayList<AbstractRuleMatcher<MyDG>> allMatchers;
    
    // The parent device group we will use for our membership rules
    protected MyDG deviceGroup;
    
    // Whoa. For each matcher we store a list of rules that have only
    // a single field set it's the matcher's field.
    HashMap<String, List<MembershipRule<MyDG>>> singleRules;
    
    @Before
    public void setUp() {
        mockTagManager = createMock(ITagManagerService.class);
        mockControllerProvider = new MockControllerProvider();
        
        dgMatcher = new DeviceGroupMatcher<MyDG>(mockTagManager,
                mockControllerProvider);
        macMatcher = new MacMatcher<MyDG>(dgMatcher);
        ipSubnetMatcher = new IpSubnetMatcher<MyDG>(dgMatcher);
        switchPortMatcher = new SwitchPortMatcher<MyDG>(dgMatcher);
        tagMatcher = new TagMatcher<MyDG>(dgMatcher);
        vlanMatcher = new VlanMatcher<MyDG>(dgMatcher);
        
        allMatchers = new ArrayList<AbstractRuleMatcher<MyDG>>();
        allMatchers.add(macMatcher);
        allMatchers.add(ipSubnetMatcher);
        allMatchers.add(switchPortMatcher); 
        allMatchers.add(tagMatcher);
        allMatchers.add(vlanMatcher);
        
        deviceGroup = new MyDG("DeviceGroup");
        deviceGroup.setActive(true);
        
        singleRules = new HashMap<String,List<MembershipRule<MyDG>>> ();
        for (AbstractRuleMatcher<MyDG> m: allMatchers) {
            singleRules.put(m.getName(), new LinkedList<MembershipRule<MyDG>>());
        }
        singleRules.put("other", new LinkedList<MembershipRule<MyDG>>());
        
        MembershipRule<MyDG> r;
        r = new MembershipRule<MyDG>("mac_1", deviceGroup);
        r.setMac(HexString.toHexString(1L, 6));
        addToSingleRule(macMatcher, r);
        r = new MembershipRule<MyDG>("ipSubnet_1", deviceGroup);
        r.setIpSubnet("1.2.3.4/8");
        addToSingleRule(ipSubnetMatcher, r);
        r = new MembershipRule<MyDG>("switchPort_1", deviceGroup);
        r.setSwitchId(HexString.toHexString(1L, 8));
        r.setPorts("eth1");
        addToSingleRule(switchPortMatcher, r);
        // Just a port. This won't match anything
        r = new MembershipRule<MyDG>("Port_1", deviceGroup);
        r.setPorts("eth1");
        singleRules.get("other").add(r);
        r = new MembershipRule<MyDG>("switch_1", deviceGroup);
        r.setSwitchId(HexString.toHexString(1L, 8));
        addToSingleRule(switchPortMatcher, r);
        r = new MembershipRule<MyDG>("vlan_1", deviceGroup);
        r.setVlans("1");
        addToSingleRule(vlanMatcher, r);
        r = new MembershipRule<MyDG>("tag_1", deviceGroup);
        r.setTags("ns1.tag1=v1");
        addToSingleRule(tagMatcher, r);
    }
    
    protected void addToSingleRule(AbstractRuleMatcher<MyDG> matcher,
                                MembershipRule<MyDG> rule) {
        rule.setActive(true);
        singleRules.get(matcher.getName()).add(rule);
    }
    
    protected MembershipRule<MyDG> allocateRule(String name) {
        MembershipRule<MyDG> r = new MembershipRule<RuleMatcherTest.MyDG>(name,
                deviceGroup);
        r.setActive(true);
        return r;
    }
    
    @Test
    public void testHaveAllMatcher() {
        // Test that the set of actual rule matchers is as we
        // expect ==> this will tell us if we forgot to add
        // tests for all matchers
        Set<String> expectedMatchers = new HashSet<String>();
        Set<String> actualMatchers = new HashSet<String>();
        for (AbstractRuleMatcher<MyDG> m: allMatchers) {
            expectedMatchers.add(m.getName());
        }
        for (AbstractRuleMatcher<MyDG> m: dgMatcher.RULE_MATCHERS) {
            actualMatchers.add(m.getName());
        }
        assertEquals(expectedMatchers, actualMatchers);
    }
    
    @Test
    public void testHasField() {
        for (AbstractRuleMatcher<MyDG> m: allMatchers) {
            for (MembershipRule<MyDG> rule: singleRules.get(m.getName())) {
                String msg = m.getName() + " " + rule.getName();
                assertTrue(msg, m.ruleHasField(rule));
            }
            for (AbstractRuleMatcher<MyDG> otherMatcher: allMatchers) {
                if (m == otherMatcher)
                    continue;
                for (MembershipRule<MyDG> rule: singleRules.get(otherMatcher.getName())) {
                    String msg = m.getName() + " " + rule.getName();
                    assertFalse(msg, m.ruleHasField(rule));
                }
            }
        }
    }
} 