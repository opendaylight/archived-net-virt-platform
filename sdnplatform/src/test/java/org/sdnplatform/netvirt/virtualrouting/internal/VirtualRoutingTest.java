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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.sdnplatform.addressspace.BetterEntityClass;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IListener.Command;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.core.util.MutableInteger;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;
import org.sdnplatform.devicemanager.internal.BetterDeviceManagerImpl;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.internal.Entity;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.OFMatchReconcile;
import org.sdnplatform.flowcache.OFMatchReconcile.ReconcileAction;
import org.sdnplatform.forwarding.IForwardingService;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.forwarding.RewriteServiceImpl;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.BroadcastMode;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.VirtualMACExhaustedException;
import org.sdnplatform.netvirt.virtualrouting.IARPListener.ARPCommand;
import org.sdnplatform.netvirt.virtualrouting.IICMPListener.ICMPCommand;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouterManager;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.ARP;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.LLDP;
import org.sdnplatform.packet.LLDPTLV;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;


@SuppressWarnings("unchecked")
public class VirtualRoutingTest extends PlatformTestCase {
    private INetVirtManagerService netVirtManager;
    private MockDeviceManager mockDeviceManager;
    private ITunnelManagerService tunnelManager;
    private MemoryStorageSource storageSource;
    private VirtualRouting virtualRouting;
    private BetterDeviceManagerImpl tagManager;
    private ModuleContext fmc;
    protected IFlowReconcileService flowReconcileMgr;
    protected ITopologyService topology;
    private IRoutingService routingEngine;
    private IForwardingService forwarding;
    private RewriteServiceImpl rewriteService;
    private IFlowCacheService betterFlowCacheMgr;
    private ILinkDiscoveryService linkDiscovery;

    private IOFSwitch mockSwitch;

    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    protected IPacket testPacketUnknownDest;
    protected byte[] testPacketUnknownDestSerialized;
    protected IPacket broadcastPacket;
    protected byte[] broadcastPacketSerialized;
    protected IPacket multicastPacket;
    protected byte[] multicastPacketSerialized;
    protected IPacket lldpPacket;
    protected byte[] lldpPacketSerialized;
    long dev1m, dev2m;
    protected IDevice dev1, dev2;

    private VNS netVirt[];
    private final int nNetVirtes = 100;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        tunnelManager = createMock(ITunnelManagerService.class);
        virtualRouting = new VirtualRouting();
        tagManager = new BetterDeviceManagerImpl();
        netVirtManager = createNiceMock(INetVirtManagerService.class);

        topology = createMock(ITopologyService.class);
        routingEngine = createNiceMock(IRoutingService.class);
        flowReconcileMgr = createNiceMock(IFlowReconcileService.class);
        RestApiServer ras = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        forwarding = createMock(IForwardingService.class);
        rewriteService = new RewriteServiceImpl();
        betterFlowCacheMgr = createNiceMock(IFlowCacheService.class);
        linkDiscovery = createMock(ILinkDiscoveryService.class);

        fmc = new ModuleContext();
        fmc.addService(IControllerService.class, mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(ITagManagerService.class, tagManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IForwardingService.class, forwarding);
        fmc.addService(IRewriteService.class, rewriteService);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(ILinkDiscoveryService.class, linkDiscovery);
        fmc.addService(ITunnelManagerService.class, tunnelManager);

        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        tagManager.init(fmc);
        virtualRouting.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);
        rewriteService.init(fmc);

        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        tagManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        rewriteService.startUp(fmc);

        // Build our test packet
        this.testPacket = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        this.testPacketUnknownDest = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:66")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.3")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketUnknownDestSerialized = testPacketUnknownDest.serialize();

        // Build a broadcast packet
        this.broadcastPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.255.255")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.broadcastPacketSerialized = broadcastPacket.serialize();

        // Build a broadcast packet
        this.multicastPacket = new Ethernet()
        .setDestinationMACAddress("01:00:5e:01:02:03")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 1)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("224.1.2.3")
                .setPayload(new UDP()
                .setSourcePort((short) 4567)
                .setDestinationPort((short) 4567)
                .setPayload(new Data(new byte[] {0x74, 0x65, 0x73, 0x74, 0x0a}))));
        this.multicastPacketSerialized = multicastPacket.serialize();

        // Build a broadcast packet
        this.lldpPacket = new Ethernet()
        .setDestinationMACAddress("01:80:c2:00:00:0e")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setEtherType(Ethernet.TYPE_LLDP)
        .setPayload(
                new LLDP()
                .setChassisId(new LLDPTLV()
                .setType((byte) 1)
                .setLength((byte) 7)
                .setValue(new byte[] {0x4, 0x0, 0x0, 0x0, 0x73, 0x28, 0x3}))
                .setPortId(new LLDPTLV()
                .setType((byte) 2)
                .setLength((byte) 3)
                .setValue(new byte[] {0x2, 0x0, 0x18}))
                .setTtl(new LLDPTLV()
                .setType((byte) 3)
                .setLength((byte) 2)
                .setValue(new byte[] {0x0, 0x78}))
                );
        this.lldpPacketSerialized = lldpPacket.serialize();

        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1)).andReturn(1L).anyTimes();

        replay(topology);

        dev1m = Ethernet.toLong(((Ethernet)testPacket).
                                getSourceMACAddress());
        dev2m = Ethernet.toLong(((Ethernet)testPacket).
                                getDestinationMACAddress());
        dev1 = mockDeviceManager.learnEntity(dev1m, null, null, 1L, 1);
        dev2 = mockDeviceManager.learnEntity(dev2m, null, null, 1L, 2);

        netVirt = new VNS[nNetVirtes];
        for (int n = 0; n < nNetVirtes; n++) {
            netVirt[n] = new VNS("tt1|NetVirt" + n);
            netVirt[n].setPriority(n);
        }

        mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        mockControllerProvider.setSwitches(switches);

        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                            .andReturn(false).anyTimes();
        replay(tunnelManager);
    }

    protected VirtualRouting getVirtualRouting() {
        return virtualRouting;
    }

    /**
     * Common mockup environment for unicast tests
     */
    private void testUnicastInternal(ListenerContext cntx,
                                     RoutingAction action) throws Exception {
        OFPacketIn pi;

        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        // build out input packet
        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        // Start recording the replay on the mocks
        replay(mockSwitch);

        // Trigger the packet in
        routing.receive(mockSwitch, pi, parseAndAnnotate(cntx, pi, dev1, dev2));

        // Get the annotation output for verification
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);

        // Verify the replay matched our expectations
        verify(mockSwitch);

        assertTrue(d.getRoutingAction() == action);
        assertTrue(d.getSourcePort().getSwitchDPID() == 1L);
        assertTrue(d.getSourcePort().getPort() == pi.getInPort());
        assertEquals(d.getSourceDevice(), dev1);
        if (dev2 != null)
            assertEquals(true, d.getDestinationDevices().contains(dev2));
    }

    private void verifyContext(ListenerContext cntx, VNS srcNetVirt, VNS dstNetVirt) {
        List<VNSInterface> srcIfaces, dstIfaces;
        srcIfaces = INetVirtManagerService.bcStore.get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        dstIfaces = INetVirtManagerService.bcStore.get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);

        if (srcNetVirt == null) {
            assertTrue(srcIfaces == null || srcIfaces.size() == 0);
            assertTrue(dstIfaces == null || dstIfaces.size() == 0);
        } else {
            assertTrue(srcIfaces.size() == 1);
            assertTrue(srcIfaces.get(0).getParentVNS() == srcNetVirt);
            if (dstIfaces != null) {
                assertTrue(dstIfaces.size() == 1);
                assertTrue(dstIfaces.get(0).getParentVNS() == dstNetVirt);
            }
        }
    }

    private void verifyContext(ListenerContext cntx, VNS netVirt) {
        verifyContext(cntx, netVirt, netVirt);
    }

    /**
     * No annotation, should drop
     */
    @Test
    public void testUnicastNoAnnotation() throws Exception {
        ListenerContext cntx = new ListenerContext();

        testUnicastInternal(cntx, RoutingAction.DROP);

        verifyContext(cntx, null);
    }

    /**
     * Same source and destination NetVirt, should forward
     */
    @Test
    public void testUnicastPass() throws Exception {
        ListenerContext cntx = new ListenerContext();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);

        testUnicastInternal(cntx, RoutingAction.FORWARD);

        verifyContext(cntx, netVirt[0]);
    }

    /**
     * Devices in the same NetVirt
     * @throws Exception
     */
    @Test
    public void testChooseNetVirtSameNetVirt() throws Exception {
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        expect(netVirtManager.getInterfaces(dev1)).andReturn(srcIfaces).times(1);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2", netVirt[0], null, null));
        expect(netVirtManager.getInterfaces(dev2)).andReturn(dstIfaces).times(1);

        replay(netVirtManager);
        boolean ret = virtualRouting.connected(dev1, 0, dev2, 0);
        verify(netVirtManager);

        assertTrue(ret);
    }

    /**
     * Devices in the same NetVirt
     * @throws Exception
     */
    @Test
    public void testChooseNetVirtDifferentNetVirt() throws Exception {
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        expect(netVirtManager.getInterfaces(dev1)).andReturn(srcIfaces).times(1);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2", netVirt[1], null, null));
        expect(netVirtManager.getInterfaces(dev2)).andReturn(dstIfaces).times(1);

        replay(netVirtManager);
        boolean ret = virtualRouting.connected(dev1, 0, dev2, 0);
        verify(netVirtManager);

        assertFalse(ret);
    }

    /**
     * Difference source and destination NetVirt, should drop
     */
    @Test
    public void testUnicastDrop() throws Exception {
        ListenerContext cntx = new ListenerContext();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[1], null, null));
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2", netVirt[2], null, null));
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);

        testUnicastInternal(cntx, RoutingAction.DROP);

        verifyContext(cntx, null);
    }

    /**
     * Multiple source and destination NetVirtes with one overlap,
     * should forward.
     */
    @Test
    public void testUnicastPassMulti() throws Exception {
        int nChosen = 50;
        ListenerContext cntx = new ListenerContext();


        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 10; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null, null));
        }
        srcIfaces.add(new VNSInterface("testSrcIface1x" + nChosen, netVirt[nChosen], null, null));
        for (int n = 21; n < 40; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 70; n < 80; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null, null));
        }
        dstIfaces.add(new VNSInterface("testSrcIface2x" + nChosen, netVirt[nChosen], null, null));
        for (int n = 81; n < 95; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);

        testUnicastInternal(cntx, RoutingAction.FORWARD);

        verifyContext(cntx, netVirt[nChosen]);
    }

    /**
     * Multiple source and destination NetVirtes with one pair connected by virtual
     * routing. Should forward.
     */
    @Test
    public void testVRUnicastPassMulti() throws Exception {
        int srcChosen = 5;
        int dstChosen = 75;
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[srcChosen].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[dstChosen].getName(), null);
        Map<String, Object> rr1 = h.createRoutingRule(r1, "tt1", null, null,
                                                      null, "tt1", null, null,
                                                      null, null, null,
                                                      "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 10; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null,
                                           null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 70; n < 80; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null,
                                           null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);

        /* NetVirt5 and NetVirt75 are connected via a router so we should see FORWARD */
        testUnicastInternal(cntx, RoutingAction.FORWARD);
        verifyContext(cntx, netVirt[srcChosen], netVirt[dstChosen]);
    }

    @Test
    public void testVRUnicastPassMultiL3() throws Exception {
        int srcChosen = 5;
        int dstChosen = 75;
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[srcChosen].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[dstChosen].getName(), null);
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", null, null, null, "tt1", null,
                                    null, null, null, null, "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 10; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null,
                                           null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 70; n < 80; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null, null));
        }
        /* Do not set CONTEXT_DST_IFACES in the cntx. This will be queried */
        long mac = HexString.toLong("00:11:22:33:44:55");
        int ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice dst = mockDeviceManager.learnEntity(mac, null, ip, 1L, 2);
        dev2 = dst;
        expect(netVirtManager.getInterfaces(dst)).andReturn(dstIfaces).anyTimes();
        replay(netVirtManager);

        this.testPacket = new Ethernet()
        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouterManager.VIRTUAL_ROUTING_MAC))
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        testUnicastInternal(cntx, RoutingAction.FORWARD);
        /* Check that the dest Ip is also matched in the flowmod */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        int dstIpWildcard = d.getWildcards() & OFMatch.OFPFW_NW_DST_MASK;
        assertEquals(dstIpWildcard, 0);

        verifyContext(cntx, netVirt[srcChosen], netVirt[dstChosen]);
        /* Check that source and dest MAC and TTL are modified */
        assertEquals(new Long(VirtualRouterManager.VIRTUAL_ROUTING_MAC),
                     rewriteService.getOrigIngressDstMac(cntx));
        assertEquals(new Long(mac), rewriteService.getFinalIngressDstMac(cntx));
        assertEquals(1, rewriteService.getTtlDecrement(cntx).intValue());
        verify(netVirtManager);
    }

    @Test
    public void testUnicastSameNetVirtDifferentSubnet() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1x0", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2x0", netVirt[0], null, null));
        /* Do not set CONTEXT_DST_IFACES in the cntx. This will be queried */

        long mac = HexString.toLong("00:11:22:33:44:55");
        int ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice dst = mockDeviceManager.learnEntity(mac, null, ip, 1L, 2);
        dev2 = dst;
        expect(netVirtManager.getInterfaces(dst)).andReturn(dstIfaces).anyTimes();
        replay(netVirtManager);

        this.testPacket = new Ethernet()
        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouterManager.VIRTUAL_ROUTING_MAC))
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        testUnicastInternal(cntx, RoutingAction.FORWARD);
        /* Check that the dest Ip is also matched in the flowmod */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        int dstIpWildcard = d.getWildcards() & OFMatch.OFPFW_NW_DST_MASK;
        assertEquals(dstIpWildcard, 0);

        verifyContext(cntx, netVirt[0], netVirt[0]);
        /* Check that source and dest MAC and TTL are modified */
        assertEquals(new Long(VirtualRouterManager.VIRTUAL_ROUTING_MAC),
                     rewriteService.getOrigIngressDstMac(cntx));
        assertEquals(new Long(mac), rewriteService.getFinalIngressDstMac(cntx));
        assertEquals(1, rewriteService.getTtlDecrement(cntx).intValue());
        verify(netVirtManager);
    }

    @Test
    public void testVRUnicastTTLDrop() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", null, null, null, "tt1", null,
                                    null, null, null, null, "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1x0", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2x1", netVirt[1], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);

        // Set the TTL to 1
        this.testPacket = new Ethernet()
        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouterManager.VIRTUAL_ROUTING_MAC))
        .setSourceMACAddress("00:44:33:22:11:00")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 1)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        // build out input packet
        OFPacketIn pi;
        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        // Start recording the replay on the mocks
        replay(mockSwitch);

        // Trigger the packet in
        Command ret = routing.receive(mockSwitch, pi,
                                      parseAndAnnotate(cntx, pi, dev1, dev2));
        assertEquals(Command.STOP, ret);
    }

    @Test
    public void testVRUnicastPassStaticARP() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, "10.1.1.1",
                                                  "0.0.0.255");

        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> if3 = h.createIface(r1, "if3", true,
                                                netVirt[2].getName(), null);
        Map<String, Object> ip3 = h.createIfaceIp(if3, "10.1.3.1",
                                                  "0.0.0.255");
        String srcMacStr = new String("00:11:33:44:55:66");
        String destMacStr = new String("00:11:22:33:44:55");
        String nextHopMacStr = new String("55:44:33:22:11:00");
        String nextHopIpStr = new String("10.1.3.10");
        String srcIpStr = new String("10.1.1.2");
        String destIpStr = new String("10.1.3.2");
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", null, null, null, null, null,
                                    "0.0.0.0", "255.255.255.255", null,
                                    nextHopIpStr, "permit");
        Map<String, Object> arp1 = h.createStaticArp(destIpStr, destMacStr);
        Map<String, Object> arp2 = h.createStaticArp(nextHopIpStr,
                                                     nextHopMacStr);
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2, if3);
        h.addIfaceIpAddr(ip1, ip3);
        h.addRoutingRule(rr1);
        h.addStaticArp(arp1, arp2);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        /* NetVirt0 and NetVirt1 are in the source. NetVirt2 is in the dest. All 3 are
         * connected via a router
         */
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 2; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null,
                                           null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2x2", netVirt[2], null, null));

        /* We need to assign dev1 and dev2 to a proper address space */
        String addressSpaceName = "MyAddressSpace";
        Short vlan = 0;
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC))
                .anyTimes();
        ecs.addListener(mockDeviceManager);
        expectLastCall().anyTimes();
        replay(ecs);
        mockDeviceManager.setEntityClassifier(ecs);
        dev1m = Ethernet.toLong(((Ethernet)testPacket).
                                getSourceMACAddress());
        dev1 = mockDeviceManager.learnEntity(HexString.toLong(srcMacStr), null,
                                             null, 1L, 1);
        /* The destination device in this case is unknown to virtual routing */
        dev2 = null;

        /* Tell device manager of the next hop device */
        long mac = HexString.toLong(nextHopMacStr);
        int ip = IPv4.toIPv4Address(nextHopIpStr);
        IDevice dst = mockDeviceManager.learnEntity(mac, null, ip, 1L, 2);
        expect(netVirtManager.getInterfaces(dst)).andReturn(dstIfaces).anyTimes();
        expect(netVirtManager.getVNS(netVirt[2].getName())).andReturn(netVirt[2]).times(2);
        replay(netVirtManager);

        this.testPacket = new Ethernet()
        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouting.MIN_VIRTUAL_MAC))
        .setSourceMACAddress(srcMacStr)
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress(srcIpStr)
                .setDestinationAddress(destIpStr)
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        testUnicastInternal(cntx, RoutingAction.FORWARD);
        /* Check that the dest Ip is also matched in the flowmod */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        int dstIpWildcard = d.getWildcards() & OFMatch.OFPFW_NW_DST_MASK;
        assertEquals(dstIpWildcard, 0);

        verifyContext(cntx, netVirt[1], netVirt[2]);
        /* Check that source and dest MAC and TTL are modified */
        assertEquals(new Long(VirtualRouting.MIN_VIRTUAL_MAC),
                     rewriteService.getOrigIngressDstMac(cntx));
        assertEquals(new Long(mac), rewriteService.getFinalIngressDstMac(cntx));
        assertEquals(new Long(VirtualRouterManager.VIRTUAL_ROUTING_MAC),
                     rewriteService.getFinalEgressSrcMac(cntx));
        verify(netVirtManager, ecs);
    }

    @Test
    public void testVRUnicastPassGatewayPool() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, "10.1.1.1",
                                                  "0.0.0.255");

        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> if3 = h.createIface(r1, "if3", true,
                                                netVirt[2].getName(), null);
        Map<String, Object> ip3 = h.createIfaceIp(if3, "10.1.3.1",
                                                  "0.0.0.255");
        Map<String, Object> gwPool = h.createGatewayPool(r1, "gw-pool");
        Map<String, Object> gwNode1 = h.createGatewayNode(gwPool, "10.1.3.10");
        Map<String, Object> gwNode2 = h.createGatewayNode(gwPool, "10.1.3.11");

        String srcMacStr = new String("00:11:33:44:55:66");
        String destMacStr = new String("00:11:22:33:44:55");
        String gwNode1MacStr = new String("55:44:33:22:11:00");
        String gwNode1IpStr = new String("10.1.3.10");
        String gwNode2MacStr = new String("55:44:33:22:11:01");
        String gwNode2IpStr = new String("10.1.3.11");
        String srcIpStr = new String("10.1.1.2");
        String destIpStr = new String("10.1.3.2");
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", null, null, null, null, null,
                                    "0.0.0.0", "255.255.255.255", null,
                                    null, "permit", "tt1|r1|gw-pool");
        Map<String, Object> arp1 = h.createStaticArp(destIpStr, destMacStr);
        Map<String, Object> arp2 = h.createStaticArp(gwNode1IpStr,
                                                     gwNode1MacStr);
        Map<String, Object> arp3 = h.createStaticArp(gwNode2IpStr,
                                                     gwNode2MacStr);
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2, if3);
        h.addIfaceIpAddr(ip1, ip3);
        h.addRoutingRule(rr1);
        h.addStaticArp(arp1, arp2, arp3);
        h.addVirtRtrGatewayPool(gwPool);
        h.addGatewayNode(gwNode1);
        h.addGatewayNode(gwNode2);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        /* NetVirt0 and NetVirt1 are in the source. NetVirt2 is in the dest. All 3 are
         * connected via a router
         */
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 2; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null,
                                           null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface2x2", netVirt[2], null, null));

        /* We need to assign dev1 and dev2 to a proper address space */
        String addressSpaceName = "MyAddressSpace";
        Short vlan = 0;
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC))
                .anyTimes();
        ecs.addListener(mockDeviceManager);
        expectLastCall().anyTimes();
        replay(ecs);
        mockDeviceManager.setEntityClassifier(ecs);
        dev1m = Ethernet.toLong(((Ethernet)testPacket).
                                getSourceMACAddress());
        dev1 = mockDeviceManager.learnEntity(HexString.toLong(srcMacStr), null,
                                             null, 1L, 1);
        /* The destination device in this case is unknown to virtual routing */
        dev2 = null;

        /* Tell device manager of gwNode1 device */
        long gwNode1Mac = HexString.toLong(gwNode1MacStr);
        int gwNode1Ip = IPv4.toIPv4Address(gwNode1IpStr);
        IDevice gwNode1Dev = mockDeviceManager.learnEntity(gwNode1Mac, null, gwNode1Ip, 1L, 2);
        expect(netVirtManager.getInterfaces(gwNode1Dev)).andReturn(dstIfaces).anyTimes();
        expect(netVirtManager.getVNS(netVirt[2].getName())).andReturn(netVirt[2]).times(2);

        /* Tell device manager of gwNode2 device */
        long gwNode2Mac = HexString.toLong(gwNode2MacStr);
        int gwNode2Ip = IPv4.toIPv4Address(gwNode2IpStr);
        IDevice gwNode2Dev = mockDeviceManager.learnEntity(gwNode2Mac, null, gwNode2Ip, 2L, 2);

        replay(netVirtManager);

        /*
         * Setup the mock routes
         */
        Route routeD1ToGN1 = new Route(dev1.getMACAddress(),
                                       gwNode1Dev.getMACAddress());
        routeD1ToGN1.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGN1.getPath().add(new NodePortTuple(1L, (short)2));


        Route routeD1ToGN2 = new Route(dev1.getMACAddress(),
                                       gwNode2Dev.getMACAddress());
        routeD1ToGN2.getPath().add(new NodePortTuple(1L, (short)1));
        routeD1ToGN2.getPath().add(new NodePortTuple(1L, (short)3));
        routeD1ToGN2.getPath().add(new NodePortTuple(2L, (short)1));
        routeD1ToGN2.getPath().add(new NodePortTuple(2L, (short)2));

        /*
         * ReMock up topology to return the L2DomainId
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
                                              .andReturn(true).anyTimes();

        /*
         * Mock up routingEngine to return the appropriate mock route
         */
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)2, 0)).
                                      andReturn(routeD1ToGN1).times(1);
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)2, 0)).
                                      andReturn(routeD1ToGN2).times(1);
        replay(topology, routingEngine);

        this.testPacket = new Ethernet()
        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouting.MIN_VIRTUAL_MAC))
        .setSourceMACAddress(srcMacStr)
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress(srcIpStr)
                .setDestinationAddress(destIpStr)
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        testUnicastInternal(cntx, RoutingAction.FORWARD);
        /* Check that the dest Ip is also matched in the flowmod */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        int dstIpWildcard = d.getWildcards() & OFMatch.OFPFW_NW_DST_MASK;
        assertEquals(dstIpWildcard, 0);

        verifyContext(cntx, netVirt[1], netVirt[2]);
        /* Check that source and dest MAC and TTL are modified */
        assertEquals(new Long(VirtualRouting.MIN_VIRTUAL_MAC),
                     rewriteService.getOrigIngressDstMac(cntx));
        assertEquals(new Long(gwNode1Mac), rewriteService.getFinalIngressDstMac(cntx));
        assertEquals(new Long(VirtualRouterManager.VIRTUAL_ROUTING_MAC),
                     rewriteService.getFinalEgressSrcMac(cntx));
        verify(netVirtManager, ecs, topology, routingEngine);
    }

    /**
     * Multiple source and destination NetVirtes with no overlap,
     * should drop.
     */
    @Test
    public void testUnicastDropMulti() throws Exception {
        final int nNetVirtes = 100;
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[nNetVirtes];
        for (int n = 0; n < nNetVirtes; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            netVirt[n].setPriority(n);
        }

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 30; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 70; n < nNetVirtes; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);

        testUnicastInternal(cntx, RoutingAction.DROP);

        verifyContext(cntx, null);
    }


    /**
     * Multiple source and destination NetVirtes with multiple,
     * overlap and different priority. Should pick the one
     * with the highest priority.
     */
    @Test
    public void testUnicastPassPriority() throws Exception {
        final int nNetVirtes = 100;
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[nNetVirtes];
        for (int n = 0; n < nNetVirtes; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            netVirt[n].setPriority(n);
        }

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 70; n >0; n--) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 30; n < nNetVirtes; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);

        testUnicastInternal(cntx, RoutingAction.FORWARD);

        verifyContext(cntx, netVirt[70]);
    }

    /**
     * Multiple source and destination NetVirtes with multiple pairs connected by
     * virtual routing. Should forward.
     */
    @Test
    public void testVRUnicastPassPriority() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[5].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[6].getName(), null);
        Map<String, Object> if3 = h.createIface(r1, "if3", true,
                                                netVirt[7].getName(), null);
        Map<String, Object> if4 = h.createIface(r1, "if4", true,
                                                netVirt[8].getName(), null);
        Map<String, Object> if5 = h.createIface(r1, "if5", true,
                                                netVirt[9].getName(), null);
        Map<String, Object> if6 = h.createIface(r1, "if6", true,
                                                netVirt[10].getName(), null);
        Map<String, Object> rr1 = h.createRoutingRule(r1, "tt1", null, null,
                                                      null, "tt1", null, null,
                                                      null, null, null,
                                                      "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2, if3, if4, if5, if6);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 7; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 7; n < 15; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null, null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);

        testUnicastInternal(cntx, RoutingAction.FORWARD);

        verifyContext(cntx, netVirt[6], netVirt[10]);
    }

    /**
     * Multiple source and destination NetVirtes with multiple pairs connected by
     * virtual routing. Should forward.
     */
    @Test
    public void testVRUnicastPassPriority2() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[5].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[6].getName(), null);
        Map<String, Object> if3 = h.createIface(r1, "if3", true,
                                                netVirt[7].getName(), null);
        Map<String, Object> if4 = h.createIface(r1, "if4", true,
                                                netVirt[8].getName(), null);
        Map<String, Object> if5 = h.createIface(r1, "if5", true,
                                                netVirt[9].getName(), null);
        Map<String, Object> if6 = h.createIface(r1, "if6", true,
                                                netVirt[10].getName(), null);
        Map<String, Object> rr1 = h.createRoutingRule(r1, "tt1", null, null,
                                                      null, "tt1", null, null,
                                                      null, null, null,
                                                      "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2, if3, if4, if5, if6);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 6; n++) {
            srcIfaces.add(new VNSInterface("testSrcIface1x" + n, netVirt[n], null,
                                           null));
        }
        srcIfaces.add(new VNSInterface("testSrcIface2x20", netVirt[20], null,
                                       null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        for (int n = 7; n < 15; n++) {
            dstIfaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null,
                                           null));
        }
        /* When a higher priority common interface is added, that is selected */
        dstIfaces.add(new VNSInterface("testSrcIface2x20", netVirt[20], null,
                                       null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);

        testUnicastInternal(cntx, RoutingAction.FORWARD);

        verifyContext(cntx, netVirt[20]);
    }

    /**
     * Common function for sending a Broadcast/Multicast packet
     */
    private void testXcastInternal(OFPacketIn pi, ListenerContext cntx, RoutingAction action, int ndev, int nifaces)
            throws Exception {
        VirtualRouting routing = getVirtualRouting();
        IControllerService mockControllerProvider = createMock(IControllerService.class);
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        // Start recording the replay on the mocks
        replay(mockSwitch);

        // Trigger the packet in
        Command ret = routing.receive(mockSwitch, pi, parseAndAnnotate(cntx, pi, dev1, dev2));

        // Get the annotation output for verification
        IRoutingDecision d = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);

        // Verify the replay matched our expectations
        // Don't veryfiy netVirtManager if the RoutingAction is drop, then NetVirt Manager function
        // wouldn't hav been invoked.
        verify(mockSwitch);

        if (action == RoutingAction.DROP) {
            assertTrue(ret == Command.STOP);
        } else if (action == RoutingAction.FORWARD_OR_FLOOD){
            assertTrue(d.getRoutingAction() == action);
            assertTrue(d.getSourcePort().getSwitchDPID() == 1L);
            assertTrue(d.getSourcePort().getPort() == pi.getInPort());
            assertEquals(d.getSourceDevice(), dev1);
            assertTrue(d.getDestinationDevices().size() == ndev);
            assertTrue(d.getMulticastInterfaces().size() == 0);
        }
    }

    /*
     * Wrapper for testing Broadcast
     */
    private void testBroadcastInternal(ListenerContext cntx, RoutingAction action, int ndev, int nifaces)
            throws Exception {
        OFPacketIn pi;

        // build out input packet
        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(broadcastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) broadcastPacketSerialized.length);

        testXcastInternal(pi, cntx, action, ndev, nifaces);
    }


    /**
     * Broadcast packet, should drop since a src NetVirt asked for it
     */
    @Test
    public void testBroadcastDrop() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VNS netVirt = new VNS("testNetVirt");
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirt, null, null));

        netVirt.setBroadcastMode(BroadcastMode.DROP);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        testBroadcastInternal(cntx, RoutingAction.DROP, 0, 0);
    }

    /**
     * Broadcast packet, should flood by default
     */
    @Test
    public void testBroadcastFlood() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[3];
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 3; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            srcIfaces.add(new VNSInterface("testSrcIface" + n, netVirt[n], null, null));
        }

        netVirt[1].setBroadcastMode(BroadcastMode.ALWAYS_FLOOD);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        testBroadcastInternal(cntx, RoutingAction.FORWARD_OR_FLOOD, 0, 0);
    }

    /**
     * Broadcast packet, should send to known destination devices and
     * explicitly configured broadcast switch interfaces.
     * FORWARD_TO_KNOWN is the default behavior for NetVirt broadcast.
     */
    @Test
    public void testBroadcastForward() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[3];
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        mockDeviceManager.startUp(fmc);
        for (int n = 0; n < 3; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            IDevice d = mockDeviceManager.learnEntity(n, null, null, null, null);
            netVirt[n].addDevice(d);
            srcIfaces.add(new VNSInterface("testSrcIface" + n, netVirt[n], null, null));
        }

        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        testBroadcastInternal(cntx, RoutingAction.MULTICAST, 2, 0);
    }

    /*
     * Wrapper for testing Broadcast
     */
    private void testMulticastInternal(ListenerContext cntx, RoutingAction action, int ndev, int nifaces)
            throws Exception {
        OFPacketIn pi;

        // build out input packet
        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(multicastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) multicastPacketSerialized.length);

        testXcastInternal(pi, cntx, action, ndev, nifaces);
    }

    /**
     * Multicast packet, should drop since a src NetVirt asked for it
     */
    @Test
    public void testMulticastDrop() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VNS netVirt = new VNS("testNetVirt");
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface", netVirt, null, null));
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        netVirt.setBroadcastMode(BroadcastMode.DROP);
        testMulticastInternal(cntx, RoutingAction.DROP, 0, 0);
    }

    /**
     * Multicast packet, floods when the mode is set to ALWAYS_FLOOD
     */
    @Test
    public void testMulticastFlood() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[3];
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 3; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            srcIfaces.add(new VNSInterface("testSrcIface" + n, netVirt[n], null, null));
        }

        netVirt[1].setBroadcastMode(BroadcastMode.ALWAYS_FLOOD);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        testMulticastInternal(cntx, RoutingAction.FORWARD_OR_FLOOD, 0, 0);
    }

    /**
     * Multicast packet: Packet is multicast only to known devices/interfaces
     * FORWARD_TO_KNOWN is the default behavior for NetVirt multicast.
     */
    @Test
    public void testMulticastForward() throws Exception {
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[3];
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        mockDeviceManager.startUp(fmc);
        for (int n = 0; n < 3; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            IDevice d = mockDeviceManager.learnEntity(n, null, null, null, null);
            netVirt[n].addDevice(d);
            srcIfaces.add(new VNSInterface("testSrcIface" + n, netVirt[n], null, null));
        }

        // FORWARD_TO_KNOWN is the default behavior.

        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        /*
         * Add a couple of switch ports to the broadcast interface on the netVirtmanager.
         */

        testMulticastInternal(cntx, RoutingAction.MULTICAST, 2, 0);
    }

    /**
     * Tests a host talking to another IP address we don't know about.
     */
    @Test
    public void testUnknownDest() throws Exception {
        OFPacketIn pi;
        ListenerContext cntx = new ListenerContext();
        VirtualRouting routing = getVirtualRouting();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        dev1 = mockDeviceManager.learnEntity(dev1m, null, null, 1L, 1);

        // build out input packet
        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketUnknownDestSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketUnknownDestSerialized.length);

        // Start recording the replay on the mocks
        replay(mockSwitch);

        // Trigger the packet in
        routing.receive(mockSwitch, pi, parseAndAnnotate(cntx, pi, dev1, null));

        // Get the annotation output for verification
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);

        // Verify the replay matched our expectations
        verify(mockSwitch);

        // Make sure we are re-injecting an ARP
        assertTrue(d.getRoutingAction() == RoutingAction.FORWARD);
    }

    @Test
    public void testBroadcastForwardWithInterfaces(){

        // Set the Listener Context.
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[3];
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 3; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            IDevice d = mockDeviceManager.learnEntity(n, null, null, null, null);
            netVirt[n].addDevice(d);
            srcIfaces.add(new VNSInterface("testSrcIface" + n, netVirt[n], null, null));
        }

        // Default broadcast behavior is FORWARD_TO_KNOWN

        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        // Set the packet in.
        OFPacketIn pi;

        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(broadcastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) broadcastPacketSerialized.length);

        // Set the routing action.
        RoutingAction action = RoutingAction.MULTICAST;

        VirtualRouting routing = getVirtualRouting();
        INetVirtManagerService mockNetVirtManager = createMock(INetVirtManagerService.class);
        routing.setNetVirtManager(mockNetVirtManager);

        // Create some mock switches for creating the switch port tuple.
        IOFSwitch mockSwitch2 = createNiceMock(IOFSwitch.class);
        expect(mockSwitch2.getId()).andReturn(2L).anyTimes();
        List<SwitchPort> lspt = new ArrayList<SwitchPort>();
        lspt.add(new SwitchPort(1L, 101));
        lspt.add(new SwitchPort(2L, 102));

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        switches.put(2L, mockSwitch2);
        mockControllerProvider.setSwitches(switches);

        expect(mockNetVirtManager.getBroadcastSwitchPorts()).
                andReturn(lspt).anyTimes();

        // Start recording the replay on the mocks
        replay(mockNetVirtManager);
        replay(mockSwitch);
        replay(mockSwitch2);
        // Trigger the packet in
        Command ret = routing.receive(mockSwitch, pi,
                                      parseAndAnnotate(cntx, pi, dev1, null));

        // Get the annotation output for verification
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);

        // Verify the replay matched our expectations
        // Don't verify netVirtManager if the RoutingAction is drop, then NetVirt
        // Manager function wouldn't have been invoked.
        verify(mockNetVirtManager);

        if (action == RoutingAction.DROP) {
            assertEquals(Command.STOP, ret);
        } else if (action == RoutingAction.FORWARD_OR_FLOOD){
            assertEquals(action, d.getRoutingAction());
            assertEquals(1L, d.getSourcePort().getSwitchDPID());
            assertEquals(pi.getInPort(), d.getSourcePort().getPort());
            assertEquals(dev1, d.getSourceDevice());
            assertEquals(2, d.getDestinationDevices().size());
            assertEquals(2, d.getMulticastInterfaces().size());
        }
    }


    @Test
    public void testMulticastForwardWithInterfaces(){

        // Set the Listener Context.
        ListenerContext cntx = new ListenerContext();
        VNS[] netVirt = new VNS[3];
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 3; n++) {
            netVirt[n] = new VNS("testNetVirt" + n);
            IDevice d = mockDeviceManager.learnEntity(n, null, null, null, null);
            netVirt[n].addDevice(d);
            srcIfaces.add(new VNSInterface("testSrcIface" + n, netVirt[n], null, null));
        }

        // Default mutlicast behavior is FORWARD_TO_KNOWN

        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        // Set the packet in.
        OFPacketIn pi;

        // build out multicast input packet
        pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(multicastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) multicastPacketSerialized.length);

        // Set the routing action.
        RoutingAction action = RoutingAction.MULTICAST;

        VirtualRouting routing = getVirtualRouting();
        INetVirtManagerService mockNetVirtManager =
                createMock(INetVirtManagerService.class);

        routing.setNetVirtManager(mockNetVirtManager);

        // Create some mock switches for creating the switch port tuple.
        IOFSwitch mockSwitch2 = createNiceMock(IOFSwitch.class);
        expect(mockSwitch2.getId()).andReturn(2L).anyTimes();
        List<SwitchPort> lspt = new ArrayList<SwitchPort>();
        lspt.add(new SwitchPort(1L, 101));
        lspt.add(new SwitchPort(2L, 102));

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        switches.put(2L, mockSwitch2);
        mockControllerProvider.setSwitches(switches);

        expect(mockNetVirtManager.getBroadcastSwitchPorts()).
                andReturn(lspt).anyTimes();

        // Start recording the replay on the mocks
        replay(mockNetVirtManager);
        replay(mockSwitch);
        replay(mockSwitch2);

        // Trigger the packet in
        Command ret = routing.receive(mockSwitch, pi,
                                      parseAndAnnotate(cntx, pi, dev1, null));

        // Get the annotation output for verification
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);

        // Verify the replay matched our expectations
        // Don't veryfiy netVirtManager if the RoutingAction is drop, then NetVirt
        // Manager function wouldn't hav been invoked.
        verify(mockNetVirtManager);

        if (action == RoutingAction.DROP) {
            assertEquals(Command.STOP, ret);
        } else if (action == RoutingAction.FORWARD_OR_FLOOD){
            assertEquals(action, d.getRoutingAction());
            assertEquals(1L, d.getSourcePort().getSwitchDPID());
            assertEquals(pi.getInPort(), d.getSourcePort().getPort());
            assertEquals(dev1, d.getSourceDevice());
            assertEquals(2, d.getDestinationDevices().size());
            assertEquals(2, d.getMulticastInterfaces().size());
        }
    }

    @Test
    public void testVMacAcquisition() {
        // Ok to request a vMac outside of the allocated block
        assertEquals(true, virtualRouting.acquireVirtualMac(
                             VirtualRouting.MAX_VIRTUAL_MAC+1));
        assertEquals(true, virtualRouting.acquireVirtualMac(
                             VirtualRouting.MIN_VIRTUAL_MAC-1));

        int remainedVMacSize = virtualRouting.reclaimedVMacs.size();
        long nextAvailVMac = virtualRouting.nextAvailableVMac;
        virtualRouting.relinquishVirtualMAC(
                             VirtualRouting.MAX_VIRTUAL_MAC+1);
        virtualRouting.relinquishVirtualMAC(
                             VirtualRouting.MIN_VIRTUAL_MAC-1);
        assertEquals(remainedVMacSize,
                     virtualRouting.reclaimedVMacs.size());
        assertEquals(nextAvailVMac, virtualRouting.nextAvailableVMac);

        long ns1_macl = VirtualRouting.MIN_VIRTUAL_MAC;
        assertTrue(virtualRouting.acquireVirtualMac(ns1_macl));
        // request for the same vMac should fail
        assertFalse(virtualRouting.acquireVirtualMac(ns1_macl));

        // Make sure we get the next available vMac
        long ns2_macl = ns1_macl + 1;
        long acquiredMac = 0;
        try {
            acquiredMac = virtualRouting.acquireVirtualMac();
        } catch (VirtualMACExhaustedException e) {
            fail("Unexpected SIVirtualMACExhaustedException exception");
        }
        assertEquals(ns2_macl, acquiredMac);

        // Get a vMac in the middle of the allocated block
        long nsxxx_macl = ns2_macl + 5;
        assertTrue(virtualRouting.acquireVirtualMac(nsxxx_macl));

        // next auto-generated vMac should be ns2_mac+1
        long acquiredMac1 = 0;
        long acquiredMac2 = 0;
        long acquiredMac3 = 0;
        long acquiredMac4 = 0;
        long acquiredMac5 = 0;
        try {
            acquiredMac1 = virtualRouting.acquireVirtualMac();
            acquiredMac2 = virtualRouting.acquireVirtualMac();
            acquiredMac3 = ns2_macl+3;
            assertTrue(virtualRouting.acquireVirtualMac(acquiredMac3));
            acquiredMac4 = virtualRouting.acquireVirtualMac();
            acquiredMac5 = virtualRouting.acquireVirtualMac();
        } catch (VirtualMACExhaustedException e) {
            fail("Unexpected SIVirtualMACExhaustedException exception");
        }
        assertEquals(ns2_macl+1, acquiredMac1);
        assertEquals(ns2_macl+2, acquiredMac2);
        assertEquals(ns2_macl+3, acquiredMac3);
        assertEquals(ns2_macl+4, acquiredMac4);
        assertEquals(ns2_macl+6, acquiredMac5);
        assertEquals(ns2_macl+7, virtualRouting.nextAvailableVMac);
        assertEquals(0, virtualRouting.reclaimedVMacs.size());

        // Relinquish ns2_mac, the next auto-generated vMac should be ns2_mac
        virtualRouting.relinquishVirtualMAC(ns2_macl);
        try {
            acquiredMac = virtualRouting.acquireVirtualMac();
        } catch (VirtualMACExhaustedException e) {
            fail("Unexpected SIVirtualMACExhaustedException exception");
        }
        assertEquals(ns2_macl, acquiredMac);

        // simulate the case when all vMacs have been allocated
        virtualRouting.reclaimedVMacs.clear();
        virtualRouting.nextAvailableVMac =
                VirtualRouting.MAX_VIRTUAL_MAC;
        try {
            acquiredMac = virtualRouting.acquireVirtualMac();
        } catch (VirtualMACExhaustedException e) {
            fail("Unexpected SIVirtualMACExhaustedException exception");
        }
        assertEquals(VirtualRouting.MAX_VIRTUAL_MAC, acquiredMac);
        try {
            acquiredMac = virtualRouting.acquireVirtualMac();
        } catch (Exception e) {
            assertTrue(e instanceof VirtualMACExhaustedException);
            return;
        }
        fail("SIVirtualMACExhaustedException is not raised.");
    }

    private OFPacketIn createARPPkt(String hostMACStr, String hostIpStr,
                                    String vIpStr) {
        int vIP = IPv4.toIPv4Address(vIpStr);
        ARP arprequestVIP = new ARP()
        .setHardwareType(ARP.HW_TYPE_ETHERNET)
        .setProtocolType(ARP.PROTO_TYPE_IP)
        .setHardwareAddressLength((byte) 6)
        .setProtocolAddressLength((byte) 4)
        .setOpCode(ARP.OP_REQUEST)
        .setSenderHardwareAddress(Ethernet.toMACAddress(hostMACStr))
        .setSenderProtocolAddress(IPv4.toIPv4AddressBytes(hostIpStr))
        .setTargetHardwareAddress(Ethernet.toMACAddress("FF:FF:FF:FF:FF:FF"))
        .setTargetProtocolAddress(vIP);

        IPacket arprequestPacketVIP = new Ethernet()
        .setSourceMACAddress(hostMACStr)
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(arprequestVIP);
        byte[] arpRequestSerializedVIP = arprequestPacketVIP.serialize();

        OFPacketIn packetInARPRequestVIP =
                ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setInPort((short) 1)
                .setPacketData(arpRequestSerializedVIP)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) arpRequestSerializedVIP.length);
        return packetInARPRequestVIP;
    }

    private OFPacketIn createICMPPkt(long hostMAC, int hostIp, long vMAC,
                                     int vIp) {
        byte[] srcMAC = Ethernet.toByteArray(hostMAC);
        byte[] dstMAC = Ethernet.toByteArray(vMAC);

        IPacket icmpRequestPacket = new Ethernet()
        .setSourceMACAddress(srcMAC)
        .setDestinationMACAddress(dstMAC)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setSourceAddress(hostIp)
                .setDestinationAddress(vIp)
                .setProtocol(IPv4.PROTOCOL_ICMP)
                .setTtl((byte) 64)
                .setPayload(
                        new ICMP()
                        .setIcmpType(ICMP.ECHO_REQUEST)
                        .setIcmpCode((byte) 0)));
        byte[] icmpRequestSerialized = icmpRequestPacket.serialize();

        OFPacketIn packetInICMPRequest =
                ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setInPort((short) 1)
                .setPacketData(icmpRequestSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) icmpRequestSerialized.length);
        return packetInICMPRequest;
    }

    private OFPacketIn createTraceroutePkt(long srcMAC, int srcIp, long dstMAC,
                                           int dstIp, byte ttl) {
        byte[] srcMACBytes = Ethernet.toByteArray(srcMAC);
        byte[] dstMACBytes = Ethernet.toByteArray(dstMAC);

        IPacket traceroutePacket = new Ethernet()
        .setSourceMACAddress(srcMACBytes)
        .setDestinationMACAddress(dstMACBytes)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setSourceAddress(srcIp)
                .setDestinationAddress(dstIp)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setTtl(ttl)
                .setPayload(
                        new UDP()
                        .setDestinationPort(VirtualRouting.TRACEROUTE_PORT_START)));
        byte[] tracerouteSerialized = traceroutePacket.serialize();

        OFPacketIn packetInTraceroute =
                ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setInPort((short) 1)
                .setPacketData(tracerouteSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) tracerouteSerialized.length);
        return packetInTraceroute;
    }

    protected void setForwardingExpect(IOFSwitch sw, Long swId, int times) {
        reset(forwarding);

        reset(sw);
        // MockSwitch2 expect
        List<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        OFPhysicalPort oPort = new OFPhysicalPort();
        oPort.setPortNumber((short)1);
        ports.add(oPort);

        oPort = new OFPhysicalPort();
        oPort.setPortNumber((short)2);
        ports.add(oPort);
        expect(sw.getEnabledPorts()).andReturn(ports).times(times);
        if (swId != null) {
            expect(sw.getId()).andReturn(swId.longValue()).times(times);
        }

        expect(forwarding.pushPacketOutToEgressPort(
                                        (Ethernet)EasyMock.anyObject(),
                                        EasyMock.anyShort(),
                                        (SwitchPort)EasyMock.anyObject(),
                                        EasyMock.anyBoolean(),
                                        (String)EasyMock.anyObject(),
                                        (Short)EasyMock.anyShort(),
                                        (ListenerContext)EasyMock.anyObject(),
                                        EasyMock.anyBoolean()))
                                        .andReturn(true)
                                        .times(times);
    }

    @Test
    public void testARPRequestHandler() throws Exception {
        String hostMACStr = "ff:ee:dd:cc:bb:aa";
        byte[] hostMAC = Ethernet.toMACAddress(hostMACStr);
        String hostIPStr = "10.1.1.2";
        int hostIP = IPv4.toIPv4Address(hostIPStr);
        String vIPStr = "10.1.1.1";

        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[5].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, vIPStr, "255.255.255.0");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1);
        h.addIfaceIpAddr(ip1);

        IDevice srcDev = mockDeviceManager.learnEntity(Ethernet.toLong(hostMAC),
                                                       null, hostIP, 1L, 3);

        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 10; n++) {
            ifaces.add(new VNSInterface("testSrcIface2x" + n, netVirt[n], null,
                                        null));
        }
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       ifaces);

        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);
        OFPacketIn pi = createARPPkt(hostMACStr, hostIPStr, vIPStr);
        replay(mockSwitch);
        cntx = parseAndAnnotate(cntx, pi, srcDev, null);
        expect(netVirtManager.getInterfaces(srcDev)).andReturn(ifaces).anyTimes();
        replay(netVirtManager);
        setForwardingExpect(mockSwitch, 1L, 1);
        replay(mockSwitch, forwarding);

        ARPCommand cmd = virtualRouting.ARPRequestHandler(mockSwitch, pi, cntx,
                                                          null);
        assertEquals(ARPCommand.CONTINUE, cmd);

        /* Now write the routing configuration to the storage and check that
         * ARP request processing is stopped
         */
        h.writeToStorage(storageSource);
        Thread.sleep(1000);
        cmd = virtualRouting.ARPRequestHandler(mockSwitch, pi, cntx, null);
        assertEquals(ARPCommand.STOP, cmd);
    }

    @Test
    public void testICMPRequestHandler() throws Exception {
        String hostMACStr = "ff:ee:dd:33:22:11";
        long hostMAC = Ethernet.toLong(Ethernet.toMACAddress(hostMACStr));
        String hostIPStr = "10.1.3.3";
        int hostIP = IPv4.toIPv4Address(hostIPStr);
        long vMAC1 = VirtualRouterManager.VIRTUAL_ROUTING_MAC;
        String vIP1Str = "10.1.3.1";
        int vIP1 = IPv4.toIPv4Address(vIP1Str);
        String vIP2Str = "10.1.4.1";
        int vIP2 = IPv4.toIPv4Address(vIP2Str);

        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[6].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[7].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, vIP1Str,
                                                  "255.255.255.0");
        Map<String, Object> ip2 = h.createIfaceIp(if2, vIP2Str,
                                                  "255.255.255.0");
        Map<String, Object> rr1 = h.createRoutingRule(r1, "tt1", null, null,
                                                      null, "tt1", null, null,
                                                      null, null, null,
                                                      "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addIfaceIpAddr(ip1, ip2);
        h.addRoutingRule(rr1);

        IDevice srcDev = mockDeviceManager.learnEntity(hostMAC, null, hostIP,
                                                       1L, 3);

        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 10; n++) {
            ifaces.add(new VNSInterface("testSrcIfaces2x" + n, netVirt[n], null,
                                        null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                INetVirtManagerService.CONTEXT_SRC_IFACES, ifaces);

        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);
        replay(mockSwitch);
        expect(netVirtManager.getInterfaces(srcDev)).andReturn(ifaces).anyTimes();
        replay(netVirtManager);
        setForwardingExpect(mockSwitch, 1L, 2);
        replay(mockSwitch, forwarding);

        ICMPCommand cmd;
        OFPacketIn pi1 = createICMPPkt(hostMAC, hostIP, vMAC1, vIP1);
        cntx = parseAndAnnotate(cntx, pi1, srcDev, null);
        cmd = virtualRouting.ICMPRequestHandler(mockSwitch, pi1,
                                                            cntx);
        assertEquals(ICMPCommand.CONTINUE, cmd);

        OFPacketIn pi2 = createICMPPkt(hostMAC, hostIP, vMAC1, vIP2);
        cntx = parseAndAnnotate(cntx, pi2, srcDev, null);
        cmd = virtualRouting.ICMPRequestHandler(mockSwitch, pi2, cntx);
        assertEquals(ICMPCommand.CONTINUE, cmd);

        /* Now write the routing configuration to the storage and check that
         * ICMP request is processed
         */
        h.writeToStorage(storageSource);
        Thread.sleep(1000);
        cntx = parseAndAnnotate(cntx, pi1, srcDev, null);
        cmd = virtualRouting.ICMPRequestHandler(mockSwitch, pi1, cntx);
        assertEquals(ICMPCommand.STOP, cmd);

        cntx = parseAndAnnotate(cntx, pi2, srcDev, null);
        cmd = virtualRouting.ICMPRequestHandler(mockSwitch, pi2, cntx);
        assertEquals(ICMPCommand.STOP, cmd);
    }

    @Test
    public void testIsTraceroutePacket() throws Exception {
        String srcMACStr = "ff:ee:dd:33:22:11";
        long srcMAC = Ethernet.toLong(Ethernet.toMACAddress(srcMACStr));
        String srcIPStr = "10.1.3.3";
        int srcIP = IPv4.toIPv4Address(srcIPStr);
        long dstMAC = VirtualRouterManager.VIRTUAL_ROUTING_MAC;
        String dstIPStr = "10.1.4.3";
        int dstIP = IPv4.toIPv4Address(dstIPStr);

        IDevice srcDev = mockDeviceManager.learnEntity(srcMAC, null, srcIP, 1L,
                                                       3);
        ListenerContext cntx = new ListenerContext();

        OFPacketIn pi1 = createICMPPkt(srcMAC, srcIP, dstMAC, dstIP);
        cntx = parseAndAnnotate(cntx, pi1, srcDev, null);
        assertEquals(false, VirtualRouting.isTraceroutePacket(cntx));

        OFPacketIn pi2 = createTraceroutePkt(srcMAC, srcIP, dstMAC, dstIP,
                                             (byte) 1);
        cntx = parseAndAnnotate(cntx, pi2, srcDev, null);
        assertEquals(true, VirtualRouting.isTraceroutePacket(cntx));
    }

    @Test
    public void testHandleTraceroute() throws Exception {
        String srcMACStr = "ff:ee:dd:33:22:11";
        long srcMAC = Ethernet.toLong(Ethernet.toMACAddress(srcMACStr));
        String srcIPStr = "10.1.3.3";
        int srcIP = IPv4.toIPv4Address(srcIPStr);
        long dstMAC = VirtualRouterManager.VIRTUAL_ROUTING_MAC;
        String dstIPStr = "10.1.4.3";
        int dstIP = IPv4.toIPv4Address(dstIPStr);
        String vrIP1Str = "10.1.3.1";
        int vrIP1 = IPv4.toIPv4Address(vrIP1Str);
        String vrIP2Str = "10.1.4.1";
        int vrIP2 = IPv4.toIPv4Address(vrIP2Str);

        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[6].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[7].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, vrIP1Str,
                                                  "255.255.255.0");
        Map<String, Object> ip2 = h.createIfaceIp(if2, vrIP2Str,
                                                  "255.255.255.0");
        Map<String, Object> rr1 = h.createRoutingRule(r1, "tt1", null, null,
                                                      null, "tt1", null, null,
                                                      null, null, null,
                                                      "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addIfaceIpAddr(ip1, ip2);
        h.addRoutingRule(rr1);

        IDevice srcDev = mockDeviceManager.learnEntity(srcMAC, null, srcIP, 1L,
                                                       3);

        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        for (int n = 0; n < 10; n++) {
            ifaces.add(new VNSInterface("testSrcIfaces2x" + n, netVirt[n], null,
                                        null));
        }
        INetVirtManagerService.bcStore.put(cntx,
                INetVirtManagerService.CONTEXT_SRC_IFACES, ifaces);

        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);
        replay(mockSwitch);
        expect(netVirtManager.getInterfaces(srcDev)).andReturn(ifaces).anyTimes();
        replay(netVirtManager);
        setForwardingExpect(mockSwitch, 1L, 4);
        replay(mockSwitch, forwarding);

        Command cmd;
        OFPacketIn pi1 = createTraceroutePkt(srcMAC, srcIP, dstMAC, dstIP,
                                             (byte) 1);
        cntx = parseAndAnnotate(cntx, pi1, srcDev, null);
        cmd = routing.receive(mockSwitch, pi1, cntx);
        assertEquals(Command.STOP, cmd);

        OFPacketIn pi2 = createTraceroutePkt(srcMAC, srcIP, dstMAC, dstIP,
                                             (byte) 2);
        cntx = parseAndAnnotate(cntx, pi2, srcDev, null);
        cmd = routing.receive(mockSwitch, pi2, cntx);
        assertEquals(Command.CONTINUE, cmd);

        OFPacketIn pi3 = createTraceroutePkt(srcMAC, srcIP, dstMAC, vrIP1,
                                             (byte) 2);
        cntx = parseAndAnnotate(cntx, pi3, srcDev, null);
        cmd = routing.receive(mockSwitch, pi3, cntx);
        assertEquals(Command.CONTINUE, cmd);

        OFPacketIn pi4 = createTraceroutePkt(srcMAC, srcIP, dstMAC, vrIP2,
                                             (byte) 2);
        cntx = parseAndAnnotate(cntx, pi4, srcDev, null);
        cmd = routing.receive(mockSwitch, pi4, cntx);
        assertEquals(Command.CONTINUE, cmd);

        /* Now write the routing configuration to the storage and check that
         * traceroute is processed
         */
        h.writeToStorage(storageSource);
        Thread.sleep(1000);
        cntx = parseAndAnnotate(cntx, pi1, srcDev, null);
        cmd = routing.receive(mockSwitch, pi1, cntx);
        assertEquals(Command.STOP, cmd);

        cntx = parseAndAnnotate(cntx, pi2, srcDev, null);
        cmd = routing.receive(mockSwitch, pi2, cntx);
        assertEquals(Command.CONTINUE, cmd);

        cntx = parseAndAnnotate(cntx, pi3, srcDev, null);
        cmd = routing.receive(mockSwitch, pi3, cntx);
        assertEquals(Command.STOP, cmd);

        cntx = parseAndAnnotate(cntx, pi4, srcDev, null);
        cmd = routing.receive(mockSwitch, pi4, cntx);
        assertEquals(Command.STOP, cmd);
    }

    @Test
    public void testReconcileFlows() throws Exception {
        String srcMACStr = "00:11:22:33:44:55";
        String dstMACStr = "55:44:33:22:11:00";
        short vlan = 20;
        int srcIp = IPv4.toIPv4Address("10.1.1.20");
        int dstIp = IPv4.toIPv4Address("10.1.2.30");
        OFMatchReconcile ofm = new OFMatchReconcile();
        OFMatch ofMatch = new OFMatch();
        ofMatch.setDataLayerSource(srcMACStr);
        ofMatch.setDataLayerDestination(dstMACStr);
        ofMatch.setDataLayerVirtualLan(vlan);
        ofMatch.setNetworkSource(srcIp);
        ofMatch.setNetworkDestination(dstIp);
        ofm.ofmWithSwDpid.setOfMatch(ofMatch);
        ArrayList<OFMatchReconcile> ofmList = new ArrayList<OFMatchReconcile>();
        ofmList.add(ofm);

        /* CONTEXT_SRC_IFACES is null so return early from the function */
        ofm.rcAction = ReconcileAction.NEW_ENTRY;
        Command ret = virtualRouting.reconcileFlows(ofmList);
        assertEquals(Command.CONTINUE, ret);
        assertEquals(OFMatchReconcile.ReconcileAction.DROP, ofm.rcAction);

        /* Source and dest Ifaces are in different NetVirt without virtual routing
         * policies to connect them. Check that the flow is dropped
         */
        ofm.rcAction = ReconcileAction.NEW_ENTRY;
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testDstIface1", netVirt[1], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        ret = virtualRouting.reconcileFlows(ofmList);
        assertEquals(Command.CONTINUE, ret);
        assertEquals(OFMatchReconcile.ReconcileAction.DROP, ofm.rcAction);

        /* Source and dest Ifaces are in same NetVirt. Check that the flow is
         * forwarded and the original DENY flow cache entry is deleted.
         */
        ofm.rcAction = ReconcileAction.NEW_ENTRY;
        ofm.action = FlowCacheObj.FCActionDENY;
        srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        ret = virtualRouting.reconcileFlows(ofmList);
        assertEquals(Command.CONTINUE, ret);
        assertEquals(OFMatchReconcile.ReconcileAction.DELETE, ofm.rcAction);

        /* Source and dest Ifaces are in same NetVirt. Check that the flow is
         * forwarded and the original appname which was different is updated
         */
        ofm.rcAction = ReconcileAction.NEW_ENTRY;
        ofm.action = FlowCacheObj.FCActionPERMIT;
        ofm.appInstName = "netVirtX";
        int DEFAULT_HINT = OFMatch.OFPFW_ALL & ~(OFMatch.OFPFW_DL_SRC |
                                                 OFMatch.OFPFW_DL_DST |
                                                 OFMatch.OFPFW_DL_VLAN |
                                                 OFMatch.OFPFW_IN_PORT |
                                                 OFMatch.OFPFW_DL_TYPE);
        ofMatch.setWildcards(DEFAULT_HINT);
        ofm.ofmWithSwDpid.setOfMatch(ofMatch);
        srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        ret = virtualRouting.reconcileFlows(ofmList);
        assertEquals(Command.CONTINUE, ret);
        assertEquals(OFMatchReconcile.ReconcileAction.APP_INSTANCE_CHANGED,
                     ofm.rcAction);
        assertEquals(netVirt[0].getName(), ofm.newAppInstName);

        /* Soure and dest are in different NetVirt, but virtual routing is
         * configured to allow flows. This changes the wildcard on the flow and
         * the flow cache entry is deleted
         */
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> rr1 = h.createRoutingRule(r1, "tt1", null, null,
                                                      null, "tt1", null, null,
                                                      null, null, null,
                                                      "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);
        ofm.rcAction = ReconcileAction.NEW_ENTRY;
        ofm.action = FlowCacheObj.FCActionPERMIT;
        ofm.appInstName = "netVirtX";
        ofMatch.setWildcards(DEFAULT_HINT);
        ofm.ofmWithSwDpid.setOfMatch(ofMatch);
        srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[1], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);
        dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testDstIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(ofm.cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       dstIfaces);
        IDeviceService.fcStore.put(ofm.cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                   dev2);
        ret = virtualRouting.reconcileFlows(ofmList);
        assertEquals(Command.CONTINUE, ret);
        assertEquals(OFMatchReconcile.ReconcileAction.DELETE, ofm.rcAction);
        assertEquals(IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                     ofm.newAppInstName);

    }

    public void
    testUpdateDestDevice() throws Exception {
        ListenerContext cntx = new ListenerContext();

        /*
         * Setup a single VR interconnecting 3 NetVirtes - netVirt[0], netVirt[1], netVirt[2]
         * Source - netVirt[0], OrigDst - netVirt[1], FinalDst - netVirt[2]
         * VR Rule: Allow netVirt[0] --> netVirt[1] and netVirt[0] --> netVirt[2]
         */
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, "10.0.0.1",
                                                  "0.0.0.255");
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> ip2 = h.createIfaceIp(if2, "10.0.1.1",
                                                  "0.0.0.255");
        Map<String, Object> if3 = h.createIface(r1, "if3", true,
                                                netVirt[2].getName(), null);
        Map<String, Object> ip3 = h.createIfaceIp(if3, "192.168.1.1",
                                                  "0.0.0.255");
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", netVirt[0].getName(), null, null,
                                    null, netVirt[1].getName(),
                                    null, null, null,
                                    null, "permit");
        Map<String, Object> rr2 =
                h.createRoutingRule(r1, "tt1", netVirt[0].getName(), null, null,
                                    null, netVirt[2].getName(),
                                    null, null, null,
                                    null, "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2, if3);
        h.addIfaceIpAddr(ip1, ip2, ip3);
        h.addRoutingRule(rr1, rr2);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        /*
         * Setup an address space to associate devices
         */
        String addressSpaceName = "MyAddressSpace";
        Short vlan = 0;
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                  .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                  .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC))
                  .anyTimes();
        ecs.addListener(mockDeviceManager);
        expectLastCall().anyTimes();
        replay(ecs);
        mockDeviceManager.setEntityClassifier(ecs);

        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        replay(topology);

        /*
         * Setup source device, origDstDevice and dstDevice
         */
        IDevice srcDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:02"),
                                          null,
                                          IPv4.toIPv4Address("10.0.0.2"),
                                          1L, 1);

        IDevice origDstDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:03"),
                                          null,
                                          IPv4.toIPv4Address("192.168.1.3"),
                                          1L, 2);

        IDevice dstDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:04"),
                                          null,
                                          IPv4.toIPv4Address("10.0.1.4"),
                                          1L, 3);

        /*
         * Setup src and dst netVirt ifaces
         */
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> origDstIfaces = new ArrayList<VNSInterface>();
        origDstIfaces.add(new VNSInterface("testSrcIface2", netVirt[2], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       origDstIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface3", netVirt[1], null, null));

        /*
         * Mock NetVirt Manager
         */
        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(dstDevice))
                         .andReturn(dstIfaces)
                         .atLeastOnce();
        replay(netVirtManager);

        /*
         * Mock tunnelManager
         */
        reset(tunnelManager);
        expect(tunnelManager.isTunnelEndpoint(origDstDevice))
               .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
               .andReturn(false).anyTimes();
        expect(tunnelManager.getSwitchDpid(IPv4.toIPv4Address("10.0.1.4")))
                            .andReturn(null).atLeastOnce();
        replay(tunnelManager);

        /*
         * Build test packet data, packet-in
         */
        testPacket = new Ethernet()
                        .setDestinationMACAddress("00:00:00:00:00:03")
                        .setSourceMACAddress("00:00:00:00:00:02")
                        .setEtherType(Ethernet.TYPE_IPv4)
                        .setPayload(
                         new IPv4()
                             .setTtl((byte) 128)
                             .setSourceAddress("10.0.0.2")
                             .setDestinationAddress("10.0.1.4")
                             .setPayload(new UDP()
                                 .setSourcePort((short) 5000)
                                 .setDestinationPort((short) 5001)
                                 .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized = testPacket.serialize();
        OFPacketIn pi = ((OFPacketIn)
                        mockControllerProvider
                            .getOFMessageFactory()
                                .getMessage(OFType.PACKET_IN))
                                    .setBufferId(-1)
                                    .setInPort((short)1)
                                    .setPacketData(testPacketSerialized)
                                    .setReason(OFPacketInReason.NO_MATCH)
                                    .setTotalLength((short) testPacketSerialized.length);

        /*
         * Setup virtual routing object
         */
        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        /*
         *  Replay switch mocker
         */
        replay(mockSwitch);

        /*
         * Finally the test
         */
        routing.receive(mockSwitch, pi, parseAndAnnotate(cntx, pi, srcDevice,
                                                         origDstDevice));

        /*
         * Verify mockers
         */
        verify(mockSwitch);
        verify(topology);
        verify(tunnelManager);
        verify(netVirtManager);

        /*
         * Validate routing decision
         */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        assertTrue(d.getRoutingAction() == RoutingAction.FORWARD);
        assertTrue(d.getSourcePort().getSwitchDPID() == 1L);
        assertTrue(d.getSourcePort().getPort() == pi.getInPort());
        assertEquals(srcDevice, d.getSourceDevice());
        assertEquals(1, d.getDestinationDevices().size());
        assertEquals(true, d.getDestinationDevices().contains(dstDevice));

        /*
         * Validate that ORIG_DST_DEVICE in sdnplatform ctx is origDstDevice
         */
        assertEquals(origDstDevice,
                     IDeviceService.fcStore.get(cntx,
                                      IDeviceService.CONTEXT_ORIG_DST_DEVICE));
    }

    public void
    testUpdateDestDeviceReconcileFlows() throws Exception {
        ListenerContext cntx = new ListenerContext();

        /*
         * Setup a single VR interconnecting 3 NetVirtes - netVirt[0], netVirt[1], netVirt[2]
         * Source - netVirt[0], OrigDst - netVirt[1], FinalDst - netVirt[2]
         * VR Rule: Allow netVirt[0] --> netVirt[1] and netVirt[0] --> netVirt[2]
         */
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, "10.0.0.1",
                                                  "0.0.0.255");
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> ip2 = h.createIfaceIp(if2, "10.0.1.1",
                                                  "0.0.0.255");
        Map<String, Object> if3 = h.createIface(r1, "if3", true,
                                                netVirt[2].getName(), null);
        Map<String, Object> ip3 = h.createIfaceIp(if3, "192.168.1.1",
                                                  "0.0.0.255");
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", netVirt[0].getName(), null, null,
                                    null, netVirt[1].getName(),
                                    null, null, null,
                                    null, "permit");
        Map<String, Object> rr2 =
                h.createRoutingRule(r1, "tt1", netVirt[0].getName(), null, null,
                                    null, netVirt[2].getName(),
                                    null, null, null,
                                    null, "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2, if3);
        h.addIfaceIpAddr(ip1, ip2, ip3);
        h.addRoutingRule(rr1, rr2);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        /*
         * Setup an address space to associate devices
         */
        String addressSpaceName = "MyAddressSpace";
        Short vlan = 0;
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                  .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                  .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC))
                  .anyTimes();
        ecs.addListener(mockDeviceManager);
        expectLastCall().anyTimes();
        replay(ecs);
        mockDeviceManager.setEntityClassifier(ecs);

        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        replay(topology);

        /*
         * Setup source device, origDstDevice and dstDevice
         */
        IDevice srcDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:02"),
                                          null,
                                          IPv4.toIPv4Address("10.0.0.2"),
                                          1L, 1);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);

        IDevice origDstDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:03"),
                                          null,
                                          IPv4.toIPv4Address("192.168.1.3"),
                                          1L, 2);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                   origDstDevice);

        IDevice dstDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:04"),
                                          null,
                                          IPv4.toIPv4Address("10.0.1.4"),
                                          1L, 3);
        IFlowCacheService.fcStore.put(cntx,
                                 IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                                 IVirtualRoutingService.VRS_FLOWCACHE_NAME);

        /*
         * Setup src and dst netVirt ifaces
         */
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        List<VNSInterface> origDstIfaces = new ArrayList<VNSInterface>();
        origDstIfaces.add(new VNSInterface("testSrcIface2", netVirt[2], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_DST_IFACES,
                                       origDstIfaces);

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("testSrcIface3", netVirt[1], null, null));

        /*
         * Mock NetVirt Manager
         */
        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(dstDevice))
                         .andReturn(dstIfaces)
                         .atLeastOnce();
        replay(netVirtManager);

        /*
         * Mock tunnelManager
         */
        reset(tunnelManager);
        expect(tunnelManager.isTunnelEndpoint(origDstDevice))
               .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
               .andReturn(false).anyTimes();
        expect(tunnelManager.getSwitchDpid(IPv4.toIPv4Address("10.0.1.4")))
                            .andReturn(null).atLeastOnce();
        replay(tunnelManager);

        /*
         * Build test packet data, packet-in
         */
        testPacket = new Ethernet()
                        .setDestinationMACAddress("00:00:00:00:00:03")
                        .setSourceMACAddress("00:00:00:00:00:02")
                        .setEtherType(Ethernet.TYPE_IPv4)
                        .setPayload(
                         new IPv4()
                             .setTtl((byte) 128)
                             .setSourceAddress("10.0.0.2")
                             .setDestinationAddress("10.0.1.4")
                             .setPayload(new UDP()
                                 .setSourcePort((short) 5000)
                                 .setDestinationPort((short) 5001)
                                 .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized = testPacket.serialize();
        OFPacketIn pi = ((OFPacketIn)
                        mockControllerProvider
                            .getOFMessageFactory()
                                .getMessage(OFType.PACKET_IN))
                                    .setBufferId(-1)
                                    .setInPort((short)1)
                                    .setPacketData(testPacketSerialized)
                                    .setReason(OFPacketInReason.NO_MATCH)
                                    .setTotalLength((short) testPacketSerialized.length);

        /*
         * Setup virtual routing object
         */
        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        /*
         *  Replay switch mocker
         */
        replay(mockSwitch);

        /*
         * Prepare the reconcile flow mods
         */
        ArrayList<OFMatchReconcile> ofmRcList = new ArrayList<OFMatchReconcile>();
        OFMatchReconcile ofmRc = new OFMatchReconcile();
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        MutableInteger hint = new MutableInteger(VirtualRouting.DEFAULT_HINT);
        hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_DST_MASK)));
        hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_SRC_MASK)));
        match.setWildcards(hint.intValue());
        ofmRc.ofmWithSwDpid.setOfMatch(match.clone());
        ofmRc.ofmWithSwDpid.setSwitchDataPathId(1L);
        ofmRc.rcAction = ReconcileAction.UPDATE_PATH;
        ofmRc.cntx = cntx;
        ofmRcList.add(ofmRc);

        /*
         * Finally the test
         */
        routing.reconcileFlows(ofmRcList);

        /*
         * Verify mockers
         */
        verify(mockSwitch);
        verify(topology);
        verify(tunnelManager);
        verify(netVirtManager);

        /*
         * Validate routing decision
         */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        assertTrue(d.getRoutingAction() == RoutingAction.FORWARD);
        assertTrue(d.getSourcePort().getSwitchDPID() == 1L);
        assertTrue(d.getSourcePort().getPort() == pi.getInPort());
        assertEquals(srcDevice, d.getSourceDevice());
        assertEquals(1, d.getDestinationDevices().size());
        assertEquals(true, d.getDestinationDevices().contains(dstDevice));

        /*
         * Validate that ORIG_DST_DEVICE in sdnplatform ctx is origDstDevice
         */
        assertEquals(origDstDevice,
                     IDeviceService.fcStore.get(cntx,
                                      IDeviceService.CONTEXT_ORIG_DST_DEVICE));
    }

    public void
    testUnknownDestWithVR() throws Exception {
        ListenerContext cntx = new ListenerContext();

        /*
         * Setup a single VR interconnecting 3 NetVirtes - netVirt[0], netVirt[1]
         * Source - netVirt[0]
         * VR Rule: Allow netVirt[0] --> 10.0.1.4 permit
         */
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, "10.0.0.1",
                                                  "0.0.0.255");
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> ip2 = h.createIfaceIp(if2, "10.0.1.1",
                                                  "0.0.0.255");
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", netVirt[0].getName(), null, null,
                                    null, null,
                                    "10.0.1.4", "0.0.0.0", null,
                                    null, "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addIfaceIpAddr(ip1, ip2);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        /*
         * Setup an address space to associate devices
         */
        String addressSpaceName = "MyAddressSpace";
        Short vlan = 0;
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                  .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                  .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC))
                  .anyTimes();
        ecs.addListener(mockDeviceManager);
        expectLastCall().anyTimes();
        replay(ecs);
        mockDeviceManager.setEntityClassifier(ecs);

        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        replay(topology);

        /*
         * Setup source device only. Do NOT setup destDevice
         */
        IDevice srcDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:02"),
                                          null,
                                          IPv4.toIPv4Address("10.0.0.2"),
                                          1L, 1);

        /*
         * Setup src ifaces only
         */
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        /*
         * Build test packet data, packet-in
         */
        testPacket = new Ethernet()
                        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouterManager.VIRTUAL_ROUTING_MAC))
                        .setSourceMACAddress("00:00:00:00:00:02")
                        .setEtherType(Ethernet.TYPE_IPv4)
                        .setPayload(
                         new IPv4()
                             .setTtl((byte) 128)
                             .setSourceAddress("10.0.0.2")
                             .setDestinationAddress("10.0.1.4")
                             .setPayload(new UDP()
                                 .setSourcePort((short) 5000)
                                 .setDestinationPort((short) 5001)
                                 .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized = testPacket.serialize();
        OFPacketIn pi = ((OFPacketIn)
                        mockControllerProvider
                            .getOFMessageFactory()
                                .getMessage(OFType.PACKET_IN))
                                    .setBufferId(-1)
                                    .setInPort((short)1)
                                    .setPacketData(testPacketSerialized)
                                    .setReason(OFPacketInReason.NO_MATCH)
                                    .setTotalLength((short) testPacketSerialized.length);

        /*
         * Setup virtual routing object
         */
        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        /*
         *  Replay switch mocker
         */
        replay(mockSwitch);

        /*
         * Finally the test
         */
        routing.receive(mockSwitch, pi, parseAndAnnotate(cntx, pi, srcDevice,
                                                         null));

        /*
         * Verify mockers
         */
        verify(mockSwitch);
        verify(topology);

        /*
         * Validate routing decision
         */
        IRoutingDecision d =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        assertTrue(d.getRoutingAction() == RoutingAction.FORWARD);
        assertTrue(d.getSourcePort().getSwitchDPID() == 1L);
        assertTrue(d.getSourcePort().getPort() == pi.getInPort());
        assertEquals(srcDevice, d.getSourceDevice());
        assertEquals(0, d.getDestinationDevices().size());
    }

    public void
    testUnknownDestWithVRReconcile() throws Exception {
        ListenerContext cntx = new ListenerContext();

        /*
         * Setup a single VR interconnecting 3 NetVirtes - netVirt[0], netVirt[1]
         * Source - netVirt[0]
         * VR Rule: Allow netVirt[0] --> 10.0.1.4 permit
         */
        VRTablesTestHelper h = new VRTablesTestHelper();
        Map<String, Object> tt1 = h.createTenant("tt1", true);
        Map<String, Object> r1 = h.createRouter(tt1, "r1");
        Map<String, Object> if1 = h.createIface(r1, "if1", true,
                                                netVirt[0].getName(), null);
        Map<String, Object> ip1 = h.createIfaceIp(if1, "10.0.0.1",
                                                  "0.0.0.255");
        Map<String, Object> if2 = h.createIface(r1, "if2", true,
                                                netVirt[1].getName(), null);
        Map<String, Object> ip2 = h.createIfaceIp(if2, "10.0.1.1",
                                                  "0.0.0.255");
        Map<String, Object> rr1 =
                h.createRoutingRule(r1, "tt1", netVirt[0].getName(), null, null,
                                    null, null,
                                    "10.0.1.4", "0.0.0.0", null,
                                    null, "permit");
        h.addTenant(tt1);
        h.addVirtRtr(r1);
        h.addVirtRtrIface(if1, if2);
        h.addIfaceIpAddr(ip1, ip2);
        h.addRoutingRule(rr1);
        h.writeToStorage(storageSource);
        Thread.sleep(1000);

        /*
         * Setup an address space to associate devices
         */
        String addressSpaceName = "MyAddressSpace";
        Short vlan = 0;
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                  .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                  .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC))
                  .anyTimes();
        ecs.addListener(mockDeviceManager);
        expectLastCall().anyTimes();
        replay(ecs);
        mockDeviceManager.setEntityClassifier(ecs);

        /*
         * Mock Topology to return true all the time as it should be called
         * with switch-port pair that should always be an attachment point
         */
        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort())).
                                              andReturn(true).anyTimes();
        replay(topology);

        /*
         * Setup source devices only
         */
        IDevice srcDevice = mockDeviceManager.learnEntity(
                                          HexString.toLong("00:00:00:00:00:02"),
                                          null,
                                          IPv4.toIPv4Address("10.0.0.2"),
                                          1L, 1);
        IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                   srcDevice);

        /*
         * Setup src ifaces only
         */
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt[0], null, null));
        INetVirtManagerService.bcStore.put(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES,
                                       srcIfaces);

        /*
         * Build test packet data, packet-in
         */
        testPacket = new Ethernet()
                        .setDestinationMACAddress(Ethernet.toByteArray(VirtualRouterManager.VIRTUAL_ROUTING_MAC))
                        .setSourceMACAddress("00:00:00:00:00:02")
                        .setEtherType(Ethernet.TYPE_IPv4)
                        .setPayload(
                         new IPv4()
                             .setTtl((byte) 128)
                             .setSourceAddress("10.0.0.2")
                             .setDestinationAddress("10.0.1.4")
                             .setPayload(new UDP()
                                 .setSourcePort((short) 5000)
                                 .setDestinationPort((short) 5001)
                                 .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized = testPacket.serialize();

        /*
         * Setup virtual routing object
         */
        VirtualRouting routing = getVirtualRouting();
        routing.setControllerProvider(mockControllerProvider);
        routing.setDeviceManager(mockDeviceManager);

        /*
         *  Replay switch mocker
         */
        replay(mockSwitch);

        /*
         * Prepare the reconcile flow mods
         */
        ArrayList<OFMatchReconcile> ofmRcList = new ArrayList<OFMatchReconcile>();
        OFMatchReconcile ofmRc = new OFMatchReconcile();
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        MutableInteger hint = new MutableInteger(VirtualRouting.DEFAULT_HINT);
        hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_DST_MASK)));
        hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_SRC_MASK)));
        match.setWildcards(hint.intValue());
        ofmRc.ofmWithSwDpid.setOfMatch(match.clone());
        ofmRc.ofmWithSwDpid.setSwitchDataPathId(1L);
        ofmRc.rcAction = ReconcileAction.UPDATE_PATH;
        ofmRc.cntx = cntx;
        ofmRcList.add(ofmRc);

        /*
         * Finally the test
         */
        routing.reconcileFlows(ofmRcList);

        /*
         * Verify mockers
         */
        verify(mockSwitch);
        verify(topology);

        /*
         * Validate reconcile action
         */
        assertEquals(OFMatchReconcile.ReconcileAction.DELETE, ofmRc.rcAction);
    }
}

