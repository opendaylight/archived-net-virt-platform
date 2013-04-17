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

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMatch;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.core.util.MutableInteger;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.internal.BetterDeviceManagerImpl;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.BetterFlowCache;
import org.sdnplatform.flowcache.BetterFlowReconcileManager;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.linkdiscovery.internal.LinkDiscoveryManager;
import org.sdnplatform.netvirt.core.VNSAccessControlList;
import org.sdnplatform.netvirt.core.VNSAccessControlListEntry;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.manager.internal.NetVirtManagerImpl;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.TCP;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.BetterTopologyManager;
import org.sdnplatform.topology.ITopologyService;



public class VirtualRoutingHintTest extends PlatformTestCase {
    protected int defaultHint = OFMatch.OFPFW_ALL &
            ~(OFMatch.OFPFW_DL_DST | OFMatch.OFPFW_DL_SRC);
    
    protected IPacket udpPacket1;
    protected IPacket tcpPacket1, tcpPacket2;
    protected IPacket icmpPacket1;
    protected IPacket arpPacket;

    private NetVirtManagerImpl netVirtManager;
    private MockDeviceManager mockDeviceManager;
    private MemoryStorageSource storageSource;
    private VirtualRouting virtualRouting;
    private BetterDeviceManagerImpl betterDeviceManager;
    private BetterFlowCache betterFlowCacheMgr;
    private BetterFlowReconcileManager flowRecocileMgr;
    private LinkDiscoveryManager linkDiscMgr;
    private BetterTopologyManager topology;
    private ModuleContext fmc;

    
    @Before
    public void setUp() throws Exception {
        super.setUp();        

        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        virtualRouting = new VirtualRouting();
        betterDeviceManager = new BetterDeviceManagerImpl();
        netVirtManager = new NetVirtManagerImpl();
        betterFlowCacheMgr = new BetterFlowCache();
        flowRecocileMgr = new BetterFlowReconcileManager();
        linkDiscMgr = new LinkDiscoveryManager();
        topology = new BetterTopologyManager();
        RestApiServer ras = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        DefaultEntityClassifier entityClassifier =
            new DefaultEntityClassifier();
        
        fmc = new ModuleContext();
        fmc.addService(IControllerService.class,
                       mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(ITagManagerService.class, betterDeviceManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(IFlowReconcileService.class, flowRecocileMgr);
        fmc.addService(ILinkDiscoveryService.class, linkDiscMgr);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        
        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        betterDeviceManager.init(fmc);
        virtualRouting.init(fmc);
        netVirtManager.init(fmc);
        betterFlowCacheMgr.init(fmc);
        flowRecocileMgr.init(fmc);
        linkDiscMgr.init(fmc);
        topology.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);
        
        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        betterDeviceManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        netVirtManager.startUp(fmc);
        betterFlowCacheMgr.startUp(fmc);
        flowRecocileMgr.startUp(fmc);
        linkDiscMgr.startUp(fmc);
        topology.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        


        buildPackets();
    }
    
    private void buildPackets() {
        // Build our test packets
        this.udpPacket1 = new Ethernet()
            .setDestinationMACAddress("00:00:00:00:00:02")
            .setSourceMACAddress("00:00:00:00:00:01")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));

        this.tcpPacket1 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:02")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.2")
            .setProtocol(IPv4.PROTOCOL_TCP)
            .setPayload(new TCP()
                        .setSourcePort((short) 5000)
                        .setDestinationPort((short) 5001)
                        .setPayload(new Data(new byte[] {0x01}))));

        this.tcpPacket2 = new Ethernet()
            .setDestinationMACAddress("00:00:00:00:00:03")
            .setSourceMACAddress("00:00:00:00:00:01")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.3")
                .setProtocol(IPv4.PROTOCOL_TCP)
                .setPayload(new TCP()
                            .setSourcePort((short) 33000)
                            .setDestinationPort((short) 5001)));
        
        this.icmpPacket1 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:03")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.3")
            .setProtocol(IPv4.PROTOCOL_ICMP)
            .setPayload(new ICMP()
                        .setIcmpType((byte) 8)
                        .setPayload(new Data(new byte[] {1, 2, 3}))));
        
        // Not a read ARP packet, just need the mac header for testing
        this.arpPacket = new Ethernet()
            .setDestinationMACAddress("00:00:00:00:00:03")
            .setSourceMACAddress("00:00:00:00:00:01")
            .setEtherType(Ethernet.TYPE_ARP)
            .setPad(true);
    }
    
    /**
     * Apply acl and verify hint is as expected
     */
    private void testHintInternal (VNSAccessControlList acl, Ethernet eth,
                                   int expectedWildcards) {
        MutableInteger hint = new MutableInteger(defaultHint);

        ListenerContext cntx = new ListenerContext();

        IDevice srcDev = null, dstDev = null;
        Integer srcIpAddr = null, dstIpAddr = null;

        if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
            srcIpAddr = ((IPv4) eth.getPayload()).getSourceAddress();
            dstIpAddr = ((IPv4) eth.getPayload()).getDestinationAddress();
        }

        /*
         * Learn source and destination entity devices, as they are required
         * during match rules execution in acls.
         */
        srcDev = mockDeviceManager.learnEntity(
                     Ethernet.toLong(eth.getSourceMACAddress()), null,
                     srcIpAddr, 1L, 1);

        dstDev = mockDeviceManager.learnEntity(
                     Ethernet.toLong(eth.getDestinationMACAddress()), null,
                     dstIpAddr, 1L, 1);

        IControllerService.bcStore.
            put(cntx, IControllerService.CONTEXT_PI_PAYLOAD, eth);
        
        if (srcDev != null) {
            IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_SRC_DEVICE,
                                       srcDev);
        }

        if (dstDev != null) {
            IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                       dstDev);
        }
        
        // No context and  direction for test case
        acl.applyAcl(eth, hint, cntx, null);
        assertTrue(hint.intValue() == expectedWildcards);
    }
    
    /**
     * Return a mac acl entry
     */
    private VNSAccessControlListEntry macAclEntry(int etherType, int vlan)
            throws Exception {
        VNSAccessControlListEntry aclMac = new VNSAccessControlListEntry(10, null);
        aclMac.setAction("permit");
        aclMac.setType("mac");
        aclMac.setSrcMac(null);
        aclMac.setDstMac(null);
        aclMac.setEtherType(etherType);
        aclMac.setVlan(vlan);
        
        return aclMac;
    }
    
    /**
     * Mac ACL with no etherType or VLAN should have default hint
     */
    @Test
    public void testMacDefault() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry macAcl = macAclEntry(
                VNSAccessControlListEntry.ETHERTYPE_ALL,
                VNSAccessControlListEntry.VLAN_ALL);
        acl.addAclEntry(macAcl);
        testHintInternal(acl, (Ethernet) udpPacket1, defaultHint);
    }
    
    /**
     * Mac ACL with no VLAN should have default hint minus vlan wildcard
     */
    @Test
    public void testMacVlan() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry macAcl = macAclEntry(
                VNSAccessControlListEntry.ETHERTYPE_ALL,
                42);
        acl.addAclEntry(macAcl);
        testHintInternal(acl, (Ethernet) udpPacket1,
                         defaultHint & ~OFMatch.OFPFW_DL_VLAN);
    }
    
    /**
     * Mac ACL with etherType or VLAN shouldn't wildcard vlan or DL_TYPE
     */
    @Test
    public void testMacVlanEthType() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry macAcl = macAclEntry(0x800, 42);
        acl.addAclEntry(macAcl);
        testHintInternal(acl, (Ethernet) udpPacket1,
                defaultHint & ~(OFMatch.OFPFW_DL_VLAN | OFMatch.OFPFW_DL_TYPE));
    }
    
    /**
     * Return a mac acl entry
     */
    private VNSAccessControlListEntry ipprotoAclEntry(int seqNo, int ipproto,
            String srcIpMask, String dstIpMask) throws Exception {
        VNSAccessControlListEntry aclIp = new VNSAccessControlListEntry(seqNo, null);
        aclIp.setAction("permit");
        if (ipproto == VNSAccessControlListEntry.IPPROTO_ALL) {
            aclIp.setType("ip");
        } else {
            aclIp.setType(Integer.toString(ipproto));
        }
        aclIp.setSrcIp("192.168.1.64");
        aclIp.setSrcIpMask(srcIpMask);
        aclIp.setDstIp("192.168.1.64");
        aclIp.setDstIpMask(dstIpMask);
        
        return aclIp;
    }

    /**
     * Test IP ACL with dst mask
     */
    @Test
    public void testIpDst() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry ipAcl = ipprotoAclEntry(20,
                VNSAccessControlListEntry.IPPROTO_ALL, "255.255.255.255", "0.0.0.255");
        acl.addAclEntry(ipAcl);
        
        testHintInternal(acl, (Ethernet) udpPacket1, (defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_DST_MASK)) |
                (8 << OFMatch.OFPFW_NW_DST_SHIFT));
    }

    /**
     * Test ipproto ACL with src mask
     */
    @Test
    public void testIpproto() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry ipprotoAcl = ipprotoAclEntry(20,
                1, "1.0.0.255", "255.255.255.255");
        acl.addAclEntry(ipprotoAcl);
        
        testHintInternal(acl, (Ethernet) icmpPacket1, (defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_NW_SRC_MASK)) |
                (8 << OFMatch.OFPFW_NW_SRC_SHIFT));
    }
    
    /**
     * Create icmp ACL entry
     */
    private VNSAccessControlListEntry icmpAclEntry(int icmpType) throws Exception {
        VNSAccessControlListEntry aclIcmp = new VNSAccessControlListEntry(30, null);
        aclIcmp.setAction("deny");
        aclIcmp.setType("icmp");
        aclIcmp.setSrcIp("192.168.1.64");
        aclIcmp.setSrcIpMask("255.255.255.255");
        aclIcmp.setDstIp("192.168.1.64");
        aclIcmp.setDstIpMask("255.255.255.255");
        aclIcmp.setIcmpType(icmpType);
        
        return aclIcmp;
    }

    /**
     * Test icmp ACL with no ICMP type matching
     */
    @Test
    public void testIcmpAllType() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry icmpAcl = icmpAclEntry(VNSAccessControlListEntry.ICMPTYPE_ALL);
        acl.addAclEntry(icmpAcl);
        
        testHintInternal(acl, (Ethernet) icmpPacket1, defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO));
    }
    
    /**
     * Test icmp ACL with ICMP type matching
     */
    @Test
    public void testIcmpWithType() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry icmpAcl = icmpAclEntry(8);
        acl.addAclEntry(icmpAcl);
        
        testHintInternal(acl, (Ethernet) icmpPacket1, defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_TP_SRC));
    }
    
    /**
     * Create tcp/udp ACL entry
     */
    private VNSAccessControlListEntry tpAclEntry(int seqNo, String type, String tpSrcOp, String tpDstOp)
             throws Exception {
        VNSAccessControlListEntry aclTp = new VNSAccessControlListEntry(seqNo, null);
        aclTp.setAction("deny");
        aclTp.setType(type);
        aclTp.setSrcIp("192.168.1.64");
        aclTp.setSrcIpMask("255.255.255.255");
        aclTp.setDstIp("192.168.1.64");
        aclTp.setDstIpMask("255.255.255.255");
        aclTp.setSrcTpPortOp(tpSrcOp);
        aclTp.setSrcTpPort(5000);
        aclTp.setDstTpPortOp(tpDstOp);
        aclTp.setDstTpPort(5001);
        
        return aclTp;
    }
    
    /**
     * Test tcp ACL with specific destination port
     */
    @Test
    public void testTcpDstPort() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry tcpAcl = tpAclEntry(40, "tcp", "any", "eq");
        acl.addAclEntry(tcpAcl);
        
        testHintInternal(acl, (Ethernet) tcpPacket1, defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO | OFMatch.OFPFW_TP_DST));
    }
    
    /**
     * Test udp ACL with specific src/dst port
     */
    @Test
    public void testUdpWithPorts() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        VNSAccessControlListEntry udpAcl = tpAclEntry(40, "udp", "eq", "eq");
        acl.addAclEntry(udpAcl);
        
        testHintInternal(acl, (Ethernet) udpPacket1, defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO |
                  OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_TP_SRC));
    }
    
    /**
     * Test mixed ACL entry types
     */
    @Test
    public void testMixedAclTypes() throws Exception {
        VNSAccessControlList acl = new VNSAccessControlList("testAcl");
        acl.addAclEntry(macAclEntry(Ethernet.TYPE_ARP, VNSAccessControlListEntry.VLAN_ALL));
        acl.addAclEntry(ipprotoAclEntry(20, 25, "0.0.0.0", "0.0.0.0"));
        acl.addAclEntry(ipprotoAclEntry(25, VNSAccessControlListEntry.IPPROTO_ALL,
                                        "0.3.0.63", "0.0.0.15"));
        acl.addAclEntry(icmpAclEntry(8));
        acl.addAclEntry(tpAclEntry(50, "tcp", "neq", "any"));
        acl.addAclEntry(tpAclEntry(60, "udp", "eq", "eq"));
        
        // match mac entry
        testHintInternal(acl, (Ethernet) arpPacket, defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE));
        // match icmp entry
        testHintInternal(acl, (Ethernet) icmpPacket1, (defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO |
                  OFMatch.OFPFW_NW_SRC_MASK |
                  OFMatch.OFPFW_TP_SRC)) |
                (6 << OFMatch.OFPFW_NW_SRC_SHIFT));
        // match tcp entry
        testHintInternal(acl, (Ethernet) tcpPacket2, (defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO |
                  OFMatch.OFPFW_NW_SRC_MASK |
                  OFMatch.OFPFW_TP_SRC)) |
                (6 << OFMatch.OFPFW_NW_SRC_SHIFT));
        // match udp entry
        testHintInternal(acl, (Ethernet) udpPacket1, (defaultHint &
                ~(OFMatch.OFPFW_DL_TYPE | OFMatch.OFPFW_NW_PROTO |
                  OFMatch.OFPFW_NW_SRC_MASK |
                  OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST)) |
                (6 << OFMatch.OFPFW_NW_SRC_SHIFT));
    }
}
