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

package org.sdnplatform.netvirt.virtualrouting.internal;


import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Before;
import static org.easymock.EasyMock.*;

import org.junit.Test;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction;
import org.sdnplatform.netvirt.virtualrouting.GatewayNode;
import org.sdnplatform.netvirt.virtualrouting.internal.GatewayPoolImpl;
import org.sdnplatform.netvirt.virtualrouting.internal.VRouterImpl;
import org.sdnplatform.netvirt.virtualrouting.internal.VRouterInterface;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouterManager;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.util.IPV4Subnet;



public class VRouterTest extends PlatformTestCase {
    private VRouterImpl vr;
    private static Long vMac1 = Ethernet.
            toLong(Ethernet.toMACAddress("00:11:22:33:44:55"));
    VirtualRouterManager vRtrManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        vRtrManager = createMock(VirtualRouterManager.class);
        vr = new VRouterImpl("r1", "t1", vMac1, vRtrManager);
    }

    @Test
    public void testGetters() {
        assertEquals("r1", vr.getName());
        assertEquals("t1", vr.getTenant());
    }

    @Test
    /* This test tests internal working of the class alone */
    public void testCreateInterface() {
        vr.createInterface("if1", "netVirt1", null, true);
        assertNotNull(vr.getInterfaceMap().get("if1"));
        assertEquals(true, vr.getInterfaceMap().get("if1").isNetVirt());

        vr.createInterface("if2", null, "rtr1", true);
        assertNotNull(vr.getInterfaceMap().get("if2"));
        assertEquals(false, vr.getInterfaceMap().get("if2").isNetVirt());

        assertEquals(2, vr.getInterfaceMap().size());
    }

    @Test
    /* This test tests internal working of the class alone */
    public void testAssignInterfaceAddr() throws Exception {
        try {
            vr.assignInterfaceAddr("if1", "10.10.1.1", "255.255.0.0");
            fail();
        } catch (IllegalArgumentException e) {
            /* An exception must have been thrown */
        }
        vr.createInterface("if1", "netVirt1", null, true);
        vr.createInterface("if2", null, "rtr1", true);
        vr.assignInterfaceAddr("if1", "10.10.1.1", "255.255.0.0");
        IPV4Subnet addr = new IPV4Subnet("10.10.1.1/16");
        assertNotNull(vr.getInterfaceMap().get("if1").getAddrs().contains(addr));

        vr.assignInterfaceAddr("if2", "200.10.1.1", "255.255.255.0");
        addr = new IPV4Subnet("200.10.1.1/24");
        assertNotNull(vr.getInterfaceMap().get("if2").getAddrs().contains(addr));

        vr.assignInterfaceAddr("if1", "10.20.1.1", "255.255.0.0");
        addr = new IPV4Subnet("10.20.1.1/16");
        assertNotNull(vr.getInterfaceMap().get("if1").getAddrs().contains(addr));
        assertEquals(2, vr.getInterfaceMap().get("if1").getAddrs().size());
    }

    @Test
    /* This test tests internal working of the class alone */
    public void testAddRoutingRule() throws Exception {
        vr.createInterface("if1", "netVirt1", null, true);
        vr.createInterface("if2", "netVirt2", null, true);
        vr.createInterface("ifs", null, "rtr1", true);

        /* Test putting rules in the SrcHostRuleMap */
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null,
                          "netVirt1", null, null, null, null, "permit");
        assertEquals(1, vr.getSrcHostRuleMap().get("10.10.1.1").ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", "tenant1", null,
                          null, null, null, null, "permit");
        assertEquals(2, vr.getSrcHostRuleMap().get("10.10.1.1").ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null, null,
                          "10.20.1.1", "0.0.0.0", null, null, "permit");
        assertEquals(3, vr.getSrcHostRuleMap().get("10.10.1.1").ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null, null,
                          "10.10.0.0", "0.0.255.255", null, null, "permit");
        assertEquals(1,
                     vr.getSrcHostRuleMap().get("10.10.1.1").subnetTrie.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null, null,
                          "255.255.255.255", "255.255.255.255", null, null,
                          "deny");
        assertEquals(2,
                     vr.getSrcHostRuleMap().get("10.10.1.1").subnetTrie.size());

        /* Test putting rules in the SrcNetVirtRuleMap */
        vr.addRoutingRule(null, "netVirt1", null, null, null, "netVirt2", null,
                          null, "if2", null, "deny");
        assertEquals(1, vr.getSrcNetVirtRuleMap().get("netVirt1").ruleSet.size());
        vr.addRoutingRule(null, "netVirt1", null, null, null, null, "10.20.1.1",
                          "0.0.0.0", "if2", null, "deny");
        assertEquals(2, vr.getSrcNetVirtRuleMap().get("netVirt1").ruleSet.size());
        vr.addRoutingRule(null, "netVirt1", null, null, "Tenant2", null, null,
                          null, "if2", null, "permit");
        assertEquals(3, vr.getSrcNetVirtRuleMap().get("netVirt1").ruleSet.size());
        vr.addRoutingRule(null, "netVirt1", null, null, null, null, "10.30.0.0",
                          "0.0.255.255", "ifs", "10.30.1.1", "permit");
        assertEquals(1, vr.getSrcNetVirtRuleMap().get("netVirt1").subnetTrie.size());
        vr.addRoutingRule(null, "netVirt1", null, null, null, null, "0.0.0.0",
                          "255.255.255.255", null, null, "deny");
        assertEquals(2, vr.getSrcNetVirtRuleMap().get("netVirt1").subnetTrie.size());

        /* Test putting rules in the SrcTenantRuleMap */
        vr.addRoutingRule("t1", null, null, null, null, null, "10.30.1.1",
                          "0.0.0.0", "ifs", null, "permit");
        assertEquals(1, vr.getSrcTenantRuleMap().get("t1").ruleSet.size());
        vr.addRoutingRule("t1", null, null, null, "t2", null, null,
                          null, null, null, "permit");
        assertEquals(2, vr.getSrcTenantRuleMap().get("t1").ruleSet.size());
        vr.addRoutingRule("t1", null, null, null, null, "netVirt4", null,
                          null, null, null, "permit");
        assertEquals(3, vr.getSrcTenantRuleMap().get("t1").ruleSet.size());
        vr.addRoutingRule("t1", null, null, null, null, null, "10.30.0.0",
                          "0.0.255.255", null, null, "deny");
        assertEquals(1, vr.getSrcTenantRuleMap().get("t1").subnetTrie.size());
        vr.addRoutingRule("t1", null, null, null, null, null, "255.255.255.255",
                          "255.255.255.255", "ifs", null, "permit");
        assertEquals(2, vr.getSrcTenantRuleMap().get("t1").subnetTrie.size());

        /* Test putting rules in the srcSubnetRuleTrie */
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, null,
                          "10.30.0.1", "0.0.0.0", "ifs", null, "permit");
        IPV4Subnet addr = new IPV4Subnet("10.10.0.0/16");
        assertEquals(1, vr.getSrcSubnetRuleTrie().get(addr).ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", "t2", null,
                          null, null, "ifs", null, "permit");
        assertEquals(2, vr.getSrcSubnetRuleTrie().get(addr).ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, "netVirt4",
                          null, null, "ifs", null, "permit");
        assertEquals(3, vr.getSrcSubnetRuleTrie().get(addr).ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, null,
                          "10.30.0.1", "0.0.255.255", null, null, "deny");
        assertEquals(1, vr.getSrcSubnetRuleTrie().get(addr).subnetTrie.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, null,
                          "255.255.255.255", "255.255.255.255", null, null,
                          "permit");
        assertEquals(2, vr.getSrcSubnetRuleTrie().get(addr).subnetTrie.size());

        try {
            /* Use an invalid interface and see whether there is an exception */
            vr.addRoutingRule(null, "netVirt1", null, null,"t2", null, null,
                              null, "if3", null, "permit");
            fail();
        } catch (IllegalArgumentException e) {
            /* Exception should be thrown */
        }
    }

    @Test
    public void testAddRoutingRuleWithNextHopGatewayPool() throws Exception {
        vr.createInterface("if1", "netVirt1", null, true);
        vr.createInterface("if2", "netVirt2", null, true);
        vr.createInterface("ifs", null, "rtr1", true);

        /* Test putting rules in the SrcHostRuleMap */
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null,
                          "netVirt1", null, null, null, null, "permit", "testpool");
        assertEquals(1, vr.getSrcHostRuleMap().get("10.10.1.1").ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", "tenant1", null,
                          null, null, null, null, "permit", "testpool");
        assertEquals(2, vr.getSrcHostRuleMap().get("10.10.1.1").ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null, null,
                          "10.20.1.1", "0.0.0.0", null, null, "permit",
                          "testpool");
        assertEquals(3, vr.getSrcHostRuleMap().get("10.10.1.1").ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null, null,
                          "10.10.0.0", "0.0.255.255", null, null, "permit",
                          "testpool");
        assertEquals(1,
                     vr.getSrcHostRuleMap().get("10.10.1.1").subnetTrie.size());
        vr.addRoutingRule(null, null, "10.10.1.1", "0.0.0.0", null, null,
                          "255.255.255.255", "255.255.255.255", null, null,
                          "deny", "testpool");
        assertEquals(2,
                     vr.getSrcHostRuleMap().get("10.10.1.1").subnetTrie.size());

        /* Test putting rules in the SrcNetVirtRuleMap */
        vr.addRoutingRule(null, "netVirt1", null, null, null, "netVirt2", null,
                          null, "if2", null, "deny", "testpool");
        assertEquals(1, vr.getSrcNetVirtRuleMap().get("netVirt1").ruleSet.size());
        vr.addRoutingRule(null, "netVirt1", null, null, null, null, "10.20.1.1",
                          "0.0.0.0", "if2", null, "deny", "testpool");
        assertEquals(2, vr.getSrcNetVirtRuleMap().get("netVirt1").ruleSet.size());
        vr.addRoutingRule(null, "netVirt1", null, null, "Tenant2", null, null,
                          null, "if2", null, "permit", "testpool");
        assertEquals(3, vr.getSrcNetVirtRuleMap().get("netVirt1").ruleSet.size());
        vr.addRoutingRule(null, "netVirt1", null, null, null, null, "10.30.0.0",
                          "0.0.255.255", "ifs", "10.30.1.1", "permit",
                          null);
        assertEquals(1, vr.getSrcNetVirtRuleMap().get("netVirt1").subnetTrie.size());
        vr.addRoutingRule(null, "netVirt1", null, null, null, null, "0.0.0.0",
                          "255.255.255.255", null, null, "deny", "testpool");
        assertEquals(2, vr.getSrcNetVirtRuleMap().get("netVirt1").subnetTrie.size());

        /* Test putting rules in the SrcTenantRuleMap */
        vr.addRoutingRule("t1", null, null, null, null, null, "10.30.1.1",
                          "0.0.0.0", "ifs", null, "permit", "testpool");
        assertEquals(1, vr.getSrcTenantRuleMap().get("t1").ruleSet.size());
        vr.addRoutingRule("t1", null, null, null, "t2", null, null,
                          null, null, null, "permit", "testpool");
        assertEquals(2, vr.getSrcTenantRuleMap().get("t1").ruleSet.size());
        vr.addRoutingRule("t1", null, null, null, null, "netVirt4", null,
                          null, null, null, "permit", "testpool");
        assertEquals(3, vr.getSrcTenantRuleMap().get("t1").ruleSet.size());
        vr.addRoutingRule("t1", null, null, null, null, null, "10.30.0.0",
                          "0.0.255.255", null, null, "deny", "testpool");
        assertEquals(1, vr.getSrcTenantRuleMap().get("t1").subnetTrie.size());
        vr.addRoutingRule("t1", null, null, null, null, null, "255.255.255.255",
                          "255.255.255.255", "ifs", null, "permit", "testpool");
        assertEquals(2, vr.getSrcTenantRuleMap().get("t1").subnetTrie.size());

        /* Test putting rules in the srcSubnetRuleTrie */
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, null,
                          "10.30.0.1", "0.0.0.0", "ifs", null, "permit",
                          "testpool");
        IPV4Subnet addr = new IPV4Subnet("10.10.0.0/16");
        assertEquals(1, vr.getSrcSubnetRuleTrie().get(addr).ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", "t2", null,
                          null, null, "ifs", null, "permit", "testpool");
        assertEquals(2, vr.getSrcSubnetRuleTrie().get(addr).ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, "netVirt4",
                          null, null, "ifs", null, "permit", "testpool");
        assertEquals(3, vr.getSrcSubnetRuleTrie().get(addr).ruleSet.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, null,
                          "10.30.0.1", "0.0.255.255", null, null, "deny",
                          "testpool");
        assertEquals(1, vr.getSrcSubnetRuleTrie().get(addr).subnetTrie.size());
        vr.addRoutingRule(null, null, "10.10.0.0", "0.0.255.255", null, null,
                          "255.255.255.255", "255.255.255.255", null, null,
                          "permit", "testpool");
        assertEquals(2, vr.getSrcSubnetRuleTrie().get(addr).subnetTrie.size());

        try {
            /* Use an invalid interface and see whether there is an exception */
            vr.addRoutingRule(null, "netVirt1", null, null,"t2", null, null,
                              null, "if3", null, "permit", "testpool");
            fail();
        } catch (IllegalArgumentException e) {
            /* Exception should be thrown */
        }
    }

    @Test
    public void testIsIfaceDown() {
        vr.createInterface("if1", "t1|netVirt1", null, true);
        vr.createInterface("if2", "t1|netVirt2", null, false);
        vr.createInterface("if3", null, "t2|r2", true);
        vr.createInterface("if4", null, "t2|r3", false);
        boolean down;
        down = vr.isIfaceDown("t1|netVirt1");
        assertEquals(false, down);
        down = vr.isIfaceDown("t1|netVirt2");
        assertEquals(true, down);
        down = vr.isIfaceDown("t2|r2");
        assertEquals(false, down);
        down = vr.isIfaceDown("t2|r3");
        assertEquals(true, down);
        down = vr.isIfaceDown("t2|r4");
        assertEquals(true, down);
    }

    @Test
    public void testGetForwardingAction() throws Exception {
        vr.createInterface("if1", "t1|netVirt1", null, true);
        vr.createInterface("if2", "t1|netVirt2", null, true);
        vr.createInterface("if3", "t1|netVirt3", null, false);
        vr.createInterface("ifs", null, "system|rs", true);
        vr.assignInterfaceAddr("if1", "10.1.1.1", "0.0.0.255");
        vr.assignInterfaceAddr("if2", "10.1.2.1", "0.0.0.255");
        vr.assignInterfaceAddr("if3", "10.1.3.1", "0.0.0.255");

        VRouterImpl vr2 = new VRouterImpl("r2", "t2", vMac1, vRtrManager);
        vr2.createInterface("if1", "t2|netVirt1", null, true);
        vr2.createInterface("if2", "t2|netVirt2", null, true);
        vr2.createInterface("if3", "t2|netVirt3", null, true);
        vr2.createInterface("ifs", null, "system|rs", true);

        /* External tenant router */
        VRouterImpl vrx = new VRouterImpl("rx", "tx", vMac1, vRtrManager);
        vrx.createInterface("if1", "tx|netVirtx", null, true);
        vrx.createInterface("ifs", null, "system|rs", true);

        /* System Router */
        VRouterImpl vrs = new VRouterImpl("rs", "system", vMac1, vRtrManager);
        vrs.createInterface("if1", null, "t1|r1", true);
        vrs.createInterface("if2", null, "t2|r2", true);
        vrs.createInterface("ifx", null, "tx|rx", true);

        VNS netVirtA1 = new VNS("t1|netVirt1");
        VNS netVirtA2 = new VNS("t1|netVirt2");
        VNS netVirtA3 = new VNS("t1|netVirt3");
        VNS netVirtB1 = new VNS("t2|netVirt1");
        VNS netVirtx = new VNS("tx|netVirtx");
        int ip1, ip2;
        ip1 = IPv4.toIPv4Address("10.1.1.2");
        ip2 = IPv4.toIPv4Address("10.1.2.2");
        ForwardingAction action;

        /* There is no routing rule so packet is dropped */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtA2, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", null, null,
                          "0.0.0.0", "255.255.255.255", null, null, "permit");
        /* Test routing between NetVirts on the same tenant */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtA2, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals("t1|netVirt2", action.getDstNetVirtName());
        assertEquals(vMac1.longValue(), action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Since interface if3 is down, the packet is dropped */
        ip2 = IPv4.toIPv4Address("10.1.3.2");
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtA3, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        /* The interface to the system router is picked by default */
        ip2 = IPv4.toIPv4Address("10.2.1.2");
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("system|rs", action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", null, null,
                          "10.2.1.0", "0.0.0.255", null, null, "deny");
        /* The same connection above is denied. Tests whether we pick the
         *  longest prefix match
         */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", null, null,
                          "10.2.1.0", "0.0.0.127", null, null, "permit");
        /* Longer prefix match still */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("system|rs", action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", "t2", null,
                          null, null, null, null, "deny");
        /* Tenant rule is given more priority */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", null,
                          "t2|netVirt1", null, null, null, null, "permit");
        /* NetVirt rule is higher priority */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("system|rs", action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Host rule has higher priority */
        int ip3 = IPv4.toIPv4Address("11.11.11.11");
        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", null, null,
                          "10.2.1.2", "0.0.0.0", "if2", "11.11.11.11",
                          "permit");
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(ip3, action.getNextHopIp());
        assertEquals("t1|netVirt2", action.getDstNetVirtName());
        assertEquals(vMac1.longValue(), action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        vr.addRoutingRule(null, "t1|netVirt2", null, null, null, null,
                          "10.2.1.2", "0.0.0.0", "if2", "11.11.11.11",
                          "permit");
        /* We dont send packet out of the interface it came in */
        ip1 = IPv4.toIPv4Address("10.1.2.2");
        action = vr.getForwardingAction("t1|netVirt2", netVirtA2, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        /* A more traditional routing config */
        vr.addRoutingRule(null, "t1|netVirt2", null, null, "t2", null,
                          null, null, null, null, "permit");
        ip2 = IPv4.toIPv4Address("10.2.1.4");
        action = vr.getForwardingAction("t1|netVirt2", netVirtA2, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("system|rs", action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Try this out in the system router policy */
        vrs.addRoutingRule("t1", null, null, null, "t2", null, null, null, null,
                           null, "permit");
        action = vrs.getForwardingAction("t1|r1", netVirtA2, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("t2|r2", action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Handle the packet at the ingress of t2|r2 */
        vr2.addRoutingRule("t1", null, null, null, null, "t2|netVirt1", null, null,
                           null, null, "permit");
        action = vr2.getForwardingAction("system|rs", netVirtA2, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals("t2|netVirt1", action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Check that an invalid input interface is handled correctly */
        action = vrs.getForwardingAction("system|rs", netVirtA2, ip1, netVirtB1, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        /* Check that the packet is dropped if there isnt any rule found */
        ip1 = IPv4.toIPv4Address("10.2.1.5");
        ip2 = IPv4.toIPv4Address("192.168.0.20");
        action = vr2.getForwardingAction("t2|netVirt1", netVirtB1, ip1, netVirtx, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        vr2.addRoutingRule("t2", null, null, null, null, "t1|netVirt1", null, null,
                           null, null, "permit");
        action = vr2.getForwardingAction("t2|netVirt1", netVirtB1, ip1, netVirtx, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

        vr2.addRoutingRule(null, null, "10.2.1.0", "0.0.0.255", null, null,
                           "192.168.0.0", "0.0.255.255", null, null, "permit");
        action = vr2.getForwardingAction("t2|netVirt1", netVirtB1, ip1, netVirtx, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("system|rs", action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* A more specific subnet match will take precedence */
        vr2.addRoutingRule(null, null, "10.2.1.0", "0.0.0.255", null, null,
                           "192.168.0.0", "0.0.0.255", null, null, "drop");
        action = vr2.getForwardingAction("t2|netVirt1", netVirtB1, ip1, netVirtx, ip2);
        assertEquals(RoutingAction.DROP, action.getAction());

    }

    @Test
    public void testGetForwardingActionSilentHost() throws Exception {
        expect(vRtrManager.findSubnetOwner(EasyMock.anyObject(IPV4Subnet.class))).
        andReturn("t2|r2").times(2);
        expect(vRtrManager.findSubnetOwner(EasyMock.anyObject(IPV4Subnet.class))).
        andReturn(null).once();
        vRtrManager.addSubnetOwner(EasyMock.anyObject(IPV4Subnet.class),
                                   EasyMock.anyObject(String.class));
        expectLastCall().times(3);
        vRtrManager.addIfaceIpMap(EasyMock.anyInt(),
                                  EasyMock.anyObject(VRouterInterface.class));
        expectLastCall().anyTimes();
        replay(vRtrManager);

        vr.createInterface("if1", "t1|netVirt1", null, true);
        vr.createInterface("if2", "t1|netVirt2", null, true);
        vr.createInterface("ifs", null, "system|rs", true);
        vr.assignInterfaceAddr("if1", "10.1.1.1", "0.0.0.255");
        vr.assignInterfaceAddr("if2", "10.1.2.1", "0.0.0.255");

        VRouterImpl vr2 = new VRouterImpl("r2", "t2", vMac1, vRtrManager);
        vr2.createInterface("if1", "t2|netVirt1", null, true);
        vr2.createInterface("if2", "t2|netVirt2", null, true);
        vr2.createInterface("ifs", null, "system|rs", true);
        vr2.assignInterfaceAddr("if1", "10.2.1.1", "0.0.0.255");

        /* External tenant router */
        VRouterImpl vrx = new VRouterImpl("rx", "tx", vMac1, vRtrManager);
        vrx.createInterface("if1", "tx|netVirtx", null, true);
        vrx.createInterface("ifs", null, "system|rs", true);

        /* System Router */
        VRouterImpl vrs = new VRouterImpl("rs", "system", vMac1, vRtrManager);
        vrs.createInterface("if1", null, "t1|r1", true);
        vrs.createInterface("if2", null, "t2|r2", true);
        vrs.createInterface("ifx", null, "tx|rx", true);

        VNS netVirtA1 = new VNS("t1|netVirt1");
        int ip1, ip2, ip3;
        ip1 = IPv4.toIPv4Address("10.1.1.2");
        ip2 = IPv4.toIPv4Address("10.1.2.2");
        ip3 = IPv4.toIPv4Address("10.2.1.2");
        ForwardingAction action;

        /* Allow host 10.1.1.2 to talk to all other hosts */
        vr.addRoutingRule(null, null, "10.1.1.2", "0.0.0.0", null, null,
                          "255.255.255.255", "255.255.255.255", null, null,
                          "permit");
        /* Testing for a silent host. The dest device entity is null */
        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, null, ip2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(ip2, action.getNextHopIp());
        assertEquals("t1|netVirt2", action.getDstNetVirtName());
        assertEquals(vMac1.longValue(), action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        action = vr.getForwardingAction("t1|netVirt1", netVirtA1, ip1, null, ip3);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("system|rs", action.getNextRtrName());
        assertEquals(ip3, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Allow all hosts in NetVirtA1 subnet to talk to any other hosts */
        vrs.addRoutingRule(null, null, "10.1.1.0", "0.0.0.255", null, null,
                           "0.0.0.0", "255.255.255.255", null, null, "permit");
        action = vrs.getForwardingAction("t1|r1", netVirtA1, ip1, null, ip3);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("t2|r2", action.getNextRtrName());
        assertEquals(ip3, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Send a packet to an unknown subnet and see that it is dropped
         * Note that we have set the vRtrManager object to return null for the
         * call to findSubnetOwner after the second time.
         */
        ip3 = IPv4.toIPv4Address("192.168.0.1");
        action = vrs.getForwardingAction("t1|r1", netVirtA1, ip1, null, ip3);
        assertEquals(RoutingAction.DROP, action.getAction());

        verify(vRtrManager);
    }

    /* Test the working of the system router when it is configured to allow all
     * traffic (full mesh)
     */
    @Test
    public void testGetForwardingActionSystemRtr() throws Exception {
        expect(vRtrManager.findSubnetOwner(EasyMock.anyObject(IPV4Subnet.class))).
        andReturn(null).once();
        expect(vRtrManager.findSubnetOwner(EasyMock.anyObject(IPV4Subnet.class))).
        andReturn("B|vrB").times(1);
        replay(vRtrManager);

        /* System Router */
        VRouterImpl vrs = new VRouterImpl("rs", "system", vMac1, vRtrManager);
        vrs.createInterface("if1", null, "A|vrA", true);
        vrs.createInterface("if2", null, "B|vrB", true);
        vrs.createInterface("ifexternal", null, "external|vrX", true);

        VNS netVirtA1 = new VNS("A|netVirtA1");
        int ipA1, ipB1, ipX;
        ipA1 = IPv4.toIPv4Address("10.1.1.2");
        ipB1 = IPv4.toIPv4Address("10.1.2.2");
        ipX = IPv4.toIPv4Address("8.8.8.8");
        ForwardingAction action;

        /* Allow all communication */
        vrs.addRoutingRule(null, null, "10.0.0.0", "255.255.255.255", null,
                           null, "10.0.0.0", "255.255.255.255", null, null,
                           "permit");
        /* Testing for a silent host. The dest device entity is null */
        action = vrs.getForwardingAction("A|vrA", netVirtA1, ipA1, null, ipX);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("external|vrX", action.getNextRtrName());
        assertEquals(ipX, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Inter tenant communication is allowed */
        action = vrs.getForwardingAction("A|vrA", netVirtA1, ipA1, null, ipB1);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("B|vrB", action.getNextRtrName());
        assertEquals(ipB1, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        /* Communication from external tenant is correctly routed */
        VNS netVirtX = new VNS("external|netVirtX");
        action = vrs.getForwardingAction("external|vrX", netVirtX, ipX, netVirtA1,
                                         ipA1);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("A|vrA", action.getNextRtrName());
        assertEquals(ipA1, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());

        verify(vRtrManager);
    }

    /* Test the working of the system router when it is configured to allow only
     * external NetVirt traffic
     */
    @Test
    public void testGetForwardingActionSystemRtr2() throws Exception {
        /* System Router */
        VRouterImpl vrs = new VRouterImpl("rs", "system", vMac1, vRtrManager);
        vrs.createInterface("if1", null, "A|vrA", true);
        vrs.createInterface("if2", null, "B|vrB", true);
        vrs.createInterface("ifexternal", null, "external|vrX", true);

        VNS netVirtA1 = new VNS("A|netVirtA1");
        int ipA1, ipB1, ipX;
        ipA1 = IPv4.toIPv4Address("10.1.1.2");
        ipB1 = IPv4.toIPv4Address("10.1.2.2");
        ipX = IPv4.toIPv4Address("8.8.8.8");
        ForwardingAction action;

        /* any to any go to external router */
        vrs.addRoutingRule(null, null, "10.0.0.0", "255.255.255.255", null,
                           null, "10.0.0.0", "255.255.255.255", "ifexternal",
                           null, "permit");
        /* External to any allow */
        vrs.addRoutingRule("external", null, null, null, null, null, "10.0.0.0",
                           "255.255.255.255", null, null, "permit");

        /* Testing for a silent host. The dest device entity is null */
        action = vrs.getForwardingAction("A|vrA", netVirtA1, ipA1, null, ipX);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("external|vrX", action.getNextRtrName());
        assertEquals(ipX, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());

        /* Inter tenant communication is sent to external router as well */
        action = vrs.getForwardingAction("A|vrA", netVirtA1, ipA1, null, ipB1);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("external|vrX", action.getNextRtrName());
        assertEquals(ipB1, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());

        /* Communication from external tenant is correctly routed */
        VNS netVirtX = new VNS("external|netVirtX");
        action = vrs.getForwardingAction("external|vrX", netVirtX, ipX, netVirtA1,
                                         ipA1);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals("A|vrA", action.getNextRtrName());
        assertEquals(ipA1, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals(0, action.getNewSrcMac());
        assertEquals(null, action.getNextHopGatewayPool());
        assertEquals(null, action.getNextHopGatewayPoolRouter());
    }

    /* Test the working of the router when it is configured with a rule having
     * a next hop IP
     */
    @Test
    public void testGetForwardingActionNHIp() throws Exception {
        vr.createInterface("if1", "A|netVirtA1", null, true);
        vr.createInterface("if2", "A|netVirtA2", null, true);
        vr.createInterface("ifs", null, "system|rs", true);
        vr.assignInterfaceAddr("if1", "10.1.1.1", "0.0.0.255");
        vr.assignInterfaceAddr("if2", "10.1.2.1", "0.0.0.255");

        String ipA1str, ipA2str, ipnhstr, ipXstr;
        ipA1str = "10.1.1.2";
        ipA2str = "10.1.2.12";
        ipnhstr = "10.1.2.2";
        ipXstr = "192.168.1.1";
        int ipA1, ipA2, ipnh, ipX;
        ipA1 = IPv4.toIPv4Address(ipA1str);
        ipA2 = IPv4.toIPv4Address(ipA2str);
        ipnh = IPv4.toIPv4Address(ipnhstr);
        ipX = IPv4.toIPv4Address(ipXstr);

        ForwardingAction action;
        VNS netVirtA1 = new VNS("A|netVirtA1");
        VNS netVirtA2 = new VNS("A|netVirtA2");
        VNS netVirtX = new VNS("X|netVirtX");

        /* Tenant A to any send to next hop ip */
        vr.addRoutingRule("A", null, null, null, null, null, "10.0.0.0",
                          "255.255.255.255", null, ipnhstr, "permit");
        /* Next hop is in the same NetVirt as the dest NetVirt */
        action = vr.getForwardingAction("A|netVirtA1", netVirtA1, ipA1, netVirtA2, ipA2);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(ipnh, action.getNextHopIp());
        assertEquals("A|netVirtA2", action.getDstNetVirtName());
        assertEquals(vMac1.longValue(), action.getNewSrcMac());

        /* Next hop is in a different NetVirt as the dest NetVirt */
        action = vr.getForwardingAction("A|netVirtA1", netVirtA1, ipA1, netVirtX, ipX);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(ipnh, action.getNextHopIp());
        assertEquals("A|netVirtA2", action.getDstNetVirtName());
        assertEquals(vMac1.longValue(), action.getNewSrcMac());

        /* The next hop is not connected to any subnet of the router */
        /* netVirtA1 to any send to ipX */
        vr.addRoutingRule(null, "A|netVirtA1", null, null, null, null, "10.0.0.0",
                          "255.255.255.255", null, ipXstr, "permit");
        action = vr.getForwardingAction("A|netVirtA1", netVirtA1, ipA1, netVirtA2, ipA2);
        assertEquals(RoutingAction.DROP, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(0, action.getNextHopIp());
        assertEquals(null, action.getDstNetVirtName());
        assertEquals((long) 0, action.getNewSrcMac());
    }

    @Test
    public void testGetForwardingActionNextHopGatewayPool() {
        /* External router belonging to an external tenant */
        VRouterImpl vrx = new VRouterImpl("rx", "tx", vMac1, vRtrManager);
        vrx.createInterface("if-external", "tx|netVirtx", null, true);
        vrx.createInterface("if-system", null, "rs", true);

        /* NetVirt attached to the tenant "t1" */
        VNS netVirtA1 = new VNS("t1|netVirt1");

        /* Source IP of the packet */
        int ip1 = IPv4.toIPv4Address("10.0.0.1");

        /* NetVirt attached to the external tenant */
        VNS netVirtX1 = new VNS("tx|netVirtx");

        /* Destination IP of the packet */
        int ipx = IPv4.toIPv4Address("8.8.8.8");

        /*
         * Setup a routing rule to permit traffic from
         * tenant|netVirt = "t1"|"netVirt1" to tenant|netVirt = "tx|netVirtx" and send it to
         * the gateway pool "test-gateway-pool"
         */
        vrx.addRoutingRule("t1", "t1|netVirt1", null, null, "tx",
                          "tx|netVirtx", null, null, "if-external", null, "permit",
                          "test-gateway-pool");
        /*
         * Now, invoke getForwardingAction on the virtual router object
         * corresponding to the external virtual router to determine
         * the forwarding action for traffic from tenant("t1")|netVirt("netVirt1")
         * to the tenant("tx")|netVirt("netVirtx")
         */
        ForwardingAction action = vrx.getForwardingAction("rs",
                                                          netVirtA1, ip1, netVirtX1,
                                                          ipx);
        assertEquals(RoutingAction.FORWARD, action.getAction());
        assertEquals(null, action.getNextRtrName());
        assertEquals(0, action.getNextHopIp());
        assertEquals("tx|netVirtx", action.getDstNetVirtName());
        assertEquals((long)0, action.getNewSrcMac());
        assertEquals("test-gateway-pool", action.getNextHopGatewayPool());
        assertEquals(vrx, action.getNextHopGatewayPoolRouter());
    }

    @Test
    public void testGetVMac() throws Exception {
        vr.createInterface("if1", "t1|netVirt1", null, true);
        vr.createInterface("if2", "t1|netVirt2", null, true);
        vr.createInterface("if3", "t1|netVirt3", null, false);
        vr.createInterface("ifs", null, "system|rs", true);
        vr.assignInterfaceAddr("if1", "10.1.1.1", "255.255.255.0");
        vr.assignInterfaceAddr("if1", "10.2.1.1", "255.255.255.0");
        vr.assignInterfaceAddr("if2", "10.1.2.1", "255.255.255.0");
        vr.assignInterfaceAddr("if3", "10.1.3.1", "255.255.255.0");
        vr.assignInterfaceAddr("ifs", "192.168.1.1", "255.255.255.0");

        int ip = IPv4.toIPv4Address("10.1.1.1");
        long vMac = vr.getVMac("t1|netVirt1", ip);
        long expectedVMac = Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55"));
        assertEquals(expectedVMac, vMac);

        ip = IPv4.toIPv4Address("10.2.1.1");
        vMac = vr.getVMac("t1|netVirt1", ip);
        assertEquals(expectedVMac, vMac);

        ip = IPv4.toIPv4Address("10.1.3.1");
        vMac = vr.getVMac("t1|netVirt3", ip);
        assertEquals(expectedVMac, vMac);

        ip = IPv4.toIPv4Address("192.168.1.1");
        vMac = vr.getVMac("system|rs", ip);
        assertEquals(expectedVMac, vMac);

        ip = IPv4.toIPv4Address("10.2.1.1");
        vMac = vr.getVMac("netVirt1", ip);
        assertEquals(0, vMac);

        ip = IPv4.toIPv4Address("10.2.1.2");
        vMac = vr.getVMac("t1|netVirt1", ip);
        assertEquals(0, vMac);
    }

    @Test
    public void testGetRtrIp() throws Exception {
        String if1ip1str = "10.1.1.1";
        String if1ip2str = "10.2.1.1";
        String if2ipstr = "10.1.2.1";
        String if3ipstr = "10.1.3.1";
        String ifsipstr = "192.168.1.1";
        int if1ip1 = IPv4.toIPv4Address(if1ip1str);
        int if1ip2 = IPv4.toIPv4Address(if1ip2str);
        vr.createInterface("if1", "t1|netVirt1", null, true);
        vr.createInterface("if2", "t1|netVirt2", null, true);
        vr.createInterface("if3", "t1|netVirt3", null, false);
        vr.createInterface("ifs", null, "system|rs", true);
        vr.assignInterfaceAddr("if1", if1ip1str, "0.0.0.255");
        vr.assignInterfaceAddr("if1", if1ip2str, "0.0.0.255");
        vr.assignInterfaceAddr("if2", if2ipstr, "0.0.0.255");
        vr.assignInterfaceAddr("if3", if3ipstr, "0.0.0.255");
        vr.assignInterfaceAddr("ifs", ifsipstr, "0.0.0.255");


        int ip, retIp;
        /* The IP belongs to the first subnet of if1 */
        ip = IPv4.toIPv4Address("10.1.1.3");
        retIp = vr.getRtrIp("t1|netVirt1", ip);
        assertEquals(if1ip1, retIp);

        /* The IP belongs to the second subnet of if1 */
        ip = IPv4.toIPv4Address("10.2.1.3");
        retIp = vr.getRtrIp("t1|netVirt1", ip);
        assertEquals(if1ip2, retIp);

        /* The IP does not belong to any subnet of the router */
        ip = IPv4.toIPv4Address("10.10.2.3");
        retIp = vr.getRtrIp("t1|netVirt1", ip);
        assertEquals(0, retIp);

        /* The NetVirt is not attached to the router */
        ip = IPv4.toIPv4Address("10.1.1.3");
        retIp = vr.getRtrIp("t1|netVirtX", ip);
        assertEquals(0, retIp);
    }

    /*
     * Note: The following tests on the gateway pool/node related APIs in
     * VirutalRouterImpl class ensure only that the IGatewayPool API usage is
     * correct. It does not cover in detail the test cases for the IGatewayPool
     * APIs. Please refer GatewayPoolTest.java for detailed tests on the
     * IGatewayPool interface.
     */
    @Test
    public void testCreateGatewayPool() {
        String gatewayPoolName1 = "testGatewayPool1";
        vr.createGatewayPool(gatewayPoolName1);
        GatewayPoolImpl gatewayPool1 = vr.getGatewayPool(gatewayPoolName1);
        assertNotNull(gatewayPool1);
        assertEquals("testGatewayPool1", gatewayPool1.getName());

        String gatewayPoolName2 = "testGatewayPool2";
        vr.createGatewayPool(gatewayPoolName2);
        GatewayPoolImpl gatewayPool2 = vr.getGatewayPool(gatewayPoolName2);
        assertNotNull(gatewayPool2);
        assertEquals("testGatewayPool2", gatewayPool2.getName());
    }

    @Test
    public void testAddGatewayNode() {
        String gatewayPoolName = "testGatewayPool";
        vr.createGatewayPool(gatewayPoolName);
        vr.addGatewayPoolNode(gatewayPoolName, "10.0.0.1");
        vr.addGatewayPoolNode(gatewayPoolName, "10.0.0.2");

        GatewayPoolImpl gatewayPool = vr.getGatewayPool(gatewayPoolName);
        assertNotNull(gatewayPool);
        assertEquals("testGatewayPool", gatewayPool.getName());

        Map<String, GatewayNode> nodes = gatewayPool.getGatewayNodes();
        assertNotNull(nodes);
        assertEquals(2, nodes.size());
        GatewayNode node1 = nodes.get("10.0.0.1");
        assertEquals("10.0.0.1", IPv4.fromIPv4Address(node1.getIp()));
        GatewayNode node2 = nodes.get("10.0.0.2");
        assertEquals("10.0.0.2", IPv4.fromIPv4Address(node2.getIp()));
    }

    @Test
    public void testAddGatewayNodeInvalidPool() throws Exception {
        try {
            vr.addGatewayPoolNode("nonExistentGatewayPool", "10.0.0.1");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    @Test
    public void getOptimalGatewayNodeIPInvalidPool() throws Exception {
        try {
            vr.getOptimalGatewayNodeInfo("nonExistentGatewayPool", null, (short)1);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }
}
