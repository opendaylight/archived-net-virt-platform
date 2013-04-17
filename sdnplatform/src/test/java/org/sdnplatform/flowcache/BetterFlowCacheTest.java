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
import java.util.concurrent.ConcurrentHashMap;



import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
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
import org.openflow.util.HexString;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFMessageListener;
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
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.BetterFlowCache;
import org.sdnplatform.flowcache.BetterFlowReconcileManager;
import org.sdnplatform.flowcache.FCQueryObj;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.FlowCacheQueryResp;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.QRFlowCacheObj;
import org.sdnplatform.flowcache.BetterFlowCache.FCOper;
import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;
import org.sdnplatform.forwarding.Forwarding;
import org.sdnplatform.forwarding.IForwardingService;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.forwarding.RewriteServiceImpl;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.ARP;
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
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;

public class BetterFlowCacheTest extends PlatformTestCase {

    protected ListenerContext cntx12, cntx21, cntx13, cntx31, cntx14;
    protected MockDeviceManager deviceManager;
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected ITunnelManagerService tunnelManager;
    protected VirtualRouting virtualRouting;
    protected Forwarding forwarding;
    protected  RestApiServer restApi;
    protected BetterFlowCache betterFlowCacheMgr;
    protected ICounterStoreService counterStore;
    protected INetVirtManagerService mockNetVirtManager;
    protected BetterFlowReconcileManager flowReconcileMgr;
    protected RewriteServiceImpl rewriteService;
    protected IAddressSpaceManagerService addressSpaceManager;
    protected IOFSwitch sw1, sw2, sw3;
    protected IDevice device1, device2, device2alt, device3, device4;
    protected OFPacketIn packetIn12, packetIn21, packetIn13, packetIn31;
    protected OFPacketIn packetIn14, arpPacketIn;
    protected OFPacketOut packetOut12, packetOut21, packetOut13, packetOut31;
    protected OFPacketOut packetOut14, arpPacketOut;
    protected OFFlowRemoved flowRemoveMsg12;
    protected OFFlowRemoved flowRemoveMsg21;
    protected OFFlowRemoved flowRemoveMsg13;
    protected OFFlowRemoved flowRemoveMsg31;
    protected OFFlowRemoved flowRemoveMsg14;
    protected IPacket testPacket12, testPacket21, testPacket13, testPacket31;
    protected IPacket testPacket14, testARPPacket;
    protected IPacket testMulticastPacket;
    protected IPacket testSecondMulticastPacket;
    protected byte[] testPacketSerialized12;
    protected byte[] testPacketSerialized21;
    protected byte[] testPacketSerialized13;
    protected byte[] testPacketSerialized31;
    protected byte[] testPacketSerialized14;
    protected byte[] testARPPacketSerialized;
    protected byte[] testSecondMulticastPacketSerialized;
    protected byte[] testMulticastPacketSerialized;
    protected IPacket testPacketUnknownDest;
    protected byte[] testPacketUnknownDestSerialized;
    protected IPacket testBroadcastPacket;
    protected byte[] testBroadcastPacketSerialized;
    protected IRoutingDecision decision12, decision21, decision13, decision31;
    protected IRoutingDecision decision14;
    protected int expected_wildcards;
    protected Date currentDate;
    
    protected OFStatisticsRequest ofStatsRequest;
    private MockThreadPoolService tp;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        ModuleContext fmc = new ModuleContext();

        // Mock context
        cntx12 = new ListenerContext();
        cntx21 = new ListenerContext();
        cntx13 = new ListenerContext();
        cntx31 = new ListenerContext();
        cntx14 = new ListenerContext();
        
        forwarding       = new Forwarding();
        deviceManager    = new MockDeviceManager();
        flowReconcileMgr = new BetterFlowReconcileManager();
        virtualRouting   = new VirtualRouting();
        mockControllerProvider = getMockControllerProvider();
        rewriteService = new RewriteServiceImpl();
        tp = new MockThreadPoolService();
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        addressSpaceManager = createMock(IAddressSpaceManagerService.class);
        restApi          = new RestApiServer();
        
        betterFlowCacheMgr  = new BetterFlowCache();
        betterFlowCacheMgr.setAppName("netVirt");
        betterFlowCacheMgr.periodicSwScanInitDelayMsec = 0;
        betterFlowCacheMgr.periodicSwScanIntervalMsec  = 100; // ms
        
        mockNetVirtManager         = createMock(INetVirtManagerService.class);
        routingEngine          = createMock(IRoutingService.class);
        topology               = createMock(ITopologyService.class);
        tunnelManager          = createMock(ITunnelManagerService.class);
        
        counterStore = new CounterStore();

        fmc.addService(IForwardingService.class, forwarding);
        fmc.addService(IControllerService.class, mockControllerProvider);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IDeviceService.class, deviceManager);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(ITunnelManagerService.class, tunnelManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(INetVirtManagerService.class, mockNetVirtManager);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(ICounterStoreService.class, counterStore);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IAddressSpaceManagerService.class, addressSpaceManager);
        fmc.addService(IRewriteService.class, rewriteService);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IStorageSourceService.class, new MemoryStorageSource());
        
        deviceManager.init(fmc);
        virtualRouting.init(fmc);
        betterFlowCacheMgr.init(fmc);
        flowReconcileMgr.init(fmc);
        forwarding.init(fmc);
        rewriteService.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);
        restApi.init(fmc);
        
        topology.addListener(flowReconcileMgr);
        expectLastCall().times(1);
        
        deviceManager.startUp(fmc);
        betterFlowCacheMgr.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        forwarding.startUp(fmc);
        rewriteService.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        restApi.startUp(fmc);

        // Mock tunnel manager
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(false).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong())).andReturn(null).anyTimes();
        replay(tunnelManager);

        // Mock address space manager
        addressSpaceManager.getSwitchPortVlanMode(anyObject(SwitchPort.class),
                                                  anyObject(String.class),
                                                  anyShort(),
                                                  anyBoolean());
        expectLastCall().andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        replay(addressSpaceManager);
        
        // Mock switches
        sw1 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();

        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).
        andReturn(false).anyTimes();
        expect(sw1.getStringId()).andReturn("00:00:00:00:00:00:00:01").
        anyTimes();

        sw2 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.isConnected()).andReturn(true).anyTimes();

        expect(sw2.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).
        andReturn(false).anyTimes();
        expect(sw2.getStringId()).andReturn("00:00:00:00:00:00:00:02").
        anyTimes();

        sw3 = EasyMock.createNiceMock(IOFSwitch.class);
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.isConnected()).andReturn(true).anyTimes();

        expect(sw3.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).
        andReturn(false).anyTimes();
        expect(sw3.getStringId()).andReturn("00:00:00:00:00:00:00:03").
        anyTimes();
        //switch 3 belongs to cluster 3. as it is on its own.

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

        testPacket13 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:03")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setDestinationAddress("192.168.1.3")
                .setSourceAddress("192.168.1.1")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized13 = testPacket13.serialize();

        testPacket31 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:01")
        .setSourceMACAddress("00:00:00:00:00:03")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setDestinationAddress("192.168.1.1")
                .setSourceAddress("192.168.1.3")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized31 = testPacket31.serialize();

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

        testARPPacket = new Ethernet()
        .setSourceMACAddress("00:00:00:00:00:01")
        .setDestinationMACAddress("00:00:00:00:00:04")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress(
                                                           "00:00:00:00:00:01"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes(
                                                               "192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress(
                                                           "00:00:00:00:00:04"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes(
                                                               "192.168.1.4")));
        testARPPacketSerialized = testARPPacket.serialize();
        
        // Build src and dest devices
        byte[] dataLayerDevice1 = ((Ethernet)testPacket12)
                .getSourceMACAddress();
        byte[] dataLayerDevice2 = ((Ethernet)testPacket21)
                .getSourceMACAddress();
        byte[] dataLayerDevice3 = ((Ethernet)testPacket31)
                .getSourceMACAddress();
        byte[] dataLayerDevice4 =
                ((Ethernet)testPacket14).getDestinationMACAddress();

        int networkSource1 = ((IPv4)((Ethernet)testPacket12).getPayload()).
                getSourceAddress();
        int networkSource2 = ((IPv4)((Ethernet)testPacket21).getPayload()).
                getSourceAddress();
        int networkSource3 = ((IPv4)((Ethernet)testPacket31).getPayload()).
                getSourceAddress();
        int networkSource4 = ((IPv4)((Ethernet)testPacket14).getPayload()).
                getDestinationAddress();

        expect(topology.getL2DomainId(EasyMock.anyLong()))
        .andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(),
                                              EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        replay(topology);
        
        currentDate = new Date();
        device1 = deviceManager.
                learnEntity(Ethernet.toLong(dataLayerDevice1),
                            null,
                            networkSource1, 
                            1L, 1, false);
        device2 = deviceManager.
                learnEntity(Ethernet.toLong(dataLayerDevice2),
                            null,
                            networkSource2, 
                            1L, 2, false);
        device3 = deviceManager.
                learnEntity(Ethernet.toLong(dataLayerDevice3),
                            null,
                            networkSource3, 
                            1L, 3, false);
        device4 = deviceManager.
                learnEntity(Ethernet.toLong(dataLayerDevice4),
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

        packetIn21 = ((OFPacketIn) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 2)
                .setPacketData(testPacketSerialized21)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized21.length);

        packetIn13 = ((OFPacketIn) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized13)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized13.length);

        packetIn31 = ((OFPacketIn) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 3)
                .setPacketData(testPacketSerialized31)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized31.length);

        packetIn14 = ((OFPacketIn) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized14)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized14.length);

        arpPacketIn = ((OFPacketIn) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testARPPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testARPPacketSerialized.length);
        
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

        packetOut21 =
                (OFPacketOut) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut21.setBufferId(this.packetIn21.getBufferId())
        .setInPort(this.packetIn21.getInPort());
        List<OFAction> poactions21 = new ArrayList<OFAction>();
        poactions21.add(new OFActionOutput((short)1, (short) 0xffff));
        packetOut21.setActions(poactions21)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized21)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH +
                packetOut21.getActionsLength() +
                testPacketSerialized21.length);

        packetOut13 =
                (OFPacketOut) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut13.setBufferId(this.packetIn13.getBufferId())
        .setInPort(this.packetIn13.getInPort());
        List<OFAction> poactions13 = new ArrayList<OFAction>();
        poactions13.add(new OFActionOutput((short)3, (short) 0xffff));
        packetOut13.setActions(poactions13)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized13)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH +
                packetOut13.getActionsLength( ) +
                testPacketSerialized13.length);

        packetOut31 =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_OUT);
        packetOut31.setBufferId(this.packetIn31.getBufferId())
        .setInPort(this.packetIn31.getInPort());
        List<OFAction> poactions31 = new ArrayList<OFAction>();
        poactions31.add(new OFActionOutput((short)1, (short) 0xffff));
        packetOut31.setActions(poactions31)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized31)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH +
                packetOut31.getActionsLength() +
                testPacketSerialized31.length);

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

        arpPacketOut =
            (OFPacketOut) mockControllerProvider.getOFMessageFactory().
            getMessage(OFType.PACKET_OUT);
        arpPacketOut.setBufferId(this.arpPacketOut.getBufferId())
        .setInPort(this.arpPacketIn.getInPort());
        List<OFAction> arppoactions = new ArrayList<OFAction>();
        arppoactions.add(new OFActionOutput((short)3, (short) 0xffff));
        arpPacketOut.setActions(poactions14)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testARPPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH +
                    arpPacketOut.getActionsLength( ) +
                    testARPPacketSerialized.length);
    
        // Mock flow-mod removal messages
        flowRemoveMsg12 = ((OFFlowRemoved) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_REMOVED));
        flowRemoveMsg12.setCookie(2L << 52);

        flowRemoveMsg21 = ((OFFlowRemoved) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_REMOVED));
        flowRemoveMsg21.setCookie(2L << 52);

        flowRemoveMsg13 = ((OFFlowRemoved) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_REMOVED));
        flowRemoveMsg13.setCookie(2L << 52);

        flowRemoveMsg31 = ((OFFlowRemoved) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_REMOVED));
        flowRemoveMsg31.setCookie(2L << 52);

        flowRemoveMsg14 = ((OFFlowRemoved) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_REMOVED));
        flowRemoveMsg14.setCookie(2L << 52);

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
        VNS netVirt1 = new VNS("default");
        srcIfaces1.add(new VNSInterface("testSrcIface1", netVirt1, null, null));
        INetVirtManagerService.bcStore.put(
                cntx12, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces1);
        IFlowCacheService.fcStore.put(cntx12,
                                   IFlowCacheService.FLOWCACHE_APP_NAME, "netVirt");
        IFlowCacheService.fcStore.put(cntx12,
                 IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, netVirt1.getName());

        // Mock decision21
        decision21 = createMock(IRoutingDecision.class);
        expect(decision21.getSourceDevice()).andReturn(device2).atLeastOnce();
        expect(decision21.getSourcePort()).andReturn(
                            new SwitchPort(1L, (short)2)).atLeastOnce();

        ArrayList<IDevice> dstDevices21 = new ArrayList<IDevice>();
        dstDevices21.add(device1);
        expect(decision21.getDestinationDevices()).andReturn(dstDevices21).
        atLeastOnce();
        IRoutingDecision.rtStore.put(
                cntx21, IRoutingDecision.CONTEXT_DECISION, decision21);
        // set decision.getRoutingAction() based on test case
        // Set SRC_IFACCES in context
        List<VNSInterface> srcIfaces2 = new ArrayList<VNSInterface>();
        VNS netVirt2 = new VNS("default");
        srcIfaces2.add(new VNSInterface("testSrcIface2", netVirt2, null, null));
        INetVirtManagerService.bcStore.put(
                cntx21, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces2);
        IFlowCacheService.fcStore.put(cntx21,
                                   IFlowCacheService.FLOWCACHE_APP_NAME, "netVirt");
        IFlowCacheService.fcStore.put(cntx21,
                 IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, netVirt2.getName());

        // Mock decision13
        decision13 = createMock(IRoutingDecision.class);
        expect(decision13.getSourceDevice()).andReturn(device1).atLeastOnce();
        expect(decision13.getSourcePort()).andReturn(
                            new SwitchPort(1L, (short)1)).atLeastOnce();

        ArrayList<IDevice> dstDevices13 = new ArrayList<IDevice>();
        dstDevices13.add(device3);
        expect(decision13.getDestinationDevices()).
        andReturn(dstDevices13).atLeastOnce();
        IRoutingDecision.rtStore.put(
                cntx13, IRoutingDecision.CONTEXT_DECISION, decision13);
        // set decision.getRoutingAction() based on test case
        // Set SRC_IFACCES in context
        List<VNSInterface> srcIfaces3 = new ArrayList<VNSInterface>();
        VNS netVirt3 = new VNS("default");
        srcIfaces3.add(new VNSInterface("testSrcIface3", netVirt3, null, null));
        INetVirtManagerService.bcStore.put(
                cntx13, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces3);
        IFlowCacheService.fcStore.put(cntx14,
                                   IFlowCacheService.FLOWCACHE_APP_NAME, "netVirt");
        IFlowCacheService.fcStore.put(cntx13,
                 IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, netVirt3.getName());

        // Mock decision31
        decision31 = createMock(IRoutingDecision.class);
        expect(decision31.getSourceDevice()).andReturn(device3).atLeastOnce();
        expect(decision31.getSourcePort()).andReturn(
                            new SwitchPort(1L, (short)3)).atLeastOnce();

        ArrayList<IDevice> dstDevices31 = new ArrayList<IDevice>();
        dstDevices31.add(device1);
        expect(decision31.getDestinationDevices()).
        andReturn(dstDevices31).atLeastOnce();
        IRoutingDecision.rtStore.put(
                cntx31, IRoutingDecision.CONTEXT_DECISION, decision31);
        // set decision.getRoutingAction() based on test case
        // Set SRC_IFACCES in context
        List<VNSInterface> srcIfaces4 = new ArrayList<VNSInterface>();
        VNS netVirt4 = new VNS("default");
        srcIfaces4.add(new VNSInterface("testSrcIface4", netVirt4, null, null));
        INetVirtManagerService.bcStore.put(
                cntx31, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces4);
        IFlowCacheService.fcStore.put(cntx14,
                                   IFlowCacheService.FLOWCACHE_APP_NAME, "netVirt");
        IFlowCacheService.fcStore.put(cntx31,
                 IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, netVirt4.getName());

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
        VNS netVirt5 = new VNS("default");
        srcIfaces5.add(new VNSInterface("testSrcIface5", netVirt5, null, null));
        INetVirtManagerService.bcStore.put(
                cntx14, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces5);
        IFlowCacheService.fcStore.put(cntx14,
                                  IFlowCacheService.FLOWCACHE_APP_NAME, "netVirt");
        IFlowCacheService.fcStore.put(cntx14,
                 IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, netVirt5.getName());
        
        // Create a statsRequest
        ofStatsRequest = new OFStatisticsRequest();
        ofStatsRequest.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = ofStatsRequest.getLengthU();

        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        OFMatch match = new OFMatch();
        match.setWildcards(FlowCacheObj.WILD_ALL);
        specificReq.setMatch(match);
        specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
        specificReq.setTableId((byte) 0xff);
        ofStatsRequest.setStatistics(Collections.singletonList(
                                            (OFStatistics)specificReq));
        requestLength += specificReq.getLength();

        ofStatsRequest.setLengthU(requestLength);
    }

    @After   
    public void tearDown() { 
        tp.getScheduledExecutor().shutdownNow();
        verify(tunnelManager);
    }
    
    @Test
    public void testFlowCacheFlowAddRemoves() throws Exception {
        // Mock decision
        expect(decision12.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision12.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision12.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        expect(decision21.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision21.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision21.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        expect(decision13.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision13.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision13.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        expect(decision31.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision31.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision31.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as local and Mock route
        Route route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)2));
        long cookie12 = forwarding.getHashByMac(((Ethernet) testPacket12).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)2, cookie12, true))
        .andReturn(route).anyTimes();

        route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(1L, (short)1));
        long cookie21 = forwarding.getHashByMac(((Ethernet) testPacket21).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)2, 1L, (short)1, cookie21, true))
        .andReturn(route).anyTimes();

        route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie13 = forwarding.getHashByMac(((Ethernet) testPacket13).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie13, true))
        .andReturn(route).anyTimes();

        route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(1L, (short)1));
        long cookie31 = forwarding.getHashByMac(((Ethernet) testPacket31).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)3, 1L, (short)1, cookie31, true))
        .andReturn(route).anyTimes();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        // Packet 1 to 2 from sw1, inport 1
        match.loadFromPacket(testPacketSerialized12, (short) 1);
        OFActionOutput action = new OFActionOutput((short)2, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        Long cookie = 2L << 52;
        fm1.setIdleTimeout((short)5)
        .setMatch(match.clone()
                .setWildcards(0x3FFFF0))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(cookie)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+
                            OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        reset(sw1);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();
        sw1.write(fm1, cntx12);
        expectLastCall().anyTimes();
        sw1.write(packetOut12, cntx12);
        expectLastCall().anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(EasyMock.anyLong()))
        .andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong(),
                                      EasyMock.anyBoolean()))
        .andReturn(1L).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)2,
                                              (long)1, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)2)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2))
        .andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)1,
                                              (long)1, (short)2, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2, true))
        .andReturn(true).anyTimes();


        expect(topology.getOutgoingSwitchPort((long)1, (short)2,
                                              (long)1, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).atLeastOnce();
        expect(topology.getOutgoingSwitchPort((long)1, (short)1,
                                              (long)1, (short)2, true))
        .andReturn(new NodePortTuple((long)1, (short)2)).atLeastOnce();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        
        replay(sw1, routingEngine, decision12, topology);
        forwarding.receive(sw1, this.packetIn12, cntx12);
        betterFlowCacheMgr.updateFlush();
        verify(sw1, routingEngine, decision12);

        // Check that the flow was added to flow-cache
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        
        int preCount = flowReconcileMgr.flowQueryRespHandlerCallCount.get();
        // Check that the get API works to get all flows to 
        // the destination device, device2
        String testName = "FCTestDst";
        FCQueryObj fcQueryObj = new FCQueryObj(
                        flowReconcileMgr,
                        null,   // null appName
                        null,   // null vlan
                        null,   // null srcDevice
                        device2,
                        testName,
                        FCQueryEvType.GET,
                        null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        
        Date startTime = new Date();
        while (flowReconcileMgr.flowQueryRespHandlerCallCount.get() == preCount) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 1000);
        }
        assertEquals(preCount+1, flowReconcileMgr.flowQueryRespHandlerCallCount.get());
        
        FlowCacheQueryResp bfcQR = flowReconcileMgr.lastFCQueryResp;
        assertNotNull(bfcQR);
        assertEquals(FCQueryEvType.GET, bfcQR.queryObj.evType);
        assertEquals(testName, bfcQR.queryObj.callerName);
        assertEquals(bfcQR.queryObj, fcQueryObj);
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

        preCount = flowReconcileMgr.flowQueryRespHandlerCallCount.get();
        // Check that the get API works to get all flows to 
        // the src device, device1
        testName = "FCTestSrc";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                   null,   // null appName
                                   null,   // null vlan
                                   device1,
                                   null,   // null destDevice
                                   testName,
                                   FCQueryEvType.GET,
                                   null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        
        startTime = new Date();
        while (flowReconcileMgr.flowQueryRespHandlerCallCount.get() == preCount) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 1000);
        }
        assertEquals(preCount+1, flowReconcileMgr.flowQueryRespHandlerCallCount.get());
        
        bfcQR = flowReconcileMgr.lastFCQueryResp;
        assertEquals(FCQueryEvType.GET, bfcQR.queryObj.evType);
        assertEquals(testName, bfcQR.queryObj.callerName);
        assertEquals(bfcQR.queryObj, fcQueryObj);
        assertEquals(false, bfcQR.moreFlag);
        assertEquals(1, bfcQR.qrFlowCacheObjList.size());
        qrFcObj = bfcQR.qrFlowCacheObjList.get(0);
        assertEquals(1L, qrFcObj.ofmWithSwDpid.getSwitchDataPathId());
        assertEquals(1, qrFcObj.ofmWithSwDpid.getOfMatch().getInputPort());
        assertEquals(Ethernet.toLong(qrFcObj.ofmWithSwDpid.getOfMatch()
                                     .getDataLayerSource()),
                                     device1.getMACAddress());
        assertEquals(Ethernet.toLong(qrFcObj.ofmWithSwDpid.getOfMatch()
                                     .getDataLayerDestination()),
                                     device2.getMACAddress());
        assertEquals(FlowCacheObj.FCActionPERMIT, qrFcObj.action);
        
        preCount = flowReconcileMgr.flowQueryRespHandlerCallCount.get();
        // Check that the get API works to get flows to 
        // the dest device, device2, in the correct netVirt
        testName = "FCTestDestInNetVirt";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    "default",
                                    null,      // null vlan
                                    null,      // null srcDevice
                                    device2,
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        
        startTime = new Date();
        while (flowReconcileMgr.flowQueryRespHandlerCallCount.get() == preCount) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 1000);
        }
        assertEquals(preCount+1, flowReconcileMgr.flowQueryRespHandlerCallCount.get());
        
        bfcQR = flowReconcileMgr.lastFCQueryResp;
        assertEquals(FCQueryEvType.GET, bfcQR.queryObj.evType);
        assertEquals(testName, bfcQR.queryObj.callerName);
        assertEquals(bfcQR.queryObj, fcQueryObj);
        assertEquals(false, bfcQR.moreFlag);
        assertEquals(1, bfcQR.qrFlowCacheObjList.size());
        qrFcObj = bfcQR.qrFlowCacheObjList.get(0);
        assertEquals(1L, qrFcObj.ofmWithSwDpid.getSwitchDataPathId());
        assertEquals(1, qrFcObj.ofmWithSwDpid.getOfMatch().getInputPort());
        assertEquals(Ethernet.toLong(qrFcObj.ofmWithSwDpid.getOfMatch()
                                     .getDataLayerSource()),
                                     device1.getMACAddress());
        assertEquals(Ethernet.toLong(qrFcObj.ofmWithSwDpid.getOfMatch()
                                     .getDataLayerDestination()),
                                     device2.getMACAddress());
        assertEquals(FlowCacheObj.FCActionPERMIT, qrFcObj.action);
        
        preCount = flowReconcileMgr.flowQueryRespHandlerCallCount.get();
        // Check that the get API works to get flows to 
        // the dest device, device2, in a wrong netVirt.
        // It is expected to get 0 flows
        testName = "FCTestDestInNetVirt";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    "netVirt1",
                                    null,      // null vlan
                                    null,      // null srcDevice
                                    device2,
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        
        startTime = new Date();
        while (flowReconcileMgr.flowQueryRespHandlerCallCount.get() == preCount) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 1000);
        }
        assertEquals(preCount+1, flowReconcileMgr.flowQueryRespHandlerCallCount.get());
        
        bfcQR = flowReconcileMgr.lastFCQueryResp;
        assertEquals(FCQueryEvType.GET, bfcQR.queryObj.evType);
        assertEquals(testName, bfcQR.queryObj.callerName);
        assertEquals(bfcQR.queryObj, fcQueryObj);
        assertEquals(false, bfcQR.moreFlag);
        assertEquals(0, bfcQR.qrFlowCacheObjList.size());
        
        // Inject other packet-ins and check the flow-cache counters
        replay(decision21);
        forwarding.receive(sw1, this.packetIn21, cntx21);
        betterFlowCacheMgr.updateFlush();
        verify(decision21);
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getAddCnt());

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();

        expect(topology.getOutgoingSwitchPort((long)1, (short)1,
                                              (long)1,(short)3, true))
        .andReturn(new NodePortTuple((long)1, (short)3)).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)1,
                                              (long)1, (short)3, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3, true))
        .andReturn(true).anyTimes();

        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        
        replay(decision13, topology);
        forwarding.receive(sw1, this.packetIn13, cntx13);
        betterFlowCacheMgr.updateFlush();
        //verify(decision13);
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getAddCnt());

        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort((long)1, (short)3,
                                              (long)1, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)3,
                                              (long)1, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)3)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3, true))
        .andReturn(true).anyTimes();

        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        
        replay(decision31, topology);
        forwarding.receive(sw1, this.packetIn31, cntx31);
        betterFlowCacheMgr.updateFlush();
        verify(decision31);
        assertEquals(4, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(4, betterFlowCacheMgr.getBfcCore().getAddCnt());

        // Check the public APIs

        // All flows in a netVirt
        testName = "FCTestDefaultNetVirt";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    "default",
                                    null,      // null vlan
                                    null,      // null srcDevice
                                    null,      // null destDevice
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        FlowCacheQueryResp resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }     
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(false, resp.moreFlag);
        assertEquals(4, resp.qrFlowCacheObjList.size());

        // All flows in a netVirt in a vlan
        testName = "FCTestDefaultNetVirtNoVlan";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    "default",
                                    (short)-1,
                                    null,      // null srcDevice
                                    null,      // null destDevice
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(testName, resp.queryObj.callerName);
        assertEquals(false, resp.moreFlag);
        assertEquals(4, resp.qrFlowCacheObjList.size());

        // All flows destined to a device
        testName = "FCTestAllFlowsToDestinationDevice";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    null,      // null appName
                                    null,      // null vlan
                                    device1,
                                    null,      // null destDevice
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(testName, resp.queryObj.callerName);
        assertEquals(false, resp.moreFlag);
        // Two flows: 2--> 1 and 3 --> 1
        assertEquals(2, resp.qrFlowCacheObjList.size());

        // All flows destined to a device by the correct netVirt
        testName = "FCTestAllFlowsToDestinationDeviceInNetVirt";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    "default",
                                    null,      // null vlan
                                    device1,
                                    null,      // null destDevice
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(testName, resp.queryObj.callerName);
        assertEquals(false, resp.moreFlag);
        // Two flows: 2--> 1 and 3 --> 1
        assertEquals(2, resp.qrFlowCacheObjList.size());
        
        // All flows destined to a device by wrong netVirt
        testName = "FCTestAllFlowsToDestinationDeviceInWrongNetVirt";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    "netVirt10",
                                    null,      // null vlan
                                    device1,
                                    null,      // null destDevice
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(testName, resp.queryObj.callerName);
        assertEquals(false, resp.moreFlag);
        assertEquals(0, resp.qrFlowCacheObjList.size());
        
        // All flows from a source device
        testName = "FCTestAllFlowsFromSourceDevice";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    null,      // null appName
                                    null,      // null vlan
                                    device1,
                                    null,      // null destDevice
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(testName, resp.queryObj.callerName);
        assertEquals(false, resp.moreFlag);
        assertEquals(2, resp.qrFlowCacheObjList.size());
        
        // All flows to a device from a source
        testName = "FCTestAllFlowBetweenSourceDestination";
        fcQueryObj = new FCQueryObj(flowReconcileMgr,
                                    null,      // null appName
                                    null,      // null vlan
                                    device1,
                                    device2,
                                    testName,
                                    FCQueryEvType.GET,
                                    null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        startTime = new Date();
        resp = flowReconcileMgr.lastFCQueryResp;
        while (resp == null || ! resp.queryObj.callerName.equals(testName)) {
            Date curTime = new Date();
            assertTrue((curTime.getTime() - startTime.getTime()) < 5000);
            resp = flowReconcileMgr.lastFCQueryResp;
        }
        assertEquals(FCQueryEvType.GET, resp.queryObj.evType);
        assertEquals(testName, resp.queryObj.callerName);
        assertEquals(false, resp.moreFlag);
        assertEquals(1, resp.qrFlowCacheObjList.size());

        OFMatch match12 = new OFMatch();
        match12.loadFromPacket(testPacketSerialized12, (short) 1);
        flowRemoveMsg12.setMatch(match12);
        flowRemoveMsg12.getMatch().setWildcards(VirtualRouting.DEFAULT_HINT);
        flowRemoveMsg12.setPriority(forwarding.getAccessPriority());
        OFMatch match13 = new OFMatch();
        match13.loadFromPacket(testPacketSerialized13, (short) 1);
        flowRemoveMsg13.setMatch(match13);
        flowRemoveMsg13.getMatch().setWildcards(VirtualRouting.DEFAULT_HINT);
        flowRemoveMsg13.setPriority(forwarding.getAccessPriority());
        OFMatch match21 = new OFMatch();
        match21.loadFromPacket(testPacketSerialized21, (short) 2);
        flowRemoveMsg21.setMatch(match21);
        flowRemoveMsg21.getMatch().setWildcards(VirtualRouting.DEFAULT_HINT);
        flowRemoveMsg21.setPriority(forwarding.getAccessPriority());
        OFMatch match31 = new OFMatch();
        match31.loadFromPacket(testPacketSerialized31, (short) 3);
        flowRemoveMsg31.setMatch(match31);
        flowRemoveMsg31.getMatch().setWildcards(VirtualRouting.DEFAULT_HINT);
        flowRemoveMsg31.setPriority(forwarding.getAccessPriority());

        // Inject flow removal message from device 1 to 2
        betterFlowCacheMgr.receive(sw1, flowRemoveMsg12, cntx12);
        betterFlowCacheMgr.updateFlush();
        // Check that the message was removed
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getDeactivatedCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());

        // Inject flow removal message from device 1 to 3
        betterFlowCacheMgr.receive(sw1, flowRemoveMsg13, cntx13);
        betterFlowCacheMgr.updateFlush();
        // Check that the message was removed
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getDeactivatedCnt());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());

        // Inject flow removal message from device 2 to 1
        betterFlowCacheMgr.receive(sw1, flowRemoveMsg21, cntx21);
        betterFlowCacheMgr.updateFlush();
        // Check that the message was removed
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getDeactivatedCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());

        // Inject the same flow removal message from device 2 to 1
        // and check that the counters doesn't change
        betterFlowCacheMgr.receive(sw1, flowRemoveMsg21, cntx21);
        betterFlowCacheMgr.updateFlush();
        // Check that the message was removed
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getDeactivatedCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());

        // Inject flow removal message from device 3 to 1
        betterFlowCacheMgr.receive(sw1, flowRemoveMsg31, cntx31);
        betterFlowCacheMgr.updateFlush();
        // Check that the message was removed
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(4, betterFlowCacheMgr.getBfcCore().getDeactivatedCnt());
        assertEquals(4, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(4, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());

        // Inject a packet-in and check that a deactivated flow was activated
        reset(topology);
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(2L, true)).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(3L, true)).andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort((long)1, (short)1,
                                              (long)1, (short)3, true))
        .andReturn(new NodePortTuple((long)1, (short)3)).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)1,
                                              (long)1, (short)3, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)3))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();

        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        replay(topology);
        forwarding.receive(sw1, this.packetIn13, cntx13);
        betterFlowCacheMgr.updateFlush();
        verify(decision13);
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(4, betterFlowCacheMgr.getBfcCore().getDeactivatedCnt());
        assertEquals(3, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActivatedCnt());
        assertEquals(5, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
    }

    /**
     * Test ARP flowmod is skipped by the flow Cache
     */
    @Test
    public void testSkipARPFlowAdd() throws Exception {
        reset(sw1, sw3);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.isConnected()).andReturn(true).anyTimes();
        
        // Mock decision
        expect(decision14.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision14.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision14.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as local and Mock route
        Route route =  new Route(1L, 3L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(3L, (short)2));
        route.getPath().add(new NodePortTuple(3L, (short)1));
        long cookie14 = forwarding.getHashByMac(((Ethernet) testPacket14).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, cookie14, true))
        .andReturn(route).anyTimes();

        // Record expected packet-outs/flow-mods
        sw1.write((OFMessage)EasyMock.anyObject(),
                  (ListenerContext)EasyMock.anyObject());
        expectLastCall().times(2);
        sw3.write((OFMessage)EasyMock.anyObject(),
                  (ListenerContext)EasyMock.anyObject());
        expectLastCall().times(1);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(EasyMock.anyLong()))
        .andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong(),
                                      EasyMock.anyBoolean()))
        .andReturn(1L).anyTimes();
        expect(topology.getIncomingSwitchPort(1L, (short)1,
                                              3L, (short)1, true))
        .andReturn(new NodePortTuple(1L, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(3L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();

        expect(topology.getOutgoingSwitchPort(1L, (short)1,
                                              3L, (short)1, true))
        .andReturn(new NodePortTuple(3L, (short)1)).atLeastOnce();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        
        replay(sw1, sw3, routingEngine, decision14, topology);
        forwarding.receive(sw1, this.arpPacketIn, cntx14);
        verify(sw1, sw3, routingEngine, decision14, topology);

        // Check that the flow was added to flow-cache
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getAddCnt());
    }
    
    /**
     * Test ARP flows are skipped when handling switch's statsReply
     */
    @Test
    public void testSkipARPStatsReply() throws Exception{

        // Mock decision
        expect(decision12.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision12.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision12.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        expect(decision21.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision21.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision21.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        expect(decision13.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision13.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision13.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        expect(decision31.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision31.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision31.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination as local and Mock route
        Route route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)2));
        long cookie12 = forwarding.getHashByMac(((Ethernet) testPacket12).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)2, cookie12, true))
        .andReturn(route).anyTimes();

        route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(1L, (short)1));
        long cookie21 = forwarding.getHashByMac(((Ethernet) testPacket21).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)2, 1L, (short)1, cookie21, true))
        .andReturn(route).anyTimes();

        route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)3));
        long cookie13 = forwarding.getHashByMac(((Ethernet) testPacket13).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 1L, (short)3, cookie13, true))
        .andReturn(route).anyTimes();

        route =  new Route(1L, 1L);
        route.getPath().add(new NodePortTuple(1L, (short)3));
        route.getPath().add(new NodePortTuple(1L, (short)1));
        long cookie31 = forwarding.getHashByMac(((Ethernet) testPacket31).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)3, 1L, (short)1, cookie31, true))
        .andReturn(route).anyTimes();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        // Packet 1 to 2 from sw1, inport 1
        match.loadFromPacket(testPacketSerialized12, (short) 1);
        OFActionOutput action = new OFActionOutput((short)2, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        Long cookie = 2L << 52;
        fm1.setIdleTimeout((short)5)
        .setMatch(match.clone()
                .setWildcards(0x3FFFF0))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(cookie)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+
                            OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        reset(sw1);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();
        
        sw1.write((OFMessage)EasyMock.anyObject(),
                  (ListenerContext)EasyMock.anyObject());
        expectLastCall().times(2);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(EasyMock.anyLong()))
        .andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong(),
                                      EasyMock.anyBoolean()))
        .andReturn(1L).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)2,
                                              (long)1, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)2)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)2))
        .andReturn(true).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)1,
                                              (long)1, (short)2, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();

        expect(topology.getOutgoingSwitchPort((long)1, (short)2,
                                              (long)1, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).atLeastOnce();
        expect(topology.getOutgoingSwitchPort((long)1, (short)1,
                                              (long)1, (short)2, true))
        .andReturn(new NodePortTuple((long)1, (short)2)).atLeastOnce();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        
        replay(sw1, routingEngine, decision12, topology);
        forwarding.receive(sw1, this.packetIn12, cntx12);
        betterFlowCacheMgr.updateFlush();
        verify(sw1, routingEngine, decision12);

        // Check that the flow was added to flow-cache
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        
        OFStatisticsReply msg = new OFStatisticsReply();
        msg.setType(OFType.STATS_REPLY);
        List<OFStatistics> statsList = new ArrayList<OFStatistics>();
        msg.setStatistics(statsList);
        msg.setXid(0);
        
        // First Non ARP flow
        OFFlowStatisticsReply oneStats = new OFFlowStatisticsReply();
        OFMatch matchResp = new OFMatch();
        matchResp.setDataLayerSource("00:00:00:00:00:01");
        matchResp.setDataLayerDestination("00:00:00:00:00:02");
        matchResp.setWildcards(FlowCacheObj.WILD_MATCH_INP_VLAN_DLADRS);
        oneStats.setCookie(
                AppCookie.makeCookie(Forwarding.FORWARDING_APP_ID, 0));
        oneStats.setMatch(matchResp);
        oneStats.setActions(actions);
        statsList.add(oneStats);
        
        // Second, ARP flow mod
        oneStats = new OFFlowStatisticsReply();
        matchResp = new OFMatch();
        matchResp.setDataLayerSource("00:00:00:00:00:01");
        matchResp.setDataLayerDestination("00:00:00:00:00:02");
        matchResp.setWildcards(FlowCacheObj.WILD_MATCH_INP_VLAN_DLADRS);
        matchResp.setDataLayerType(Ethernet.TYPE_ARP);
        oneStats.setCookie(
                AppCookie.makeCookie(Forwarding.FORWARDING_APP_ID, 0));
        oneStats.setMatch(matchResp);
        oneStats.setActions(actions);
        statsList.add(oneStats);

        reset(sw1);
        replay(sw1);
        betterFlowCacheMgr.receive(sw1, msg, null);
        betterFlowCacheMgr.updateFlush();
        verify(sw1);
        
        // Check that the flow was added to flow-cache
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(2, betterFlowCacheMgr.getBfcCore().getAddCnt());
    }
    
    /**
     * Test that explain packet is not stored in flow cache
     * @throws Exception
     */

    @Test
    public void testExplainPktNotInFlowCache() throws Exception {
        deviceManager.startUp(null);
        // Set destination as sw2 and Mock route
        IDevice device3alt = deviceManager.
                learnEntity(Ethernet.toLong(((Ethernet)testPacket31)
                                            .getSourceMACAddress()),
                            null,
                            ((IPv4)((Ethernet)testPacket31).getPayload()).
                            getSourceAddress(), 
                            2L, 3, false);
        Route route = new Route(1L, 2L);
        route.setPath(new ArrayList<NodePortTuple>());
        route.getPath().add(new NodePortTuple(1L, (short)1));
        route.getPath().add(new NodePortTuple(1L, (short)2));
        route.getPath().add(new NodePortTuple(2L, (short)1));
        route.getPath().add(new NodePortTuple(2L, (short)3));
        long cookie13 = forwarding.getHashByMac(((Ethernet) testPacket31).getSourceMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie13, true))
        .andReturn(route).atLeastOnce();

        // Set up the context to indicate that it is an explain packet
        NetVirtExplainPacket.ExplainStore.put(cntx13,
                NetVirtExplainPacket.KEY_EXPLAIN_PKT,
                NetVirtExplainPacket.VAL_EXPLAIN_PKT);
        NetVirtExplainPacket.ExplainPktRoute epr =
                new NetVirtExplainPacket.ExplainPktRoute();
        NetVirtExplainPacket.ExplainRouteStore.put(cntx13,
                NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE, epr);

        // Set up Mock decision
        decision13 = createMock(IRoutingDecision.class);
        expect(decision13.getSourceDevice()).andReturn(device1).atLeastOnce();
        expect(decision13.getSourcePort()).andReturn(
                            new SwitchPort(1L, (short)1)).atLeastOnce();
        ArrayList<IDevice> dstDevices = new ArrayList<IDevice>();
        dstDevices.add(device3alt);
        expect(decision13.getDestinationDevices()).
        andReturn(dstDevices).atLeastOnce();
        IRoutingDecision.rtStore.put(
                cntx13, IRoutingDecision.CONTEXT_DECISION, decision13);
        expect(decision13.getRoutingAction()).andReturn(
                IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();

        // Start the replay
        reset(topology);
        expect(topology.getL2DomainId(EasyMock.anyLong()))
        .andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong(),
                                      EasyMock.anyBoolean()))
        .andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort((long)1, (short)1,
                                              (long)2, (short)3, true))
        .andReturn(new NodePortTuple((long)2, (short)3)).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)1,
                                              (long)2, (short)3, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1))
        .andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1, true))
        .andReturn(true).anyTimes();

        expect(topology.isAttachmentPointPort(2L, (short)3))
        .andReturn(true).anyTimes();
        expect(topology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort()))
        .andReturn(true).anyTimes();
        
        reset(sw1, sw2, sw3);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();
        expect(sw2.getId()).andReturn(3L).anyTimes();
        expect(sw2.isConnected()).andReturn(true).anyTimes();
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.isConnected()).andReturn(true).anyTimes();
        
        replay(sw1, sw2, sw3, routingEngine, decision13, topology);

        // Call the action function
        forwarding.receive(sw1, this.packetIn13, cntx13);

        // Verify that all the replays that were setup did happen
        verify(sw1, sw2, sw3, routingEngine, decision13, topology);
        // Check that flow cache is empty, i.e. the test packet-in was not
        // added to flow-cache
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getAddCnt());
    }


    /* Test periodic scanning of switch flow tables:
     *
     */

    @SuppressWarnings("deprecation")
    @Test
    public void testFlowCachePeriodicSwitchFlowTableScan() throws Exception {
        betterFlowCacheMgr.fqTask.setEnableFlowQueryTask(true);
        /* Periodic scan interval has already been set to low value 
         * in the setup() */

        /* Add a flow to flow cache */
        OFMatch ofm = new OFMatch();
        ofm.setDataLayerSource(Ethernet.toByteArray(device1.getMACAddress()));
        ofm.setDataLayerDestination(Ethernet.
                                    toByteArray(device2.getMACAddress()));
        ofm.setDataLayerVirtualLan((short)-1);
        OFMatchWithSwDpid ofmWithSwDpid = new OFMatchWithSwDpid(ofm, 1L);
        betterFlowCacheMgr.addFlow("testNetVirt1", ofmWithSwDpid, 2L, 1L, (short)1,
                (short)0, FlowCacheObj.FCActionPERMIT);
        /* Check that its scan count is zero */
        FlowCacheObj fco = 
        betterFlowCacheMgr.getAllFlowsByApplInstVlanSrcDestDevicesInternal(
                "testNetVirt1", (short)-1, device1.getMACAddress(), 
                device2.getMACAddress());
        assertTrue(fco != null);
        assertEquals(0, fco.fce.scanCnt);

        Capture<OFStatisticsRequest> request =
                new Capture<OFStatisticsRequest>(CaptureType.ALL);
        Capture<Integer> xid = new Capture<Integer>(CaptureType.ALL);
        Capture<IOFMessageListener> iofml =
                new Capture<IOFMessageListener>(CaptureType.ALL);

        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = req.getLengthU();
        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        OFMatch match = new OFMatch();
        match.setWildcards(FlowCacheObj.WILD_ALL);
        specificReq.setMatch(match);
        specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
        specificReq.setTableId((byte) 0xff);
        req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
        requestLength += specificReq.getLength();
        req.setLengthU(requestLength);

        sw1.sendStatsQuery(capture(request),  capture(xid), capture(iofml));
        expectLastCall().atLeastOnce();
        sw2.sendStatsQuery(capture(request),  capture(xid), capture(iofml));
        expectLastCall().atLeastOnce();
        sw3.sendStatsQuery(capture(request),  capture(xid), capture(iofml));
        expectLastCall().atLeastOnce();
        replay(sw1, sw2, sw3);
        /* Sleep for to allow for the switch flow table scans to kick in */
        Thread.sleep((long)(betterFlowCacheMgr.periodicSwScanIntervalMsec*2.5)); 
        verify(sw1, sw2, sw3);
        assertTrue(iofml.getValues().size() >= 3);
        assertEquals(betterFlowCacheMgr, iofml.getValues().get(1));
        assertEquals(betterFlowCacheMgr, iofml.getValues().get(2));
        assertEquals(betterFlowCacheMgr, iofml.getValues().get(3));
        assertTrue(request.getValues().size() >= 3);
        assertEquals(req, request.getValues().get(1));
        assertEquals(req, request.getValues().get(2));
        assertEquals(req, request.getValues().get(3));
        /* Confirm that the scan count of the entry in flow cache was
         * incremented as we didn't inject any response from the switches.
         */
        assertTrue(2 <= fco.fce.scanCnt);
    }

    /* When a switch disconnects from the controller all the flows in the
     * flow cache with the disconnected switch as the source switch should
     * be removed.
     */
    @Test
    public void testFlowCacheSwitchDisconnect() throws Exception {
        betterFlowCacheMgr.fqTask.setEnableFlowQueryTask(false);
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
        long cookie14 = forwarding.getHashByMac(((Ethernet) testPacket14).getDestinationMAC().toLong());
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)1, cookie14, true))
        .andReturn(route).atLeastOnce();

        // Expected Flow-mods
        OFMatch match = new OFMatch();
        // Packet 1 to 4 from sw1, input port 1
        match.loadFromPacket(testPacketSerialized14, (short) 1);
        OFActionOutput action  = new OFActionOutput((short)7, (short)0);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = (OFFlowMod) mockControllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        Long cookie = 2L << 52;
        fm1.setIdleTimeout((short)5)
        .setMatch(match.clone()
                .setWildcards(0x3FFFF0))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(cookie)
                .setFlags((short)1)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+
                            OFActionOutput.MINIMUM_LENGTH);

        // Record expected packet-outs/flow-mods
        sw1.write(fm1, cntx14);
        sw1.write(packetOut14, cntx14);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.getL2DomainId(EasyMock.anyLong()))
        .andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(EasyMock.anyLong(),
                                      EasyMock.anyBoolean()))
        .andReturn(1L).anyTimes();
        expect(topology.getOutgoingSwitchPort((long)1, (short)1,
                                              (long)3, (short)1, true))
        .andReturn(new NodePortTuple((long)3, (short)1)).anyTimes();
        expect(topology.getIncomingSwitchPort((long)1, (short)1,
                                              (long)3, (short)1, true))
        .andReturn(new NodePortTuple((long)1, (short)1)).anyTimes();
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
        //verify(decision12);

        // Check that the flow was added to flow-cache
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getDelCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getNotDampenedCnt());

        /* Inject a switch removed event */
        betterFlowCacheMgr.removedSwitch(sw1);
        betterFlowCacheMgr.updateFlush();

        // Check that the flow was removed from the cache
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt()); // 1 to 0
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getDelCnt()); // 0 to 1
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());// 0 to 1
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getNotDampenedCnt());

        /* Inject a switch removed event - this switch has no flow sourced
         * on it */
        betterFlowCacheMgr.removedSwitch(sw2);
        // Check that the counters are unchanged
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getDelCnt());
        assertEquals(1, betterFlowCacheMgr.getBfcCore().getAddCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getCacheHitCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getNotDampenedCnt());
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testSwitchAdded() throws Exception {
        reset(sw1);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.isConnected()).andReturn(true).anyTimes();
        
        betterFlowCacheMgr.clearFlowCache();
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getInactiveCnt());
        
        Capture<OFStatisticsRequest> statsRequest = 
            new Capture<OFStatisticsRequest>(CaptureType.ALL);
        Capture<Integer> xid = 
            new Capture<Integer>(CaptureType.ALL);
        Capture<IOFMessageListener> callback = 
            new Capture<IOFMessageListener>(CaptureType.ALL);
        
        sw1.sendStatsQuery(capture(statsRequest), capture(xid),
                           capture(callback));
        expectLastCall().times(1);
        
        replay(sw1);
        
        // Simulate switch added
        betterFlowCacheMgr.addedSwitch(sw1);
        verify(sw1);
        
        assertTrue(statsRequest.hasCaptured());
        assertTrue(statsRequest.getValues().size() > 0);
        OFStatisticsRequest thisRequest = statsRequest.getValues().get(0);
        assertEquals(ofStatsRequest, thisRequest);
    }
    
    private class FlowWorker implements Runnable {
        private String appName;
        private long minSrcMac = 0;
        private long srcMacRange = 0;
        private long minDstMac = 0;
        private long dstMacRange = 0;
        private FCOper operation;
        private int id;
        
        public FlowWorker(int id,
                          long minSrcMac, long srcMacRange,
                          long minDstMac, long dstMacRange,
                          FCOper oper,
                          String appName) {
            this.appName = appName;
            this.id = id;
            this.minSrcMac = minSrcMac;
            this.srcMacRange = srcMacRange;
            this.minDstMac = minDstMac;
            this.dstMacRange = dstMacRange;
            this.operation = oper;
        }
        
        private OFMatchWithSwDpid initOfMatchWithSWDpid(long srcMac,
                                                        long dstMac,
                                                        long dpid) {
            OFMatchWithSwDpid fm = new OFMatchWithSwDpid();
            OFMatch match = new OFMatch();
            match.loadFromPacket(testPacketSerialized13, (short)1);
            match.setDataLayerSource(Ethernet.toByteArray(srcMac));
            match.setDataLayerDestination(Ethernet.toByteArray(dstMac));
            fm.setOfMatch(match);
            fm.setSwitchDataPathId(dpid);
            
            return fm;
        }
        
        @Override
        public void run() {
            long srcMac = minSrcMac;
            long swDpid = 1000L;
            short inport = 10;
            short priority = 0;
            
            boolean verbose = false;
            StringBuilder sb = new StringBuilder();
            while (srcMac < minSrcMac + srcMacRange) {
                long dstMac = minDstMac;
                while (dstMac < minDstMac + dstMacRange) {
                    OFMatchWithSwDpid fm =
                            initOfMatchWithSWDpid(srcMac, dstMac, swDpid);
                    OFMatch ofmatch = fm.getOfMatch();
                    ofmatch.setWildcards(0x100000);
                    ofmatch.setInputPort(inport);
                    boolean status = false;
                    while (!status) {
                        switch (this.operation) {
                            case NEW_ENTRY:
                                status = betterFlowCacheMgr.addFlow(
                                                 appName,
                                                 fm,
                                                 100L, swDpid,
                                                 inport,
                                                 priority,
                                                 FlowCacheObj.FCActionPERMIT);
                                break;
                            case DELETED_ACTIVE:
                                status = betterFlowCacheMgr.deleteFlow(appName,
                                                           fm, priority);
                                break;
                            case DEACTIVATED:
                                status = betterFlowCacheMgr.deactivateFlow(appName,
                                                               fm, priority);
                                break;
                            default:
                                break;
                        }
                        
                        /* Enable the printout to debug test failures.
                        if (!status) {
                            verbose = true;
                        }
                        if (verbose) {
                            sb.append(new Date() + "Thread id: " + id + " "
                                + this.operation + " flow from " +
                                Long.toHexString(srcMac) + " to " +
                                Long.toHexString(dstMac) +
                                " activeFlowCnt: " +
                                betterFlowCacheMgr.getBfcCore().getActiveCnt() +
                                " deactiveFlowCnt: " +
                                betterFlowCacheMgr.getBfcCore().getInactiveCnt() +
                                " deletedFlowCnt: " +
                                betterFlowCacheMgr.getBfcCore().getDelCnt() +
                                " status: " + status + "\n");
                        }*/
                    }
                    dstMac++;
                    
                    // Slow down the add to create race with either delete or
                    // de-activate threads
                    if (this.operation == BetterFlowCache.FCOper.NEW_ENTRY) {
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                srcMac++;
                betterFlowCacheMgr.updateFlush();
            }
            if (verbose) {
                System.out.println(sb.toString());
                System.out.println("Thread " + id + " Done.");
            }
        }
    }
    
    /**
     * Test flowAdd and flowDelete in multi-threaded environment
     * @throws InterruptedException
     */
    @Test
    public void testFlowAddDeletes() throws InterruptedException {
        betterFlowCacheMgr.fqTask.setEnableFlowQueryTask(false);
        // Simulate flow
        int NUM_THREADS = 1;
        int macRange = 5;
        String appName = "app1";
        // Add-flow threads
        for (int i = 0; i < NUM_THREADS; i++) {
            Runnable worker = this.new FlowWorker(i,
                                                 i * macRange,
                                                 macRange,
                                                 i * macRange,
                                                 macRange,
                                                 BetterFlowCache.FCOper.NEW_ENTRY,
                                                 appName);
            Thread t = new Thread(worker);
            t.start();
        }
        
        // Delete-flow threads
        int idOffset = 2 * NUM_THREADS;
        /**
         *  Make sure add-thread runs first and have enough time to
         *  create all the destMaps. Otherwise, delete thread can't delete
         *  flows whose destMap hasn't been created.
         */
        while (betterFlowCacheMgr.getBfcCore().getActiveCnt() <
                macRange*NUM_THREADS) {
            Thread.sleep(1);
        }
        
        for (int i = 0; i < NUM_THREADS; i++) {
            Runnable worker = this.new FlowWorker(idOffset + i,
                                             i * macRange,
                                             macRange,
                                             i * macRange,
                                             macRange,
                                             BetterFlowCache.FCOper.DELETED_ACTIVE,
                                             appName);
            Thread t = new Thread(worker);
            t.start();
        }
        
        Date startTime = new Date();
        
        long totalFlows = NUM_THREADS * macRange * macRange;
        if (totalFlows >= BetterFlowCache.MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT) {
            totalFlows = BetterFlowCache.MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT;
        }
        
        while (betterFlowCacheMgr.getBfcCore().getActiveCnt() != 0 ||
                betterFlowCacheMgr.getBfcCore().getDelCnt() != totalFlows) {
            Date currTime = new Date();
            if (currTime.getTime() - startTime.getTime() > 400) {
                ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
                ConcurrentHashMap<Long, FlowCacheObj>>> fms =
                betterFlowCacheMgr.getAllFlowsByApplInstInternal(appName);
                if (fms == null || fms.size() == 0) break;
                int count = 0;
                for (Short vlan : fms.keySet()) {
                    ConcurrentHashMap<Long, 
                    ConcurrentHashMap<Long, FlowCacheObj>> fms_dst =
                    fms.get(vlan);
                    if (fms_dst == null || fms_dst.size() == 0) continue;
                    for (Long dstMac : fms_dst.keySet()) {
                        ConcurrentHashMap<Long, FlowCacheObj> fms_src =
                                fms_dst.get(dstMac);
                        if (fms_src == null || fms_src.size() == 0) continue;
                        for (long srcMac : fms_src.keySet()) {
                            FlowCacheObj fco = fms_src.get(srcMac);
                            if (fco.fce.state == FlowCacheObj.FCStateACTIVE) {
                                System.out.println(
                               "Count: " + ++count +
                               " vlan: " + vlan.shortValue() +
                               " src: " +
                               HexString.toHexString(srcMac, 6) +
                               " dst: " +
                               HexString.toHexString(dstMac.longValue(), 6) +
                               " fm: " +
                               fms_src.get(srcMac).fce.toString());
                            }
                        }
                    }
                }
                // break out of while loop
                break;
            }
        }
        assertEquals(0, betterFlowCacheMgr.getBfcCore().getActiveCnt());
        assertEquals(totalFlows, betterFlowCacheMgr.getBfcCore().getDelCnt());
        
        // Free all flows
        betterFlowCacheMgr.clearFlowCache();
    }
    
    /**
     * Test flowAdd and flowDeactivate in multi-threaded environment
     * @throws InterruptedException
     */
    @Test
    public void testFlowAddDeactivate() throws InterruptedException {
        betterFlowCacheMgr.fqTask.setEnableFlowQueryTask(false);
        // Simulate flow
        int NUM_THREADS = 1;
        int macRange = 10;
        String appName = "app1";
        // Add-flow threads
        for (int i = 0; i < NUM_THREADS; i++) {
            Runnable worker = this.new FlowWorker(i,
                                                 i * macRange,
                                                 macRange,
                                                 i * macRange,
                                                 macRange,
                                                 BetterFlowCache.FCOper.NEW_ENTRY,
                                                 appName);
            Thread t = new Thread(worker);
            t.start();
        }
        
        // Deactive-flow threads
        /**
         *  Make sure add-thread runs first and have enough time to
         *  create all the destMaps. Otherwise, delete thread can't delete
         *  flows whose destMap hasn't been created.
         */
        while (betterFlowCacheMgr.getBfcCore().getActiveCnt() <
                macRange*NUM_THREADS) {
            Thread.sleep(1);
        }
        int idOffset = NUM_THREADS;
        for (int i = 0; i < NUM_THREADS; i++) {
            Runnable worker = this.new FlowWorker(idOffset + i,
                                             i * macRange,
                                             macRange,
                                             i * macRange,
                                             macRange,
                                             BetterFlowCache.FCOper.DEACTIVATED,
                                             appName);
            Thread t = new Thread(worker);
            t.start();
        }
        
        Thread.sleep(1000);
        Date startTime = new Date();
        
        long totalFlows = NUM_THREADS * macRange * macRange;
        if (totalFlows >= BetterFlowCache.MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT) {
            totalFlows = BetterFlowCache.MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT;
        }
        
        while (betterFlowCacheMgr.getBfcCore().getActiveCnt() != 0 ||
               betterFlowCacheMgr.getBfcCore().getDeactivatedCnt() != totalFlows) {
            Date currTime = new Date();
            assertTrue((currTime.getTime() - startTime.getTime()) < 10000);
        }
    }
}
