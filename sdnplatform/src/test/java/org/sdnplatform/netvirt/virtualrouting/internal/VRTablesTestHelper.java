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
import java.util.Map;

import org.junit.Test;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.storage.IStorageSourceService;

@SuppressWarnings("unchecked")
public class VRTablesTestHelper {
    ArrayList<Map<String, Object>> tenantList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> virtRtrList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> virtRtrIfaceList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> ifaceIpAddrList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> routingRuleList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> staticArpList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> virtRtrGatewayPoolList =
            new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> gatewayNodeList =
            new ArrayList<Map<String, Object>>();

    public void addTenant(Map<String, Object>... tenants) {
        for (Map<String, Object> tenant : tenants) {
            tenantList.add(tenant);
        }
    }
    public void addVirtRtr(Map<String, Object>... routers) {
        for (Map<String, Object> router : routers) {
            virtRtrList.add(router);
        }
    }
    public void addVirtRtrIface(Map<String, Object>... ifaces) {
        for (Map<String, Object> iface : ifaces) {
            virtRtrIfaceList.add(iface);
        }
    }
	public void addIfaceIpAddr(Map<String, Object>... ips) {
        for (Map<String, Object> ip : ips) {
            ifaceIpAddrList.add(ip);
        }
    }
    public void addRoutingRule(Map<String, Object>... rules) {
        for (Map<String, Object> rule : rules) {
            routingRuleList.add(rule);
        }
    }
    public void addStaticArp(Map<String, Object>... arps) {
        for (Map<String, Object> arp : arps) {
            staticArpList.add(arp);
        }
    }
    public void addVirtRtrGatewayPool(Map<String, Object>... gwPools) {
        for (Map<String, Object> gwPool : gwPools) {
            virtRtrGatewayPoolList.add(gwPool);
        }
    }
    public void addGatewayNode(Map<String, Object>... gwNodes) {
        for (Map<String, Object> gwNode : gwNodes) {
            gatewayNodeList.add(gwNode);
        }
    }

    public void writeToStorage(IStorageSourceService storageSource) {
        for (Map<String, Object> row : tenantList) {
            storageSource.insertRow(VirtualRouting.TENANT_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : virtRtrList) {
            storageSource.insertRow(VirtualRouting.VIRT_RTR_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : virtRtrIfaceList) {
            storageSource.insertRow(VirtualRouting.VIRT_RTR_IFACE_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : ifaceIpAddrList) {
            storageSource.insertRow(VirtualRouting.IFACE_ADDRESS_POOL_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : routingRuleList) {
            storageSource.insertRow(VirtualRouting.VIRT_RTR_ROUTING_RULE_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : staticArpList) {
            storageSource.insertRow(VirtualRouting.STATIC_ARP_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : virtRtrGatewayPoolList) {
            storageSource.insertRow(VirtualRouting.VIRT_RTR_GATEWAY_POOL_TABLE_NAME,
                                    row);
        }
        for (Map<String, Object> row : gatewayNodeList) {
            storageSource.insertRow(VirtualRouting.GATEWAY_NODE_TABLE_NAME,
                                    row);
        }
    }

    public Map<String, Object> createTenant(String name, boolean active) {
        Map<String, Object> t = new HashMap<String, Object>();
        t.put(VirtualRouting.NAME_COLUMN_NAME, name);
        t.put(VirtualRouting.ACTIVE_COLUMN_NAME, active);
        return t;
    }

    public Map<String, Object> createRouter(Map<String, Object> tenant,
                                            String name) {
        Map<String, Object> r = new HashMap<String, Object>();
        String tName = (String) tenant.get(VirtualRouting.NAME_COLUMN_NAME);
        String id = new StringBuilder().append(tName).append("|").append(name).toString();
        r.put(VirtualRouting.ID_COLUMN_NAME, id);
        r.put(VirtualRouting.TENANT_COLUMN_NAME, tName);
        r.put(VirtualRouting.VIRT_RTR_COLUMN_NAME, name);
        return r;
    }

    public Map<String, Object> createIface(Map<String, Object> router,
                                           String name, boolean active,
                                           String netVirt, String rtr) {
        Map<String, Object> iface = new HashMap<String, Object>();
        String rName = (String) router.get(VirtualRouting.ID_COLUMN_NAME);
        String id = new StringBuilder().append(rName).append("|").append(name).toString();
        iface.put(VirtualRouting.ID_COLUMN_NAME, id);
        iface.put(VirtualRouting.VIRT_RTR_ID_COLUMN_NAME, rName);
        iface.put(VirtualRouting.VIRT_RTR_IFACE_COLUMN_NAME, name);
        iface.put(VirtualRouting.ACTIVE_COLUMN_NAME, active);
        iface.put(VirtualRouting.VNS_CONNECTED_COLUMN_NAME, netVirt);
        iface.put(VirtualRouting.RTR_CONNECTED_COLUMN_NAME, rtr);
        return iface;
    }

    public Map<String, Object> createIfaceIp(Map<String, Object> iface,
                                             String ip, String mask) {
        Map<String, Object> ifaceIp = new HashMap<String, Object>();
        String ifName = (String) iface.get(VirtualRouting.ID_COLUMN_NAME);
        String id = new StringBuilder().append(ifName).append("|").append(ip).toString();
        ifaceIp.put(VirtualRouting.ID_COLUMN_NAME, id);
        ifaceIp.put(VirtualRouting.VIRT_RTR_IFACE_ID_COLUMN_NAME, ifName);
        ifaceIp.put(VirtualRouting.IP_ADDRESS_COLUMN_NAME, ip);
        ifaceIp.put(VirtualRouting.SUBNET_MASK_COLUMN_NAME, mask);
        return ifaceIp;
    }

    public Map<String, Object> createRoutingRule(Map<String, Object> router,
                                                 String srcTenant,
                                                 String srcNetVirt,
                                                 String srcIp, String srcMask,
                                                 String dstTenant,
                                                 String dstNetVirt,
                                                 String dstIp, String dstMask,
                                                 Map<String, Object> iface,
                                                 String nextHop,
                                                 String action,
                                                 String nextHopGatewayPool) {
        Map<String, Object> rr = new HashMap<String, Object>();
        String rName = (String) router.get(VirtualRouting.ID_COLUMN_NAME);
        String ifName;
        if (iface != null)
            ifName = (String) iface.get(VirtualRouting.ID_COLUMN_NAME);
        else
            ifName = null;
        String id = new StringBuilder().append(rName).append("|").
                append(srcTenant).append("|").append(srcNetVirt).append("|").
                append(srcIp).append("|").append(srcMask).append("|").
                append(dstTenant).append("|").append(dstNetVirt).append("|").
                append(dstIp).append("|").append(dstMask).toString();
        rr.put(VirtualRouting.ID_COLUMN_NAME, id);
        rr.put(VirtualRouting.VIRT_RTR_ID_COLUMN_NAME, rName);
        rr.put(VirtualRouting.SRC_TENANT_COLUMN_NAME, srcTenant);
        rr.put(VirtualRouting.SRC_VNS_COLUMN_NAME, srcNetVirt);
        rr.put(VirtualRouting.SRC_IP_COLUMN_NAME, srcIp);
        rr.put(VirtualRouting.SRC_IP_MASK_COLUMN_NAME, srcMask);
        rr.put(VirtualRouting.DST_TENANT_COLUMN_NAME, dstTenant);
        rr.put(VirtualRouting.DST_VNS_COLUMN_NAME, dstNetVirt);
        rr.put(VirtualRouting.DST_IP_COLUMN_NAME, dstIp);
        rr.put(VirtualRouting.DST_IP_MASK_COLUMN_NAME, dstMask);
        rr.put(VirtualRouting.OUTGOING_INTF_COLUMN_NAME, ifName);
        rr.put(VirtualRouting.NEXT_HOP_IP_COLUMN_NAME, nextHop);
        rr.put(VirtualRouting.ACTION_COLUMN_NAME, action);
        rr.put(VirtualRouting.NEXT_HOP_GATEWAY_POOL_COLUMN_NAME,
               nextHopGatewayPool);
        return rr;
    }

    public Map<String, Object> createRoutingRule(Map<String, Object> router,
                                                 String srcTenant,
                                                 String srcNetVirt,
                                                 String srcIp, String srcMask,
                                                 String dstTenant,
                                                 String dstNetVirt,
                                                 String dstIp, String dstMask,
                                                 Map<String, Object> iface,
                                                 String nextHop,
                                                 String action) {
        return createRoutingRule(router, srcTenant, srcNetVirt, srcIp, srcMask,
                                 dstTenant, dstNetVirt, dstIp, dstMask,
                                 iface, nextHop, action, null);
    }
    public Map<String, Object> createStaticArp(String ip, String mac) {
        Map<String, Object> arp = new HashMap<String, Object>();
        arp.put(VirtualRouting.IP_COLUMN_NAME, ip);
        arp.put(VirtualRouting.MAC_COLUMN_NAME, mac);
        return arp;
    }

    public Map<String, Object> createGatewayPool(Map<String, Object> router,
                                                 String name) {
        Map<String, Object> gatewayPool = new HashMap<String, Object>();
        String rName = (String) router.get(VirtualRouting.ID_COLUMN_NAME);
        String id = new StringBuilder().append(rName).append("|").append(name).toString();
        gatewayPool.put(VirtualRouting.ID_COLUMN_NAME, id);
        gatewayPool.put(VirtualRouting.VIRT_RTR_ID_COLUMN_NAME, rName);
        gatewayPool.put(VirtualRouting.VIRT_RTR_GATEWAY_POOL_COLUMN_NAME, name);
        return gatewayPool;
    }

    public Map<String, Object> createGatewayNode(Map<String, Object> gwPool,
                                                 String ip) {
        Map<String, Object> gwNode = new HashMap<String, Object>();
        String gatewayPoolName = (String) gwPool.get(VirtualRouting.ID_COLUMN_NAME);
        String id = new StringBuilder().append(gatewayPoolName).append("|").append(ip).toString();
        gwNode.put(VirtualRouting.ID_COLUMN_NAME, id);
        gwNode.put(VirtualRouting.VIRT_RTR_GATEWAY_POOL_ID_COLUMN_NAME, gatewayPoolName);
        gwNode.put(VirtualRouting.IP_ADDRESS_COLUMN_NAME, ip);
        return gwNode;
    }

    @Test
    public void test() {
        /* Nothing to test for this class */
    }
}
