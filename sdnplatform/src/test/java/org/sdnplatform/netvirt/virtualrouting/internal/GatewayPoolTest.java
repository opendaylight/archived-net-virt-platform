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

import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.netvirt.manager.INetVirtListener;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.GatewayNode;
import org.sdnplatform.netvirt.virtualrouting.IVirtualMacService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.GatewayPoolImpl;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouterManager;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;


public class GatewayPoolTest extends PlatformTestCase {
    private GatewayPoolImpl gatewayPool;
    private static final String gatewayNode1MacStr = "01:aa:bb:cc:dd:01";
    private static final String gatewayNode2MacStr = "01:aa:bb:cc:dd:02";
    private static final Long gatewayNode1Mac =
            Ethernet.toLong(Ethernet.toMACAddress(gatewayNode1MacStr));
    private static final Long gatewayNode2Mac =
            Ethernet.toLong(Ethernet.toMACAddress(gatewayNode2MacStr));
    private static final String gatewayNode1IpStr = "192.168.20.10";
    private static final int gatewayNode1Ip =
            IPv4.toIPv4Address(gatewayNode1IpStr);
    private static final String gatewayNode2IpStr = "192.168.20.20";
    private static final int gatewayNode2Ip =
            IPv4.toIPv4Address(gatewayNode2IpStr);
    private VirtualRouterManager vRtrManager;
    private ITopologyService topology;
    private IRoutingService routingEngine;
    private MockDeviceManager mockDeviceManager;
    private MemoryStorageSource storageSource;
    private VirtualRouting virtualRouting;
    private IFlowReconcileService flowReconcileMgr;
    private INetVirtManagerService netVirtManager;
    private IAddressSpaceManagerService addressSpaceMgr;
    private IFlowCacheService betterFlowCacheMgr;
    private DefaultEntityClassifier entityClassifier;
    private ModuleContext fmc;
    protected IDevice device1, device2, gatewayNode1, gatewayNode2;

    /*
     * A few helpers
     */
    private void verifyNodePresent(String ip, int size) {
        Map<String, GatewayNode> gatewayNodes = gatewayPool.getGatewayNodes();
        assertEquals(size, gatewayNodes.size());
        GatewayNode node = gatewayNodes.get(ip);
        assertNotNull(null, node);
        assertEquals(ip, IPv4.fromIPv4Address(node.getIp()));
    }

    private void verifyNodeAbsent(String ip, int size) {
        Map<String, GatewayNode> gatewayNodes = gatewayPool.getGatewayNodes();
        assertEquals(size, gatewayNodes.size());
        assertEquals(null, gatewayNodes.get(ip));
    }

    private void setup2NodeGatewayPool() {
        gatewayPool.addGatewayNode(gatewayNode1IpStr);
        gatewayPool.addGatewayNode(gatewayNode2IpStr);
        expect(vRtrManager.findDevice(null,
                                      entityClassifier.classifyEntity(null),
                                      0L, (short)0, (Integer)gatewayNode1Ip)).
            andReturn(gatewayNode1).anyTimes();
        expect(vRtrManager.findDevice(null,
                                      entityClassifier.classifyEntity(null),
                                      0L, (short)0, (Integer)gatewayNode2Ip)).
            andReturn(gatewayNode2).anyTimes();
        replay(vRtrManager);
    }

    private void setup1NodeGatewayPool() {
        gatewayPool.addGatewayNode(gatewayNode1IpStr);
        expect(vRtrManager.findDevice(null,
                                      entityClassifier.classifyEntity(null),
                                      0L, (short)0, (Integer)gatewayNode1Ip)).
            andReturn(gatewayNode1).anyTimes();
        replay(vRtrManager);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        topology = createMock(ITopologyService.class);
        routingEngine = createMock(IRoutingService.class);
        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        virtualRouting = new VirtualRouting();
        addressSpaceMgr = createMock(IAddressSpaceManagerService.class);
        flowReconcileMgr = createMock(IFlowReconcileService.class);
        netVirtManager = createMock(INetVirtManagerService.class);
        RestApiServer ras = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        entityClassifier = new DefaultEntityClassifier();
        betterFlowCacheMgr = createMock(IFlowCacheService.class);
        vRtrManager = createMock(VirtualRouterManager.class);
        fmc = new ModuleContext();
        fmc.addService(IControllerService.class, mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IAddressSpaceManagerService.class, addressSpaceMgr);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IVirtualMacService.class, virtualRouting);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        expect(vRtrManager.getTopology()).andReturn(topology).anyTimes();
        expect(vRtrManager.getRoutingService()).andReturn(routingEngine).
               anyTimes();
        vRtrManager.setDeviceManager(mockDeviceManager);
        gatewayPool = new GatewayPoolImpl("testGatewayPool", vRtrManager);

        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        virtualRouting.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        entityClassifier.init(fmc);
        tp.init(fmc);

        netVirtManager.addNetVirtListener((INetVirtListener)EasyMock.anyObject());
        expectLastCall().anyTimes();
        netVirtManager.clearCachedDeviceState(EasyMock.anyLong());
        expectLastCall().anyTimes();

        replay(netVirtManager, addressSpaceMgr);
        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        entityClassifier.startUp(fmc);
        tp.startUp(fmc);
    }

    @Test
    public void testGetters() {
        assertEquals("testGatewayPool", gatewayPool.getName());
    }

    @Test
    public void testAddGatewayNode() {
        verifyNodeAbsent("10.0.0.1", 0);
        gatewayPool.addGatewayNode("10.0.0.1");
        verifyNodePresent("10.0.0.1", 1);

        verifyNodeAbsent("10.0.0.2", 1);
        gatewayPool.addGatewayNode("10.0.0.2");
        verifyNodePresent("10.0.0.2", 2);

        verifyNodeAbsent("10.0.0.3", 2);
        gatewayPool.addGatewayNode("10.0.0.3");
        verifyNodePresent("10.0.0.3", 3);

        verifyNodeAbsent("10.0.0.4", 3);
        gatewayPool.addGatewayNode("10.0.0.4");
        verifyNodePresent("10.0.0.4", 4);
    }

    @Test
    public void testRemoveGatewayNode() {
        gatewayPool.addGatewayNode("10.0.0.1");
        verifyNodePresent("10.0.0.1", 1);
        gatewayPool.addGatewayNode("10.0.0.2");
        verifyNodePresent("10.0.0.2", 2);
        gatewayPool.addGatewayNode("10.0.0.3");
        verifyNodePresent("10.0.0.3", 3);
        gatewayPool.addGatewayNode("10.0.0.4");
        verifyNodePresent("10.0.0.4", 4);

        gatewayPool.removeGatewayNode("10.0.0.1");
        verifyNodeAbsent("10.0.0.1", 3);

        gatewayPool.removeGatewayNode("10.0.0.2");
        verifyNodeAbsent("10.0.0.2", 2);

        gatewayPool.removeGatewayNode("10.0.0.3");
        verifyNodeAbsent("10.0.0.3", 1);

        gatewayPool.removeGatewayNode("10.0.0.4");
        verifyNodeAbsent("10.0.0.4", 0);
    }

    /*
     * Test the gateway node selection when one of the gateway nodes is
     * on the same AP as the device.
     *
     *     +-------+                               +-------+
     *     |       |                               |       |
     *     |   3  2|-------------------------------|2  4   |
     *     |   1   |                               |   1   |
     *     +-------+                               +-------+
     *         |                                       |
     *         |                                       |
     *         |                                       |
     *     +-------+                               +-------+
     *     |   2   |                               |   2   |
     *     |   1   |                               |   2   |
     *     |   1   |                               |   1   |
     *     +-------+                               +-------+
     *         |\                                      |\
     *         | \-------------                        | \-------------
     *         |              |                        |              |
     *     +-------+      +-------+                +-------+      +-------+
     *     |   1   |      |   1   |                |   1   |      |   1   |
     *     |Host 1 |      |  GW1  |                |Host 2 |      |  GW2  |
     *     |       |      |       |                |       |      |       |
     *     +-------+      +-------+                +-------+      +-------+
     */
    @Test
    public void testGetOptimalGatewayNodeSameAP() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        replay(topology);

        /*
         * Setup device1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);

        /*
         * Setup device2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 1, false);

        /*
         * Setup gatewayNode1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 1L, 1,
                                                     false);

        /*
         * Setup gatewayNode2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 2L, 1,
                                                     false);
        setup2NodeGatewayPool();

        /*
         * Setup the mock routes
         */
        Route routeD1ToGN2 = new Route(device1.getMACAddress(),
                                       gatewayNode1.getMACAddress());
        routeD1ToGN2.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGN2.getPath().add(new NodePortTuple(1L, (short)2));
        routeD1ToGN2.getPath().add(new NodePortTuple(3L, (short)1));
        routeD1ToGN2.getPath().add(new NodePortTuple(3L, (short)2));
        routeD1ToGN2.getPath().add(new NodePortTuple(4L, (short)2));
        routeD1ToGN2.getPath().add(new NodePortTuple(4L, (short)1));
        routeD1ToGN2.getPath().add(new NodePortTuple(2L, (short)2));
        routeD1ToGN2.getPath().add(new NodePortTuple(2L, (short)1));

        Route routeD2ToGN1 = new Route(device2.getMACAddress(),
                                       gatewayNode1.getMACAddress());
        routeD2ToGN1.getPath().add(new NodePortTuple(2L, (short)1));
        routeD2ToGN1.getPath().add(new NodePortTuple(2L, (short)2));
        routeD2ToGN1.getPath().add(new NodePortTuple(4L, (short)1));
        routeD2ToGN1.getPath().add(new NodePortTuple(4L, (short)2));
        routeD2ToGN1.getPath().add(new NodePortTuple(3L, (short)2));
        routeD2ToGN1.getPath().add(new NodePortTuple(3L, (short)1));
        routeD2ToGN1.getPath().add(new NodePortTuple(1L, (short)2));
        routeD2ToGN1.getPath().add(new NodePortTuple(1L, (short)1));

        /*
         * ReMock up topology to return the L2DomainId
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
                                              .andReturn(true).anyTimes();

        /*
         * Mock up routingEngine to return the appropriate mock route
         */
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)1, 0)).
                                      andReturn(routeD1ToGN2).times(1);
        expect(routingEngine.getRoute(2L, (short)1, 1L, (short)1, 0)).
                                      andReturn(routeD2ToGN1).times(1);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
            gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
            gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();

        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode2Ip, selectedGwIP2);
    }

    /*
     * Test the node selection with device and gateway nodes in the same cluster
     *
     *          +-------+                                        +-------+
     *          |       |                                        |       |
     *          |   GW1 |                                        |  GW2  |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   3  2|----------------------------------------|2  4   |
     *          |   3   |                                        |   3   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   1   |                                        |   2   |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |Host 1 |                                        |Host 2 |
     *          |       |                                        |       |
     *          +-------+                                        +-------+
     */
    @Test
    public void testGetOptimalGatewayNodeSameCluster() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        replay(topology);

        /*
         * Setup device1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);

        /*
         * Setup device2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 1, false);

        /*
         * Setup gatewayNode1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 3L, 1,
                                                     false);

        /*
         * Setup gatewayNode2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 4L, 1,
                                                     false);

        setup2NodeGatewayPool();

        /*
         * Setup mock routes
         */
        Route routeD1ToGW1 = new Route(device1.getMACAddress(),
                                       gatewayNode1.getMACAddress());
        routeD1ToGW1.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGW1.getPath().add(new NodePortTuple(1L, (short)2));
        routeD1ToGW1.getPath().add(new NodePortTuple(3L, (short)3));
        routeD1ToGW1.getPath().add(new NodePortTuple(3L, (short)1));

        Route routeD1ToGW2 = new Route(device1.getMACAddress(),
                                       gatewayNode2.getMACAddress());
        routeD1ToGW2.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGW2.getPath().add(new NodePortTuple(1L, (short)2));
        routeD1ToGW2.getPath().add(new NodePortTuple(3L, (short)3));
        routeD1ToGW2.getPath().add(new NodePortTuple(3L, (short)2));
        routeD1ToGW2.getPath().add(new NodePortTuple(4L, (short)2));
        routeD1ToGW2.getPath().add(new NodePortTuple(4L, (short)1));

        Route routeD2ToGW1 = new Route(device2.getMACAddress(),
                                       gatewayNode1.getMACAddress());
        routeD2ToGW1.getPath().add(new NodePortTuple(2L, (short)1));
        routeD2ToGW1.getPath().add(new NodePortTuple(2L, (short)2));
        routeD2ToGW1.getPath().add(new NodePortTuple(4L, (short)3));
        routeD2ToGW1.getPath().add(new NodePortTuple(4L, (short)2));
        routeD2ToGW1.getPath().add(new NodePortTuple(3L, (short)2));
        routeD2ToGW1.getPath().add(new NodePortTuple(3L, (short)1));

        Route routeD2ToGW2 = new Route(device2.getMACAddress(),
                                       gatewayNode2.getMACAddress());
        routeD2ToGW2.getPath().add(new NodePortTuple(2L, (short)1));
        routeD2ToGW2.getPath().add(new NodePortTuple(2L, (short)2));
        routeD2ToGW2.getPath().add(new NodePortTuple(4L, (short)3));
        routeD2ToGW2.getPath().add(new NodePortTuple(4L, (short)1));

        /*
         * ReMock up topology to return the L2DomainId and
         * determine if a given port is an AttachmentPoint
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).
                                              andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).
                                              andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1)).
                                              andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(4L, (short)1)).
                                              andReturn(true).anyTimes();

        /*
         * Mock up routing engine
         */
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, 0)).
                                      andReturn(routeD1ToGW1).times(1);
        expect(routingEngine.getRoute(1L, (short)1, 4L, (short)1, 0)).
                                      andReturn(routeD1ToGW2).times(1);
        expect(routingEngine.getRoute(2L, (short)1, 3L, (short)1, 0)).
                                      andReturn(routeD2ToGW1).times(1);
        expect(routingEngine.getRoute(2L, (short)1, 4L, (short)1, 0)).
                                      andReturn(routeD2ToGW2).times(1);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
                gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode2Ip, selectedGwIP2);
    }

    /*
     * Test the node selection with device and gateway nodes in different clusters
     *
     *          +-------+                                        +-------+
     *          |       |                                        |       |
     *          |   GN1 |                                        |  GN2  |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   6  2|----------------------------------------|2  7   |
     *          |   3   |                                        |       |
     *          +-------+                                        +-------+
     *              |
     *              |
     *          +-------+
     *          |   2   |
     *          |   5   |
     *          |   1   |
     *          +-------+
     *              |
     *              |
     *              |
     *          {  NOF  }
     *              |
     *              |
     *              |
     *          +-------+                                        +-------+
     *          |   1   |                                        |       |
     *          |   3  2|----------------------------------------|2  4   |
     *          |   3   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   1   |                                        |   2   |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |Host 1 |                                        |Host 2 |
     *          |       |                                        |       |
     *          +-------+                                        +-------+
     */
    @Test
    public void testGetOptimalGatewayNodeTwoClusters() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(5L).anyTimes();
        replay(topology);

        /*
         * Setup device1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                5L, 1, false);

        /*
         * Setup device2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 1, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                5L, 1, false);

        /*
         * Setup gatewayNode1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 6L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 3L, 1,
                                                     false);

        /*
         * Setup gatewayNode2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 7L, 1,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 3L, 1,
                                                     false);

        setup2NodeGatewayPool();


        /*
         * Setup mock routes
         */
        Route routeD1ToGW11 = new Route(device1.getMACAddress(),
                                        gatewayNode1.getMACAddress());
        routeD1ToGW11.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGW11.getPath().add(new NodePortTuple(1L, (short)2));
        routeD1ToGW11.getPath().add(new NodePortTuple(3L, (short)3));
        routeD1ToGW11.getPath().add(new NodePortTuple(3L, (short)1));

        Route routeD1ToGW51 = new Route(device1.getMACAddress(),
                                        gatewayNode1.getMACAddress());
        routeD1ToGW51.getPath().add(new NodePortTuple(5L, (short)1));
        routeD1ToGW51.getPath().add(new NodePortTuple(5L, (short)2));
        routeD1ToGW51.getPath().add(new NodePortTuple(6L, (short)3));
        routeD1ToGW51.getPath().add(new NodePortTuple(6L, (short)1));

        Route routeD1ToGW52 = new Route(device1.getMACAddress(),
                                        gatewayNode2.getMACAddress());
        routeD1ToGW52.getPath().add(new NodePortTuple(5L, (short)1));
        routeD1ToGW52.getPath().add(new NodePortTuple(5L, (short)2));
        routeD1ToGW52.getPath().add(new NodePortTuple(6L, (short)3));
        routeD1ToGW52.getPath().add(new NodePortTuple(6L, (short)2));
        routeD1ToGW52.getPath().add(new NodePortTuple(7L, (short)2));
        routeD1ToGW52.getPath().add(new NodePortTuple(7L, (short)1));

        Route routeD2ToGW11 = new Route(device2.getMACAddress(),
                                        gatewayNode1.getMACAddress());
        routeD2ToGW11.getPath().add(new NodePortTuple(2L, (short)1));
        routeD2ToGW11.getPath().add(new NodePortTuple(2L, (short)2));
        routeD2ToGW11.getPath().add(new NodePortTuple(4L, (short)1));
        routeD2ToGW11.getPath().add(new NodePortTuple(4L, (short)2));
        routeD2ToGW11.getPath().add(new NodePortTuple(3L, (short)2));
        routeD2ToGW11.getPath().add(new NodePortTuple(3L, (short)1));

        /*
         * ReMock up topology to return L2DomainId and determine if
         * a given switch,port is an attachment point.
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(5L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(5L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(6L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(7L, (short)1)).
               andReturn(true).anyTimes();

        /*
         * Mock routing engine to return the appropriate mock routes
         */
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, 0)).
               andReturn(routeD1ToGW11).times(2);
        expect(routingEngine.getRoute(2L, (short)1, 3L, (short)1, 0)).
               andReturn(routeD2ToGW11).times(2);
        expect(routingEngine.getRoute(5L, (short)1, 6L, (short)1, 0)).
               andReturn(routeD1ToGW51).times(2);
        expect(routingEngine.getRoute(5L, (short)1, 7L, (short)1, 0)).
               andReturn(routeD1ToGW52).times(2);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
                gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode1Ip, selectedGwIP2);
    }

    /*
     * Test the node selection with device & gateway nodes in disjoint clusters
     *
     *          +-------+                                        +-------+
     *          |       |                                        |       |
     *          |   SN1 |                                        |  SN2  |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   6   |                                        |   7   |
     *          |   2   |                                        |   2   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   5   |                                        |   8   |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *              |                                                |
     *          {  NOF  }                                        {  NOF  }
     *              |                                                |
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   3   |                                        |   4   |
     *          |   2   |                                        |   2   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   1   |                                        |   2   |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |Host 1 |                                        |Host 2 |
     *          |       |                                        |       |
     *          +-------+                                        +-------+
     */
    @Test
    public void testGetOptimalGatewayDisjointClusters() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(7L).anyTimes();
        expect(topology.getL2DomainId(8L)).andReturn(7L).anyTimes();
        replay(topology);

        /*
         * Setup device1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                5L, 1, false);

        /*
         * Setup device2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 1, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                8L, 1, false);

        /*
         * Setup gatewayNode1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 6L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 3L, 1,
                                                     false);

        /*
         * Setup gatewayNode2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 7L, 1,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 4L, 1,
                                                     false);

        setup2NodeGatewayPool();

        /*
         * Setup the mock routes
         */
        Route routeD1ToGW11 = new Route(device1.getMACAddress(),
                                        gatewayNode1.getMACAddress());
        routeD1ToGW11.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGW11.getPath().add(new NodePortTuple(1L, (short)2));
        routeD1ToGW11.getPath().add(new NodePortTuple(3L, (short)2));
        routeD1ToGW11.getPath().add(new NodePortTuple(3L, (short)1));

        Route routeD1ToGW51 = new Route(device1.getMACAddress(),
                                        gatewayNode1.getMACAddress());
        routeD1ToGW51.getPath().add(new NodePortTuple(5L, (short)1));
        routeD1ToGW51.getPath().add(new NodePortTuple(5L, (short)2));
        routeD1ToGW51.getPath().add(new NodePortTuple(6L, (short)2));
        routeD1ToGW51.getPath().add(new NodePortTuple(6L, (short)1));

        Route routeD2ToS22 = new Route(device2.getMACAddress(),
                                       gatewayNode2.getMACAddress());
        routeD2ToS22.getPath().add(new NodePortTuple(2L, (short)1));
        routeD2ToS22.getPath().add(new NodePortTuple(2L, (short)2));
        routeD2ToS22.getPath().add(new NodePortTuple(4L, (short)2));
        routeD2ToS22.getPath().add(new NodePortTuple(4L, (short)1));

        Route routeD2ToGW72 = new Route(device2.getMACAddress(),
                                        gatewayNode2.getMACAddress());
        routeD2ToGW72.getPath().add(new NodePortTuple(8L, (short)1));
        routeD2ToGW72.getPath().add(new NodePortTuple(8L, (short)2));
        routeD2ToGW72.getPath().add(new NodePortTuple(7L, (short)2));
        routeD2ToGW72.getPath().add(new NodePortTuple(7L, (short)1));

        /*
         * ReMock up topology to return the L2DomainId and determine if a
         * given switch port is an Attachment point
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(7L).anyTimes();
        expect(topology.getL2DomainId(8L)).andReturn(7L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(4L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(5L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(6L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(7L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(8L, (short)1)).
               andReturn(true).anyTimes();

        /*
         * Mock up routing engine to return appropriate mock routes
         */
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, 0)).
               andReturn(routeD1ToGW11).times(1);
        expect(routingEngine.getRoute(2L, (short)1, 4L, (short)1, 0)).
               andReturn(routeD2ToS22).times(1);
        expect(routingEngine.getRoute(5L, (short)1, 6L, (short)1, 0)).
               andReturn(routeD1ToGW51).times(1);
        expect(routingEngine.getRoute(8L, (short)1, 7L, (short)1, 0)).
               andReturn(routeD2ToGW72).times(1);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
                gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode2Ip, selectedGwIP2);
    }

    /*
     * Test the node selection with device and gateway nodes in multi-clusters
     *
     *          +-------+                                        +-------+
     *          |       |                                        |       |
     *          |   GW1 |                                        |  GW2  |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   6   |                                        |   7   |
     *          |   2   |                                        |   2   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   5  3|\                                       |   8   |
     *          |   1   | \                                      |   1   |
     *          +-------+  \                                     +-------+
     *              |       \                                        |
     *              |        \                                       |
     *              |         \                                      |
     *          {  NOF  }      \---------------------------------{  NOF  }
     *              |                                                |
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   3   |                                        |   4   |
     *          |   2   |                                        |   2   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   1   |                                        |   2   |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |Host 1 |                                        |Host 2 |
     *          |       |                                        |       |
     *          +-------+                                        +-------+
     */
    @Test
    public void testGetOptimalGatewayMultiClusters() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(7L).anyTimes();
        expect(topology.getL2DomainId(8L)).andReturn(7L).anyTimes();
        expect(topology.isBroadcastDomainPort(3L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(5L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(4L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(8L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(2L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(6L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(7L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(5L, (short)3)).
               andReturn(false).anyTimes();
        replay(topology);

        /*
         * Setup device1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                5L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                4L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                8L, 1, false);

        /*
         * Setup device2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 1, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                5L, 3, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                3L, 1, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                8L, 1, false);

        /*
         * Setup gatewayNode1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 6L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 3L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 4L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 8L, 1,
                                                     false);

        /*
         * Setup gatewayNode2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 7L, 1,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 3L, 1,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 5L, 3,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 4L, 1,
                                                     false);

        setup2NodeGatewayPool();

        /*
         * Setup mock routes
         */
        Route route13 = new Route(device1.getMACAddress(),
                                  gatewayNode1.getMACAddress());
        route13.getPath().add(new NodePortTuple(1L, (short)1));
        route13.getPath().add(new NodePortTuple(1L, (short)2));
        route13.getPath().add(new NodePortTuple(3L, (short)2));
        route13.getPath().add(new NodePortTuple(3L, (short)1));

        Route route51_61 = new Route(device1.getMACAddress(),
                                     gatewayNode1.getMACAddress());
        route51_61.getPath().add(new NodePortTuple(5L, (short)1));
        route51_61.getPath().add(new NodePortTuple(5L, (short)2));
        route51_61.getPath().add(new NodePortTuple(6L, (short)2));
        route51_61.getPath().add(new NodePortTuple(6L, (short)1));

        Route route53_61 = new Route(device2.getMACAddress(),
                                     gatewayNode1.getMACAddress());
        route53_61.getPath().add(new NodePortTuple(5L, (short)3));
        route53_61.getPath().add(new NodePortTuple(5L, (short)2));
        route53_61.getPath().add(new NodePortTuple(6L, (short)2));
        route53_61.getPath().add(new NodePortTuple(6L, (short)1));

        Route route51_53 = new Route(device1.getMACAddress(),
                                     gatewayNode2.getMACAddress());
        route51_53.getPath().add(new NodePortTuple(5L, (short)1));
        route51_53.getPath().add(new NodePortTuple(5L, (short)3));

        Route route24 = new Route(device2.getMACAddress(),
                                  gatewayNode2.getMACAddress());
        route24.getPath().add(new NodePortTuple(2L, (short)1));
        route24.getPath().add(new NodePortTuple(2L, (short)2));
        route24.getPath().add(new NodePortTuple(4L, (short)2));
        route24.getPath().add(new NodePortTuple(4L, (short)1));

        Route route78 = new Route(device2.getMACAddress(),
                                  gatewayNode2.getMACAddress());
        route78.getPath().add(new NodePortTuple(8L, (short)1));
        route78.getPath().add(new NodePortTuple(8L, (short)2));
        route78.getPath().add(new NodePortTuple(7L, (short)2));
        route78.getPath().add(new NodePortTuple(7L, (short)1));

        /*
         * Re Mock up topology to return the correct L2 Domain ID and to
         * determine if a given switch port is an attachment point
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(7L).anyTimes();
        expect(topology.getL2DomainId(8L)).andReturn(7L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(4L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(5L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(5L, (short)3)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(6L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(7L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(8L, (short)1)).andReturn(true).
               anyTimes();

        /*
         * Mock up routing engine to return the appropriate mock routes
         */
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, 0)).
               andReturn(route13).times(2);
        expect(routingEngine.getRoute(2L, (short)1, 4L, (short)1, 0)).
               andReturn(route24).times(2);
        expect(routingEngine.getRoute(5L, (short)1, 6L, (short)1, 0)).
               andReturn(route51_61).times(1);
        expect(routingEngine.getRoute(5L, (short)1, 5L, (short)3, 0)).
               andReturn(route51_53).times(1);
        expect(routingEngine.getRoute(5L, (short)3, 6L, (short)1, 0)).
               andReturn(route53_61).times(1);
        expect(routingEngine.getRoute(8L, (short)1, 7L, (short)1, 0)).
               andReturn(route78).times(2);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
                gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode1Ip, selectedGwIP2);
    }

    /*
     * Test the node selection with device and gateway nodes in multi-clusters
     *
     *          +-------+                                        +-------+
     *          |       |                                        |       |
     *          |   GW1 |                                        |  GW2  |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   6   |                                        |   7   |
     *          |   2   |                                        |   2   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                            |
     *          |   1   |                                            |
     *          |   9   |                                            |
     *          |   2   |                                            |
     *          +-------+                                            |
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   5  3|\                                       |   8   |
     *          |   1   | \                                      |   1   |
     *          +-------+  \                                     +-------+
     *              |       \                                        |
     *              |        \                                       |
     *              |         \                                      |
     *          {  NOF  }      \---------------------------------{  NOF  }
     *              |                                                |
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |   3   |                                        |   4   |
     *          |   2   |                                        |   2   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   2   |                                        |   2   |
     *          |   1   |                                        |   2   |
     *          |   1   |                                        |   1   |
     *          +-------+                                        +-------+
     *              |                                                |
     *              |                                                |
     *          +-------+                                        +-------+
     *          |   1   |                                        |   1   |
     *          |Host 1 |                                        |Host 2 |
     *          |       |                                        |       |
     *          +-------+                                        +-------+
     */
    @Test
    public void testGetOptimalGatewayMultiClusters2() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(9L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(7L).anyTimes();
        expect(topology.getL2DomainId(8L)).andReturn(7L).anyTimes();
        expect(topology.isBroadcastDomainPort(3L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(5L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(4L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(8L, (short)1)).
               andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(2L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(6L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(7L, (short)1)).
               andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(5L, (short)3)).
               andReturn(false).anyTimes();
        replay(topology);

        /*
         * Setup device1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                5L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                4L, 1, false);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                8L, 1, false);

        /*
         * Setup device2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 1, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                5L, 3, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                3L, 1, false);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                8L, 1, false);

        /*
         * Setup gatewayNode1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 6L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 3L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 4L, 1,
                                                     false);
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 8L, 1,
                                                     false);

        /*
         * Setup gatewayNode2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 7L, 1,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 3L, 1,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 5L, 3,
                                                     false);
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 4L, 1,
                                                     false);
        setup2NodeGatewayPool();

        /*
         * Setup mock routes
         */
        Route route13 = new Route(device1.getMACAddress(),
                                  gatewayNode1.getMACAddress());
        route13.getPath().add(new NodePortTuple(1L, (short)1));
        route13.getPath().add(new NodePortTuple(1L, (short)2));
        route13.getPath().add(new NodePortTuple(3L, (short)2));
        route13.getPath().add(new NodePortTuple(3L, (short)1));

        Route route51_61 = new Route(device1.getMACAddress(),
                                     gatewayNode1.getMACAddress());
        route51_61.getPath().add(new NodePortTuple(5L, (short)1));
        route51_61.getPath().add(new NodePortTuple(5L, (short)2));
        route51_61.getPath().add(new NodePortTuple(9L, (short)2));
        route51_61.getPath().add(new NodePortTuple(9L, (short)1));
        route51_61.getPath().add(new NodePortTuple(6L, (short)2));
        route51_61.getPath().add(new NodePortTuple(6L, (short)1));

        Route route53_61 = new Route(device2.getMACAddress(),
                                     gatewayNode1.getMACAddress());
        route53_61.getPath().add(new NodePortTuple(5L, (short)3));
        route53_61.getPath().add(new NodePortTuple(5L, (short)2));
        route53_61.getPath().add(new NodePortTuple(9L, (short)2));
        route53_61.getPath().add(new NodePortTuple(9L, (short)1));
        route53_61.getPath().add(new NodePortTuple(6L, (short)2));
        route53_61.getPath().add(new NodePortTuple(6L, (short)1));

        Route route51_53 = new Route(device1.getMACAddress(),
                                     gatewayNode2.getMACAddress());
        route51_53.getPath().add(new NodePortTuple(5L, (short)1));
        route51_53.getPath().add(new NodePortTuple(5L, (short)3));

        Route route24 = new Route(device2.getMACAddress(),
                                  gatewayNode2.getMACAddress());
        route24.getPath().add(new NodePortTuple(2L, (short)1));
        route24.getPath().add(new NodePortTuple(2L, (short)2));
        route24.getPath().add(new NodePortTuple(4L, (short)2));
        route24.getPath().add(new NodePortTuple(4L, (short)1));

        Route route78 = new Route(device2.getMACAddress(),
                                  gatewayNode2.getMACAddress());
        route78.getPath().add(new NodePortTuple(8L, (short)1));
        route78.getPath().add(new NodePortTuple(8L, (short)2));
        route78.getPath().add(new NodePortTuple(7L, (short)2));
        route78.getPath().add(new NodePortTuple(7L, (short)1));

        /*
         * ReMock up topology to return L2DomainId and determine if a given
         * switch port is an attachment point
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(4L)).andReturn(2L).anyTimes();
        expect(topology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(6L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(9L)).andReturn(5L).anyTimes();
        expect(topology.getL2DomainId(7L)).andReturn(7L).anyTimes();
        expect(topology.getL2DomainId(8L)).andReturn(7L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(4L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(5L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(5L, (short)3)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(6L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(7L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(8L, (short)1)).andReturn(true).
               anyTimes();

        /*
         * Mock up topology to return appropriate mock routes
         */
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, 0)).
               andReturn(route13).times(2);
        expect(routingEngine.getRoute(2L, (short)1, 4L, (short)1, 0)).
               andReturn(route24).times(2);
        expect(routingEngine.getRoute(5L, (short)1, 6L, (short)1, 0)).
               andReturn(route51_61).times(1);
        expect(routingEngine.getRoute(5L, (short)1, 5L, (short)3, 0)).
               andReturn(route51_53).times(1);
        expect(routingEngine.getRoute(5L, (short)3, 6L, (short)1, 0)).
               andReturn(route53_61).times(1);
        expect(routingEngine.getRoute(8L, (short)1, 7L, (short)1, 0)).
               andReturn(route78).times(2);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
                gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode2Ip, selectedGwIP2);
    }

    /*
     *
     * Physical Topology
     * -----------------
     *
     *               +--------+
     *    -----------| L3 NOF |--------
     *    |          |   Agg  |       |
     *    |          +--------+       |
     * +-------+                   +-------+
     * | L3 NOF|                   | L3 NOF|
     * | TOR   |                   | TOR   |
     * |       |                   | (GW1) |
     * +-------+                   +-------+
     *     |                           |
     *     |                           |
     * +-------+                   +-------+
     * |  2    |                   |   1   |
     * |  S1   |                   |  S2   |
     * |  1    |                   |       |
     * +-------+                   +-------+
     *    |
     *  Host1
     *
     * Logical Topology (w/o Tunnels)
     * ------------------------------
     *
     *                             +-----+
     *                             | GW1 |
     *                             +-----+
     *                                |
     *                                |
     * +-----+                     +-----+
     * | NOF |                     | NOF |
     * +-----+                     +-----+
     *    |                           |
     * +-----+                     +-----+
     * |  2  |                     |  1  |
     * |  S1 |                     |  S2 |
     * |  1  |                     |     |
     * +-----+                     +-----+
     *    |
     *  Host1
     *
     * Logical Topology (with Tunnels)
     * -------------------------------
     *
     *                             +-----+
     *                             | GW1 |
     *                             +-----+
     *                                |
     *                                |
     * +-----+                     +-----+
     * | NOF |                     | NOF |
     * +-----+                     +-----+
     *    |                           |
     *    |                           |
     * +-----+                     +-----+
     * |  2  |                     |  1  |
     * |  S1 |     Tunnel Link     |  S2 |
     * |  3  |---------------------|  2  |
     * |  1  |                     |     |
     * +-----+                     +-----+
     *    |
     *  Host1
     *
     * Test Notes(testGetOptimalGatewayOnlyTunnelPathAvailable):
     *
     * Gateway Pool            : has only one Gateway Node (GW1)
     * No Tunnel topology (P1) : No path from Host1 to GW1
     * Tunnel topology (P2)    : H1 --> S1:1 --> S1:3 --> S2:2 --> S2:1 --> GW1
     *
     * The objective of this test is to ensure that when there is no path
     * available from the host to the gateway node in the regular topology
     * (w/o tunnels i.e., P1 = nil), the tunnel topology instance is checked
     * and the path P2 is used to determine that GW1 is the only and optimal
     * choice as the Gateway Node for the source device corresponding to H1
     *
     */
    public void testGetOptimalGatewayOnlyTunnelPathAvailable() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        replay(topology);

        /*
         * Setup device1 corresponding to Host1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);

        /*
         * Setup gatewayDeviceNode corresponding to GW1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 2L, 1,
                                                     false);
        setup1NodeGatewayPool();

        /*
         * Setup mock route for use in the tunnel topology route lookup
         */
        Route routeD1ToGW1 = new Route(device1.getMACAddress(),
                                       gatewayNode1.getMACAddress());
        routeD1ToGW1.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGW1.getPath().add(new NodePortTuple(1L, (short)3));
        routeD1ToGW1.getPath().add(new NodePortTuple(2L, (short)2));
        routeD1ToGW1.getPath().add(new NodePortTuple(2L, (short)1));

        /*
         * ReMock up topology to return L2DomainId and determine if a given
         * switch port is an attachment point
         * Note: When consulting the tunnel topology, both S1 and S2 belong
         * to the same cluster.
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).andReturn(true).
               anyTimes();

        /*
         * Setup the routing engine to return the mock route
         */
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)1, 0)).
               andReturn(routeD1ToGW1).times(1);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
    }

    /*
     *
     * Physical Topology
     * -----------------
     *
     *               +--------+
     *    -----------| L3 NOF |--------
     *    |          |   Agg  |       |
     *    |          +--------+       |
     * +-------+                   +-------+
     * | L3 NOF|                   | L3 NOF|
     * | TOR   |                   | TOR   |
     * | (GW2) |                   | (GW1) |
     * +-------+                   +-------+
     *     |                           |
     *     |                           |
     * +-------+                   +-------+
     * |  2    |                   |   1   |
     * |  S1   |                   |  S2   |
     * |  1    |                   |   2   |
     * +-------+                   +-------+
     *    |                            |
     *  Host1                        Host2
     *
     * Logical Topology (w/o Tunnels)
     * ------------------------------
     *
     * +-----+                     +-----+
     * | GW2 |                     | GW1 |
     * +-----+                     +-----+
     *    |                           |
     *    |                           |
     * +-----+                     +-----+
     * | NOF |                     | NOF |
     * +-----+                     +-----+
     *    |                           |
     * +-----+                     +-----+
     * |  2  |                     |  1  |
     * |  S1 |                     |  S2 |
     * |  1  |                     |  2  |
     * +-----+                     +-----+
     *    |                           |
     *  Host1                       Host2
     *
     * Logical Topology (with Tunnels)
     * -------------------------------
     *
     * +-----+                     +-----+
     * | GW1 |                     | GW2 |
     * +-----+                     +-----+
     *    |                           |
     *    |                           |
     * +-----+                     +-----+
     * | NOF |                     | NOF |
     * +-----+                     +-----+
     *    |                           |
     *    |                           |
     * +-----+                     +-----+
     * |  2  |                     |  1  |
     * |  S1 |     Tunnel Link     |  S2 |
     * |  3  |---------------------|  3  |
     * |  1  |                     |  2  |
     * +-----+                     +-----+
     *    |                           |
     *  Host1                       Host2
     *
     * Test Notes(testGetOptimalGatewayTunnelPathAlsoAvailable):
     *
     * Gateway Pool            : has 2 Gateway Nodes (GW1, GW2)
     *
     * The objective of this test is to ensure that when there is a path
     * available from the host to the gateway node in the regular topology
     * (w/o tunnels), the tunnel topology instance is skipped
     * and the path in the tunnel topology is not computed.
     * Therefore, for H1, the optimal gateway node is GW1 and for H2,
     * the optimal gateway node is GW2.
     */
    public void testGetOptimalGatewayTunnelPathAlsoAvailable() {
        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)2)).andReturn(true).
               anyTimes();
        replay(topology);

        /*
         * Setup device1 corresponding to Host1
         */
        String macDevice1Str = "aa:bb:cc:dd:ee:01";
        long macDevice1 = Ethernet.toLong(Ethernet.toMACAddress(macDevice1Str));
        String ipDevice1Str = "192.168.2.10";
        int ipDevice1 = IPv4.toIPv4Address(ipDevice1Str);
        device1 = mockDeviceManager.learnEntity(macDevice1, null, ipDevice1,
                                                1L, 1, false);

        /*
         * Setup device2 corresponding to Host2
         */
        String macDevice2Str = "aa:bb:cc:dd:ee:02";
        long macDevice2 = Ethernet.toLong(Ethernet.toMACAddress(macDevice2Str));
        String ipDevice2Str = "192.168.2.20";
        int ipDevice2 = IPv4.toIPv4Address(ipDevice2Str);
        device2 = mockDeviceManager.learnEntity(macDevice2, null, ipDevice2,
                                                2L, 2, false);

        /*
         * Setup gatewayDeviceNode corresponding to GW1
         */
        gatewayNode1 = mockDeviceManager.learnEntity(gatewayNode1Mac, null,
                                                     gatewayNode1Ip, 1L, 2,
                                                     false);

        /*
         * Setup gatewayDeviceNode corresponding to GW2
         */
        gatewayNode2 = mockDeviceManager.learnEntity(gatewayNode2Mac, null,
                                                     gatewayNode2Ip, 2L, 1,
                                                     false);
        setup2NodeGatewayPool();

        /*
         * Setup mock route for use in the tunnel topology route lookup
         */
        Route routeD1ToGW1 = new Route(device1.getMACAddress(),
                                       gatewayNode1.getMACAddress());
        routeD1ToGW1.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGW1.getPath().add(new NodePortTuple(1L, (short)2));
        Route routeD2ToGW2 = new Route(device2.getMACAddress(),
                                       gatewayNode2.getMACAddress());
        routeD2ToGW2.getPath().add(new NodePortTuple(2L, (short)2));
        routeD2ToGW2.getPath().add(new NodePortTuple(2L, (short)1));

        /*
         * ReMock up topology to return L2DomainId and determine if a given
         * switch port is an attachment point
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(2L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2)).andReturn(true).
               anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)2)).andReturn(true).
               anyTimes();

        /*
         * Setup the routing engine to return the mock route
         */
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)2, 0)).
               andReturn(routeD1ToGW1).times(1);
        expect(routingEngine.getRoute(2L, (short)2, 2L, (short)1, 0)).
               andReturn(routeD2ToGW2).times(1);
        replay(topology, routingEngine);

        /*
         * Now the test
         */
        int selectedGwIP1 =
                gatewayPool.getOptimalGatewayNodeInfo(device1, null).getIp();
        int selectedGwIP2 =
                gatewayPool.getOptimalGatewayNodeInfo(device2, null).getIp();
        /*
         * Verify
         */
        verify(topology, routingEngine);
        assertEquals(gatewayNode1Ip, selectedGwIP1);
        assertEquals(gatewayNode2Ip, selectedGwIP2);
    }
}
