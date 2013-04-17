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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction;
import org.sdnplatform.netvirt.virtualrouting.GatewayNode;
import org.sdnplatform.netvirt.virtualrouting.IGatewayPool;
import org.sdnplatform.netvirt.virtualrouting.IVRouter;
import org.sdnplatform.netvirt.virtualrouting.IVirtualMacService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.VirtualMACExhaustedException;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction.DropReason;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.util.IPV4Subnet;
import org.sdnplatform.util.IPV4SubnetTrie;
import org.sdnplatform.util.MACAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class VirtualRouterManager {
    public static final long VIRTUAL_ROUTING_MAC =
            Ethernet.toLong(Ethernet.toMACAddress("5C:16:C7:01:00:00"));
    protected static final Logger logger =
            LoggerFactory.getLogger(VirtualRouterManager.class);

    public static class RoutingRuleParams {
        String owner;       /* The owner router name */
        String srcTenant;   /* The source tenant */
        String srcVNS;      /* The source netVirt */
        String srcIp;       /* The source IP */
        String srcMask;     /* The source subnet mask */
        String dstTenant;   /* The dest tenant */
        String dstVNS;      /* The dest netVirt */
        String dstIp;       /* The dest IP */
        String dstMask;     /* The dest subnet mask */
        String outIface;    /* The interface to send packets out of */
        String nextHopIp;   /* The next hop IP */
        String action;      /* The action PERMIT/DENY */
        String nextHopGatewayPool; /* The next hop Gateway Pool */
    }

    protected static class Tenant {
        String name;    /* The name of this tenant */
        boolean active; /* Indicates whether the tenant is active or not */

        public Tenant(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    /* Map of all tenants */
    protected Map<String, Tenant> tenants;
    /* Maintains a map of all router objects based on their names */
    protected Map<String, IVRouter> vRouters;
    /* Maintains a mapping of all NetVirts to the virtual router it connects to */
    protected Map<String, IVRouter> netVirtToRouterMap;
    protected IVirtualMacService vMacManager;
    protected IRewriteService rewriteService;
    /* Map of all the virtual MACs used by this virtual router manager */
    /* XXX Using only one MAC:
    protected Set<Long> vMacSet;
    */
    protected IDeviceService deviceManager;
    protected INetVirtManagerService netVirtManager;
    protected ILinkDiscoveryService linkDiscovery;
    protected ITopologyService topology;
    protected IRoutingService routingService;
    protected ITunnelManagerService tunnelManager;

    /* The static ARP table */
    protected Map<Integer, MACAddress> staticArpTable;
    /* The trie of all subnets to tenant routers owning them */
    protected IPV4SubnetTrie<String> subnetTrie;
    /* Map of router interface IP address to the netVirt name that router interface
     * is connected to. Used for testing router interface reachability
     */
    protected Map<Integer, VRouterInterface> ifaceIpMap;

    public VirtualRouterManager() {
        tenants = new HashMap<String, Tenant>();
        vRouters = new HashMap<String, IVRouter>();
        netVirtToRouterMap = new HashMap<String, IVRouter>();
        /* XXX using only one MAC for all virtual routers
        vMacSet = Collections.
                newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        */
        subnetTrie = new IPV4SubnetTrie<String>();
        ifaceIpMap = new HashMap<Integer, VRouterInterface>();
    }

    // ***************
    // Getters and Setters
    // ***************

    public Map<Integer, MACAddress> getStaticArpTable() {
        return staticArpTable;
    }

    public void setStaticArpTable(Map<Integer, MACAddress> staticArpTable) {
        this.staticArpTable = staticArpTable;
    }

    public void setNetVirtManager(INetVirtManagerService netVirtManager) {
        this.netVirtManager = netVirtManager;
    }

    public void setvMacManager(IVirtualMacService vMacManager) {
        this.vMacManager = vMacManager;
    }

    public void setRewriteService(IRewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    public void setDeviceManager(IDeviceService deviceManager) {
        this.deviceManager = deviceManager;
    }

    public void setLinkDiscovery(ILinkDiscoveryService linkDiscovery) {
        this.linkDiscovery = linkDiscovery;
    }

    public void setTopology(ITopologyService topology) {
        this.topology = topology;
    }

    public ITopologyService getTopology() {
        return topology;
    }

    public void setRoutingService(IRoutingService routingService) {
        this.routingService = routingService;
    }

    public IRoutingService getRoutingService() {
        return routingService;
    }

    public ITunnelManagerService getTunnelManager() {
        return tunnelManager;
    }

    public void setTunnelManager(ITunnelManagerService tunnelManager) {
        this.tunnelManager = tunnelManager;
    }

    // ***************
    // Virtual Routing Related
    // ***************

    private String[] splitEntityName(String name)
            throws IllegalArgumentException {
        if (name == null) {
            return null;
        }
        String[] n = name.split("\\|");
        if (n.length != 2) {
            String err = new StringBuilder().append("Invalid format ").
                    append(name).append(", expected <tenant>|<entity>").
                    toString();
            throw new IllegalArgumentException(err);
        }
        return n;
    }

    /**
     * Create a new tenant object
     * @param name Name of the tenant
     * @param active Whether the tenant is active or not
     */
    public void createTenant(String name, boolean active) {
        Tenant tenant = new Tenant(name, active);
        tenants.put(name, tenant);
    }

    /**
     * Create a virtual router
     * @param rtrName The name of the router
     * @param tenantName Name of the tenant to which it belongs
     * @throws IllegalArgumentException if the tenant is not present
     *         VirtualMACExhaustedException if it is unable to acquire a virtual
     *         MAC
     */
    public void createVirtualRouter(String rtrName, String tenantName)
                                    throws IllegalArgumentException,
                                    VirtualMACExhaustedException {
        Tenant tenant = tenants.get(tenantName);
        if (tenant == null) {
            String err = new StringBuilder().append("Unknonwn tenant ").
                    append(tenantName).toString();
            throw new IllegalArgumentException(err);
        }
        String name = new StringBuilder().append(tenantName).append("|").
                append(rtrName).toString();
        /* XXX HACK (or maybe not). We are using just one MAC for all virtual
         * routers. This is to ensure that the end VMs do not see a MAC address
         * change on a failover or a virtual router config change.
         */
        //Long vMac = vMacManager.acquireVirtualMac();

        // packets sent by virtual routing with virtual-router mac address as the
        // src-mac address can come back to the controller as a  packet-in (from
        // bcast domain) - ensure that these packets are ignored early in the pipeline
        linkDiscovery.addMACToIgnoreList(VIRTUAL_ROUTING_MAC, 0);

        Long vMac = Long.valueOf(VIRTUAL_ROUTING_MAC);
        IVRouter router = new VRouterImpl(name, tenantName, vMac, this);
        vRouters.put(name, router);

        /* XXX Using only one MAC for all routers
        vMacSet.add(vMac);
        */
    }

    /**
     * Create a router interface
     * @param rtrName The name of the router owning the interface
     * @param name The interface name
     * @param netVirtName The netVirt name connected to the interface
     * @param nextRtrName The router name connected to the interface
     * @param active Whether the interface is active or not
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public void addVRouterIface(String rtrName, String name, String netVirtName,
                                String nextRtrName, boolean active)
                                throws IllegalArgumentException {
        String[] o = splitEntityName(rtrName);
        String tenantName = o[0];
        IVRouter router = vRouters.get(rtrName);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(rtrName).toString();
            throw new IllegalArgumentException(err);
        }

        if (netVirtName != null) {
            if (nextRtrName != null) {
                String err = new StringBuilder().
                        append("Cannot specify both netVirt and router in iface ").
                        append(name).append(" for router ").append(rtrName).
                        toString();
                throw new IllegalArgumentException(err);
            }
            String b[] = splitEntityName(netVirtName);
            if (!b[0].equals(tenantName)) {
                /* The netVirt and owner are in different tenants */
                String err = new StringBuilder().append("Invalid tenant ").
                        append(b[0]).append(", expected ").append(tenantName).
                        toString();
                throw new IllegalArgumentException(err);
            }
            netVirtToRouterMap.put(netVirtName, router);
        } else {
            if (nextRtrName == null) {
                String err = new StringBuilder().
                        append("Must specify netVirt or router: iface ").
                        append(name).append(" router ").append(rtrName).
                        toString();
                throw new IllegalArgumentException(err);
            }
            IVRouter nextRouter = vRouters.get(nextRtrName);
            if (nextRouter == null) {
                String err = new StringBuilder().append("Unknown router ").
                        append(nextRtrName).toString();
                throw new IllegalArgumentException(err);
            }
        }
        router.createInterface(name, netVirtName, nextRtrName, active);
    }

    /**
     * Assign an IP address to an interface
     * @param ifaceId The interface id '<tenant>|<router>|<iface>'
     * @param ip The ip address
     * @param subnet The subnet mask
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public void addIfaceIp(String ifaceId, String ip, String subnet)
                           throws IllegalArgumentException {
        String[] o = ifaceId.split("\\|");
        if (o.length != 3) {
            String err = new StringBuilder().append("Invalid iface name ").
                    append(ifaceId).toString();
            throw new IllegalArgumentException(err);
        }
        String tenantName = o[0];
        String rtrName = new StringBuilder().append(o[0]).append("|").
                append(o[1]).toString();
        String ifaceName = o[2];
        IVRouter router = vRouters.get(rtrName);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(rtrName).append(" in tenant ").append(tenantName).
                    toString();
            throw new IllegalArgumentException(err);
        }
        router.assignInterfaceAddr(ifaceName, ip, subnet);
    }

    /**
     * Adds a routing rule to a router
     * Atleast one of srcTenant/srcNetVirt/(srcIp + srcMask) must be specified
     * Atleast one of dstTenant/dstNetVirt/(dstIp + dstMask) must be specified
     * @param p The routing rule parameters
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public void addRoutingRule(RoutingRuleParams p)
            throws IllegalArgumentException {
        String[] o = splitEntityName(p.owner);
        String tenantName = o[0];
        String rtrName = o[1];
        IVRouter router = vRouters.get(p.owner);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(p.owner).toString();
            throw new IllegalArgumentException(err);
        }

        IVRouter r;
        if (p.srcVNS != null) {
            r = netVirtToRouterMap.get(p.srcVNS);
            if (r == null) {
                String err = new StringBuilder().
                        append("NetVirt not attached to router ").append(p.srcVNS).
                        toString();
                throw new IllegalArgumentException(err);
            }
        }
        if (p.dstVNS != null) {
            r = netVirtToRouterMap.get(p.dstVNS);
            if (r == null) {
                String err = new StringBuilder().
                        append("NetVirt not attached to router ").append(p.dstVNS).
                        toString();
                throw new IllegalArgumentException(err);
            }
        }

        if (p.srcTenant != null) {
            Tenant tenant = tenants.get(p.srcTenant);
            if (tenant == null) {
                String err = new StringBuilder().append("Unknown tenant ").
                        append(p.srcTenant).toString();
                throw new IllegalArgumentException(err);
            }
        }
        if (p.dstTenant != null) {
            Tenant tenant = tenants.get(p.dstTenant);
            if (tenant == null) {
                String err = new StringBuilder().append("Unknown tenant ").
                        append(p.dstTenant).toString();
                throw new IllegalArgumentException(err);
            }
        }

        String iface = null;
        if (p.outIface != null) {
            String[] i = p.outIface.split("\\|");
            if (i.length != 3 || !i[0].equals(tenantName) ||
                    !i[1].equals(rtrName)) {
                String err = new StringBuilder().append("Invalid iface ").
                        append(p.outIface).append(" for router ").
                        append(p.owner).toString();
                throw new IllegalArgumentException(err);
            }
            iface = i[2];
        }

        String gwPool = null;
        if (p.nextHopGatewayPool != null) {
            String[] g = p.nextHopGatewayPool.split("\\|");
            if (g.length != 3 || !g[0].equals(tenantName) ||
                    !g[1].equals(rtrName)) {
                String err = new StringBuilder().append("Invalid gwPool ").
                        append(p.nextHopGatewayPool).append(" for router ").
                        append(p.owner).toString();
                throw new IllegalArgumentException(err);
            }
            gwPool = g[2];
        }
        router.addRoutingRule(p.srcTenant, p.srcVNS, p.srcIp, p.srcMask,
                              p.dstTenant, p.dstVNS, p.dstIp, p.dstMask,
                              iface, p.nextHopIp, p.action,
                              gwPool);
    }

    /**
     * Create a gateway pool on a router
     * @param rtrName The name of the router owning the interface
     * @param name The gateway pool name
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public void addGatewayPool(String rtrName, String name)
                                throws IllegalArgumentException {
        IVRouter router = vRouters.get(rtrName);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(rtrName).toString();
            throw new IllegalArgumentException(err);
        }
        router.createGatewayPool(name);
    }

    /**
     * Add a gateway node to a gateway pool
     * @param gatewayNodeId The gateway node id of the format
     *        <tenant>|<router>|<gateway pool name>'
     * @param ip The ip address
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public void addGatewayPoolNode(String gatewayNodeId, String ip)
                           throws IllegalArgumentException {
        String[] o = gatewayNodeId.split("\\|");
        if (o.length != 3) {
            String err = new StringBuilder().append("Invalid gateway node name ").
                    append(gatewayNodeId).toString();
            throw new IllegalArgumentException(err);
        }
        String tenantName = o[0];
        String rtrName = new StringBuilder().append(o[0]).append("|").
                append(o[1]).toString();
        String gatewayPoolName = o[2];
        IVRouter router = vRouters.get(rtrName);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(rtrName).append(" in tenant ").append(tenantName).
                    toString();
            throw new IllegalArgumentException(err);
        }
        router.addGatewayPoolNode(gatewayPoolName, ip);
    }

    /**
     * Get the gateway pool object with given name that belongs to the
     * specified router (only used for testing)
     * @param rtrName name of the router
     * @param gatewayPoolName name of the gateway pool
     * @return gateway pool object
     */
    public IGatewayPool getGatewayPool(String rtrName,
                                       String gatewayPoolName) {
        IVRouter router = vRouters.get(rtrName);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(rtrName).toString();
            throw new IllegalArgumentException(err);
        }
        return router.getGatewayPool(gatewayPoolName);
    }

    /**
     * Remove a gateway node from a gateway pool (only used for testing)
     * @param gatewayNodeId The gateway node id of the format
     *        <tenant>|<router>|<gateway pool name>'
     * @param ip The ip address
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public void removeGatewayPoolNode(String gatewayNodeId, String ip)
                           throws IllegalArgumentException {
        String[] o = gatewayNodeId.split("\\|");
        if (o.length != 3) {
            String err = new StringBuilder().append("Invalid gateway node name ").
                    append(gatewayNodeId).toString();
            throw new IllegalArgumentException(err);
        }
        String tenantName = o[0];
        String rtrName = new StringBuilder().append(o[0]).append("|").
                append(o[1]).toString();
        String gatewayPoolName = o[2];
        IVRouter router = vRouters.get(rtrName);
        if (router == null) {
            String err = new StringBuilder().append("Unknown router ").
                    append(rtrName).append(" in tenant ").append(tenantName).
                    toString();
            throw new IllegalArgumentException(err);
        }
        router.removeGatewayPoolNode(gatewayPoolName, ip);
    }

    /**
     * Returns if there exists a netVirt interface in 'srcIfaces' that is allowed
     * to communicate with a netVirt interface in 'dstIfaces'. This also includes
     * the case where there is a common netVirt interface in the two sets
     * @param srcIfaces The list of source interfaces
     * @param dstIfaces The list of dest interfaces
     * @param srcIp The source IP address
     * @param dstIp The dest IP address
     * @return true if communication between the source and dest is allowed
     *         false otherwise
     */
    public boolean connected(List<VNSInterface> srcIfaces,
                             List<VNSInterface> dstIfaces, int srcIp,
                             int dstIp) {
        VNS srcNetVirt, dstNetVirt;

        if (logger.isTraceEnabled()) {
            logger.trace("VirtualRouterManager: connected() called. " +
                         "srcIp={}, dstIp={}", IPv4.fromIPv4Address(srcIp),
                         IPv4.fromIPv4Address(dstIp));
        }

        if (srcIfaces == null || dstIfaces == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("srcIfaces or dstIfaces is null returning false");
            }
            return false;
        }

        /*
         * Check if there are two NetVirts which are matching or are configured to
         * communicate by policy
         */
        for (VNSInterface sface : srcIfaces) {
            srcNetVirt = sface.getParentVNS();
            for (VNSInterface dface : dstIfaces) {
                dstNetVirt = dface.getParentVNS();
                if (srcNetVirt.equals(dstNetVirt)) {
                    /*
                     * device1 and device2 are in the same NetVirt so they are
                     * connected
                     */
                    if (logger.isTraceEnabled()) {
                        logger.trace("Source and dest are in the same NetVirt " +
                                     "({}), returning true", srcNetVirt.getName());
                    }
                    return true;
                } else {
                    ForwardingAction action = findRoute(srcNetVirt, srcIp, dstNetVirt,
                                                        dstIp);
                    if (action.getAction().equals(RoutingAction.FORWARD)) {
                        /* The two NetVirts are allowed to communicate by policy */
                        if (logger.isTraceEnabled()) {
                            logger.trace("Source NetVirt {} and dest netVirt {} are " +
                                         "allowed to communicate by VRS",
                                         srcNetVirt.getName(), dstNetVirt.getName());
                        }
                        return true;
                    }
                }
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Source and dest are not connected");
        }
        return false;
    }

    /**
     * Find a device based on the static ARP configuration or IP address.
     * If the static ARP MAC address is the same as the original MAC address,
     * then the device lookup is spared and we use the original device
     * @param oldDev The original device
     * @param ec The entity class to look in
     * @param origMAC The original MAC address of the device
     * @param vlan The vlan on the packet
     * @param ip The IP address of the device.
     * @return The device to use. null if no device is found
     */
    protected IDevice findDevice(IDevice oldDev, IEntityClass ec,
                                 long origMAC, short vlan, int ip) {
        /* Check if there is a static ARP entry for this IP. If so, try
         * to find the device associated with that MAC.
         */
        MACAddress mac = staticArpTable.get(Integer.valueOf(ip));
        if (mac != null) {
            if (origMAC != mac.toLong()) {
                /* If the origMAC is already the same as the static ARP table
                 * MAC, then the device lookup was already attempted by
                 * deviceManager, so we can skip this.
                 * If not, see if we can find the device with the corrected MAC.
                 */
                return deviceManager.findClassDevice(ec, mac.toLong(),
                                                     vlan, ip);
            } else {
                return oldDev;
            }
        } else {
            /* Attempt to find the device from the IP */
            Iterator<? extends IDevice> deviter =
                    deviceManager.queryClassDevices(ec, null, null, ip, null,
                                                    null);
            while (deviter.hasNext()) {
                IDevice dev = deviter.next();
                Integer[] ipv4addrs = dev.getIPv4Addresses();
                for (Integer ipv4addr : ipv4addrs) {
                    if (ipv4addr.equals(ip)) {
                        return dev;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Updates the explain packet if required
     * @param cntx The listener context
     * @param srcIface The source NetVirt interface chosen
     * @param dstIface The dest NetVirt interface chosen
     * @param retAction The return action
     */
    private void updateExplainPacket(ListenerContext cntx,
                                     VNSInterface srcIface,
                                     VNSInterface dstIface,
                                     ForwardingAction retAction) {
        if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
            NetVirtExplainPacket.ExplainPktVRouting.
            ExplainPktAddVRouteToContext(cntx, srcIface, dstIface, retAction);

            if (retAction.getAction() != RoutingAction.DROP) {
                NetVirtExplainPacket.explainPacketSetContext(cntx,
                                    NetVirtExplainPacket.KEY_EXPLAIN_PKT_SRC_NetVirt,
                                    srcIface.getParentVNS().getName());
                NetVirtExplainPacket.explainPacketSetContext(cntx,
                                    NetVirtExplainPacket.KEY_EXPLAIN_PKT_DST_NetVirt,
                                    dstIface.getParentVNS().getName());
            }
        }
    }

    /**
     * Sets the appropriate rewrite actions for this packet
     * @param cntx The listener context
     * @param act The return forwarding action
     * @param origDstMAC The original dest MAC
     * @param origSrcMAC The original source MAC
     * @param dst The destination device to which we are sending packets
     * @param vRouterMac Indicates if this is a virtual router MAC or not
     */
    private void setRewriteActions(ListenerContext cntx, ForwardingAction act,
                                   long origDstMAC, long origSrcMAC,
                                   IDevice dst, boolean vRouterMac) {
        /* Check if the dest MAC has changed or dest MAC is a vRouter mac.
         * If so, we MUST rewrite the dest MAC
         */
        long newDstMAC = dst.getMACAddress();
        if ((origDstMAC != newDstMAC) || vRouterMac) {
            if (logger.isTraceEnabled()) {
                logger.trace("Rewriting dst MAC from {} to {}",
                             HexString.toHexString(origDstMAC),
                             HexString.toHexString(newDstMAC));
            }
            rewriteService.setIngressDstMac(origDstMAC, newDstMAC, cntx);
            /* XXX If we are rewriting the dest MAC, we need the flowmod to
             * match the dest IP. Otherwise there is no way to send two
             * packets both destined to the virtual router MAC to
             * separate destinations.
             */
        }

        if (act.getNewSrcMac() != 0) {
            if (logger.isTraceEnabled()) {
                logger.trace("Rewriting src MAC from {} to {}",
                         HexString.toHexString(origSrcMAC),
                         HexString.toHexString(act.getNewSrcMac()));
            }
            rewriteService.setEgressSrcMac(origSrcMAC, act.getNewSrcMac(),
                                           cntx);
        }

        if (vRouterMac) {
            if (logger.isTraceEnabled()) {
                logger.trace("Decrementing TTL by 1");
            }
            rewriteService.setTtlDecrement(1, cntx);
        }
    }

    /**
     * If the packet is destined to a tunnel loopback port mac address,
     * but the destination IP does not correspond to a tunnel loopback
     * port on any of the switches, we assume they have been sent to the
     * tunnel loopback port by the NOF domain so that they can be
     * routed to the device with the destination IP address.
     * Such packets are subjected to the conventional virtual routing
     * logic, with the following pre-processing on the sdnplatform ctxt:
     *     o Clear CONTEXT_DST_DEVICE
     *     o Clear CONTEXT_DST_IFACES
     *
     * @param cntx - listener context
     * @param vlan id - vlan id of the packet
     * @param dstIp - destination ip of the packet
     * @return the original destination device if there was an update, null
     * otherwise
     */
    private IDevice
    updateDestDevice(ListenerContext cntx, short vlan, int dstIp) {
        IDevice srcDevice =
                IDeviceService.fcStore.get(cntx,
                                           IDeviceService.CONTEXT_SRC_DEVICE);
        IDevice origDstDevice =
                IDeviceService.fcStore.get(cntx,
                                           IDeviceService.CONTEXT_DST_DEVICE);
        if (origDstDevice == null) {
            return null;
        }
        if (!tunnelManager.isTunnelEndpoint(origDstDevice)) {
            return null;
        }
        if (dstIp == 0 ||
            tunnelManager.getSwitchDpid(dstIp) != null) {
            return null;
        }

        /*
         * Note on vlan: findDevice uses vlan only if it finds a static ARP
         * entry for the given IP address. Otherwise the device is found
         * merely by looking up the IP address.
         */
        IDevice newDstDevice = findDevice(null, srcDevice.getEntityClass(),
                                          0, vlan, dstIp);
        if (newDstDevice == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to find dstDevice for actual DstIp {}",
                             dstIp);
            }
        }

        IDeviceService.fcStore.remove(cntx,
                                      IDeviceService.CONTEXT_DST_DEVICE);
        INetVirtManagerService.bcStore.remove(cntx,
                                        INetVirtManagerService.CONTEXT_DST_IFACES);
        return origDstDevice;
    }

    /**
     * Checks if there exists a netVirt interface for the source that is allowed
     * to communicate with a netVirt interface for the dest. This also includes
     * the case where there is a common netVirt interface for source and dest
     * If the source and dest are allowed to communicate, it selects the netVirt
     * interfaces of the highest priority. This function handles virtual routing
     * and will configure rewrite manager to rewrite source and destination MACs
     * as well as decrement TTL when required.
     * @param origSrcMAC The source MAC of the flow
     * @param origDstMAC The dest MAC of the flow
     * @param vlan The vlan on the flow
     * @param srcIp The source IP address
     * @param dstIp The dest IP address
     * @param cntx The listener context
     * @return The forwarding action
     */
    public ForwardingAction getForwardingAction(long origSrcMAC,
                                                long origDstMAC, short vlan,
                                                short ethType, int srcIp,
                                                int dstIp,
                                                ListenerContext cntx) {
        VNS srcNetVirt = null, dstNetVirt = null;
        VNSInterface srcIfaceChosen = null, dstIfaceChosen = null;
        VNS srcNetVirtChosen = null, dstNetVirtChosen = null;
        boolean ret = false;
        IDevice src, dst, origDst, origTunnDst;
        ForwardingAction retAction = new ForwardingAction();
        List<VNSInterface> srcIfaces, dstIfaces, oldDstIfaces;

        srcIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        if (srcIfaces == null) return new ForwardingAction();

        src = IDeviceService.fcStore.get(cntx,
                                         IDeviceService.CONTEXT_SRC_DEVICE);

        origTunnDst = updateDestDevice(cntx, vlan, dstIp);
        origDst = IDeviceService.fcStore.get(cntx,
                                             IDeviceService.CONTEXT_DST_DEVICE);
        oldDstIfaces = INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        dstIfaces = oldDstIfaces;
        if (dstIfaces == null) {
            // We may have a static arp configured for this IP. Alternately we
            // may need to lookup a device based just on dst IP. The latter may
            // happen when the packet comes in with dst mac = v.router mac (we never
            // learn devices with src mac = v.router mac)
            dst = findDevice(origDst, src.getEntityClass(), origDstMAC, vlan,
                             dstIp);
            if (dst != null)
                dstIfaces = netVirtManager.getInterfaces(dst);
        } else {
            dst = origDst;
        }

        /* Find the source and dest NetVirt with the highest priority that are
         * allowed to communicate
         */
        for (VNSInterface sface : srcIfaces) {
            srcNetVirt = sface.getParentVNS();
            if (dstIfaces != null) {
                for (VNSInterface dface : dstIfaces) {
                    dstNetVirt = dface.getParentVNS();
                    if (srcNetVirt.equals(dstNetVirt)) {
                        /* Both devices are in the same NetVirt so they are
                         * connected and virtual routing is not necessary
                         */
                        if (!ret || srcNetVirt.compareTo(srcNetVirtChosen) < 0) {
                            ret = true;
                            srcNetVirtChosen = srcNetVirt;
                            dstNetVirtChosen = dstNetVirt;
                            srcIfaceChosen = sface;
                            dstIfaceChosen = dface;
                            retAction.setNewSrcMac(0);
                            retAction.setNextHopIp(dstIp);
                        }
                    } else {
                        /* Check if the two NetVirts are allowed to communicate by
                         * policy configured in virtual routing
                         */
                        ForwardingAction action;
                        action = findRoute(srcNetVirt, srcIp, dstNetVirt, dstIp);
                        if (action.getAction().equals(RoutingAction.FORWARD)) {
                            if (!action.getDstNetVirtName().
                                    equals(dstNetVirt.getName())) {
                                /* Routing has been configured to send this
                                 * packet to some other NetVirt possibly to a
                                 * special device (eg. for service insertion)
                                 */
                                dstNetVirt = netVirtManager.
                                        getVNS(action.getDstNetVirtName());
                            }
                            if (!ret || srcNetVirt.compareTo(srcNetVirtChosen) < 0 ||
                                    (srcNetVirt.compareTo(srcNetVirtChosen) == 0 &&
                                     dstNetVirt.compareTo(dstNetVirtChosen) < 0)) {
                                ret = true;
                                srcNetVirtChosen = srcNetVirt;
                                dstNetVirtChosen = dstNetVirt;
                                srcIfaceChosen = sface;
                                dstIfaceChosen = dface;
                                retAction.setNextHopIp(action.getNextHopIp());
                                retAction.setNewSrcMac(action.getNewSrcMac());
                                retAction.setNextHopGatewayPool(action.getNextHopGatewayPool());
                                retAction.setNextHopGatewayPoolRouter(action.getNextHopGatewayPoolRouter());
                            }
                        } else if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                            NetVirtExplainPacket.ExplainPktVRouting.
                            ExplainPktAddVRouteToContext(cntx, sface, dface,
                                                         action);
                        }
                    }
                }
            } else {
                ForwardingAction action;
                /* We weren't able to find the dst interfaces. Attempt to
                 * perform pure L3 routing from srcNetVirt
                 */
                action = findRoute(srcNetVirt, srcIp, null, dstIp);
                if (action.getAction().equals(RoutingAction.FORWARD)) {
                    if (!ret || srcNetVirt.compareTo(srcNetVirtChosen) < 0) {
                        ret = true;
                        srcNetVirtChosen = srcNetVirt;
                        dstNetVirtChosen = netVirtManager.getVNS(action.getDstNetVirtName());
                        srcIfaceChosen = sface;
                        dstIfaceChosen = null;
                        retAction.setNewSrcMac(action.getNewSrcMac());
                        retAction.setNextHopIp(action.getNextHopIp());
                        retAction.setNextHopGatewayPool(action.getNextHopGatewayPool());
                        retAction.setNextHopGatewayPoolRouter(action.getNextHopGatewayPoolRouter());
                    }
                } else if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                    NetVirtExplainPacket.ExplainPktVRouting.
                    ExplainPktAddVRouteToContext(cntx, sface, null, action);
                }
            }
        }

        /* XXX Using only one MAC for all virtual routers
        boolean vRouterMac = vMacSet.contains(origDstMAC);
        */
        boolean vRouterMac = false;
        if (origDstMAC == VIRTUAL_ROUTING_MAC) {
            vRouterMac = true;
            retAction.setDestinedToVirtualRouterMac(true);
        }

        if (ret && !srcNetVirtChosen.equals(dstNetVirtChosen) &&
                ethType == Ethernet.TYPE_IPv4) {
            /* This packet has forwarding action as RoutingAction.FORWARD (from 'ret').
             * Additionally this packet has been virtual routed since source and dest NetVirt are
             * different.
             */
            IDevice newDst = null;
            if (retAction.getNextHopGatewayPool() != null) {
                IVRouter router = retAction.getNextHopGatewayPoolRouter();
                GatewayNode gatewayNode =
                    router.getOptimalGatewayNodeInfo(
                                       retAction.getNextHopGatewayPool(),
                                       src, Short.valueOf(vlan));
                retAction.setNextHopIp((gatewayNode != null) ?
                                       gatewayNode.getIp() : 0);
                newDst = (gatewayNode != null) ? gatewayNode.getDevice(): null;
                if (logger.isTraceEnabled()) {
                    logger.trace("GWPool Optimal node {} for Src {}",
                                 gatewayNode,
                                 IPv4.fromIPv4Address(srcIp));
                }
            }
            /* This appears to be dead code because if the next hop IP is 0, then
             * the forwarding action shouldn't be forwarding in the first place
             *
            else if (retAction.getNextHopIp() == 0) {
                newDst = null;
            }
            */
            else if (retAction.getNextHopIp() != dstIp) {
                newDst = findDevice(dst, src.getEntityClass(), origDstMAC, vlan,
                                    retAction.getNextHopIp());
            } else {
                /* New destination is the same as original destination */
                newDst = dst;
            }

            /* At this point if the destination is unknown (silent host), it is
             * possible that newDst is null
             */
            if (newDst == null) {
                /* Device manager failed to lookup this next hop device. We do
                 * not have a static ARP entry for this next hop IP. We have
                 * attempted to find this device using the IP addr but we still
                 * don't know this device. This is a silent host for which we
                 * don't know the rewrite address. We should ARP for this but for
                 * now just return
                 */
                if (logger.isTraceEnabled()) {
                    logger.trace("Destination unknown after virtual routing");
                }
                retAction.setDropReason(DropReason.NEXT_HOP_UNKNOWN);
                retAction.setDropInfo(IPv4.fromIPv4Address(retAction.getNextHopIp()));
                updateExplainPacket(cntx, srcIfaceChosen, dstIfaceChosen,
                                    retAction);
                return retAction;
            }

            if (dstIfaceChosen == null || newDst != dst) {
                /* If the next hop is different from the original dest, we need
                 * to look up the dest interface again
                 */
                dstIfaceChosen = null;
                List<VNSInterface> ifaces = netVirtManager.getInterfaces(newDst);
                for (VNSInterface i : ifaces) {
                    if (i.getParentVNS().equals(dstNetVirtChosen)) {
                        dstIfaceChosen = i;
                        break;
                    }
                }
            }

            if (dstIfaceChosen == null) {
                /* The new destination device is not in the NetVirt that was chosen
                 * return DROP for now
                 */
                if (logger.isTraceEnabled()) {
                    logger.trace("New destination does not belong to netVirt {}",
                                 dstNetVirtChosen.getName());
                }
                retAction.setDropReason(DropReason.NetVirt_MISMATCH);
                retAction.setDropInfo(dstNetVirtChosen.getName());
                updateExplainPacket(cntx, srcIfaceChosen, null,
                                    retAction);
                return retAction;
            }

            setRewriteActions(cntx, retAction, origDstMAC, origSrcMAC, newDst,
                              vRouterMac);

            if (!newDst.equals(origDst)) {
                /* The next hop has changed. Update the context */
                IDeviceService.fcStore.put(cntx,
                                           IDeviceService.CONTEXT_DST_DEVICE,
                                           newDst);
                if (origTunnDst != null) {
                    IDeviceService.fcStore.put(cntx,
                                       IDeviceService.CONTEXT_ORIG_DST_DEVICE,
                                       origTunnDst);
                }
            }
        } else if (ret && vRouterMac) {
            /* The source and dest devices belong to the same NetVirt but the packet
             * is destined to the virtual router.
             */
            long vMac = getRtrVMac(srcNetVirtChosen, srcIp, dstIp);
            retAction.setNewSrcMac(vMac);
            setRewriteActions(cntx, retAction, origDstMAC, origSrcMAC, dst,
                              vRouterMac);
            IDeviceService.fcStore.put(cntx, IDeviceService.CONTEXT_DST_DEVICE,
                                       dst);
        }

        /* If the dest MAC is a virtual router MAC we MUST annotate this flow
         * as a virtual routed flow in the flow cache. This is because we have
         * made decisions on this flow using virtual routing
         */
        if (vRouterMac || (ret && !srcNetVirtChosen.equals(dstNetVirtChosen))) {
            /* Annotate the flow cache with virtual routing service */
            IFlowCacheService.fcStore.
            put(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                IVirtualRoutingService.VRS_FLOWCACHE_NAME);
            retAction.setVirtualRouted(true);
        } else if (ret) {
            IFlowCacheService.fcStore.
            put(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                srcNetVirtChosen.getName());
        } else {
            IFlowCacheService.fcStore.
            put(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                IVirtualRoutingService.VRS_FLOWCACHE_NAME);
        }

        if (ret) {
            if (srcIfaces.size() > 1) {
                srcIfaces = Collections.singletonList(srcIfaceChosen);
                INetVirtManagerService.bcStore.
                put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
            }
            if (oldDstIfaces == null || oldDstIfaces.size() > 1) {
                dstIfaces = Collections.singletonList(dstIfaceChosen);
                INetVirtManagerService.bcStore.
                put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);
            }

            // Annotate netVirtName if the packetIn belongs to a NetVirt
            IVirtualRoutingService.vrStore.
            put(cntx, IVirtualRoutingService.NetVirt_NAME,
                srcNetVirtChosen.getName());
            retAction.setAction(RoutingAction.FORWARD);
            updateExplainPacket(cntx, srcIfaceChosen, dstIfaceChosen,
                                retAction);
            return retAction;
        } else if (dstIfaces != null) {
            /* The two sets of interfaces are not allowed to communicate */
            INetVirtManagerService.bcStore.
            remove(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
            INetVirtManagerService.bcStore.
            remove(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
        }

        return retAction;
    }

    /**
     * Look up routing table and check if the source is allowed to communicate
     * with the dest via virtual routing feature. Returns the forwarding action
     * if this is the case
     * @param srcNetVirt The source NetVirt
     * @param srcIp The source IP
     * @param dstNetVirt The dest NetVirt
     * @param dstIp The dest IP
     * @return The forwarding action
     */
    private ForwardingAction findRoute(VNS srcNetVirt, int srcIp, VNS dstNetVirt,
                                       int dstIp) {
        String entity = srcNetVirt.getName();
        IVRouter vRouter = netVirtToRouterMap.get(entity);
        int hopcount = 1;
        ForwardingAction fAction = new ForwardingAction();

        if (vRouter == null) {
            fAction.setDropReason(DropReason.UNKNOWN_SRC_RTR);
            fAction.setDropInfo(entity);
            return fAction;
        }

        while (hopcount < 256) {
            if (vRouter.isIfaceDown(entity)) {
                fAction.setAction(RoutingAction.DROP);
                fAction.setDropReason(DropReason.IFACE_DOWN);
                String info;
                info = vRouter.getName() + " " + entity;
                fAction.setDropInfo(info);
                break;
            }
            fAction = (ForwardingAction)
                    vRouter.getForwardingAction(entity, srcNetVirt, srcIp, dstNetVirt,
                                                dstIp);
            if (fAction.getAction() == RoutingAction.DROP) {
                break;
            }
            String nextRouter = fAction.getNextRtrName();
            if (nextRouter != null) {
                entity = vRouter.getName();
                vRouter = vRouters.get(nextRouter);
                hopcount++;
            } else {
                /* Destination NetVirt is found */
                break;
            }
        }
        /* If the hopcount reaches 256, we hit max TTL. Drop packet.
         * XXX This indicates that there is a route loop
         */
        return fAction;
    }

    /**
     * Give up all the vMacs that are used by routers of this object
     * This method should be called only when this object is being destroyed
     */
    public void relinquishVMacs() {
        /* Only one thread can be deleting calling this function at a time */
        /* XXX Only one virtual router MAC for all virtual routers
        for (Long vMac : vMacSet) {
            vMacManager.relinquishVirtualMAC(vMac);
        }

        vMacSet.clear();
        */
    }

    /**
     * Tests whether a router IP is reachable from a source NetVirt + src IP
     * combination and returns the router MAC
     * The behaviour of this function is undefined if a router IP belongs to
     * more than one router interfaces. However, it will always return a router
     * MAC address if the source NetVirt is directly connected to the interface
     * configured with the IP.
     * @param srcNetVirt The source NetVirt
     * @param srcIp The source IP
     * @param dstIp The dest IP. This IP must belong to a router
     * @return a router MAC if the router IP is reachable by the source.
     *         0 otherwise
     */
    public long getRtrVMac(VNS srcNetVirt, int srcIp, int dstIp) {
        String srcNetVirtName = srcNetVirt.getName();
        IVRouter vRouter = netVirtToRouterMap.get(srcNetVirtName);
        if (vRouter == null) return 0;

        long vMac = vRouter.getVMac(srcNetVirtName, dstIp);
        if (vMac != 0) {
            /* The dstIp is configured on an interface that is directly
             * connected to the srcNetVirt
             */
            return vMac;
        }

        /* Find the NetVirt connected to the router interface configured with the
         * 'dstIp'
         */
        VRouterInterface iface = ifaceIpMap.get(Integer.valueOf(dstIp));
        if (iface == null) {
            /* The dstIp does not belong to any router interface */
            return 0;
        }
        String dstNetVirtName = iface.getNetVirt();
        VNS dstNetVirt = netVirtManager.getVNS(dstNetVirtName);
        ForwardingAction fwdAct = findRoute(srcNetVirt, srcIp, dstNetVirt, dstIp);
        if (fwdAct.getAction() != RoutingAction.DROP) {
            /* Check if the reverse path is allowed */
            ForwardingAction revAct = findRoute(dstNetVirt, dstIp, srcNetVirt, srcIp);
            if (revAct.getAction() != RoutingAction.DROP) {
                /* Return the MAC address of the router the source NetVirt is
                 * connected to
                 */
                return iface.getOwner().getVMac(dstNetVirtName, dstIp);
            }
        }
        /* Not allowed to communicate to the dest IP */
        return 0;
    }

    /**
     * Returns a virtual router IP.
     * If the dstIp is a virtual router IP reachable from the srcNetVirt and srcIp,
     * this function returns that IP
     * Otherwise returns the virtual router IP of the interface connected to the
     * source NetVirt.
     * @param srcNetVirt The source NetVirt
     * @param srcIp The source IP
     * @param dstIp The dest IP
     * @return A virtual router IP
     *         0 If the interface connected to the source NetVirt does not have an
     *         IP or if the dstIp is a router IP that is not reachable from the
     *         source
     */
    public int getRtrIp(VNS srcNetVirt, int srcIp, int dstIp) {
        String srcNetVirtName = srcNetVirt.getName();
        IVRouter vRouter = netVirtToRouterMap.get(srcNetVirtName);
        if (vRouter == null) {
            /* The source NetVirt is not connected to a virtual router */
            return 0;
        }

        long vMac = vRouter.getVMac(srcNetVirtName, dstIp);
        if (vMac != 0) {
            /* The dstIp is configured on an interface that is directly
             * connected to the srcNetVirt
             */
            return dstIp;
        }

        VRouterInterface iface = ifaceIpMap.get(Integer.valueOf(dstIp));
        if (iface != null) {
            /* The dstIp belongs to a router interface */
            String dstNetVirtName = iface.getNetVirt();
            VNS dstNetVirt = netVirtManager.getVNS(dstNetVirtName);
            ForwardingAction fwdAct = findRoute(srcNetVirt, srcIp, dstNetVirt, dstIp);
            if (fwdAct.getAction() != RoutingAction.DROP) {
                /* Check if the reverse path is allowed */
                ForwardingAction revAct = findRoute(dstNetVirt, dstIp, srcNetVirt,
                                                    srcIp);
                if (revAct.getAction() != RoutingAction.DROP) {
                    return dstIp;
                }
            }
        } else {
            /* The dst IP is not a virtual router IP. Return the IP on the
             * interface connected to the source NetVirt
             */
            return vRouter.getRtrIp(srcNetVirtName, srcIp);
        }

        return 0;
    }

    /**
     * Assign ownership of an IP subnet to a router
     * @param subnet The IP subnet address
     * @param rtrName The router name
     */
    public void addSubnetOwner(IPV4Subnet subnet, String rtrName) {
        subnetTrie.put(subnet, rtrName);
    }

    /**
     * Find the router that owns the subnet to which IP belongs
     * @param ip An IP address or subnet
     * @return The name of the router which owns this subnet
     */
    public String findSubnetOwner(IPV4Subnet ip) {
        List<Entry<IPV4Subnet, String>> ownerList;
        ownerList = subnetTrie.prefixSearch(ip);
        if (ownerList != null && ownerList.size() > 0) {
            /* Return the longest prefix match */
            return ownerList.get(ownerList.size() - 1).getValue();
        } else
            return null;
    }

    public void addIfaceIpMap(int ip, VRouterInterface iface) {
        ifaceIpMap.put(Integer.valueOf(ip), iface);
    }
}
