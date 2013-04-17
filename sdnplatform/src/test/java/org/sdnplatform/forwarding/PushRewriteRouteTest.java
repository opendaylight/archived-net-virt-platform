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
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
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
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.util.HexString;
import org.openflow.util.U8;
import org.sdnplatform.IBetterOFSwitch;
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
import org.sdnplatform.devicemanager.internal.Device;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.forwarding.Forwarding;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.routing.RouteId;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyListener;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.util.OFMessageDamper;
import org.sdnplatform.vendor.OFActionNiciraTtlDecrement;


public class PushRewriteRouteTest {
    /*
     * This class tests Forwarding's pushRewriteRoute. We use the following
     * basic topology with 4 swiches connected in a "line"
     *  
     *  Switch0 0x10: in port  1,  out port  2
     *  link0
     *  Switch1 0x11: in port 11,  out port 12
     *  link1 
     *  Switch2 0x12: in port 21,  out port 22
     *  link2
     *  Switch3 0x13: in port 31,  out port 32
     *  
     * I.e., ingress port is 1, egress port is 32 
     * 
     * It follows that we have 3 links between the switches. These can be 
     * internal or external links. If we add the input and output port we 
     * have 5 "zones". Let's call them: ingress, link0, link1, link2, egress.
     * 
     * Each zone has a ZoneVlanMode associated with it. This indicates if we
     * would expect our test packet to be TAGGED or UNTAGGED in this zone. 
     * We'll use zones and their VlanMode to help us automate test verification
     * 
     * Our parameter space is as follows:
     * - tagged/untagged ingress
     * - tagged/untagged egress
     * - MAC rewrite
     * - PacketIn switch (sw 0--3 and a switch not on the route)
     * - Link-type:
     *   + internal (always tagged)
     *   + external untagged (transport VLAN is ports native Vlan)
     *   + external tagged (transport VLAN is not native)
     * 
     * TODO: now that we have TestConfig, the verify* and setup* should just
     * take a TestConfig as parameter....
     */
    protected enum ZoneVlanMode { TAGGED, UNTAGGED };
    protected enum LinkType { INT, EXT_TAGGED, EXT_UNTAGGED };
    protected MockControllerProvider mockControllerProvider;
    protected ListenerContext cntx;
    protected MockThreadPoolService threadPool;
    protected MockDeviceManager deviceManager;
    protected IRoutingService routingEngine;
    protected ITopologyService topology;
    protected ITunnelManagerService tunnelManager;
    protected RestApiServer restApi;
    protected Forwarding forwarding;
    protected IFlowReconcileService flowReconcileMgr;
    protected IFlowCacheService flowCacheService;
    protected Capture<OFMatchWithSwDpid> flowCacheOfmCapture;
    protected DefaultEntityClassifier entityClassifier;
    protected IRewriteService rewriteService;
    protected IOFSwitch[] switches;                   // swithes for use in multi-action packet out for netVirt-broadcast  
    protected Capture<OFMessage>[] writeCaptures;   // Capture writes to switches
    protected Capture<ListenerContext>[] cntxCaptures;  // Capture writes to switches
    
    protected IDevice srcDevice, dstDevice;
    
    protected OFPacketIn packetIn;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    protected Long packetSrcMac;
    protected Long packetDstMac;
    
    protected static long cookie = 0x42a23a42;
    
    protected String curTestId;
    
    protected int expected_wildcards;
    protected Short vlan;
    private int networkSource;
    private int networkDest;
    private short initialPacketTtl;
    
    
    @SuppressWarnings("unchecked")
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
        tunnelManager = createMock(ITunnelManagerService.class);
        flowReconcileMgr = createMock(IFlowReconcileService.class);
        rewriteService = createMock(IRewriteService.class);
        entityClassifier = new DefaultEntityClassifier();
        restApi = new RestApiServer();
        
        flowCacheService = createMock(IFlowCacheService.class);
        flowCacheOfmCapture = new Capture<OFMatchWithSwDpid>(CaptureType.ALL);
        

        ModuleContext fmc = new ModuleContext();
        fmc.addService(IControllerService.class, 
                       mockControllerProvider);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IThreadPoolService.class, threadPool);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(ICounterStoreService.class, new CounterStore());
        fmc.addService(IDeviceService.class, deviceManager);
        fmc.addService(IFlowCacheService.class, flowCacheService);
        fmc.addService(ITunnelManagerService.class, tunnelManager);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IRewriteService.class, rewriteService);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IStorageSourceService.class, new MemoryStorageSource());
        
        deviceManager.init(fmc);
        threadPool.init(fmc);
        forwarding.init(fmc);
        forwarding.setMessageDamper(
                new OFMessageDamper(1, EnumSet.noneOf(OFType.class), 0));
        entityClassifier.init(fmc);
        restApi.init(fmc);
        threadPool.startUp(fmc);
        deviceManager.startUp(fmc);
        forwarding.startUp(fmc);
        entityClassifier.startUp(fmc);
        restApi.startUp(fmc);

        // Mock tunnel service
        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                .andReturn(false).anyTimes();
        expect(tunnelManager.isTunnelEndpoint(null)).andReturn(false).anyTimes();
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();
        expect(tunnelManager.getTunnelLoopbackPort(EasyMock.anyLong())).andReturn(null).anyTimes();
        replay(tunnelManager);

        IFlowCacheService.fcStore.put(cntx, IFlowCacheService.FLOWCACHE_APP_NAME, "netVirt");
        IFlowCacheService.fcStore.put(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, "netVirt1");

        vlan = 23;

        // Load the switch map
        Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
        switches = new IOFSwitch[4];
        writeCaptures = new Capture[4];
        cntxCaptures = new Capture[4];
        for (int i=0; i<4; i++) {
            switches[i] = createMock(IOFSwitch.class);
            switchMap.put(16L+i, switches[i]);
            writeCaptures[i] = new Capture<OFMessage>(CaptureType.ALL);
            cntxCaptures[i] = new Capture<ListenerContext>(CaptureType.ALL);
        }
        mockControllerProvider.setSwitches(switchMap);

        // Build test packet
        initialPacketTtl = (short)128;
        testPacket = new Ethernet()
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setSourceMACAddress("00:44:33:22:11:00")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl(U8.t(initialPacketTtl))
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        testPacketSerialized = testPacket.serialize();


        // Build src and dest devices
        packetSrcMac = Ethernet.toLong(((Ethernet)testPacket)
                                       .getSourceMACAddress());
        packetDstMac = Ethernet.toLong(((Ethernet)testPacket)
                                       .getDestinationMACAddress());
        networkSource = 
                ((IPv4)((Ethernet)testPacket).getPayload())
                .getSourceAddress();
        networkDest = 
                ((IPv4)((Ethernet)testPacket).getPayload())
                .getDestinationAddress();


        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        topology.addListener(anyObject(ITopologyListener.class));
        expectLastCall().anyTimes();
        expect(topology.getL2DomainId(anyLong())).andReturn(0x10L).anyTimes();
        replay(topology);

        srcDevice = 
                deviceManager.learnEntity(packetSrcMac,
                                          null, networkSource,
                                          0x10L, 1);
        dstDevice = 
                deviceManager.learnEntity(packetDstMac,
                                          null, networkDest,
                                          0x13L, 32);
                
        // Mock Packet-in
        packetIn = ((OFPacketIn) mockControllerProvider
                .getOFMessageFactory()
                .getMessage(OFType.PACKET_IN))
                .setBufferId(0x42)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        int expected_match_fields = OFMatch.OFPFW_DL_SRC 
                                    | OFMatch.OFPFW_DL_DST
                                    | OFMatch.OFPFW_DL_VLAN
                                    | OFMatch.OFPFW_IN_PORT
                                    | OFMatch.OFPFW_DL_TYPE
                                    | OFMatch.OFPFW_NW_SRC_MASK
                                    | OFMatch.OFPFW_NW_DST_MASK;
        expected_wildcards = OFMatch.OFPFW_ALL & ~expected_match_fields;
        
        curTestId = "<foo>";
    }
    
    @After
    public void tearDown() {
        verify(tunnelManager);
    }
    
    /* change the global packet in to make it tagged with the global vlan */
    protected void tagPacketIn() {
        Ethernet eth = (Ethernet)testPacket;
        eth.setVlanID(vlan);
        testPacketSerialized = testPacket.serialize();

        srcDevice = 
                deviceManager.learnEntity(packetSrcMac,
                                          vlan, null,
                                          0x10L, 1);
        packetIn.setPacketData(testPacketSerialized);
        packetIn.setTotalLength((short) testPacketSerialized.length);
    }

    /* non-javadoc
     * 
     * Resets all mock switches and sets their expectations. Also sets
     * up captures for sw.write()
     * @param doReplay if true replay() will be called on each switch
     * @throws Exception
     */
    protected void setupMockSwitches(boolean doFlush, 
                                     boolean doReplay,
                                     boolean expectPacketIgnored) throws Exception {
        //fastWilcards mocked as this constant
        int fastWildcards = OFMatch.OFPFW_ALL; 
        
        long clusterId = 16L;
        for (int i=0; i<switches.length; i++) {
            IOFSwitch sw = switches[i];
            reset(sw);
            long swId = 16+i; // also change the switchMap in setUp if you change here
            String strId = HexString.toHexString(swId);
            
            expect(sw.getId()).andReturn(swId).anyTimes();
            expect(sw.getStringId()).andReturn(strId).anyTimes();
            expect(topology.getL2DomainId(swId)).andReturn(clusterId).anyTimes();
            if (i == 0) {
                // first hop switch
                expect(topology.isAttachmentPointPort(swId, (short)1))
                        .andReturn(true).anyTimes();
                expect(topology.isInSameBroadcastDomain(swId, 
                                                        (short)1,
                                                        swId,
                                                        (short)1,
                                                        false))
                        .andReturn(true).anyTimes();
            }
            
            
            if (! expectPacketIgnored) {
                if (doFlush) {
                    sw.flush();
                    expectLastCall().atLeastOnce();
                }
            }
            
            // Setup switch properties 
            expect(sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS))
                    .andReturn(fastWildcards).anyTimes();
            expect(sw.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS))
                    .andReturn(true).anyTimes();
            expect(sw.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH))
                    .andReturn(false).anyTimes();
            expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true))
                    .andReturn(true).anyTimes();
            
            sw.write(capture(writeCaptures[i]), capture(cntxCaptures[i]));
            expectLastCall().anyTimes();
            if (doReplay)
                replay(sw);
        }
    }
    
    /* Reset the IOFSwitch.write() captures */
    protected void resetCaptures() {
        flowCacheOfmCapture.reset();
        for (int i=0; i<writeCaptures.length; i++) {
            writeCaptures[i].reset();
            cntxCaptures[i].reset();
        }
    }
    
    /* reset expectations on all mocks */
    protected void resetAllMocks() {
        resetCaptures();
        reset(topology, rewriteService, flowCacheService);
        for (IOFSwitch sw: switches)
            reset(sw);
    }
    
    /* non-javadoc
     * Creates new easyMock'ed IRoutingDecision 
     * @param doReplay if true will call replay on the IRoutingDecision
     * @return
     */
    protected IRoutingDecision getMockRoutingDecision(SwitchPort packetInSwp,
                                                      boolean doReplay) {
        IRoutingDecision d = createMock(IRoutingDecision.class);
        
        ArrayList<IDevice> dstDeviceList = new ArrayList<IDevice>(1);
        dstDeviceList.add(dstDevice);
        expect(d.getSourceDevice()).andReturn(srcDevice).anyTimes();
        expect(d.getDestinationDevices()).andReturn(dstDeviceList).anyTimes();
        expect(d.getWildcards()).andReturn(expected_wildcards).anyTimes();
        expect(d.getSourcePort()).andReturn(packetInSwp).anyTimes();
        
        if (doReplay)
            replay(d);
        return d;
    }
    
    /* Returns the route for our sample topology */
    protected Route getTestRoute() {
        RouteId rid = new RouteId(packetSrcMac, packetDstMac);
        ArrayList<NodePortTuple> switchPorts = new ArrayList<NodePortTuple>();
        
        assertEquals(4, switches.length); // make sure nobody changes the number
                                      // of switches without changing the rte
        /* We have 4 switches on the route and thus 3 links */
        switchPorts.add(new NodePortTuple(0x10L, (short)1));
        switchPorts.add(new NodePortTuple(0x10L, (short)2));
        // link 0 is here
        switchPorts.add(new NodePortTuple(0x11L, (short)11));
        switchPorts.add(new NodePortTuple(0x11L, (short)12));
        // link 1 is here
        switchPorts.add(new NodePortTuple(0x12L, (short)21));
        switchPorts.add(new NodePortTuple(0x12L, (short)22));
        // link2 is here
        switchPorts.add(new NodePortTuple(0x13L, (short)31));
        switchPorts.add(new NodePortTuple(0x13L, (short)32));
        
        return new Route(rid, switchPorts);
    }
    
    
    protected enum SwitchPortDirection { INPUT, OUTPUT };
    
    /* Given the idx of one of the switches in our topology and a port
     * direction will return the appropriate SwitchPort */
    protected SwitchPort getSwitchPort(int swIdx, SwitchPortDirection dir) {
        if (dir == SwitchPortDirection.INPUT)
            return new SwitchPort(16 + swIdx, 10*swIdx + 1);
        else
            return new SwitchPort(16 + swIdx, 10*swIdx + 2);
    }
   
    
    /* Sets the topology and rewriteService mock expectations for the given
     * SwitchPort and transportVlan for a port that connects to an 
     * "external" link were we wan the vlan to be tagged"
     */
    protected void setExpectExternalTagged(SwitchPort swp, 
                                           Short transportVlan) {
        long dpid = swp.getSwitchDPID();
        short port = (short) swp.getPort();
        expect(topology.isAttachmentPointPort(eq(dpid), eq(port), anyBoolean()))
                .andReturn(true).anyTimes();
        expect(rewriteService.getSwitchPortVlanMode(eq(swp), 
                                                    anyObject(String.class),
                                                    anyShort(),
                                                    anyBoolean()))
                .andReturn(transportVlan).anyTimes();
    }
    
    
    /* Sets the topology and rewriteService mock expectations for the given
     * SwitchPort and transportVlan for a port that connects to an 
     * "external" link were we wan the vlan to be untagged/native"
     */
    protected void setExpectExternalUntagged(SwitchPort swp,
                                             Short transportVlan) {
        long dpid = swp.getSwitchDPID();
        short port = (short) swp.getPort();
        expect(topology.isAttachmentPointPort(eq(dpid), eq(port), anyBoolean()))
                .andReturn(true).anyTimes();
        expect(rewriteService.getSwitchPortVlanMode(eq(swp), 
                                                    anyObject(String.class),
                                                    anyShort(),
                                                    anyBoolean()))
                .andReturn(Ethernet.VLAN_UNTAGGED).anyTimes();
        
    }
    
    
    /* Sets the topology and rewriteService mock expectations for the given
     * SwitchPort and transportVlan for a port that connects to an 
     * "internal" link
     */
    protected void setExpectInternal(SwitchPort swp,
                                     Short transportVlan) {
        long dpid = swp.getSwitchDPID();
        short port = (short) swp.getPort();
        expect(topology.isAttachmentPointPort(eq(dpid), eq(port), anyBoolean()))
                .andReturn(false).anyTimes();
        expect(rewriteService.getSwitchPortVlanMode(eq(swp), 
                                                    anyObject(String.class),
                                                    anyShort(),
                                                    anyBoolean()))
                .andReturn(transportVlan).anyTimes();
    }
    
    /* Set the expectations for rewriteService and topology assuming the 
     * given transportVlan, Mac rewrite config, ZoneVlanModes and linkTypes. 
     */
    protected void setupZoneExpectations(Short transportVlan,
                                         Long origDstMac,
                                         Long finalDstMac,
                                         Long origSrcMac,
                                         Long finalSrcMac,
                                         Integer ttlDecrement,
                                         ZoneVlanMode[] vlanModes,
                                         LinkType[] linkTypes) {
        expect(rewriteService.getTransportVlan(cntx))
                .andReturn(transportVlan).anyTimes();
        expect(rewriteService.getOrigIngressDstMac(cntx))
                .andReturn(origDstMac).anyTimes();
        expect(rewriteService.getFinalIngressDstMac(cntx))
                .andReturn(finalDstMac).anyTimes();
        expect(rewriteService.getOrigEgressSrcMac(cntx))
                .andReturn(origSrcMac).anyTimes();
        expect(rewriteService.getFinalEgressSrcMac(cntx))
                .andReturn(finalSrcMac).anyTimes();
        expect(rewriteService.getTtlDecrement(cntx))
                .andReturn(ttlDecrement).anyTimes();
        
        // Make sure we have the right number of zones / links 
        assertEquals("inconsistent test setup", 5, vlanModes.length);
        assertEquals("inconsistent test setup", 3, linkTypes.length);
        
        // setup expectations for links
        for (int i=0; i < linkTypes.length; i++) {
            SwitchPort srcSwp = getSwitchPort(i, SwitchPortDirection.OUTPUT);
            SwitchPort dstSwp = getSwitchPort(i+1, SwitchPortDirection.INPUT);
            if (linkTypes[i] == LinkType.INT) {
                setExpectInternal(srcSwp, transportVlan);
                setExpectInternal(dstSwp, transportVlan);
                // link i <==> zone i+1 
                assertEquals("inconsistent test setup link " + i,
                             ZoneVlanMode.TAGGED, vlanModes[i+1]);
            } else if (linkTypes[i] == LinkType.EXT_TAGGED) {
                setExpectExternalTagged(srcSwp, transportVlan);
                setExpectExternalTagged(dstSwp, transportVlan);
                // link i <==> zone i+1 
                assertEquals("inconsistent test setup link " + i,
                             ZoneVlanMode.TAGGED, vlanModes[i+1]);
            } else {
                setExpectExternalUntagged(srcSwp, transportVlan);
                setExpectExternalUntagged(dstSwp, transportVlan);
                // link i <==> zone i+1 
                assertEquals("inconsistent test setup link " + i,
                             ZoneVlanMode.UNTAGGED, vlanModes[i+1]);
            }
        }
        // setup expectation for egress port
        SwitchPort egressSwp = getSwitchPort(3, SwitchPortDirection.OUTPUT);
        if (vlanModes[4] == ZoneVlanMode.TAGGED) 
            setExpectExternalTagged(egressSwp, transportVlan);
        else
            setExpectExternalUntagged(egressSwp, transportVlan);
        // no-op for ingress port
    }
    
    /*
     * Set the expectations for flowCacheService
     */
    protected void setupFlowCacheExpectations(boolean expectPacketIgnored) {
        // The switch/port will always be for the ingress port on the first
        // hop switch
        if (expectPacketIgnored)
            return;
        flowCacheService.addFlow(eq(cntx), 
                                capture(flowCacheOfmCapture), 
                                eq(forwarding.appCookie),
                                eq(new SwitchPort(0x10L, (short)1)),
                                EasyMock.anyShort(),
                                eq(FlowCacheObj.FCActionPERMIT));
        expectLastCall().andReturn(true).once();
    }
    
    
    /*
     * Verify that the given OFActions of the given FlowMod are consistent
     * with the given config
     */
    protected void verifyFlodModActions(OFFlowMod fm,
                                        int outPort,
                                        Short transportVlan,
                                        Long origDstMac,
                                        Long finalDstMac,
                                        Long origSrcMac,
                                        Long finalSrcMac,
                                        Integer ttlDecrement,
                                        ZoneVlanMode inVlanMode,
                                        ZoneVlanMode outVlanMode)
    {
        List<OFAction> actions = new ArrayList<OFAction>(fm.getActions());
        OFAction a;
        
        // output action must be the last action in the array
        a = new OFActionOutput((short)outPort);
        assertEquals(curTestId, true, actions.size()>0);
        assertEquals(curTestId, a, actions.get(actions.size()-1));
        actions.remove(a);
        
        // check that dstMac rewrite action is present if necessary
        if (origDstMac != null && !origDstMac.equals(finalDstMac)) {
            a = new OFActionDataLayerDestination(
                    Ethernet.toByteArray(finalDstMac));
            assertEquals(curTestId, true, actions.remove(a));
        }
        
        // check that srcMac rewrite action is present if necessary
        if (origSrcMac != null && !origSrcMac.equals(finalSrcMac)) {
            a = new OFActionDataLayerSource(Ethernet.toByteArray(finalSrcMac));
            assertEquals(curTestId, true, actions.remove(a));
        }
        
        // check that TTL decrement is present
        if (ttlDecrement != null) {
            a = new OFActionNiciraTtlDecrement();
            assertEquals(curTestId, true, actions.remove(a));
        }
        
        // check vlan rewrite action 
        if (inVlanMode == ZoneVlanMode.TAGGED 
                && outVlanMode == ZoneVlanMode.UNTAGGED) {
            a = new OFActionStripVirtualLan();
            assertEquals(curTestId, true, actions.remove(a));
        } 
        else if (inVlanMode == ZoneVlanMode.UNTAGGED 
                    && outVlanMode == ZoneVlanMode.TAGGED) {
            a = new OFActionVirtualLanIdentifier(transportVlan);
            assertEquals(curTestId, true, actions.remove(a));
        } 
        else {
            // no vlan change ==> no action
        }
        
        // no more actions should be present
        assertEquals(curTestId, 0, actions.size());
    }
    
    
    /*
     * Verify that the given FlodMod matches the given non OFActions fields
     */
    protected void verifyFlowModNonActionFields(OFFlowMod fm,
                                                OFMatch expectedMatch,
                                                int expectedBufferId,
                                                short expectedCommand,
                                                boolean expectFlowRemovedNotif
                                                ) {
        assertEquals(curTestId, expectedBufferId, fm.getBufferId());
        assertEquals(curTestId, expectedCommand, fm.getCommand());
        assertEquals(curTestId, expectedMatch, fm.getMatch());
        if (expectFlowRemovedNotif) 
            assertEquals(curTestId, OFFlowMod.OFPFF_SEND_FLOW_REM, fm.getFlags());
        else 
            assertEquals(curTestId, 0, fm.getFlags());
        assertEquals(curTestId, 0, fm.getHardTimeout());
        assertEquals(curTestId, 5, fm.getIdleTimeout());
        assertEquals(curTestId, forwarding.getAccessPriority(), fm.getPriority());
        assertEquals(curTestId, cookie, fm.getCookie());
    }
    
    
    /*
     * Verify that the given OFPacketOut matches the given config 
     */
    protected void verifyPacketOut(OFPacketOut po,
                                   Ethernet eth,
                                   int expectedBufferId,
                                   int expectedInPort,
                                   int expectedOutPort,
                                   Short transportVlan,
                                   ZoneVlanMode inVlanMode,
                                   ZoneVlanMode outVlanMode)
    {
        assertEquals(curTestId, expectedInPort, po.getInPort());
        assertEquals(curTestId, expectedBufferId, po.getBufferId());
        if (outVlanMode == ZoneVlanMode.UNTAGGED) 
            eth.setVlanID(Ethernet.VLAN_UNTAGGED);
        else
            eth.setVlanID(transportVlan);
        assertArrayEquals(curTestId, eth.serialize(), po.getPacketData());
        
        List<OFAction> actions = po.getActions();
        assertEquals(curTestId, 1, actions.size());
        OFActionOutput expectedAction = 
                new OFActionOutput((short)expectedOutPort, (short)0xffff);
        assertEquals(curTestId, expectedAction, actions.get(0));
    }
    
    /*
     * Verifies the writeCaptures and makes sure we received what we 
     * expected
     */
    protected void doVerify(Short transportVlan,
                            int bufferId,
                            Long origDstMac,
                            Long finalDstMac,
                            Long origSrcMac,
                            Long finalSrcMac,
                            Integer ttlDecrement,
                            Long packetInSwitchId,
                            boolean haveRemoveNotif,
                            short command,
                            ZoneVlanMode[] vlanModes,
                            LinkType[] linkTypes,
                            boolean isOFMatchTest) {
        String saveCurTestId = curTestId;
        boolean expectPacketOut = !isOFMatchTest;
        for(int i=0; i<switches.length; i++) {
            curTestId = saveCurTestId + "SwitchIdx=" + i;
            int inputZoneIdx = i;     // zone index of input switch port
            int outputZoneIdx = i+1;  // zone index of output switch port
            long curSwitchId = 0x10L + i;
            int expectedBufferId;
            SwitchPort outSwp = getSwitchPort(i, SwitchPortDirection.OUTPUT);
            SwitchPort inSwp = getSwitchPort(i, SwitchPortDirection.INPUT);
            
            // create the expected OFMatch 
            OFMatch expectedMatch = new OFMatch();
            expectedMatch.loadFromPacket(testPacketSerialized, (short)inSwp.getPort());
            if (vlanModes[inputZoneIdx] == ZoneVlanMode.TAGGED) {
                expectedMatch.setDataLayerVirtualLan(transportVlan);
            } else {
                expectedMatch.setDataLayerVirtualLan(Ethernet.VLAN_UNTAGGED);
            }
            if (i>0 && finalDstMac != null) {
                expectedMatch.setDataLayerDestination(
                        Ethernet.toByteArray(finalDstMac));
            }
            expectedMatch.setWildcards(expected_wildcards);
            
            // get other expected fields in flow mod & packet out
            // TODO
            if (packetInSwitchId.equals(curSwitchId)) 
                expectedBufferId = -1;
            else
                expectedBufferId = -1;
            
            
            Long curSwitchOrigDstMac;
            Long curSwitchFinalDstMac;
            Long curSwitchOrigSrcMac;
            Long curSwitchFinalSrcMac;
            Integer curSwitchTtlDecrement;
            Ethernet eth = (Ethernet)testPacket.clone();
            // we always set the dest mac in the reference ether 
            // since we never expect a packetOut
            // with the original mac
            if (finalDstMac != null)
                eth.setDestinationMACAddress(Ethernet.toByteArray(finalDstMac));
            
            // on the same note, we always decrement the TTL in the reference
            // eth
            if (ttlDecrement != null) {
                // NOTE: we expect that forwarding.decrementTtl is correct.
                // there's a test for it in ForwardingTest
                boolean notExpired = forwarding.decrementTtl(eth, ttlDecrement);
                // quick cross-check to make sure decrementTtl did the right thing
                assertEquals(curTestId, notExpired, 
                             initialPacketTtl > ttlDecrement);
                
                if (ttlDecrement >= initialPacketTtl && !isOFMatchTest) {
                    // TTL will expire. Expect drop.
                    assertEquals(curTestId, false,
                                 writeCaptures[i].hasCaptured());
                    continue;
                }
            }
            
            // Check that we have captured something. 
            assertEquals(curTestId, true, writeCaptures[i].hasCaptured());
            List <OFMessage> msgs = writeCaptures[i].getValues();
            Iterator<OFMessage> it = msgs.iterator();
            int flowModCount = 0;
            int packetOutCount = 0;
            
            // Fill out our expectations based on the switch Idx
            if (i == 0) {
                // FIRST HOP SWITCH. might have mac rewriting
                curSwitchOrigDstMac = origDstMac;
                curSwitchFinalDstMac = finalDstMac;
                // might have TTL decrement
                curSwitchTtlDecrement = ttlDecrement;
                // We also check the flow cache addFlow call here since the 
                // expectedMatch is properly constructed. We could also do 
                // this check outside the loop.
                assertEquals(curTestId, 1, 
                             flowCacheOfmCapture.getValues().size());
                OFMatchWithSwDpid fcMatch = flowCacheOfmCapture.getValue();
                assertEquals(curTestId, curSwitchId, 
                             fcMatch.getSwitchDataPathId());
                assertEquals(curTestId, expectedMatch,
                             fcMatch.getOfMatch());
            } else {
                // not the first hop. DstMac will have been rewritten before
                curSwitchOrigDstMac = finalDstMac; // [sic] set both to finalDstMac
                curSwitchFinalDstMac = finalDstMac;
                curSwitchTtlDecrement = null;
            }
            
            if (i == switches.length-1) {
                // LAST HOP SWITCH 
                // might have src mac rewrite
                curSwitchOrigSrcMac = origSrcMac;
                curSwitchFinalSrcMac = finalSrcMac;
                if (finalSrcMac != null) {
                    eth.setSourceMACAddress(Ethernet.toByteArray(finalSrcMac));
                }
            } else {
                curSwitchOrigSrcMac = origSrcMac; // [sic] set both the origSrcMac
                curSwitchFinalSrcMac = origSrcMac;
            }
            
   
            // Check the OFMessages we captured
            while (it.hasNext()) {
                OFMessage ofm = it.next();
                if (ofm instanceof OFFlowMod) {
                    flowModCount++;
                    OFFlowMod offm = (OFFlowMod)ofm;
                    verifyFlowModNonActionFields(offm, 
                                                 expectedMatch,
                                                 expectedBufferId, 
                                                 command,
                                                 haveRemoveNotif && (i==0));
                    verifyFlodModActions((OFFlowMod)ofm,
                                         outSwp.getPort(),
                                         transportVlan,
                                         curSwitchOrigDstMac,
                                         curSwitchFinalDstMac, 
                                         curSwitchOrigSrcMac,
                                         curSwitchFinalSrcMac,
                                         curSwitchTtlDecrement,
                                         vlanModes[inputZoneIdx],
                                         vlanModes[outputZoneIdx]);
                    it.remove();
                }
                else if (expectPacketOut && ofm instanceof OFPacketOut) {
                    packetOutCount++;
                    verifyPacketOut((OFPacketOut)ofm,
                                    (Ethernet)eth.clone(),
                                    expectedBufferId,
                                    inSwp.getPort(),
                                    outSwp.getPort(),
                                    transportVlan,
                                    vlanModes[inputZoneIdx],
                                    vlanModes[outputZoneIdx]);
                    it.remove();
                }
            }
            
            
            // we expect exactly one flow mode
            assertEquals(curTestId, 1, flowModCount);
            if (packetInSwitchId == curSwitchId && expectPacketOut) 
                assertEquals(curTestId, 1, packetOutCount);
            else
                assertEquals(curTestId, 0, packetOutCount);
            
            assertEquals(curTestId, 0, msgs.size());
        }
    }

    
    /* calls verify() on all mocks */
    protected void verifyAllMocks() {
        verify(topology, rewriteService, flowCacheService);
        for (IOFSwitch sw: switches)
            verify(sw);
    }
    
    /*
     * Collects the config for a particular test run
     */
    class TestConfig {
        public TestConfig() {
            // setup some defaults
            this.vlan = PushRewriteRouteTest.this.vlan;
            this.packet = (Ethernet)PushRewriteRouteTest.this.testPacket;
            this.packetIn = PushRewriteRouteTest.this.packetIn;
        }

        public boolean requestFlowRemovedNotifn;
        public boolean doFlush;
        public int bufferId;
        public Long origDstMac;
        public Long finalDstMac;
        public Long origSrcMac;
        public Long finalSrcMac;
        public Integer ttlDecrement;
        public ZoneVlanMode[] vlanModes;
        public LinkType[] linkTypes;
        public SwitchPort packetInSwp;
        public Short vlan;
        public Ethernet packet;
        public OFPacketIn packetIn;
        public short flowModCmd;
        
        public void setupDstMac(Long origMac, Long finalMac) {
            if (finalMac != null ) {
                this.origDstMac = origMac;
                this.finalDstMac = finalMac;
            } else {
                this.origDstMac = null;
                this.finalDstMac = null;
            }
        }
        
        public void setupSrcMac(Long origMac, Long finalMac) {
            if (finalMac != null ) {
                this.origSrcMac = origMac;
                this.finalSrcMac = finalMac;
            } else {
                this.origSrcMac = null;
                this.finalSrcMac = null;
            }
        }
        
        @Override
        public String toString() {
            String rv = "Config<";
            rv += "vlan=" + this.vlan;
            rv += ", zones=";
            for (ZoneVlanMode vm: vlanModes) 
                rv += (vm==ZoneVlanMode.TAGGED) ? "T" : "U";
            rv += ", links=";
            for (LinkType lt: linkTypes) {
                if (lt == LinkType.INT)
                    rv += "I";
                else if (lt == LinkType.EXT_TAGGED)
                    rv += "T";
                else 
                    rv += "U";
            }
            rv += ", pinSwitch=" + packetInSwp.getSwitchDPID();
            if (finalSrcMac != null)
                rv += ", SrcMacRewrite=" + HexString.toHexString(finalSrcMac, 6);
            if (finalDstMac != null)
                rv += ", DstMacRewrite=" + HexString.toHexString(finalDstMac, 6);
            if (ttlDecrement != null)
                rv += ", TTLDec=" + ttlDecrement;
            rv += ", FlowModCmd=" + flowModCmd;        
            rv += ", bufferId=" + bufferId;
            if (doFlush)
                rv += ", flush";
            if (requestFlowRemovedNotifn)
                rv += ", notif";
            rv += ">";
            return rv;
        }
    }
    
    /*
     * Evil hack: juggles some not-so-important test parameters based
     * on the given idx
     */
    protected void setupMinorParameters(TestConfig c, int idx) {
        switch (idx % 4) {
            case 0:
                c.doFlush = true;
                c.requestFlowRemovedNotifn = true;
                c.flowModCmd = OFFlowMod.OFPFC_ADD;
                break;
            case 1:
                c.doFlush = true;
                c.requestFlowRemovedNotifn = true;
                c.flowModCmd = OFFlowMod.OFPFC_MODIFY;
                break;
            case 2:
                c.doFlush = true;
                c.requestFlowRemovedNotifn = false;
                c.flowModCmd = OFFlowMod.OFPFC_MODIFY;
            case 3:
                c.doFlush = false;
                c.requestFlowRemovedNotifn = true;
                c.flowModCmd = OFFlowMod.OFPFC_ADD;
                break;
        }
    }

    /*
     * Run a single test with the given config
     * If checkWithOFMatch is true we test the behavior of pushRewriteRoute
     * in cases were we act on a OFMatch as input (i.e., flow reconciliation) 
     * rather than a packet in
     */
    protected void doOneTest(TestConfig c, boolean checkWithOFMatch) throws Exception {
        boolean tunnelEnabled = false;
        Ethernet origEth = (Ethernet)testPacket.clone();
        byte[] origPacketData = testPacketSerialized;
        curTestId  = c.toString();
        Route route = getTestRoute();
        IRoutingDecision decision = getMockRoutingDecision(c.packetInSwp, true);
        
        boolean expectPacketIgnored = false;
        if (c.ttlDecrement != null && c.ttlDecrement >= initialPacketTtl)
            expectPacketIgnored = true;
        
        if (checkWithOFMatch) 
            curTestId += " <mode: OFMatch> ";
        else
            curTestId += " <mode: PacketIn> ";
        
        OFMatch match = null;
        OFPacketIn packetIn = c.packetIn;
        // When creating a packet-in, ensure that the packet-in port
        // matches the expected input port.
        packetIn.setInPort((short)c.packetInSwp.getPort());

        if (checkWithOFMatch) {
            match = new OFMatch();
            match.loadFromPacket(origPacketData, (short)c.packetInSwp.getPort());
            match.setWildcards(VirtualRouting.DEFAULT_HINT);
            packetIn = null;
            expectPacketIgnored = false;
        }

        setupMockSwitches(c.doFlush, true, expectPacketIgnored);
        setupZoneExpectations(c.vlan, 
                              c.origDstMac, 
                              c.finalDstMac, 
                              c.origSrcMac,
                              c.finalSrcMac,
                              c.ttlDecrement,
                              c.vlanModes, 
                              c.linkTypes);
        
        setupFlowCacheExpectations(expectPacketIgnored);
        
        IControllerService.bcStore.put(
               cntx,
               IControllerService.CONTEXT_PI_PAYLOAD,
               c.packet);
        replay(topology, rewriteService, flowCacheService);
        forwarding.pushRewriteRoute(route,
                                    decision.getSourceDevice(), 
                                    match,
                                    packetIn,
                                    c.packetInSwp.getSwitchDPID(),
                                    cookie,
                                    decision.getWildcards(),
                                    c.requestFlowRemovedNotifn, 
                                    c.doFlush,
                                    c.flowModCmd,
                                    tunnelEnabled,
                                    cntx);
        doVerify(c.vlan, 
                 c.bufferId,
                 c.origDstMac,
                 c.finalDstMac,
                 c.origSrcMac,
                 c.finalSrcMac,
                 c.ttlDecrement,
                 c.packetInSwp.getSwitchDPID(), 
                 c.requestFlowRemovedNotifn,
                 c.flowModCmd, 
                 c.vlanModes, 
                 c.linkTypes,
                 checkWithOFMatch);
        verifyAllMocks();
        assertEquals(origEth, testPacket);
        assertArrayEquals(origPacketData, testPacketSerialized);
    }
    
    protected void doTestForEachSwitch(TestConfig c) throws Exception {
        for (int pinSwIdx=0; pinSwIdx<switches.length; pinSwIdx++) {
            c.packetInSwp = getSwitchPort(pinSwIdx, 
                                          SwitchPortDirection.INPUT);
            resetAllMocks();
            doOneTest(c, false);
            resetAllMocks();
            doOneTest(c, true);

        }
        c.packetInSwp = new SwitchPort (4200L, 1);
        resetAllMocks();
        doOneTest(c, false);
        resetAllMocks();
        doOneTest(c, true);
    }
    
    /* Run several tests with the given base config. 
     * We currently take the ZoneVlanModes, LinkTypes, and bufferId from the
     * baseConfig and modify the other parameters 
     */
    protected void doMultiTests(TestConfig c) throws Exception {
        int i = 0;
        setupMinorParameters(c, i);
        for (Long dstMac: new Long[] { null, 0x424242L })  {
            c.setupDstMac(packetDstMac, dstMac);
            for (Long srcMac: new Long[] { null, 0x232323L }) {
                c.setupSrcMac(packetSrcMac, srcMac);
                for (Integer ttlDecrement: new Integer[] { null, 1, 255,
                                                    (int) initialPacketTtl }) {
                    c.ttlDecrement = ttlDecrement;
                    doTestForEachSwitch(c);
                }
            }
        }
    }

    @Test
    public void testZones1() throws Exception {
        TestConfig c = new TestConfig();
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.TAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.INT,
                                       LinkType.EXT_TAGGED,
                                       LinkType.INT
                                     };
        doMultiTests(c);
    }
    
    @Test
    public void testZones2() throws Exception {
        TestConfig c = new TestConfig();
        c.bufferId = -1;
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.UNTAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.EXT_TAGGED,
                                       LinkType.EXT_UNTAGGED,
                                       LinkType.INT
                                     };
        doMultiTests(c);
    }
    
    @Test
    public void testZones3() throws Exception {
        TestConfig c = new TestConfig();
        c.bufferId = -1;
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.EXT_UNTAGGED,
                                       LinkType.EXT_UNTAGGED,
                                       LinkType.EXT_UNTAGGED
                                     };
        doMultiTests(c);
    }
    
    @Test
    public void testZones4() throws Exception {
        TestConfig c = new TestConfig();
        c.bufferId = -1;
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.EXT_UNTAGGED,
                                       LinkType.EXT_UNTAGGED,
                                       LinkType.EXT_UNTAGGED
                                     };
        doMultiTests(c);
    }
    
    @Test
    public void testZones5() throws Exception {
        tagPacketIn();
        TestConfig c = new TestConfig();
        c.bufferId = -1;
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.UNTAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.EXT_TAGGED,
                                       LinkType.EXT_UNTAGGED,
                                       LinkType.INT
                                     };
        doMultiTests(c);
    }
    
    @Test
    public void testZones6() throws Exception {
        tagPacketIn();
        TestConfig c = new TestConfig();
        c.bufferId = -1;
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.TAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.EXT_UNTAGGED,
                                       LinkType.EXT_UNTAGGED,
                                       LinkType.INT
                                     };
        doMultiTests(c);
    }
    
    
    /* Test that we find the correct input vlan when then packet in is
     * from BD
     */
    @Test
    public void testInputVlan() throws Exception {
        // We learn the src device on sw 0x20, port 1 
        reset(topology);
        expect(topology.isAttachmentPointPort(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        topology.addListener(anyObject(ITopologyListener.class));
        expectLastCall().anyTimes();
        expect(topology.getL2DomainId(anyLong())).andReturn(0x10L).anyTimes();
        replay(topology);
        deviceManager.deleteDevice((Device)srcDevice);
        srcDevice = 
                deviceManager.learnEntity(packetSrcMac,
                                          null, networkSource,
                                          0x20L, 1);
        
        // Packet in is on some other switch 
        tagPacketIn();
        TestConfig c = new TestConfig();
        c.bufferId = -1;
        c.vlanModes = new ZoneVlanMode[] { ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.UNTAGGED,
                                           ZoneVlanMode.TAGGED,
                                           ZoneVlanMode.TAGGED
                                          };
        c.linkTypes = new LinkType[] { LinkType.EXT_UNTAGGED,
                                       LinkType.EXT_UNTAGGED,
                                       LinkType.INT
                                     };
        setupMinorParameters(c, 0);
        c.packetInSwp = new SwitchPort(4200, 1);
        resetAllMocks();
        expect(topology.isAttachmentPointPort(0x20L, (short)1))
               .andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(0x20, 
                                                (short)1,
                                                0x10,
                                                (short)1,
                                                false))
                .andReturn(true).anyTimes();
        expect(topology.isInSameBroadcastDomain(0x10, 
                                                (short)1,
                                                0x20,
                                                (short)1,
                                                false))
                .andReturn(true).anyTimes();
        doOneTest(c, false);
    }
    
}   