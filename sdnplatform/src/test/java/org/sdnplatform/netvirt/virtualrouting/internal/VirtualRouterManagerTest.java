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

import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.Device;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction;
import org.sdnplatform.netvirt.virtualrouting.GatewayNode;
import org.sdnplatform.netvirt.virtualrouting.IGatewayPool;
import org.sdnplatform.netvirt.virtualrouting.IVirtualMacService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouterManager;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouterManager.RoutingRuleParams;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.util.IPV4Subnet;
import org.sdnplatform.util.MACAddress;


public class VirtualRouterManagerTest extends PlatformTestCase {
    protected VirtualRouterManager vrm;
    protected IVirtualMacService vMacService;
    protected IRewriteService rewriteService;
    protected IDeviceService deviceManager;
    protected INetVirtManagerService netVirtManager;
    protected ILinkDiscoveryService linkDiscovery;
    protected ITopologyService topology;
    protected IRoutingService routingEngine;
    private ITunnelManagerService tunnelManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        vMacService = createMock(IVirtualMacService.class);
        rewriteService = createMock(IRewriteService.class);
        deviceManager = createMock(IDeviceService.class);
        netVirtManager = createMock(INetVirtManagerService.class);
        linkDiscovery = createMock(ILinkDiscoveryService.class);
        topology = createNiceMock(ITopologyService.class);
        routingEngine = createNiceMock(IRoutingService.class);
        tunnelManager = createMock(ITunnelManagerService.class);

        vrm = new VirtualRouterManager();
        vrm.setvMacManager(vMacService);
        vrm.setRewriteService(rewriteService);
        vrm.setNetVirtManager(netVirtManager);
        vrm.setDeviceManager(deviceManager);
        vrm.setLinkDiscovery(linkDiscovery);
        vrm.setTopology(topology);
        vrm.setRoutingService(routingEngine);
        vrm.setTunnelManager(tunnelManager);
        Map<Integer, MACAddress> staticArpMap =
                new HashMap<Integer, MACAddress>();
        vrm.setStaticArpTable(staticArpMap);
    }

    @Test
    /* This test only tests internal working of the class */
    public void testCreateTenant() {
        vrm.createTenant("t1", true);
        assertNotNull(vrm.tenants.get("t1"));
        assertTrue(vrm.tenants.get("t1").active);
        assertEquals(vrm.tenants.get("t1").name, "t1");
        vrm.createTenant("t2", false);
        assertNotNull(vrm.tenants.get("t2"));
        assertFalse(vrm.tenants.get("t2").active);
        assertEquals(vrm.tenants.get("t2").name, "t2");
        assertEquals(2, vrm.tenants.size());
    }

    @Test
    /* This test only tests internal working of the class */
    public void testCreateVirtualRouter() throws Exception {
        linkDiscovery.
        addMACToIgnoreList(VirtualRouterManager.VIRTUAL_ROUTING_MAC, 0);
        expectLastCall().times(1);
        replay(linkDiscovery);
        vrm.createTenant("t1", true);
        vrm.createVirtualRouter("r1", "t1");
        assertNotNull(vrm.vRouters.get("t1|r1"));
        assertEquals("t1", vrm.vRouters.get("t1|r1").getTenant());
        assertEquals("t1|r1", vrm.vRouters.get("t1|r1").getName());
        try {
            /* Attempting to add a router to a non existing tenant should throw
             * an exception
             */
            vrm.createVirtualRouter("r2", "t2");
            fail();
        } catch (IllegalArgumentException e) {
            /* Exception should be thrown */
        }
        assertNull(vrm.vRouters.get("t2|r2"));
        verify(linkDiscovery);
    }

    @Test
    /* This test only tests internal working of the class */
    public void testAddVRouterIface() throws Exception {
        linkDiscovery.
        addMACToIgnoreList(VirtualRouterManager.VIRTUAL_ROUTING_MAC, 0);
        expectLastCall().times(2);
        replay(linkDiscovery);
        vrm.createTenant("t1", true);
        vrm.createTenant("t2", true);
        long vMac1 = Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55"));
        expect(vMacService.acquireVirtualMac()).andReturn(vMac1).anyTimes();
        replay(vMacService);
        vrm.createVirtualRouter("r1", "t1");
        vrm.createVirtualRouter("r2", "t2");
        verify(linkDiscovery);

        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        assertNotNull(vrm.netVirtToRouterMap.get("t1|netVirt1"));

        vrm.addVRouterIface("t1|r1", "if2", null, "t2|r2", true);
        assertEquals(1, vrm.netVirtToRouterMap.size());

        try {
            vrm.addVRouterIface("r1", "if3", "t1|netVirt2", null, true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid owner router name */
        }
        try {
            vrm.addVRouterIface("t2|r1", "if3", "t1|netVirt2", null, true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid owner tenant name */
        }
        try {
            vrm.addVRouterIface("t1|r3", "if3", "t1|netVirt2", null, true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown owner router name */
        }
        try {
            vrm.addVRouterIface("t1|r1", "if3", "t1|netVirt2", "t1|r2", true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Cannot connect an interface to both a netVirt and a router */
        }
        try {
            vrm.addVRouterIface("t1|r1", "if3", "netVirt2", null, true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid netVirt name */
        }
        try {
            vrm.addVRouterIface("t1|r1", "if3", "t2|netVirt2", null, true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid tenant in netVirt name */
        }
        try {
            vrm.addVRouterIface("t1|r1", "if3", null, "r2", true);
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid router name */
        }
        try {
            vrm.addVRouterIface("t1|r1", "if3", null, null, false);
            fail();
        } catch (IllegalArgumentException e) {
            /* Both router and netVirt cannot be null */
        }
    }

    @Test
    /* This test only tests internal working of the class */
    public void testAddIfaceIp() throws Exception {
        vrm.createTenant("t1", true);
        vrm.createVirtualRouter("r1", "t1");
        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        vrm.addIfaceIp("t1|r1|if1", "10.0.1.1", "0.0.255.255");
        try {
            vrm.addIfaceIp("r1|if1", "10.20.2.2", "0.0.0.255");
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid owner interface name */
        }
        try {
            vrm.addIfaceIp("t2|r1|if1", "10.20.2.2", "0.0.0.255");
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown tenant name */
        }
        try {
            vrm.addIfaceIp("t1|r2|if1", "10.20.2.2", "0.0.0.255");
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown router name */
        }
    }

    @Test
    public void testAddGatewayPool() throws Exception {
        vrm.createTenant("tx", true);
        vrm.createVirtualRouter("rx", "tx");
        vrm.addGatewayPool("tx|rx", "test-gateway-pool");
        try {
            vrm.addGatewayPool("tx|rinvalid", "test-gateway-pool");
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid router name */
        }
        IGatewayPool gatewayPool = vrm.getGatewayPool("tx|rx",
                                                      "test-gateway-pool");
        assertNotNull(gatewayPool);
        assertEquals("test-gateway-pool", gatewayPool.getName());
        Map<String, GatewayNode> gatewayNodes = gatewayPool.getGatewayNodes();
        assertEquals(0, gatewayNodes.size());
    }

    @Test
    public void testAddGatewayNodes() throws Exception {
        vrm.createTenant("tx", true);
        vrm.createVirtualRouter("rx", "tx");
        vrm.addGatewayPool("tx|rx", "test-gateway-pool");

        IGatewayPool gatewayPool = vrm.getGatewayPool("tx|rx",
                                                      "test-gateway-pool");
        assertNotNull(gatewayPool);
        assertEquals("test-gateway-pool", gatewayPool.getName());
        Map<String, GatewayNode> gatewayNodes = gatewayPool.getGatewayNodes();
        assertEquals(0, gatewayNodes.size());

        gatewayPool.addGatewayNode("10.0.0.1");
        gatewayNodes = gatewayPool.getGatewayNodes();
        assertEquals(1, gatewayNodes.size());

        assertNotNull(gatewayNodes.get("10.0.0.1"));
    }

    private RoutingRuleParams createRuleParams(String owner, String srcTenant,
                                               String srcNetVirt, String srcIp,
                                               String srcMask, String dstTenant,
                                               String dstNetVirt, String dstIp,
                                               String dstMask, String outIface,
                                               String nextHopIp,
                                               String action) {
        RoutingRuleParams p = new RoutingRuleParams();
        p.owner = owner;
        p.srcTenant = srcTenant;
        p.srcVNS = srcNetVirt;
        p.srcIp = srcIp;
        p.srcMask = srcMask;
        p.dstTenant = dstTenant;
        p.dstVNS = dstNetVirt;
        p.dstIp = dstIp;
        p.dstMask = dstMask;
        p.outIface = outIface;
        p.nextHopIp = nextHopIp;
        p.action = action;
        return p;
    }

    private RoutingRuleParams createRuleParamsWithNextHopGatewayPool(
                                               String owner, String srcTenant,
                                               String srcNetVirt, String srcIp,
                                               String srcMask, String dstTenant,
                                               String dstNetVirt, String dstIp,
                                               String dstMask, String outIface,
                                               String nextHopIp,
                                               String action,
                                               String nextHopGatewayPool) {
        RoutingRuleParams p = new RoutingRuleParams();
        p.owner = owner;
        p.srcTenant = srcTenant;
        p.srcVNS = srcNetVirt;
        p.srcIp = srcIp;
        p.srcMask = srcMask;
        p.dstTenant = dstTenant;
        p.dstVNS = dstNetVirt;
        p.dstIp = dstIp;
        p.dstMask = dstMask;
        p.outIface = outIface;
        p.nextHopIp = nextHopIp;
        p.action = action;
        p.nextHopGatewayPool = nextHopGatewayPool;
        return p;
    }

    @Test
    /* This test only tests internal working of the class */
    public void testAddRoutingRule() throws Exception {
        vrm.createTenant("t1", true);
        vrm.createVirtualRouter("r1", "t1");
        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        vrm.addVRouterIface("t1|r1", "if2", "t1|netVirt2", null, true);
        vrm.addRoutingRule(createRuleParamsWithNextHopGatewayPool("t1|r1", null,
                                            "t1|netVirt1", null,
                                            null, null, "t1|netVirt2", null, null,
                                            "t1|r1|if1", null, "permit",
                                            "t1|r1|test-gateway-pool"));
        try {
            vrm.addRoutingRule(createRuleParams("r1", null, "t1|netVirt1", null,
                                                null, null, "t1|netVirt2", null,
                                                null, "t1|r1|if1", null,
                                                "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid owner router name */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t2|r1", null, "t1|netVirt1", null,
                                                null, null, "t1|netVirt2", null,
                                                null, "t1|r1|if1", null,
                                                "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown tenant name */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r2", null, "t1|netVirt1", null,
                                                null, null, "t1|netVirt2", null,
                                                null, "t1|r1|if1", null,
                                                "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown router name */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt1", null,
                                                null, null, "t1|netVirt2", null,
                                                null, "r1|if1", null,
                                                "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid outgoing interface name */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt1", null,
                                                null, null, "t1|netVirt2", null,
                                                null, "t2|r1|if1", null,
                                                "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid tenant in outgoing interface name */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt1", null,
                                                null, null, "t1|netVirt2", null,
                                                null, "t1|r2|if1", null,
                                                "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Invalid router in outgoing interface name */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", "t2", null, null, null,
                                                null, "t1|netVirt2", null, null,
                                                "t1|r2|if1", null, "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown srcTenant */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", "t1", null, null, null,
                                                "t2", null, null, null,
                                                "t1|r2|if1", null, "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* Unknown dstTenant */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt3", null,
                                                null, "t1", null, null, null,
                                                "t1|r2|if1", null, "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* src NetVirt not attached to any router */
        }
        try {
            vrm.addRoutingRule(createRuleParams("t1|r1", "t1", null, null, null,
                                                null, "t1|netVirt3", null, null,
                                                "t1|r2|if1", null, "permit"));
            fail();
        } catch (IllegalArgumentException e) {
            /* dst NetVirt not attached to any router */
        }
    }

    @Test
    public void testConnected() throws Exception {
        vrm.createTenant("t1", true);
        vrm.createTenant("t2", true);
        vrm.createTenant("system", true);
        vrm.createTenant("tx", true);

        long vMac1 = Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55"));
        expect(vMacService.acquireVirtualMac()).andReturn(vMac1).anyTimes();
        replay(vMacService);
        vrm.createVirtualRouter("r1", "t1");
        vrm.createVirtualRouter("r2", "t2");
        vrm.createVirtualRouter("rs", "system");
        vrm.createVirtualRouter("rx", "tx");
        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        vrm.addVRouterIface("t1|r1", "if2", "t1|netVirt2", null, true);
        vrm.addVRouterIface("t1|r1", "ifs", null, "system|rs", true);
        vrm.addVRouterIface("t2|r2", "if1", "t2|netVirt1", null, true);
        vrm.addVRouterIface("t2|r2", "if2", "t2|netVirt2", null, false);
        vrm.addVRouterIface("t2|r2", "ifs", null, "system|rs", true);
        vrm.addVRouterIface("system|rs", "if1", null, "t1|r1", true);
        vrm.addVRouterIface("system|rs", "if2", null, "t2|r2", true);
        vrm.addVRouterIface("system|rs", "ifx", null, "tx|rx", true);
        vrm.addVRouterIface("tx|rx", "if1", "tx|netVirtx", null, true);
        vrm.addVRouterIface("tx|rx", "ifs", null, "system|rs", false);
        vrm.addRoutingRule(createRuleParams("t1|r1", "t1", null, null, null,
                                            null, null, "0.0.0.0",
                                            "255.255.255.255", null, null,
                                            "permit"));
        vrm.addRoutingRule(createRuleParams("system|rs", "t1", null, null, null,
                                            "t2", null, null, null, null, null,
                                            "permit"));
        vrm.addRoutingRule(createRuleParams("t2|r2", "t1", null, null, null,
                                            null, "t2|netVirt1", null, null, null,
                                            null, "permit"));
        int ip1 = IPv4.toIPv4Address("10.1.1.2");
        int ip2 = IPv4.toIPv4Address("10.2.1.2");
        VNS netVirtA1 = new VNS("t1|netVirt1");
        VNS netVirtB1 = new VNS("t2|netVirt1");
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtB1, null, null));

        boolean found = vrm.connected(srcIfaces, dstIfaces, ip1, ip2);
        /* A route from t1|netVirt1 to t2|netVirt1 through system|rs so expect 'true' */
        assertEquals(true, found);

        ip2 = IPv4.toIPv4Address("10.2.2.2");
        VNS netVirtB2 = new VNS("t2|netVirt2");
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtB2, null, null));

        found = vrm.connected(srcIfaces, dstIfaces, ip1, ip2);
        /* Interface to netVirtB2 is disabled so vRouter would return drop */
        assertEquals(false, found);

        vrm.addRoutingRule(createRuleParams("system|rs", "t1", null, null, null,
                                            "tx", null, null, null, null, null,
                                            "permit"));
        ip2 = IPv4.toIPv4Address("192.168.0.20");
        VNS netVirtx = new VNS("tx|netVirtx");
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtx, null, null));
        found = vrm.connected(srcIfaces, dstIfaces, ip1, ip2);
        /* Interface on rx from rs is down so return drop */
        assertEquals(false, found);

        ip1 = IPv4.toIPv4Address("10.2.2.20");
        ip2 = IPv4.toIPv4Address("10.1.2.20");
        srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtB2, null, null));
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA1, null, null));
        found = vrm.connected(srcIfaces, dstIfaces, ip1, ip2);
        /* Interface connecting netVirtB2 to r2 is down so return drop */
        assertEquals(false, found);

        ip1 = IPv4.toIPv4Address("10.1.1.20");
        ip2 = IPv4.toIPv4Address("10.1.1.25");
        srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA1, null, null));
        found = vrm.connected(srcIfaces, dstIfaces, ip1, ip2);
        /* Both source and dest are in the same netVirt so return true */
        assertEquals(true, found);

        ip2 = IPv4.toIPv4Address("10.1.5.20");
        VNS netVirtA5 = new VNS("t1|netVirt5");
        srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA5, null, null));
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA1, null, null));
        found = vrm.connected(srcIfaces, dstIfaces, ip1, ip2);
        /* Source netVirt is not connected to router */
        assertEquals(false, found);
        verify(vMacService);
    }

    public void testGetForwardingAction() throws Exception {
        long srcMAC = Ethernet.
                toLong(Ethernet.toMACAddress("00:11:22:33:44:55"));
        long dstMAC = Ethernet.
                toLong(Ethernet.toMACAddress("55:44:33:22:11:00"));
        long nextHopMAC = Ethernet.
                toLong(Ethernet.toMACAddress("bb:ee:55:77:00:11"));
        short vlan = 20;
        long vMac = VirtualRouterManager.VIRTUAL_ROUTING_MAC;
        int srcIp = IPv4.toIPv4Address("10.1.1.20");
        int dstIp = IPv4.toIPv4Address("10.1.2.30");
        int dstIp2 = IPv4.toIPv4Address("10.1.2.35");
        String nextHopIpStr = "10.1.2.25";
        int nextHopIp = IPv4.toIPv4Address(nextHopIpStr);
        VNS netVirtA1 = new VNS("t1|netVirt1");
        VNS netVirtA2 = new VNS("t1|netVirt2");
        VNS netVirtA3 = new VNS("t1|netVirt3");
        VNS netVirtA4 = new VNS("t1|netVirt4");

        ListenerContext cntx = new ListenerContext();

        Device srcDevice = createMock(Device.class);
        Device dstDevice = createMock(Device.class);
        Device nextHopDevice = createMock(Device.class);
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        List<VNSInterface> nextHopIfaces = new ArrayList<VNSInterface>();
        ArrayList<Device> dstList = new ArrayList<Device>();
        dstList.add(dstDevice);
        Iterator<Device> dstIter = dstList.iterator();
        ArrayList<Device> srcList = new ArrayList<Device>();
        srcList.add(srcDevice);
        Iterator<Device> srcIter = srcList.iterator();
        ArrayList<Device> nextHopList = new ArrayList<Device>();
        nextHopList.add(nextHopDevice);
        Iterator<Device> nextHopIter = nextHopList.iterator();

        Integer[] srcIpArr = new Integer[1];
        srcIpArr[0] = srcIp;
        Integer[] dstIpArr = new Integer[1];
        dstIpArr[0] = dstIp;
        Integer[] nextHopIpArr = new Integer[1];
        nextHopIpArr[0] = nextHopIp;
        srcDevice.getEntityClass();
        expectLastCall().andReturn(null).anyTimes();
        expect(deviceManager.findClassDevice(null, dstMAC, vlan, dstIp)).
                andReturn(dstDevice).anyTimes();
        expect(deviceManager.findClassDevice(null, nextHopMAC, vlan, nextHopIp)).
                andReturn(nextHopDevice).anyTimes();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
                queryClassDevices(null, null, null, new Integer(dstIp), null, null)).
                andReturn(dstIter).anyTimes();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
                queryClassDevices(null, null, null, new Integer(dstIp2), null, null)).
                andReturn(dstIter).anyTimes();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
                queryClassDevices(null, null, null, new Integer(srcIp), null, null)).
                andReturn(srcIter).anyTimes();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
                queryClassDevices(null, null, null, new Integer(nextHopIp), null, null)).
                andReturn(nextHopIter).anyTimes();
        expect(dstDevice.getMACAddress()).andReturn(dstMAC).anyTimes();
        expect(srcDevice.getMACAddress()).andReturn(srcMAC).anyTimes();
        expect(nextHopDevice.getMACAddress()).andReturn(nextHopMAC).anyTimes();
        expect(dstDevice.getIPv4Addresses()).andReturn(dstIpArr).anyTimes();
        expect(srcDevice.getIPv4Addresses()).andReturn(srcIpArr).anyTimes();
        expect(nextHopDevice.getIPv4Addresses()).andReturn(nextHopIpArr).anyTimes();
        expect(vMacService.acquireVirtualMac()).andReturn(vMac).anyTimes();
        expect(netVirtManager.getInterfaces(dstDevice)).
                andReturn(dstIfaces).anyTimes();
        expect(netVirtManager.getInterfaces(srcDevice)).
                andReturn(srcIfaces).anyTimes();
        expect(netVirtManager.getInterfaces(nextHopDevice)).
                andReturn(nextHopIfaces).anyTimes();
        expect(netVirtManager.getVNS("t1|netVirt1")).andReturn(netVirtA1).anyTimes();
        expect(netVirtManager.getVNS("t1|netVirt2")).andReturn(netVirtA2).anyTimes();
        expect(netVirtManager.getVNS("t1|netVirt3")).andReturn(netVirtA3).anyTimes();
        expect(netVirtManager.getVNS("t1|netVirt4")).andReturn(netVirtA4).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                            .andReturn(false).anyTimes();
        replay(tunnelManager);

        /* Simulate the rewrite manager expectations according to the order of
         * the tests
         */
        /* TEST4 */
        rewriteService.setIngressDstMac(EasyMock.eq(new Long(vMac)),
                                        EasyMock.eq(new Long(dstMAC)),
                                        (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        rewriteService.setTtlDecrement(EasyMock.eq(1),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        /* TEST5 */
        rewriteService.setIngressDstMac(EasyMock.eq(new Long(vMac)),
                                        EasyMock.eq(new Long(dstMAC)),
                                        (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        rewriteService.setTtlDecrement(EasyMock.eq(1),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        /* TEST6 */
        rewriteService.setIngressDstMac(EasyMock.eq(new Long(vMac)),
                                        EasyMock.eq(new Long(nextHopMAC)),
                                        (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        rewriteService.setTtlDecrement(EasyMock.eq(1),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        rewriteService.setEgressSrcMac(EasyMock.eq(new Long(srcMAC)),
                                       EasyMock.eq(new Long(vMac)),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        /* TEST7 */
        rewriteService.setIngressDstMac(EasyMock.eq(new Long(vMac)),
                                        EasyMock.eq(new Long(nextHopMAC)),
                                        (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        rewriteService.setTtlDecrement(EasyMock.eq(1),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        rewriteService.setEgressSrcMac(EasyMock.eq(new Long(srcMAC)),
                                       EasyMock.eq(new Long(vMac)),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        /* TEST8 */
        rewriteService.setEgressSrcMac(EasyMock.eq(new Long(srcMAC)),
                                       EasyMock.eq(new Long(vMac)),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().once();
        replay(srcDevice, dstDevice, nextHopDevice, deviceManager, vMacService,
               netVirtManager, rewriteService);

        /* TEST1: No common NetVirt and virtual routing not setup so return drop */
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                   dstDevice);
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA2, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        ForwardingAction fAction = vrm.getForwardingAction(srcMAC, dstMAC, vlan,
                                                           Ethernet.TYPE_IPv4,
                                                           srcIp, dstIp, cntx);
        assertEquals(RoutingAction.DROP, fAction.getAction());
        String fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        List<VNSInterface> retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        List<VNSInterface> retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertNull(retSrcIfaces);
        assertNull(retDstIfaces);

        /* TEST2: Source and dest are in same NetVirts, so action returned is
         * forward
         */
        cntx = new ListenerContext();
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                   dstDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        srcIfaces.add(new VNSInterface("testSrcIface2", netVirtA3, null, null));
        dstIfaces.clear();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA2, null, null));
        dstIfaces.add(new VNSInterface("testDstIface2", netVirtA3, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        fAction = vrm.getForwardingAction(srcMAC, dstMAC, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(false, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        /* The common netVirt is encapsulated in the flow cache app name */
        assertEquals("t1|netVirt3", fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertEquals(netVirtA3, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA3, retDstIfaces.get(0).getParentVNS());

        /* TEST3: Virtual routing has been setup so allow communcation */
        /* Connect NetVirt A1/A2/A3 with virtual routing */
        vrm.createTenant("t1", true);
        vrm.createVirtualRouter("r1", "t1");
        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        vrm.addVRouterIface("t1|r1", "if2", "t1|netVirt2", null, true);
        vrm.addVRouterIface("t1|r1", "if3", "t1|netVirt3", null, true);
        /* Allow all packets from tenant A */
        vrm.addRoutingRule(createRuleParams("t1|r1", "t1", null, null, null,
                                            null, null, "0.0.0.0",
                                            "255.255.255.255", null, null,
                                            "permit"));

        cntx = new ListenerContext();
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                   dstDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        srcIfaces.add(new VNSInterface("testSrcIface2", netVirtA2, null, null));
        dstIfaces.clear();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA3, null, null));
        dstIfaces.add(new VNSInterface("testDstIface2", netVirtA4, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        fAction = vrm.getForwardingAction(srcMAC, dstMAC, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertEquals(netVirtA1, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA3, retDstIfaces.get(0).getParentVNS());

        /* TEST4: Send packet with dstMac as virtual router MAC. There is a
         * static ARP entry for the dest device using which the device is
         * found
         */
        Map<Integer, MACAddress> staticArpMap =
                new HashMap<Integer, MACAddress>();
        /* Add the dest IP to the static ARP table */
        staticArpMap.put(new Integer(dstIp),
                         new MACAddress(Ethernet.toByteArray(dstMAC)));
        vrm.setStaticArpTable(staticArpMap);
        cntx = new ListenerContext();
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces.clear();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA3, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        /* NetVirtA1 is allowed to communicate to A3 so action is forward */
        fAction = vrm.getForwardingAction(srcMAC, vMac, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        IDevice retDstDev = IDeviceService.fcStore.
                get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(netVirtA1, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA3, retDstIfaces.get(0).getParentVNS());
        assertEquals(dstDevice, retDstDev);

        /* TEST5: Send packet with dstMac as virtual router MAC. There is no
         * static ARP entry and so the device is looked up successfully by IP
         */
        cntx = new ListenerContext();
        /* Add empty static ARP table */
        staticArpMap = new HashMap<Integer, MACAddress>();
        vrm.setStaticArpTable(staticArpMap);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces.clear();
        dstIfaces.add(new VNSInterface("testDstIface", netVirtA3, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        /* NetVirtA1 is allowed to communicate to A3 so action is forward */
        fAction = vrm.getForwardingAction(srcMAC, vMac, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        retDstDev = IDeviceService.fcStore.
                get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(netVirtA1, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA3, retDstIfaces.get(0).getParentVNS());
        assertEquals(dstDevice, retDstDev);

        /* TEST6: Test pure L3 routing with a next hop configured. The
         * destination device is a silent host but the next hop is known
         */
        /* Add a pure L3 routing rule */
        vrm.addIfaceIp("t1|r1|if1", "10.1.1.0", "0.0.0.255");
        vrm.addIfaceIp("t1|r1|if2", "10.1.2.0", "0.0.0.255");
        vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt1", null,
                                            null, null, null, "10.1.2.0",
                                            "0.0.0.255", null, nextHopIpStr,
                                            "permit"));
        cntx = new ListenerContext();
        /* Add empty static ARP table */
        staticArpMap = new HashMap<Integer, MACAddress>();
        vrm.setStaticArpTable(staticArpMap);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces.clear();
        nextHopIfaces.clear();
        nextHopIfaces.add(new VNSInterface("nextHopIface", netVirtA2, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        /* 10.1.1.x is allowed to communicate with 10.1.2.x so action is
         * forward
         */
        fAction = vrm.getForwardingAction(srcMAC, vMac, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp2,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        retDstDev = IDeviceService.fcStore.
                get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(netVirtA1, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA2, retDstIfaces.get(0).getParentVNS());
        assertEquals(nextHopDevice, retDstDev);

        /* TEST7: Test pure L3 routing with a next hop configured. The
         * destination device is a silent host but the next hop is known using
         * static ARP entry
         * The only difference from TEST6 is the presence of static ARP entry
         */
        cntx = new ListenerContext();
        staticArpMap = new HashMap<Integer, MACAddress>();
        /* Add the next hop IP to the static ARP table */
        staticArpMap.put(new Integer(nextHopIp),
                         new MACAddress(Ethernet.toByteArray(nextHopMAC)));
        vrm.setStaticArpTable(staticArpMap);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces.clear();
        nextHopIfaces.clear();
        nextHopIfaces.add(new VNSInterface("nextHopIface", netVirtA2, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        /* 10.1.1.x is allowed to communicate with 10.1.2.x so action is
         * forward
         */
        fAction = vrm.getForwardingAction(srcMAC, vMac, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp2,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        retDstDev = IDeviceService.fcStore.
                get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(netVirtA1, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA2, retDstIfaces.get(0).getParentVNS());
        assertEquals(nextHopDevice, retDstDev);

        /* TEST8: Test the case where a static ARP is configured but it is not
         * required since the packet is already destined to that MAC. This
         * simulates packet from an intermediate switch which sees packet after
         * dest MAC rewrite
         */
        cntx = new ListenerContext();
        staticArpMap = new HashMap<Integer, MACAddress>();
        /* Add the next hop IP to the static ARP table */
        staticArpMap.put(new Integer(nextHopIp),
                         new MACAddress(Ethernet.toByteArray(nextHopMAC)));
        vrm.setStaticArpTable(staticArpMap);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                   nextHopDevice);
        srcIfaces.clear();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirtA1, null, null));
        dstIfaces.clear();
        nextHopIfaces.clear();
        nextHopIfaces.add(new VNSInterface("nextHopIface", netVirtA2, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        /* The next hop interface is already discovered by device manager */
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       nextHopIfaces);
        /* 10.1.1.x is allowed to communicate with 10.1.2.x so action is
         * forward
         */
        fAction = vrm.getForwardingAction(srcMAC, nextHopMAC, vlan,
                                          Ethernet.TYPE_IPv4, srcIp, dstIp2,
                                          cntx);
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        fcAppName = IFlowCacheService.fcStore.
                get(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        retDstDev = IDeviceService.fcStore.
                get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(netVirtA1, retSrcIfaces.get(0).getParentVNS());
        assertEquals(netVirtA2, retDstIfaces.get(0).getParentVNS());
        assertEquals(nextHopDevice, retDstDev);

        verify(srcDevice, dstDevice, nextHopDevice, deviceManager, vMacService,
               netVirtManager, rewriteService);
    }

    /*
     * Logical Topology
     * ----------------
     *
     * 10.0.0.1/24 +------+ 10.0.1.1/24
     *    ---------|  VR  |-------
     *    |        +------+      |
     *    |                      |
     *    |[NetVirt DB: 10.0.0.0/24] | [NetVirt Web : 10.0.1.0/24]
     * -------                ---------------------------------
     *    |                      |         |          |
     *    |                      |     +---|----------|------+
     *  +---+                  +---+   | +-----+    +-----+  |
     *  | A |                  | B |   | | GW1 |    | GW2 |  | Web-Gateway-Pool
     *  +---+                  +---+   | +-----+    +-----+  |
     *  10.0.0.2             10.0.1.2  +---------------------+
     *
     * Test Context:
     * -------------
     * o A wants to talk to B
     * o Packet which hits the controller as a packet IN has the following
     *   header fields:
     *   - SRC IP:  10.0.0.2
     *   - DST IP:  10.0.1.2
     *   - SRC MAC: 00:00:00:00:00:01
     *   - DST MAC: VR's MAC
     *   - VLAN:    Untagged (null)
     * o VR has a rule to permit traffic from A to B with next hop as a gateway
     *   pool = "Web-Gateway-Pool"
     *
     * Test Objective:
     * ---------------
     * o When VirtualRouting calls getForwardingAction() with the above fields,
     *   it should return a ForwardingAction that has a
     *   nextHopGatewayPool = "Web-Gateway-Pool"
     * o Also, the dstDevice in the SDN Platform context should point to the
     *   node which is closest to A. The computation of the closest node uses
     *   RoutingEngine and Topology APIs which will be mocked so that either
     *   GW1 or GW2 will be returned. For a view on how GW1, GW2, A are mocked
     *   up w.r.t physical topo: see Physical Topology section in the comments
     *   below.
     *
     * Physical Topology(A, GW1, GW2):
     * ------------------
     * +-------+
     * |       |
     * |  2  2 | --- GW2
     * |  1    |
     * +-------+
     *    |
     *    |
     * +-------+
     * |  3    |
     * |  1  2 | --- GW1
     * |  1    |
     * +-------+
     *    |
     *    A
     */
    @Test
    public void testGetForwardingActionGatewayPool() throws Exception {
        /*
         * Setup Source [IP, MAC, Device]
         */
        long srcMAC = Ethernet.toLong(Ethernet.toMACAddress("00:00:00:00:00:01"));
        int srcIp = IPv4.toIPv4Address("10.0.0.2");
        Integer[] srcIpArr = new Integer[1];
        srcIpArr[0] = srcIp;
        Device srcDevice = createMock(Device.class);
        srcDevice.getEntityClass();
        expectLastCall().andReturn(null).anyTimes();
        expect(srcDevice.getMACAddress()).andReturn(srcMAC).anyTimes();
        expect(srcDevice.getIPv4Addresses()).andReturn(srcIpArr).anyTimes();

        /*
         * Setup Destination [IP, MAC, Device]
         */
        long dstMAC = Ethernet.toLong(Ethernet.toMACAddress("00:00:00:00:00:02"));
        int dstIp = IPv4.toIPv4Address("10.0.1.2");
        Integer[] dstIpArr = new Integer[1];
        dstIpArr[0] = dstIp;
        Device dstDevice = createMock(Device.class);
        expect(dstDevice.getMACAddress()).andReturn(dstMAC).anyTimes();
        expect(dstDevice.getIPv4Addresses()).andReturn(dstIpArr).anyTimes();
        expect(deviceManager.findClassDevice(null, dstMAC, (short)0, dstIp)).
               andReturn(dstDevice).anyTimes();
        ArrayList<Device> dstList = new ArrayList<Device>();
        dstList.add(dstDevice);
        Iterator<Device> dstIter = dstList.iterator();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
            queryClassDevices(null, null, null, new Integer(dstIp), null, null)).
                              andReturn(dstIter).anyTimes();

        /*
         * Setup Gateway Node1 [IP, MAC, Device]
         */
        long gw1MAC = Ethernet.toLong(Ethernet.toMACAddress("00:00:00:00:00:03"));
        int gw1Ip = IPv4.toIPv4Address("10.0.1.3");
        Integer[] gw1IpArr = new Integer[1];
        gw1IpArr[0] = gw1Ip;
        Device gw1Device = createMock(Device.class);
        expect(gw1Device.getMACAddress()).andReturn(gw1MAC).anyTimes();
        expect(gw1Device.getIPv4Addresses()).andReturn(gw1IpArr).anyTimes();
        expect(deviceManager.findClassDevice(null, gw1MAC, (short)0, gw1Ip)).
               andReturn(gw1Device).anyTimes();
        ArrayList<Device> gw1List = new ArrayList<Device>();
        gw1List.add(gw1Device);
        Iterator<Device> gw1Iter = gw1List.iterator();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
            queryClassDevices(null, null, null, new Integer(gw1Ip), null, null)).
                              andReturn(gw1Iter).once();

        /*
         * Setup Gateway Node2 [IP, MAC, Device]
         */
        long gw2MAC = Ethernet.toLong(Ethernet.toMACAddress("00:00:00:00:00:04"));
        int gw2Ip = IPv4.toIPv4Address("10.0.1.4");
        Integer[] gw2IpArr = new Integer[1];
        gw2IpArr[0] = gw2Ip;
        Device gw2Device = createMock(Device.class);
        expect(gw2Device.getMACAddress()).andReturn(gw2MAC).anyTimes();
        expect(gw2Device.getIPv4Addresses()).andReturn(gw2IpArr).anyTimes();
        expect(deviceManager.findClassDevice(null, gw2MAC, (short)0, gw2Ip)).
               andReturn(gw2Device).anyTimes();
        ArrayList<Device> gw2List = new ArrayList<Device>();
        gw2List.add(gw2Device);
        Iterator<Device> gw2Iter = gw2List.iterator();
        EasyMock.<Iterator<? extends IDevice>>expect(deviceManager.
            queryClassDevices(null, null, null, new Integer(gw2Ip), null, null)).
                              andReturn(gw2Iter).anyTimes();

        /*
         * Setup Virtual Router MAC and vMacService to always return same vMac
         */
        long vMac = VirtualRouterManager.VIRTUAL_ROUTING_MAC;
        expect(vMacService.acquireVirtualMac()).andReturn(vMac).anyTimes();

        /*
         * Setup NetVirt and NetVirt interfaces for the devices
         */
        VNS dbNetVirt = new VNS("coke|db");
        VNS webNetVirt = new VNS("coke|web");
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        List<VNSInterface> gw1Ifaces = new ArrayList<VNSInterface>();
        List<VNSInterface> gw2Ifaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface", webNetVirt, null, null));
        gw1Ifaces.add(new VNSInterface("testGw1Iface", webNetVirt, null, null));
        gw2Ifaces.add(new VNSInterface("testGw2Iface", webNetVirt, null, null));
        expect(netVirtManager.getInterfaces(dstDevice)).andReturn(dstIfaces).anyTimes();
        expect(netVirtManager.getInterfaces(srcDevice)).andReturn(srcIfaces).anyTimes();
        expect(netVirtManager.getInterfaces(gw1Device)).andReturn(gw1Ifaces).anyTimes();
        expect(netVirtManager.getInterfaces(gw2Device)).andReturn(gw2Ifaces).anyTimes();
        expect(netVirtManager.getVNS("coke|db")).andReturn(dbNetVirt).anyTimes();
        expect(netVirtManager.getVNS("coke|web")).andReturn(webNetVirt).anyTimes();

        /*
         * Setup the Virtual Router
         */
        vrm.createTenant("coke", true);
        vrm.createVirtualRouter("vr", "coke");
        vrm.addVRouterIface("coke|vr", "if-db", "coke|db", null, true);
        vrm.addIfaceIp("coke|vr|if-db", "10.0.0.0", "0.0.0.255");
        vrm.addVRouterIface("coke|vr", "if-web", "coke|web", null, true);
        vrm.addIfaceIp("coke|vr|if-web", "10.0.1.0", "0.0.0.255");
        vrm.addGatewayPool("coke|vr", "Web-Gateway-Pool");
        vrm.addGatewayPoolNode("coke|vr|Web-Gateway-Pool", "10.0.1.3");
        vrm.addGatewayPoolNode("coke|vr|Web-Gateway-Pool", "10.0.1.4");

        /*
         * Setup Static ARP Table for dstIp, gw1Ip, gw2Ip
         */
        Map<Integer, MACAddress> staticArpMap = new HashMap<Integer, MACAddress>();
        staticArpMap.put(new Integer(dstIp),
                         new MACAddress(Ethernet.toByteArray(dstMAC)));
        staticArpMap.put(new Integer(gw1Ip),
                         new MACAddress(Ethernet.toByteArray(gw1MAC)));
        staticArpMap.put(new Integer(gw2Ip),
                         new MACAddress(Ethernet.toByteArray(gw2MAC)));
        vrm.setStaticArpTable(staticArpMap);

        /*
         * Add a host specific route to permit traffic from A to B via
         * a nexthop gateway pool
         */
        vrm.addRoutingRule(createRuleParamsWithNextHopGatewayPool("coke|vr",
                                        null, null,
                                        "10.0.0.2", "255.255.255.255",
                                        null, null,
                                        "10.0.1.2", "255.255.255.255",
                                        null, null,
                                        "permit", "coke|vr|Web-Gateway-Pool"));

        /*
         * Prepare the listener context
         * DstDevice, DstInterfaces are obtained by getForwardingAction() as:
         * dstIp --> dstMac(from static arp table) --> dstDevice --> dstInterfaces
         */
        ListenerContext cntx = new ListenerContext();
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        srcIfaces.add(new VNSInterface("testSrcIface", dbNetVirt, null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        /*
         * Mock Up device attachment points for srcDevice
         */
        SwitchPort srcAP = new SwitchPort(1L, (int)1);
        SwitchPort[] srcAPs = new SwitchPort[1];
        srcAPs[0] = srcAP;
        expect(srcDevice.getAttachmentPoints()).andReturn(srcAPs).anyTimes();

        /*
         * Mock Up device attachment points for gw1Device
         */
        SwitchPort gw1AP = new SwitchPort(1L, (int)2);
        SwitchPort[] gw1APs = new SwitchPort[1];
        gw1APs[0] = gw1AP;
        expect(gw1Device.getAttachmentPoints()).andReturn(gw1APs).anyTimes();

        /*
         * Mock Up device attachment points for gw2Device
         */
        SwitchPort gw2AP = new SwitchPort(2L, (int)2);
        SwitchPort[] gw2APs = new SwitchPort[1];
        gw2APs[0] = gw2AP;
        expect(gw2Device.getAttachmentPoints()).andReturn(gw2APs).anyTimes();

        /*
         * Setup mock routes
         */
        Route routeAToGW1 = new Route(srcMAC, gw1MAC);
        routeAToGW1.getPath().add(new NodePortTuple(1L, (short)1));
        routeAToGW1.getPath().add(new NodePortTuple(1L, (short)2));
        Route routeAToGW2 = new Route(srcMAC, gw2MAC);
        routeAToGW2.getPath().add(new NodePortTuple(1L, (short)1));
        routeAToGW2.getPath().add(new NodePortTuple(1L, (short)3));
        routeAToGW2.getPath().add(new NodePortTuple(2L, (short)1));
        routeAToGW2.getPath().add(new NodePortTuple(2L, (short)2));

        /*
         * Mock Up topology and routing engine to return the appropriate mock
         * objects.
         */
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)2)).andReturn(true).
               anyTimes();
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)2, 0)).
               andReturn(routeAToGW1).times(2);
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)2, 0)).
               andReturn(routeAToGW2).times(2);

        /*
         * Setup the Rewrite service mock ups
         */
        rewriteService.setIngressDstMac(EasyMock.eq(new Long(vMac)),
                                        EasyMock.eq(new Long(gw1MAC)),
                                        (ListenerContext)EasyMock.anyObject());
        expectLastCall().times(2);
        rewriteService.setTtlDecrement(EasyMock.eq(1),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().times(2);
        rewriteService.setEgressSrcMac(EasyMock.eq(new Long(srcMAC)),
                                       EasyMock.eq(new Long(vMac)),
                                       (ListenerContext)EasyMock.anyObject());
        expectLastCall().times(2);

        /*
         * Activate the mock ups
         */
        replay(srcDevice, dstDevice, gw1Device, gw2Device, deviceManager,
               vMacService, netVirtManager, topology, routingEngine, rewriteService);

        /*
         * Test I
         */
        ForwardingAction fAction = vrm.getForwardingAction(srcMAC, vMac,
                                                           (short)0,
                                                           Ethernet.TYPE_IPv4,
                                                           srcIp, dstIp, cntx);
        /*
         * Verify Test I output
         */
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        assertEquals("Web-Gateway-Pool", fAction.getNextHopGatewayPool());
        assertEquals("coke|vr", fAction.getNextHopGatewayPoolRouter().getName());
        assertEquals(gw1Ip, fAction.getNextHopIp());
        assertEquals(vMac, fAction.getNewSrcMac());
        assertEquals(gw1Device, IDeviceService.fcStore.get(cntx,
                                           IDeviceService.CONTEXT_DST_DEVICE));
        String fcAppName = IFlowCacheService.fcStore.get(cntx,
                           IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        List<VNSInterface> retSrcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        List<VNSInterface> retDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertEquals(dbNetVirt, retSrcIfaces.get(0).getParentVNS());
        assertEquals(webNetVirt, retDstIfaces.get(0).getParentVNS());

        /*
         * Test II:
         * Repeat the test with no Static ARPs for dst, gw1 and gw2. This would
         * test the capability to query the devices based purely on IP Addresses.
         */
        staticArpMap.remove(new Integer(dstIp));
        staticArpMap.remove(new Integer(gw1Ip));
        staticArpMap.remove(new Integer(gw2Ip));
        cntx = new ListenerContext();
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        fAction = vrm.getForwardingAction(srcMAC, vMac,
                                          (short)0,
                                          Ethernet.TYPE_IPv4,
                                          srcIp, dstIp, cntx);
        /*
         * Verify Test II output
         */
        assertEquals(RoutingAction.FORWARD, fAction.getAction());
        assertEquals(true, fAction.isVirtualRouted());
        assertEquals("Web-Gateway-Pool", fAction.getNextHopGatewayPool());
        assertEquals("coke|vr", fAction.getNextHopGatewayPoolRouter().getName());
        assertEquals(gw1Ip, fAction.getNextHopIp());
        assertEquals(vMac, fAction.getNewSrcMac());
        assertEquals(gw1Device, IDeviceService.fcStore.get(cntx,
                                           IDeviceService.CONTEXT_DST_DEVICE));
        fcAppName = IFlowCacheService.fcStore.get(cntx,
                           IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME, fcAppName);
        retSrcIfaces = INetVirtManagerService.bcStore.
                       get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        retDstIfaces = INetVirtManagerService.bcStore.
                       get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertEquals(dbNetVirt, retSrcIfaces.get(0).getParentVNS());
        assertEquals(webNetVirt, retDstIfaces.get(0).getParentVNS());

        /*
         * Test III:
         * Repeat the test with no nodes reachable in the gateway pool,
         * this is simulated by annihilating the gateway pool nodes
         * For this test, put back the ARP entry for the DstIp just for
         * the convenience of not having to set up the iterator again.
         */
        vrm.removeGatewayPoolNode("coke|vr|Web-Gateway-Pool", "10.0.1.3");
        vrm.removeGatewayPoolNode("coke|vr|Web-Gateway-Pool", "10.0.1.4");
        staticArpMap.put(new Integer(dstIp),
                         new MACAddress(Ethernet.toByteArray(dstMAC)));
        cntx = new ListenerContext();
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        fAction = vrm.getForwardingAction(srcMAC, vMac,
                                          (short)0,
                                          Ethernet.TYPE_IPv4,
                                          srcIp, dstIp, cntx);
        /*
         * Verify Test III output
         */
        assertEquals(RoutingAction.DROP, fAction.getAction());

        /*
         * Verify the mockers
         */
        verify(srcDevice, dstDevice, gw1Device, gw2Device, deviceManager,
               vMacService, netVirtManager, topology, routingEngine, rewriteService);
    }

    /* XXX Only use one MAC for all virtual routers
    public void testRelinquishMacs() throws Exception {
        vrm.createTenant("t1", true);
        vrm.createTenant("t2", true);
        vrm.createTenant("system", true);
        vrm.createTenant("tx", true);

        long vMac1 =
                Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:55"));
        expect(vMacService.acquireVirtualMac()).andReturn(vMac1).once();
        long vMac2 =
                Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:56"));
        expect(vMacService.acquireVirtualMac()).andReturn(vMac2).once();
        long vMac3 =
                Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:57"));
        expect(vMacService.acquireVirtualMac()).andReturn(vMac3).once();
        long vMac4 =
                Ethernet.toLong(Ethernet.toMACAddress("00:11:22:33:44:58"));
        expect(vMacService.acquireVirtualMac()).andReturn(vMac4).once();
        vMacService.relinquishVirtualMAC(vMac1);
        expectLastCall().once();
        vMacService.relinquishVirtualMAC(vMac2);
        expectLastCall().once();
        vMacService.relinquishVirtualMAC(vMac3);
        expectLastCall().once();
        vMacService.relinquishVirtualMAC(vMac4);
        expectLastCall().once();
        replay(vMacService);

        vrm.createVirtualRouter("r1", "t1");
        vrm.createVirtualRouter("r2", "t2");
        vrm.createVirtualRouter("rs", "system");
        vrm.createVirtualRouter("rx", "tx");

        vrm.relinquishVMacs();

        verify(vMacService);
    }
    */

    @Test
    public void testSubnetOwner() {
        IPV4Subnet ips1 = new IPV4Subnet("10.1.1.1/24");
        IPV4Subnet ips2 = new IPV4Subnet("10.1.1.1/16");
        IPV4Subnet ips3 = new IPV4Subnet("10.2.1.1/24");
        vrm.addSubnetOwner(ips1, "t1|r1");
        vrm.addSubnetOwner(ips2, "t1|r2");
        vrm.addSubnetOwner(ips3, "t2|r1");

        String owner;
        IPV4Subnet test = new IPV4Subnet("10.1.1.2/32");
        owner = vrm.findSubnetOwner(test);
        /* The most specific subnet is chosen */
        assertEquals(owner, "t1|r1");

        test = new IPV4Subnet("10.1.2.2/32");
        owner = vrm.findSubnetOwner(test);
        assertEquals(owner, "t1|r2");

        test = new IPV4Subnet("10.2.1.4/32");
        owner = vrm.findSubnetOwner(test);
        assertEquals(owner, "t2|r1");

        test = new IPV4Subnet("192.168.1.1/32");
        owner = vrm.findSubnetOwner(test);
        assertNull(owner);
    }

    @Test
    public void testGetRtrVMac() throws Exception {
        vrm.createTenant("t1", true);
        vrm.createTenant("t2", true);
        vrm.createTenant("system", true);

        String ipr1if1str = "10.1.1.1";
        String ipr1if2str = "10.1.2.1";
        String ipr2if1str = "10.2.1.1";
        String ipr2if2str = "10.2.2.1";
        vrm.createVirtualRouter("r1", "t1");
        vrm.createVirtualRouter("r2", "t2");
        vrm.createVirtualRouter("rs", "system");
        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        vrm.addIfaceIp("t1|r1|if1", ipr1if1str, "0.0.0.255");
        vrm.addVRouterIface("t1|r1", "if2", "t1|netVirt2", null, true);
        vrm.addIfaceIp("t1|r1|if2", ipr1if2str, "0.0.0.255");
        vrm.addVRouterIface("t1|r1", "ifs", null, "system|rs", true);
        vrm.addVRouterIface("t2|r2", "if1", "t2|netVirt1", null, true);
        vrm.addIfaceIp("t2|r2|if1", ipr2if1str, "0.0.0.255");
        vrm.addVRouterIface("t2|r2", "if2", "t2|netVirt2", null, false);
        vrm.addIfaceIp("t2|r2|if2", ipr2if2str, "0.0.0.255");
        vrm.addVRouterIface("t2|r2", "ifs", null, "system|rs", true);
        vrm.addVRouterIface("system|rs", "if1", null, "t1|r1", true);
        vrm.addVRouterIface("system|rs", "if2", null, "t2|r2", true);
        /* Allow t1 to communicate with any other host in t1 */
        vrm.addRoutingRule(createRuleParams("t1|r1", "t1", null, null,
                                            null, "t1", null, null, null,
                                            null, null, "permit"));
        /* Allow t1|netVirt1 to communicate with t2|netVirt1 and vice versa */
        vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt1", null,
                                            null, null, "t2|netVirt1", null, null,
                                            null, null, "permit"));
        vrm.addRoutingRule(createRuleParams("t1|r1", null, "t2|netVirt1", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));

        /* Allow t1 to communicate to t2 and vice versa */
        vrm.addRoutingRule(createRuleParams("system|rs", "t1", null, null, null,
                                            "t2", null, null, null, null, null,
                                            "permit"));
        vrm.addRoutingRule(createRuleParams("system|rs", "t2", null, null, null,
                                            "t1", null, null, null, null, null,
                                            "permit"));

        /* Allow t1|netVirt1 to communicate with t2|netVirt1 and vice versa */
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t1|netVirt1", null,
                                            null, null, "t2|netVirt1", null, null,
                                            null, null, "permit"));
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t2|netVirt1", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));
        /* Allow t2|netVirt1 to communicate with t2|netVirt2 but NOT vice versa */
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t2|netVirt1", null,
                                            null, null, "t2|netVirt2", null, null,
                                            null, null, "permit"));
        /* Allow t2|netVirt2 to communicate with t1|netVirt1 but NOT vice versa */
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t2|netVirt2", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));

        VNS netVirtA1 = new VNS("t1|netVirt1");
        VNS netVirtA2 = new VNS("t1|netVirt2");
        VNS netVirtA3 = new VNS("t1|netVirt3");
        VNS netVirtB1 = new VNS("t2|netVirt1");
        VNS netVirtB2 = new VNS("t2|netVirt2");
        expect(netVirtManager.getVNS("t1|netVirt1")).andReturn(netVirtA1).anyTimes();
        expect(netVirtManager.getVNS("t1|netVirt2")).andReturn(netVirtA2).anyTimes();
        expect(netVirtManager.getVNS("t2|netVirt1")).andReturn(netVirtB1).anyTimes();
        expect(netVirtManager.getVNS("t2|netVirt2")).andReturn(netVirtB2).anyTimes();
        replay(netVirtManager);

        int ipA1 = IPv4.toIPv4Address("10.1.1.2");
        int ipA2 = IPv4.toIPv4Address("10.1.2.2");
        int ipA3 = IPv4.toIPv4Address("10.1.3.2");
        int ipB1 = IPv4.toIPv4Address("10.2.1.2");
        int ipB2 = IPv4.toIPv4Address("10.2.2.2");
        int ipr1if1 = IPv4.toIPv4Address(ipr1if1str);
        int ipr1if2 = IPv4.toIPv4Address(ipr1if2str);
        int ipr2if1 = IPv4.toIPv4Address(ipr2if1str);
        int ipr2if2 = IPv4.toIPv4Address(ipr2if2str);

        long vMac;
        vMac = vrm.getRtrVMac(netVirtA1, ipA1, ipr1if1);
        assertEquals(VirtualRouterManager.VIRTUAL_ROUTING_MAC, vMac);
        vMac = vrm.getRtrVMac(netVirtA2, ipA2, ipr1if2);
        assertEquals(VirtualRouterManager.VIRTUAL_ROUTING_MAC, vMac);
        vMac = vrm.getRtrVMac(netVirtB1, ipB1, ipr2if1);
        assertEquals(VirtualRouterManager.VIRTUAL_ROUTING_MAC, vMac);
        vMac = vrm.getRtrVMac(netVirtB2, ipB2, ipr2if2);
        assertEquals(VirtualRouterManager.VIRTUAL_ROUTING_MAC, vMac);

        vMac = vrm.getRtrVMac(netVirtA1, ipA1, ipr1if2);
        assertEquals(VirtualRouterManager.VIRTUAL_ROUTING_MAC, vMac);

        vMac = vrm.getRtrVMac(netVirtA1, ipA1, ipr2if1);
        assertEquals(VirtualRouterManager.VIRTUAL_ROUTING_MAC, vMac);

        /* No route from netVirt B1 to netVirt B2 */
        vMac = vrm.getRtrVMac(netVirtB1, ipB1, ipr2if2);
        assertEquals(0, vMac);

        /* No route from netVirt A1 to netVirt B2 */
        vMac = vrm.getRtrVMac(netVirtB2, ipB2, ipr1if1);
        assertEquals(0, vMac);

        /* The dest IP does not belong to a router */
        vMac = vrm.getRtrVMac(netVirtA1, ipA1, ipB1);
        assertEquals(0, vMac);

        /* The source NetVirt is not connected to a router */
        vMac = vrm.getRtrVMac(netVirtA3, ipA3, ipr1if1);
        assertEquals(0, vMac);

        verify(netVirtManager);
    }

    @Test
    public void testGetRtrIp() throws Exception {
        vrm.createTenant("t1", true);
        vrm.createTenant("t2", true);
        vrm.createTenant("system", true);

        String ipr1if1str = "10.1.1.1";
        String ipr1if2str = "10.1.2.1";
        String ipr2if1str = "10.2.1.1";
        String ipr2if2str = "10.2.2.1";
        vrm.createVirtualRouter("r1", "t1");
        vrm.createVirtualRouter("r2", "t2");
        vrm.createVirtualRouter("rs", "system");
        vrm.addVRouterIface("t1|r1", "if1", "t1|netVirt1", null, true);
        vrm.addIfaceIp("t1|r1|if1", ipr1if1str, "0.0.0.255");
        /* If1 has 2 IPs. The second IP is the same as r2 if1 IP */
        vrm.addIfaceIp("t1|r1|if1", ipr2if1str, "0.0.0.255");
        vrm.addVRouterIface("t1|r1", "if2", "t1|netVirt2", null, true);
        vrm.addIfaceIp("t1|r1|if2", ipr1if2str, "0.0.0.255");
        vrm.addVRouterIface("t1|r1", "ifs", null, "system|rs", true);
        vrm.addVRouterIface("t2|r2", "if1", "t2|netVirt1", null, true);
        vrm.addIfaceIp("t2|r2|if1", ipr2if1str, "0.0.0.255");
        vrm.addVRouterIface("t2|r2", "if2", "t2|netVirt2", null, false);
        vrm.addIfaceIp("t2|r2|if2", ipr2if2str, "0.0.0.255");
        vrm.addVRouterIface("t2|r2", "ifs", null, "system|rs", true);
        vrm.addVRouterIface("system|rs", "if1", null, "t1|r1", true);
        vrm.addVRouterIface("system|rs", "if2", null, "t2|r2", true);
        /* Allow t1 to communicate with any other host in t1 */
        vrm.addRoutingRule(createRuleParams("t1|r1", "t1", null, null,
                                            null, "t1", null, null, null,
                                            null, null, "permit"));
        /* Allow t1|netVirt1 to communicate with t2|netVirt1 and vice versa */
        vrm.addRoutingRule(createRuleParams("t1|r1", null, "t1|netVirt1", null,
                                            null, null, "t2|netVirt1", null, null,
                                            null, null, "permit"));
        vrm.addRoutingRule(createRuleParams("t1|r1", null, "t2|netVirt1", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));
        /* Allow t2|netVirt2 to communicate with t1|netVirt1 but not vice versa */
        vrm.addRoutingRule(createRuleParams("t1|r1", null, "t2|netVirt2", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));

        /* Allow t1 to communicate to t2 and vice versa */
        vrm.addRoutingRule(createRuleParams("system|rs", "t1", null, null, null,
                                            "t2", null, null, null, null, null,
                                            "permit"));
        vrm.addRoutingRule(createRuleParams("system|rs", "t2", null, null, null,
                                            "t1", null, null, null, null, null,
                                            "permit"));

        /* Allow t1|netVirt1 to communicate with t2|netVirt1 and vice versa */
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t1|netVirt1", null,
                                            null, null, "t2|netVirt1", null, null,
                                            null, null, "permit"));
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t2|netVirt1", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));
        /* Allow t2|netVirt1 to communicate with t2|netVirt2 but NOT vice versa */
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t2|netVirt1", null,
                                            null, null, "t2|netVirt2", null, null,
                                            null, null, "permit"));
        /* Allow t2|netVirt2 to communicate with t1|netVirt1 but NOT vice versa */
        vrm.addRoutingRule(createRuleParams("t2|r2", null, "t2|netVirt2", null,
                                            null, null, "t1|netVirt1", null, null,
                                            null, null, "permit"));

        VNS netVirtA1 = new VNS("t1|netVirt1");
        VNS netVirtA2 = new VNS("t1|netVirt2");
        VNS netVirtA3 = new VNS("t1|netVirt3");
        VNS netVirtB1 = new VNS("t2|netVirt1");
        VNS netVirtB2 = new VNS("t2|netVirt2");
        expect(netVirtManager.getVNS("t1|netVirt1")).andReturn(netVirtA1).anyTimes();
        expect(netVirtManager.getVNS("t1|netVirt2")).andReturn(netVirtA2).anyTimes();
        expect(netVirtManager.getVNS("t2|netVirt1")).andReturn(netVirtB1).anyTimes();
        expect(netVirtManager.getVNS("t2|netVirt2")).andReturn(netVirtB2).anyTimes();
        replay(netVirtManager);

        int ipA1 = IPv4.toIPv4Address("10.1.1.2");
        int ipB1 = IPv4.toIPv4Address("10.2.1.2");
        int ipB2 = IPv4.toIPv4Address("10.2.2.2");
        int ipr1if1 = IPv4.toIPv4Address(ipr1if1str);
        int ipr2if1 = IPv4.toIPv4Address(ipr2if1str);

        int retIp;
        /* netVirtA3 is not connected to any router so expect 0 */
        retIp = vrm.getRtrIp(netVirtA3, ipA1, ipr1if1);
        assertEquals(0, retIp);

        /* netVirtA1 is connected to interface if1 so return if1 ip */
        retIp = vrm.getRtrIp(netVirtA1, ipA1, ipr1if1);
        assertEquals(ipr1if1, retIp);

        /* netVirtA1 is connected to interface if1 so return if1 ip, even though
         * this IP belongs to some other interface as well
         */
        retIp = vrm.getRtrIp(netVirtA1, ipA1, ipr2if1);
        assertEquals(ipr2if1, retIp);

        /* netVirtB1 is connected to interface if1 of router r2 so return if1 ip */
        retIp = vrm.getRtrIp(netVirtB1, ipB1, ipr2if1);
        assertEquals(ipr2if1, retIp);

        /* netVirtB1 is allowed to communicate to netVirtA1 so it can reach interface
         * if1 of router r1
         */
        retIp = vrm.getRtrIp(netVirtB1, ipB1, ipr1if1);
        assertEquals(ipr1if1, retIp);

        /* netVirtB2 is allowed to communicate to netVirtA1 but the reverse path is not
         * permitted. So return 0
         */
        retIp = vrm.getRtrIp(netVirtB2, ipB2, ipr1if1);
        assertEquals(0, retIp);

        /* netVirtA1 and netVirtB1 are allowed to communicate so when this happens
         * return the interface ip of r1 if1
         */
        retIp = vrm.getRtrIp(netVirtA1, ipA1, ipB1);
        assertEquals(ipr1if1, retIp);
    }
}
