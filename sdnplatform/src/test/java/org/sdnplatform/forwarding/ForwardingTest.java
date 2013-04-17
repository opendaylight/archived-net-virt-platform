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

package org.sdnplatform.forwarding;

import static org.easymock.EasyMock.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.util.HexString;
import org.openflow.util.U8;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.addressspace.BetterEntityClass;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.core.util.AppCookie;
import org.sdnplatform.counter.CounterStore;
import org.sdnplatform.counter.ICounterStoreService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.internal.Entity;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.BetterFlowCache;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.OFMatchReconcile;
import org.sdnplatform.flowcache.OFMatchReconcile.ReconcileAction;
import org.sdnplatform.forwarding.Forwarding;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.packet.ARP;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.routing.ForwardingBase;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.IBetterTopologyService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.util.OFMessageDamper;
import org.sdnplatform.util.TimedCache;
import org.sdnplatform.vendor.OFActionTunnelDstIP;

import static org.junit.Assert.*;

// Don't extend SDN PlatformTestCase or TestCase, use
// @Test, @Before annotations!
public class ForwardingTest {
    protected MockControllerProvider mockControllerProvider;
    protected ListenerContext cntx;
    protected MockThreadPoolService threadPool;
    protected MockDeviceManager deviceManager;
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected IBetterTopologyService bettertopology;
    protected ITunnelManagerService tunnelManager;
    protected Forwarding forwarding;
    protected IFlowReconcileService flowReconcileMgr;
    protected IRewriteService rewriteService;
    protected IRestApiService restApi;
    protected IAddressSpaceManagerService addressSpaceMgr;
    protected IOFSwitch sw1, sw2, sw3;                        // swithes for use in multi-action packet out for netVirt-broadcast
    protected Capture<OFMessage> wc1, wc2, wc3;         // Capture writes to switches
    protected Capture<ListenerContext> fc1, fc2, fc3;  // Capture writes to switches
    //protected OFMessageSafeOutStream out1, out2, out3;
    protected IDevice srcDevice, dstDevice1, dstDevice2;
    protected ArrayList<IDevice> dstDevices;
    protected IDevice device1, device2, device3, device4, device5;      // devices added for multi-action packet out for netVirt-broadcast
    protected OFPacketIn packetIn;
    protected OFPacketIn multicastPacketIn;                    // added for multi-action packet out for netVirt-broadcast
    protected OFPacketIn secondMulticastPacketIn;
    protected OFPacketIn packetInUnknownDest;
    protected OFPacketIn broadcastPacketIn;
    protected OFPacketOut packetOut;
    protected IPacket testPacket;
    protected IPacket testMulticastPacket;                    // added for multi-action packet out for netVirt-broadcast
    protected IPacket testSecondMulticastPacket;
    protected byte[] testPacketSerialized;
    protected byte[] testSecondMulticastPacketSerialized;
    protected byte[] testMulticastPacketSerialized;            // added for multi-action packet out for netVirt-broadcast
    protected IPacket testPacketUnknownDest;
    protected byte[] testPacketUnknownDestSerialized;
    protected IPacket testBroadcastPacket;
    protected byte[] testBroadcastPacketSerialized;
    protected IRoutingDecision decision;
    protected int fastWildcards;
    protected int expected_wildcards;
    protected Date currentDate;

    protected int DIRECT_LINK = 1;
    protected int MULTIHOP_LINK = 2;
    protected int TUNNEL_LINK = 3;

    @Before
    public void setUp() throws Exception {

        // Mock context
        cntx = new ListenerContext();
        mockControllerProvider = new MockControllerProvider();
        forwarding = new Forwarding();
        threadPool = new MockThreadPoolService();
        deviceManager = new MockDeviceManager();
        routingEngine = createMock(IRoutingService.class);
        topology = createMock(ITopologyService.class);
        bettertopology = createMock(IBetterTopologyService.class);
        //topology = new BetterTopologyManager();
        tunnelManager = createMock(ITunnelManagerService.class);
        flowReconcileMgr = createMock(IFlowReconcileService.class);
        rewriteService = createNiceMock(IRewriteService.class);
        restApi = createNiceMock(IRestApiService.class);
        addressSpaceMgr = createMock(IAddressSpaceManagerService.class);
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();

        BetterFlowCache betterFlowCacheMgr = new BetterFlowCache();
        betterFlowCacheMgr.setAppName("netVirt");


        ModuleContext fmc = new ModuleContext();
        fmc.addService(IControllerService.class,
                       mockControllerProvider);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IBetterTopologyService.class, bettertopology);
        fmc.addService(IThreadPoolService.class, threadPool);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(ICounterStoreService.class, new CounterStore());
        fmc.addService(IDeviceService.class, deviceManager);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(ITunnelManagerService.class, tunnelManager);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IRewriteService.class, rewriteService);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IAddressSpaceManagerService.class, addressSpaceMgr);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IStorageSourceService.class, new MemoryStorageSource());

        deviceManager.init(fmc);
        betterFlowCacheMgr.init(fmc);
        threadPool.init(fmc);
        forwarding.init(fmc);
        entityClassifier.init(fmc);
        threadPool.startUp(fmc);
        deviceManager.startUp(fmc);
        betterFlowCacheMgr.startUp(fmc);
        forwarding.startUp(fmc);
        entityClassifier.startUp(fmc);

        // Mock tunnel service
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.isTunnelSubnet(EasyMock.anyInt())).andReturn(false).anyTimes();
        replay(tunnelManager);

        // Mock switches
        sw1 = EasyMock.createMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();

        expect(sw1.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();

        sw2 = EasyMock.createMock(IOFSwitch.class);
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.getStringId()).andReturn("00:00:00:00:00:00:00:02").anyTimes();

        expect(sw2.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();

        sw3 = EasyMock.createMock(IOFSwitch.class);
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.getStringId()).andReturn("00:00:00:00:00:00:00:03").anyTimes();

        expect(sw3.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();  //switch 3 belongs to cluster 3. as it is on its own.
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();  //switch 3 belongs to cluster 3. as it is on its own.

        wc1 = new Capture<OFMessage>(CaptureType.ALL);  //capture message on switch 1.
        wc2 = new Capture<OFMessage>(CaptureType.ALL);
        wc3 = new Capture<OFMessage>(CaptureType.ALL);

        fc1 = new Capture<ListenerContext>(CaptureType.ALL);
        fc2 = new Capture<ListenerContext>(CaptureType.ALL);
        fc3 = new Capture<ListenerContext>(CaptureType.ALL);


        //fastWilcards mocked as this constant
        fastWildcards = OFMatch.OFPFW_IN_PORT | OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_TP_SRC
                | OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_NW_SRC_ALL | OFMatch.OFPFW_NW_DST_ALL
                | OFMatch.OFPFW_NW_TOS;

        // for netVirt-broadcast multi-action packet
        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(false).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();

        expect(sw2.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(false).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();

        expect(sw3.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(false).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();

        // Load the switch map
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        mockControllerProvider.setSwitches(switches);

        // Build test packet
        testPacket = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setSourceMACAddress("00:44:33:22:11:00")
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
        testPacketSerialized = testPacket.serialize();


        // Build test packet for multi-action netVirt-broadcast
        testMulticastPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:11:33:55:77:03")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.10.3")
                .setDestinationAddress("192.168.255.255")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testMulticastPacketSerialized = testMulticastPacket.serialize();

        // Build test packet for multi-action netVirt-broadcast
        testSecondMulticastPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:11:33:55:77:01")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.10.1")
                .setDestinationAddress("192.168.255.255")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testSecondMulticastPacketSerialized = testSecondMulticastPacket.serialize();

        testPacketUnknownDest = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:66")
        .setSourceMACAddress("00:44:33:22:11:00")
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
        testPacketUnknownDestSerialized = testPacketUnknownDest.serialize();

        testBroadcastPacket = new Ethernet()
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setSourceMACAddress("00:11:22:33:44:66")
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
        testBroadcastPacketSerialized = testBroadcastPacket.serialize();

        // Build src and dest devices
        byte[] dataLayerSource = ((Ethernet)testPacket).getSourceMACAddress();
        byte[] dataLayerDest = ((Ethernet)testPacket).getDestinationMACAddress();
        int networkSource = ((IPv4)((Ethernet)testPacket).getPayload()).getSourceAddress();
        int networkDest = ((IPv4)((Ethernet)testPacket).getPayload()).getDestinationAddress();

        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort(),
                                              EasyMock.anyBoolean()))
        .andReturn(true).anyTimes();
        replay(topology);

        currentDate = new Date();
        srcDevice =
                deviceManager.learnEntity(Ethernet.toLong(dataLayerSource),
                                          null, networkSource,
                                          1L, 1);
        dstDevice1 =
                deviceManager.learnEntity(Ethernet.toLong(dataLayerDest),
                                          null, networkDest,
                                          2L, 3);

        // Mock Packet-in
        packetIn = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        packetInUnknownDest = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketUnknownDestSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketUnknownDestSerialized.length);

        // Mock Packet-in multi-action netVirt-broadcast
        multicastPacketIn = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testMulticastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testMulticastPacketSerialized.length);

        // second multicast packet in
        secondMulticastPacketIn = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testSecondMulticastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testSecondMulticastPacketSerialized.length);


        broadcastPacketIn = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testBroadcastPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testBroadcastPacketSerialized.length);

        // Mock Packet-out
        packetOut =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(this.packetIn.getBufferId())
        .setInPort(this.packetIn.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput((short)3, (short) 0xffff));
        packetOut.setActions(poactions)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);

        expected_wildcards = fastWildcards;
        expected_wildcards &= ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN &
                ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST;
        expected_wildcards &= ~OFMatch.OFPFW_DL_TYPE; // & ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK;

        // Mock decision
        decision = createMock(IRoutingDecision.class);
        expect(decision.getSourceDevice()).andReturn(srcDevice).atLeastOnce();
        expect(decision.getSourcePort()).andReturn(new SwitchPort(1L, (short)1)).atLeastOnce();
        dstDevices = new ArrayList<IDevice>();
        expect(decision.getDestinationDevices()).andReturn(dstDevices).atLeastOnce();
        IRoutingDecision.rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION, decision);

        IDeviceService.fcStore.
            put(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE,
                srcDevice);

        // set decision.getRoutingAction() based on test case
        // Set SRC_IFACCES in context
        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        VNS netVirt = new VNS("default");
        srcIfaces.add(new VNSInterface("testSrcIface1", netVirt, null, null));
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        // Mock default behavior for getSwitchPortVlanMode
        expect(rewriteService.getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                                    anyObject(String.class),
                                                    anyShort(),
                                                    anyBoolean()))
                .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        replay(rewriteService);

    }

    @After
    public void tearDown() {
        verify(tunnelManager);
    }

    /*
     * Verifies that ofm is a PacketOut that has ports.size() output actions,
     * involving the ports specified in the action. It also verifies that the
     * packet date matches packetData. If packetData is null we simply ignore
     * it
     */
    protected void assertPacketOut(OFMessage ofm, byte[] packetData,
                                   Short[] ports) {
        assertNotNull(ofm);
        assertNotNull(ports);
        assertEquals(true, ofm instanceof OFPacketOut);
        OFPacketOut ofpo = (OFPacketOut) ofm;

        List<OFAction> actions = ofpo.getActions();
        assertEquals(ports.length, actions.size());
        HashSet<Short> packetOutPorts = new HashSet<Short>();
        for (OFAction action: actions) {
            assertEquals(true, action instanceof OFActionOutput);
            OFActionOutput a = (OFActionOutput)action;
            packetOutPorts.add(a.getPort());
        }
        Arrays.sort(ports);
        Short[] packetOutPortsArray =
                packetOutPorts.toArray(new Short[0]);
        Arrays.sort(packetOutPortsArray);
        assertArrayEquals(ports, packetOutPortsArray);
        if (packetData != null) {
            // a mismatch here usually indicates that something
            // went wrong with rewriting.
            assertArrayEquals(packetData, ofpo.getPacketData());
        }
    }

    @Test
    public void testForwardMultiSwitchPath() throws Exception {

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).anyTimes();

        replay(sw1, sw2, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m: msglist) {
            if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
            else if (m instanceof OFFlowMod)
                assertEquals(m, fm1);
        }

        OFMessage m = wc2.getValue();
        assertTrue(m.equals(fm2));
    }

    /**
     * In this scenario, the packet-in is from an internal switch port,
     * however the internal switch-port is not part of the route.  In this
     * case, we will setup the correct flow-mod, but we will not send
     * packet-out.
     * @throws Exception
     */
    @Test
    public void testNoPacketOutWithFlowMod() throws Exception {

        // Mock decision
        reset(decision);
        expect(decision.getSourceDevice()).andReturn(srcDevice).atLeastOnce();
        dstDevices = new ArrayList<IDevice>();
        expect(decision.getDestinationDevices()).andReturn(dstDevices).atLeastOnce();
        expect(decision.getSourcePort()).andReturn(new SwitchPort(2L, (short)1)).atLeastOnce();
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods.
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();

        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only
        fm2.getMatch().setInputPort((short)10);

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1, true)).andReturn(false).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(1L, (short)1, 1L, (short)1, true)).andReturn(true).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)10));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).anyTimes();

        replay(sw1, sw2, routingEngine, decision, topology);
        wc1.reset(); wc2.reset();
        // Packet-in is on switch 2, inPort = 1
        forwarding.receive(sw2, this.packetIn, cntx);
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);

        assertTrue(wc1.hasCaptured());  // wc1 should get flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod, and no packet-out.

        List<OFMessage> msgList = wc1.getValues();
        assertTrue (msgList.size() == 1);
        for(OFMessage m: msgList) {
            assertTrue(m.equals(fm1));
        }

        msgList = wc2.getValues();
        assertTrue (msgList.size() == 1);
        for(OFMessage m: msgList) {
            assertTrue(m.equals(fm2));
        }
    }


    /**
     * In this scenario, the packet-in is from an internal switch port,
     * and the internal switch port is part of the route. In this case,
     * there should be a flow-mod on both switches on the path, and
     * packet-out on the second switch.
     * @throws Exception
     */
    @Test
    public void testPacketOutWithFlowModOnIntermedaiteSwitch() throws Exception {

        // Mock decision
        reset(decision);
        expect(decision.getSourceDevice()).andReturn(srcDevice).atLeastOnce();
        dstDevices = new ArrayList<IDevice>();
        expect(decision.getDestinationDevices()).andReturn(dstDevices).atLeastOnce();
        expect(decision.getSourcePort()).andReturn(new SwitchPort(2L, (short)1)).atLeastOnce();
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods.
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();

        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1, true)).andReturn(false).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(1L, (short)1, 1L, (short)1, true)).andReturn(true).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).anyTimes();

        replay(sw1, sw2, routingEngine, decision, topology);
        wc1.reset(); wc2.reset();
        // Packet-in is on switch 2, inPort = 1
        forwarding.receive(sw2, this.packetIn, cntx);
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);

        assertTrue(wc1.hasCaptured());  // wc1 should get flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod, and no packet-out.

        List<OFMessage> msgList = wc1.getValues();
        assertTrue (msgList.size() == 1);
        for(OFMessage m: msgList) {
            assertTrue(m.equals(fm1));
        }

        msgList = wc2.getValues();
        assertTrue (msgList.size() == 2);
        for(OFMessage m: msgList) {
            if (m instanceof OFFlowMod)
                assertTrue(m.equals(fm2));
            else if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
        }
    }

    /**
     * This test checks to see if the flow-mods are NOT installed if the
     * packet-in comes in on a wrong attachment point port.  If the packet-in
     * switch port is an attachment point port and is not present on the
     * route returned by topology, then the flow-mod will not be installed,
     * and packet out will not be sent.
     * @throws Exception
     */
    @Test
    public void testIgnoreFlowModOnIncorrectPacketInPort() throws Exception {
        // Mock the sdnplatform provider service
        IControllerService bcp = createMock(IControllerService.class);
        forwarding.setControllerProvider(bcp);
        IControllerService.bcStore.put(cntx,
                                IControllerService.CONTEXT_PI_PAYLOAD,
                                (Ethernet)testPacket);
        expect(bcp.getOFMessageFactory()).andReturn(mockControllerProvider.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(sw1, getFakeArpPi(this.packetIn,
                                   (Ethernet)testPacket, null, null))).andReturn(true);
        expectLastCall().times(2);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)1, true))
                       .andReturn(false).anyTimes();
        expect(topology.getAllowedIncomingBroadcastPort(1L, (short)1, true))
                       .andReturn(new NodePortTuple(1L, (short)1)).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)10));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).anyTimes();

        replay(sw1, sw2, routingEngine, decision, topology, bcp);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(bcp);

        // No flow-mod or packet-out was received on either switch.
        assertFalse(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
    }

    private void setupDevice2() {
        byte[] dataLayerSource = ((Ethernet)testPacket).getSourceMACAddress();
        byte[] dataLayerDest =
                ((Ethernet)testPacket).getDestinationMACAddress();
        int networkSource =
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getSourceAddress();
        int networkDest =
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getDestinationAddress();
        deviceManager.startUp(null);

        srcDevice =
                deviceManager.learnEntity(Ethernet.toLong(dataLayerSource),
                                          null, networkSource,
                                          1L, 1);
        dstDevice2 =
                deviceManager.learnEntity(Ethernet.toLong(dataLayerDest),
                                          null, networkDest,
                                          1L, 3);
    }

    @Test
    public void testForwardSingleSwitchPath() throws Exception {
        setupDevice2();

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as local and Mock route
        dstDevices.add(dstDevice2);
        Route route = new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice2.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)3)).anyTimes();

        replay(sw1, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, routingEngine, decision);
    }

    @Test
    public void testFlowDampening() throws Exception {
        int timeout = 40;
        int timeToSleep = 50;
        forwarding.setMessageDamper(
                new OFMessageDamper(100,
                                    EnumSet.of(OFType.FLOW_MOD),
                                    timeout));
        setupDevice2();

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as local and Mock route
        dstDevices.add(dstDevice2);
        Route route = new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice2.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // WE CANNOT USE MULTIPLE SWITCHES BECAUSE EASYMOCK DOESN'T ALLOW
        // US TO OVERWRITE hashCode() and equals()
        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        expectLastCall().times(2);
        sw1.write(packetOut, cntx);
        expectLastCall().times(4);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)3)).anyTimes();

        replay(sw1, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        forwarding.receive(sw1, this.packetIn, cntx);
        forwarding.receive(sw1, this.packetIn, cntx);
        Thread.sleep(timeToSleep);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, routingEngine, decision);
    }

    @Test
    public void testForwardWithNoOfppTable() throws Exception {
        setupDevice2();

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        reset(topology);
        // Set OFPP_FLOOD not supported
        resetToNice(sw1);
        //expect(sw1.getOutputStream()).andReturn(out1).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(false).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();

        // Set destination as local and Mock route
        dstDevices.add(dstDevice2);
        Route route = new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice2.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        int wc = expected_wildcards & ~OFMatch.OFPFW_NW_SRC_MASK
                & ~OFMatch.OFPFW_NW_DST_MASK;

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(wc))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // Set expected packet-out
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput((short) 3, (short) 0xffff));
        packetOut.setActions(poactions)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);

        // Reset mocks, trigger the packet in, and validate results
        expect(topology.getIncomingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();

        replay(sw1, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, routingEngine, decision);
    }

    @Test
    public void testForwardWithNoL3Match() throws Exception {
        setupDevice2();

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set OFPP_FLOOD not supported
        resetToNice(sw1);
        reset(topology);
        //expect(sw1.getOutputStream()).andReturn(out1).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(false).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();

        // Set destination as local and Mock route
        dstDevices.add(dstDevice2);

        Route route = new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice2.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(fastWildcards &
                        ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN &
                        ~OFMatch.OFPFW_DL_SRC  & ~OFMatch.OFPFW_DL_DST))
                        .setActions(actions)
                        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                        .setCookie(2L << 52)
                        .setFlags((short)1)
                        .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);

        // Reset mocks, trigger the packet in, and validate results
        expect(topology.getIncomingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();

        replay(sw1, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, routingEngine, decision);
    }

    @Test
    public void testForwardWithFastWildcardsAll() throws Exception {
        doTestForwardWithFastWildcard(OFMatch.OFPFW_ALL);
    }

    @Test
    public void testForwardWithFastWildcardsNone() throws Exception {
        doTestForwardWithFastWildcard(0);
    }

    protected void doTestForwardWithFastWildcard(int wildcards) throws IOException {
        setupDevice2();
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set OFPP_FLOOD not supported
        resetToNice(sw1);
        reset(topology);
        //expect(sw1.getOutputStream()).andReturn(out1).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(wildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();

        // Set destination as local and Mock route
        dstDevices.add(dstDevice2);
        Route route = new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice2.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(wildcards &
                        ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN &
                        ~OFMatch.OFPFW_DL_SRC  & ~OFMatch.OFPFW_DL_DST &
                        ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK))
                        .setActions(actions)
                        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                        .setCookie(2L << 52)
                        .setFlags((short)1)
                        .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);

        // Reset mocks, trigger the packet in, and validate results
        expect(topology.getIncomingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 1, (short)3, true)).andReturn(new NodePortTuple(1, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();

        replay(sw1, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, routingEngine, decision);
    }

    @Test
    public void testForwardNoPath() throws Exception {
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();

        // Set no destination attachment point or route
        // expect no Flow-mod or packet out
        reset(sw1, sw2, routingEngine);

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision);
    }

    @Test
    public void testForwardWithHints() throws Exception {


        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(new Integer(expected_wildcards & ~OFMatch.OFPFW_IN_PORT)).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);
        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = new OFFlowMod();
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards & ~OFMatch.OFPFW_IN_PORT))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        fm1.setFlags((short)1);
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);
        sw2.write(fm2, cntx);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();


        replay(sw1, sw2, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision);
    }

    @Test
    public void testForwardOrFloodNoPath() throws Exception {
        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testPacket);
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).atLeastOnce();

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)1, false)).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(2L, (short)1, false)).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)3, false)).andReturn(true).anyTimes();
        // Set no destination attachment point or route
        // expect no Flow-mod
        // expect packet-out action to be multi-action packet-out

        // Even though we have other other switches in the openflow domain,
        // we are giving only one switch to see if the broadcast happens there.
        Set<Long> switches = new HashSet<Long>();
        switches.add(1L);
        switches.add(2L);
        expect(topology.getSwitchesInOpenflowDomain(1L, false)).andReturn(switches).anyTimes();
        List<Short> ports = Arrays.asList(new Short[] { (short)6, (short)7, (short)100 });
        HashSet<Short> portSet = new HashSet<Short>(ports);
        expect (sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect (sw2.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect (topology.getPorts(1L)).andReturn(portSet).anyTimes();
        expect (topology.getPorts(2L)).andReturn(portSet).anyTimes();
        expect (topology.getPorts(EasyMock.anyLong())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.getPortsWithLinks(EasyMock.anyLong())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect (topology.getBroadcastPorts(EasyMock.anyLong(), EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.isConsistent(1L, (short)1, 1L, (short)1, false)).andReturn(true).anyTimes();
        // Record expected packet-outs/flow-mods
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().once();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().once();
        // TODO: verify correct broadcast cache behavior
        sw1.updateBroadcastCache(anyLong(), eq((short)1));
        expectLastCall().andReturn(false).once();

        // Tunnel port number is set to 100 in all the switches.
        // However, the tunnel itself is not active -- hence tunnel
        // is not knwon to topology.
        reset(tunnelManager);
        // Mock tunnel service
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong()))
        .andReturn((short)100).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, topology, tunnelManager, decision);

        assertPacketOut(wc1.getValue(), this.testPacketSerialized,
                        new Short[] { 6, 7 } );
    }

    @Test
    public void testForwardOrFloodNoPathNoOfppFlood() throws Exception {
        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testPacket);
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).atLeastOnce();

        // Set OFPP_FLOOD not supported
        resetToNice(sw1);
        reset(topology);
        //expect(sw1.getOutputStream()).andReturn(out1).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        Set<Long> switches = new HashSet<Long>();
        switches.add(1L);
        switches.add(2L);
        expect(topology.getSwitchesInOpenflowDomain(1L, false)).andReturn(switches).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)1, false)).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)3, false)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        expect(sw1.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(false).anyTimes();

        // Set no destination attachment point or route
        // expect no Flow-mod
        // expect packet-out action to be multi-action packet out
        List<Short> ports = Arrays.asList(new Short[] { (short)6, (short)7, (short)100 });
        expect (sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect (sw2.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        HashSet<Short> portSet = new HashSet<Short>(ports);
        expect (topology.getPorts(1L)).andReturn(portSet).anyTimes();
        expect (topology.getPorts(2L)).andReturn(portSet).anyTimes();
        expect (topology.getPorts(EasyMock.anyLong())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.getPortsWithLinks(EasyMock.anyLong())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.getBroadcastPorts(EasyMock.anyLong(), EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.isConsistent(1L, (short)1, 1L, (short)1, false)).andReturn(true).anyTimes();
        // Record expected packet-outs/flow-mods
        sw1.write(capture(wc1), capture(fc1));
        sw2.write(capture(wc2), capture(fc2));

        // Tunnel port number is set to 100 in all the switches.
        // However, the tunnel itself is not active -- hence tunnel
        // is not knwon to topology.
        reset(tunnelManager);
        // Mock tunnel service
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong()))
        .andReturn((short)100).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, topology, tunnelManager, decision);

        assertPacketOut(wc1.getValue(), this.testPacketSerialized,
                        new Short[] { 6, 7 } );
        assertPacketOut(wc2.getValue(), this.testPacketSerialized,
                        new Short[] { 6, 7 } );
    }

    @Test
    public void testForwardOrFloodWithPath() throws Exception {
        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<OFMessage> wc2 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> bc1 = new Capture<ListenerContext>(CaptureType.ALL);
        Capture<ListenerContext> bc2 = new Capture<ListenerContext>(CaptureType.ALL);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testPacket);
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);
        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(AppCookie.makeCookie(2, 0))
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        fm1.setFlags((short)1);
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);


        sw1.write(capture(wc1), capture(bc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(bc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        expect(topology.isBroadcastDomainPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(false).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();

        replay(sw1, sw2, routingEngine, decision, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision);
        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m: msglist) {
            if (m instanceof OFFlowMod)
                assertTrue(m.equals(fm1));
            else if (m instanceof OFPacketOut)
                assertTrue(m.equals(packetOut));
        }

        OFMessage m = wc2.getValue();
        assertTrue(m.equals(fm2));
    }

    @Test
    public void testFakeArpFromAHost() throws Exception {
        IControllerService bcp = createMock(IControllerService.class);
        forwarding.setControllerProvider(bcp);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testPacketUnknownDest);
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(bcp.getOFMessageFactory()).andReturn(mockControllerProvider.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(sw1, getFakeArpPi(packetInUnknownDest, (Ethernet)testPacketUnknownDest, null, null))).andReturn(true);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)1, true)).andReturn(false).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, decision, bcp, topology);

        forwarding.receive(sw1, this.packetInUnknownDest, cntx);

        verify(sw1);
        verify(bcp);
        verify(decision);
    }

    @Test
    public void testFakeArpFromABroadcastDomain() throws Exception {
        IControllerService bcp = createMock(IControllerService.class);
        forwarding.setControllerProvider(bcp);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testPacketUnknownDest);
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(bcp.getOFMessageFactory()).andReturn(mockControllerProvider.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(sw2, getFakeArpPi(packetInUnknownDest, (Ethernet)testPacketUnknownDest, null, null))).andReturn(true);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getAllowedIncomingBroadcastPort(1L, (short)1, true)).
            andReturn(new NodePortTuple(2L, (short)1)).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, decision, bcp, topology);

        forwarding.receive(sw1, this.packetInUnknownDest, cntx);

        verify(sw1, sw2);
        verify(bcp);
        verify(decision);
    }

    /**
     * Creates a fake ARP PacketIn message
     */
    private OFPacketIn getFakeArpPi(OFPacketIn pi, Ethernet eth,
                                    Integer targetIpInput,
                                    Short packetInPortInput) {
        IPv4 payload = (IPv4)eth.getPayload();
        int targetIp = payload.getDestinationAddress();
        if (targetIpInput != null) {
            targetIp = targetIpInput.intValue();
        }
        short packetInPort = pi.getInPort();
        if (packetInPortInput != null) {
            packetInPort = packetInPortInput.shortValue();
        }
        IPacket arpRequest = new Ethernet()
        .setSourceMACAddress(eth.getSourceMACAddress())
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REQUEST)
                .setSenderHardwareAddress(eth.getSourceMACAddress())
                .setSenderProtocolAddress(0)
                .setTargetHardwareAddress(new byte[] { 0, 0, 0, 0, 0, 0} )
                .setTargetProtocolAddress(targetIp));
        byte[] arpRequestSerialized = arpRequest.serialize();

        OFPacketIn fakePi =
                (OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN);
        fakePi.setInPort(packetInPort);
        fakePi.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        fakePi.setReason(OFPacketInReason.NO_MATCH);
        fakePi.setPacketData(arpRequestSerialized);
        fakePi.setTotalLength((short) arpRequestSerialized.length);
        fakePi.setLength(OFPacketIn.MINIMUM_LENGTH);

        return fakePi;
    }



    protected void setupMulticastTest(IEntityClass addressSpace)
            throws Exception {
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        expect(sw1.updateBroadcastCache(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();
        expect(sw2.updateBroadcastCache(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        sw3.write(capture(wc3), capture(fc3));
        expectLastCall().anyTimes();
        expect(sw3.updateBroadcastCache(anyLong(), anyShort()))
                .andReturn(false).anyTimes();

        deviceManager.startUp(null);

        if (addressSpace != null) {
            reset(topology);
            expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                                  EasyMock.anyShort()))
                    .andReturn(true).anyTimes();
            topology.addListener(deviceManager);
            expectLastCall().once();
            IEntityClassifierService ecs =
                    createMock(IEntityClassifierService.class);
            expect(ecs.classifyEntity(anyObject(Entity.class)))
                    .andReturn(addressSpace).anyTimes();
            expect(ecs.getKeyFields())
                    .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC,
                                          DeviceField.SWITCH, DeviceField.PORT))
                    .anyTimes();
            ecs.addListener(deviceManager);
            expectLastCall().anyTimes();
            replay(ecs, topology);
            deviceManager.setEntityClassifier(ecs);
        }
        //setup 5 devices based on three switches.  for multi-action
        // netVirt-broadcast
        device1 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:01"),
                                          null,
                                          IPv4.toIPv4Address("192.168.10.1"),
                                          1L, 3);
        device2 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:02"),
                                      null,
                                      IPv4.toIPv4Address("192.168.10.2"),
                                      1L, 4);
        device3 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:03"),
                                      null,
                                      IPv4.toIPv4Address("192.168.10.3"),
                                      2L, 3);
        device4 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:04"),
                                      null,
                                      IPv4.toIPv4Address("192.168.10.4"),
                                      3L, 3);
        device5 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:05"),
                                      null,
                                      IPv4.toIPv4Address("192.168.10.5"),
                                      3L, 4);

    }

    @Test
    public void testMulticast() throws Exception {
        setupMulticastTest(null);

        ArrayList<IDevice> dstDevices = new ArrayList<IDevice>();
        dstDevices.add(device1);
        dstDevices.add(device2);
        dstDevices.add(device3);
        dstDevices.add(device4);
        dstDevices.add(device5);

        // we alternate in handing in an empty interface list and a
        // null pointer
        List<SwitchPort> multicastIfaces = new ArrayList<SwitchPort>();

        // TEST 1:  Send a packet from device 3; (switch 2, port #3)
        // Verifies the multi-action packet output.

        IRoutingDecision mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device3).atLeastOnce();
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(2L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(multicastIfaces).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testMulticastPacket);

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)4)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)4)).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(true).anyTimes();
        expect(topology.getOpenflowDomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getOpenflowDomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getOpenflowDomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.inSameL2Domain(1L, 1L)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(1L, 2L)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(2L, 1L)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(2L, 2L)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(1L, 3L)).andReturn(false).anyTimes();
        expect(topology.inSameL2Domain(2L, 3L)).andReturn(false).anyTimes();
        expect(topology.inSameL2Domain(3L, 1L)).andReturn(false).anyTimes();
        expect(topology.inSameL2Domain(3L, 2L)).andReturn(false).anyTimes();
        expect(topology.inSameL2Domain(3L, 3L)).andReturn(true).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(1L, (short)3, 1L, (short)3, true)).andReturn(new NodePortTuple(1L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(1L, (short)3, 2L, (short)3, true)).andReturn(new NodePortTuple(2L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(1L, (short)3, 1L, (short)4, true)).andReturn(new NodePortTuple(1L, (short)4)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 1L, (short)3, true)).andReturn(new NodePortTuple(1L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 1L, (short)4, true)).andReturn(new NodePortTuple(1L, (short)4)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 2L, (short)3, true)).andReturn(new NodePortTuple(2L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 3L, (short)3, true)).andReturn(new NodePortTuple(3L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 3L, (short)4, true)).andReturn(new NodePortTuple(3L, (short)4)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(3L, (short)3, 3L, (short)3, true)).andReturn(new NodePortTuple(3L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(3L, (short)3, 3L, (short)4, true)).andReturn(new NodePortTuple(3L, (short)4)).anyTimes();
        expect(topology.isInSameBroadcastDomain(1L, (short)3, 1L, (short)3, true)).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(1L, (short)3, 2L, (short)3, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(1L, (short)3, 1L, (short)4, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 1L, (short)3, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 1L, (short)4, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 2L, (short)3, true)).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 3L, (short)3, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 3L, (short)4, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(3L, (short)3, 3L, (short)3, true)).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(3L, (short)3, 3L, (short)4, true)).andReturn(false).anyTimes();
        replay(sw1, sw2, sw3, mydecision, topology);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);

        verify(sw1, sw2, sw3, mydecision);

        assertTrue(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        assertPacketOut(wc1.getValue(), this.testMulticastPacketSerialized,
                        new Short[] { 3,4});


        ///////////////////////////////////////////////////////////////////////
        // TEST 2.  Send the same packet again and test if it is not broadcast.
        ///////////////////////////////////////////////////////////////////////

        wc1.reset(); wc2.reset(); wc3.reset();

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testMulticastPacket);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);

        assertFalse(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        ///////////////////////////////////////////////////////////////////////
        // TEST 3.  Send a packet from a different cluster.
        ///////////////////////////////////////////////////////////////////////

        wc1.reset(); wc2.reset(); wc3.reset();

        mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device3).atLeastOnce(); // for tunnel check
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(3L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(null).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);
        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testMulticastPacket);


        replay (mydecision);

        forwarding.processPacketInMessage(sw3, this.multicastPacketIn, mydecision, cntx);

        verify(sw1, sw2, sw3, mydecision);

        assertFalse(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertTrue(wc3.hasCaptured());

        assertPacketOut(wc3.getValue(), this.testMulticastPacketSerialized,
                        new Short[] { 4});

        ///////////////////////////////////////////////////////////////////////
        // TEST 4.  Send a different packet.
        ///////////////////////////////////////////////////////////////////////
        wc1.reset(); wc2.reset(); wc3.reset();

        mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device1).anyTimes(); // for tunnel check
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(1L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(null).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);
        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testSecondMulticastPacket);

        replay (mydecision);
        forwarding.processPacketInMessage(sw1, this.secondMulticastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        assertPacketOut(wc1.getValue(), this.testSecondMulticastPacketSerialized,
                        new Short[] { 4 });
        assertPacketOut(wc2.getValue(), this.testSecondMulticastPacketSerialized,
                        new Short[] { 3 });

        ///////////////////////////////////////////////////////////////////////
        // TEST 5. Wait 6 seconds; and then try again.
        ///////////////////////////////////////////////////////////////////////

        // clear out broadcast cache
        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        wc1.reset(); wc2.reset(); wc3.reset();

        mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device3).anyTimes(); // for tunnel check
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(2L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(multicastIfaces).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testMulticastPacket);

        replay (mydecision);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision);

        assertFalse(wc3.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertTrue(wc1.hasCaptured());

        assertPacketOut(wc1.getValue(), this.testMulticastPacketSerialized,
                        new Short[] { 3,4});
    }


    /*
     * Test VLAN rewrite, Src and Dst Mac rewrite.
     * Focus is on VLAN rewrite.
     */
    @Test
    public void testMulticastWithRewrites() throws Exception {
        Short vlan = 1;
        String addressSpaceName = "MyAddressSpace";
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);

        setupMulticastTest(addressSpace);

        ArrayList<IDevice> dstDevices = new ArrayList<IDevice>();
        dstDevices.add(device1);
        dstDevices.add(device2);
        dstDevices.add(device3);
        dstDevices.add(device4);
        dstDevices.add(device5);

        List<SwitchPort> multicastIfaces = new ArrayList<SwitchPort>();
        multicastIfaces.add(new SwitchPort(2L, 4));
        multicastIfaces.add(new SwitchPort(1L, 5));

        SwitchPort swp1x3 = new SwitchPort(1L, 3);
        SwitchPort swp1x5 = new SwitchPort(1L, 5);

        IRoutingDecision mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device3).anyTimes();
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(2L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(multicastIfaces).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);


        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(true).anyTimes();
        expect(topology.getOpenflowDomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getOpenflowDomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getOpenflowDomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort(2L, (short)3, 2L, (short)4, true))
                .andReturn(new NodePortTuple(2L, (short)4)).anyTimes();
        expect(topology.getOutgoingSwitchPort(2L, (short)3, 1L, (short)5, true))
                .andReturn(new NodePortTuple(1L, (short)5)).anyTimes();
        // Setting up expectations for isAttachmetPointPort. All ports are
        // attachment point ports, except switch 2, port 4 which we'll toggle
        // using an answer object
        expect(topology.isAttachmentPointPort(not(eq(2L)), not(eq((short)4)), eq(false)))
                .andReturn(true).anyTimes();
        class MyAnswer implements IAnswer<Boolean> {
            Boolean value;
            @Override
            public Boolean answer() {
                return this.value;
            }
        }
        MyAnswer myAnswer = new MyAnswer();
        myAnswer.value = true;
        expect(topology.isAttachmentPointPort(eq(2L), eq((short)4), eq(false)))
                .andAnswer(myAnswer).anyTimes();
        expect(topology.inSameL2Domain(1L, 2L)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(2L, 2L)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(3L, 2L)).andReturn(false).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 1L, (short)3, true)).andReturn(new NodePortTuple(1L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 2L, (short)3, true)).andReturn(new NodePortTuple(2L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 1L, (short)4, true)).andReturn(new NodePortTuple(1L, (short)4)).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 1L, (short)3, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 2L, (short)3, true)).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 1L, (short)4, true)).andReturn(false).anyTimes();

        // TEST 1:  Send a packet from device 3; (switch 2, port #3)
        // Verifies the multi-action packet output.
        // Transport vlan set. All ports tag ==> we expect rewritten VLAN
        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService
                .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                       eq(addressSpaceName),
                                       anyShort(),
                                       anyBoolean()))
                .andReturn(vlan).anyTimes();

        IControllerService.bcStore.put(cntx,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet)testMulticastPacket.clone());
        Ethernet contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);

        replay(sw1, sw2, sw3, mydecision, topology, rewriteService);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision, rewriteService);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        Ethernet ethWithVlanSet = (Ethernet)testMulticastPacket.clone();
        ethWithVlanSet.setVlanID(vlan);
        assertPacketOut(wc1.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 3, 4, 5});
        assertPacketOut(wc2.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 4 });
        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        // Make sure we didn't change the PI_PAYLOAD in the context
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);


        // TEST 2:  Same as TEST 1 but no port tags. However, switch 2, port 4
        // is an internal port now ==> we expect tagging on it but not on
        // other ports
        wc1.reset(); wc2.reset(); wc3.reset();

        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(null).atLeastOnce();
        SwitchPort swp2x4 = new SwitchPort(2L, 4);
        expect(rewriteService
               .getSwitchPortVlanMode(eq(swp2x4),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        myAnswer.value = false;


        replay(rewriteService);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, rewriteService, mydecision);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        ethWithVlanSet = (Ethernet)testMulticastPacket.clone();
        ethWithVlanSet.setVlanID(vlan);
        assertPacketOut(wc1.getValue(), this.testMulticastPacketSerialized,
                        new Short[] { 3, 4, 5});
        assertPacketOut(wc2.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 4 });
        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        myAnswer.value = true;
        // Make sure we didn't change the PI_PAYLOAD in the context
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);

        //
        // TEST 3: same as TEST 1 but only switch 1, port 3,5 have
        //  the vlan tagged
        // and we rewrite the source MAC
        //
        wc1.reset(); wc2.reset(); wc3.reset();

        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(42L).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(null).atLeastOnce();
        SwitchPort otherSwp = and(not(eq(swp1x3)), not(eq(swp1x5)));
        expect(rewriteService
               .getSwitchPortVlanMode(otherSwp,
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(eq(swp1x3),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(eq(swp1x5),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();

        replay(rewriteService);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision, rewriteService);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        Ethernet ethRewrittenSrcMac = (Ethernet)testMulticastPacket.clone();
        assertEquals(ethRewrittenSrcMac, testMulticastPacket);
        ethRewrittenSrcMac.setSourceMACAddress(Ethernet.toByteArray(42L));
        ethWithVlanSet = (Ethernet) ethRewrittenSrcMac.clone();
        ethWithVlanSet.setVlanID(vlan);
        List<OFMessage> ofms = wc1.getValues();
        assertEquals(2, ofms.size());
        for (OFMessage ofm: ofms) {
            assertEquals(true, ofm instanceof OFPacketOut);
            OFPacketOut ofpo = (OFPacketOut)ofm;
            // This is a hack to get the right capture to the right
            // assertPacketOut call...
            if (ofpo.getActions().size() == 1) {
                assertPacketOut(ofm, ethRewrittenSrcMac.serialize(),
                            new Short[] { 4 });
            } else {
                assertPacketOut(ofm, ethWithVlanSet.serialize(),
                            new Short[] { 3, 5 });
            }

        }
        assertPacketOut(wc2.getValue(), ethRewrittenSrcMac.serialize(),
                        new Short[] { 4 });
        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        // Make sure we didn't change the PI_PAYLOAD in the context
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);

        //
        // TEST 4: same as TEST 1 but now switch 1, port 3,5 have
        //  the vlan untagged and we rewrite dst MAC
        //
        wc1.reset(); wc2.reset(); wc3.reset();

        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(1L).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(null).atLeastOnce();
        otherSwp = and(not(eq(swp1x3)), not(eq(swp1x5)));
        expect(rewriteService
               .getSwitchPortVlanMode(otherSwp,
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(eq(swp1x3),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(eq(swp1x5),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();

        replay(rewriteService);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision, rewriteService);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        Ethernet ethRewrittenDstMac = (Ethernet)testMulticastPacket.clone();
        assertEquals(ethRewrittenDstMac, testMulticastPacket);
        ethRewrittenDstMac.setDestinationMACAddress(Ethernet.toByteArray(1L));
        ethWithVlanSet = (Ethernet)ethRewrittenDstMac.clone();
        ethWithVlanSet.setVlanID(vlan);
        ofms = wc1.getValues();
        assertEquals(2, ofms.size());
        for (OFMessage ofm: ofms) {
            assertEquals(true, ofm instanceof OFPacketOut);
            OFPacketOut ofpo = (OFPacketOut)ofm;
            // This is a hack to get the right capture to the right
            // assertPacketOut call...
            if (ofpo.getActions().size() == 1) {
                assertPacketOut(ofm, ethWithVlanSet.serialize(),
                            new Short[] { 4 });
            } else {
                assertPacketOut(ofm, ethRewrittenDstMac.serialize(),
                            new Short[] { 3, 5 });
            }

        }
        assertPacketOut(wc2.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 4 });
        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        // Make sure we didn't change the PI_PAYLOAD in the context
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);


        // TEST 5a:  Same as TEST 1
        // However, we decrement the TTL by 1
        wc1.reset(); wc2.reset(); wc3.reset();

        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(1).atLeastOnce();
        expect(rewriteService
                .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                       eq(addressSpaceName),
                                       anyShort(),
                                       anyBoolean()))
                .andReturn(vlan).anyTimes();

        IControllerService.bcStore.put(cntx,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet)testMulticastPacket.clone());
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);

        replay(rewriteService);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision, rewriteService);

        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        ethWithVlanSet = (Ethernet)testMulticastPacket.clone();
        ethWithVlanSet.setVlanID(vlan);
        IPv4 ip = (IPv4)ethWithVlanSet.getPayload();
        short newTtl = U8.f(ip.getTtl());
        newTtl -= (short)1;
        ip.setTtl(U8.t(newTtl));
        ip.resetChecksum();

        assertPacketOut(wc1.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 3, 4, 5});
        assertPacketOut(wc2.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 4 });
        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        // Make sure we didn't change the PI_PAYLOAD in the context
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);

        // TEST 5b:  Same as TEST 1
        // However, we decrement the TTL by 255 ==> Packet should be dropped
        wc1.reset(); wc2.reset(); wc3.reset();

        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(255).atLeastOnce();
        expect(rewriteService
                .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                       eq(addressSpaceName),
                                       anyShort(),
                                       anyBoolean()))
                .andReturn(vlan).anyTimes();

        IControllerService.bcStore.put(cntx,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet)testMulticastPacket.clone());
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);

        replay(rewriteService);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        verify(sw1, sw2, sw3, mydecision, rewriteService);

        assertFalse(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        forwarding.broadcastCache = new TimedCache<Long>(100, 5*1000);
        // Make sure we didn't change the PI_PAYLOAD in the context
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testMulticastPacket, contextEth);
    }

    @Test
    public void testBroadcastLoopSuppression() throws Exception {
        SwitchPort spt = new SwitchPort(1L, (short)3);

        int expectedPktHashCode = ForwardingBase.prime2 * 1 + ((Ethernet) testBroadcastPacket).hashCode();
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();

        deviceManager.startUp(null);

        // setup 2 devices based on one sw1
        device1 =
                deviceManager.learnEntity(HexString.toLong("00:11:22:33:44:66"),
                                          null,
                                          IPv4.toIPv4Address("192.168.10.1"),
                                          1L, 3);
        device2 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:02"),
                                          null,
                                          IPv4.toIPv4Address("192.168.10.2"),
                                          1L, 4);

        // TEST 1:  Send a packet from device 1; (switch 1, port #3)
        // Verifies the packet output.

        IRoutingDecision mydecision = createMock(IRoutingDecision.class);
        ArrayList<IDevice> dstDevices = new ArrayList<IDevice>();

        expect(mydecision.getSourceDevice()).andReturn(device1).anyTimes();
        expect(mydecision.getSourcePort()).andReturn(spt).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)3, false)).andReturn(true).anyTimes();

        Capture<Long> pktHashCode = new Capture<Long>(CaptureType.ALL);
        Capture<Short> inPortCapture = new Capture<Short>(CaptureType.ALL);
        sw1.updateBroadcastCache(captureLong(pktHashCode), capture(inPortCapture));
        expectLastCall().andReturn(false)
        .andReturn(true)
        .anyTimes();

        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testBroadcastPacket);

        // Even though we have other other switches in the openflow domain,
        // we are giving only one switch to see if the broadcast happens there.
        Set<Long> switches = new HashSet<Long>();
        switches.add(1L);
        switches.add(2L);
        expect(topology.getSwitchesInOpenflowDomain(1L, false)).andReturn(switches).anyTimes();
        List<Short> ports = Arrays.asList(new Short[] { (short)6, (short)7, (short)100 });
        expect (sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        HashSet<Short> portSet = new HashSet<Short>(ports);
        expect (topology.getPorts(1L)).andReturn(portSet).anyTimes();
        expect (topology.getPorts(EasyMock.anyLong())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.getPortsWithLinks(EasyMock.anyLong())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect (topology.getBroadcastPorts(EasyMock.anyLong(), EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(new HashSet<Short>()).anyTimes();
        expect (topology.isConsistent(1L, (short)3, 1L, (short)3, false)).andReturn(true).anyTimes();

        // Tunnel port number is set to 100 in all the switches.
        // However, the tunnel itself is not active -- hence tunnel
        // is not knwon to topology.
        reset(tunnelManager);
        // Mock tunnel service
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong()))
        .andReturn((short)100).anyTimes();

        replay(sw1, mydecision, topology, tunnelManager);
        forwarding.processPacketInMessage(sw1, this.broadcastPacketIn, mydecision, cntx);
        verify(sw1, mydecision, topology, tunnelManager);
        assertTrue(wc1.hasCaptured());
        assertTrue(expectedPktHashCode == pktHashCode.getValue().intValue());
        assertTrue(this.broadcastPacketIn.getInPort() == inPortCapture.getValue().shortValue());

        assertPacketOut(wc1.getValue(), this.testBroadcastPacketSerialized,
                        new Short[] { 6, 7});

        //assertTrue(actions.get(0) instanceof OFActionOutput);
        //OFActionOutput action = (OFActionOutput)actions.get(0);
        //assertTrue(action.getPort() == OFPort.OFPP_FLOOD.getValue());

        ///////////////////////////////////////////////////////////////////////
        // TEST 2.  Send the same packet again and test if it is suppressed.
        ///////////////////////////////////////////////////////////////////////

        wc1.reset();
        pktHashCode.reset();
        inPortCapture.reset();

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testBroadcastPacket);

        forwarding.processPacketInMessage(sw1, this.broadcastPacketIn, mydecision, cntx);

        assertFalse(wc1.hasCaptured());
        assertTrue(expectedPktHashCode == pktHashCode.getValue().intValue());
        assertTrue(this.broadcastPacketIn.getInPort() == inPortCapture.getValue().shortValue());
    }

    @Test
    public void testBroadcastDrop() throws Exception {
        // Mock decision
        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)this.testBroadcastPacket);
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).atLeastOnce();
        int pktHashCode = ((Ethernet)testBroadcastPacket).hashCode();
        expect(sw1.updateBroadcastCache(new Long(pktHashCode), this.broadcastPacketIn.getInPort())).andReturn(false).anyTimes();

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();

        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)1, true)).andReturn(false).atLeastOnce();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, topology, decision);
        forwarding.receive(sw1, this.broadcastPacketIn, cntx);
        verify(sw1, decision);
        assertFalse(wc1.hasCaptured());
    }

    // We get a broadcast packet on switch port (2,1) from host h1.
    // Switchport (2,1) is a broadcast domain port.
    // h1 on switch-port (1,1) - which is a non-broadcast domain port.
    @Test
    public void testBroadcastDropOnInconsistentPort() throws Exception {
        // Mock decision
        // Mock decision
        decision = createMock(IRoutingDecision.class);
        expect(decision.getSourceDevice()).andReturn(srcDevice).atLeastOnce();
        expect(decision.getSourcePort()).andReturn(new SwitchPort(2L, (short)1)).atLeastOnce();
        dstDevices = new ArrayList<IDevice>();
        expect(decision.getDestinationDevices()).andReturn(dstDevices).atLeastOnce();
        IRoutingDecision.rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION, decision);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)this.testBroadcastPacket);
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).atLeastOnce();
        expect(sw2.updateBroadcastCache(anyLong(),
                                       eq(this.broadcastPacketIn.getInPort())))
                .andReturn(false).anyTimes();


        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(2L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.isConsistent(1L, (short)1, 2L, (short)1, true)).andReturn(false).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw2, topology, decision);
        forwarding.receive(sw2, this.broadcastPacketIn, cntx);
        verify(sw2, decision);
        assertFalse(wc2.hasCaptured());
    }

    @Test
    public void testMulticastForwardWithInterfaces() throws Exception {
        setupMulticastTest(null);

        ArrayList<IDevice> dstDevices = new ArrayList<IDevice>();

        dstDevices.add(device1);
        dstDevices.add(device2);
        dstDevices.add(device3);

        List<SwitchPort> multicastIfaces = new ArrayList<SwitchPort>();

        // TEST 1:  Send a packet from device 3; (switch 2, port #3)
        // Verifies the multi-action packet output.
        // There are no attachments on switch 3, hence switch 3 should not receive any packetouts.

        IRoutingDecision mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device3).anyTimes();
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(2L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(multicastIfaces).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);

        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testMulticastPacket);

        reset(topology);
        expect(topology.isIncomingBroadcastAllowed(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(true).anyTimes();
        expect(topology.getOutgoingSwitchPort(2L, (short)3, 3L, (short)5, true)).andReturn(new NodePortTuple(3L, (short)7)).anyTimes();
        expect(topology.getOutgoingSwitchPort(2L, (short)3, 3L, (short)6, true)).andReturn(new NodePortTuple(3L, (short)8)).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)4)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)3)).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(EasyMock.anyLong(), EasyMock.anyLong())).andReturn(true).anyTimes();
        expect(topology.getOpenflowDomainId(EasyMock.anyLong(), EasyMock.anyBoolean())).andReturn(1L).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 1L, (short)3, true)).andReturn(new NodePortTuple(1L, (short)3)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 1L, (short)4, true)).andReturn(new NodePortTuple(1L, (short)4)).anyTimes();
        expect(topology.getAllowedOutgoingBroadcastPort(2L, (short)3, 2L, (short)3, true)).andReturn(new NodePortTuple(2L, (short)3)).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 1L, (short)3, true)).andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 2L, (short)3, true)).andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(2L, (short)3, 1L, (short)4, true)).andReturn(false).anyTimes();
        replay(sw1, sw2, sw3, mydecision, topology);
        forwarding.processPacketInMessage(sw2, this.multicastPacketIn, mydecision, cntx);
        assertTrue(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertFalse(wc3.hasCaptured());

        assertPacketOut(wc1.getValue(), this.testMulticastPacketSerialized,
                        new Short[] { 3, 4 });

        ///////////////////////////////////////////////////////////////////////
        // TEST 2. Send a different packet from device 3.
        // Now, add switch 3, port 5 and 6.
        // to the multicast interfaces
        // which will be returned by the routing decision.
        // In addition, the outgoing switch port for switch 3, port 5 and 6
        // is set to ports 7 and 8
        // Verify that switch3 receives a packet out.
        ///////////////////////////////////////////////////////////////////////
        wc1.reset(); wc2.reset(); wc3.reset();

        multicastIfaces.add(new SwitchPort(3L, 5));
        multicastIfaces.add(new SwitchPort(3L, 6));

        mydecision = createMock(IRoutingDecision.class);
        expect(mydecision.getSourceDevice()).andReturn(device3).anyTimes(); // for tunnel check
        expect(mydecision.getSourcePort()).andReturn(new SwitchPort(2L, (short)3)).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.MULTICAST).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getMulticastInterfaces()).andReturn(multicastIfaces).anyTimes();
        IRoutingDecision.rtStore.put(cntx, "decision", mydecision);
        IControllerService.bcStore.put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, (Ethernet)testSecondMulticastPacket);

        replay (mydecision);
        forwarding.processPacketInMessage(sw1, this.secondMulticastPacketIn, mydecision, cntx);
        assertTrue(wc1.hasCaptured());
        assertFalse(wc2.hasCaptured());
        assertTrue(wc3.hasCaptured());

        assertPacketOut(wc1.getValue(), this.testSecondMulticastPacketSerialized,
                        new Short[] { 3, 4 });
        assertPacketOut(wc3.getValue(), this.testSecondMulticastPacketSerialized,
                        new Short[] { 7, 8 });
    }

    @Test
    public void testBroadcastWithRewrites() throws Exception {
        Short vlan = 42;

        SwitchPort spt = new SwitchPort(1L, (short)3);

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();

        deviceManager.startUp(null);

        // setup 2 devices based on one sw1
        String addressSpaceName = "MyAddressSpace";
        BetterEntityClass addressSpace = new BetterEntityClass(addressSpaceName, vlan);
        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
                .andReturn(true).anyTimes();
        topology.addListener(deviceManager);
        expectLastCall().once();
        IEntityClassifierService ecs =
                createMock(IEntityClassifierService.class);
        expect(ecs.classifyEntity(anyObject(Entity.class)))
                .andReturn(addressSpace).anyTimes();
        expect(ecs.getKeyFields())
                .andReturn(EnumSet.of(DeviceField.VLAN, DeviceField.MAC,
                                      DeviceField.SWITCH, DeviceField.PORT))
                .anyTimes();
        ecs.addListener(deviceManager);
        expectLastCall().anyTimes();
        replay(ecs, topology);
        deviceManager.setEntityClassifier(ecs);

        device1 =
                deviceManager.learnEntity(HexString.toLong("00:11:22:33:44:66"),
                                          null,
                                          IPv4.toIPv4Address("192.168.10.1"),
                                          1L, 3);
        device2 =
                deviceManager.learnEntity(HexString.toLong("00:11:33:55:77:02"),
                                          null,
                                          IPv4.toIPv4Address("192.168.10.2"),
                                          1L, 4);

        Set<Short> ports = new HashSet<Short>(Arrays.asList(new Short[] {
                                                        (short)6,
                                                        (short)7,
                                                        (short)8,
                                                        (short)9,
                                                        (short)10
                                                        }));
        expect (sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();

        // link ports. these will be excluded from the BC

        HashSet<Short> linkPorts = new HashSet<Short>();
        linkPorts.add((short) 8);
        linkPorts.add((short) 9);
        linkPorts.add((short) 10);

        reset(topology);
        expect(topology.getPorts(1L)).andReturn(ports).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect (topology.getPortsWithLinks(EasyMock.anyLong()))
                .andReturn(linkPorts).anyTimes();
        // broadcast ports. these will be included in the BC
        HashSet<Short> bcPorts = new HashSet<Short>();
        bcPorts.add((short) 10);
        expect (topology.getBroadcastPorts(EasyMock.anyLong(),
                                           EasyMock.anyLong(),
                                           EasyMock.anyShort(),
                                           EasyMock.anyBoolean()))
                .andReturn(bcPorts).anyTimes();
        expect (topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect (topology.isConsistent(1L, (short)3, 1L, (short)3, false)).andReturn(true).anyTimes();
        // Setting up expectations for isAttachmetPointPort.
        // Port 7 is always an attachment point port and we toggle
        // whether ports 6 and 10 are
        class MyAnswer implements IAnswer<Boolean> {
            Boolean value;
            @Override
            public Boolean answer() {
                return this.value;
            }
        }
        MyAnswer myAnswer = new MyAnswer();
        myAnswer.value = true;
        expect(topology.isAttachmentPointPort(anyLong(), not(eq((short)7)), eq(false)))
                .andAnswer(myAnswer).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), eq((short)7), eq(false)))
                .andReturn(true).anyTimes();

        IRoutingDecision mydecision = createMock(IRoutingDecision.class);
        ArrayList<IDevice> dstDevices = new ArrayList<IDevice>();

        expect(mydecision.getSourceDevice()).andReturn(device1).anyTimes();
        expect(mydecision.getSourcePort()).andReturn(spt).anyTimes();
        expect(mydecision.getDestinationDevices()).andReturn(dstDevices).anyTimes();
        expect(mydecision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD).anyTimes();
        Set<Long> switches = new HashSet<Long>();
        switches.add(1L);
        expect(topology.getSwitchesInOpenflowDomain(1L, false)).andReturn(switches).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(1L, (short)3, false)).andReturn(true).anyTimes();
        expect(sw1.updateBroadcastCache(anyLong(), anyShort()))
                .andReturn(false).anyTimes();

        byte[] origPktData = testBroadcastPacketSerialized.clone();
        IControllerService.bcStore.put(cntx,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet)testBroadcastPacket.clone());
        Ethernet contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testBroadcastPacket, contextEth);


        // TEST 1:  Send a packet from device 1; (switch 1, port #3)
        // Verifies the packet output.
        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService
               .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();

        // Tunnel port number is set to 100 in all the switches.
        // However, the tunnel itself is not active -- hence tunnel
        // is not knwon to topology.
        reset(tunnelManager);
        // Mock tunnel service
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong()))
        .andReturn((short)100).anyTimes();

        replay(sw1, mydecision, topology, tunnelManager, rewriteService);
        forwarding.processPacketInMessage(sw1, this.broadcastPacketIn, mydecision, cntx);
        verify(rewriteService, tunnelManager, topology);
        assertArrayEquals(origPktData, testBroadcastPacketSerialized);
        assertTrue(wc1.hasCaptured());

        Ethernet ethWithVlanSet = (Ethernet)testBroadcastPacket.clone();
        ethWithVlanSet.setVlanID(vlan);
        assertPacketOut(wc1.getValue(), ethWithVlanSet.serialize(),
                        new Short[] { 6, 7, 10});
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testBroadcastPacket, contextEth);

        //
        // TEST 2a:  Send a packet from device 1; (switch 1, port #3)
        // All untagged. Do TTL decrement (by 1)
        wc1.reset();
        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(1).atLeastOnce();
        expect(rewriteService
               .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();

        replay(rewriteService);
        forwarding.processPacketInMessage(sw1, this.broadcastPacketIn, mydecision, cntx);
        verify(rewriteService);
        assertTrue(wc1.hasCaptured());

        Ethernet ethWithTtlChanged = (Ethernet) this.testBroadcastPacket.clone();
        IPv4 ip = (IPv4) ethWithTtlChanged.getPayload();
        short newTtl = U8.f(ip.getTtl());
        newTtl -= (short)1;
        ip.setTtl(U8.t(newTtl));
        ip.resetChecksum();

        assertPacketOut(wc1.getValue(), ethWithTtlChanged.serialize(),
                        new Short[] { 6, 7, 10});
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testBroadcastPacket, contextEth);

        //
        // TEST 2b:  Send a packet from device 1; (switch 1, port #3)
        // All untagged. Do TTL decrement (by 255). Packet should be dropped
        wc1.reset();
        resetToDefault(rewriteService);
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(null).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(255).atLeastOnce();
        expect(rewriteService
               .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();

        replay(rewriteService);
        forwarding.processPacketInMessage(sw1, this.broadcastPacketIn, mydecision, cntx);
        verify(rewriteService);

        assertEquals(false, wc1.hasCaptured());
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testBroadcastPacket, contextEth);



        List<OFMessage> ofms;
        //
        // TEST 3:  Send a packet from device 1; (switch 1, port #3)
        // ports 6 is tagged, 7 and 10 are native and thus untagged
        // In addition: rewrite MAC addresses
        resetToDefault(rewriteService);
        wc1.reset();
        SwitchPort swp1x6 = new SwitchPort(1L, 6);

        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(1L).atLeastOnce();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(42L).atLeastOnce();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(null).atLeastOnce();
        SwitchPort otherSwp = not(eq(swp1x6));
        expect(rewriteService
               .getSwitchPortVlanMode(otherSwp,
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(eq(swp1x6),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();
        expect(rewriteService
               .getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                      eq(addressSpaceName),
                                      anyShort(),
                                      anyBoolean()))
               .andReturn(vlan).anyTimes();

        replay(rewriteService);
        forwarding.processPacketInMessage(sw1, this.broadcastPacketIn, mydecision, cntx);
        verify(rewriteService);
        assertArrayEquals(origPktData, testBroadcastPacketSerialized);
        assertTrue(wc1.hasCaptured());

        Ethernet ethRewrittenMac = (Ethernet)testBroadcastPacket.clone();
        assertEquals(ethRewrittenMac, testBroadcastPacket);
        ethRewrittenMac.setDestinationMACAddress(Ethernet.toByteArray(1L));
        ethRewrittenMac.setSourceMACAddress(Ethernet.toByteArray(42L));
        ethWithVlanSet = (Ethernet)ethRewrittenMac.clone();
        ethWithVlanSet.setVlanID(vlan);
        ofms = wc1.getValues();
        assertEquals(2, ofms.size());
        for (OFMessage ofm: ofms) {
            assertEquals(true, ofm instanceof OFPacketOut);
            OFPacketOut ofpo = (OFPacketOut)ofm;
            // This is a hack to get the right capture to the right
            // assertPacketOut call...
            if (ofpo.getActions().size() == 2) {
                assertPacketOut(ofm, ethRewrittenMac.serialize(),
                            new Short[] { 7, 10 });
            } else {
                assertPacketOut(ofm, ethWithVlanSet.serialize(),
                            new Short[] { 6 });
            }
        }
        contextEth = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(testBroadcastPacket, contextEth);
    }

    class EgressPortConfig {
        Short transportVlan;
        Short egressVlan;
        boolean expectedReturn;

        EgressPortConfig(Short tVlan, Short eVlan, boolean expectedReturn) {
            this.transportVlan = tVlan;
            this.egressVlan = eVlan;
            this.expectedReturn = expectedReturn;
        }
    }

    /**
     * Test vlan tagging for packetOut.
     * transportVlan is set.
     * egressVlan is set too.
     * packetOut should be sent to the switch.
     * @throws IOException
     */

    @Test
    public void testPushPacketOutToEgressPort1() throws IOException {
        EgressPortConfig epCfg = new EgressPortConfig((short)1, (short)2, true);
        internalPushPacketOutToEgressPortTest(epCfg);
    }

    /**
     * Test vlan tagging for packetOut
     * transportVlan is set.
     * egressVlan is null.
     * packetOut should be dropped.
     * @throws IOException
     */

    @Test
    public void testPushPacketOutToEgressPort2() throws IOException {
        EgressPortConfig epCfg = new EgressPortConfig((short)1, null, false);
        internalPushPacketOutToEgressPortTest(epCfg);
    }

    /**
     * Test vlan tagging for packetOut
     * transportVlan is null.
     * packetOut should be sent without vlan.
     * @throws IOException
     */

    @Test
    public void testPushPacketOutToEgressPort3() throws IOException {
        EgressPortConfig epCfg = new EgressPortConfig(null, null, true);
        internalPushPacketOutToEgressPortTest(epCfg);
    }

    /**
     * Test vlan tagging for packetOut
     * transportVlan is Untagged.
     * egressVlan is Untagged
     * packetOut should be sent without vlan.
     * @throws IOException
     */

    @Test
    public void testPushPacketOutToEgressPort4() throws IOException {
        EgressPortConfig epCfg = new EgressPortConfig((short)-1, (short)-1, true);
        internalPushPacketOutToEgressPortTest(epCfg);
    }

    protected void internalPushPacketOutToEgressPortTest(
                                                   EgressPortConfig epConfig)
        throws IOException {
        String addressSpace = "foobar";
        Short vlan = -1;

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        resetToDefault(rewriteService);
        if (epConfig.transportVlan != null) {
            expect(rewriteService.getSwitchPortVlanMode(
                                        EasyMock.anyObject(SwitchPort.class),
                                        eq(addressSpace),
                                        eq(vlan),
                                        eq(true)))
                .andReturn(epConfig.egressVlan).atLeastOnce();
        } else {
            expect(rewriteService.getSwitchPortVlanMode(
                                        EasyMock.anyObject(SwitchPort.class),
                                        eq(addressSpace),
                                        eq(vlan),
                                        eq(true)))
                .andReturn(Ethernet.VLAN_UNTAGGED).atLeastOnce();
        }

        replay(addressSpaceMgr, rewriteService, sw1);
        SwitchPort swp = new SwitchPort(1L, (short)1);
        boolean rtCode = forwarding.pushPacketOutToEgressPort(
                                             (Ethernet)this.testPacket,
                                             (short)2,
                                             swp,
                                             false,
                                             addressSpace,
                                             vlan.shortValue(),
                                             cntx,
                                             false);
        verify(addressSpaceMgr, rewriteService, sw1);
        assertEquals(epConfig.expectedReturn, rtCode);

        // If push succeeds, check the packet capture.
        if (epConfig.expectedReturn) {
            assertTrue(wc1.hasCaptured());
            Ethernet ethWithVlanSet = (Ethernet)testPacket.clone();
            // Set egressVlan if it is expected.
            if (epConfig.egressVlan != null && epConfig.egressVlan != Ethernet.VLAN_UNTAGGED) {
                ethWithVlanSet.setVlanID(epConfig.egressVlan);
            }
            assertPacketOut(wc1.getValue(), ethWithVlanSet.serialize(),
                            new Short[] { 1 });
        }
    }

    /**
     * Test that the route is annotated in the explain packet
     * @throws Exception
     */

    @Test
    public void testRouteAnnotationOfExplainPacket() throws Exception {

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);
        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // Set up the context to indicate that it is an explain packet
        NetVirtExplainPacket.ExplainStore.put(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT, NetVirtExplainPacket.VAL_EXPLAIN_PKT);
        NetVirtExplainPacket.ExplainPktRoute epr = new NetVirtExplainPacket.ExplainPktRoute();
        NetVirtExplainPacket.ExplainRouteStore.put(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE, epr);

        // Set up Mock decision
        decision = createMock(IRoutingDecision.class);
        expect(decision.getSourceDevice()).andReturn(srcDevice).atLeastOnce();
        expect(decision.getSourcePort()).andReturn(new SwitchPort(1L, (short)1)).atLeastOnce();
        expect(decision.getDestinationDevices()).andReturn(dstDevices).atLeastOnce();
        IRoutingDecision.rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION, decision);
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();

        // Start the replay
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        replay(sw1, sw2, routingEngine, decision, topology);

        // Call the action function
        forwarding.receive(sw1, this.packetIn, cntx);

        // Verify that all the replays that were setup did happen
        verify(sw1, sw2, routingEngine, decision);

        // Finally get the route from the context and verify it
        assertTrue(epr.numClusters == 1);
        assertTrue(epr.oc.get(0).route.getPath().size() == 4);
        assertTrue(epr.oc.get(0).route.getPath().get(2).getPortId()  == 1);
        assertTrue(epr.oc.get(0).route.getPath().get(1).getPortId() == 2);
        // The 2 in equal (2) below represents dpid 00:00:00:00:00:00:00:02 of sw2
        assertTrue(epr.oc.get(0).route.getPath().get(2).getNodeId() == 2L);
    }


    /**
     * Verify that route is NOT annotated for non-explain packet
     * @throws Exception
     */

    @Test
    public void testNoRouteAnnotationOfNonExplainPacket() throws Exception {

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);
        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, true)).andReturn(route).atLeastOnce();

        // DONT set up the context to indicate that it is an explain packet, but set up its route store
        NetVirtExplainPacket.ExplainPktRoute epr = new NetVirtExplainPacket.ExplainPktRoute();
        epr.numClusters = -1;
        NetVirtExplainPacket.ExplainRouteStore.put(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE, epr);

        // Set up Mock decision
        decision = createMock(IRoutingDecision.class);
        expect(decision.getSourceDevice()).andReturn(srcDevice).atLeastOnce();
        expect(decision.getSourcePort()).andReturn(new SwitchPort(1L, (short)1)).atLeastOnce();
        expect(decision.getDestinationDevices()).andReturn(dstDevices).atLeastOnce();
        IRoutingDecision.rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION, decision);
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(expected_wildcards).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Mock topology
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, true)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();

        // Expected Flow-mods
        OFActionOutput action = new OFActionOutput((short)2, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1)
                .setWildcards(expected_wildcards);
        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
                .setPriority(forwarding.getAccessPriority())
                .setMatch(match.clone())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        fm2.setFlags((short)0); // set flow-mod-removal flag on src switch only
        ((OFActionOutput)(fm2.getActions().get(0))).setPort((short)3);
        ((OFActionOutput)packetOut.getActions().get(0)).setPort((short)2);

        // Record expected packet-outs/flow-mods
        sw2.write(fm2, cntx);
        sw1.write(fm1, cntx);
        sw1.write(packetOut, cntx);


        // Start the replay
        replay(sw1, sw2, routingEngine, decision, topology);

        // Call the action function
        forwarding.receive(sw1, this.packetIn, cntx);

        // Verify that all the replays that were setup did happen
        verify(sw1, sw2, routingEngine, decision);

        // Verify that route is NOT annotated in non-explain packet
        // Check that the numClusters is still -1
        assertTrue(epr.numClusters == -1);
    }


    @Test
    public void testMacRewriteAction() {
        IPacket[] packets = new IPacket[] { testPacket, testMulticastPacket,
                                            testPacketUnknownDest,
                                            testBroadcastPacket };
        for (IPacket pkt: packets) {
            Ethernet eth = (Ethernet)pkt.clone();
            OFAction action = null;


            Long curMac = Ethernet.toLong(eth.getDestinationMACAddress());
            Long newMac = curMac + 1;
            byte[] newMacArray = Ethernet.toByteArray(newMac);

            // Dest Mac
            assertNull(forwarding.getDstMacRewriteAction(eth.getDestinationMAC().toLong(),
                                                         curMac));
            assertNull(forwarding.getDstMacRewriteAction(eth.getDestinationMAC().toLong(),
                                                         null));
            action = forwarding.getDstMacRewriteAction(eth.getDestinationMAC().toLong(),
                                                       newMac);
            assertEquals(true, action instanceof OFActionDataLayerDestination);
            OFActionDataLayerDestination dstMacAction =
                    (OFActionDataLayerDestination)action;
            assertArrayEquals(newMacArray, dstMacAction.getDataLayerAddress());
            assertEquals(eth, pkt); // make sure the packet is unchanged

            // Source Mac
            curMac = Ethernet.toLong(eth.getSourceMACAddress());
            newMac = curMac + 1;
            newMacArray = Ethernet.toByteArray(newMac);
            assertNull(forwarding.getSrcMacRewriteAction(eth.getSourceMAC().toLong(),
                                                         curMac));
            assertNull(forwarding.getSrcMacRewriteAction(eth.getSourceMAC().toLong(),
                                                         null));
            action = forwarding.getSrcMacRewriteAction(eth.getSourceMAC().toLong(),
                                                       newMac);
            assertEquals(true, action instanceof OFActionDataLayerSource);
            OFActionDataLayerSource srcMacAction =
                    (OFActionDataLayerSource)action;
            assertArrayEquals(newMacArray, srcMacAction.getDataLayerAddress());
            assertEquals(eth, pkt); // make sure the packet is unchanged
        }
    }

    @Test
    public void testDecrementTtl() {
        IPacket[] packets = new IPacket[] { testPacket, testMulticastPacket,
                                            testPacketUnknownDest,
                                            testBroadcastPacket };
        for (IPacket pkt: packets) {
            Ethernet eth = (Ethernet)pkt.clone();
            boolean notExpired;

            IPv4 ip = (IPv4)eth.getPayload();
            ip.setChecksum((short)0x1234);
            // Make sure the original TTL is as we expect. Just to calibrate
            assertEquals("Default TTL for IP packets is different than " +
                    "expected. Please adjust test", (byte)0x80, ip.getTtl());

            notExpired = forwarding.decrementTtl(eth, 1);
            assertEquals(true, notExpired);
            ip = (IPv4)eth.getPayload();
            assertEquals((byte)0x7F, ip.getTtl());
            assertEquals(0, ip.getChecksum());

            notExpired = forwarding.decrementTtl(eth, 3);
            assertEquals(true, notExpired);
            ip = (IPv4)eth.getPayload();
            assertEquals((byte)0x7C, ip.getTtl());
            assertEquals(0, ip.getChecksum());

            notExpired = forwarding.decrementTtl(eth, 124);
            assertEquals(false, notExpired);
            ip = (IPv4)eth.getPayload();
            assertEquals(0, ip.getTtl());
            assertEquals(0, ip.getChecksum());

            // get a fresh clone
            eth = (Ethernet)pkt.clone();
            notExpired = forwarding.decrementTtl(eth, 200);
            assertEquals(false, notExpired);
            ip = (IPv4)eth.getPayload();
            assertEquals(0, ip.getTtl());
            assertEquals(0, ip.getChecksum());

            // get a fresh clone
            eth = (Ethernet)pkt.clone();
            notExpired = forwarding.decrementTtl(eth, 2000);
            assertEquals(false, notExpired);
            ip = (IPv4)eth.getPayload();
            assertEquals(0, ip.getTtl());
            assertEquals(0, ip.getChecksum());
        }

        IPacket nonIpPacket = new Ethernet()
            .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
            .setSourceMACAddress("00:11:22:33:44:66")
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload( new ARP() );
        Ethernet eth = (Ethernet)nonIpPacket;
        boolean notExpired = forwarding.decrementTtl(eth, 3);
        assertEquals(true, notExpired);
        notExpired = forwarding.decrementTtl(eth, 0xFF);
        assertEquals(true, notExpired);
    }

    protected void assertExpectedVlanAction(OFAction action,
                                            Short origVlan, Short newVlan) {
        String msg = origVlan.toString() + "-->" + newVlan.toString();
        if (origVlan.equals(newVlan))
            assertNull(msg, action);
        else if (newVlan.equals(Ethernet.VLAN_UNTAGGED)) {
            assertEquals(msg, true, action instanceof OFActionStripVirtualLan);
        }
        else {
            assertEquals(msg, true,
                         action instanceof OFActionVirtualLanIdentifier);
            OFActionVirtualLanIdentifier vlanAction =
                    (OFActionVirtualLanIdentifier)action;
            assertEquals(newVlan.shortValue(),
                         vlanAction.getVirtualLanIdentifier());
        }
    }

    @Test
    public void testVlanRewriteRule() {
        IPacket[] packets = new IPacket[] { testPacket, testMulticastPacket,
                                            testPacketUnknownDest,
                                            testBroadcastPacket };
        Short[] vlans = new Short[] { Ethernet.VLAN_UNTAGGED, 1, 23, 42 };
        for (IPacket pkt: packets) {
            for (Short vlan: vlans) {
                Ethernet orig = (Ethernet)pkt.clone();
                orig.setVlanID(vlan);
                Ethernet eth = (Ethernet)pkt.clone();
                eth.setVlanID(vlan);
                assertEquals(orig, eth);

                for (Short newVlan: vlans) {
                    OFAction action;

                    action = forwarding.getVlanRewriteAction(eth.getVlanID(), newVlan);
                    assertExpectedVlanAction(action, vlan, newVlan);
                    assertEquals(orig, eth);
                }

                assertNull(forwarding.getVlanRewriteAction(eth.getVlanID(), null));
            }
        }
    }

    /**
     * Tests if the flow mods and the packet-outs for routing through a
     * tunnel port have the correct tunnel destination actions in them.
     * @throws Exception
     */
    @Test
    public void testForwardMultiSwitchPathWithTunnelPorts() throws Exception {

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        int wc = expected_wildcards & ~OFMatch.OFPFW_NW_SRC_MASK
                & ~OFMatch.OFPFW_NW_DST_MASK;

        OFFlowMod fm2 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm2.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(wc))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);


        // Expected Flow-mods
        match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        actions = new ArrayList<OFAction>();

        // Add tunnel action first
        OFActionTunnelDstIP tunnelDstAction =
                new OFActionTunnelDstIP(100);
        actions.add(tunnelDstAction);
        int tunnelActionLength = tunnelDstAction.getLengthU();

        // Then add output action
        action = new OFActionOutput((short)3, (short)0xffff);
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(wc))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH+tunnelActionLength);
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only


        // Reset the packetout.
        packetOut =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(this.packetIn.getBufferId())
        .setInPort(this.packetIn.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(tunnelDstAction);
        poactions.add(new OFActionOutput((short)3, (short) 0xffff));
        packetOut.setActions(poactions)
        .setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH + tunnelActionLength))
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);


        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, false)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, false)).andReturn(route).anyTimes();

        // Mock tunnel service
        reset(tunnelManager);
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(1L)).andReturn(new Short((short)3)).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(2L)).andReturn(new Short((short)1)).anyTimes();
        expect(tunnelManager.getTunnelIPAddr(2L)).andReturn(new Integer(100)).anyTimes();


        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision, topology, tunnelManager);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m: msglist) {
            if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
            else if (m instanceof OFFlowMod) {
                assertEquals(m, fm1);
            }
        }

        OFMessage m = wc2.getValue();
        assertTrue(m.equals(fm2));
    }

    /**
     * This test simulates traffic that's already tunneled.  The tunneled
     * traffic in its first and last hop will be written with srcIP, dstIP,
     * and eth-type fields, without wildcarding them.
     * @throws Exception
     */
    @Test
    public void testForwardMultiSwitchPathForTunnelTraffic() throws Exception {

        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        // Since the packets are the first and last hops of the switch
        // the wildcard will have the network source and destination masks.
        int wc = expected_wildcards & ~OFMatch.OFPFW_NW_SRC_MASK
                & ~OFMatch.OFPFW_NW_DST_MASK;

        OFFlowMod fm2 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm2.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(wc))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);


        // Expected Flow-mods
        match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        actions = new ArrayList<OFAction>();

        // Then add output action
        action = new OFActionOutput((short)3, (short)0xffff);
        actions.add(action);


        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(wc))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only

        // Reset the packetout.
        packetOut =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(this.packetIn.getBufferId())
        .setInPort(this.packetIn.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput((short)3, (short) 0xffff));
        packetOut.setActions(poactions)
        .setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH))
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);


        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, false)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, false)).andReturn(route).anyTimes();

        // Mock tunnel service
        reset(tunnelManager);
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(1L)).andReturn(null).once();
        expect(tunnelManager.getTunnelLoopbackPort(2L)).andReturn(null).once();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(1L)).andReturn(new Short((short)100)).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(2L)).andReturn(new Short((short)100)).anyTimes();

        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision, topology, tunnelManager);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m: msglist) {
            if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
            else if (m instanceof OFFlowMod) {
                assertEquals(m, fm1);
            }
        }

        OFMessage m = wc2.getValue();
        assertTrue(m.equals(fm2));
    }


    /**
     * Test set-up that is common to testTunnelTraffic* unit tests.
     * @throws Exception
     */
    private void testDetectTunnelTrafficCommon() throws Exception {
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as sw2 and Mock route
        dstDevices.add(dstDevice1);

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        OFFlowMod fm2 = fm1.clone();
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, false)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 2, (short)3, false)).andReturn(new NodePortTuple(2, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie = forwarding.getHashByMac(dstDevice1.getMACAddress());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie, false)).andReturn(route).anyTimes();

        // Mock tunnel service
        reset(tunnelManager);
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(1L)).andReturn(new Short((short)100)).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(2L)).andReturn(new Short((short)100)).anyTimes();

        reset(bettertopology);
    }

    /**
     * This test is to ensure that the tunneled traffic (from one tun-loopback
     * port to another is identified correctly when the source of the traffic
     * is from the tunnel port.
     */
    @Test
    public void testDetectTunnelTrafficSource() throws Exception {

        testDetectTunnelTrafficCommon();

        expect(tunnelManager.getTunnelLoopbackPort(1L)).andReturn(new Short((short)1)).once();
        expect(tunnelManager.getTunnelLoopbackPort(2L)).andReturn(null).once();
        expect(tunnelManager.getSwitchDpid(new Integer(-1062731518))).andReturn(new Long(2L)).anyTimes();


        bettertopology.detectTunnelSource(1L, 2L);
        expectLastCall().once();

        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager, bettertopology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision, topology, tunnelManager, bettertopology);
    }

    /**
     * This test is to ensure that the tunneled traffic (from one tun-loopback
     * port to another is identified correctly when the destination of the traffic
     * is from the tunnel port.
     */
    @Test
    public void testDetectTunnelTrafficDestination() throws Exception {

        testDetectTunnelTrafficCommon();

        expect(tunnelManager.getTunnelLoopbackPort(1L)).andReturn(null).once();
        expect(tunnelManager.getTunnelLoopbackPort(2L)).andReturn(new Short((short)3)).once();
        expect(tunnelManager.getSwitchDpid(new Integer(-1062731519))).andReturn(new Long(1L)).anyTimes();

        bettertopology.detectTunnelDestination(1L, 2L);
        expectLastCall().once();

        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager, bettertopology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision, topology, tunnelManager, bettertopology);
    }

    /**
     * This test is to ensure that the tunneled traffic (from one tun-loopback
     * port to another is identified correctly when the source and destination
     * of the traffic is from the tunnel port.  In this case, the entire
     * tunnel path is through the L2 domain, thus we don't have to add it to
     * the detection queue as we establish both the flow-mods.
     */
    @Test
    public void testDetectTunnelTrafficSourceAndDestination() throws Exception {

        testDetectTunnelTrafficCommon();

        expect(tunnelManager.getTunnelLoopbackPort(1L)).andReturn(new Short((short)1)).once();
        expect(tunnelManager.getTunnelLoopbackPort(2L)).andReturn(new Short((short)3)).once();

        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager, bettertopology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine, decision, topology, tunnelManager, bettertopology);
    }


    private void
    setupNOFViaTunnelToOFSwTest(short packetInPort, short packetOutPort,
                                boolean flowModProg, boolean reconcileTest) {
        /*
         * Setup devices corresponding to H1, H2, L3 NOF TOR, TEP1, TEP2
         */
        device1 = deviceManager.learnEntity(HexString.toLong("00:00:00:00:00:01"),
                                            null,
                                            IPv4.toIPv4Address("10.0.0.1"),
                                            1L, 3);
        device2 = deviceManager.learnEntity(HexString.toLong("00:00:00:00:00:02"),
                                            null,
                                            IPv4.toIPv4Address("10.0.0.2"),
                                            2L, 3);
        device3 = deviceManager.learnEntity(HexString.toLong("00:00:00:00:00:03"),
                                            null,
                                            IPv4.toIPv4Address("192.168.0.3"),
                                            1L, Integer.valueOf(packetInPort));
        device4 = deviceManager.learnEntity(HexString.toLong("00:00:00:00:00:04"),
                                            null,
                                            IPv4.toIPv4Address("192.168.0.4"),
                                            1L, 4);
        device5 = deviceManager.learnEntity(HexString.toLong("00:00:00:00:00:05"),
                                            null,
                                            IPv4.toIPv4Address("192.168.0.5"),
                                            2L, 4);
        /*
         * Setup listener context - DST_DEVICE, ORIG_DST_DEVICE setup by
         * individual test case. SRC_IFACES, DST_IFACES no-op.
         */
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_SRC_DEVICE,
                                   device3);

        /*
         * Setup decision mock
         */
        reset(decision);
        if (!reconcileTest) {
            expect(decision.getSourceDevice())
                           .andReturn(device3)
                           .atLeastOnce();
            expect(decision.getSourcePort())
                            .andReturn(new SwitchPort(1L, packetInPort))
                            .atLeastOnce();
            expect(decision.getRoutingAction())
                           .andReturn(IRoutingDecision.RoutingAction.FORWARD)
                           .atLeastOnce();
        }
        dstDevices = new ArrayList<IDevice>();
        expect(decision.getDestinationDevices())
                       .andReturn(dstDevices)
                       .atLeastOnce();
        if (flowModProg) {
            if (!reconcileTest) {
                expect(decision.getWildcards())
                               .andReturn(null)
                               .atLeastOnce();
            }
            expect(decision.getHardTimeout())
                           .andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT)
                           .atLeastOnce();
        }
        IRoutingDecision.rtStore.put(cntx, IRoutingDecision.CONTEXT_DECISION,
                                     decision);

        /*
         * Build test packet data, packet-in, packet-out
         */
        testPacket = new Ethernet()
                        .setDestinationMACAddress("00:00:00:00:00:04")
                        .setSourceMACAddress("00:00:00:00:00:03")
                        .setEtherType(Ethernet.TYPE_IPv4)
                        .setPayload(
                         new IPv4()
                             .setTtl((byte) 128)
                             .setSourceAddress("192.168.0.3")
                             .setDestinationAddress("10.0.0.1")
                             .setPayload(new UDP()
                                 .setSourcePort((short) 5000)
                                 .setDestinationPort((short) 5001)
                                 .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized = testPacket.serialize();

        packetIn = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort(packetInPort)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        packetOut =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(this.packetIn.getBufferId())
        .setInPort(this.packetIn.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput(packetOutPort, (short) 0xffff));
        packetOut.setActions(poactions)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);

        /*
         * Mock tunnel service
         */
        reset(tunnelManager);
        expect(tunnelManager.isTunnelEndpoint(device4))
                            .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(device5))
                            .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                            .andReturn(false).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong()))
                            .andReturn(null).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong()))
                            .andReturn(null).anyTimes();
        expect(tunnelManager.isTunnelSubnet(EasyMock.anyInt()))
                            .andReturn(false).anyTimes();

        /*
         * Mock topology
         */
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(3L).anyTimes();
        expect(topology.getL2DomainId(1L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, false)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, false)).andReturn(3L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)5, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)5))
                        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)4, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)4))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2, true))
                       .andReturn(false).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)1, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)5, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)4, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)3, true))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)3))
                       .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(2L, (short)2, true))
                       .andReturn(false).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)1, 1, (short)4, true))
                       .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getIncomingSwitchPort(1, (short)5, 1, (short)4, true))
                       .andReturn(new NodePortTuple(1, (short)1)).anyTimes();
        expect(topology.getOutgoingSwitchPort(1, (short)1, 1, (short)3, true))
                       .andReturn(new NodePortTuple(1, (short)3)).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
                       .andReturn(true).anyTimes();
        expect(topology.getAllowedIncomingBroadcastPort(1L, (short)1, true))
                       .andReturn(new NodePortTuple(1L, (short)1)).anyTimes();
        expect(topology.getAllowedIncomingBroadcastPort(1L, (short)5, true))
                       .andReturn(new NodePortTuple(1L, (short)1)).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)5, true))
                       .andReturn(true).anyTimes();
        expect(topology.isBroadcastDomainPort(1L, (short)1, true))
                       .andReturn(true).anyTimes();
    }

    /*
     * Topology:
     * ---------
     *        *--------*
     *        | L3 NOF | ----- H3
     *        *--------*
     *            |
     *      ----------------------
     *     |   |               |   |
     * (*) |   |               |   |
     * +-----------+         +-----------+
     * |  (1) (5)  |         | (5) (1)   |
     * |           |         |           |
     * |           |         |           |
     * |        (2)|o ----- o|(2)        |
     * |           |         |           |
     * |    SW1    |         |    SW2    |
     * |           |         |           |
     * |           |         |           |
     * |           |         |           |
     * |  (4) (3)  |         | (3) (4)   |
     * +-----------+         +-----------+
     *     o  o                 o  o
     *     |  |                 |  |
     *     |  --H1          H2--|  |
     * TEP1                        TEP2
     *
     * Test Context:
     * -------------
     * o H3 wants to talk to H2
     * o H1 and H2 are in the subnet X
     * o L3 NOF is configured with a nexthop of (TEP1, TEP2)[ECMP] to subnet X
     * o Packet from H3 destined to H2 hashes to TEP1 as next hop on L3 NOF
     * o So packet comes in on (SW1, 1)
     * o VirtualRouting(VRS) finds out that packet destined to TEP1's MAC,
     *   but destIP is H2. Let's say VRS looks up it's config and decides to
     *   Forward. It is assumed that VRS would re-program CONTEXT_DST_DEVICE
     *   to H2 and set CONTEXT_ORIG_DST_DEVICE to TEP1.
     * o Now, packet reaches Forwarding.
     *
     * Test Assumptions:
     * ----------------
     * o It is assumed that (SW1, 1) is the designated port in topology to
     *   receive traffic from NOF destined to TEP1
     *
     * Test Expectation:
     * -----------------
     * o Forwarding notices from CONTEXT_ORIG_DST_DEVICE that this packet was
     *   destined to TEP1's MAC, but is actually destined to H2's IP.
     * o Forwarding should make sure that (SW1, 1) is the designated port to
     *   receive traffic for (TEP1 and not H2).
     * o Forwarding should query the SW-SW path from topology for SW1 --> SW2
     *   and prepend (SW1, 1) and append (SW2, 3) to construct the final path.
     *   This would be { (SW1, 1) (SW1, 2) (SW2, 2) (SW2, 3) }
     * o FlowMods should be programed on SW1 and SW2 appropriately.
     *
     */
    @Test
    public void testForwardNOFViaTunnelOrigDestToOFSwRemote() throws Exception {
        /*
         * Common setup for all NOFViaTunnelToOFSw* tests
         */
        setupNOFViaTunnelToOFSwTest((short)1, (short)2, true, false);

        /*
         * Test specific setup
         */
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_DST_DEVICE,
                                   device2);
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_ORIG_DST_DEVICE,
                                   device4);
        dstDevices.add(device2);

        /*
         * Expected Flow-mods
         */
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)2, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod)mockControllerProvider
                                   .getOFMessageFactory()
                                   .getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
           .setPriority(forwarding.getAccessPriority())
           .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        OFFlowMod fm2 = fm1.clone();
        ((OFActionOutput)fm2.getActions().get(0)).setPort((short) 3);
        fm2.getMatch().setInputPort((short)2);
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only

        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        /*
         * Setup mock routes
         */
        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(2L, (short)2));
        long cookie = forwarding.getHashByMac(device2.getMACAddress());
        expect(routingEngine.getRoute(1L, 2L, cookie, true))
                            .andReturn(route)
                            .anyTimes();

        /*
         * Replay mocks
         */
        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);

        /*
         * Finally the test
         */
        forwarding.receive(sw1, this.packetIn, cntx);

        /*
         * Verify
         */
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(topology);
        verify(tunnelManager);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.
        List<OFMessage> msglist = wc1.getValues();
        for (OFMessage m: msglist) {
            if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
            else if (m instanceof OFFlowMod)
                assertEquals(m, fm1);
        }
        OFMessage m = wc2.getValue();
        assertTrue(m.equals(fm2));
    }

    /*
     * Topology:
     * ---------
     *        *--------*
     *        | L3 NOF | ----- H3
     *        *--------*
     *            |
     *      ----------------------
     *     |   |               |   |
     * (*) |   |               |   |
     * +-----------+         +-----------+
     * |  (1) (5)  |         | (5) (1)   |
     * |           |         |           |
     * |           |         |           |
     * |    (2)    |o ----- o|(2)        |
     * |           |         |           |
     * |    SW1    |         |    SW2    |
     * |           |         |           |
     * |           |         |           |
     * |           |         |           |
     * |  (4) (3)  |         | (4) (3)   |
     * +-----------+         +-----------+
     *     o  o                 o  o
     *     |  |                 |  |
     *     |  --H1          H2--|  |
     * TEP1                        TEP2
     *
     * Test Context:
     * -------------
     * o H3 wants to talk to H1
     * o H1 and H2 are in the subnet X
     * o L3 NOF is configured with a nexthop of (TEP1, TEP2)[ECMP] to subnet X
     * o Packet from H3 destined to H1 hashes to TEP1 as next hop on L3 NOF
     * o So packet comes in on (SW1, 1)
     * o VirtualRouting(VRS) finds out that packet destined to TEP1's MAC,
     *   but destIP is H1. Let's say VRS looks up it's config and decides to
     *   Forward. It is assumed that VRS would re-program CONTEXT_DST_DEVICE
     *   to H1 and set CONTEXT_ORIG_DST_DEVICE to TEP1.
     * o Now, packet reaches Forwarding.
     *
     * Test Assumptions:
     * ----------------
     * o It is assumed that (SW1, 1) is the designated port in topology to
     *   receive traffic from NOF destined to TEP1
     *
     * Test Expectation:
     * -----------------
     * o Forwarding notices from CONTEXT_ORIG_DST_DEVICE that this packet was
     *   destined to TEP1's MAC, but is actually destined to H1(CONTEXT_DST_DEVICE).
     * o Forwarding should make sure that (SW1, 1) is the designated port to
     *   receive traffic for (TEP1 and not H1).
     * o Forwarding should simply add (SW1, 1) and (SW1, 3) to construct
     *   the final path. It should not query SW-SW path as destination is
     *   local to SW1.
     * o FlowMods should be programed only on SW1.
     */
    @Test
    public void testForwardNOFViaTunnelOrigDestToOFSwLocal() throws Exception {
        /*
         * Common setup for all NOFViaTunnelToOFSw tests
         */
        setupNOFViaTunnelToOFSwTest((short)1, (short)3, true, false);

        /*
         * Test specific setup
         */
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_DST_DEVICE,
                                   device1);
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_ORIG_DST_DEVICE,
                                   device4);
        dstDevices.add(device1);

        /*
         * Expected Flow-mods
         */
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod)mockControllerProvider
                                   .getOFMessageFactory()
                                   .getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
           .setPriority(forwarding.getAccessPriority())
           .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only

        /*
         * Capture writes of OFMessages(flowmods, packetout) to switches
         */
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        /*
         * Replay mocks
         */
        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);

        /*
         * Finally the test
         */
        forwarding.receive(sw1, this.packetIn, cntx);

        /*
         * Verify
         */
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(topology);
        verify(tunnelManager);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertFalse(wc2.hasCaptured());  // no flowmods on wc2
        List<OFMessage> msglist = wc1.getValues();
        for (OFMessage m: msglist) {
            if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
            else if (m instanceof OFFlowMod)
                assertEquals(m, fm1);
        }
    }

    /*
     * Topology:
     * ---------
     *        *--------*
     *        | L3 NOF | ----- H3
     *        *--------*
     *            |
     *      ----------------------
     *     |   |               |   |
     * (*) |   |               |   | (*)
     * +-----------+         +-----------+
     * |  (1) (5)  |         | (5) (1)   |
     * |           |         |           |
     * |           |         |           |
     * |    (2)    |o ----- o|(2)        |
     * |           |         |           |
     * |    SW1    |         |    SW2    |
     * |           |         |           |
     * |           |         |           |
     * |           |         |           |
     * |  (4) (3)  |         | (4) (3)   |
     * +-----------+         +-----------+
     *     o  o                 o  o
     *     |  |                 |  |
     *     |  --H1          H2--|  |
     * TEP1                        TEP2
     *
     * Test Context:
     * -------------
     * o H3 wants to talk to H1
     * o H1 and H2 are in the subnet X
     * o L3 NOF is configured with a nexthop of (TEP1, TEP2)[ECMP] to subnet X
     * o Packet from H3 destined to H1 hashes to TEP1 as next hop on L3 NOF
     * o However, NOF does not where TEP1's MAC is present. So it floods it
     *   to (SW1, 1), (SW1, 5), (SW2, 5), (SW2, 1)
     * o Consider we are currently processing packet-in from (SW1, 5)
     * o VirtualRouting(VRS) finds out that packet destined to TEP1's MAC,
     *   but destIP is H1. Let's say VRS looks up it's config and decides to
     *   Forward. It is assumed that VRS would re-program CONTEXT_DST_DEVICE
     *   to H1 and set CONTEXT_ORIG_DST_DEVICE to TEP1.
     * o Now, packet reaches Forwarding.
     *
     * Test Assumptions:
     * ----------------
     * o It is assumed that (SW1, 1) is the designated port in topology to
     *   receive traffic from NOF destined to TEP2.
     *
     * Test Expectation:
     * -----------------
     * o Forwarding notices from CONTEXT_ORIG_DST_DEVICE that this packet was
     *   destined to TEP1's MAC, but is actually destined to H1(CONTEXT_DST_DEVICE).
     * o Forwarding should make sure that (SW1, 1) is the designated port to
     *   receive traffic for (TEP1 and not H1).
     * o Forwarding should simply add (SW1, 1) and (SW1, 3) to construct
     *   the final path. It should not query SW-SW path as destination is
     *   local to SW2.
     * o Forwarding figures that the packet-in switch,port (SW1, 5) is not
     *   in the path and hence should not program any flowmods.
     * o Instead it should inject arps originating with TEP1 as target IP in
     *   ARP request:
     *     o Broadcast Port for SW1 : (1)
     *     o Permitted  Unicast Port for TEP1 on SW1: also (1)
     */
    @Test
    public void testForwardNOFViaTunnelOrigDestToOFSwLocalDeny()
                throws Exception {
        /*
         * Common setup for all NOFViaTunnelToOFSw tests
         */
        setupNOFViaTunnelToOFSwTest((short)5, (short)3, false, false);

        /*
         * Test specific setup
         */
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_DST_DEVICE,
                                   device1);
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_ORIG_DST_DEVICE,
                                   device4);
        dstDevices.add(device1);
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        /*
         * Mock the sdnplatform provider service
         */
        IControllerService bcp = createMock(IControllerService.class);
        forwarding.setControllerProvider(bcp);
        IControllerService.bcStore.put(cntx,
                                IControllerService.CONTEXT_PI_PAYLOAD,
                                (Ethernet)testPacket);
        expect(bcp.getOFMessageFactory())
                  .andReturn(mockControllerProvider.getOFMessageFactory())
                  .anyTimes();
        expect(bcp.injectOfMessage(sw1, getFakeArpPi(this.packetIn,
                                   (Ethernet)testPacket,
                                   device4.getIPv4Addresses()[0].intValue(),
                                   Short.valueOf((short)1))))
                                   .andReturn(true);
        expectLastCall().times(2);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        /*
         * Replay mocks
         */
        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager, bcp);

        /*
         * Finally the test
         */
        forwarding.receive(sw1, this.packetIn, cntx);

        /*
         * Verify
         */
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(topology);
        verify(tunnelManager);
        verify(bcp);

        assertFalse(wc1.hasCaptured());  // no packetout + flowmod on wc1.
        assertFalse(wc2.hasCaptured());  // no flowmods on wc2
    }

    @Test
    public void testForwardNOFViaTunnelOrigDestToOFSwLocalReconcile()
                throws Exception {
        /*
         * Common setup for all NOFViaTunnelToOFSw tests
         */
        setupNOFViaTunnelToOFSwTest((short)1, (short)3, true, true);

        /*
         * Test specific setup
         */
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_DST_DEVICE,
                                   device1);
        IDeviceService.fcStore.put(cntx,
                                   IDeviceService.CONTEXT_ORIG_DST_DEVICE,
                                   device4);
        dstDevices.add(device1);

        /*
         * Expected Flow-mods
         */
        OFMatch match = new OFMatch();
        match.loadFromPacket(testPacketSerialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod)mockControllerProvider
                                   .getOFMessageFactory()
                                   .getMessage(OFType.FLOW_MOD);
        fm1.setIdleTimeout((short)5)
           .setPriority(forwarding.getAccessPriority())
           .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setPriority(forwarding.getAccessPriority())
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
        fm1.setFlags((short)1); // set flow-mod-removal flag on src switch only
        fm1.getMatch().setWildcards(match.getWildcards());
        fm1.setCommand(OFFlowMod.OFPFC_MODIFY);

        /*
         * Capture writes of OFMessages(flowmods, packetout) to switches
         */
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().once();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();

        /*
         * Replay mocks
         */
        replay(sw1, sw2, routingEngine, decision, topology, tunnelManager);

        /*
         * Prepare the reconcile flow mods
         */
        ArrayList<OFMatchReconcile> ofmRcList = new ArrayList<OFMatchReconcile>();
        OFMatchReconcile ofmRc = new OFMatchReconcile();
        ofmRc.ofmWithSwDpid.setOfMatch(match.clone());
        ofmRc.ofmWithSwDpid.setSwitchDataPathId(1L);
        ofmRc.rcAction = ReconcileAction.UPDATE_PATH;
        ofmRc.cntx = cntx;
        ofmRcList.add(ofmRc);

        /*
         * Finally, the test
         */
        forwarding.reconcileFlows(ofmRcList);

        /*
         * Verify
         */
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(topology);
        verify(tunnelManager);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertFalse(wc2.hasCaptured());  // no flowmods on wc2
        List<OFMessage> msglist = wc1.getValues();
        for (OFMessage m: msglist) {
            if (m instanceof OFPacketOut)
                assertEquals(m, packetOut);
            else if (m instanceof OFFlowMod)
                assertEquals(m, fm1);
        }
    }
}
