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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import static org.easymock.EasyMock.*;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.internal.BetterDeviceManagerImpl;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.ARPMode;
import org.sdnplatform.netvirt.core.VNS.BroadcastMode;
import org.sdnplatform.netvirt.manager.INetVirtListener;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.ArpManager;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.ARP;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;



public class ArpManagerTest extends PlatformTestCase {
    private INetVirtManagerService netVirtManager;
    private MockDeviceManager mockDeviceManager;
    private MemoryStorageSource storageSource;
    private VirtualRouting virtualRouting;
    ITopologyService topology;
    ITunnelManagerService tunnelManager;
    private ArpManager arpManager;
    private BetterDeviceManagerImpl tagManager;
    private ModuleContext fmc;
    private IFlowReconcileService flowReconcileMgr;
    private IFlowCacheService betterFlowCacheMgr;

    protected static OFPacketIn packetInARPRequest;
    protected static IPacket arprequestPacket;
    protected static byte[] arpRequestSerialized;
    protected static OFPacketIn packetInARPRequestUnicast;
    protected static IPacket arprequestPacketUnicast;
    protected static byte[] arpRequestUnicastSerialized;
    protected static OFPacketIn packetInGratARP;
    protected static IPacket gratARPPacket;
    protected static byte[] gratARPSerialized;

    static {
        ARP request = new ARP()
        .setHardwareType(ARP.HW_TYPE_ETHERNET)
        .setProtocolType(ARP.PROTO_TYPE_IP)
        .setHardwareAddressLength((byte) 6)
        .setProtocolAddressLength((byte) 4)
        .setOpCode(ARP.OP_REQUEST)
        .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
        .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
        .setTargetHardwareAddress(Ethernet.toMACAddress("FF:FF:FF:FF:FF:FF"))
        .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2"));

        arprequestPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(request);
        arpRequestSerialized = arprequestPacket.serialize();

        arprequestPacketUnicast = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(request);
        arpRequestUnicastSerialized = arprequestPacketUnicast.serialize();

        packetInARPRequest =
            ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(arpRequestSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) arpRequestSerialized.length);
        packetInARPRequestUnicast =
            ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(arpRequestSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) arpRequestSerialized.length);

        ARP gratARP = new ARP()
        .setHardwareType(ARP.HW_TYPE_ETHERNET)
        .setProtocolType(ARP.PROTO_TYPE_IP)
        .setHardwareAddressLength((byte) 6)
        .setProtocolAddressLength((byte) 4)
        .setOpCode(ARP.OP_REQUEST)
        .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
        .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
        .setTargetHardwareAddress(Ethernet.toMACAddress("FF:FF:FF:FF:FF:FF"))
        .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"));

        gratARPPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(gratARP);
        gratARPSerialized = gratARPPacket.serialize();

        packetInGratARP =
            ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(gratARPSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) gratARPSerialized.length);
    }

    protected static OFPacketIn packetInARPReply;
    protected static IPacket arpreplyPacket;
    protected static byte[] arpReplySerialized;
    static {
        arpreplyPacket = new Ethernet()
        // Note: Ethernet src mac address is specifically set to be different
        // from the sender hardware address.  This is to test ARP responses
        // in case of VRRP scenarios.
        .setSourceMACAddress("00:AA:BB:CC:DD:EE")
        .setDestinationMACAddress("00:44:33:22:11:00")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1")));
        arpReplySerialized = arpreplyPacket.serialize();

        packetInARPReply =
            ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(arpReplySerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) arpReplySerialized.length);
    }

    protected VNS defaultNetVirt;
    protected VNS newNetVirt;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        virtualRouting = new VirtualRouting();
        tagManager = new BetterDeviceManagerImpl();
        netVirtManager = createNiceMock(INetVirtManagerService.class);
        flowReconcileMgr = createNiceMock(IFlowReconcileService.class);
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        RestApiServer ras = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        topology = createMock(ITopologyService.class);
        tunnelManager = EasyMock.createMock(ITunnelManagerService.class);
        betterFlowCacheMgr = createNiceMock(IFlowCacheService.class);
        topology.addListener(mockDeviceManager);
        expectLastCall().times(1);
        topology.addListener(tagManager);
        expectLastCall().times(1);
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        replay(topology);

        fmc = new ModuleContext();
        fmc.addService(IControllerService.class,
                       mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(ITagManagerService.class, tagManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(ITunnelManagerService.class, tunnelManager);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        tagManager.init(fmc);
        virtualRouting.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);

        netVirtManager.addNetVirtListener((INetVirtListener)EasyMock.anyObject());
        expectLastCall().anyTimes();

        replay(netVirtManager);
        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        tagManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        arpManager = virtualRouting.getArpManager();

        defaultNetVirt = new VNS("default");
        newNetVirt = new VNS("new");
    }

    /**
     * Assert that the OFMessage is a unicast ARP message with
     * with the correct source and destination details.
     * @param outmessage
     */
    private void assertUnicastArp(OFMessage outmessage) {
        assertNotNull(outmessage);
        assertTrue(outmessage instanceof OFPacketIn);
        OFPacketIn po = (OFPacketIn)outmessage;

        Ethernet eth = new Ethernet();
        eth.deserialize(po.getPacketData(), 0, po.getPacketData().length);
        assertEquals("00:11:22:33:44:55",
                HexString.toHexString(eth.getDestinationMACAddress()));
        assertEquals("00:44:33:22:11:00",
                HexString.toHexString(eth.getSourceMACAddress()));
        assertTrue(eth.getPayload() instanceof ARP);
        ARP arp = (ARP) eth.getPayload();
        assertEquals(ARP.PROTO_TYPE_IP, arp.getProtocolType());
        assertEquals(ARP.OP_REQUEST, arp.getOpCode());
        assertEquals("00:44:33:22:11:00",
                HexString.toHexString(arp.getSenderHardwareAddress()));
        assertEquals("ff:ff:ff:ff:ff:ff",
                HexString.toHexString(arp.getTargetHardwareAddress()));
    }

    /**
     * Tests ARP manager's setting of flood if it does not know
     * the Device location of where the ARP request should go.
     */
    @Test
    public void testARPFloodIfNotFound() throws Exception {
        ArpManager am = getArpManager();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));
        defaultNetVirt.setArpManagerMode(ARPMode.FLOOD_IF_UNKNOWN);

        VirtualRouting vr = getVirtualRouting();
        am.ARP_CACHE_DEFAULT_TIMEOUT_MS = 50;

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        Capture<OFMessage> writeCapture =
                new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);

        IControllerService bcp =
                createMock(IControllerService.class);
        MockControllerProvider bp = getMockControllerProvider();
        am.setControllerProvider(bcp);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).
                                   andReturn(true).anyTimes();
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        am.setControllerProvider(bcp);
        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();

        // Learn the source device.
        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice src = mockDeviceManager.learnEntity(mac, null, ip, 1L, 1);

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(src)).andReturn(srcIfaces).anyTimes();
        replay(netVirtManager, mockSwitch, bcp);

        ListenerContext bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);

        // The packet is sent along the regular forwarding logic for flooding,
        // thus, nothing gets written to the switch.
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        verify(netVirtManager, mockSwitch, bcp);

        // Make sure the switch hasn't seen any new packets
        assertFalse(writeCapture.hasCaptured());

        // We should be flooding the ARP request here because we do not yet
        // know where the target Device is
        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD,
                     result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

        //////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////
        // Now, we learn the destination device.

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(src)).andReturn(srcIfaces).anyTimes();
        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice dst = mockDeviceManager.learnEntity(mac, null, ip, 1L, 2);
        expect(netVirtManager.getInterfaces(dst)).andReturn(srcIfaces).anyTimes();

        // Here the packet should be reinjected as a unicast ARP message.
        // as we have learned the destination.
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        expect(tunnelManager.isTunnelEndpoint((IDevice)EasyMock.anyObject())).andReturn(false).anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(1L, 1L, true)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1L, (short)1, 1L, (short)2, true)).andReturn(new NodePortTuple(1, 1)).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(false).anyTimes();

        replay(netVirtManager, topology, tunnelManager);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

        verify(netVirtManager, topology, mockSwitch, bcp);
        assertTrue(writeCapture.hasCaptured());

        // Assert that the re-injected packet is a unicast packet.
        OFMessage outmessage = writeCapture.getValue();
        assertUnicastArp(outmessage);

        //////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////
        // Now, dispatch the outmessage we got from the previous step, to
        // ensure that the unicast ARP is handled correctly.
        // We should see FORWARD_OR_FLOOD as the action, and no new packets
        // injected on the switch.

        writeCapture.reset();

        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, outmessage, bc);
        verify(netVirtManager, mockSwitch, bcp);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD,
                     result.getRoutingAction());
        assertFalse(writeCapture.hasCaptured());

        //////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////
        // If we send a broadcast ARP request, it should be converted to unicast
        // ARP, as we are still within the ARP cache timeout threshold.

        writeCapture.reset();
        Thread.sleep(1+am.ARP_CACHE_DEFAULT_TIMEOUT_MS/2);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);

        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertTrue(writeCapture.hasCaptured());
        outmessage = writeCapture.getValue();
        assertUnicastArp(outmessage);

        //////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////
        // Once the timeout has elapsed, the packet should be flooded. Thus,
        // no unicast conversion should take place.

        writeCapture.reset();
        Thread.sleep(1+am.ARP_CACHE_DEFAULT_TIMEOUT_MS/2);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD,
                     result.getRoutingAction());
        assertFalse(writeCapture.hasCaptured());

        //////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////
        // Now, we send an ARP response so that the arp cache is cleared.
        // Note that the eth src mac and the sender hardware address are
        // different, thus we are testing if the ARP is handled correctly
        // in the VRRP scenarios.
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPReply, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPReply, bc);


        //////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////
        // The destination device should now be reset, and we should start
        // converting broadcast ARP to unicast ARP again.
        writeCapture.reset();
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);

        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertTrue(writeCapture.hasCaptured());
        outmessage = writeCapture.getValue();
        assertUnicastArp(outmessage);
    }

    /**
     * This test verifies that when config is set to DROP_IF_UNKNOWN,
     * the behavior is correct.
     * @throws Exception
     */
    @Test
    public void testARPDropIfUnknown() throws Exception {
        ArpManager am = getArpManager();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));
        defaultNetVirt.setArpManagerMode(ARPMode.DROP_IF_UNKNOWN);

        VirtualRouting vr = getVirtualRouting();
        am.ARP_CACHE_DEFAULT_TIMEOUT_MS = 50;

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        Capture<OFMessage> writeCapture =
                new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);

        IControllerService bcp =
                createMock(IControllerService.class);
        MockControllerProvider bp = getMockControllerProvider();
        am.setControllerProvider(bcp);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).
                                   andReturn(true).anyTimes();
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        am.setControllerProvider(bcp);
        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice src = mockDeviceManager.learnEntity(mac, null, ip, 1L, 1);

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(src)).andReturn(srcIfaces).anyTimes();

        replay(netVirtManager, mockSwitch, bcp);
        ListenerContext bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);

        verify(netVirtManager, mockSwitch, bcp);
        assertFalse(writeCapture.hasCaptured());

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(src)).andReturn(srcIfaces).anyTimes();
        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice dst = mockDeviceManager.learnEntity(mac, null, ip, 1L, 2);
        expect(netVirtManager.getInterfaces(dst)).andReturn(srcIfaces).anyTimes();

        // We should be flooding the ARP request here because we do not yet
        // know where the target Device is
        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

        // Here the packet should be reinjected
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        expect(tunnelManager.isTunnelEndpoint((IDevice)EasyMock.anyObject())).andReturn(false).anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        expect(topology.inSameL2Domain(1L, 1L, true)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(1L, (short)1, 1L, (short)2, true)).andReturn(new NodePortTuple(1, 1)).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(false).anyTimes();

        replay(netVirtManager, topology, tunnelManager);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

        verify(netVirtManager, topology, mockSwitch, bcp);
        assertTrue(writeCapture.hasCaptured());

        OFMessage outmessage = writeCapture.getValue();
        assertNotNull(outmessage);
        if (outmessage != null) {
            assertTrue(outmessage instanceof OFPacketIn);
            OFPacketIn po = (OFPacketIn)outmessage;

            Ethernet eth = new Ethernet();
            eth.deserialize(po.getPacketData(), 0, po.getPacketData().length);
            assertEquals("00:11:22:33:44:55",
                    HexString.toHexString(eth.getDestinationMACAddress()));
            assertEquals("00:44:33:22:11:00",
                    HexString.toHexString(eth.getSourceMACAddress()));
            assertTrue(eth.getPayload() instanceof ARP);
            ARP arp = (ARP) eth.getPayload();
            assertEquals(ARP.PROTO_TYPE_IP, arp.getProtocolType());
            assertEquals(ARP.OP_REQUEST, arp.getOpCode());
            assertEquals("00:44:33:22:11:00",
                    HexString.toHexString(arp.getSenderHardwareAddress()));
            assertEquals("ff:ff:ff:ff:ff:ff",
                    HexString.toHexString(arp.getTargetHardwareAddress()));
        }

        writeCapture.reset();

        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, outmessage, bc);
        verify(netVirtManager, mockSwitch, bcp);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);

        // The action should be strictly FORWARD as flood is not allowed
        // under DROP_IF_UNKNOWN.
        assertEquals(RoutingAction.FORWARD,
                     result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
        assertFalse(writeCapture.hasCaptured());


        // Wait and send the message again.
        Thread.sleep(1+am.ARP_CACHE_DEFAULT_TIMEOUT_MS/2);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

        // Wait and send the message again. should be dropped.
        Thread.sleep(1+am.ARP_CACHE_DEFAULT_TIMEOUT_MS/2);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

       // Wait and send the message again, still should be dropped.
        Thread.sleep(1+am.ARP_CACHE_DEFAULT_TIMEOUT_MS);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
    }

    /**
     * Tests ARP manager's conversion to unicast
     */
    @Test
    public void testARPUnicast() throws Exception {
        ArpManager am = getArpManager();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("defaultDstIface1", defaultNetVirt, null, null));
        defaultNetVirt.setArpManagerMode(ARPMode.FLOOD_IF_UNKNOWN);

        VirtualRouting vr = getVirtualRouting();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(11L).anyTimes();
        Capture<OFMessage> writeCapture =
                new Capture<OFMessage>(CaptureType.ALL);

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);

        IControllerService bcp =
                createMock(IControllerService.class);
        MockControllerProvider bp = getMockControllerProvider();
        am.setControllerProvider(bcp);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).
                                   andReturn(true).times(1);
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        am.setControllerProvider(bcp);

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice src = mockDeviceManager.learnEntity(mac, null, ip, 11L, 1);

        ListenerContext bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);

        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        mockDeviceManager.learnEntity(mac, null, ip, 11L, 2);

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces((IDevice)EasyMock.anyObject()))
        .andAnswer(new IAnswer<List<VNSInterface>>() {
            @Override
            public List<VNSInterface> answer() throws Throwable {
                IDevice d = (IDevice)EasyMock.getCurrentArguments()[0];
                long srcMac = HexString.toLong("00:44:33:22:11:00");
                long dstMac = HexString.toLong("00:11:22:33:44:55");
                if (d.getMACAddress() == srcMac) {
                    List<VNSInterface> srcIfaces =
                            new ArrayList<VNSInterface>();
                    srcIfaces.add(new VNSInterface(
                                   "defaultSrcIface1", defaultNetVirt, null, null));
                    return srcIfaces;
                } else if (d.getMACAddress() == dstMac) {
                    List<VNSInterface> dstIfaces =
                            new ArrayList<VNSInterface>();
                    dstIfaces.add(new VNSInterface(
                                   "defaultDstIface1", defaultNetVirt, null, null));
                    return dstIfaces;
                } else {
                    return null;
                }
            }
        }).times(3);

        expect(tunnelManager.isTunnelEndpoint((IDevice)EasyMock.anyObject())).andReturn(false).anyTimes();
        reset(topology);
        expect(topology.inSameL2Domain(11L, 11L, true)).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(11L, (short)1, 11L, (short)2, true)).andReturn(new NodePortTuple(11, 1)).anyTimes();
        expect(topology.isConsistent(11L, (short)1, 11L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(true).anyTimes();

        replay(netVirtManager, tunnelManager, topology, mockSwitch, bcp);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc,
                                         IRoutingDecision.CONTEXT_DECISION);
        // The original broadcast packet would be dropped.
        assertEquals(RoutingAction.NONE, result.getRoutingAction());

        verify(netVirtManager, mockSwitch, bcp, topology);
        assertTrue(writeCapture.hasCaptured());
        OFPacketIn pi = (OFPacketIn)writeCapture.getValue();
        Ethernet eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        // Make sure the destination MAC is not FF
        assertEquals(mac, eth.getDestinationMAC().toLong());


        // Re do the same test, except this time every switch port is
        // treated as an internal port.  Thus, conversion should always work.
        reset(topology);
        expect(topology.inSameL2Domain(11L, 11L, true)).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(11L, (short)1, 11L, (short)2, true)).andReturn(new NodePortTuple(11, 1)).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort(), EasyMock.anyBoolean())).andReturn(false).anyTimes();
        replay(topology);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        result =
                IRoutingDecision.rtStore.get(bc,
                                             IRoutingDecision.CONTEXT_DECISION);
        // The original broadcast packet would be dropped.
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

        verify(netVirtManager, mockSwitch, bcp, topology);
        assertTrue(writeCapture.hasCaptured());
        pi = (OFPacketIn)writeCapture.getValue();
        eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        // Make sure the destination MAC is not FF
        assertEquals(mac, eth.getDestinationMAC().toLong());

    }

    /**
     * Tests NO ARP unicast conversion for PI from non-AP switch port
     */
    @Test
    public void testARPSkipUnicastNonTrueAP() throws Exception {
        ArpManager am = getArpManager();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));
        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        dstIfaces.add(new VNSInterface("defaultDstIface1", defaultNetVirt, null, null));
        defaultNetVirt.setArpManagerMode(ARPMode.FLOOD_IF_UNKNOWN);

        VirtualRouting vr = getVirtualRouting();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(12L).anyTimes();

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);

        IControllerService bcp =
                createMock(IControllerService.class);
        MockControllerProvider bp = getMockControllerProvider();
        am.setControllerProvider(bcp);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.getSwitches()).andReturn(switches).anyTimes();

        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        am.setControllerProvider(bcp);

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice src = mockDeviceManager.learnEntity(mac, null, ip, 11L, 1);

        ListenerContext bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);

        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        mockDeviceManager.learnEntity(mac, null, ip, 11L, 2);

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces((IDevice)EasyMock.anyObject()))
        .andAnswer(new IAnswer<List<VNSInterface>>() {
            @Override
            public List<VNSInterface> answer() throws Throwable {
                IDevice d = (IDevice)EasyMock.getCurrentArguments()[0];
                long srcMac = HexString.toLong("00:44:33:22:11:00");
                long dstMac = HexString.toLong("00:11:22:33:44:55");
                if (d.getMACAddress() == srcMac) {
                    List<VNSInterface> srcIfaces =
                            new ArrayList<VNSInterface>();
                    srcIfaces.add(new VNSInterface(
                                                   "defaultSrcIface1", defaultNetVirt, null, null));
                    return srcIfaces;
                } else if (d.getMACAddress() == dstMac) {
                    List<VNSInterface> dstIfaces =
                            new ArrayList<VNSInterface>();
                    dstIfaces.add(new VNSInterface(
                                                   "defaultDstIface1", defaultNetVirt, null, null));
                    return dstIfaces;
                } else {
                    return null;
                }
            }
        }).times(2);

        expect(tunnelManager.isTunnelEndpoint((IDevice)EasyMock.anyObject())).andReturn(false).anyTimes();


        // The packet-in switch port is not the one allowed for unicast
        // transmission.  So, ensure the test fails.
        reset(topology);
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.inSameL2Domain(12L, 11L, true)).andReturn(true).anyTimes();
        expect(topology.isConsistent(11L, (short)1, 12L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.isConsistent(12L, (short)1, 11L, (short)2, true)).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(12L, (short)1, true)).andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort(12L, (short)1, 11L, (short)2, true)).andReturn(new NodePortTuple(12, 4)).anyTimes();

        replay(netVirtManager, tunnelManager, topology, mockSwitch, bcp);
        bc = new ListenerContext();
        parseAndAnnotate(bc, packetInARPRequest, src, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        IRoutingDecision result =
                IRoutingDecision.rtStore.get(bc,
                                             IRoutingDecision.CONTEXT_DECISION);


        verify(netVirtManager, mockSwitch, bcp, topology);
        // The original broadcast packet would be dropped.
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());

    }



    @Test
    public void testARPReply() {
        VirtualRouting vr = getVirtualRouting();

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice dst = mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice src = mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));

        IOFSwitch mockSwitch = createStrictMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        mockControllerProvider.setSwitches(switches);

        MockControllerProvider bp = getMockControllerProvider();
        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(mockSwitch);
        ListenerContext bc = new ListenerContext();
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        parseAndAnnotate(bc, packetInARPReply, src, dst);
        bp.dispatchMessage(mockSwitch, packetInARPReply, bc);

        verify(mockSwitch);
        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
    }

    /**
     * Tests ARP manager's setting where it drops the ARP request
     * if the device location of where the ARP should go is not
     * known.
     */
    @Test
    public void testARPDropIfNotFound() throws Exception {
        ArpManager am = getArpManager();

        VirtualRouting vr = getVirtualRouting();

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice d = mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));
        defaultNetVirt.setArpManagerMode(ARPMode.DROP_IF_UNKNOWN);

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        Capture<OFMessage> writeCapture =
                new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);
        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        mockControllerProvider.setSwitches(switches);

        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();

        MockControllerProvider bp = getMockControllerProvider();
        IControllerService bcp =
                createMock(IControllerService.class);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).
                                   andReturn(true).anyTimes();
        am.setControllerProvider(bcp);
        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(mockSwitch, bcp);

        ListenerContext bc =
                parseAndAnnotate(packetInARPRequest, d, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        verify(mockSwitch, bcp);

        assertFalse(writeCapture.hasCaptured());

        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
    }

    /**
     * Test ARP manager's 'disabled' setting.
     * Flood all ARP requests like normal.
     */
    @Test
    public void testARPAlwaysFlood() throws Exception {
        ArpManager am = getArpManager();
        VirtualRouting vr = getVirtualRouting();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice srcDevice = mockDeviceManager.learnEntity(mac, null, ip, null, null);

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(srcDevice)).andReturn(srcIfaces).times(2);

        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice dstDevice = mockDeviceManager.learnEntity(mac, null, ip, null, null);
        expect(netVirtManager.getInterfaces(dstDevice)).andReturn(srcIfaces).times(1);

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        Capture<OFMessage> writeCapture =
                new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);

        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();

        MockControllerProvider bp = getMockControllerProvider();
        IControllerService bcp =
                createMock(IControllerService.class);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).
                                   andReturn(true).anyTimes();
        am.setControllerProvider(bcp);
        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(netVirtManager, mockSwitch,  bcp);

        ListenerContext bc = parseAndAnnotate(packetInARPRequest, srcDevice, dstDevice);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_DST_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        verify(netVirtManager, mockSwitch, bcp);

        assertFalse(writeCapture.hasCaptured());

        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
    }

    /**
     * Test that ARPManager drops ARP requests to devices not in the same
     * NetVirt as the source device.
     */
    @Test
    public void testARPDifferentNetVirt() throws Exception {
        ArpManager am = getArpManager();
        VirtualRouting vr = getVirtualRouting();

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));

        List<VNSInterface> dstIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("newDstIface1", newNetVirt, null, null));

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice srcDevice = mockDeviceManager.learnEntity(mac, null, ip, null, null);

        reset(netVirtManager);
        expect(netVirtManager.getInterfaces(srcDevice)).andReturn(srcIfaces).times(1);

        mac = HexString.toLong("00:11:22:33:44:55");
        ip = IPv4.toIPv4Address("192.168.1.2");
        IDevice dstDevice = mockDeviceManager.learnEntity(mac, null, ip, 1L, 1);
        expect(netVirtManager.getInterfaces(dstDevice)).andReturn(dstIfaces).times(1);

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        Capture<OFMessage> writeCapture =
                new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);

        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();
        expect(mockSwitch.getId()).andReturn(1L).times(1);

        MockControllerProvider bp = getMockControllerProvider();
        IControllerService bcp =
                createMock(IControllerService.class);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).
                                   andReturn(true).anyTimes();
        am.setControllerProvider(bcp);
        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(netVirtManager, mockSwitch,  bcp);

        ListenerContext bc = parseAndAnnotate(packetInARPRequest, srcDevice, dstDevice);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        verify(netVirtManager, mockSwitch, bcp);

        assertFalse(writeCapture.hasCaptured());

        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
    }

    /**
     * Test Gratuitous ARP processing.
     */
    @Test
    public void testGratARP() throws Exception {
        ArpManager am = getArpManager();
        MockDeviceManager dm = getMockDeviceManager();
        reset(topology);
        topology.addListener(dm);
        expectLastCall().times(1);
        replay(topology);
        dm.startUp(null);

        VirtualRouting vr = getVirtualRouting();
        vr.setDeviceManager(dm);

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice d = mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));

        defaultNetVirt.setArpManagerMode(ARPMode.ALWAYS_FLOOD);
        defaultNetVirt.setBroadcastMode(BroadcastMode.FORWARD_TO_KNOWN);

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        Capture<OFMessage> writeCapture = new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);

        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();

        MockControllerProvider bp = getMockControllerProvider();
        IControllerService bcp = createMock(IControllerService.class);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).andReturn(true).
                                   anyTimes();
        am.setControllerProvider(bcp);
        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(mockSwitch,  bcp);

        ListenerContext bc = parseAndAnnotate(packetInGratARP, d, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInGratARP, bc);
        verify(mockSwitch, bcp);

        assertFalse(writeCapture.hasCaptured());

        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.MULTICAST, result.getRoutingAction());
    }

    /**
     * Test ARP on cluster without AP.
     * @throws Exception
     */
    @Test
    public void testBroadcastARPonClusterWithoutAP() throws Exception {
        ArpManager am = getArpManager();
        MockDeviceManager dm = getMockDeviceManager();
        reset(topology);
        topology.addListener(dm);
        expectLastCall().times(1);
        replay(topology);
        dm.startUp(null);

        VirtualRouting vr = getVirtualRouting();
        vr.setDeviceManager(dm);

        long mac = HexString.toLong("00:44:33:22:11:00");
        int ip = IPv4.toIPv4Address("192.168.1.1");
        IDevice srcDev =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> srcIfaces = new ArrayList<VNSInterface>();
        srcIfaces.add(new VNSInterface("defaultSrcIface1", defaultNetVirt, null, null));

        defaultNetVirt.setArpManagerMode(ARPMode.ALWAYS_FLOOD);
        defaultNetVirt.setBroadcastMode(BroadcastMode.FORWARD_TO_KNOWN);

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        Capture<OFMessage> writeCapture = new Capture<OFMessage>(CaptureType.ALL);
        Capture<ListenerContext> contextCapture =
                new Capture<ListenerContext>(CaptureType.ALL);

        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();

        MockControllerProvider bp = getMockControllerProvider();
        IControllerService bcp =
                createMock(IControllerService.class);
        expect(bcp.getOFMessageFactory()).
        andReturn(bp.getOFMessageFactory()).anyTimes();
        expect(bcp.injectOfMessage(eq(mockSwitch),
                                   capture(writeCapture))).andReturn(true).
                                   anyTimes();
        am.setControllerProvider(bcp);
        bp.clearListeners();
        bp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(mockSwitch,  bcp);

        ListenerContext bc =
                parseAndAnnotate(packetInARPRequest, srcDev, null);
        INetVirtManagerService.bcStore.put(bc, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
        bp.dispatchMessage(mockSwitch, packetInARPRequest, bc);
        verify(mockSwitch, bcp);

        assertFalse(writeCapture.hasCaptured());

        IRoutingDecision result =
            IRoutingDecision.rtStore.get(bc, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, result.getRoutingAction());
        assertEquals(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT,
                     result.getHardTimeout());
    }

    protected ArpManager getArpManager() {
        return arpManager;
    }

    protected MockDeviceManager getMockDeviceManager() {
        return mockDeviceManager;
    }

    protected VirtualRouting getVirtualRouting() {
        return virtualRouting;
    }

    protected INetVirtManagerService getNetVirtManager() {
        return netVirtManager;
    }

    protected IStorageSourceService getStorageSource() {
        return storageSource;
    }
}
