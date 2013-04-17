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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileListener;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSAccessControlListEntry;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
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
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;



@SuppressWarnings("unchecked")
public class VirtualRoutingAclTest extends PlatformTestCase {
    private INetVirtManagerService netVirtManager;
    private MockDeviceManager mockDeviceManager;
    private MemoryStorageSource storageSource;
    private VirtualRouting virtualRouting;
    private IFlowCacheService betterFlowCacheMgr;
    private IFlowReconcileService flowRecocileMgr;
    private ITopologyService topology;
    private ModuleContext fmc;
    private ITunnelManagerService tunnelManager;

    protected class AclTest {
        ArrayList<Map<String, Object>> aclList = 
            new ArrayList<Map<String, Object>>();
        ArrayList<Map<String, Object>> aclEntryList = 
            new ArrayList<Map<String, Object>>();
        ArrayList<Map<String, Object>> ifAclList =
            new ArrayList<Map<String, Object>>();
        
        public void addAcl(Map<String, Object>... acls) {
            for (Map<String, Object> acl : acls) {
                aclList.add(acl);
            }
        }
        public void addAclEntry(Map<String, Object>... entries) {
            for (Map<String, Object> entry : entries) {
                aclEntryList.add(entry);
            }
        }
        public void addIfAcl(Map<String, Object>... ifacls) {
            for (Map<String, Object> ifacl : ifacls) {
                ifAclList.add(ifacl);
            }
        }
        
        public void writeToStorage(IStorageSourceService storageSource) {
            for (Map<String, Object> row : aclList) {
                storageSource.insertRow(VirtualRouting.VNS_ACL_TABLE_NAME,
                                        row);
            }
            for (Map<String, Object> row : aclEntryList) {
                storageSource.insertRow(VirtualRouting.VNS_ACL_ENTRY_TABLE_NAME, 
                                        row);
            }
            for (Map<String, Object> row : ifAclList) {
                storageSource.insertRow(VirtualRouting.VNS_INTERFACE_ACL_TABLE_NAME, 
                                        row);
            }
        }
    }
    
    private static void clearStorage(IStorageSourceService storageSource) {
        IResultSet rSet = storageSource.executeQuery(VirtualRouting.VNS_ACL_TABLE_NAME,
                new String[]{VirtualRouting.ID_COLUMN_NAME},
                null, null);
        while (rSet.next()) {
            String id = rSet.getString(VirtualRouting.ID_COLUMN_NAME);
            storageSource.deleteRow(VirtualRouting.VNS_ACL_TABLE_NAME, id);
        }
        rSet = 
            storageSource.executeQuery(VirtualRouting.VNS_ACL_ENTRY_TABLE_NAME,
                new String[]{VirtualRouting.ID_COLUMN_NAME},
                null, null);
        while (rSet.next()) {
            String id = rSet.getString(VirtualRouting.ID_COLUMN_NAME);
            storageSource.deleteRow(VirtualRouting.VNS_ACL_ENTRY_TABLE_NAME, id);
        }
        rSet = 
            storageSource.executeQuery(VirtualRouting.VNS_INTERFACE_ACL_TABLE_NAME,
                new String[]{VirtualRouting.ID_COLUMN_NAME},
                null, null);
        while (rSet.next()) {
            String id = rSet.getString(VirtualRouting.ID_COLUMN_NAME);
            storageSource.deleteRow(VirtualRouting.VNS_INTERFACE_ACL_TABLE_NAME, id);
        }
    }

    private static final Map<String, Object> acl1;
    static {  // for ip, ipproto, icmp tests
        acl1 = new HashMap<String, Object>();
        acl1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl1");
        acl1.put(VirtualRouting.NAME_COLUMN_NAME, "acl1");
        acl1.put(VirtualRouting.PRIORITY_COLUMN_NAME, 1000);
    }
    private static final Map<String, Object> acl2;
    static {  // for priority test, override acl1 via if1/if2
        acl2 = new HashMap<String, Object>();
        acl2.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl2");
        acl2.put(VirtualRouting.NAME_COLUMN_NAME, "acl2");
        acl2.put(VirtualRouting.PRIORITY_COLUMN_NAME, 2000);
    }
    private static final Map<String, Object> acl3;
    static {  // test default deny of empty acl
        acl3 = new HashMap<String, Object>();
        acl3.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl3");
        acl3.put(VirtualRouting.NAME_COLUMN_NAME, "acl3");
        acl3.put(VirtualRouting.PRIORITY_COLUMN_NAME, 3000);
    }
    private static final Map<String, Object> acl4;
    static {  // test udp/tcp rules
        acl4 = new HashMap<String, Object>();
        acl4.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl4");
        acl4.put(VirtualRouting.NAME_COLUMN_NAME, "acl4");
        acl4.put(VirtualRouting.PRIORITY_COLUMN_NAME, 4000);
    }
    private static final Map<String, Object> acl5;
    static {  // test mac rules
        acl5 = new HashMap<String, Object>();
        acl5.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl5");
        acl5.put(VirtualRouting.NAME_COLUMN_NAME, "acl5");
        acl5.put(VirtualRouting.PRIORITY_COLUMN_NAME, 4000);
    }
    
    private static final Map<String, Object> entry1_1;
    static {  // ip: deny 192.168.1.1->.2
        entry1_1 = new HashMap<String, Object>();
        entry1_1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl1|210");
        entry1_1.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl1");
        entry1_1.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "210");
        entry1_1.put(VirtualRouting.TYPE_COLUMN_NAME, "ip");
        entry1_1.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry1_1.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.1");
        entry1_1.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.0");
        entry1_1.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.2");
        entry1_1.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.0");
    }
    private static final Map<String, Object> entry1_2;
    static {  // ip: deny 192.168.1.2->.1
        entry1_2 = new HashMap<String, Object>();
        entry1_2.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl1|220");
        entry1_2.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl1");
        entry1_2.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "220");
        entry1_2.put(VirtualRouting.TYPE_COLUMN_NAME, "ip");
        entry1_2.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry1_2.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.2");
        entry1_2.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.0");
        entry1_2.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.1");
        entry1_2.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.0");
    }
    private static final Map<String, Object> entry1_3;
    static {  // ip: permit 192.168.1.0/24 subnet
        entry1_3 = new HashMap<String, Object>();
        entry1_3.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl1|230");
        entry1_3.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl1");
        entry1_3.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "230");
        entry1_3.put(VirtualRouting.TYPE_COLUMN_NAME, "ip");
        entry1_3.put(VirtualRouting.ACTION_COLUMN_NAME, "permit");
        entry1_3.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.2");
        entry1_3.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry1_3.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.1");
        entry1_3.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.255");
    }
    private static final Map<String, Object> entry1_4;
    static {  // ipproto: deny protocol 13 (ARGUS)
        entry1_4 = new HashMap<String, Object>();
        entry1_4.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl1|110");
        entry1_4.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl1");
        entry1_4.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "110");
        entry1_4.put(VirtualRouting.TYPE_COLUMN_NAME, "13");  // some random protocol
        entry1_4.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry1_4.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.1");
        entry1_4.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.0");
        entry1_4.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.3");
        entry1_4.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.0");
    }
    private static final Map<String, Object> entry1_5;
    static {  // icmp: deny type 8
        entry1_5 = new HashMap<String, Object>();
        entry1_5.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl1|120");
        entry1_5.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl1");
        entry1_5.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "120");
        entry1_5.put(VirtualRouting.TYPE_COLUMN_NAME, "icmp");
        entry1_5.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry1_5.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.1");
        entry1_5.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.0");
        entry1_5.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.3");
        entry1_5.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.0");
        entry1_5.put(VirtualRouting.ICMP_TYPE_COLUMN_NAME, 8);
    }
    private static final Map<String, Object> entry2_1;
    static {  // ip: deny traffic within subnet 192.168.1.0/24
        entry2_1 = new HashMap<String, Object>();
        entry2_1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl2|20");
        entry2_1.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl2");
        entry2_1.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "20");
        entry2_1.put(VirtualRouting.TYPE_COLUMN_NAME, "ip");
        entry2_1.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry2_1.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.2");
        entry2_1.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry2_1.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.1");
        entry2_1.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.255");
    }
    private static final Map<String, Object> entry2_2;
    static {  // ip: deny traffic within subnet 192.168.1.0/24
        entry2_2 = new HashMap<String, Object>();
        entry2_2.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl2|120");
        entry2_2.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl2");
        entry2_2.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "120");
        entry2_2.put(VirtualRouting.TYPE_COLUMN_NAME, "ip");
        entry2_2.put(VirtualRouting.ACTION_COLUMN_NAME, "permit");
    }
    private static final Map<String, Object> entry4_1;
    static {  // udp: deny 5000->5001
        entry4_1 = new HashMap<String, Object>();
        entry4_1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl4|120");
        entry4_1.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl4");
        entry4_1.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "120");
        entry4_1.put(VirtualRouting.TYPE_COLUMN_NAME, "udp");
        entry4_1.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry4_1.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.0");
        entry4_1.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_1.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.0");
        entry4_1.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_1.put(VirtualRouting.SRC_TP_PORT_OP_COLUMN_NAME, "eq");
        entry4_1.put(VirtualRouting.SRC_TP_PORT_COLUMN_NAME, 5000);
        entry4_1.put(VirtualRouting.DST_TP_PORT_OP_COLUMN_NAME, "eq");
        entry4_1.put(VirtualRouting.DST_TP_PORT_COLUMN_NAME, 5001);
    }
    private static final Map<String, Object> entry4_2;
    static {  // tcp: deny all except 5000->5001
        entry4_2 = new HashMap<String, Object>();
        entry4_2.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl4|150");
        entry4_2.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl4");
        entry4_2.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "150");
        entry4_2.put(VirtualRouting.TYPE_COLUMN_NAME, "tcp");
        entry4_2.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry4_2.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.0");
        entry4_2.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_2.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.0");
        entry4_2.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_2.put(VirtualRouting.SRC_TP_PORT_OP_COLUMN_NAME, "neq");
        entry4_2.put(VirtualRouting.SRC_TP_PORT_COLUMN_NAME, 5000);
        entry4_2.put(VirtualRouting.DST_TP_PORT_OP_COLUMN_NAME, "neq");
        entry4_2.put(VirtualRouting.DST_TP_PORT_COLUMN_NAME, 5001);
    }
    private static final Map<String, Object> entry4_3;
    static {  // udp: permit any->any
        entry4_3 = new HashMap<String, Object>();
        entry4_3.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl4|220");
        entry4_3.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl4");
        entry4_3.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "220");
        entry4_3.put(VirtualRouting.TYPE_COLUMN_NAME, "udp");
        entry4_3.put(VirtualRouting.ACTION_COLUMN_NAME, "permit");
        entry4_3.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.0");
        entry4_3.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_3.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.0");
        entry4_3.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_3.put(VirtualRouting.SRC_TP_PORT_OP_COLUMN_NAME, "any");
        entry4_3.put(VirtualRouting.DST_TP_PORT_OP_COLUMN_NAME, "any");
    }
    private static final Map<String, Object> entry4_4;
    static {  // tcp: permit any->any
        entry4_4 = new HashMap<String, Object>();
        entry4_4.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl4|250");
        entry4_4.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl4");
        entry4_4.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "250");
        entry4_4.put(VirtualRouting.TYPE_COLUMN_NAME, "tcp");
        entry4_4.put(VirtualRouting.ACTION_COLUMN_NAME, "permit");
        entry4_4.put(VirtualRouting.SRC_IP_COLUMN_NAME, "192.168.1.0");
        entry4_4.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_4.put(VirtualRouting.DST_IP_COLUMN_NAME, "192.168.1.0");
        entry4_4.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, "0.0.0.255");
        entry4_4.put(VirtualRouting.SRC_TP_PORT_OP_COLUMN_NAME, "any");
        entry4_4.put(VirtualRouting.DST_TP_PORT_OP_COLUMN_NAME, "any");
    }
    private static final Map<String, Object> entry5_1;
    static {  // mac: permit ARP :01->:03
        entry5_1 = new HashMap<String, Object>();
        entry5_1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl5|20");
        entry5_1.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl5");
        entry5_1.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "20");
        entry5_1.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        entry5_1.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry5_1.put(VirtualRouting.SRC_MAC_COLUMN_NAME, "00:00:00:00:00:01");
        entry5_1.put(VirtualRouting.DST_MAC_COLUMN_NAME, "00:00:00:00:00:03");
        entry5_1.put(VirtualRouting.ETHER_TYPE_COLUMN_NAME, Ethernet.TYPE_ARP);
    }
    private static final Map<String, Object> entry5_2;
    static {  // mac: deny IP any->:03
        entry5_2 = new HashMap<String, Object>();
        entry5_2.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl5|40");
        entry5_2.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl5");
        entry5_2.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "40");
        entry5_2.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        entry5_2.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry5_2.put(VirtualRouting.DST_MAC_COLUMN_NAME, "00:00:00:00:00:03");
        entry5_2.put(VirtualRouting.ETHER_TYPE_COLUMN_NAME, Ethernet.TYPE_IPv4);
    }
    private static final Map<String, Object> entry5_3;
    static {  // mac: permit all
        entry5_3 = new HashMap<String, Object>();
        entry5_3.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl5|520");
        entry5_3.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl5");
        entry5_3.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "520");
        entry5_3.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        entry5_3.put(VirtualRouting.ACTION_COLUMN_NAME, "permit");
        entry5_3.put(VirtualRouting.ETHER_TYPE_COLUMN_NAME, 0x10000);
    }
    private static final Map<String, Object> entry5_4;
    static {  // mac: deny broadcast from :01
        entry5_4 = new HashMap<String, Object>();
        entry5_4.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl5|200");
        entry5_4.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl5");
        entry5_4.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "200");
        entry5_4.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        entry5_4.put(VirtualRouting.ACTION_COLUMN_NAME, "permit");
        entry5_4.put(VirtualRouting.SRC_MAC_COLUMN_NAME, "00:00:00:00:00:01");
        entry5_4.put(VirtualRouting.DST_MAC_COLUMN_NAME, "ff:ff:ff:ff:ff:ff");
    }
    private static final Map<String, Object> entry5_5;
    static {  // mac: permit broadcast from :03
        entry5_5 = new HashMap<String, Object>();
        entry5_5.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|acl5|210");
        entry5_5.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME, "netVirt|acl5");
        entry5_5.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "210");
        entry5_5.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        entry5_5.put(VirtualRouting.ACTION_COLUMN_NAME, "deny");
        entry5_5.put(VirtualRouting.SRC_MAC_COLUMN_NAME, "00:00:00:00:00:03");
        entry5_5.put(VirtualRouting.DST_MAC_COLUMN_NAME, "ff:ff:ff:ff:ff:ff");
    }
    
    // NetVirtInterfae to ACL associations
    private static final Map<String, Object> ifAcl1in;
    static {
        ifAcl1in = new HashMap<String, Object>();
        ifAcl1in.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if1|netVirt|acl1|in");
        ifAcl1in.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if1");
        ifAcl1in.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl1");
        ifAcl1in.put(VirtualRouting.IN_OUT_COLUMN_NAME, "in");
        ifAcl1in.put(VirtualRouting.PRIORITY_COLUMN_NAME, 100);
    }
    private static final Map<String, Object> ifAcl1out;
    static {
        ifAcl1out = new HashMap<String, Object>();
        ifAcl1out.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if1|netVirt|acl1|out");
        ifAcl1out.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if1");
        ifAcl1out.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl1");
        ifAcl1out.put(VirtualRouting.IN_OUT_COLUMN_NAME, "out");
    }
    private static final Map<String, Object> ifAcl2in;
    static {
        ifAcl2in = new HashMap<String, Object>();
        ifAcl2in.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if2|netVirt|acl2|in");
        ifAcl2in.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if2");
        ifAcl2in.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl2");
        ifAcl2in.put(VirtualRouting.IN_OUT_COLUMN_NAME, "in");
    }
    private static final Map<String, Object> ifAcl2out;
    static {
        ifAcl2out = new HashMap<String, Object>();
        ifAcl2out.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if2|netVirt|acl2|out");
        ifAcl2out.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if2");
        ifAcl2out.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl2");
        ifAcl2out.put(VirtualRouting.IN_OUT_COLUMN_NAME, "out");
    }
    private static final Map<String, Object> ifAcl3out;
    static {
        ifAcl3out = new HashMap<String, Object>();
        ifAcl3out.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if3|netVirt|acl3|out");
        ifAcl3out.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if3");
        ifAcl3out.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl3");
        ifAcl3out.put(VirtualRouting.IN_OUT_COLUMN_NAME, "out");
    }
    private static final Map<String, Object> ifAcl4in;
    static {
        ifAcl4in = new HashMap<String, Object>();
        ifAcl4in.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if4|netVirt|acl4|in");
        ifAcl4in.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if4");
        ifAcl4in.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl4");
        ifAcl4in.put(VirtualRouting.IN_OUT_COLUMN_NAME, "in");
    }
    private static final Map<String, Object> ifAcl5in;
    static {
        ifAcl5in = new HashMap<String, Object>();
        ifAcl5in.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|if5|netVirt|acl5|in");
        ifAcl5in.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|if5");
        ifAcl5in.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|acl5");
        ifAcl5in.put(VirtualRouting.IN_OUT_COLUMN_NAME, "in");
    }
    
    // NetVirt interfaces
    private static final VNS netVirt = new VNS("netVirt");
    private static final VNSInterface if2 = new VNSInterface("if2", netVirt, null, null);;
    private static final VNSInterface if1 = new VNSInterface("if1", netVirt, null, if2);
    private static final VNSInterface if3 = new VNSInterface("if3", netVirt, null, null);
    private static final VNSInterface if4 = new VNSInterface("if4", netVirt, null, if2);
    private static final VNSInterface if5 = new VNSInterface("if5", netVirt, null, null);
    
    protected IPacket udpPacket1, udpPacket2, udpPacket3;
    protected byte[] udpPacketSerialized1, udpPacketSerialized2, udpPacketSerialized3;
    protected IPacket tcpPacket1, tcpPacket2, tcpPacket3;
    protected byte[] tcpPacketSerialized1, tcpPacketSerialized2, tcpPacketSerialized3;
    protected IPacket ipprotoPacket, icmpPacket1, icmpPacket2;
    protected byte[] ipprotoPacketSerialized, icmpPacketSerialized1, icmpPacketSerialized2;
    protected IPacket arpPacket, arpPacket1, arpPacket2;
    protected byte[] arpPacketSerialized, arpPacketSerialized1, arpPacketSerialized2;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        virtualRouting = new VirtualRouting();
        netVirtManager = createMock(INetVirtManagerService.class);
        tunnelManager = createMock(ITunnelManagerService.class);
        // TODO: this should not be a nice mock! we should set up the 
        // expectation for each of the test cases to check if they are 
        // handled correctly.
        betterFlowCacheMgr = createNiceMock(IFlowCacheService.class);
        flowRecocileMgr = createMock(IFlowReconcileService.class);
        topology = createMock(ITopologyService.class);
        RestApiServer ras = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        
        fmc = new ModuleContext();
        fmc.addService(IControllerService.class, mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(IVirtualRoutingService.class, virtualRouting);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IFlowCacheService.class, betterFlowCacheMgr);
        fmc.addService(IFlowReconcileService.class, flowRecocileMgr);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(ITunnelManagerService.class, tunnelManager);

        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        virtualRouting.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        tp.init(fmc);
        entityClassifier.init(fmc);
        
        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        virtualRouting.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        
        // mock services
        // topology
        expect(topology.isAttachmentPointPort(anyLong(), anyShort()))
                .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.isBroadcastDomainPort(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        expect(topology.isConsistent(anyLong(), anyShort(), anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(anyLong(), anyShort(), anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        // flow reconcile manager 
        flowRecocileMgr.addFlowReconcileListener(
                anyObject(IFlowReconcileListener.class));
        expectLastCall().anyTimes();
        // netVirt manager
        expect(netVirtManager.getBroadcastSwitchPorts())
                .andReturn(new ArrayList<SwitchPort>()).anyTimes();
        
        // TODO: betterFlowCacheMgr should be mocked by each individual test
        replay(topology, flowRecocileMgr, betterFlowCacheMgr, netVirtManager);

        expect(tunnelManager.isTunnelEndpoint(anyObject(IDevice.class)))
                            .andReturn(false).anyTimes();
        replay(tunnelManager);

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
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));
        this.udpPacketSerialized1 = udpPacket1.serialize();

        this.udpPacket2 = new Ethernet()
            .setDestinationMACAddress("00:00:00:00:00:03")
            .setSourceMACAddress("00:00:00:00:00:01")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.3")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 33033)));
        this.udpPacketSerialized2 = udpPacket2.serialize();
        
        this.udpPacket3 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:03")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.3")
            .setPayload(new UDP()
                        .setSourcePort((short) 0)
                        .setDestinationPort((short) 33033)));
        this.udpPacketSerialized3 = udpPacket3.serialize();
        
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
            .setPayload(new TCP()
                        .setSourcePort((short) 5000)
                        .setDestinationPort((short) 5001)
                        .setPayload(new Data(new byte[] {0x01}))));
        this.tcpPacketSerialized1 = tcpPacket1.serialize();

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
                .setPayload(new TCP()
                            .setSourcePort((short) 33000)
                            .setDestinationPort((short) 5001)));
        this.tcpPacketSerialized2 = tcpPacket2.serialize();
        
        this.tcpPacket3 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:03")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.3")
            .setPayload(new TCP()
                        .setSourcePort((short) 33000)
                        .setDestinationPort((short) 0)));
        this.tcpPacketSerialized3 = tcpPacket3.serialize();
   
        this.ipprotoPacket = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:03")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setProtocol((byte) 13)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.3")
            .setPayload(new Data(new byte[] {1, 2, 3, 4, 5, 6, 8, 9, 10})));
        this.ipprotoPacketSerialized = ipprotoPacket.serialize();
        
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
            .setPayload(new ICMP()
                        .setIcmpType((byte) 8)
                        .setPayload(new Data(new byte[] {1, 2, 3}))));
        this.icmpPacketSerialized1 = icmpPacket1.serialize();
        
        this.icmpPacket2 = new Ethernet()
        .setDestinationMACAddress("00:00:00:00:00:03")
        .setSourceMACAddress("00:00:00:00:00:01")
        .setVlanID((short) 42)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
            new IPv4()
            .setTtl((byte) 128)
            .setSourceAddress("192.168.1.1")
            .setDestinationAddress("192.168.1.3")
            .setPayload(new ICMP()
                        .setIcmpType((byte) 0)
                        .setPayload(new Data(new byte[] {1, 2, 3}))));
        this.icmpPacketSerialized2 = icmpPacket2.serialize();
        
        // Not a real ARP packet, just need the mac header for testing
        this.arpPacket = new Ethernet()
            .setDestinationMACAddress("00:00:00:00:00:03")
            .setSourceMACAddress("00:00:00:00:00:01")
            .setEtherType(Ethernet.TYPE_ARP)
            .setPad(true);
        this.arpPacketSerialized = arpPacket.serialize();
        
        // Not a real ARP packet, test broadcast ACL (input only)
        this.arpPacket1 = new Ethernet()
            .setDestinationMACAddress("ff:ff:ff:ff:ff:ff")
            .setSourceMACAddress("00:00:00:00:00:01")
            .setEtherType(Ethernet.TYPE_ARP)
            .setPad(true);
        this.arpPacketSerialized1 = arpPacket1.serialize();
        this.arpPacket2 = new Ethernet()
            .setDestinationMACAddress("ff:ff:ff:ff:ff:ff")
            .setSourceMACAddress("00:00:00:00:00:03")
            .setEtherType(Ethernet.TYPE_ARP)
            .setPad(true);
        this.arpPacketSerialized2 = arpPacket2.serialize();
    }

    protected VirtualRouting getVirtualRouting() {
        return virtualRouting;
    }
    
    protected IStorageSourceService getStorageSource() {
        return storageSource;
    }

    /**
     * Add acl entries to storage at the beginning of each test
     */
    private void addAcls(AclTest test) {

        if (macAcl1 != null) {
            test.addAcl(macAcl1);
            test.addAclEntry(macAclEntry1, macAclEntry2);
        } else {
            test.addAcl(acl1, acl2, acl3, acl4, acl5);
            test.addAclEntry(entry1_1, entry1_2, entry1_3, entry1_4, entry1_5);
            test.addAclEntry(entry2_1, entry2_2);
            test.addAclEntry(entry4_1, entry4_2, entry4_3, entry4_4);
            test.addAclEntry(entry5_1, entry5_2, entry5_3, entry5_4, entry5_5);
        }
    }
    
    /**
     * Common mockup environment for unicast tests
     */
    private void testAclInternal(OFPacketIn pi, ListenerContext cntx, 
                                 RoutingAction action, byte[] packetSerialized)
                                 throws Exception {
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();

        VirtualRouting routing = getVirtualRouting();
        IControllerService mockControllerProvider = createMock(IControllerService.class);
        routing.setControllerProvider(mockControllerProvider);
        routing.setStorageSource(getStorageSource());
        routing.readAclTablesFromStorage();
        
        // Start recording the replay on the mocks      
        replay(mockSwitch);

        Ethernet eth = new Ethernet();

        eth.deserialize(packetSerialized, 0, packetSerialized.length);

        Integer srcIpAddr = null, dstIpAddr = null;

        if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
            srcIpAddr = ((IPv4) eth.getPayload()).getSourceAddress();
            dstIpAddr = ((IPv4) eth.getPayload()).getDestinationAddress();
        }

        IDevice src = null, dst = null;

        /*
         * Learn pkt's source entity device, unless we need to.
         */
        if (!skipSourceEntityLearn) {
            src = mockDeviceManager.learnEntity(
                      Ethernet.toLong(eth.getSourceMACAddress()), null,
                      srcIpAddr, 1L, 1);
        }

        /*
         * Learn pkt's destination entity device, unless we need to.
         */
        if (!skipDestEntityLearn) {
            dst = mockDeviceManager.learnEntity(
                      Ethernet.toLong(eth.getDestinationMACAddress()), null,
                      dstIpAddr, 1L, 2);
        }
        
        // Trigger the packet in
        routing.receive(mockSwitch, pi, parseAndAnnotate(cntx, pi, src, dst));

        // Get the annotation output for verification
        IRoutingDecision d = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);

        // Verify the replay matched our expectations      
        verify(mockSwitch);

        if (action == RoutingAction.NONE) {
            assertNull(d);
        } else {
            assertNotNull(d);
            assertEquals(action, d.getRoutingAction());
            assertEquals(1L, d.getSourcePort().getSwitchDPID());
            assertEquals(pi.getInPort(), d.getSourcePort().getPort());
        }
    }
    
    /**
     * Common implementation for setting up interfaces and if-Acl associations
     */
    
    public void testAclCommon(VNSInterface iface, Map<String, Object> ifToAcl,
            byte[] packetSerialized, RoutingAction action) throws Exception {
        testAclCommon(iface, ifToAcl, packetSerialized, action, null);
    }
        
        
    public void testAclCommon(VNSInterface iface, Map<String, Object> ifToAcl,
            byte[] packetSerialized, RoutingAction action, ListenerContext paramCntx) throws Exception {
        ListenerContext cntx;
        if (paramCntx == null) {
            cntx = new ListenerContext();
        } else {
            cntx = paramCntx;
        }
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        ifaces.add(iface);
        INetVirtManagerService.bcStore.put(cntx, 
                                       INetVirtManagerService.CONTEXT_SRC_IFACES, 
                                       ifaces);
        INetVirtManagerService.bcStore.put(cntx, 
                                       INetVirtManagerService.CONTEXT_DST_IFACES, 
                                       ifaces);
        
        OFPacketIn pi = new OFPacketIn()
             .setBufferId(-1)
             .setInPort((short) 1)
             .setPacketData(packetSerialized)
             .setReason(OFPacketInReason.NO_MATCH)
             .setTotalLength((short) packetSerialized.length);
                
        // Clear ACL from storage
        IStorageSourceService storageSource = getStorageSource();
        clearStorage(storageSource);
        AclTest test = new AclTest();
        addAcls(test);
        test.addIfAcl(ifToAcl);
        test.writeToStorage(storageSource);

        testAclInternal(pi, cntx, action, packetSerialized);
    }
    
    /**
     * No ACL, should pass by default
     */
    @Test
    public void testNoAcl() throws Exception {
        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        ifaces.add(if1);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, ifaces);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, ifaces);
        
        OFPacketIn pi = new OFPacketIn()
             .setBufferId(-1)
             .setInPort((short) 1)
             .setPacketData(udpPacketSerialized1)
             .setReason(OFPacketInReason.NO_MATCH)
             .setTotalLength((short) udpPacketSerialized1.length);
                
        // Clear ACL from storage
        IStorageSourceService storageSource = getStorageSource();
        clearStorage(storageSource);
        
        testAclInternal(pi, cntx, RoutingAction.FORWARD, udpPacketSerialized1);
    }
    
    /**
     * Add ACL with no entry, default to deny, should drop
     */
    @Test
    public void testDefaultDeny() throws Exception {
        testAclCommon(if3, ifAcl3out, udpPacketSerialized2, RoutingAction.DROP);
    }
    
    /**
     * Add ACL ip exact: match on input, should drop
     */
    @Test
    public void testIpExactIn() throws Exception {
        testAclCommon(if1, ifAcl1in, udpPacketSerialized1, RoutingAction.DROP);
    }
    
    /**
     * Add ACL ip exact: match on output, should drop
     */
    @Test
    public void testIpExactOut() throws Exception {
        testAclCommon(if1, ifAcl1out, udpPacketSerialized1, RoutingAction.DROP);
    }
    
    /**
     * Add ACL: match on subnet on output, should pass
     */
    @Test
    public void testIpSubnetMatch() throws Exception {
        testAclCommon(if1, ifAcl1out, udpPacketSerialized2, RoutingAction.FORWARD);
    }
    
    /**
     * Same as above except we add ACL for parent interface (if2).
     * Since if2 acl has a higher priority, and matches with deny,
     * the packet should should be dropped.
     */
    @Test
    public void testInterfacePriority() throws Exception {
        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        ifaces.add(if1);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, ifaces);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, ifaces);
        
        OFPacketIn pi = new OFPacketIn()
             .setBufferId(-1)
             .setInPort((short) 1)
             .setPacketData(udpPacketSerialized2)
             .setReason(OFPacketInReason.NO_MATCH)
             .setTotalLength((short) udpPacketSerialized2.length);

        // Setup ACL
        IStorageSourceService storageSource = getStorageSource();
        clearStorage(storageSource);
        AclTest test = new AclTest();
        addAcls(test);
        test.addIfAcl(ifAcl1out);
        test.addIfAcl(ifAcl2out);
        test.writeToStorage(storageSource);
        
        testAclInternal(pi, cntx, RoutingAction.DROP, udpPacketSerialized2);
    }
    
    /**
     * Test ipproto match ("13"), should drop
     */
    @Test
    public void testIpprotoMatch() throws Exception {
        testAclCommon(if1, ifAcl1out, ipprotoPacketSerialized, RoutingAction.DROP);
    }
    
    /**
     * Test icmp match type 8, should drop
     */
    @Test
    public void testIcmpMatch() throws Exception {
        testAclCommon(if1, ifAcl1out, icmpPacketSerialized1, RoutingAction.DROP);
    }
    
    /**
     * Test icmp mismatch type 0, should forward
     */
    @Test
    public void testIcmpNoMatch() throws Exception {
        testAclCommon(if1, ifAcl1out, icmpPacketSerialized2, RoutingAction.FORWARD);
    }
      
    /**
     * Test udp matched port, should deny
     */
    @Test
    public void testUdpPortEqMatch() throws Exception {
        testAclCommon(if4, ifAcl4in, udpPacketSerialized1, RoutingAction.DROP);
    }
    
    /**
     * Test udp match on dst port only, should forward
     */
    @Test
    public void testUdpPortEqPartialMatch() throws Exception {
        testAclCommon(if4, ifAcl4in, udpPacketSerialized2, RoutingAction.FORWARD);
    }
    
    /**
     * Test udp match to any port, should forward
     */
    @Test
    public void testUdpPortEqNoMatch() throws Exception {
        testAclCommon(if4, ifAcl4in, udpPacketSerialized3, RoutingAction.FORWARD);
    }
    
    /**
     * Test tcp mismatched port, should forward
     */
    @Test
    public void testTcpPortNeqNoMatch() throws Exception {
        testAclCommon(if4, ifAcl4in, tcpPacketSerialized1, RoutingAction.FORWARD);
    }
    
    /**
     * Test tcp matched port, should forward
     */
    @Test
    public void testTcpPortNeqPartialMatch() throws Exception {
        testAclCommon(if4, ifAcl4in, tcpPacketSerialized2, RoutingAction.FORWARD);
    }
    
    /**
     * Test tcp matched port, should deny
     */
    @Test
    public void testTcpPortNeqMatch() throws Exception {
        testAclCommon(if4, ifAcl4in, tcpPacketSerialized3, RoutingAction.DROP);
    }
    
    /**
     * Test matched mac and ethertype, should deny
     */
    @Test
    public void testMacArpMatch() throws Exception {
        testAclCommon(if5, ifAcl5in, arpPacketSerialized, RoutingAction.DROP);
    }
    
    /**
     * Test mac src wildcard, exact dst, should deny
     */
    @Test
    public void testMacIpMatch() throws Exception {
        testAclCommon(if5, ifAcl5in, udpPacketSerialized2, RoutingAction.DROP);
    }
    
    /**
     * Test mac src/dst wildcard, should permit
     */
    @Test
    public void testMacWildcard() throws Exception {
        testAclCommon(if5, ifAcl5in, udpPacketSerialized1, RoutingAction.FORWARD);
    }
    
    /**
     * Test broadcast ACL, should allow
     */
    @Test
    public void testBroadcast() throws Exception {
        testAclCommon(if5, ifAcl5in, arpPacketSerialized1, RoutingAction.MULTICAST);
    }
    
    /**
     * Test broadcast ACL deny, in this cast there is no action annotation
     */
    @Test
    public void testBroadcastDROP() throws Exception {
        testAclCommon(if5, ifAcl5in, arpPacketSerialized2, RoutingAction.NONE);
    }

    /*
     * Private variables used in MAC address matching ACL tests below.
     */
    private boolean                 skipSourceEntityLearn;
    private boolean                 skipDestEntityLearn;
    private VNSInterface            macNetVirtIf1;
    private Map<String, Object>     macIf1;
    private HashMap<String, Object> macAcl1 = null;
    private Map<String, Object>     macAclEntry1 = null;
    private Map<String, Object>     macAclEntry2 = null;

    /*
     * macAclTestInit
     *
     * Initialize the acl and interface access-group used in the mac tests
     * that follow.
     */
    private void macAclTestsInit () {
        macAcl1 = new HashMap<String, Object>();

        macAcl1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|macAcl1");
        macAcl1.put(VirtualRouting.NAME_COLUMN_NAME, "macAcl1");
        macAcl1.put(VirtualRouting.PRIORITY_COLUMN_NAME, 1000);

        macNetVirtIf1 = new VNSInterface("macNetVirtIf1", netVirt, null, null);

        macIf1 = new HashMap<String, Object>();
        macIf1.put(VirtualRouting.ID_COLUMN_NAME,
                   "netVirt|macNetVirtIf1|netVirt|macAcl1|in");
        macIf1.put(VirtualRouting.INTERFACE_COLUMN_NAME, "netVirt|macNetVirtIf1");
        macIf1.put(VirtualRouting.ACL_NAME_COLUMN_NAME, "netVirt|macAcl1");
        macIf1.put(VirtualRouting.IN_OUT_COLUMN_NAME, "in");
    }

    /*
     * runMacAclTests
     *
     * Run variuos mac address match based ACL tests.
     */
    private void runMacAclTests (
                     String        asMac,         /* Configured source mac */
                     String        adMac,         /* Configured dest mac */
                     int           avlan,         /* Configured vlan id */
                     short         aetherType,    /* Configured ether type */
                     String        aaction,       /* Configured match action */

                     boolean       learnSource,   /* Should src dvc be null ? */
                     boolean       learnDest,     /* Should dst dvc be null ? */

                     String        psMac,         /* Pkt's source mac */ 
                     String        pdMac,         /* Pkt's dest mac */
                     int           pvlan,         /* Pkt's vlan id */
                     short         petherType,    /* Pkt's ether type */
                     RoutingAction routingAction) /* Expected routing action */
    {
        IPacket samplePkt;

        /*
         * Create a sample packet to feed based on the packet parameters passed
         * in.
         */
        samplePkt = new Ethernet()
            .setDestinationMACAddress(psMac)
            .setSourceMACAddress(pdMac)
            .setVlanID((short) pvlan)
            .setEtherType(petherType)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));

        byte[] samplePktSerialized = samplePkt.serialize();

        /*
         * Create acl entries configured as desired.
         */
        macAclEntry1 = new HashMap<String, Object>();
        macAclEntry1.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|macAcl1|10");
        macAclEntry1.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME,
                           "netVirt|macAcl1");
        macAclEntry1.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "10");
        macAclEntry1.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        macAclEntry1.put(VirtualRouting.SRC_MAC_COLUMN_NAME, asMac);
        macAclEntry1.put(VirtualRouting.DST_MAC_COLUMN_NAME, adMac);
        macAclEntry1.put(VirtualRouting.VLAN_COLUMN_NAME, avlan);
        macAclEntry1.put(VirtualRouting.ETHER_TYPE_COLUMN_NAME, aetherType);
        macAclEntry1.put(VirtualRouting.ACTION_COLUMN_NAME, aaction);

        /*
         * Have a default explicit rule that does the opposite of the above
         * rule. i.e, if the above rule tries to match and 'permit', do a 
         * match all 'deny', and vice versa.
         */
        macAclEntry2 = new HashMap<String, Object>();
        macAclEntry2.put(VirtualRouting.ID_COLUMN_NAME, "netVirt|macAcl1|20");
        macAclEntry2.put(VirtualRouting.ACL_ENTRY_VNS_ACL_COLUMN_NAME,
                         "netVirt|macAcl1");
        macAclEntry2.put(VirtualRouting.ACL_ENTRY_RULE_COLUMN_NAME, "20");
        macAclEntry2.put(VirtualRouting.TYPE_COLUMN_NAME, "mac");
        macAclEntry2.put(VirtualRouting.SRC_MAC_COLUMN_NAME, "");
        macAclEntry2.put(VirtualRouting.DST_MAC_COLUMN_NAME, "");
        macAclEntry2.put(VirtualRouting.ETHER_TYPE_COLUMN_NAME, aetherType);
        macAclEntry2.put(VirtualRouting.ACTION_COLUMN_NAME,
                         aaction.equals("permit") ? "deny" : "permit");

        /*
         * Set the private variables appropriately, so that later in the call
         * chain, source and/or destination device entity learning process is
         * skipped, if necessary for this test run.
         */
        skipSourceEntityLearn = learnSource;
        skipDestEntityLearn   = learnDest;

        /*
         * Invoke the packet feed and acl match routines.
         */
        try {
            testAclCommon(macNetVirtIf1, macIf1, samplePktSerialized,
                          routingAction);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return;
    }

    /*
     * testMacAclTests
     *
     * Fields:
     *     aSrcMac aDstMac aVlan aEtherType pSrcMac pDstMac pVlan pEtherType
     *
     * Values:
     *     pMac      : Known and UnKnown, matching and non-matching
     *                     Unicast, Multicast and Broadcast MAC addresses
     *     pVlan     : <0 - 4095> {0, 10, 4096}
     *     pEtherType: <0 - 255>  {IPv4, ARP, IPv6}
     *
     *     aMac      : Unicast, Multicast and Broadcast
     *     aVlan     : <0 - 4095>
     *     aEtherType: <0 - 255>
     *     aAction   : permit, deny
     *
     * Action:
     *     enum RoutingAction
     *
     * XXX Generate more tests, given the core logic already provided above.
     *
     */
    @Test
    public void testMacAclTests () throws Exception {
        macAclTestsInit();

        /*
         * XXX These calls have been intentionally written in very wide lines,
         * so that tests comparison is easy, in wide terminals.
         */

        /*
         * Run tests with both source and destination entities learnt.
         */
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", false, false, "00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.FORWARD); // Match and forward
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "deny",   false, false, "00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.DROP);    // Match and drop
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "deny",   false, false, "00:00:00:00:00:01", "00:00:00:00:00:02", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.FORWARD); // No-Match and forward (by second all wild cards rule)
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", false, false, "00:00:00:00:00:01", "00:00:00:00:00:02", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.DROP);    // No-Match and drop (by second all wild cards rule)

        /*
         * Run tests with source and/or dest entities learning skipped, for unicast and broadcast addresses.
         */
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", false, false, "00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.FORWARD); // Match and forward
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", false, true,  "00:00:00:00:00:01", "00:00:00:00:00:02", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.FORWARD); // No-Match, yet forward as there is no dest device
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", true,  false, "00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.NONE);    // Match but still deny as there is no source device
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", true,  true,  "00:00:00:00:00:01", "00:00:00:00:00:01", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.NONE);    // Match but still deny as there is no source device

        runMacAclTests("ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", false, false, "ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.MULTICAST); // Match and forward
        runMacAclTests("ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "deny",   false, true,  "ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.NONE);      // Match and drop even though is no dest device
        runMacAclTests("ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", true,  false, "ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.NONE);      // Match but still deny as there is no source device
        runMacAclTests("ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, "permit", true,  true,  "ff:ff:ff:ff:ff:ff", "ff:ff:ff:ff:ff:ff", VNSAccessControlListEntry.VLAN_ALL, Ethernet.TYPE_IPv4, RoutingAction.NONE);      // Match but still deny as there is no source device

        /*
         * Do some tests with valid vlan tags.
         */
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, "permit", false, false, "00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, RoutingAction.FORWARD); // Match and forward
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, "permit", false, true,  "00:00:00:00:00:01", "00:00:00:00:00:02",                                  1, Ethernet.TYPE_IPv4, RoutingAction.FORWARD); // No-Match, yet forward as there is no dest device
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, "permit", true,  false, "00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, RoutingAction.NONE);    // Match but still deby as there is no source device
        runMacAclTests("00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, "permit", true,  true,  "00:00:00:00:00:01", "00:00:00:00:00:01",                                  1, Ethernet.TYPE_IPv4, RoutingAction.NONE);    // Match but still deny as there is no source device -- REVIST THIS


        /*
         * Reset the variables, as mac tests are complete.
         */
        macAcl1      = null;
        macAclEntry1 = null;
        macAclEntry2 = null;
    }

    @Test
    public void testAclIn() throws Exception {
        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        ifaces.add(if3);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, ifaces);
        List<VNSInterface> ifaces2 = new ArrayList<VNSInterface>();
        ifaces2.add(if2);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, ifaces2);
        
        OFPacketIn pi = new OFPacketIn()
             .setBufferId(-1)
             .setInPort((short) 1)
             .setPacketData(udpPacketSerialized1)
             .setReason(OFPacketInReason.NO_MATCH)
             .setTotalLength((short) udpPacketSerialized1.length);

        // Setup ACL
        IStorageSourceService storageSource = getStorageSource();
        clearStorage(storageSource);
        AclTest test = new AclTest();
        addAcls(test);
        test.addIfAcl(ifAcl2in);
        test.writeToStorage(storageSource);
        
        testAclInternal(pi, cntx, RoutingAction.FORWARD, udpPacketSerialized1);
    }
    
    @Test
    public void testAclOut() throws Exception {
        ListenerContext cntx = new ListenerContext();
        List<VNSInterface> ifaces = new ArrayList<VNSInterface>();
        ifaces.add(if3);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, ifaces);
        List<VNSInterface> ifaces2 = new ArrayList<VNSInterface>();
        ifaces2.add(if2);
        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, ifaces2);
        
        OFPacketIn pi = new OFPacketIn()
             .setBufferId(-1)
             .setInPort((short) 1)
             .setPacketData(udpPacketSerialized1)
             .setReason(OFPacketInReason.NO_MATCH)
             .setTotalLength((short) udpPacketSerialized1.length);

        // Setup ACL
        IStorageSourceService storageSource = getStorageSource();
        clearStorage(storageSource);
        AclTest test = new AclTest();
        addAcls(test);
        test.addIfAcl(ifAcl2out);
        test.writeToStorage(storageSource);
        
        testAclInternal(pi, cntx, RoutingAction.DROP, udpPacketSerialized1);
    }
    
    /**
     * Test Acl Annotation for "test packet-in" message
     */
    @Test
    public void testAclAnnotationOfExplainPacket() throws Exception {
     // set appropriate context 
        ListenerContext cntx = new ListenerContext();
        NetVirtExplainPacket.ExplainStore.put(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT, NetVirtExplainPacket.VAL_EXPLAIN_PKT);
        NetVirtExplainPacket.ExplainPktRoute epr = new NetVirtExplainPacket.ExplainPktRoute();
        NetVirtExplainPacket.ExplainRouteStore.put(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE, epr);
        
        testAclCommon(if5, ifAcl5in, udpPacketSerialized1, RoutingAction.FORWARD, cntx);
        
        // Now check if the NetVirt name and Input ACL Name are correctly Annotated
        String aclName = NetVirtExplainPacket.ExplainStore.get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_NAME);
        assertTrue(aclName.equals("acl5"));
        String aclResult = NetVirtExplainPacket.ExplainStore.get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_RESULT);
        assertTrue(aclResult.equals("ACL_PERMIT"));
        String aclEntry = NetVirtExplainPacket.ExplainStore.get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_ENTRY);
        assertTrue(aclEntry.contains("520 permit mac any any"));
        // Check that the NetVirt Name is annotated
        String srcNetVirt = NetVirtExplainPacket.ExplainStore.get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_SRC_NetVirt);
        assertTrue(srcNetVirt.equals("netVirt"));
        String dstNetVirt = NetVirtExplainPacket.ExplainStore.get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_DST_NetVirt);
        assertTrue(dstNetVirt.equals("netVirt"));
        
        // Check the annotation of output ACL
        ListenerContext outCntx = new ListenerContext();        
        NetVirtExplainPacket.ExplainStore.put(outCntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT, NetVirtExplainPacket.VAL_EXPLAIN_PKT);
        NetVirtExplainPacket.ExplainPktRoute outEpr = new NetVirtExplainPacket.ExplainPktRoute();
        NetVirtExplainPacket.ExplainRouteStore.put(outCntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE, outEpr);
        // Run the command
        testAclCommon(if3, ifAcl3out, udpPacketSerialized2, RoutingAction.DROP, outCntx);
        aclName = NetVirtExplainPacket.ExplainStore.get(outCntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_NAME);
        assertTrue(aclName.equals("acl3"));
        aclResult = NetVirtExplainPacket.ExplainStore.get(outCntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_RESULT);
        assertTrue(aclResult.equals("ACL_DENY"));
        aclEntry = NetVirtExplainPacket.ExplainStore.get(outCntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_ENTRY);
        assertTrue(aclEntry.contains("Implicit deny"));
        
        // Check that non Explain Packets are not annotated
        ListenerContext noAnnotateCntx = new ListenerContext();
        testAclCommon(if3, ifAcl3out, udpPacketSerialized2, RoutingAction.DROP, noAnnotateCntx);
        aclName = null;
        aclName = NetVirtExplainPacket.ExplainStore.get(noAnnotateCntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_NAME);
        assertTrue(aclName == null);
        
       
        // Verify that the context contains the NetVirt and ACL annotations
    }
}
