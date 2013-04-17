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

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyShort;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
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
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.counter.CounterStore;
import org.sdnplatform.counter.ICounterStoreService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.BetterFlowCache;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.forwarding.Forwarding;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.linkdiscovery.ILinkDiscovery;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
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
import org.sdnplatform.routing.RouteId;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.BetterTopologyManager;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;


public class MultipathForwardingTest {
    protected MockControllerProvider mockControllerProvider;
    protected ListenerContext cntx;
    protected MockThreadPoolService threadPool;
    protected MockDeviceManager deviceManager;
    protected IRoutingService routingEngine;
    protected BetterTopologyManager topology;
    protected ITunnelManagerService tunnelManager;
    protected Forwarding forwarding;
    protected IFlowReconcileService flowReconcileMgr;
    protected IRewriteService rewriteService;
    protected IRestApiService restApi;
    protected ILinkDiscoveryService linkDiscovery;
    protected IAddressSpaceManagerService addressSpaceMgr;
    protected IOFSwitch sw1, sw2, sw3;                        // swithes for use in multi-action packet out for netVirt-broadcast  
    protected Capture<OFMessage> wc1, wc2, wc3;         // Capture writes to switches
    protected Capture<ListenerContext> fc1, fc2, fc3;  // Capture writes to switches
    protected IDevice srcDevice;
    protected IDevice mpathDstDevice1, mpathDstDevice2, mpathDstDevice3, mpathDstDevice4;
    protected ArrayList<IDevice> dstDevices;
    protected OFPacketIn mpathPacketIn1, mpathPacketIn2, mpathPacketIn3, mpathPacketIn4;
    protected OFPacketOut mpathPacketOut1, mpathPacketOut2, mpathPacketOut3, mpathPacketOut4;
    protected IPacket testMpathPacket1, testMpathPacket2, testMpathPacket3, testMpathPacket4;
    protected byte[] testMpathPacket1Serialized, testMpathPacket2Serialized, testMpathPacket3Serialized, testMpathPacket4Serialized;
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
        topology = new BetterTopologyManager();
        tunnelManager = createMock(ITunnelManagerService.class);
        flowReconcileMgr = createMock(IFlowReconcileService.class);
        rewriteService = createNiceMock(IRewriteService.class);
        restApi = createNiceMock(IRestApiService.class);
        addressSpaceMgr = createMock(IAddressSpaceManagerService.class);
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        linkDiscovery = EasyMock.createMock(ILinkDiscoveryService.class);
        
        BetterFlowCache betterFlowCacheMgr = new BetterFlowCache();
        betterFlowCacheMgr.setAppName("netVirt");
        
        ModuleContext fmc = new ModuleContext();
        fmc.addService(IControllerService.class, 
                       mockControllerProvider);
        fmc.addService(ITopologyService.class, topology);
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
        fmc.addService(ILinkDiscoveryService.class, linkDiscovery);
        fmc.addService(IStorageSourceService.class, new MemoryStorageSource());
        
        topology.init(fmc);
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
                .andReturn(true).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(true).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong())).andReturn(null).anyTimes();
        replay(tunnelManager);
        
        int [][] linkArray = {
                              {1, 3, 2, 1, DIRECT_LINK},
                              {1, 4, 2, 2, DIRECT_LINK},
                              {2, 3, 3, 1, DIRECT_LINK},
                              {2, 4, 3, 2, DIRECT_LINK}
        };

        expect(linkDiscovery.isTunnelPort(1L, (short)1)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(1L, (short)3)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(1L, (short)4)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(2L, (short)1)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(2L, (short)2)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(2L, (short)3)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(2L, (short)4)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(3L, (short)1)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(3L, (short)2)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(3L, (short)3)).andReturn(false).anyTimes();
        expect(linkDiscovery.isTunnelPort(3L, (short)4)).andReturn(false).anyTimes();
        replay(linkDiscovery);
        
        ILinkDiscovery.LinkType type = ILinkDiscovery.LinkType.DIRECT_LINK;

        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            if (r[4] == DIRECT_LINK)
                type= ILinkDiscovery.LinkType.DIRECT_LINK;
            else if (r[4] == MULTIHOP_LINK)
                type= ILinkDiscovery.LinkType.MULTIHOP_LINK;
            else if (r[4] == TUNNEL_LINK)
                type = ILinkDiscovery.LinkType.TUNNEL;

            topology.addOrUpdateLink(r[0], (short)r[1], r[2], (short)r[3], type);
        }
        
        topology.createNewInstance();
        
        topology.updateTopology();
        
        // Mock switches
        sw1 = EasyMock.createMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        expect(sw1.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                .andReturn(false).anyTimes();

        sw2 = EasyMock.createMock(IOFSwitch.class);  
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.getStringId()).andReturn("00:00:00:00:00:00:00:02").anyTimes();
        expect(sw2.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                .andReturn(false).anyTimes();

        sw3 = EasyMock.createMock(IOFSwitch.class);  
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.getStringId()).andReturn("00:00:00:00:00:00:00:03").anyTimes();
        expect(sw3.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                .andReturn(false).anyTimes();
        
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
        expect(sw1.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();
        expect(sw1.portEnabled(EasyMock.anyShort())).andReturn(true).anyTimes();
        
        expect(sw2.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(true).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();
        expect(sw2.portEnabled(EasyMock.anyShort())).andReturn(true).anyTimes();
        
        expect(sw3.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(fastWildcards).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)).andReturn(true).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)).andReturn(true).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw3.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)).andReturn(true).anyTimes();
        expect(sw3.portEnabled(EasyMock.anyShort())).andReturn(true).anyTimes();
        
        sw1.write(capture(wc1), capture(fc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2), capture(fc2));
        expectLastCall().anyTimes();
        sw3.write(capture(wc3), capture(fc3));
        expectLastCall().anyTimes();

        replay(sw1, sw2, sw3);

        // Load the switch map
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        mockControllerProvider.setSwitches(switches);

        // Build test packets for multipath tests
        testMpathPacket1 = new Ethernet()
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
        testMpathPacket1Serialized = testMpathPacket1.serialize();

        testMpathPacket2 = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:56")
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
        testMpathPacket2Serialized = testMpathPacket2.serialize();

        testMpathPacket3 = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:57")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.4")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testMpathPacket3Serialized = testMpathPacket3.serialize();

        testMpathPacket4 = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:59")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.5")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testMpathPacket4Serialized = testMpathPacket4.serialize();

        // Build multipath test src and dest devices
        byte[] mpathDataLayerSource = ((Ethernet)testMpathPacket1).getSourceMACAddress();
        int mpathNetworkSource = ((IPv4)((Ethernet)testMpathPacket1).getPayload()).getSourceAddress();
        
        byte[] mpathDataLayerDest1 = ((Ethernet)testMpathPacket1).getDestinationMACAddress();
        int mpathNetworkDest1 = ((IPv4)((Ethernet)testMpathPacket1).getPayload()).getDestinationAddress();

        byte[] mpathDataLayerDest2 = ((Ethernet)testMpathPacket2).getDestinationMACAddress();
        int mpathNetworkDest2 = ((IPv4)((Ethernet)testMpathPacket2).getPayload()).getDestinationAddress();

        byte[] mpathDataLayerDest3 = ((Ethernet)testMpathPacket3).getDestinationMACAddress();
        int mpathNetworkDest3 = ((IPv4)((Ethernet)testMpathPacket3).getPayload()).getDestinationAddress();

        byte[] mpathDataLayerDest4 = ((Ethernet)testMpathPacket4).getDestinationMACAddress();
        int mpathNetworkDest4 = ((IPv4)((Ethernet)testMpathPacket4).getPayload()).getDestinationAddress();

        srcDevice = 
                deviceManager.learnEntity(Ethernet.toLong(mpathDataLayerSource), 
                                          null, mpathNetworkSource,
                                          1L, 1);
        mpathDstDevice1 = 
                deviceManager.learnEntity(Ethernet.toLong(mpathDataLayerDest1), 
                                          null, mpathNetworkDest1,
                                          2L, 3);
        mpathDstDevice2 = 
                deviceManager.learnEntity(Ethernet.toLong(mpathDataLayerDest2), 
                                          null, mpathNetworkDest2,
                                          2L, 4);
        mpathDstDevice3 = 
                deviceManager.learnEntity(Ethernet.toLong(mpathDataLayerDest3), 
                                          null, mpathNetworkDest3,
                                          3L, 3);
        mpathDstDevice4 = 
                deviceManager.learnEntity(Ethernet.toLong(mpathDataLayerDest4), 
                                          null, mpathNetworkDest4,
                                          3L, 4);
        
        // Mock multipath PacketIns
        mpathPacketIn1 = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testMpathPacket1Serialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testMpathPacket1Serialized.length);

        mpathPacketIn2 = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testMpathPacket2Serialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testMpathPacket2Serialized.length);

        mpathPacketIn3 = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testMpathPacket3Serialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testMpathPacket3Serialized.length);

        mpathPacketIn4 = ((OFPacketIn) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testMpathPacket4Serialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testMpathPacket4Serialized.length);

        // Mock multipath PacketOuts
        mpathPacketOut1 =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        mpathPacketOut1.setBufferId(this.mpathPacketIn1.getBufferId())
        .setInPort(this.mpathPacketIn1.getInPort());
        List<OFAction> poactions1 = new ArrayList<OFAction>();
        poactions1.add(new OFActionOutput((short)3, (short) 0xffff));
        mpathPacketOut1.setActions(poactions1)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testMpathPacket1Serialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+mpathPacketOut1.getActionsLength()+testMpathPacket1Serialized.length);

        mpathPacketOut2 =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        mpathPacketOut2.setBufferId(this.mpathPacketIn2.getBufferId())
        .setInPort(this.mpathPacketIn2.getInPort());
        List<OFAction> poactions2 = new ArrayList<OFAction>();
        poactions2.add(new OFActionOutput((short)4, (short) 0xffff));
        mpathPacketOut2.setActions(poactions2)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testMpathPacket2Serialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+mpathPacketOut2.getActionsLength()+testMpathPacket2Serialized.length);

        mpathPacketOut3 =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        mpathPacketOut3.setBufferId(this.mpathPacketIn3.getBufferId())
        .setInPort(this.mpathPacketIn3.getInPort());
        List<OFAction> poactions3 = new ArrayList<OFAction>();
        poactions3.add(new OFActionOutput((short)3, (short) 0xffff));
        mpathPacketOut3.setActions(poactions3)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testMpathPacket3Serialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+mpathPacketOut3.getActionsLength()+testMpathPacket3Serialized.length);

        mpathPacketOut4 =
                (OFPacketOut) mockControllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        mpathPacketOut4.setBufferId(this.mpathPacketIn4.getBufferId())
        .setInPort(this.mpathPacketIn4.getInPort());
        List<OFAction> poactions4 = new ArrayList<OFAction>();
        poactions4.add(new OFActionOutput((short)4, (short) 0xffff));
        mpathPacketOut4.setActions(poactions2)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testMpathPacket4Serialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+mpathPacketOut4.getActionsLength()+testMpathPacket4Serialized.length);

        expected_wildcards = fastWildcards;
        expected_wildcards &= ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN &
                ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST;
        expected_wildcards &= ~OFMatch.OFPFW_DL_TYPE & ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK;

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
    
    @Test
    public void testMultipathDualLinkBetweenTwoSwitches() throws Exception {
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination and Mock route        
        dstDevices.add(mpathDstDevice1);

        // Expected Flow-mods
        // first packet in to mpathDstDevice1
        OFMatch match = new OFMatch();
        match.loadFromPacket(testMpathPacket1Serialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1a = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1a.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
       
        OFFlowMod fm2a = fm1a.clone();

        fm1a.setFlags((short)1); // set flow-mod-removal flag on src switch only

        // second packet in to mpathDstDevice2
        match = new OFMatch();
        match.loadFromPacket(testMpathPacket2Serialized, (short) 1);
        action = new OFActionOutput((short)4, (short)0xffff);
        actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1b = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1b.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
       
        OFFlowMod fm2b = fm1b.clone();
        fm2b.getMatch().setInputPort((short) 2);

        fm1b.setFlags((short)1); // set flow-mod-removal flag on src switch only
     
        long cookie1 = forwarding.getHashByMac(mpathDstDevice1.getMACAddress());
        RouteId routeId1 = new RouteId(1L, 2L, cookie1);
        Route route1 = new Route(routeId1, new ArrayList<NodePortTuple>());
        route1.getPath().add(new NodePortTuple(1L, (short)1));
        route1.getPath().add(new NodePortTuple(1L, (short)3));
        route1.getPath().add(new NodePortTuple(2L, (short)1));
        route1.getPath().add(new NodePortTuple(2L, (short)3));        
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)3, cookie1, false)).andReturn(route1).anyTimes();

        long cookie2 = forwarding.getHashByMac(mpathDstDevice2.getMACAddress());
        RouteId routeId2 = new RouteId(1L, 2L, cookie2);
        Route route2 = new Route(routeId2, new ArrayList<NodePortTuple>());
        route2.getPath().add(new NodePortTuple(1L, (short)1));
        route2.getPath().add(new NodePortTuple(1L, (short)4));
        route2.getPath().add(new NodePortTuple(2L, (short)2));
        route2.getPath().add(new NodePortTuple(2L, (short)4));
        expect(routingEngine.getRoute(1L, (short)1, 2L, (short)4, cookie2, false)).andReturn(route2).anyTimes();

        replay(routingEngine, decision);
        forwarding.receive(sw1, this.mpathPacketIn1, cntx);
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(linkDiscovery);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m1: msglist) {
            if (m1 instanceof OFPacketOut)
                assertEquals(m1, mpathPacketOut1);                
            else if (m1 instanceof OFFlowMod) 
                assertEquals(m1, fm1a);
        }
        

        OFMessage m1 = wc2.getValue();
        assert (m1 instanceof OFFlowMod);
        assertTrue(m1.equals(fm2a)); 

        wc1.reset(); wc2.reset();
        
        // Set destination and Mock route
        dstDevices.clear();
        dstDevices.add(mpathDstDevice2);
        
        forwarding.receive(sw1, this.mpathPacketIn2, cntx);
        verify(sw1);
        verify(sw2);
        verify(routingEngine);
        verify(decision);
        verify(linkDiscovery);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

        msglist = wc1.getValues();

        for (OFMessage m2: msglist) {
            if (m2 instanceof OFPacketOut)
                assertEquals(m2, mpathPacketOut2);                
            else if (m2 instanceof OFFlowMod) 
                assertEquals(m2, fm1b);
        }

        OFMessage m2 = wc2.getValue();
        assert (m2 instanceof OFFlowMod);
        assertEquals(m2, fm2b); 
    }

    @Test
    public void testMultipathDualLinkBetweenThreeSwitches() throws Exception {
        // Mock decision
        expect(decision.getRoutingAction()).andReturn(IRoutingDecision.RoutingAction.FORWARD).atLeastOnce();
        expect(decision.getWildcards()).andReturn(null).atLeastOnce();
        expect(decision.getHardTimeout()).
        andReturn(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT).atLeastOnce();

        // Set destination and Mock route        
        dstDevices.add(mpathDstDevice3);

        // Expected Flow-mods
        // first packet in to mpathDstDevice3
        OFMatch match = new OFMatch();
        match.loadFromPacket(testMpathPacket3Serialized, (short) 1);
        OFActionOutput action = new OFActionOutput((short)3, (short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1a = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1a.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
       
        OFFlowMod fm2a = fm1a.clone();

        OFFlowMod fm3a = fm1a.clone();

        fm1a.setFlags((short)1); // set flow-mod-removal flag on src switch only

        // second packet in to mpathDstDevice2
        match = new OFMatch();
        match.loadFromPacket(testMpathPacket4Serialized, (short) 1);
        action = new OFActionOutput((short)4, (short)0xffff);
        actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1b = (OFFlowMod) mockControllerProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm1b.setIdleTimeout((short)5)
        .setPriority(forwarding.getAccessPriority())
        .setMatch(match.clone()
                .setWildcards(expected_wildcards))
                .setActions(actions)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(2L << 52)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
       
        OFFlowMod fm2b = fm1b.clone();
        fm2b.getMatch().setInputPort((short) 2);

        OFFlowMod fm3b = fm1b.clone();
        fm3b.getMatch().setInputPort((short) 2);

        fm1b.setFlags((short)1); // set flow-mod-removal flag on src switch only
     
        long cookie1 = forwarding.getHashByMac(mpathDstDevice3.getMACAddress());
        RouteId routeId1 = new RouteId(1L, 3L, cookie1);
        Route route1 = new Route(routeId1, new ArrayList<NodePortTuple>());
        route1.getPath().add(new NodePortTuple(1L, (short)1));
        route1.getPath().add(new NodePortTuple(1L, (short)3));
        route1.getPath().add(new NodePortTuple(2L, (short)1));
        route1.getPath().add(new NodePortTuple(2L, (short)3));        
        route1.getPath().add(new NodePortTuple(3L, (short)1));
        route1.getPath().add(new NodePortTuple(3L, (short)3));
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)3, cookie1, false)).andReturn(route1).anyTimes();

        long cookie2 = forwarding.getHashByMac(mpathDstDevice4.getMACAddress());
        RouteId routeId2 = new RouteId(1L, 3L, cookie2);
        Route route2 = new Route(routeId2, new ArrayList<NodePortTuple>());
        route2.getPath().add(new NodePortTuple(1L, (short)1));
        route2.getPath().add(new NodePortTuple(1L, (short)4));
        route2.getPath().add(new NodePortTuple(2L, (short)2));
        route2.getPath().add(new NodePortTuple(2L, (short)4));
        route2.getPath().add(new NodePortTuple(3L, (short)2));
        route2.getPath().add(new NodePortTuple(3L, (short)4));
        expect(routingEngine.getRoute(1L, (short)1, 3L, (short)4, cookie2, false)).andReturn(route2).anyTimes();

        replay(routingEngine, decision);
        forwarding.receive(sw1, this.mpathPacketIn3, cntx);
        verify(sw1);
        verify(sw2);
        verify(sw3);
        verify(routingEngine);
        verify(decision);
        verify(linkDiscovery);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.
        assertTrue(wc3.hasCaptured());  // wc3 should be a flowmod.

        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m1: msglist) {
            if (m1 instanceof OFPacketOut)
                assertEquals(m1, mpathPacketOut3);                
            else if (m1 instanceof OFFlowMod) 
                assertEquals(m1, fm1a);
        }
        
        OFMessage m1 = wc2.getValue();
        assert (m1 instanceof OFFlowMod);
        assertTrue(m1.equals(fm2a)); 

        m1 = wc3.getValue();
        assert (m1 instanceof OFFlowMod);
        assertTrue(m1.equals(fm3a)); 

        wc1.reset(); wc2.reset(); wc3.reset();
        
        // Set destination and Mock route
        dstDevices.clear();
        dstDevices.add(mpathDstDevice4);
        
        forwarding.receive(sw1, this.mpathPacketIn4, cntx);
        verify(sw1);
        verify(sw2);
        verify(sw3);
        verify(routingEngine);
        verify(decision);
        verify(linkDiscovery);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.
        assertTrue(wc3.hasCaptured());  // wc2 should be a flowmod.

        msglist = wc1.getValues();

        for (OFMessage m2: msglist) {
            if (m2 instanceof OFPacketOut)
                assertEquals(m2, mpathPacketOut4);                
            else if (m2 instanceof OFFlowMod) 
                assertEquals(m2, fm1b);
        }

        OFMessage m2 = wc2.getValue();
        assert (m2 instanceof OFFlowMod);
        assertEquals(m2, fm2b); 

        m2 = wc3.getValue();
        assert (m2 instanceof OFFlowMod);
        assertEquals(m2, fm3b); 
    }
}
