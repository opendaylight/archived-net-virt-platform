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

package org.sdnplatform.flowcache;

import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.core.util.AppCookie;
import org.sdnplatform.counter.CounterStore;
import org.sdnplatform.counter.ICounterStoreService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.internal.MockBetterDeviceManager;
import org.sdnplatform.flowcache.BetterFlowCache;
import org.sdnplatform.flowcache.BetterFlowReconcileManager;
import org.sdnplatform.flowcache.FCQueryObj;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.FlowCacheQueryResp;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.QRFlowCacheObj;
import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;
import org.sdnplatform.forwarding.Forwarding;
import org.sdnplatform.forwarding.IForwardingService;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.manager.internal.NetVirtManagerImpl;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.routing.ForwardingBase;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;

public class BetterFlowReconcileMgrTest extends PlatformTestCase {

    protected ListenerContext cntx12, cntx14;

    protected Forwarding forwarding;
    protected BetterFlowReconcileManager flowReconcileMgr;
    protected NetVirtManagerImpl netVirtManager;
    protected VirtualRouting virtualRouting;
    protected MockBetterDeviceManager betterDeviceManager;
    protected IRewriteService rewriteService;
    protected BetterFlowCache betterFlowCacheMgr;
    protected MockThreadPoolService tp;
    protected DefaultEntityClassifier entityClassifier;
    protected MemoryStorageSource storageSource;
    protected RestApiServer restApi;
    protected CounterStore counterStore;

    // Mocked modules
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected ITunnelManagerService tunnelManager;
    protected ILinkDiscoveryService linkDiscovery;

    protected IOFSwitch sw1, sw2, sw3;
    protected IDevice device1, device2, device2alt, device4;
    protected OFPacketIn packetIn12, packetIn14;
    protected OFPacketOut packetOut12, packetOut14;
    protected IPacket testPacket12, testPacket21, testPacket14;
    protected byte[] testPacketSerialized12;
    protected byte[] testPacketSerialized21;
    protected byte[] testPacketSerialized14;
    protected IRoutingDecision decision12, decision14;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        ModuleContext fmc = new ModuleContext();

        // Mock context
        cntx12 = new ListenerContext();
        cntx14 = new ListenerContext();

        forwarding       = new Forwarding();
        flowReconcileMgr = new BetterFlowReconcileManager();
        netVirtManager = new NetVirtManagerImpl();
        virtualRouting = new VirtualRouting();
        betterDeviceManager = new MockBetterDeviceManager();
        rewriteService = createMock(IRewriteService.class);
        betterFlowCacheMgr  = new BetterFlowCache();
        tp = new MockThreadPoolService();
        entityClassifier = new DefaultEntityClassifier();
        storageSource = new MemoryStorageSource();
        restApi = new RestApiServer();
        counterStore = new CounterStore();

        mockControllerProvider = getMockControllerProvider();
        routingEngine          = createMock(IRoutingService.class);
        topology               = createMock(ITopologyService.class);
        tunnelManager          = createMock(ITunnelManagerService.class);
        linkDiscovery          = createMock(ILinkDiscoveryService.class);

        fmc.addService(IForwardingService.class, forwarding);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(IDeviceService.class, betterDeviceManager);
        fmc.addService(ITagManagerService.class, betterDeviceManager);
        fmc.addService(IRewriteService.class, rewriteService);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(ICounterStoreService.class, counterStore);

        fmc.addService(IControllerService.class,
                       mockControllerProvider);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(ITunnelManagerService.class, tunnelManager);
        fmc.addService(ILinkDiscoveryService.class, linkDiscovery);

        forwarding.init(fmc);
        flowReconcileMgr.init(fmc);
        netVirtManager.init(fmc);
        virtualRouting.init(fmc);
        betterDeviceManager.init(fmc);
        betterFlowCacheMgr.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);
        storageSource.init(fmc);
        restApi.init(fmc);
        counterStore.init(fmc);

        topology.addListener(flowReconcileMgr);
        expectLastCall().times(1);
        topology.addListener(betterDeviceManager);
        expectLastCall().times(1);
        replay(topology);

        forwarding.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        netVirtManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        betterDeviceManager.startUp(fmc);
        betterFlowCacheMgr.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        storageSource.startUp(fmc);
        restApi.startUp(fmc);
        counterStore.startUp(fmc);

        reset(topology);

        betterFlowCacheMgr.setAppName("netVirt");
        betterFlowCacheMgr.periodicSwScanInitDelayMsec = 0;
        betterFlowCacheMgr.periodicSwScanIntervalMsec  = 1000;  // 1 second

        // Mock tunnel manager
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(false).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong())).andReturn(null).anyTimes();
        replay(tunnelManager);
        
        // Mock rewrite service 
        resetToNice(rewriteService);
        rewriteService.getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                             anyObject(String.class),
                                             anyShort(),
                                             anyBoolean());
        expectLastCall().andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        replay(rewriteService);

        // Mock switches
        sw1 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();

        expect(sw1.getStringId()).andReturn("00:00:00:00:00:00:00:01").
        anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();

        sw2 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.isConnected()).andReturn(true).anyTimes();

        expect(sw2.getStringId()).andReturn("00:00:00:00:00:00:00:02").
        anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();

        sw3 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.isConnected()).andReturn(true).anyTimes();

        expect(sw3.getStringId()).andReturn("00:00:00:00:00:00:00:03").
        anyTimes();
        //switch 3 belongs to cluster 3. as it is on its own.
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();

        // Load the switch map
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        mockControllerProvider.setSwitches(switches);
        assertEquals(switches, mockControllerProvider.getSwitches());

        // Build test packets
        testPacket12 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:02")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setDestinationAddress("192.168.1.2")
                .setSourceAddress("192.168.1.1")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized12 = testPacket12.serialize();

        testPacket21 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:01")
        .setSourceMACAddress("00:00:00:00:00:02")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setDestinationAddress("192.168.1.1")
                .setSourceAddress("192.168.1.2")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized21= testPacket21.serialize();

        testPacket14 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:04")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setDestinationAddress("192.168.1.4")
                .setSourceAddress("192.168.1.1")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized14 = testPacket14.serialize();

        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
                                              .andReturn(true).anyTimes();
        
        replay(topology);
        // Build src and dest devices
        byte[] dataLayerDevice1 = ((Ethernet)testPacket12).
                getSourceMACAddress();
        byte[] dataLayerDevice2 = ((Ethernet)testPacket21).
                getSourceMACAddress();
        byte[] dataLayerDevice4 =
                ((Ethernet)testPacket14).getDestinationMACAddress();

        int networkSource1 = ((IPv4)((Ethernet)testPacket12).getPayload()).
                getSourceAddress();
        int networkSource2 = ((IPv4)((Ethernet)testPacket21).getPayload()).
                getSourceAddress();
        int networkSource4 = ((IPv4)((Ethernet)testPacket14).getPayload()).
                getDestinationAddress();

        device1 = betterDeviceManager.learnEntity(
                            Ethernet.toLong(dataLayerDevice1),
                            null,
                            networkSource1,
                            1L, 1, false);
        device2 = betterDeviceManager.learnEntity(
                            Ethernet.toLong(dataLayerDevice2),
                            null,
                            networkSource2,
                            1L, 2, false);
        device4 = betterDeviceManager.learnEntity(
                            Ethernet.toLong(dataLayerDevice4),
                            null,
                            networkSource4,
                            3L, 1, false);

        // Mock Packet-in
        packetIn12 = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized12)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized12.length);

        packetIn14 = ((OFPacketIn) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized14)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized14.length);

        // Mock Packet-out
        packetOut12 =
                (OFPacketOut) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut12.setBufferId(this.packetIn12.getBufferId())
        .setInPort(this.packetIn12.getInPort());
        List<OFAction> poactions12 = new ArrayList<OFAction>();
        poactions12.add(new OFActionOutput((short)2, (short) 0xffff));
        packetOut12.setActions(poactions12)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized12)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut12.
                getActionsLength()+testPacketSerialized12.length);

        packetOut14 =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_OUT);
        packetOut14.setBufferId(this.packetIn14.getBufferId())
        .setInPort(this.packetIn14.getInPort());
        List<OFAction> poactions14 = new ArrayList<OFAction>();
        poactions14.add(new OFActionOutput((short)3, (short) 0xffff));
        packetOut14.setActions(poactions14)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized14)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH +
                packetOut14.getActionsLength( ) +
                testPacketSerialized14.length);

        // Mock decision12
        decision12 = createMock(IRoutingDecision.class);
        expect(decision12.getSourceDevice()).andReturn(device1).atLeastOnce();
        expect(decision12.getSourcePort()).andReturn(
                            new SwitchPort(1L, (short)1)).atLeastOnce();

        ArrayList<IDevice> dstDevices12 = new ArrayList<IDevice>();
        dstDevices12.add(device2);
        expect(decision12.getDestinationDevices()).andReturn(dstDevices12).
        atLeastOnce();
        IRoutingDecision.rtStore.put(
                cntx12, IRoutingDecision.CONTEXT_DECISION, decision12);

        // set decision.getRoutingAction() based on test case
        // Set SRC_IFACCES in context
        List<VNSInterface> srcIfaces1 = new ArrayList<VNSInterface>();
        String netVirtName = "default|DefaultEntityClass-default";
        VNS netVirt1 = new VNS(netVirtName);
        srcIfaces1.add(new VNSInterface("testSrcIface1", netVirt1, null, null));
        INetVirtManagerService.bcStore.put(
                cntx12, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces1);
        IFlowCacheService.fcStore.put(
                cntx12, IFlowCacheService.FLOWCACHE_APP_NAME, netVirtName);
        IFlowCacheService.fcStore.put(
                cntx12, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                netVirt1.getName());

        // Mock decision14
        decision14 = createMock(IRoutingDecision.class);
        expect(decision14.getSourceDevice()).andReturn(device1).atLeastOnce();
        expect(decision14.getSourcePort()).andReturn(
                            new SwitchPort(1L, (short)1)).atLeastOnce();

        ArrayList<IDevice> dstDevices14 = new ArrayList<IDevice>();
        dstDevices14.add(device4);
        expect(decision14.getDestinationDevices()).
        andReturn(dstDevices14).atLeastOnce();
        IRoutingDecision.rtStore.put(
                cntx14, IRoutingDecision.CONTEXT_DECISION, decision14);

        // set decision.getRoutingAction() based on test case
        // Set SRC_IFACCES in context
        List<VNSInterface> srcIfaces5 = new ArrayList<VNSInterface>();
        netVirtName = "default|DefaultEntityClass-default";
        VNS netVirt5 = new VNS(netVirtName);
        srcIfaces5.add(new VNSInterface("testSrcIface5", netVirt5, null, null));
        INetVirtManagerService.bcStore.put(
                cntx14, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces5);
        IFlowCacheService.fcStore.put(
                cntx14, IFlowCacheService.FLOWCACHE_APP_NAME, netVirtName);
        IFlowCacheService.fcStore.put(
                cntx14, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                netVirt5.getName());
    }

    @Override
    @After
    public void tearDown() {
        verify(tunnelManager);
    }

    // Test host move handling:
    // There are two types of host moves
    // (a) Host move with port status change: Here host was directly connected
    //     to port P1 on openflow switch SW1. Then host moves from Port P1 to
    //     to Port P2 of switch SW2. Here the controller would get two
    //     port status change message. Down for P1 and up for P2
    // (b) Host move with no port-status change: Here host was connected to
    //     a switch that was not connected by the controller and moves to
    //     another port on a switch which was also not connected to the
    //     controller. Here the controller would get an attachment-point change
    //     event for the host. Both of these cases are tested here.
    // In each of these cases the flows that were *destined* to the moved host
    // would queried from the flow-cache and they would be reprogrammed to
    // towards the new attachment point of the moved host.
    // Switch device1 ---Port 1---> SW1 ---Port 2 ---> device 2
    // Trigger: device 2 moves from Port 2 to Port 4

    @Test
    public void testFlowCacheHostMove() throws Exception {
        // Mock decision
        expect(decision12.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision12.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision12.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as local and Mock route
        Route route1 = new Route(1L, 1L);
        route1.getPath().add(new NodePortTuple(1L, (short)1));
        route1.getPath().add(new NodePortTuple(1L, (short)2));
        long routeCookie12 = forwarding.getHashByMac(((Ethernet) testPacket12).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)2, routeCookie12, true))
        .andReturn(route1).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        // Packet 1 to 2 from sw1, input port 1
        match.loadFromPacket(testPacketSerialized12, (short) 1);
        OFActionOutput action  = new OFActionOutput((short)2, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        Long cookie = 2L << 52;
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(VirtualRouting.DEFAULT_HINT))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(cookie)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+
                            OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx12);
        sw1.write(packetOut12, cntx12);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1,
                                              1, (short)2, true))
        .andReturn(new NodePortTuple(1, (short)2)).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1,
                                              1, (short)2, true))
        .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();

        replay(sw1, routingEngine, decision12, topology);
        forwarding.receive(sw1, this.packetIn12, cntx12);
        betterFlowCacheMgr.updateFlush();
        verify(sw1, routingEngine, decision12, topology);

        // Check that the flow was added to flow-cache
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getNotDampenedCnt());

        // Set flowReconcileManager as a DeviceListener
        betterDeviceManager.addListener(flowReconcileMgr.deviceListener);

        // Create a new flow-mod that is expected to be programmed
        fm1.getActions().remove(0);
        OFActionOutput actionNew  = new OFActionOutput((short)4, (short)0xffff);
        List<OFAction> actionsNew = new ArrayList<OFAction>();
        actionsNew.add(actionNew);
        fm1.setActions(actionsNew);
        fm1.setPriority(forwarding.getAccessPriority());
        fm1.setCommand(OFFlowMod.OFPFC_MODIFY);

        reset(topology, routingEngine);
        sw1.write(fm1, cntx12);
        Route route2 = new Route(1L, 1L);
        route2.getPath().add(new NodePortTuple(1L, (short)1));
        route2.getPath().add(new NodePortTuple(1L, (short)4));
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)4,routeCookie12, true))
        .andReturn(route2).atLeastOnce();

        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1,
                                              1, (short)4, true))
        .andReturn(new NodePortTuple(1, (short)4)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1,
                                              1, (short)2, true))
        .andReturn(new NodePortTuple(1, (short)4)).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1,
                                              1, (short)4, true))
        .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1,
                                              1, (short)2, true))
        .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isConsistent(EasyMock.anyLong(), EasyMock.anyShort(),
                                     EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
        .andReturn(false).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(EasyMock.anyLong(),
                                                EasyMock.anyShort(),
                                                EasyMock.anyLong(),
                                                EasyMock.anyShort()))
        .andReturn(false).anyTimes();

        replay(topology, routingEngine);

        // Now trigger Device Move with port status change
        int pre_count = flowReconcileMgr.flowReconcileThreadRunCount.get();
        device2alt = betterDeviceManager.
                learnEntity(Ethernet.toLong(((Ethernet)testPacket21)
                                            .getSourceMACAddress()),
                            null,
                            ((IPv4)((Ethernet)testPacket21).getPayload()).
                            getSourceAddress(),
                            1L, 4, true);
        
        Date startTime = new Date();
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() == pre_count) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
        }

        FlowCacheQueryResp bfcQR = flowReconcileMgr.lastFCQueryResp;
        assertEquals(FCQueryEvType.DEVICE_MOVED, bfcQR.queryObj.evType);
        assertEquals(false, bfcQR.moreFlag);
        assertEquals(1, bfcQR.qrFlowCacheObjList.size());
        QRFlowCacheObj qrFcObj = bfcQR.qrFlowCacheObjList.get(0);
        assertEquals(1L, qrFcObj.ofmWithSwDpid.getSwitchDataPathId());
        assertEquals(1, qrFcObj.ofmWithSwDpid.getOfMatch().getInputPort());
        assertEquals(Ethernet.toLong(qrFcObj.ofmWithSwDpid.getOfMatch()
                                     .getDataLayerSource()),
                device1.getMACAddress());
        assertEquals(Ethernet.toLong(qrFcObj.ofmWithSwDpid.getOfMatch()
                                     .getDataLayerDestination()),
                device2.getMACAddress());
        assertEquals(FlowCacheObj.FCActionPERMIT, qrFcObj.action);
        
        verify(topology, routingEngine);

        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
    }

    /* Test link down handling:
     * When a link does down, FlowReconcile Manager , being linkDiscovery-aware
     * gets a linkDiscoveryUp triggers a redeployment of all the flows that
     * were routed over that failed link.
     * If there is an alternate route then those flows would be
     * re-established via an alternate path.
     */

    @Test
    public void testFlowCacheLinkDown() throws Exception {
        // Mock decision
        expect(decision14.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision14.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision14.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as on sw3 and Mock route
        Route route = new Route(1L, 3L);
        route.setPath(new ArrayList<NodePortTuple>());
        /* Route: device1--[P1SW1P5]--[P5SW2P7]--[P7SW3P1]--device4 */
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)7));
        route.getPath().add(new NodePortTuple(2L, (short)7));
        route.getPath().add(new NodePortTuple(2L, (short)5));
        route.getPath().add(new NodePortTuple(3L, (short)5));
        route.getPath().add(new NodePortTuple(3L, (short)1));
        long routeCookie14 = forwarding.getHashByMac(((Ethernet) testPacket14).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, routeCookie14, true))
        .andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        // Packet 1 to 4 from sw1, input port 1
        match.loadFromPacket(testPacketSerialized14, (short) 1);
        OFActionOutput action  = new OFActionOutput((short)7, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        Long cookie = 2L << 52;
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(VirtualRouting.DEFAULT_HINT))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(cookie)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> bc1 =
                new Capture<ListenerContext>(CaptureType.ALL);
        sw1.write(capture(wc1), capture(bc1));

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1,
                                              3, (short)1, true))
        .andReturn(new NodePortTuple(3, (short)1)).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1,
                                              3, (short)1, true))
        .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();

        replay(sw1, sw2, sw3, routingEngine, decision14, topology);
        forwarding.receive(sw1, this.packetIn14, cntx14);
        betterFlowCacheMgr.updateFlush();
        verify(sw1, sw2, sw3, routingEngine, decision14, topology);
        OFMessage m = wc1.getValues().get(0);
        assertEquals(fm1, m);

        // Check that the flow was added to flow-cache
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getNotDampenedCnt());

        /* Create a new route bypassing the link */
        /* ----- Reset the mocks ------------- */
        reset(sw1, sw2, sw3, decision14, routingEngine, topology);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw3.getId()).andReturn(3L).anyTimes();

        expect(sw1.isConnected()).andReturn(true).atLeastOnce();
        expect(sw1.getStringId()).andReturn("00:00:00:00:00:00:00:01").
        anyTimes();
        expect(sw2.getStringId()).andReturn("00:00:00:00:00:00:00:02").
        anyTimes();

        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1,
                                              3, (short)1, true))
        .andReturn(new NodePortTuple(3, (short)1)).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1,
                                              3, (short)1, true))
        .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(1L,  (short)7,  2L,  (short)7))
        .andReturn(false).anyTimes();

        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = req.getLengthU();

        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        OFMatch matchReq = new OFMatch();
        match.setWildcards(0xffffffff);
        specificReq.setMatch(matchReq);
        specificReq.setOutPort((short) 7);
        specificReq.setTableId((byte) 0xff);
        req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
        requestLength += specificReq.getLength();
        req.setLengthU(requestLength);

        Route routeNew = new Route(1L, 3L);
        routeNew.setPath(new ArrayList<NodePortTuple>());
        /* Route: device1--[P1SW1P8]--[P8SW3P1]--device4 */
        routeNew.getPath().add(new NodePortTuple(1L, (short)1));
        routeNew.getPath().add(new NodePortTuple(1L, (short)8));
        routeNew.getPath().add(new NodePortTuple(3L, (short)8));
        routeNew.getPath().add(new NodePortTuple(3L, (short)1));
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, routeCookie14, true)).
            andReturn(routeNew).atLeastOnce();

        // Create a new flow-mod that is expected to be programmed
        fm1.getActions().remove(0);
        OFActionOutput actionNew  = new OFActionOutput((short)8, (short)0xffff);
        List<OFAction> actionsNew = new ArrayList<OFAction>();
        actionsNew.add(actionNew);
        fm1.setActions(actionsNew);
        fm1.setCommand(OFFlowMod.OFPFC_MODIFY);

        // Record expected flow mods
        sw1.write(capture(wc1), capture(bc1));

        replay(sw1, sw2, sw3, routingEngine, decision14, topology);
        // Now bring the link down between switch SW1 and SW2
        flowReconcileMgr.removedLink(sw1.getId(), (short)7,
                                     sw2.getId(), (short)7);
        OFStatisticsReply msg = new OFStatisticsReply();
        msg.setType(OFType.STATS_REPLY);
        List<OFStatistics> statsList = new ArrayList<OFStatistics>();
        msg.setStatistics(statsList);
        msg.setXid(0);
        OFFlowStatisticsReply oneStats = new OFFlowStatisticsReply();
        OFMatch matchResp = new OFMatch();
        matchResp.setDataLayerSource("00:00:00:00:00:01");
        matchResp.setDataLayerDestination("00:00:00:00:00:04");
        matchResp.setWildcards(FlowCacheObj.WILD_MATCH_INP_VLAN_DLADRS);
        oneStats.setCookie(
                AppCookie.makeCookie(Forwarding.FORWARDING_APP_ID, 0));
        oneStats.setMatch(matchResp);
        statsList.add(oneStats);
        
        int pre_count = flowReconcileMgr.flowReconcileThreadRunCount.get();
        
        Date startTime = new Date();
        flowReconcileMgr.receive(sw1, msg, null);
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() == pre_count) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
        }
        verify (sw1, sw2, sw3, routingEngine, decision14, topology);

        assertEquals(pre_count+1, flowReconcileMgr.switchQueryRespHandlerCallCount.get());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());

        m = wc1.getValues().get(1);
        assert (m instanceof OFFlowMod);
        assertEquals(fm1, m);
    }

    /* Test flow reconciliation upon NetVirt config change
     *
     */

    @Test
    public void testFlowReconcileUponNetVirtCfgChange() throws Exception {
        /* Add a flow to flow cache */
        betterFlowCacheMgr.clearFlowCache();
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        String netVirtName = "testNetVirt1";
        OFMatch ofm = new OFMatch();
        ofm.setDataLayerSource(Ethernet.toByteArray(device1.getMACAddress()));
        ofm.setDataLayerDestination(
                               Ethernet.toByteArray(device2.getMACAddress()));
        ofm.setWildcards(FlowCacheObj.WILD_MATCH_INP_VLAN_DLADRS_ET);
        OFMatchWithSwDpid ofmWithSwDpid = new OFMatchWithSwDpid(ofm, 1L);
        /* device1 is connected to sw1 */
        betterFlowCacheMgr.addFlow(netVirtName, ofmWithSwDpid, 2L, 1L, (short)1,
                (short)0, FlowCacheObj.FCActionPERMIT);
        betterFlowCacheMgr.updateFlush();
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());

        int pre_count = flowReconcileMgr.flowReconcileThreadRunCount.get();
        Date startTime = new Date();
        
        /* Simulate that there is a config change in "testNetVirt1" causing
         * NetVirtManagerImpl to submit a flow cache query.
         * NetVirt Manager triggers the flow reconciliation by submitting
         * flow query to the flow cache.
         */
        FCQueryObj fcQueryObj = new FCQueryObj(
                                    netVirtManager,
                                    netVirtName,
                                    null,       // null vlan
                                    null,       // null srcDevice
                                    null,       // null destDevice
                                    getName(),
                                    FCQueryEvType.APP_CONFIG_CHANGED,
                                    null);

        
        /* Do the replay */
        replay(sw1);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() == pre_count) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 1000);
        }
        verify(sw1);

        /* Now netVirtManager module should get callback with the flow that
         * we inserted above.
         */
        assertEquals(1, netVirtManager.getFlowQueryRespHandlerCallCount());
        assertEquals(1,
                netVirtManager.getLastFCQueryResp().qrFlowCacheObjList.size());
        String newNetVirtName = "default|DefaultEntityClass-default";
        FlowCacheObj fco = betterFlowCacheMgr.
                getAllFlowsByApplInstVlanSrcDestDevicesInternal(
                                                newNetVirtName, (short)-1,
                                                device1.getMACAddress(),
                                                device2.getMACAddress());

        /* Confirm that the flow cache now has entry under the default netVirt */
        assertNotNull(fco);
        assertEquals(FlowCacheObj.FCActionPERMIT, fco.fce.getAction());
        assertEquals(null, fco.fceList);
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());

        /* **** Now simulate that device1 and device2 are in the same NetVirt */
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);

        /* Now virtual routing module should get callback with the flow that
         * we inserted above.
         */
        startTime = new Date();
        while (netVirtManager.getFlowQueryRespHandlerCallCount() == 1) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 1000);
        }
        assertEquals(2, netVirtManager.getFlowQueryRespHandlerCallCount());
        assertEquals(0,
                     netVirtManager.getLastFCQueryResp().qrFlowCacheObjList.size());
        fco = betterFlowCacheMgr.
                getAllFlowsByApplInstVlanSrcDestDevicesInternal(
                                                   netVirtName, (short)-1,
                                                   device1.getMACAddress(),
                                                   device2.getMACAddress());

        /* Confirm that the flow cache is now empty as the delete entry would
         * be deleted */
        assertNull(fco);
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
    }
}
