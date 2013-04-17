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

package org.sdnplatform.netvirt.manager.internal;

import static org.easymock.EasyMock.*;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.sdnplatform.addressspace.BetterEntityClass;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.Device;
import org.sdnplatform.devicemanager.internal.Entity;
import org.sdnplatform.devicemanager.internal.MockTagManager;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.FCQueryObj;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileListener;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.IVNSInterfaceClassifier;
import org.sdnplatform.netvirt.manager.INetVirtListener;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.manager.internal.NetVirtManagerImpl;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.tagmanager.Tag;
import org.sdnplatform.tagmanager.TagManagerException;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyListener;
import org.sdnplatform.topology.ITopologyService;




@SuppressWarnings("unchecked")
public class NetVirtManagerImplTest extends PlatformTestCase {
    private NetVirtManagerImpl netVirtManager;
    private IVNSInterfaceClassifier siNetVirtIfaceClassifier;
    private MockDeviceManager mockDeviceManager;
    private MemoryStorageSource storageSource; 
    private MockTagManager mockTagManager;
    private ModuleContext fmc;
    private ITopologyService topology;
    private IRewriteService rewriteService;
    private IFlowReconcileService flowReconciler;
    private IFlowCacheService flowCacheMgr;
    
    private INetVirtListener netVirtListener;
    
    private static class NetVirtTest {
        
        ArrayList<Map<String, Object>> netVirtlist;
        ArrayList<Map<String, Object>> netVirtRuleList;
        
        public NetVirtTest() {
            netVirtlist = new ArrayList<Map<String, Object>>();
            netVirtRuleList = new ArrayList<Map<String, Object>>();
        }
        
        public void addNetVirt(Map<String, Object>... netVirts) {
            for (Map<String, Object> netVirt : netVirts) {
                netVirtlist.add(netVirt);
            }
        }
        public void addNetVirtRule(Map<String, Object>... rules) {
            for (Map<String, Object> rule : rules) {
                netVirtRuleList.add(rule);
            }
        }
        
        public Set<String> getNetVirtNames() {
            HashSet<String> tmp = new HashSet<String>();
            for (Map<String, Object> netVirt: netVirtlist) {
                tmp.add((String)netVirt.get(NetVirtManagerImpl.ID_COLUMN_NAME));
            }
            return tmp;
    }
        
        
        public void writeToStorage(IStorageSourceService storageSource) {
            for (Map<String, Object> row : netVirtlist) {
                storageSource.insertRow(NetVirtManagerImpl.VNS_TABLE_NAME,
                                        row);
            }
            for (Map<String, Object> row : netVirtRuleList) {
                storageSource.insertRow(NetVirtManagerImpl.VNS_INTERFACE_RULE_TABLE_NAME, 
                                        row);
            }
        }
        
        public void removeFromStorage(IStorageSourceService storageSource) {
            for (Map<String, Object> row : netVirtRuleList) {
                storageSource.deleteRow(NetVirtManagerImpl.VNS_INTERFACE_RULE_TABLE_NAME, 
                                        row.get(NetVirtManagerImpl.ID_COLUMN_NAME));
            }
            for (Map<String, Object> row : netVirtlist) {
                storageSource.deleteRow(NetVirtManagerImpl.VNS_TABLE_NAME,
                                        row.get(NetVirtManagerImpl.ID_COLUMN_NAME));
            }
        }
        
    }
    
    
    private static final Map<String, Object> netVirt1;
    static {
        netVirt1 = new HashMap<String, Object>();
        netVirt1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1");
        netVirt1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 500);
        netVirt1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
    }
    private static final Map<String, Object> netVirt1_highpriority;
    static {
        netVirt1_highpriority = new HashMap<String, Object>();
        netVirt1_highpriority.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1");
        netVirt1_highpriority.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 2000);
        netVirt1_highpriority.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
    }
    
    private static final Map<String, Object> netVirt2;
    static {
        netVirt2 = new HashMap<String, Object>();
        netVirt2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt2");
        netVirt2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        netVirt2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
    }
    
    private static final Map<String, Object> netVirt3;
    static {
        netVirt3 = new HashMap<String, Object>();
        netVirt3.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt3");
        netVirt3.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 200);
        netVirt3.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
    }
    
    private static final Map<String, Object> netVirt4;
    static {
        netVirt4 = new HashMap<String, Object>();
        netVirt4.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt4");
        netVirt4.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 500);
        netVirt4.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
    }
    
    private static final Map<String, Object> addrSpace42NetVirt1;
    static {
        addrSpace42NetVirt1 = new HashMap<String, Object>();
        addrSpace42NetVirt1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "addrSpace42NetVirt1");
        addrSpace42NetVirt1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 200);
        addrSpace42NetVirt1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        addrSpace42NetVirt1.put(NetVirtManagerImpl.ADDRESS_SPACE_COLUMN_NAME, 
                            "addrSpace42");
    }
    private static final Map<String, Object> addrSpace42NetVirt2;
    static {
        addrSpace42NetVirt2 = new HashMap<String, Object>();
        addrSpace42NetVirt2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "addrSpace42NetVirt2");
        addrSpace42NetVirt2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 500);
        addrSpace42NetVirt2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        addrSpace42NetVirt2.put(NetVirtManagerImpl.ADDRESS_SPACE_COLUMN_NAME, 
                            "addrSpace42");
    }
    private static final Map<String, Object> netVirtWithOrigin;
    static {
        netVirtWithOrigin = new HashMap<String, Object>();
        netVirtWithOrigin.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirtWithOrigin");
        netVirtWithOrigin.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 
                          Integer.MAX_VALUE);
        netVirtWithOrigin.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        netVirtWithOrigin.put(NetVirtManagerImpl.ORIGIN_COLUMN_NAME, "Foo");
    }
    private static final Map<String, Object> netVirtWithEmptyOrigin;
    static {
        netVirtWithEmptyOrigin = new HashMap<String, Object>();
        netVirtWithEmptyOrigin.put(NetVirtManagerImpl.ID_COLUMN_NAME, 
                               "netVirtWithEmptyOrigin");
        netVirtWithEmptyOrigin.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 
                          Integer.MAX_VALUE);
        netVirtWithEmptyOrigin.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        netVirtWithEmptyOrigin.put(NetVirtManagerImpl.ORIGIN_COLUMN_NAME, "");
    }
    
    private static final Map<String, Object> rule1;
    static {
        rule1 = new HashMap<String, Object>();
        rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|rule1");
        rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        rule1.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }

    private static final Map<String, Object> rule1_highpriority;
    static {
        rule1_highpriority = new HashMap<String, Object>();
        rule1_highpriority.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|rule1");
        rule1_highpriority.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        rule1_highpriority.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 4000);
        rule1_highpriority.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        rule1_highpriority.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        rule1_highpriority.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        rule1_highpriority.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }

    private static final Map<String, Object> rule2;
    static {
        rule2 = new HashMap<String, Object>();
        rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|rule2");
        rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        rule2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 2000);
        rule2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        rule2.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        rule2.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        rule2.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }
    
    private static final Map<String, Object> rule3;
    static {
        rule3 = new HashMap<String, Object>();
        rule3.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt2|rule3");
        rule3.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt2");
        rule3.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        rule3.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        rule3.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        rule3.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        rule3.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }
    
    private static final Map<String, Object> rule1nameReuse;
    static {
        // Re-use the name "rule1" but this time in NetVirt 2
        rule1nameReuse = new HashMap<String, Object>(rule1);
        rule1nameReuse.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt2|rule1");
        rule1nameReuse.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt2");
        rule1nameReuse.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:02");
    }
    
    private static final Map<String, Object> rule4;
    static {
        rule4 = new HashMap<String, Object>();
        rule4.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt2|rule4");
        rule4.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt2");
        rule4.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        rule4.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        rule4.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        rule4.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        rule4.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:02");
    }
    
    private static final Map<String, Object> netVirt3rule1;
    static {
        netVirt3rule1 = new HashMap<String, Object>();
        netVirt3rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt3|rule1");
        netVirt3rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt3");
        netVirt3rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        netVirt3rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        netVirt3rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        netVirt3rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        netVirt3rule1.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:03");
    }
    
    private static final Map<String, Object> netVirt4rule1;
    static {
        netVirt4rule1 = new HashMap<String, Object>();
        netVirt4rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt4|netVirt4rule1");
        netVirt4rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt4");
        netVirt4rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        netVirt4rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        netVirt4rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        netVirt4rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        netVirt4rule1.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:04");
    }
    
    private static final Map<String, Object> addrSpace42Rule1;
    static {
        // copy rule1 then change necessary fields
        addrSpace42Rule1 = new HashMap<String, Object>(rule1);
        addrSpace42Rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, 
                             "addrSpace42NetVirt1|addrSpace42Rule1");
        addrSpace42Rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, 
                             "addrSpace42NetVirt1");
    }

    private static final Map<String, Object> addrSpace42Rule2;
    static {
        // copy rule1 then change necessary fields
        addrSpace42Rule2 = new HashMap<String, Object>(rule2);
        addrSpace42Rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, 
                             "addrSpace42NetVirt1|addrSpace42Rule2");
        addrSpace42Rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, 
                             "addrSpace42NetVirt1");
    }
    
    private static final Map<String, Object> addrSpace42Rule3;
    static {
        // copy rule1 then change necessary fields
        addrSpace42Rule3 = new HashMap<String, Object>(rule3);
        addrSpace42Rule3.put(NetVirtManagerImpl.ID_COLUMN_NAME, 
                             "addrSpace42NetVirt2|addrSpace42Rule3");
        addrSpace42Rule3.put(NetVirtManagerImpl.VNS_COLUMN_NAME, 
                             "addrSpace42NetVirt2");
    }
    
    private static final Map<String, Object> addrSpace42Rule4;
    static {
        // copy rule1 then change necessary fields
        addrSpace42Rule4 = new HashMap<String, Object>(rule4);
        addrSpace42Rule4.put(NetVirtManagerImpl.ID_COLUMN_NAME, 
                             "addrSpace42NetVirt2|addrSpace42Rule4");
        addrSpace42Rule4.put(NetVirtManagerImpl.VNS_COLUMN_NAME, 
                             "addrSpace42NetVirt2");
    }
    
    private static final Map<String, Object> multi_rule1;
    static {
        multi_rule1 = new HashMap<String, Object>();
        multi_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|multi_rule1");
        multi_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        multi_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 100);
        multi_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        multi_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, true);
        multi_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        multi_rule1.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }

    private static final Map<String, Object> multi_rule1_highpriority;
    static {
        multi_rule1_highpriority = new HashMap<String, Object>();
        multi_rule1_highpriority.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|multi_rule1");
        multi_rule1_highpriority.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        multi_rule1_highpriority.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 5000);
        multi_rule1_highpriority.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        multi_rule1_highpriority.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, true);
        multi_rule1_highpriority.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        multi_rule1_highpriority.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }
    
    private static final Map<String, Object> multi_rule2;
    static {
        multi_rule2 = new HashMap<String, Object>();
        multi_rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt2|multi_rule2");
        multi_rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt2");
        multi_rule2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 250);
        multi_rule2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        multi_rule2.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, true);
        multi_rule2.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        multi_rule2.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }

    private static final Map<String, Object> multi_rule2_highpriority;
    static {
        multi_rule2_highpriority = new HashMap<String, Object>();
        multi_rule2_highpriority.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt2|multi_rule2");
        multi_rule2_highpriority.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt2");
        multi_rule2_highpriority.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 5000);
        multi_rule2_highpriority.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        multi_rule2_highpriority.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, true);
        multi_rule2_highpriority.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        multi_rule2_highpriority.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
    }

    private static final Map<String, Object> ipsubnet_rule1;
    static {
        ipsubnet_rule1 = new HashMap<String, Object>();
        ipsubnet_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|ipsubnet_rule1");
        ipsubnet_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        ipsubnet_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 5000);
        ipsubnet_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        ipsubnet_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        ipsubnet_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        ipsubnet_rule1.put(NetVirtManagerImpl.IP_SUBNET_COLUMN_NAME, "192.168.1.0/24");
    }

    private static final Map<String, Object> ipsubnet_rule2;
    static {
        ipsubnet_rule2 = new HashMap<String, Object>();
        ipsubnet_rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|ipsubnet_rule2");
        ipsubnet_rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        ipsubnet_rule2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 6000);
        ipsubnet_rule2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        ipsubnet_rule2.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        ipsubnet_rule2.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        ipsubnet_rule2.put(NetVirtManagerImpl.IP_SUBNET_COLUMN_NAME, "192.168.0.0/16");
    }

    private static final Map<String, Object> ipsubnet_rule2_lowpriority;
    static {
        ipsubnet_rule2_lowpriority = new HashMap<String, Object>();
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|ipsubnet_rule2");
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 1000);
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        ipsubnet_rule2_lowpriority.put(NetVirtManagerImpl.IP_SUBNET_COLUMN_NAME, "192.168.0.0/16");
    }

    private static final Map<String, Object> ipsubnet_rule3;
    static {
        ipsubnet_rule3 = new HashMap<String, Object>();
        ipsubnet_rule3.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|ipsubnet_rule3");
        ipsubnet_rule3.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        ipsubnet_rule3.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 5000);
        ipsubnet_rule3.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        ipsubnet_rule3.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        ipsubnet_rule3.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        ipsubnet_rule3.put(NetVirtManagerImpl.IP_SUBNET_COLUMN_NAME, "10.0.0.0/8");
    }

    private static final Map<String, Object> switch_rule1;
    static {
        switch_rule1 = new HashMap<String, Object>();
        switch_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|1");
        switch_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        switch_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 5000);
        switch_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        switch_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        switch_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        switch_rule1.put(NetVirtManagerImpl.SWITCH_COLUMN_NAME, "00:00:00:00:00:00:00:01");
    }

    private static final Map<String, Object> switch_port_rule1;
    static {
        switch_port_rule1 = new HashMap<String, Object>();
        switch_port_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|2");
        switch_port_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        switch_port_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 6000);
        switch_port_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        switch_port_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        switch_port_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        switch_port_rule1.put(NetVirtManagerImpl.SWITCH_COLUMN_NAME, "00:00:00:00:00:00:00:02");
        switch_port_rule1.put(NetVirtManagerImpl.PORTS_COLUMN_NAME, "A54-58,B1,C0-5");
    }

    private static final Map<String, Object> vlan_rule1;
    static {
        vlan_rule1 = new HashMap<String, Object>();
        vlan_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|1");
        vlan_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        vlan_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 5000);
        vlan_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        vlan_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        vlan_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        vlan_rule1.put(NetVirtManagerImpl.VLANS_COLUMN_NAME, "5");
    }

    private static final Map<String, Object> vlan_rule2;
    static {
        vlan_rule2 = new HashMap<String, Object>();
        vlan_rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|2");
        vlan_rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        vlan_rule2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 4000);
        vlan_rule2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        vlan_rule2.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        vlan_rule2.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        vlan_rule2.put(NetVirtManagerImpl.VLANS_COLUMN_NAME, "5-10");
    }

    private static final Map<String, Object> tag_rule1;
    static {
        tag_rule1 = new HashMap<String, Object>();
        tag_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|1");
        tag_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        tag_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 4000);
        tag_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        tag_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        tag_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        tag_rule1.put(NetVirtManagerImpl.TAGS_COLUMN_NAME, "org.sdnplatform.tag1=value1");
    }

    private static final Map<String, Object> tag_rule2;
    static {
        tag_rule2 = new HashMap<String, Object>();
        tag_rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|1");
        tag_rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        tag_rule2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 4000);
        tag_rule2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        tag_rule2.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        tag_rule2.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        tag_rule2.put(NetVirtManagerImpl.TAGS_COLUMN_NAME, "org.sdnplatform.tag1=value1,org.sdnplatform.tag2=value2");
    }
    
    private static final Map<String, Object> multifield_rule1;
    static {
        multifield_rule1 = new HashMap<String, Object>();
        multifield_rule1.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|5");
        multifield_rule1.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        multifield_rule1.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 6000);
        multifield_rule1.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        multifield_rule1.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        multifield_rule1.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        multifield_rule1.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
        multifield_rule1.put(NetVirtManagerImpl.IP_SUBNET_COLUMN_NAME, "10.0.0.0/8");
    }
    
    private static final Map<String, Object> multifield_rule2;
    static {
        multifield_rule2 = new HashMap<String, Object>();
        multifield_rule2.put(NetVirtManagerImpl.ID_COLUMN_NAME, "netVirt1|5");
        multifield_rule2.put(NetVirtManagerImpl.VNS_COLUMN_NAME, "netVirt1");
        multifield_rule2.put(NetVirtManagerImpl.PRIORITY_COLUMN_NAME, 6000);
        multifield_rule2.put(NetVirtManagerImpl.ACTIVE_COLUMN_NAME, true);
        multifield_rule2.put(NetVirtManagerImpl.MULTIPLE_ALLOWED_COLUMN_NAME, false);
        multifield_rule2.put(NetVirtManagerImpl.VLAN_TAG_ON_EGRESS_COLUMN_NAME, false);
        multifield_rule2.put(NetVirtManagerImpl.MAC_COLUMN_NAME, "00:00:00:00:00:01");
        multifield_rule2.put(NetVirtManagerImpl.IP_SUBNET_COLUMN_NAME, "10.0.0.0/8");
        multifield_rule2.put(NetVirtManagerImpl.TAGS_COLUMN_NAME, "org.sdnplatform.tag1 value1");
    }
    
    
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        storageSource = new MemoryStorageSource();
        mockDeviceManager = new MockDeviceManager();
        mockTagManager = new MockTagManager();
        netVirtManager = new NetVirtManagerImpl();
        siNetVirtIfaceClassifier = createNiceMock(IVNSInterfaceClassifier.class);
        RestApiServer ras = new RestApiServer();
        topology = createMock(ITopologyService.class);
        MockThreadPoolService tp = new MockThreadPoolService();
        rewriteService = createNiceMock(IRewriteService.class);
        IEntityClassifierService entityClassifier = 
                new NetVirtMockEntityClassifier();
        flowReconciler = createMock(IFlowReconcileService.class);
        flowCacheMgr = createMock(IFlowCacheService.class);
        
        netVirtListener = createMock(INetVirtListener.class);
        
        fmc = new ModuleContext();
        fmc.addService(IControllerService.class, mockControllerProvider);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(ITagManagerService.class, mockTagManager);
        fmc.addService(INetVirtManagerService.class, netVirtManager);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(IRewriteService.class, rewriteService);
        fmc.addService(IFlowReconcileService.class, flowReconciler);
        fmc.addService(IFlowCacheService.class, flowCacheMgr);
        
        
        // Setup and replay mock sdnplatform modules 
        topology.addListener(anyObject(ITopologyListener.class));
        expectLastCall().anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort()))
               .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.isConsistent(anyLong(),
                                     anyShort(),
                                     anyLong(),
                                     anyShort())).andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                anyLong(), anyShort()))
                .andReturn(true).anyTimes();
        flowReconciler.addFlowReconcileListener(
                anyObject(IFlowReconcileListener.class));
        expectLastCall().anyTimes();
        
        
        replay(topology, flowReconciler);
        
        
        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        netVirtManager.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        tp.init(fmc);
        
        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        netVirtManager.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        tp.startUp(fmc);
        
        netVirtManager.addNetVirtListener(netVirtListener);
    }
    
    
    private void setupTest(NetVirtTest test) {
        setupTest(test, test.getNetVirtNames());
    }
    
    private void setupTest(NetVirtTest test, Set<String> expectedChangedNetVirtNames) {
        IStorageSourceService storageSource = getStorageSource();
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        test.writeToStorage(storageSource);
        if (!test.netVirtlist.isEmpty())
            expectedChangedNetVirtNames.add("default|default");
        
        // Setup the mock expectations for the set of changed NetVirt that
        // we expect to be reconciled
        reset(flowCacheMgr);
        reset(netVirtListener);
        for (String netVirtName: expectedChangedNetVirtNames) {
            FCQueryObj query = new FCQueryObj(netVirtManager,
                                        netVirtName,
                                        null,   // null vlan
                                        null,   // null srcDevice
                                        null,   // null destDevice
                                        netVirtManager.getName(),
                                        FCQueryEvType.APP_CONFIG_CHANGED,
                                        null);
            flowCacheMgr.submitFlowCacheQuery(query);
            expectLastCall().once();
        }
        if (!expectedChangedNetVirtNames.isEmpty()) {
            FCQueryObj query = new FCQueryObj(netVirtManager,
                                        IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                                        null,   // null vlan
                                        null,   // null srcDevice
                                        null,   // null destDevice
                                        netVirtManager.getName(),
                                        FCQueryEvType.APP_CONFIG_CHANGED,
                                        null);
            flowCacheMgr.submitFlowCacheQuery(query);
            expectLastCall().once();
            netVirtListener.netVirtChanged(eq(expectedChangedNetVirtNames));
            expectLastCall().once();
        }
        replay(flowCacheMgr, netVirtListener);
        netVirtManager.readVNSConfigFromStorage();
        verify(flowCacheMgr);
        verify(netVirtListener);
    }
    
    
    private static final NetVirtTest emptyTest;
    static {
        emptyTest = new NetVirtTest();
    }
    @Test
    public void testNoRules() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(emptyTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default", iface.getParentVNSInterface().getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        
    }
    
    @Test
    public void testNetVirtInterfaceClassifier() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(emptyTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);

        // If external classifier doesn't classify the device,
        // the device is matched to default netVirt.
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        
        // clear the device cache
        netVirtManager.clearCachedDeviceState(d.getDeviceKey());
        
        List<VNSInterface> classifiedIfaces = new ArrayList<VNSInterface>();
        VNS myNetVirt = new VNS("myNetVirt");
        classifiedIfaces.add(new VNSInterface("myNetVirtSrcIface", myNetVirt, null, null));
        expect(siNetVirtIfaceClassifier.classifyDevice((IDevice)EasyMock.anyObject()))
               .andReturn(classifiedIfaces).times(1);
        expect(siNetVirtIfaceClassifier.getName()).andReturn("SIClassifier");
        
        replay(siNetVirtIfaceClassifier);
        netVirtManager.addVNSInterfaceClassifier(siNetVirtIfaceClassifier);

        // Now, the device is classified by the external classifier,
        // Default match should be removed.
        ifaces = netVirtManager.getInterfaces(d);
        verify(siNetVirtIfaceClassifier);
        
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("myNetVirtSrcIface", iface.getName());
        assertEquals("myNetVirt", iface.getParentVNS().getName());
        
    } 

    private static final NetVirtTest basicTest;
    static {
        basicTest = new NetVirtTest();
        basicTest.addNetVirt(netVirt1);
        basicTest.addNetVirtRule(rule1);
    }
    
    @Test
    public void testNetVirtInterfaceClassifier2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);

        // If external classifier doesn't classify the device,
        // the device is matched to default netVirt.
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        List<VNSInterface> classifiedIfaces = new ArrayList<VNSInterface>();
        VNS myNetVirt = new VNS("myNetVirt");
        classifiedIfaces.add(new VNSInterface("myNetVirtSrcIface", myNetVirt, null, null));
        expect(siNetVirtIfaceClassifier.classifyDevice((IDevice)EasyMock.anyObject()))
        .andReturn(classifiedIfaces).times(1);
        expect(siNetVirtIfaceClassifier.getName()).andReturn("SIClassifier");
        
        replay(siNetVirtIfaceClassifier);
        netVirtManager.addVNSInterfaceClassifier(siNetVirtIfaceClassifier);

        // Now, the device is classified by the external classifier,
        // Default match should be removed.
        ifaces = netVirtManager.getInterfaces(d);
        verify(siNetVirtIfaceClassifier);
        
        assertNotNull(ifaces);
        assertEquals(2, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("myNetVirtSrcIface", iface.getName());
        assertEquals("myNetVirt", iface.getParentVNS().getName());
        iface = ifaces.get(1);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    @Test
    public void testNoRulesAddrSpace() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(emptyTest);
        
        BetterEntityClass as42 = new BetterEntityClass("addrSpace42", (short)42);
        
        // A device with MAC 1 on AS 42
        Entity e = new Entity(1L, null, null, null, null, null);
        Device d = new Device(null, 1L, e, as42);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default", iface.getParentVNSInterface().getName());
        assertEquals("default|addrSpace42-default", iface.getParentVNS().getName());
        
    }

    @Test
    public void testBasic() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("netVirt1|rule1", iface.getParentRule().getName());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("rule1", iface.getParentVNSInterface().getName());
                
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:02", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("default", iface.getParentVNSInterface().getName());
        
    }   
    
    @Test
    public void testDeviceCaching() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest);
        
        BetterEntityClass asDefault = new BetterEntityClass("default", null);
        
        Entity e = new Entity(1L, null, null, null, null, null);
        Device d = new Device(null, 1L, e, asDefault);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("netVirt1|rule1", iface.getParentRule().getName());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("rule1", iface.getParentVNSInterface().getName());
        
        // Reuse device key 1. We'll change the underlying entity and 
        // call getInterfaces() again which should return the same 
        // interfaces despite the entity not matching anymore. 
        // (That's why we delete the cache on device change)
        e = new Entity(2L, null, null, null, null, null);
        d = new Device(null, 1L, e, asDefault);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("netVirt1|rule1", iface.getParentRule().getName());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("rule1", iface.getParentVNSInterface().getName());
        
        // now use new device key
        d = new Device(null, 2L, e, asDefault);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:02", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("default", iface.getParentVNSInterface().getName());
        
    }   
    
    private static final NetVirtTest interfaceCachingTest;
    static {
        interfaceCachingTest = new NetVirtTest();
        interfaceCachingTest.addNetVirt(netVirt1);
        interfaceCachingTest.addNetVirtRule(vlan_rule1);
    }
    @Test
    public void testInterfaceCaching() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(interfaceCachingTest);
        
        IDevice d = 
                mockDeviceManager.learnEntity(1L, (short)5, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("VEth1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("VEth1", iface.getParentVNSInterface().getName());
        
                
        d = mockDeviceManager.learnEntity(2L, (short)5, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface2 = ifaces.get(0);
        assertNotNull(iface2);
        assertNotNull(iface2.getParentRule());
        assertEquals("netVirt1|1", iface2.getParentRule().getName());
        assertEquals("VEth1/00:00:00:00:00:02", iface2.getName());
        assertEquals("netVirt1", iface2.getParentVNS().getName());
        assertNotNull(iface2.getParentVNSInterface());
        assertEquals("VEth1", iface2.getParentVNSInterface().getName());
        
        // The parent interface should have been cached from the first lookup
        assertSame(iface.getParentVNSInterface(), 
                   iface2.getParentVNSInterface());
        
    }
    
    private static final NetVirtTest basicTestAddrSpace;
    static {
        basicTestAddrSpace = new NetVirtTest();
        basicTestAddrSpace.addNetVirt(addrSpace42NetVirt1);
        basicTestAddrSpace.addNetVirtRule(addrSpace42Rule1);
    }
    @Test
    public void testBasicAddrSpace() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTestAddrSpace);
        
        BetterEntityClass as42 = new BetterEntityClass("addrSpace42", (short)42);
        BetterEntityClass asDefault = new BetterEntityClass("default", null);
        
        // A device with MAC 1 on AS 42
        Entity e = new Entity(1L, null, null, null, null, null);
        Device d = new Device(null, 1L, e, as42);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNotNull(iface.getParentRule());
        assertEquals("addrSpace42NetVirt1|addrSpace42Rule1", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("addrSpace42NetVirt1", iface.getParentVNS().getName());
        assertNotNull(iface.getParentVNSInterface());
        assertEquals("addrSpace42Rule1", iface.getParentVNSInterface().getName());
        
        // Device with same MAC but in address space default
        d = new Device(null, 2L, e, asDefault);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default", iface.getParentVNSInterface().getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        
        e = new Entity(2L, null, null, null, null, null);
        d = new Device(null, 3L, e, as42);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:02", iface.getName());
        assertEquals("default", iface.getParentVNSInterface().getName());
        assertEquals("default|addrSpace42-default", iface.getParentVNS().getName());
        
    }

    private static final NetVirtTest basicTest2;
    static {
        basicTest2 = new NetVirtTest();
        basicTest2.addNetVirt(netVirt1, netVirt2);
        basicTest2.addNetVirtRule(rule1, rule2, rule3, rule4);
    }
    @Test
    public void testBasic2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest2);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule3", iface.getParentRule().getName());
        assertEquals("rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule4", iface.getParentRule().getName());
        assertEquals("rule4/00:00:00:00:00:02", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(3L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:03", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        

    }

    @Test
    public void testDefaultNetVirtCreation () {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        VNS            netVirt;
        IEntityClass   entityClass;

        netVirt = netVirtManager.createDefaultNetVirt(null);
        assertEquals(netVirt.getName(), "default|default");
        assertEquals(netVirt.isActive(), true);

        entityClass = new BetterEntityClass("", null);
        netVirt = netVirtManager.createDefaultNetVirt(entityClass);
        assertEquals(netVirt.getName(), "default|default");
        assertEquals(netVirt.isActive(), true);

        entityClass = new BetterEntityClass("foo", (short) 10);
        netVirt = netVirtManager.createDefaultNetVirt(entityClass);
        assertEquals(netVirt.getName(), "default|foo-default");
        assertEquals(netVirt.isActive(), true);
    }
    
    private static final NetVirtTest basicTest2AddrSpace;
    static {
        basicTest2AddrSpace = new NetVirtTest();
        basicTest2AddrSpace.addNetVirt(addrSpace42NetVirt1, addrSpace42NetVirt2);
        basicTest2AddrSpace.addNetVirtRule(addrSpace42Rule1, addrSpace42Rule2, 
                                       addrSpace42Rule3, addrSpace42Rule4);
    }
    @Test
    public void testBasic2AddrSpace() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest2AddrSpace);
        
        BetterEntityClass as42 = new BetterEntityClass("addrSpace42", (short)42);
        
        Entity e = new Entity(1L, null, null, null, null, null);
        Device d = new Device(null, 1L, e, as42);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt2|addrSpace42Rule3", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("addrSpace42NetVirt2", iface.getParentVNS().getName());
        
        e = new Entity(2L, null, null, null, null, null);
        d = new Device(null, 2L, e, as42);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt2|addrSpace42Rule4", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule4/00:00:00:00:00:02", iface.getName());
        assertEquals("addrSpace42NetVirt2", iface.getParentVNS().getName());
        
        e = new Entity(3L, null, null, null, null, null);
        d = new Device(null, 3L, e, as42);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:03", iface.getName());
        assertEquals("default|addrSpace42-default", iface.getParentVNS().getName());
    }
    
    private static final NetVirtTest configTestNetVirt1;
    static {
        configTestNetVirt1 = new NetVirtTest();
        configTestNetVirt1.addNetVirt(netVirt1);
        configTestNetVirt1.addNetVirtRule(rule1);
    }
    private static final NetVirtTest configTestNetVirt12;
    static {
        configTestNetVirt12 = new NetVirtTest();
        configTestNetVirt12.addNetVirt(netVirt1, netVirt2);
        configTestNetVirt12.addNetVirtRule(rule1, rule4);
    }
    private static final NetVirtTest configTestNetVirt123;
    static {
        configTestNetVirt123 = new NetVirtTest();
        configTestNetVirt123.addNetVirt(netVirt1, netVirt2, netVirt3);
        configTestNetVirt123.addNetVirtRule(rule1, rule4, netVirt3rule1);
    }
    private static final NetVirtTest configTestNetVirt1234;
    static {
        configTestNetVirt1234 = new NetVirtTest();
        configTestNetVirt1234.addNetVirt(netVirt1, netVirt2, netVirt3, netVirt4);
        configTestNetVirt1234.addNetVirtRule(rule1, rule4,
                                    netVirt3rule1, netVirt4rule1);
    }
    private static final NetVirtTest configTestNetVirt123Origin;
    static {
        configTestNetVirt123Origin = new NetVirtTest();
        configTestNetVirt123Origin.addNetVirt(netVirt1, netVirt2, netVirt3, netVirtWithOrigin);
        configTestNetVirt123Origin.addNetVirtRule(rule1, rule4,
                                           netVirt3rule1);
    }
    private static final NetVirtTest configTestNetVirt123EmptyOrigin;
    static {
        configTestNetVirt123EmptyOrigin = new NetVirtTest();
        configTestNetVirt123EmptyOrigin.addNetVirt(netVirt1, netVirt2, netVirt3, 
                                           netVirtWithEmptyOrigin);
        configTestNetVirt123EmptyOrigin.addNetVirtRule(rule1, rule4, netVirt3rule1);
    }
    /*
     * TODO: we currently only test that the correct NetVirtes are reconciled and
     * sent to the listeners. We should also check that the new config is 
     * indeed applied and the old config is completelty gone. 
     */
    @Test
    public void testNetVirtConfigChangeWithoutOriginHack() {
        doNetVirtConfigChangeTest(false);
    }
    @Test
    public void testNetVirtConfigChangeWithOriginHack() {
        doNetVirtConfigChangeTest(true);
    }
        
    public void doNetVirtConfigChangeTest(boolean nonNullOriginSimpleReconcile) {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        netVirtManager.setNonNullOriginSimpleReconcile(nonNullOriginSimpleReconcile);
        HashSet<String> expectedChangedNetVirt = new HashSet<String>();
        IDevice d;
        List<VNSInterface> ifaces;
        VNSInterface iface;
        
        setupTest(configTestNetVirt1);
        
        d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        // Change config. No default NetVirt has been created, so it shouldn't be 
        // reconciled. 
        // NetVirt2 has higher priority than NetVirt1, so we expect NetVirt 1 and 2 to
        // have changed. 
        expectedChangedNetVirt.addAll(basicTest2.getNetVirtNames());
        configTestNetVirt1.removeFromStorage(storageSource);
        setupTest(configTestNetVirt12, expectedChangedNetVirt);
        
        d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt1", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt2", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(3L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("default|default", iface.getParentVNS().getName()); 
        
        // Config change. Add NetVirt, which has lower priority than NetVirt 1 and 2
        // Default NetVirt had been created. 
        expectedChangedNetVirt.clear();
        expectedChangedNetVirt.add("netVirt3");
        expectedChangedNetVirt.add("default|default");
        configTestNetVirt12.removeFromStorage(storageSource);
        setupTest(configTestNetVirt123, expectedChangedNetVirt);
        
        d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt1", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt2", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(3L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt3", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(4L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("default|default", iface.getParentVNS().getName()); 
        
        // Config change. Add NetVirt 4, which has same priority as NetVirt 1
        // Default NetVirt had been created. 
        expectedChangedNetVirt.clear();
        expectedChangedNetVirt.add("netVirt1"); // same priority as NetVirt 4
        expectedChangedNetVirt.add("netVirt3"); // lower priority than NetVirt 4
        expectedChangedNetVirt.add("netVirt4"); // new NetVirt 
        configTestNetVirt123.removeFromStorage(storageSource);
        setupTest(configTestNetVirt1234, expectedChangedNetVirt);
        
        d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt1", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt2", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(3L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt3", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(4L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt4", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(5L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("default|default", iface.getParentVNS().getName()); 
        
        
        // Config change. remove NetVirt 4, which has same priority as NetVirt 1
        // Default NetVirt had been created. 
        expectedChangedNetVirt.clear();
        expectedChangedNetVirt.add("netVirt1"); // same priority as NetVirt 4
        expectedChangedNetVirt.add("netVirt3"); // lower priority than NetVirt 4
        expectedChangedNetVirt.add("netVirt4"); // removed NetVirt 
        configTestNetVirt1234.removeFromStorage(storageSource);
        setupTest(configTestNetVirt123, expectedChangedNetVirt);
        
        d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt1", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt2", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(3L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("netVirt3", iface.getParentVNS().getName()); 
        
        d = mockDeviceManager.learnEntity(4L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertEquals("default|default", iface.getParentVNS().getName()); 
        
        // Add NetVirt with an origin set
        expectedChangedNetVirt.clear();
        if (nonNullOriginSimpleReconcile) {
            expectedChangedNetVirt.add("netVirtWithOrigin");
        } else {
            expectedChangedNetVirt.add("netVirtWithOrigin");
            // all other NetVirt have lower priority
            expectedChangedNetVirt.add("netVirt1"); 
            expectedChangedNetVirt.add("netVirt2"); 
            expectedChangedNetVirt.add("netVirt3"); 
        }
        //configTestNetVirt123.removeFromStorage(storageSource);
        setupTest(configTestNetVirt123Origin, expectedChangedNetVirt);
    }
    
    private static final NetVirtTest ruleNameReuseTest;
    static {
        ruleNameReuseTest = new NetVirtTest();
        ruleNameReuseTest.addNetVirt(netVirt1, netVirt2);
        ruleNameReuseTest.addNetVirtRule(rule1, rule1nameReuse);
    }
    @Test
    public void testRuleNameReuse() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(ruleNameReuseTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|rule1", iface.getParentRule().getName());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule1", iface.getParentRule().getName());
        assertEquals("rule1/00:00:00:00:00:02", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest multiAddressSpaceTest;
    static {
        multiAddressSpaceTest = new NetVirtTest();
        multiAddressSpaceTest.addNetVirt(addrSpace42NetVirt1, netVirt1, netVirt2);
        multiAddressSpaceTest.addNetVirtRule(addrSpace42Rule1, rule1, rule3);
    }
    @Test
    public void testMultiAddressSpace() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(multiAddressSpaceTest);
        
        BetterEntityClass as42 = new BetterEntityClass("addrSpace42", (short)42);
        BetterEntityClass asdefault = new BetterEntityClass("default", null);
        
        Entity e = new Entity(1L, null, null, null, null, null);
        Device d = new Device(null, 1L, e, as42);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt1|addrSpace42Rule1", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("addrSpace42NetVirt1", iface.getParentVNS().getName());
        
        d = new Device(null, 2L, e, asdefault);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule3", 
                     iface.getParentRule().getName());
        assertEquals("rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
        e = new Entity(3L, null, null, null, null, null);
        d = new Device(null, 3L, e, as42);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:03", iface.getName());
        assertEquals("default|addrSpace42-default", iface.getParentVNS().getName());

        d = new Device(null, 4L, e, asdefault);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:03", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        

    }
    
    @Test
    public void testReassignment() throws InterruptedException {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        netVirtManager.getInterfaces(d);
        
        IDevice d2 = mockDeviceManager.learnEntity(2L, null, null, null, null);
        netVirtManager.getInterfaces(d2);

        IDevice d3 = mockDeviceManager.learnEntity(3L, null, null, null, null);
        netVirtManager.getInterfaces(d3);
        
        HashSet<String> expectedChangesNetVirtNames = new HashSet<String>();
        expectedChangesNetVirtNames.add("default|default");
        expectedChangesNetVirtNames.add("netVirt1");
        expectedChangesNetVirtNames.add("netVirt2");
        setupTest(basicTest2, expectedChangesNetVirtNames);
        netVirtManager.getInterfaces(d);
        netVirtManager.getInterfaces(d2);
        netVirtManager.getInterfaces(d3);
        
    }
    
    private static final NetVirtTest interfacePriorityTest;
    static {
        interfacePriorityTest = new NetVirtTest();
        interfacePriorityTest.addNetVirt(netVirt1);
        interfacePriorityTest.addNetVirtRule(rule1, rule2);
    }
    @Test
    public void testInterfacePriority() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(interfacePriorityTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|rule2", iface.getParentRule().getName());
        assertEquals("rule2/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest interfacePriorityTest2;
    static {
        interfacePriorityTest2 = new NetVirtTest();
        interfacePriorityTest2.addNetVirt(netVirt1);
        interfacePriorityTest2.addNetVirtRule(rule1_highpriority, rule2);
    }
    @Test
    public void testInterfacePriority2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(interfacePriorityTest2);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|rule1", iface.getParentRule().getName());
        assertEquals("rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest netVirtPriorityTest;
    static {
        netVirtPriorityTest = new NetVirtTest();
        netVirtPriorityTest.addNetVirt(netVirt1, netVirt2);
        netVirtPriorityTest.addNetVirtRule(rule1, rule2, rule3);
    }
    @Test
    public void testNetVirtPriority() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(netVirtPriorityTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule3", iface.getParentRule().getName());
        assertEquals("rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest netVirtPriorityTest2;
    static {
        netVirtPriorityTest2 = new NetVirtTest();
        netVirtPriorityTest2.addNetVirt(netVirt1_highpriority, netVirt2);
        netVirtPriorityTest2.addNetVirtRule(rule1, rule2, rule3);
    }
    @Test
    public void testNetVirtPriority2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(netVirtPriorityTest2);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|rule2", iface.getParentRule().getName());
        assertEquals("rule2/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    @Test
    public void testNetVirtPriorityChange() {
        Set<String> expectedChangedNetVirt = new HashSet<String>();
        expectedChangedNetVirt.clear();
        expectedChangedNetVirt.add("netVirt1");
        expectedChangedNetVirt.add("netVirt2");
        setupTest(netVirtPriorityTest, expectedChangedNetVirt);
        setupTest(netVirtPriorityTest2, expectedChangedNetVirt);
        setupTest(netVirtPriorityTest, expectedChangedNetVirt);
    }
    
    private static final NetVirtTest multiruleTest;
    static {
        multiruleTest = new NetVirtTest();
        multiruleTest.addNetVirt(netVirt1, netVirt2);
        multiruleTest.addNetVirtRule(multi_rule1, multi_rule2);
    }
    @Test
    public void testMultipleAllowed() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(multiruleTest);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(2, ifaces.size());
        
        for (VNSInterface iface : ifaces) {
            assertNotNull(iface);
            if (iface.getParentRule().getName().equals("netVirt1|multi_rule1")) {
                assertEquals("multi_rule1/00:00:00:00:00:01", iface.getName());
                assertEquals("netVirt1", iface.getParentVNS().getName());                
            } else if (iface.getParentRule().getName().equals("netVirt2|multi_rule2")) {
                assertEquals("multi_rule2/00:00:00:00:00:01", iface.getName());
                assertEquals("netVirt2", iface.getParentVNS().getName());
            } else {
                fail("Unexpected interface rule match: " + iface.getParentRule().getName());
            }
        }
        
    }

    private static final NetVirtTest multiruleTest2;
    static {
        multiruleTest2 = new NetVirtTest();
        multiruleTest2.addNetVirt(netVirt1, netVirt2);
        multiruleTest2.addNetVirtRule(multi_rule1, multi_rule2, rule3);
    }
    @Test
    public void testMultipleAllowed2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(multiruleTest2);

        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule3", iface.getParentRule().getName());
        assertEquals("rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
    }

    private static final NetVirtTest multiruleTest3;
    static {
        multiruleTest3 = new NetVirtTest();
        multiruleTest3.addNetVirt(netVirt1, netVirt2);
        multiruleTest3.addNetVirtRule(multi_rule1_highpriority, multi_rule2, 
                                  rule1, rule2, rule3, rule4);
    }
    @Test
    public void testMultipleAllowed3() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(multiruleTest3);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule3", iface.getParentRule().getName());
        assertEquals("rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule4", iface.getParentRule().getName());
        assertEquals("rule4/00:00:00:00:00:02", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
    }

    private static final NetVirtTest multiruleTest4;
    static {
        multiruleTest4 = new NetVirtTest();
        multiruleTest4.addNetVirt(netVirt1, netVirt2);
        multiruleTest4.addNetVirtRule(multi_rule1, multi_rule2_highpriority, 
                                  rule1, rule2, rule3, rule4);
    }
    @Test
    public void testMultipleAllowed4() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(multiruleTest4);
        
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(2, ifaces.size());
        
        for (VNSInterface iface : ifaces) {
            assertNotNull(iface);
            if (iface.getParentRule().getName().equals("netVirt1|multi_rule1")) {
                assertEquals("multi_rule1/00:00:00:00:00:01", iface.getName());
                assertEquals("netVirt1", iface.getParentVNS().getName());                
            } else if (iface.getParentRule().getName().equals("netVirt2|multi_rule2")) {
                assertEquals("multi_rule2/00:00:00:00:00:01", iface.getName());
                assertEquals("netVirt2", iface.getParentVNS().getName());
            } else {
                fail("Unexpected interface rule match: " + iface.getParentRule().getName());
            }
        }
        
        d = mockDeviceManager.learnEntity(2L, null, null, null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule4", iface.getParentRule().getName());
        assertEquals("rule4/00:00:00:00:00:02", iface.getName());
        assertEquals("netVirt2", iface.getParentVNS().getName());
        
    }

    private static final NetVirtTest ipSubnetTest;
    static {
        ipSubnetTest = new NetVirtTest();
        ipSubnetTest.addNetVirt(netVirt1);
        ipSubnetTest.addNetVirtRule(ipsubnet_rule1, ipsubnet_rule2, ipsubnet_rule3);
    }
    @Test
    public void testipSubnet() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(ipSubnetTest);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, 
                                          IPv4.toIPv4Address("192.168.1.1"), 
                                          null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|ipsubnet_rule2", iface.getParentRule().getName());
        assertEquals("ipsubnet_rule2/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(2L, null, 
                                          IPv4.toIPv4Address("10.5.2.25"), 
                                          null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|ipsubnet_rule3", iface.getParentRule().getName());
        assertEquals("ipsubnet_rule3/00:00:00:00:00:02", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(3L, null, 
                                          IPv4.toIPv4Address("4.4.4.4"), 
                                          null, null);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:03", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest ipSubnetTest2;
    static {
        ipSubnetTest2 = new NetVirtTest();
        ipSubnetTest2.addNetVirt(netVirt1);
        ipSubnetTest2.addNetVirtRule(ipsubnet_rule1, ipsubnet_rule2_lowpriority, ipsubnet_rule3);
    }
    @Test
    public void testipSubnet2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(ipSubnetTest2);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, 
                                          IPv4.toIPv4Address("192.168.1.1"), 
                                          null, null);
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|ipsubnet_rule1", iface.getParentRule().getName());
        assertEquals("ipsubnet_rule1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest switchPortTest;
    static {
        switchPortTest = new NetVirtTest();
        switchPortTest.addNetVirt(netVirt1);
        switchPortTest.addNetVirtRule(switch_rule1, switch_port_rule1);
    }
    @Test
    public void testSwitchPort() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        MockControllerProvider mockControllerProvider = getMockControllerProvider();
        setupTest(switchPortTest);
        
        IOFSwitch mockSwitch1 = createMock(IOFSwitch.class);
        expect(mockSwitch1.getId()).andReturn(1L).anyTimes();
        IOFSwitch mockSwitch2 = createMock(IOFSwitch.class);
        expect(mockSwitch2.getId()).andReturn(2L).anyTimes();
        ArrayList<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        for (int i = 0; i < 100; i++) {
            int n = 0;
            for (String s : new String[] {"A","B","C","D","E"}) {
                OFPhysicalPort p = new OFPhysicalPort();
                short portNumber = (short)(i+100*n);
                p.setName(s + i);
                p.setPortNumber(portNumber);
                ports.add(p);
                n += 1;
                expect(mockSwitch1.getPort(portNumber)).andReturn(p).anyTimes();
                expect(mockSwitch2.getPort(portNumber)).andReturn(p).anyTimes();
            }
        }

        expect(mockSwitch1.getEnabledPorts()).andReturn(ports).anyTimes();
        expect(mockSwitch2.getEnabledPorts()).andReturn(ports).anyTimes();
        expect(mockSwitch1.portEnabled(anyObject(OFPhysicalPort.class)))
                .andReturn(true).anyTimes();
        expect(mockSwitch2.portEnabled(anyObject(OFPhysicalPort.class)))
                .andReturn(true).anyTimes();
        
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch1);
        switches.put(2L, mockSwitch2);
        mockControllerProvider.setSwitches(switches);
        
        replay(mockSwitch1, mockSwitch2);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, 
                                          IPv4.toIPv4Address("192.168.1.1"), 
                                          1L, 0);

        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);

        d = mockDeviceManager.learnEntity(1L, null, 
                                          IPv4.toIPv4Address("192.168.1.1"), 
                                          2L, 0);
        netVirtManager.deviceListener.deviceMoved(d);
        List<VNSInterface> ifaces2 = netVirtManager.getInterfaces(d);

        netVirtManager.deviceListener.deviceRemoved(d);
        d = mockDeviceManager.learnEntity(2L, null, 
                                          IPv4.toIPv4Address("192.168.1.2"), 
                                          1L, 0);
        d = mockDeviceManager.learnEntity(2L, null, 
                                          IPv4.toIPv4Address("192.168.1.2"), 
                                          2L, 202);

        netVirtManager.deviceListener.deviceMoved(d);
        List<VNSInterface> ifaces3 = netVirtManager.getInterfaces(d);
        
        verify(mockSwitch1, mockSwitch2);
        
        assertNotNull(ifaces);
        assertNotNull(ifaces2);
        assertEquals(1, ifaces.size());
        assertEquals(1, ifaces2.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("Eth1/A0", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        iface = ifaces2.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("Eth1/A0", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        iface = ifaces3.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|2", iface.getParentRule().getName());
        assertEquals("Eth2/C2", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }

    private static final NetVirtTest vlanTest;
    static {
        vlanTest = new NetVirtTest();
        vlanTest.addNetVirt(netVirt1);
        vlanTest.addNetVirtRule(vlan_rule1);
    }
    @Test
    public void testVlan() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(vlanTest);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, null, null, null); 
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());

        d = mockDeviceManager.learnEntity(1L, (short)5, null, null, null);
        netVirtManager.deviceListener.deviceVlanChanged(d);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("VEth1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }

    private static final NetVirtTest vlanTest2;
    static {
        vlanTest2 = new NetVirtTest();
        vlanTest2.addNetVirt(netVirt1);
        vlanTest2.addNetVirtRule(vlan_rule1, vlan_rule2);
    }
    @Test
    public void testVlan2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(vlanTest2);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, null, null, null); 
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());

        d = mockDeviceManager.learnEntity(1L, (short)5, null, null, null);
        netVirtManager.deviceListener.deviceVlanChanged(d);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("VEth1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        d = mockDeviceManager.learnEntity(1L, (short)7, null, null, null);
        netVirtManager.deviceListener.deviceVlanChanged(d);
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|2", iface.getParentRule().getName());
        assertEquals("VEth2/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest tagTest1;
    static {
        tagTest1 = new NetVirtTest();
        tagTest1.addNetVirt(netVirt1);
        tagTest1.addNetVirtRule(tag_rule1);
    }
    
    @Test
    public void testTag1() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        MockTagManager tagManager = getTagManager();
        setupTest(tagTest1);
        String hostMac = "00:00:00:00:00:01";
        
        ArrayList<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        OFPhysicalPort port1 = new OFPhysicalPort();
        port1.setName("eth1");
        port1.setPortNumber((short) 1);
        ports.add(port1);
        OFPhysicalPort port2 = new OFPhysicalPort();
        port2.setName("eth2");
        port2.setPortNumber((short) 2);
        ports.add(port2);
        OFPhysicalPort port3 = new OFPhysicalPort();
        port3.setName("eth3");
        port3.setPortNumber((short) 3);
        ports.add(port3);
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        for (OFPhysicalPort p: ports) {
            expect(mockSwitch.getPort(p.getName())).andReturn(p).anyTimes();
            expect(mockSwitch.getPort(p.getPortNumber())).andReturn(p).anyTimes();
        }

        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        mockControllerProvider.setSwitches(switches);
        replay(mockSwitch);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, null, null, null); 
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());

        netVirtManager.clearCachedDeviceState(d.getDeviceKey());
        
        Tag tag = new Tag("org.sdnplatform", "tag1", "value1", false);
        Set<IDevice> devices = new HashSet<IDevice>();
        devices.add(d);
        Set<IDevice> noDevices = new HashSet<IDevice>();  
        Set<Tag> tags = new HashSet<Tag>();
        tags.add(tag);
        Set<Tag> noTags = new HashSet<Tag>();
        
        tagManager.setDevices(devices);
        tagManager.setTags(tags);
        tagManager.addTag(tag);
        try {
            tagManager.mapTagToHost(tag, hostMac, null, null, null);
        } catch (TagManagerException e) {
            fail("Got tagManager exception: " + e);
        }
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("VEth1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        netVirtManager.clearCachedDeviceState(d.getDeviceKey());
        tagManager.setDevices(noDevices);
        tagManager.setTags(noTags);
        
        try {
            tagManager.unmapTagToHost(tag, hostMac, null, null, null);
        } catch (TagManagerException e) {
            fail("Got tagManager exception: " + e);
        }
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        
    }
    
    private static final NetVirtTest tagTest2;
    static {
        tagTest2 = new NetVirtTest();
        tagTest2.addNetVirt(netVirt1);
        tagTest2.addNetVirtRule(tag_rule2);
    }
    @Test
    public void testTag2() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        MockTagManager tagManager = getTagManager();
        setupTest(tagTest2);
        String hostMac = "00:00:00:00:00:01";
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, null, null, null, null); 
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());

        netVirtManager.clearCachedDeviceState(d.getDeviceKey());
        
        Tag tag1 = new Tag("org.sdnplatform", "tag1", "value1");
        Set<IDevice> devices = new HashSet<IDevice>();
        devices.add(d);
        Set<IDevice> noDevices = new HashSet<IDevice>();  
        Set<Tag> tags = new HashSet<Tag>();
        tagManager.setDevices(devices);
        
        tags.add(tag1);
        tagManager.setTags(tags);
   
        Set<Tag> noTags = new HashSet<Tag>();
        //tagManager.addTag(tag);
        try {
            tagManager.mapTagToHost(tag1, hostMac, null, null, null);
        } catch (TagManagerException e) {
            fail("Got tagManager exception: " + e);
        }
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());

        Tag tag = new Tag("org.sdnplatform", "tag2", "value2");
        tags.add(tag);
        tagManager.setTags(tags);
        netVirtManager.clearCachedDeviceState(d.getDeviceKey());
        
    
        
     
        try {
            tagManager.mapTagToHost(tag, hostMac, null, null, null);
        } catch (TagManagerException e) {
            fail("Got tagManager exception: " + e);
        }
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt1|1", iface.getParentRule().getName());
        assertEquals("VEth1/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
        tagManager.setDevices(noDevices);
        tagManager.setTags(noTags);
        
        netVirtManager.clearCachedDeviceState(d.getDeviceKey());
        try {
            tagManager.unmapTagToHost(tag, hostMac, null, null, null);
        } catch (TagManagerException e) {
            fail("Got tagManager exception: " + e);
        }
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());
        assertEquals("default/00:00:00:00:00:01", iface.getName());
        assertEquals("default|default", iface.getParentVNS().getName());
        
    }
    
    
    private static final NetVirtTest multifieldTest;
    static {
        multifieldTest = new NetVirtTest();
        multifieldTest.addNetVirt(netVirt1);
        multifieldTest.addNetVirtRule(multifield_rule1);
    }
    @Test
    public void testMultipleFields() {
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(multifieldTest);
        
        IDevice d;
        d = mockDeviceManager.learnEntity(1L, 
                                          null, 
                                          IPv4.toIPv4Address("192.168.1.1"), 
                                          null, 
                                          null); 
        
        List<VNSInterface> ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertNull(iface.getParentRule());

        d = mockDeviceManager.learnEntity(1L, 
                                          null, 
                                          IPv4.toIPv4Address("10.0.0.1"), 
                                          null, 
                                          null);
        netVirtManager.deviceListener.deviceIPV4AddrChanged(d);
        
        ifaces = netVirtManager.getInterfaces(d);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface.getParentRule());
        assertEquals("netVirt1|5", iface.getParentRule().getName());
        assertEquals("VEth5/00:00:00:00:00:01", iface.getName());
        assertEquals("netVirt1", iface.getParentVNS().getName());
        
    }
    
    private static final OFPacketIn packetIn;
    static {
        IPacket testPacket = new Ethernet()
            .setSourceMACAddress("00:00:00:00:00:01")
            .setDestinationMACAddress("00:00:00:00:00:02")
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
        byte[] testPacketSerialized = testPacket.serialize();

        // Build the PacketIn
        packetIn = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
        .setBufferId(-1)
        .setInPort((short) 1)
        .setPacketData(testPacketSerialized)
        .setReason(OFPacketInReason.NO_MATCH)
        .setTotalLength((short) testPacketSerialized.length);
    }
    
    @Test
    public void testReceivePktIn() {        
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest2);

        IDevice src = 
                mockDeviceManager.learnEntity(1L, null, null, null, null); 
        IDevice dst = 
                mockDeviceManager.learnEntity(2L, null, null, null, null); 
        
        ListenerContext cntx = new ListenerContext();
        netVirtManager.receive(null, packetIn,
                           parseAndAnnotate(cntx, packetIn, src, dst));

        List<VNSInterface> ifaces = 
            NetVirtManagerImpl.bcStore.get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule3", iface.getParentRule().getName());
        
        ifaces = 
            NetVirtManagerImpl.bcStore.get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("netVirt2|rule4", iface.getParentRule().getName());
        
    }
    
    @Test
    public void testReceiveServiceInsertionPktIn() {        
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest);

        IDevice src = 
                mockDeviceManager.learnEntity(1L, null, null, null, null);
        List<VNSInterface> classifiedIfaces = new ArrayList<VNSInterface>();
        VNS myNetVirt = new VNS("myNetVirt");
        classifiedIfaces.add(new VNSInterface("myNetVirtSrcIface", myNetVirt, null, null));
        expect(siNetVirtIfaceClassifier.classifyDevice(
                                       (String)EasyMock.anyObject(),
                                       EasyMock.anyLong(),
                                       (Short)EasyMock.anyObject(),
                                       (Integer)EasyMock.anyObject(),
                                       (SwitchPort)EasyMock.anyObject()))
        .andReturn(classifiedIfaces).times(1);
        expect(siNetVirtIfaceClassifier.classifyDevice((IDevice)EasyMock.anyObject()))
        .andReturn(classifiedIfaces).times(1);
        expect(siNetVirtIfaceClassifier.getName()).andReturn("SIClassifier");
        
        replay(siNetVirtIfaceClassifier);
        netVirtManager.addVNSInterfaceClassifier(siNetVirtIfaceClassifier);
        
        ListenerContext cntx = new ListenerContext();
        netVirtManager.receive(null, packetIn,
                           parseAndAnnotate(cntx, packetIn, src, null));
        
        verify(siNetVirtIfaceClassifier);

        List<VNSInterface> ifaces = 
            NetVirtManagerImpl.bcStore.get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        assertNotNull(ifaces);
        assertEquals(2, ifaces.size());
        VNSInterface iface = ifaces.get(1);
        assertNotNull(iface);
        assertEquals("netVirt1|rule1", iface.getParentRule().getName());
        
        ifaces = 
            NetVirtManagerImpl.bcStore.get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("myNetVirtSrcIface", iface.getName());
        assertEquals("myNetVirt", iface.getParentVNS().getName());
        
    }
    
    @Test
    public void testReceivePktInSetTransportVlan() {        
        ListenerContext cntx = new ListenerContext();
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest2AddrSpace);
        
        BetterEntityClass as42 = new BetterEntityClass("addrSpace42", (short)42);  
        
        Entity srcEntity = new Entity(1L, null, null, null, null, null);
        Device src = new Device(null, 1L, srcEntity, as42);
        
        Entity dstEntity = new Entity(2L, null, null, null, null, null); 
        Device dst = new Device(null, 2L, dstEntity, as42);
        
        resetToStrict(rewriteService);
        rewriteService.setTransportVlan(Short.valueOf((short)42), cntx);
        expectLastCall().once();
        
        replay(rewriteService);
        netVirtManager.receive(null, packetIn,
                           parseAndAnnotate(cntx, packetIn, src, dst));
        verify(rewriteService);
        
        List<VNSInterface> ifaces = NetVirtManagerImpl.bcStore.get(cntx, 
                INetVirtManagerService.CONTEXT_SRC_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt2|addrSpace42Rule3", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("addrSpace42NetVirt2", iface.getParentVNS().getName());
        
        
        ifaces = NetVirtManagerImpl.bcStore.get(cntx, 
                INetVirtManagerService.CONTEXT_DST_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt2|addrSpace42Rule4", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule4/00:00:00:00:00:02", iface.getName());
        assertEquals("addrSpace42NetVirt2", iface.getParentVNS().getName());
        
    }
    
    @Test
    public void testReceivePktInSetTransportVlanNull() {        
        ListenerContext cntx = new ListenerContext();
        NetVirtManagerImpl netVirtManager = getNetVirtManager();
        setupTest(basicTest2AddrSpace);
        
        // Set vlan to null
        BetterEntityClass as42 = new BetterEntityClass("addrSpace42", null);  
        
        Entity srcEntity = new Entity(1L, null, null, null, null, null);
        Device src = new Device(null, 1L, srcEntity, as42);
        
        Entity dstEntity = new Entity(2L, null, null, null, null, null); 
        Device dst = new Device(null, 2L, dstEntity, as42);
        
        resetToStrict(rewriteService);
        // no calls to rewriteService since transport vlan is null
        replay(rewriteService);
        netVirtManager.receive(null, packetIn,
                           parseAndAnnotate(cntx, packetIn, src, dst));
        verify(rewriteService);
        
        List<VNSInterface> ifaces = NetVirtManagerImpl.bcStore.get(cntx, 
                INetVirtManagerService.CONTEXT_SRC_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        VNSInterface iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt2|addrSpace42Rule3", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule3/00:00:00:00:00:01", iface.getName());
        assertEquals("addrSpace42NetVirt2", iface.getParentVNS().getName());
        
        
        ifaces = NetVirtManagerImpl.bcStore.get(cntx, 
                INetVirtManagerService.CONTEXT_DST_IFACES);
        assertNotNull(ifaces);
        assertEquals(1, ifaces.size());
        iface = ifaces.get(0);
        assertNotNull(iface);
        assertEquals("addrSpace42NetVirt2|addrSpace42Rule4", 
                     iface.getParentRule().getName());
        assertEquals("addrSpace42Rule4/00:00:00:00:00:02", iface.getName());
        assertEquals("addrSpace42NetVirt2", iface.getParentVNS().getName());
        
    }
    
    @Test
    public void testBroadcastSwitchInterfaces() {
        List<SwitchPort> swt;
        
        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put(NetVirtManagerImpl.SWITCH_DPID, "00:00:00:00:00:01");
        row1.put(NetVirtManagerImpl.SWITCH_IFACE_NAME, "A0");
        row1.put(NetVirtManagerImpl.SWITCH_BROADCAST_IFACE_COLUMN_NAME, true);

        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put(NetVirtManagerImpl.SWITCH_DPID, "00:00:00:00:00:02");
        row2.put(NetVirtManagerImpl.SWITCH_IFACE_NAME, "A1");       
        row2.put(NetVirtManagerImpl.SWITCH_BROADCAST_IFACE_COLUMN_NAME, true);
        
        getStorageSource().insertRow(NetVirtManagerImpl.SWITCH_INTERFACE_CONFIG_TABLE_NAME, 
                row1);
        getStorageSource().insertRow(NetVirtManagerImpl.SWITCH_INTERFACE_CONFIG_TABLE_NAME, 
                row2);
        
        
        IOFSwitch mockSwitch1 = createMock(IOFSwitch.class);
        expect(mockSwitch1.getId()).andReturn(1L).anyTimes();
        IOFSwitch mockSwitch2 = createMock(IOFSwitch.class);
        expect(mockSwitch2.getId()).andReturn(2L).anyTimes();
        ArrayList<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        for (int i = 0; i < 100; i++) {
            int n = 0;
            for (String s : new String[] {"A","B","C","D","E"}) {
                OFPhysicalPort p = new OFPhysicalPort();
                short portNumber = (short)(i+100*n);
                String portName = s + i;
                p.setName(portName);
                p.setPortNumber(portNumber);
                ports.add(p);
                n += 1;
                expect(mockSwitch1.getPort(portName)).andReturn(p).anyTimes();
                expect(mockSwitch2.getPort(portName)).andReturn(p).anyTimes();
            }
        }
        expect(mockSwitch1.getEnabledPorts()).andReturn(ports).anyTimes();
        expect(mockSwitch2.getEnabledPorts()).andReturn(ports).anyTimes();
        expect(mockSwitch1.portEnabled(anyObject(OFPhysicalPort.class)))
                .andReturn(true).anyTimes();
        expect(mockSwitch2.portEnabled(anyObject(OFPhysicalPort.class)))
                .andReturn(true).anyTimes(); 
        
        // make sure the switches will replay the features.
        replay (mockSwitch1, mockSwitch2);
        
        // set the mock sdnplatform provider.
        getNetVirtManager().controllerProvider = mockControllerProvider;

        // Lis of switches that the mock sdnplatform provider will provide
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();


        // There should not be any interfaces for broadcast before the 
        // switches join.
        swt = getNetVirtManager().getBroadcastSwitchPorts();        
        assertTrue(swt != null && swt.isEmpty());

        // Add one switch and check if the braodcast interfaces shows it.
        switches.put(1L, mockSwitch1);
        mockControllerProvider.setSwitches(switches);
        getNetVirtManager().addedSwitch(mockSwitch1);
        swt = getNetVirtManager().getBroadcastSwitchPorts();        
        assertTrue(swt != null && swt.size() == 1); 
        
        //  add the second switch and see if two interfaces are returned.
        switches.put(2L, mockSwitch2);
        mockControllerProvider.setSwitches(switches);
        getNetVirtManager().addedSwitch(mockSwitch2); 
        swt = getNetVirtManager().getBroadcastSwitchPorts();        
        assertTrue(swt != null && swt.size() == 2); 
        
        // remove a switch and see if only one interface is returned.
        switches.remove(2L);
        mockControllerProvider.setSwitches(switches);
        getNetVirtManager().removedSwitch(mockSwitch2);         
        swt = getNetVirtManager().getBroadcastSwitchPorts();        
        assertTrue(swt != null && swt.size() == 1); 
        
    }
    
    @Test
    public void testGetIfaceFromName() {
        IDevice d = mockDeviceManager.learnEntity(1L, null, null, null, null);
        VNSInterface iface = netVirtManager.getIfaceFromName(
                                     new String[] {"default", d.getMACAddressString()},
                                     null, d, null);
        assertEquals(iface.getParentVNS().getName(), "default|default");

        VNS b = new VNS("default|office");
        iface = netVirtManager.getIfaceFromName(
                                     new String[] {"default", d.getMACAddressString()},
                                     b, d, null);
        assertEquals(iface.getParentVNS().getName(), "default|office");
    }

    protected NetVirtManagerImpl getNetVirtManager() {
        return netVirtManager;
    }
    
    protected MockTagManager getTagManager() {
        return mockTagManager;
    }
    
    protected MockDeviceManager getMockDeviceManager() {
        return mockDeviceManager;
    }
    
    protected IStorageSourceService getStorageSource() {
        return storageSource;
    }
    
    protected IFlowCacheService getFlowCacheMgr() {
        return flowCacheMgr;
    }
}
