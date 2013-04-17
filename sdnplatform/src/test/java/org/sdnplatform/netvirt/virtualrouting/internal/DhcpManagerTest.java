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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IListener.Command;
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
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.linkdiscovery.internal.LinkDiscoveryManager;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.DHCPMode;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.manager.internal.NetVirtManagerImpl;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.DhcpManager;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.DHCP;
import org.sdnplatform.packet.DHCPOption;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.UDP;
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
import org.sdnplatform.tunnelmanager.ITunnelManagerService;


public class DhcpManagerTest extends PlatformTestCase {
    private NetVirtManagerImpl netVirtManager;
    private MockDeviceManager mockDeviceManager;
    private MemoryStorageSource storageSource;
    private VirtualRouting virtualRouting;
    private DhcpManager dhcpManager;
    private BetterDeviceManagerImpl tagManager;
    private LinkDiscoveryManager linkDiscovery;
    private ModuleContext fmc;
    private ITopologyService topology;
    private IFlowReconcileService flowReconcileMgr;
    private IFlowCacheService betterFlowCacheMgr;
    private ITunnelManagerService tunnelManager;

    // DHCP Discovery Request/Reply
    protected OFPacketIn packetInDHCPDiscoveryRequest;
    protected OFPacketIn packetInDHCPDiscoveryRequestUnicast;
    protected OFPacketIn packetInDHCPDiscoveryReply;
    protected OFPacketIn packetInDHCPDiscoveryRoqueReply;
    protected OFPacketIn packetInDHCPDiscoveryBroadcastReply;


    // DHCP Discovery Request/ACK
    protected OFPacketIn packetInDHCPRequestRequest;
    protected OFPacketIn packetInDHCPRequestAck;

    // Static MAC and IP assignments
    protected String hostMac = "00:0b:82:01:fc:42";
    protected String hostIp = "192.168.0.10";
    protected String altHostMac = "00:0b:82:01:fc:43";
    protected String altHostIp = "192.168.0.11";
    protected String dhcpMac = "00:08:74:ad:f1:9b";
    protected String roqueDhcpMac = "00:08:74:ad:f1:9a";
    protected String dhcpIp = "192.168.0.1";
    protected String roqueDhcpIp = "192.168.0.3";
    protected String hostSubnetMask = "255.255.255.0";

    protected String broadcastMac = "ff:ff:ff:ff:ff:ff";
    protected String broadcastIp = "255.255.255.255";

    public class MessageCapture implements IOFMessageListener {
        protected List<IOFSwitch> switches;
        protected List<OFMessage> messages;
        protected List<ListenerContext> contexts;

        public MessageCapture() {
            switches = new ArrayList<IOFSwitch>();
            messages = new ArrayList<OFMessage>();
            contexts = new ArrayList<ListenerContext>();
        }

        public void assertOneMessageAndReset() {
            assertEquals(1, switches.size());
            assertEquals(1, messages.size());
            assertEquals(1, contexts.size());
            reset();
        }

        public void reset() {
            switches.clear();
            messages.clear();
            contexts.clear();
        }

        @Override
        public String getName() {
            return this.getClass().getName();
        }

        @Override
        public boolean isCallbackOrderingPrereq(OFType type, String name) {
            return false;
        }

        @Override
        public boolean isCallbackOrderingPostreq(OFType type, String name) {
            // We want to be the first listener.
            if (!name.equals(getName()))
                return true;
            return false;
        }

        @Override
        public Command
                receive(IOFSwitch sw, OFMessage msg, ListenerContext cntx) {
            switches.add(sw);
            messages.add(msg);
            contexts.add(cntx);
            return Command.CONTINUE;
        }

        // Getters
        public List<IOFSwitch> getSwitches() {
            return switches;
        }
        public List<OFMessage> getMessages() {
            return messages;
        }
        public List<ListenerContext> getContexts() {
            return contexts;
        }
    }


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        virtualRouting = new VirtualRouting();
        tagManager = new BetterDeviceManagerImpl();
        netVirtManager = new NetVirtManagerImpl();
        tunnelManager = createMock(ITunnelManagerService.class);
        topology = createNiceMock(ITopologyService.class);
        RestApiServer ras = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        linkDiscovery = new LinkDiscoveryManager();
        flowReconcileMgr = createNiceMock(IFlowReconcileService.class);
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        betterFlowCacheMgr = createNiceMock(IFlowCacheService.class);

        fmc = new ModuleContext();
        fmc.addService(IControllerService.class,
                       mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(ITagManagerService.class, tagManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(ILinkDiscoveryService.class, linkDiscovery);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(ITunnelManagerService.class, tunnelManager);

        replay(topology);

        storageSource.init(fmc);
        linkDiscovery.init(fmc);
        mockDeviceManager.init(fmc);
        tagManager.init(fmc);
        virtualRouting.init(fmc);
        netVirtManager.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);

        storageSource.startUp(fmc);
        linkDiscovery.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        tagManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        netVirtManager.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);

        dhcpManager = virtualRouting.getDhcpManager();

        initDiscoveryRequestPacket();
        initDiscoveryRequestPacketUnicast();
        initDiscoveryReplyPacket();
        initDiscoveryRoqueReplyPacket();
        initDiscoveryBroadcastReplyPacket();
        initRequestRequestPacket();
        initRequestAckPacket();

        resetToNice(topology);

        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(topology.isAttachmentPointPort(1L, (short)1)).andReturn(true).anyTimes();

        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                            .andReturn(false).anyTimes();
        replay(tunnelManager);
    }

    /** Test cases when there's no NetVirt or no source device in the context
     * (which really shouldn't happen)
     */
    @Test
    public void testNoNetVirtNoDevice() {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        DhcpManager dhcpManager = new DhcpManager();
        dhcpManager.init();

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        replay(mockSwitch);

        // no source device
        ListenerContext cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest,
                                                  null, null);
        assertEquals(Command.STOP,
                     dhcpManager.receive(mockSwitch,
                                         packetInDHCPDiscoveryRequest, cntx));
        IRoutingDecision res =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());

        // no NetVirt
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest, host, null);
        assertEquals(Command.STOP,
                     dhcpManager.receive(mockSwitch,
                                         packetInDHCPDiscoveryRequest, cntx));
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());

    }

    /**
     * Test that we correctly punt on non DHCP packets or when the source
     * device is missing
     *
     */
    @Test
    public void testNonDhcpPacket() {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        DhcpManager dhcpManager = new DhcpManager();
        dhcpManager.init();

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        replay(mockSwitch, topology);


        // a UDP packet (with DHCP payload but wrong port)
        OFPacketIn packetIn = newNonDhcpPacket(true, true);
        ListenerContext cntx = parseAndAnnotate(packetIn, host, null);
        assertEquals(Command.CONTINUE,
                     dhcpManager.receive(mockSwitch, packetIn, cntx));
        IRoutingDecision res = IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        assertNull(res);

        // An non-UDP packet
        packetIn = newNonDhcpPacket(true, false);
        cntx = parseAndAnnotate(packetIn, host, null);
        assertEquals(Command.CONTINUE,
                     dhcpManager.receive(mockSwitch, packetIn, cntx));
        res = IRoutingDecision.rtStore.get(cntx,
                                          IRoutingDecision.CONTEXT_DECISION);
        assertNull(res);

        // An non-UDP packet
        packetIn = newNonDhcpPacket(false, false);
        cntx = parseAndAnnotate(packetIn, host, null);
        assertEquals(Command.CONTINUE,
                     dhcpManager.receive(mockSwitch, packetIn, cntx));
        res = IRoutingDecision.rtStore.get(cntx,
                                          IRoutingDecision.CONTEXT_DECISION);
        assertNull(res);


    }


    /**
     * Tests the ALWAYS_FLOOD (disabled) DHCP Manager setting.
     * We should just flood the packet.
     * @throws IOException
     */
    @Test
    public void testDhcpManagerAlwaysFlood() throws IOException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VirtualRouting vr = getVirtualRouting();
        DhcpManager dhcpManager = getDhcpManager();

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.ALWAYS_FLOOD);
        }

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        replay(mockSwitch, topology);
        MockControllerProvider flp = getMockControllerProvider();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, vr);
        flp.addOFMessageListener(OFType.PACKET_IN, dhcpManager);

        ListenerContext cntx =
                parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);

        IRoutingDecision res =
                IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());

        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);

        cntx = parseAndAnnotate(packetInDHCPRequestRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPRequestRequest, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());

        cntx = parseAndAnnotate(packetInDHCPRequestAck);
        flp.dispatchMessage(mockSwitch, packetInDHCPRequestAck, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);

        verify(mockSwitch);
    }

    /**
     * Tests that we have snooped a DHCP server and unicast
     * other DHCP requests.
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testFloodIfUnknown() throws IOException, InterruptedException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VirtualRouting vr = getVirtualRouting();

        // Set timeout to low value
        vr.dhcpManager.UNICAST_CONV_TIMEOUT = 30;
        vr.dhcpManager.init();

        // Learn devices
        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(altHostMac);
        ip = IPv4.toIPv4Address(altHostIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);

        // Configure DHCP mode
        // All hosts are in the default NetVirt so its fine if we just set the
        // mode once.
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.FLOOD_IF_UNKNOWN);
            break;
        }

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        MockControllerProvider flp = getMockControllerProvider();
        flp.clearListeners();
        MessageCapture msgCapture = new MessageCapture();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, vr);

        replay(mockSwitch, topology);

        // Send first request. Nothing snooped yet so we expect a flood.
        ListenerContext cntx =
                parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);

        IRoutingDecision res =
            IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        msgCapture.assertOneMessageAndReset();

        // Send the reply. Now we will snoop the server
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        msgCapture.assertOneMessageAndReset();

        reset(mockSwitch, topology);


        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(topology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        replay(mockSwitch, topology);
        // The DHCP server should have been learned by now
        // Next request should be converted to unicast
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);

        // Note that the unicast packet is reinjected. We therefore expect
        // a routing decision of NONE and we check that unicast packet
        // was indeed injected (by checking the captured calls to our
        // OFMessageListener)
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        // Expect two captures messaged. First is the original broadcast, second
        // is the reinjected unicast.
        assertEquals(2, msgCapture.getMessages().size());
        ListenerContext injectedContext = msgCapture.getContexts().get(1);
        Ethernet injectedEth = IControllerService.bcStore.
                get(injectedContext, IControllerService.CONTEXT_PI_PAYLOAD);
        OFPacketIn injectedMessage = (OFPacketIn)msgCapture.getMessages().get(1);
        assertEquals(dhcpMac,
                     HexString.toHexString(
                             injectedEth.getDestinationMACAddress()));
        assertArrayEquals(injectedMessage.getPacketData(),
                          injectedEth.serialize());
        msgCapture.reset();


        // We are within the unicast timeout so we should convert again
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        assertEquals(2, msgCapture.getMessages().size());
        msgCapture.reset();

        // Should be back to broadcast since no reply to last request
        Thread.sleep(35);
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        Ethernet eth =
            IControllerService.bcStore.
            get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        assertEquals(broadcastMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));
        assertEquals(1, msgCapture.getMessages().size());
        msgCapture.reset();

        // Send the reply again. To refresh the server state and remove pending
        // request state
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        msgCapture.assertOneMessageAndReset();

        // Send a packet from a different client host and verify that it's
        // converted to unicast
        OFPacketIn altHostPacketnDHCPDiscoverRequest =
                newDiscoveryRequest(altHostMac, broadcastMac);
        msgCapture.reset();
        cntx = parseAndAnnotate(altHostPacketnDHCPDiscoverRequest);
        flp.dispatchMessage(mockSwitch, altHostPacketnDHCPDiscoverRequest, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        assertEquals(2, msgCapture.getMessages().size());
        injectedContext = msgCapture.getContexts().get(1);
        injectedEth = IControllerService.bcStore.
                get(injectedContext, IControllerService.CONTEXT_PI_PAYLOAD);
        injectedMessage = (OFPacketIn)msgCapture.getMessages().get(1);
        assertEquals(dhcpMac, HexString.toHexString(
                             injectedEth.getDestinationMACAddress()));
        assertArrayEquals(injectedMessage.getPacketData(),
                          injectedEth.serialize());
        msgCapture.reset();

        // wait for timeout to expire.
        Thread.sleep(35);
        // Now send a request from the first host. This host does not have
        // a pending request but the server has an unanswered pending request.
        // Thus we should flood.
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        eth = IControllerService.bcStore.
                get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        assertEquals(broadcastMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));
        assertEquals(1, msgCapture.getMessages().size());
        msgCapture.reset();


        verify(mockSwitch);
    }


    /**
     * Tests handling of the reinjected unicast packet
     * @throws IOException
     */
    @Test
    public void testDhcpManagerUnicast() throws IOException {
        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host = mockDeviceManager.learnEntity(mac, null, ip, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);


        // Do not learn about the destination (DHCP server) at this stage

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        MockControllerProvider flp = getMockControllerProvider();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, getVirtualRouting());

        replay(mockSwitch, topology);

        //-----------------
        // TEST with unknown destination device
        //-----------------

        // No dest device and flood if unknown ==> flood
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.FLOOD_IF_UNKNOWN);
        }
        ListenerContext cntx =
                parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        IRoutingDecision res =
            IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());

        // Always Flood ==> flood packet.
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.ALWAYS_FLOOD);
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());

        // static ==> ignore packet
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        verify(mockSwitch, topology);


        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);

        //-----------------
        // TEST with known destination device.
        //-----------------

        // flood if unknown ==> forward
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.FLOOD_IF_UNKNOWN);
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);

        // Always Flood ==> still forward packet as is
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.ALWAYS_FLOOD);
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);

        // static with correct DHCP-server ==> forward packet
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);

        // static with in-correct DHCP-server ==> forward packet (because it's
        // an request we will allow it. Could also drop it. )
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(roqueDhcpIp));
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequestUnicast);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequestUnicast, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        verify(mockSwitch, topology);

    }



    /**
     * Tests static DHCP configuration. We should convert
     * all broadcast requests to unicast.
     * @throws IOException
     */
    @Test
    public void testDhcpManagerStatic() throws IOException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VirtualRouting vr = getVirtualRouting();

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.hasAttribute("isCoreSwitch")).andReturn(false);

        replay(mockSwitch, topology);

        MockControllerProvider flp = getMockControllerProvider();
        MessageCapture msgCapture = new MessageCapture();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, vr);

        // send request
        ListenerContext cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        IRoutingDecision res =
                IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        assertEquals(2, msgCapture.getMessages().size());
        ListenerContext injectedContext = msgCapture.getContexts().get(1);
        Ethernet injectedEth = IControllerService.bcStore.
                get(injectedContext, IControllerService.CONTEXT_PI_PAYLOAD);
        OFPacketIn injectedMessage = (OFPacketIn)msgCapture.getMessages().get(1);
        assertEquals(dhcpMac,
                     HexString.toHexString(
                             injectedEth.getDestinationMACAddress()));
        assertArrayEquals(injectedMessage.getPacketData(),
                          injectedEth.serialize());
        msgCapture.reset();

        // send reply
        Ethernet eth =
            IControllerService.bcStore
            .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(dhcpMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));

        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        assertEquals(1, msgCapture.getMessages().size());
        msgCapture.reset();
    }

    /**
     * Tests static DHCP configuration when we can't find the server.
     * Request should be simply ignored
     * @throws IOException
     */
    @Test
    public void testDhcpManagerStaticNoServer() throws IOException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VirtualRouting vr = getVirtualRouting();

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(dhcpMac);
        // do not learn server's IP
        mockDeviceManager.learnEntity(mac, null, null, null, null);

        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.hasAttribute("isCoreSwitch")).andReturn(false);

        replay(mockSwitch, topology);

        MockControllerProvider flp = getMockControllerProvider();
        MessageCapture msgCapture = new MessageCapture();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, vr);

        // send request
        ListenerContext cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        IRoutingDecision res =
                IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        assertEquals(1, msgCapture.getMessages().size());
        assertSame(cntx, msgCapture.getContexts().get(0));
        Ethernet eth = IControllerService.bcStore.
                get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(broadcastMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));

    }



    /**
     * Tests static DHCP configuration. A DHCP reply from an un-authorized server
     * should be dropped.
     * @throws IOException
     */
    @Test
    public void testDhcpManagerStaticNegative() throws IOException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VirtualRouting vr = getVirtualRouting();

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);

        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.hasAttribute("isCoreSwitch")).andReturn(false);

        replay(mockSwitch, topology);

        MockControllerProvider flp = getMockControllerProvider();
        MessageCapture msgCapture = new MessageCapture();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, vr);

        // send request
        ListenerContext cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        IRoutingDecision res =
                IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        assertEquals(2, msgCapture.getMessages().size());
        ListenerContext injectedContext = msgCapture.getContexts().get(1);
        Ethernet injectedEth = IControllerService.bcStore.
                get(injectedContext, IControllerService.CONTEXT_PI_PAYLOAD);
        OFPacketIn injectedMessage = (OFPacketIn)msgCapture.getMessages().get(1);
        assertEquals(dhcpMac,
                     HexString.toHexString(
                             injectedEth.getDestinationMACAddress()));
        assertArrayEquals(injectedMessage.getPacketData(),
                          injectedEth.serialize());
        msgCapture.reset();

        Ethernet eth =
            IControllerService.bcStore
            .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        assertEquals(dhcpMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));

        // send reply
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRoqueReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRoqueReply, cntx);
        res =
            IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.DROP, res.getRoutingAction());
        assertEquals(1, msgCapture.getMessages().size());
        msgCapture.reset();
    }

    /**
     * Test all DHCP modes with a broadcast reply packet.
     * @throws IOException
     */
    @Test
    public void testDhcpManagerBroadcastReplies() throws IOException {

        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host = mockDeviceManager.learnEntity(mac, null, ip, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);

        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        mockDeviceManager.learnEntity(mac, null, ip, null, null);


        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        MockControllerProvider flp = getMockControllerProvider();
        MessageCapture msgCapture = new MessageCapture();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, getVirtualRouting());

        replay(mockSwitch, topology);

        // FLOOD-IF-UNKNOWN
        // We expect the packet to be flooded. We might be able to convert
        // to unicast though. Need to take a closer look at the DHCP protocol
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.FLOOD_IF_UNKNOWN);
        }
        ListenerContext cntx =
                parseAndAnnotate(packetInDHCPDiscoveryBroadcastReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryBroadcastReply,
                            cntx);
        IRoutingDecision res =
            IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        msgCapture.assertOneMessageAndReset();

        // Always Flood ==> flood packet.
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.ALWAYS_FLOOD);
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryBroadcastReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryBroadcastReply,
                            cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        msgCapture.assertOneMessageAndReset();

        // static
        // This is very tricky. We currently flood. We could potentially
        // convert to unicast but we need to take a closer look at the
        // packet. But ultimately the problem is that the server decided
        // to flood although it (presumably) knew about the client.
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryBroadcastReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryBroadcastReply,
                            cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        msgCapture.assertOneMessageAndReset();
    }


    /**
     * Test all DHCP modes with a unicast reply packet with an unknown
     * destination (client host)
     * @throws IOException
     */
    @Test
    public void testDhcpManagerUnicastNoDeviceReply() throws IOException {

        long mac = HexString.toLong(dhcpMac);
        int ip = IPv4.toIPv4Address(dhcpIp);
        IDevice dhcpDevice =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(dhcpDevice);

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        MockControllerProvider flp = getMockControllerProvider();
        MessageCapture msgCapture = new MessageCapture();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, getVirtualRouting());

        replay(mockSwitch, topology);

        // FLOOD-IF-UNKNOWN ==> Flood
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.FLOOD_IF_UNKNOWN);
        }
        ListenerContext cntx =
                parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply,
                            cntx);
        IRoutingDecision res =
            IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        msgCapture.assertOneMessageAndReset();

        // Always Flood ==> flood packet.
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.ALWAYS_FLOOD);
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply,
                            cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        msgCapture.assertOneMessageAndReset();

        // static ==> ignore (no drop flow)
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.STATIC);
            iface.getParentVNS().setDhcpIp(IPv4.toIPv4Address(dhcpIp));
        }
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply,
                            cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        msgCapture.assertOneMessageAndReset();
    }



    /** Heloer for callDeviceListener
     */
    enum DeviceListenerToCall {
        DEVICE_ADDED,
        DEVICE_REOMOVED
    }

    /**
     * Helper function that acutally calls an IDeviceListener method
     * @param listenerToCall
     * @param device
     */
    public void callDeviceListener(DeviceListenerToCall listenerToCall,
                             IDevice device) {
        VirtualRouting vr = getVirtualRouting();
        switch(listenerToCall) {
            case DEVICE_ADDED:
                vr.dhcpManager.deviceListener.deviceAdded(device);
                break;
            case DEVICE_REOMOVED:
                vr.dhcpManager.deviceListener.deviceRemoved(device);
                break;
            default:
                break;
        }
    }

    /**
     * Tests handling IDeviceListener events. Caller can specify the
     * listener function to be called
     * @throws IOException
     * @throws InterruptedException
     */
    public void doDeviceListenerTest(DeviceListenerToCall listenerToCall)
                throws IOException, InterruptedException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VirtualRouting vr = getVirtualRouting();

        int timeout = 30; // ms
        int sleepTime = timeout + 6; // ms

        // Set timeout to low value
        vr.dhcpManager.UNICAST_CONV_TIMEOUT = timeout;
        vr.dhcpManager.init();

        // Learn devices
        long mac = HexString.toLong(hostMac);
        int ip = IPv4.toIPv4Address(hostIp);
        IDevice host =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        mac = HexString.toLong(altHostMac);
        ip = IPv4.toIPv4Address(altHostIp);
        IDevice altDevice =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);


        mac = HexString.toLong(dhcpMac);
        ip = IPv4.toIPv4Address(dhcpIp);
        IDevice dhpcServerDevice =
                mockDeviceManager.learnEntity(mac, null, ip, null, null);

        // Configure DHCP mode
        // All hosts are in the default NetVirt so its fine if we just set the
        // mode once.
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(host);
        for (VNSInterface iface : ifaces) {
            iface.getParentVNS().setDhcpManagerMode(DHCPMode.FLOOD_IF_UNKNOWN);
            break;
        }

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        MockControllerProvider flp = getMockControllerProvider();
        MessageCapture msgCapture = new MessageCapture();
        flp.clearListeners();
        flp.addOFMessageListener(OFType.PACKET_IN, msgCapture);
        flp.addOFMessageListener(OFType.PACKET_IN, mockDeviceManager);
        flp.addOFMessageListener(OFType.PACKET_IN, netVirtManager);
        flp.addOFMessageListener(OFType.PACKET_IN, vr);

        OFPacketIn altHostPacketnDHCPDiscoverRequest =
                newDiscoveryRequest(altHostMac, broadcastMac);

        replay(mockSwitch, topology);

        // Send first request. Nothing snooped yet so we expect a flood.
        ListenerContext cntx;
        IRoutingDecision res;

        // Send a reply. Now we will snoop the server
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        msgCapture.assertOneMessageAndReset();

        //--
        // The DHCP server should have been learned by now
        // Send a request from altHost. Should be converted
        //--
        cntx = parseAndAnnotate(altHostPacketnDHCPDiscoverRequest);
        flp.dispatchMessage(mockSwitch, altHostPacketnDHCPDiscoverRequest,
                            cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        // Expect two captured messages. First is the original broadcast, second
        // is the reinjected unicast.
        assertEquals(2, msgCapture.getMessages().size());
        ListenerContext injectedContext = msgCapture.getContexts().get(1);
        Ethernet injectedEth = IControllerService.bcStore.
                get(injectedContext, IControllerService.CONTEXT_PI_PAYLOAD);
        OFPacketIn injectedMessage = (OFPacketIn)msgCapture.getMessages().get(1);
        assertEquals(dhcpMac,
                     HexString.toHexString(
                             injectedEth.getDestinationMACAddress()));
        assertArrayEquals(injectedMessage.getPacketData(),
                          injectedEth.serialize());
        msgCapture.reset();

        //--
        // Wait for the timeout seconds. Send a request to the original
        // device to keep the server alive. But the altHost should still be
        // timed out
        //--
        Thread.sleep(sleepTime);
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        msgCapture.assertOneMessageAndReset();

        //--
        // check that we convert to unicast for orig host (just a safety check)
        //--
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        // Expect two captures messaged. First is the original broadcast, second
        // is the reinjected unicast.
        assertEquals(2, msgCapture.getMessages().size());
        msgCapture.reset();

        //--
        // check that alt host is still timed out (i.e., it floods):
        //--
        cntx = parseAndAnnotate(altHostPacketnDHCPDiscoverRequest);
        flp.dispatchMessage(mockSwitch, altHostPacketnDHCPDiscoverRequest,
                            cntx);
        Ethernet eth =
            IControllerService.bcStore.
            get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        assertEquals(broadcastMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));
        assertEquals(1, msgCapture.getMessages().size());
        msgCapture.reset();

        //--
        // Send Device Update for altHost. We should now clear the pending
        // device state
        //--
        callDeviceListener(listenerToCall, altDevice);

        //
        // check we convert to unicast for alt host
        //
        cntx = parseAndAnnotate(altHostPacketnDHCPDiscoverRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        // Expect two captured messages. First is the original broadcast, second
        // is the reinjected unicast.
        assertEquals(2, msgCapture.getMessages().size());
        injectedMessage = (OFPacketIn)msgCapture.getMessages().get(1);
        assertEquals(dhcpMac,
                     HexString.toHexString(
                             injectedEth.getDestinationMACAddress()));
        msgCapture.reset();


        //////////////////////////////////////////////
        // TEST DHCP SERVER DEVICE HANDLING
        // ///////////////////////////////////////////
        //
        // Send a reply. Now we will snoop the server
        //
        cntx = parseAndAnnotate(packetInDHCPDiscoveryReply);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryReply, cntx);
        res = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD, res.getRoutingAction());
        assertEquals(true, res.getWildcards() != 0);
        msgCapture.assertOneMessageAndReset();

        //
        //
        // Check that we convert to unicast
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.NONE, res.getRoutingAction());
        // Expect two captures messaged. First is the original broadcast, second
        // is the reinjected unicast.
        assertEquals(2, msgCapture.getMessages().size());
        msgCapture.reset();


        // Remvoe the DHCPServer device by calling an IOFDeviceListener
        // A request from the same client should be flooded again
        callDeviceListener(listenerToCall, dhpcServerDevice);

        //
        // Send request. We expect it to be flooded since we just removed
        // the server
        //
        cntx = parseAndAnnotate(packetInDHCPDiscoveryRequest);
        flp.dispatchMessage(mockSwitch, packetInDHCPDiscoveryRequest, cntx);
        eth = IControllerService.bcStore.
                get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        res = IRoutingDecision.rtStore.get(cntx,
                                           IRoutingDecision.CONTEXT_DECISION);
        assertEquals(RoutingAction.FORWARD_OR_FLOOD, res.getRoutingAction());
        assertEquals(Integer.valueOf(0), res.getWildcards());
        assertEquals(broadcastMac,
                     HexString.toHexString(eth.getDestinationMACAddress()));
        assertEquals(1, msgCapture.getMessages().size());
        msgCapture.reset();


        verify(mockSwitch);
    }

    @Test
    public void testDeviceAddedHandling()
            throws IOException, InterruptedException {
        doDeviceListenerTest(DeviceListenerToCall.DEVICE_ADDED);
    }

    @Test
    public void testDeviceRemovedHandling()
            throws IOException, InterruptedException {
        doDeviceListenerTest(DeviceListenerToCall.DEVICE_REOMOVED);
    }



    protected DhcpManager getDhcpManager() {
        return dhcpManager;
    }

    protected MockDeviceManager getMockDeviceManager() {
        return mockDeviceManager;
    }

    protected IStorageSourceService getStorageSource() {
        return storageSource;
    }

    protected NetVirtManagerImpl getNetVirtManager() {
        return netVirtManager;
    }

    protected VirtualRouting getVirtualRouting() {
        return virtualRouting;
    }

    protected void initDiscoveryRequestPacket() {
        this.packetInDHCPDiscoveryRequest = newDiscoveryRequest(hostMac,
                                                                broadcastMac);
    }

    protected void initDiscoveryRequestPacketUnicast() {
        this.packetInDHCPDiscoveryRequestUnicast = newDiscoveryRequest(hostMac,
                                                                       dhcpMac);
    }

    protected void initDiscoveryReplyPacket() {
        int srcIp = IPv4.toIPv4Address(dhcpIp);
        int dstIp = IPv4.toIPv4Address(hostIp);
        this.packetInDHCPDiscoveryReply =
                newDiscoveryReply(dhcpMac, hostMac, srcIp, dstIp);
    }

    protected void initDiscoveryRoqueReplyPacket() {
        int srcIp = IPv4.toIPv4Address(roqueDhcpIp);
        int dstIp = IPv4.toIPv4Address(hostIp);
        this.packetInDHCPDiscoveryRoqueReply =
                newDiscoveryReply(roqueDhcpMac, hostMac, srcIp, dstIp);
    }

    protected void initDiscoveryBroadcastReplyPacket() {
        int srcIp = IPv4.toIPv4Address(dhcpIp);
        int dstIp = IPv4.toIPv4Address(broadcastIp);
        this.packetInDHCPDiscoveryBroadcastReply =
                newDiscoveryReply(dhcpMac, broadcastMac, srcIp, dstIp);
    }

    protected OFPacketIn newDiscoveryRequest(String srcMac, String dstMac) {
        List<DHCPOption> optionList = new ArrayList<DHCPOption>();

        byte[] requestValue = new byte[4];
        requestValue[0] = requestValue[1] = requestValue[2] = requestValue[3] = 0;
        DHCPOption requestOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_RequestedIP.
                             getValue())
                    .setLength((byte)4)
                    .setData(requestValue);

        byte[] msgTypeValue = new byte[1];
        msgTypeValue[0] = 1;    // DHCP request
        DHCPOption msgTypeOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_MessageType.
                             getValue())
                    .setLength((byte)1)
                    .setData(msgTypeValue);

        byte[] reqParamValue = new byte[4];
        reqParamValue[0] = 1;   // subnet mask
        reqParamValue[1] = 3;   // Router
        reqParamValue[2] = 6;   // Domain Name Server
        reqParamValue[3] = 42;  // NTP Server
        DHCPOption reqParamOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_RequestedParameters.
                             getValue())
                    .setLength((byte)4)
                    .setData(reqParamValue);

        byte[] clientIdValue = new byte[7];
        clientIdValue[0] = 1;   // Ethernet
        System.arraycopy(Ethernet.toMACAddress(srcMac), 0,
                         clientIdValue, 1, 6);
        DHCPOption clientIdOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_ClientID.
                             getValue())
                             .setLength((byte)7)
                             .setData(clientIdValue);

        DHCPOption endOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_END.
                             getValue())
                             .setLength((byte)0)
                             .setData(null);

        optionList.add(requestOption);
        optionList.add(msgTypeOption);
        optionList.add(reqParamOption);
        optionList.add(clientIdOption);
        optionList.add(endOption);

        IPacket requestPacket = new Ethernet()
        .setSourceMACAddress(srcMac)
        .setDestinationMACAddress(dstMac)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setVersion((byte)4)
                .setDiffServ((byte)0)
                .setIdentification((short)100)
                .setFlags((byte)0)
                .setFragmentOffset((short)0)
                .setTtl((byte)250)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setChecksum((short)0)
                .setSourceAddress(0)
                .setDestinationAddress(broadcastIp)
                .setPayload(
                        new UDP()
                        .setSourcePort(UDP.DHCP_CLIENT_PORT)
                        .setDestinationPort(UDP.DHCP_SERVER_PORT)
                        .setChecksum((short)0)
                        .setPayload(
                                new DHCP()
                                .setOpCode(DHCP.OPCODE_REQUEST)
                                .setHardwareType(DHCP.HWTYPE_ETHERNET)
                                .setHardwareAddressLength((byte)6)
                                .setHops((byte)0)
                                .setTransactionId(0x00003d1d)
                                .setSeconds((short)0)
                                .setFlags((short)0)
                                .setClientIPAddress(0)
                                .setYourIPAddress(0)
                                .setServerIPAddress(0)
                                .setGatewayIPAddress(0)
                                .setClientHardwareAddress(Ethernet.
                                                          toMACAddress(srcMac))
                                .setOptions(optionList))));

        byte[] serializedPacket = requestPacket.serialize();

        return (((OFPacketIn) mockControllerProvider.getOFMessageFactory()
                .getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(serializedPacket)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short)serializedPacket.length));
    }

    protected OFPacketIn newDiscoveryReply(String srcMac, String dstMac,
                                           int srcIp, int dstIp) {
        List<DHCPOption> optionList = new ArrayList<DHCPOption>();

        byte[] subnetMaskValue = IPv4.toIPv4AddressBytes(hostSubnetMask);
        DHCPOption subnetMaskOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_SubnetMask.
                             getValue())
                    .setLength((byte)4)
                    .setData(subnetMaskValue);

        byte[] leaseTimeValue = new byte[4];
        leaseTimeValue[0] = leaseTimeValue[1] = 0;
        leaseTimeValue[2] = 0x0e;
        leaseTimeValue[3] = 0x10;   // 1 Hour
        DHCPOption leaseTimeOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_LeaseTime
                             .getValue())
                    .setLength((byte)4)
                    .setData(leaseTimeValue);

        byte[] msgTypeValue = new byte[1];
        msgTypeValue[0] = 2;    // DHCP offer
        DHCPOption msgTypeOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_MessageType
                             .getValue())
                    .setLength((byte)1)
                    .setData(msgTypeValue);

        byte[] dhcpServerIdValue = IPv4.toIPv4AddressBytes(dhcpIp);
        DHCPOption dhcpServerIdOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_DHCPServerIp
                             .getValue())
                    .setLength((byte)4)
                    .setData(dhcpServerIdValue);

        byte[] renewalTimeValue = new byte[4];
        renewalTimeValue[0] = renewalTimeValue[1] = 0;
        renewalTimeValue[2] = 0x07;
        renewalTimeValue[3] = 0x08;
        DHCPOption renewalTimeOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_RenewalTime
                             .getValue())
                    .setLength((byte)4)
                    .setData(renewalTimeValue);

        byte[] rebindingTimeValue = new byte[4];
        rebindingTimeValue[0] = rebindingTimeValue[1] = 0;
        rebindingTimeValue[2] = 0x0c;
        rebindingTimeValue[3] = 0x4e;
        DHCPOption rebindingTimeOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OPtionCode_RebindingTime
                             .getValue())
                    .setLength((byte)4)
                    .setData(rebindingTimeValue);

        DHCPOption endOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_END.getValue())
                    .setLength((byte)0)
                    .setData(null);

        optionList.add(subnetMaskOption);
        optionList.add(leaseTimeOption);
        optionList.add(msgTypeOption);
        optionList.add(dhcpServerIdOption);
        optionList.add(renewalTimeOption);
        optionList.add(rebindingTimeOption);
        optionList.add(endOption);

        IPacket replyPacket = new Ethernet()
        .setSourceMACAddress(srcMac)
        .setDestinationMACAddress(dstMac)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setVersion((byte)4)
                .setDiffServ((byte)0)
                .setIdentification((short)200)
                .setFlags((byte)0)
                .setFragmentOffset((short)0)
                .setTtl((byte)128)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setChecksum((short)0)
                .setSourceAddress(srcIp)
                .setDestinationAddress(dstIp)
                .setPayload(
                        new UDP()
                        .setSourcePort(UDP.DHCP_SERVER_PORT)
                        .setDestinationPort(UDP.DHCP_CLIENT_PORT)
                        .setChecksum((short)0)
                        .setPayload(
                                new DHCP()
                                .setOpCode(DHCP.OPCODE_REPLY)
                                .setHardwareType(DHCP.HWTYPE_ETHERNET)
                                .setHardwareAddressLength((byte)6)
                                .setHops((byte)0)
                                .setTransactionId(0x00003d1d)
                                .setSeconds((short)0)
                                .setFlags((short)0)
                                .setClientIPAddress(0)
                                .setYourIPAddress(IPv4.toIPv4Address(hostIp))
                                .setServerIPAddress(srcIp)
                                .setGatewayIPAddress(0)
                                .setClientHardwareAddress(Ethernet.
                                                          toMACAddress(hostMac))
                                .setOptions(optionList))));

        byte[] serializedPacket = replyPacket.serialize();

        return (((OFPacketIn) mockControllerProvider.getOFMessageFactory()
                .getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short)1)
                .setPacketData(serializedPacket)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short)serializedPacket.length));
    }

    protected void initRequestRequestPacket() {
        List<DHCPOption> optionList = new ArrayList<DHCPOption>();

        byte[] requestValue = IPv4.toIPv4AddressBytes(hostIp);
        DHCPOption requestOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_RequestedIP
                             .getValue())
                    .setLength((byte)4)
                    .setData(requestValue);

        byte[] msgTypeValue = new byte[1];
        msgTypeValue[0] = 1;    // DHCP request
        DHCPOption msgTypeOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_MessageType
                             .getValue())
                    .setLength((byte)1)
                    .setData(msgTypeValue);

        byte[] dhcpServerIdValue = IPv4.toIPv4AddressBytes(dhcpIp);
        DHCPOption dhcpServerIdOption =
                new DHCPOption()
                    .setCode(DHCP.DHCPOptionCode.OptionCode_DHCPServerIp
                             .getValue())
                    .setLength((byte)4)
                    .setData(dhcpServerIdValue);

        byte[] reqParamValue = new byte[4];
        reqParamValue[0] = 1;   // subnet mask
        reqParamValue[1] = 3;   // Router
        reqParamValue[2] = 6;   // Domain Name Server
        reqParamValue[3] = 42;  // NTP Server
        DHCPOption reqParamOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode
                                             .OptionCode_RequestedParameters
                                             .getValue())
                                    .setLength((byte)4)
                                    .setData(reqParamValue);

        byte[] clientIdValue = new byte[7];
        clientIdValue[0] = 1;   // Ethernet
        System.arraycopy(Ethernet.toMACAddress(hostMac), 0, clientIdValue, 1, 6);
        DHCPOption clientIdOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode
                                             .OptionCode_ClientID.getValue())
                                    .setLength((byte)7)
                                    .setData(clientIdValue);

        DHCPOption endOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode
                                             .OptionCode_END.getValue())
                                    .setLength((byte)0)
                                    .setData(null);

        optionList.add(requestOption);
        optionList.add(msgTypeOption);
        optionList.add(reqParamOption);
        optionList.add(dhcpServerIdOption);
        optionList.add(clientIdOption);
        optionList.add(endOption);

        IPacket dhcpRequestRequestPacket = new Ethernet()
        .setSourceMACAddress(hostMac)
        .setDestinationMACAddress(broadcastMac)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setVersion((byte)4)
                .setDiffServ((byte)0)
                .setIdentification((short)101)
                .setFlags((byte)0)
                .setFragmentOffset((short)0)
                .setTtl((byte)250)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setChecksum((short)0)
                .setSourceAddress(0)
                .setDestinationAddress(broadcastIp)
                .setPayload(
                        new UDP()
                        .setSourcePort(UDP.DHCP_CLIENT_PORT)
                        .setDestinationPort(UDP.DHCP_SERVER_PORT)
                        .setChecksum((short)0)
                        .setPayload(
                                new DHCP()
                                .setOpCode(DHCP.OPCODE_REQUEST)
                                .setHardwareType(DHCP.HWTYPE_ETHERNET)
                                .setHardwareAddressLength((byte)6)
                                .setHops((byte)0)
                                .setTransactionId(0x00003d1d)
                                .setSeconds((short)0)
                                .setFlags((short)0)
                                .setClientIPAddress(0)
                                .setYourIPAddress(0)
                                .setServerIPAddress(0)
                                .setGatewayIPAddress(0)
                                .setClientHardwareAddress(Ethernet.
                                                          toMACAddress(hostMac))
                                .setOptions(optionList))));

        byte[] serializedPacket = dhcpRequestRequestPacket.serialize();

        this.packetInDHCPRequestRequest =
                ((OFPacketIn) mockControllerProvider.getOFMessageFactory()
                        .getMessage(OFType.PACKET_IN))
                        .setBufferId(-1)
                        .setInPort((short)1)
                        .setPacketData(serializedPacket)
                        .setReason(OFPacketInReason.NO_MATCH)
                        .setTotalLength((short)serializedPacket.length);
    }

    protected void initRequestAckPacket() {

        List<DHCPOption> optionList = new ArrayList<DHCPOption>();

        byte[] subnetMaskValue = IPv4.toIPv4AddressBytes(hostSubnetMask);
        DHCPOption subnetMaskOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_SubnetMask.getValue())
                                    .setLength((byte)4)
                                    .setData(subnetMaskValue);

        byte[] leaseTimeValue = new byte[4];
        leaseTimeValue[0] = leaseTimeValue[1] = 0;
        leaseTimeValue[2] = 0x0e;
        leaseTimeValue[3] = 0x10;   // 1 Hour
        DHCPOption leaseTimeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_LeaseTime.getValue())
                                    .setLength((byte)4)
                                    .setData(leaseTimeValue);

        byte[] msgTypeValue = new byte[1];
        msgTypeValue[0] = 5;    // DHCP ACK
        DHCPOption msgTypeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_MessageType.getValue())
                                    .setLength((byte)1)
                                    .setData(msgTypeValue);

        byte[] dhcpServerIdValue = IPv4.toIPv4AddressBytes(dhcpIp);
        DHCPOption dhcpServerIdOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_DHCPServerIp.getValue())
                                    .setLength((byte)4)
                                    .setData(dhcpServerIdValue);

        byte[] renewalTimeValue = new byte[4];
        renewalTimeValue[0] = renewalTimeValue[1] = 0;
        renewalTimeValue[2] = 0x07;
        renewalTimeValue[3] = 0x08;
        DHCPOption renewalTimeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_RenewalTime.getValue())
                                    .setLength((byte)4)
                                    .setData(renewalTimeValue);

        byte[] rebindingTimeValue = new byte[4];
        rebindingTimeValue[0] = rebindingTimeValue[1] = 0;
        rebindingTimeValue[2] = 0x0c;
        rebindingTimeValue[3] = 0x4e;
        DHCPOption rebindingTimeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OPtionCode_RebindingTime.
                                             getValue())
                                    .setLength((byte)4)
                                    .setData(rebindingTimeValue);

        DHCPOption endOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_END.getValue())
                                    .setLength((byte)0)
                                    .setData(null);

        optionList.add(subnetMaskOption);
        optionList.add(leaseTimeOption);
        optionList.add(msgTypeOption);
        optionList.add(dhcpServerIdOption);
        optionList.add(renewalTimeOption);
        optionList.add(rebindingTimeOption);
        optionList.add(endOption);

        IPacket dhcpRequestAckPacket = new Ethernet()
        .setSourceMACAddress(dhcpMac)
        .setDestinationMACAddress(hostMac)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setVersion((byte)4)
                .setDiffServ((byte)0)
                .setIdentification((short)201)
                .setFlags((byte)0)
                .setFragmentOffset((short)0)
                .setTtl((byte)128)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setChecksum((short)0)
                .setSourceAddress(dhcpIp)
                .setDestinationAddress(hostIp)
                .setPayload(
                        new UDP()
                        .setSourcePort(UDP.DHCP_SERVER_PORT)
                        .setDestinationPort(UDP.DHCP_CLIENT_PORT)
                        .setChecksum((short)0)
                        .setPayload(
                                new DHCP()
                                .setOpCode(DHCP.OPCODE_REPLY)
                                .setHardwareType(DHCP.HWTYPE_ETHERNET)
                                .setHardwareAddressLength((byte)6)
                                .setHops((byte)0)
                                .setTransactionId(0x00003d1d)
                                .setSeconds((short)0)
                                .setFlags((short)0)
                                .setClientIPAddress(0)
                                .setYourIPAddress(IPv4.toIPv4Address(hostIp))
                                .setServerIPAddress(0)
                                .setGatewayIPAddress(0)
                                .setClientHardwareAddress(Ethernet.
                                                          toMACAddress(hostMac))
                                .setOptions(optionList))));

        byte[] serializedPacket = dhcpRequestAckPacket.serialize();

        this.packetInDHCPRequestAck =
                ((OFPacketIn) mockControllerProvider.getOFMessageFactory()
                        .getMessage(OFType.PACKET_IN))
                        .setBufferId(-1)
                        .setInPort((short)1)
                        .setPacketData(serializedPacket)
                        .setReason(OFPacketInReason.NO_MATCH)
                        .setTotalLength((short)serializedPacket.length);
    }

    /* return a non DHCP packet.
     * We'll change Ethertype, IP protocol, and/or UDP ports to make the
     * packet non-DHCP
     */
    protected OFPacketIn newNonDhcpPacket(boolean makeIpPacket,
                                          boolean makeUDPPacket) {

        List<DHCPOption> optionList = new ArrayList<DHCPOption>();

        byte[] subnetMaskValue = IPv4.toIPv4AddressBytes(hostSubnetMask);
        DHCPOption subnetMaskOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_SubnetMask.getValue())
                                    .setLength((byte)4)
                                    .setData(subnetMaskValue);

        byte[] leaseTimeValue = new byte[4];
        leaseTimeValue[0] = leaseTimeValue[1] = 0;
        leaseTimeValue[2] = 0x0e;
        leaseTimeValue[3] = 0x10;   // 1 Hour
        DHCPOption leaseTimeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_LeaseTime.getValue())
                                    .setLength((byte)4)
                                    .setData(leaseTimeValue);

        byte[] msgTypeValue = new byte[1];
        msgTypeValue[0] = 5;    // DHCP ACK
        DHCPOption msgTypeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_MessageType.getValue())
                                    .setLength((byte)1)
                                    .setData(msgTypeValue);

        byte[] dhcpServerIdValue = IPv4.toIPv4AddressBytes(dhcpIp);
        DHCPOption dhcpServerIdOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_DHCPServerIp.getValue())
                                    .setLength((byte)4)
                                    .setData(dhcpServerIdValue);

        byte[] renewalTimeValue = new byte[4];
        renewalTimeValue[0] = renewalTimeValue[1] = 0;
        renewalTimeValue[2] = 0x07;
        renewalTimeValue[3] = 0x08;
        DHCPOption renewalTimeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_RenewalTime.getValue())
                                    .setLength((byte)4)
                                    .setData(renewalTimeValue);

        byte[] rebindingTimeValue = new byte[4];
        rebindingTimeValue[0] = rebindingTimeValue[1] = 0;
        rebindingTimeValue[2] = 0x0c;
        rebindingTimeValue[3] = 0x4e;
        DHCPOption rebindingTimeOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OPtionCode_RebindingTime.
                                             getValue())
                                    .setLength((byte)4)
                                    .setData(rebindingTimeValue);

        DHCPOption endOption = new DHCPOption()
                                    .setCode(DHCP.DHCPOptionCode.
                                             OptionCode_END.getValue())
                                    .setLength((byte)0)
                                    .setData(null);

        optionList.add(subnetMaskOption);
        optionList.add(leaseTimeOption);
        optionList.add(msgTypeOption);
        optionList.add(dhcpServerIdOption);
        optionList.add(renewalTimeOption);
        optionList.add(rebindingTimeOption);
        optionList.add(endOption);

        Ethernet eth = new Ethernet()
            .setSourceMACAddress(dhcpMac)
            .setDestinationMACAddress(hostMac)
            .setEtherType((short)0);

        IPv4 ipv4 = new IPv4()
                .setVersion((byte)4)
                .setDiffServ((byte)0)
                .setIdentification((short)201)
                .setFlags((byte)0)
                .setFragmentOffset((short)0)
                .setTtl((byte)128)
                .setProtocol((byte)0)
                .setChecksum((short)0)
                .setSourceAddress(dhcpIp)
                .setDestinationAddress(hostIp);
        UDP udp = (UDP)new UDP()
                .setSourcePort((short)2000)
                .setDestinationPort((short)3000)
                .setChecksum((short)0)
                .setPayload(
                        new DHCP()
                        .setOpCode(DHCP.OPCODE_REPLY)
                        .setHardwareType(DHCP.HWTYPE_ETHERNET)
                        .setHardwareAddressLength((byte)6)
                        .setHops((byte)0)
                        .setTransactionId(0x00003d1d)
                        .setSeconds((short)0)
                        .setFlags((short)0)
                        .setClientIPAddress(0)
                        .setYourIPAddress(IPv4.toIPv4Address(hostIp))
                        .setServerIPAddress(0)
                        .setGatewayIPAddress(0)
                        .setClientHardwareAddress(Ethernet.
                                                  toMACAddress(hostMac))
                        .setOptions(optionList));

        if (makeUDPPacket) {
            ipv4.setPayload(udp);
            ipv4.setProtocol(IPv4.PROTOCOL_UDP);
        }
        if (makeIpPacket) {
            eth.setPayload(ipv4);
            eth.setEtherType(Ethernet.TYPE_IPv4);
        }

        byte[] packetSerialized = eth.serialize();
        OFPacketIn pi =
            ((OFPacketIn) mockControllerProvider.getOFMessageFactory()
                    .getMessage(OFType.PACKET_IN))
                    .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                    .setInPort((short)1)
                    .setPacketData(packetSerialized)
                    .setReason(OFPacketInReason.NO_MATCH)
                    .setTotalLength((short) packetSerialized.length);
        pi.setLength((short)(OFPacketIn.MINIMUM_LENGTH +
                                 packetSerialized.length));
        return pi;
    }
}

