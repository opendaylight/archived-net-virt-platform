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

/*
 * Virtual Routing implementation
 */
package org.sdnplatform.netvirt.virtualrouting.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IInfoProvider;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.util.ListenerDispatcher;
import org.sdnplatform.core.util.MutableInteger;
import org.sdnplatform.core.util.SingletonTask;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.flowcache.FCQueryObj;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.FlowCacheQueryResp;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowQueryHandler;
import org.sdnplatform.flowcache.IFlowReconcileListener;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.OFMatchReconcile;
import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;
import org.sdnplatform.forwarding.IForwardingService;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSAccessControlList;
import org.sdnplatform.netvirt.core.VNSAccessControlListEntry;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.ARPMode;
import org.sdnplatform.netvirt.core.VNS.BroadcastMode;
import org.sdnplatform.netvirt.core.VNSAccessControlList.VNSAclMatchResult;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction;
import org.sdnplatform.netvirt.virtualrouting.IARPListener;
import org.sdnplatform.netvirt.virtualrouting.IICMPListener;
import org.sdnplatform.netvirt.virtualrouting.IVirtualMacService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.VirtualMACExhaustedException;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction.DropReason;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouterManager.RoutingRuleParams;
import org.sdnplatform.packet.ARP;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.TCP;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.RoutingDecision;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.util.MACAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * VirtualRouteEngine enforces NetVirt virtual routing policies
 */
@LogMessageCategory("Network Virtualization")
public class VirtualRouting implements IModule, IOFMessageListener,
                                       IVirtualRoutingService, IStorageSourceListener,
                                       IFlowReconcileListener, IFlowQueryHandler, IInfoProvider,
                                       IHAListener, IVirtualMacService,
                                       IARPListener, IICMPListener {
    protected static final Logger logger =
        LoggerFactory.getLogger(VirtualRouting.class);


    // *********
    // Constants
    // *********

    // ACL Table names
    public static final String VNS_ACL_TABLE_NAME = "controller_vnsacl";
    public static final String VNS_ACL_ENTRY_TABLE_NAME = "controller_vnsaclentry";
    public static final String VNS_INTERFACE_ACL_TABLE_NAME = "controller_vnsinterfaceacl";
    // Table Column names
    public static final String NAME_COLUMN_NAME = "name";
    public static final String VNS_COLUMN_NAME = "vns";
    public static final String PRIORITY_COLUMN_NAME = "priority";
    public static final String DESCRIPTION_COLUMN_NAME = "description";
    public static final String ID_COLUMN_NAME = "id";
    public static final String ACTION_COLUMN_NAME = "action";
    public static final String TYPE_COLUMN_NAME = "type";
    public static final String SRC_IP_COLUMN_NAME = "src_ip";
    public static final String SRC_IP_MASK_COLUMN_NAME = "src_ip_mask";
    public static final String DST_IP_COLUMN_NAME = "dst_ip";
    public static final String DST_IP_MASK_COLUMN_NAME = "dst_ip_mask";
    public static final String SRC_TP_PORT_OP_COLUMN_NAME = "src_tp_port_op";
    public static final String SRC_TP_PORT_COLUMN_NAME = "src_tp_port";
    public static final String DST_TP_PORT_OP_COLUMN_NAME = "dst_tp_port_op";
    public static final String DST_TP_PORT_COLUMN_NAME = "dst_tp_port";
    public static final String ICMP_TYPE_COLUMN_NAME = "icmp_type";
    public static final String SRC_MAC_COLUMN_NAME = "src_mac";
    public static final String DST_MAC_COLUMN_NAME = "dst_mac";
    public static final String ETHER_TYPE_COLUMN_NAME = "ether_type";
    public static final String VLAN_COLUMN_NAME = "vlan";
    public static final String INTERFACE_COLUMN_NAME = "vns_interface_id";
    public static final String IN_OUT_COLUMN_NAME = "in_out";
    public static final String ACL_ENTRY_VNS_ACL_COLUMN_NAME = "vns_acl_id";
    public static final String ACL_ENTRY_RULE_COLUMN_NAME = "rule";
    public static final String ACL_NAME_COLUMN_NAME = "vns_acl_id";
    public static final String ACL_DIRECTION_INPUT = "acl_in";
    public static final String ACL_DIRECTION_OUTPUT = "acl_out";

    // Virtual routing table names
    public static final String TENANT_TABLE_NAME = "controller_tenant";
    public static final String VIRT_RTR_TABLE_NAME = "controller_virtualrouter";
    public static final String VIRT_RTR_IFACE_TABLE_NAME =
                                                "controller_virtualrouterinterface";
    public static final String IFACE_ADDRESS_POOL_TABLE_NAME =
                                                "controller_vrinterfaceipaddresspool";
    public static final String STATIC_ARP_TABLE_NAME = "controller_staticarp";
    public static final String VIRT_RTR_ROUTING_RULE_TABLE_NAME =
            "controller_virtualroutingrule";
    public static final String VIRT_RTR_GATEWAY_POOL_TABLE_NAME =
            "controller_virtualroutergwpool";
    public static final String GATEWAY_NODE_TABLE_NAME =
            "controller_vrgatewayipaddresspool";
    // Table column names
    public static final String TENANT_COLUMN_NAME = "tenant_id";
    public static final String VIRT_RTR_COLUMN_NAME = "vrname";
    public static final String VIRT_RTR_ID_COLUMN_NAME = "virtual_router_id";
    public static final String VIRT_RTR_IFACE_COLUMN_NAME = "vriname";
    public static final String ACTIVE_COLUMN_NAME = "active";
    public static final String VNS_CONNECTED_COLUMN_NAME = "vns_connected_id";
    public static final String RTR_CONNECTED_COLUMN_NAME = "router_connected_id";
    public static final String VIRT_RTR_IFACE_ID_COLUMN_NAME = "virtual_router_interface_id";
    public static final String SRC_HOST_COLUMN_NAME = "src_host_id";
    public static final String SRC_VNS_COLUMN_NAME = "src_vns_id";
    public static final String SRC_TENANT_COLUMN_NAME = "src_tenant_id";
    public static final String DST_HOST_COLUMN_NAME = "dst_host_id";
    public static final String DST_VNS_COLUMN_NAME = "dst_vns_id";
    public static final String DST_TENANT_COLUMN_NAME = "dst_tenant_id";
    public static final String OUTGOING_INTF_COLUMN_NAME = "outgoing_intf_id";
    public static final String NEXT_HOP_IP_COLUMN_NAME = "nh_ip";
    public static final String NEXT_HOP_GATEWAY_POOL_COLUMN_NAME = "gateway_pool_id";
    public static final String IP_ADDRESS_COLUMN_NAME = "ip_address";
    public static final String SUBNET_MASK_COLUMN_NAME = "subnet_mask";
    public static final String SUBNET_ADDRESS_COLUMN_NAME = "subnet_address";
    public static final String MAC_COLUMN_NAME = "mac";
    public static final String IP_COLUMN_NAME = "ip";
    public static final String VIRT_RTR_GATEWAY_POOL_COLUMN_NAME = "vrgwname";
    public static final String VIRT_RTR_GATEWAY_POOL_ID_COLUMN_NAME = "virtual_router_gwpool_id";

    public static final int DEFAULT_HINT = OFMatch.OFPFW_ALL &
                                              ~(OFMatch.OFPFW_DL_SRC |
                                                OFMatch.OFPFW_DL_DST |
                                                OFMatch.OFPFW_DL_VLAN |
                                                OFMatch.OFPFW_IN_PORT |
                                                OFMatch.OFPFW_DL_TYPE) ;

    /* Time period to batch virtual routing updates */
    public static final int VR_UPDATE_TASK_BATCH_DELAY_MS = 750;

    // **************
    // Module members
    // **************

    protected IDeviceService deviceManager;
    public INetVirtManagerService netVirtManager;
    protected IControllerService controllerProvider;
    protected IStorageSourceService storageSource;
    protected IFlowReconcileService flowReconcileMgr;
    protected IFlowCacheService     betterFlowCacheMgr;
    protected IForwardingService    forwarding;
    protected IThreadPoolService threadPool;
    protected ITopologyService topology;
    protected ITunnelManagerService tunnelManager;
    protected IRewriteService rewriteService;
    protected ILinkDiscoveryService linkDiscovery;
    protected IRoutingService routingService;

    // We start these ourselves
    protected ArpManager arpManager;
    protected DhcpManager dhcpManager;
    protected IcmpManager icmpManager;

    // **************
    // Configurations
    // **************

    protected ListenerDispatcher<OFType, IOFMessageListener> packetListeners;

    /**
     * The list of flow reconcile listeners that have registered to get
     * flow reconcile callbacks. Such callbacks are invoked, for example, when
     * a switch with existing flow-mods joins this controller and those flows
     * need to be reconciled with the current configuration of the controller.
     */
    protected ListenerDispatcher<OFType, IFlowReconcileListener> flowReconcileListeners;

    protected Map<String, VNSAccessControlList> acls;
    protected Map<String, List<VNSAccessControlList>> inIfToAcls;
    protected Map<String, List<VNSAccessControlList>> outIfToAcls;

    // Lock protecting ACL lists
    protected ReentrantReadWriteLock aclLock;

    // Asynchronous task for responding to ACL configuration changes
    protected SingletonTask configUpdateTask;

    // Asynchronous task for responding to Virtual routing configuration changes
    protected SingletonTask virtRtrConfigUpdateTask;

    /** The number of times flow query resp handler method was called. */
    protected int flowQueryRespHandlerCallCount;

    /** The last fc query resp. */
    protected FlowCacheQueryResp lastFCQueryResp;
    /* End of flow reconciliation related states */

    protected List<String> vnsWithAclChangedList;

    protected volatile VirtualRouterManager vRouterManager;

    // Generated Virtual MAC has BSN OUI prefix,
    // 5C:16:C7:01:00:01 - 5C:16:C7:01:FF:FF is reserved for SI vMAC
    public static final long MIN_VIRTUAL_MAC =
        Ethernet.toLong(Ethernet.toMACAddress("5C:16:C7:01:00:01"));
    public static final long MAX_VIRTUAL_MAC =
        Ethernet.toLong(Ethernet.toMACAddress("5C:16:C7:01:FF:FF"));

    // Traceroute port numbers
    public static final short TRACEROUTE_PORT_START = (short) 33434;
    public static final short TRACEROUTE_PORT_END = (short) 33534;

    //TODO: the vMAC should be grouped by vlan
    protected List<Long> reclaimedVMacs;
    protected long nextAvailableVMac;

    // ***************
    // Getters and Setters
    // ***************

    public FlowCacheQueryResp getLastFCQueryResp() {
        return lastFCQueryResp;
    }

    public IDeviceService getDeviceManager() {
        return this.deviceManager;
    }

    public void setDeviceManager(IDeviceService deviceManager) {
        this.deviceManager = deviceManager;
    }

    public INetVirtManagerService getNetVirtManager() {
        return netVirtManager;
    }

    public void setNetVirtManager(INetVirtManagerService netVirtManager) {
        this.netVirtManager = netVirtManager;
    }

    public IControllerService getControllerProvider() {
        return controllerProvider;
    }

    public void setControllerProvider(IControllerService controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    public IStorageSourceService getStorageSource() {
        return this.storageSource;
    }

    public void setStorageSource(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }

    public ArpManager getArpManager() {
        return this.arpManager;
    }

    public DhcpManager getDhcpManager() {
        return this.dhcpManager;
    }

    public IcmpManager getIcmpManager() {
        return this.icmpManager;
    }

    // ***************
    // IVirtualRoutingService
    // ***************

    /**
     * Returns the list of INPUT ACLs for a certain interface.
     * @param ifaceKey The interface key <vnsname>|<ifacename>
     * @return The list of ACLs on this interface, null if none exist
     */
    @Override
    public List<VNSAccessControlList> getInIfaceAcls(String ifaceKey) {
        return inIfToAcls.get(ifaceKey);
    }

    /**
     * Returns the list of OUTPUT ACLs for a certain interface.
     * @param ifaceKey The interface key <vnsname>|<ifacename>
     * @return The list of ACLs on this interface, null if none exist
     */
    @Override
    public List<VNSAccessControlList> getOutIfaceAcls(String ifaceKey) {
        return outIfToAcls.get(ifaceKey);
    }

    @Override
    public boolean connected(IDevice device1, int dev1Ip, IDevice device2, int dev2Ip) {
        if (device1 == null || device2 == null) {
            return false;
        }

        List<VNSInterface> vnsIfaces1 = netVirtManager.getInterfaces(device1);
        List<VNSInterface> vnsIfaces2 = netVirtManager.getInterfaces(device2);

        return vRouterManager.connected(vnsIfaces1, vnsIfaces2, dev1Ip, dev2Ip);
    }

    /**
     * Handles applications built on top of virtual routing (ARP/DHCP)
     */
    @Override
    public synchronized void addPacketListener(IOFMessageListener listener) {
        packetListeners.addListener(OFType.PACKET_IN, listener);

        if (logger.isDebugEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("VirtualRouting PacketIn Listeners: ");
            for (IOFMessageListener l : packetListeners.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            logger.debug(sb.toString());
        }
    }

    @Override
    public synchronized void removePacketListener(IOFMessageListener listener) {
        packetListeners.removeListener(listener);
    }

    @Override
    public synchronized void addFlowReconcileListener(IFlowReconcileListener listener) {
        flowReconcileListeners.addListener(OFType.FLOW_MOD, listener);

        if (logger.isTraceEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("FlowMod listeners: ");
            for (IFlowReconcileListener l : flowReconcileListeners.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            logger.trace(sb.toString());
        }
    }

    @Override
    public synchronized void removeFlowReconcileListener(IFlowReconcileListener listener) {
        flowReconcileListeners.removeListener(listener);
    }

    @Override
    public synchronized void clearFlowReconcileListeners() {
        flowReconcileListeners.clearListeners();
    }

    @Override
    public void addARPListener(IARPListener al) {
        arpManager.addArpListener(al);
    }

    // ***************
    // Internal Methods - Packet Processing and ACL Related
    // ***************

    /**
     * processBroadcastPacket processes all bcast packets that the not specially
     * handled by the 'packetListeners' - this is where we wormhole all bcast
     * packets through the controller to all known devices in the VNS (if the
     * bcast mode has been set to 'forward-to-known', which it is by default)
     */
    private Command processBroadcastPacket(long swDpid, short inPort, Ethernet eth,
                                           ListenerContext cntx) {
        // Do not wildcard SRC and DST MAC and VLAN
        // Do not wildcard EtherType ==> for IP spoofing protection
        int defaultHint = OFMatch.OFPFW_ALL & ~(OFMatch.OFPFW_DL_SRC |
                                                OFMatch.OFPFW_DL_DST |
                                                OFMatch.OFPFW_DL_VLAN |
                                                OFMatch.OFPFW_DL_TYPE);
        MutableInteger hint = new MutableInteger(defaultHint);

        // Check VNS policy, annotate based on the policy for the src interface
        RoutingAction action = getBroadcastAction(eth, hint, cntx);
        if (action == RoutingAction.DROP)
            return Command.STOP;    // no DROP flow-mod for broadcast packets

        IDevice srcDev =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);

        RoutingDecision d =
            new RoutingDecision(swDpid, inPort, srcDev, action);
        d.setWildcards(hint.intValue());
        d.addToContext(cntx);

        // Add destination devices if action is multicast (within the VNS)
        List<VNSInterface> srcIfaces =
                INetVirtManagerService.bcStore.get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        if (action == RoutingAction.MULTICAST) {
            for (VNSInterface iface : srcIfaces) {
                VNS vns = iface.getParentVNS();
                // We trimmed src interfaces disallowing broadcast in getBroadcastAction().
                // If we were to support broadcast ACL against output interfaces, this would
                // be the place to do it.
                for (Long deviceKey : vns.getKnownDevices()) {
                    IDevice destination = deviceManager.getDevice(deviceKey);
                    if (destination != null)
                        d.addDestinationDevice(destination);
                }
            }
            d.setMulticastInterfaces(netVirtManager.getBroadcastSwitchPorts());
        }
        return Command.CONTINUE;
    }

    /**
     * Check each src interface. If both the broadcast mode and ACL allows,
     * let the traffic through.
     * Remove src interfaces that disallow broadcast.
     */
    private RoutingAction getBroadcastAction(Ethernet eth, MutableInteger hint,
                                             ListenerContext cntx) {
        RoutingAction action;
        BroadcastMode config = BroadcastMode.DROP;
        List<VNSInterface> srcIfaces = INetVirtManagerService.bcStore.get(cntx,
                                           INetVirtManagerService.CONTEXT_SRC_IFACES);
        List<VNSInterface> newSrcIfaces = Collections.synchronizedList(new ArrayList<VNSInterface>());

        if (srcIfaces != null) {
            for (VNSInterface iface : srcIfaces) {
                // First check against the mode
                BroadcastMode bc = iface.getParentVNS().getBroadcastMode();
                if (bc == BroadcastMode.DROP)
                    continue;

                // Next, check against input ACL
                if (applyAcl(eth, cntx, hint, iface, null) == VNSAclMatchResult.ACL_DENY)
                    continue;

                // Broadcast is allowed on this source interface
                newSrcIfaces.add(iface);
                if (bc.compareTo(config) > 0) {
                    config = bc;
                }
            }
        }

        INetVirtManagerService.bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, newSrcIfaces);

        switch (config) {
            case ALWAYS_FLOOD:
                action = RoutingAction.FORWARD_OR_FLOOD;
                break;
            case FORWARD_TO_KNOWN:
                action = RoutingAction.MULTICAST;
                break;
            case DROP:
            default: // drop by default
                action = RoutingAction.DROP;
        }
        return action;
    }

    private Command processMulticastPacket(long swDpid, short inPort, Ethernet eth,
                                           ListenerContext cntx) {
        // Follow broadcast setting for multicast packets.
        // A real implementation should do IGMP snooping and keep track of multicast groups.
        return processBroadcastPacket(swDpid, inPort, eth, cntx);
    }

    private Command processUnicastPacket(long swDpid, short inPort, Ethernet eth,
                                         ListenerContext cntx,
                                         ForwardingAction fAction) {
        // Deny by default
        RoutingAction action = RoutingAction.DROP;

        // By default, wildcard everything except src/dst mac, vlan and input port.
        int defaultHint = DEFAULT_HINT;
        MutableInteger hint = new MutableInteger(defaultHint);

        if (fAction.isVirtualRouted()) {
            /* In case the packet is virtual routed, we need to NOT wildcard
             * both the source and dest IP. This is because the source and dest
             * IPs are used to determine flow permit/deny policy and we will
             * require this information for flow reconciliation.
             * XXX There is a better way to do this without affecting actual
             * flows on the switch...redesign flow cache
             */
            hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_DST_MASK)));
            hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_SRC_MASK)));
        }

        // Forward packet if there is a matching VNS and ACL permits
        if (fAction.getAction() == RoutingAction.FORWARD) {
            VNSInterface sIface = INetVirtManagerService.bcStore.get(
                                      cntx, INetVirtManagerService.CONTEXT_SRC_IFACES).get(0);
            List<VNSInterface> dstIfaces = INetVirtManagerService.bcStore.get(
                                               cntx, INetVirtManagerService.CONTEXT_DST_IFACES);
            VNSInterface dIface;
            if (dstIfaces != null)
                dIface = dstIfaces.get(0);
            else
                dIface = null;
            if (applyAcl(eth, cntx, hint, sIface, dIface) == VNSAclMatchResult.ACL_PERMIT)
                action = RoutingAction.FORWARD;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("defaultHint {}, actual hint 0x{}", Integer.toHexString(defaultHint),
                         Integer.toHexString(hint.intValue()));
        }
        // Annotate with action and src/dst physical ports
        IDevice srcDev =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
        IDevice dstDev =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        if (srcDev == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("No source device. Dropping packet {} -> {} ethType 0x{}"
                             + " vlan {} from switch {} port {}",
                        new Object[] { HexString.toHexString(eth.getSourceMACAddress()),
                                       HexString.toHexString(eth.getDestinationMACAddress()),
                                       Integer.toHexString(eth.getEtherType()),
                                       eth.getVlanID(),
                                       HexString.toHexString(swDpid),
                                       inPort}
                );
            }
            // Update context if this is an explain packet
            if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                NetVirtExplainPacket.explainPacketSetContext(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ACTION,
                        "Source MAC is not part of any VNS - Explain packet is dropped");
            }
            return Command.STOP;
        }

        /*
         * Handling unknown destinations:
         * ------------------------------
         *
         * Case I: Without Virtual Router Intervention:
         * --------------------------------------------
         * If there is no destination device, then forward, so that Forwarding
         * will inject ARPs to discover the device
         *
         * Case II: With Virtual Router Intervention:
         * ------------------------------------------
         * We want to *FORWARD* if either:
         *     o VR permits the packet
         *     o VR drops the packet because the next hop is unknown
         *
         * We want to *DROP* if:
         *     o VR drops the packet to a known next hop
         */
        if (dstDev == null &&
            (!fAction.isVirtualRouted() ||
             fAction.getDropReason() == DropReason.NEXT_HOP_UNKNOWN)) {
            // LOOK! This is slightly ambiguous, but we are telling Forwarding
            // to ARP for the dstDevice. We may want to create a new action
            // in the future.
            action = RoutingAction.FORWARD;
        }

        RoutingDecision d = new RoutingDecision(swDpid, inPort, srcDev, action);
        d.setWildcards(hint.intValue());
        if (dstDev != null)
            d.addDestinationDevice(dstDev);
        if (eth.getPayload() instanceof ARP) {
            /* VNS-254 ARP flows need to have a hard timeout. */
            d.setHardTimeout(ArpManager.ARP_FLOWMOD_HARD_TIMEOUT);
        }
        d.addToContext(cntx);
        if (logger.isTraceEnabled())
            logger.trace("Wildcard SET 0x{}", Integer.toHexString(d.getWildcards()));
        return Command.CONTINUE;
    }

    /**
     * Find the highest priority ACL along the interface chain
     * @param map
     * @param iface
     * @return ACL to apply or null
     */
    private VNSAccessControlList interfaceToAcl(Map<String, List<VNSAccessControlList>> map,
                                                VNSInterface iface) {
        VNSAccessControlList acl = null;
        do {
            List<VNSAccessControlList> acls = map.get(iface.getParentVNS().getName() + "|" + iface.getName());
            VNSAccessControlList newAcl;
            if (acls != null) {
                newAcl = acls.get(0);
                if (acl == null || acl.getPriority() < newAcl.getPriority())
                    acl = newAcl;
            }
        } while ((iface = iface.getParentVNSInterface()) != null);

        return acl;
    }

    /**
     * Apply ACL to patch, first on input, then on output. dstIface can be null for broadcast traffic
     * @param eth
     * @param cntx
     * @param wildcards
     * @param srcIface
     * @param dstIface
     * @return ACL_PERMIT or ACL_DENY
     */
    protected VNSAclMatchResult applyAcl(Ethernet eth, ListenerContext cntx, MutableInteger wildcards,
                                         VNSInterface srcIface, VNSInterface dstIface) {
        /* A VNS must be chosen at this point, so we have a single src and dst
         * interface. With virtual routing it is possible that the dst interface
         * is unknown and null
         */
        VNSAclMatchResult ret = VNSAclMatchResult.ACL_PERMIT;

        aclLock.readLock().lock();
        try {
            VNSAccessControlList acl = interfaceToAcl(inIfToAcls, srcIface);
            if (acl != null) {
                ret = acl.applyAcl(eth, wildcards, cntx, ACL_DIRECTION_INPUT);
                // The split() below removed the VNS name from the acl name string
                if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                    NetVirtExplainPacket.
                        explainPacketSetContext(cntx,
                                                NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_NAME,
                                                acl.getName().split("\\|", 2)[1]);
                    NetVirtExplainPacket.
                        explainPacketSetContext(cntx,
                                                NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_RESULT,
                                                ret.toString());
                }

                logger.trace("Apply acl {} to {} on input: {}", new Object[] {acl, eth, ret});
            }

            if (ret != VNSAclMatchResult.ACL_DENY && dstIface != null) {
                // Input is fine, look at output
                acl = interfaceToAcl(outIfToAcls, dstIface);
                if (acl != null) {
                    ret = acl.applyAcl(eth, wildcards, cntx, ACL_DIRECTION_OUTPUT);
                    if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                        NetVirtExplainPacket.
                            explainPacketSetContext(cntx,
                                                    NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_NAME,
                                                    acl.getName().split("\\|", 2)[1]);
                        NetVirtExplainPacket.
                            explainPacketSetContext(cntx,
                                                    NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_RESULT,
                                                    ret.toString());
                    }
                    logger.trace("Apply acl {} to {} on output: {}", new Object[] {acl, eth, ret});
                }
            }
        } finally {
            aclLock.readLock().unlock();
        }
        return ret;
    }

    @LogMessageDoc(level="WARN",
            message="Unexpected ARP flow in flow cache",
            explanation="ARP Flows should not be stored in the flow cache",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private Ethernet convertOfmToEthernet(OFMatch ofm) {
        Ethernet eth = new Ethernet();

        int wildcards = ofm.getWildcards();

        if ((wildcards & OFMatch.OFPFW_DL_DST) == 0) {
            eth.setDestinationMACAddress(ofm.getDataLayerDestination());
        }

        if ((wildcards & OFMatch.OFPFW_DL_SRC) == 0) {
            eth.setSourceMACAddress(ofm.getDataLayerSource());
        }

        if ((wildcards & OFMatch.OFPFW_DL_VLAN) == 0) {
            eth.setVlanID(ofm.getDataLayerVirtualLan());
        }

        if ((wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == 0) {
            eth.setPriorityCode(ofm.getDataLayerVirtualLanPriorityCodePoint());
        }

        if ((wildcards & OFMatch.OFPFW_DL_TYPE) == 0) {
            eth.setEtherType(ofm.getDataLayerType());
            switch (eth.getEtherType()) {
                case Ethernet.TYPE_IPv4:

                    IPv4 ipv4 = new IPv4();
                    eth.setPayload(ipv4);

                    /* Set the Network layer addresses */
                    if ((wildcards & OFMatch.OFPFW_NW_DST_MASK) !=
                                                OFMatch.OFPFW_NW_DST_MASK) {
                        ipv4.setDestinationAddress(ofm.getNetworkDestination());
                    }
                    if ((wildcards & OFMatch.OFPFW_NW_SRC_MASK) !=
                            OFMatch.OFPFW_NW_SRC_MASK) {
                        ipv4.setSourceAddress(ofm.getNetworkSource());
                    }

                    if ((wildcards & OFMatch.OFPFW_NW_PROTO) == 0) {
                        ipv4.setProtocol(ofm.getNetworkProtocol());

                        switch (ipv4.getProtocol()) {
                            case IPv4.PROTOCOL_TCP:
                                TCP tcp = new TCP();
                                ipv4.setPayload(tcp);
                                if ((wildcards & OFMatch.OFPFW_TP_DST) == 0) {
                                    tcp.setDestinationPort(
                                                ofm.getTransportDestination());
                                }
                                if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0) {
                                    tcp.setSourcePort(ofm.getTransportSource());
                                }
                                break;

                            case IPv4.PROTOCOL_UDP:
                                UDP udp = new UDP();
                                ipv4.setPayload(udp);
                                if ((wildcards & OFMatch.OFPFW_TP_DST) == 0) {
                                    udp.setDestinationPort(
                                                ofm.getTransportDestination());
                                }
                                if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0) {
                                    udp.setSourcePort(ofm.getTransportSource());
                                }
                                break;

                            case IPv4.PROTOCOL_ICMP:
                                ICMP icmp = new ICMP();
                                ipv4.setPayload(icmp);
                                break;

                            default:

                                break;
                        }
                    }
                    break;

                case Ethernet.TYPE_ARP:
                    /* Arp flows are not stored in flow cache */
                    logger.warn("Unexpected ARP flow in flow cache");
                    break;

                default:
                    /* No op. */
                    break;

            }
        }
        return eth;
    }

    private VNSAclMatchResult applyAclToReconciledFlow (
                                  ListenerContext cntx, OFMatch ofm,
                                  VNSInterface srcIface,
                                  VNSInterface dstIface,
                                  MutableInteger wildcards) {

        Ethernet eth = convertOfmToEthernet(ofm);
        return applyAcl(eth, cntx, wildcards, srcIface, dstIface);
    }

    // ***************
    // IOFMessageListener
    // ***************

    @Override
    @LogMessageDoc(level="ERROR",
    message="No src VNS for unicast packet from switch " +
            "{switch} port {port}",
    explanation="Could not determine policy to apply to " +
            "the unicast flow because there was no source VNS" +
            "found",
    recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public Command receive(IOFSwitch sw, OFMessage msg, ListenerContext cntx) {
        if (msg.getType() != OFType.PACKET_IN) {
            return Command.CONTINUE;
        }
        OFPacketIn pi = (OFPacketIn) msg;
        Ethernet eth = IControllerService.bcStore.get(cntx,
                IControllerService.CONTEXT_PI_PAYLOAD);

        List<VNSInterface> srcIfaces =
            INetVirtManagerService.bcStore.get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        List<VNSInterface> dstIfaces =
            INetVirtManagerService.bcStore.get(cntx, INetVirtManagerService.CONTEXT_DST_IFACES);

        // Set default APPName and may be overwritten if netVirt is found.
        if (srcIfaces != null) {
            IFlowCacheService.fcStore.put(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME,
                                          srcIfaces.get(0).getParentVNS().getName());
        }

        ForwardingAction fAction = null;
        if (!eth.isBroadcast() && !eth.isMulticast()) {
            int srcIp = 0, dstIp = 0, ttl = 256;
            if (eth.getPayload() instanceof IPv4) {
                IPv4 ipv4 = (IPv4) eth.getPayload();
                srcIp = ipv4.getSourceAddress();
                dstIp = ipv4.getDestinationAddress();
                ttl = (ipv4.getTtl() & 0xFF);
            } else if (eth.getPayload() instanceof ARP) {
                ARP arp = (ARP) eth.getPayload();
                if (arp.getProtocolType() == ARP.PROTO_TYPE_IP) {
                    srcIp = IPv4.toIPv4Address(arp.getSenderProtocolAddress());
                    dstIp = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
                }
            }

            if (srcIfaces == null) {
                /* It's not an error if no dstIface. It happens if dst host is
                 * in the ARP cache of the src host, but not yet seen by the
                 *  devicemanager.
                 */
                logger.error("No src VNS for unicast packet from switch {} port {}",
                            HexString.toHexString(sw.getId()), pi.getInPort());
            }

            long srcMac = Ethernet.toLong(eth.getSourceMACAddress());
            long dstMac = Ethernet.toLong(eth.getDestinationMACAddress());

            if (logger.isTraceEnabled()) {
                logger.trace("Getting Forwarding Action: {}::{} -> {}::{}, " +
                             "vlan={}, ethType={} from virtualRouterMgr for unicast pkt",
                             new Object[] { HexString.toHexString(srcMac),
                                            IPv4.fromIPv4Address(srcIp),
                                            HexString.toHexString(dstMac),
                                            IPv4.fromIPv4Address(dstIp),
                                            eth.getVlanID(),
                                            eth.getEtherType()});
            }
            fAction = vRouterManager.getForwardingAction(srcMac, dstMac,
                                                         eth.getVlanID(),
                                                         eth.getEtherType(),
                                                         srcIp, dstIp, cntx);
            if (logger.isTraceEnabled()) {
                logger.trace("getForwardingAction returned {}",
                             fAction.toString());
            }

            /* If packet is destined to a virtual router MAC, we try to handle
             * it if it is a traceroute packet.
             */
            if (fAction.isDestinedToVirtualRouterMac()) {
                boolean processed = false;
                if (isTraceroutePacket(cntx))
                    processed = this.handleTraceroute(sw, pi, cntx);

                /* If TTL < 2, it will be <= 0 after TTL decrement. We should
                 * drop it and stop processing (don't install DROP flow mods).
                 *
                 * If the packet has already been handled by the traceroute
                 * handler at this point, it needs no further packet processing.
                 */
                if (processed || ttl < 2)
                    return Command.STOP;
            }

            if (fAction.getAction() != RoutingAction.FORWARD &&
                    (srcIfaces != null && dstIfaces != null)) {
                // if the packet is not going to be forwarded we avoid calling the
                // packetListeners by short-cutting to processUnicast
                return this.processUnicastPacket(sw.getId(), pi.getInPort(), eth, cntx, fAction);
            }
        } else {
            fAction = new ForwardingAction();
        }

        if (packetListeners.getOrderedListeners() != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Calling all packetListeners for pkt from eth src: {}",
                             HexString.toHexString(eth.getSourceMACAddress()));
            }
            for (IOFMessageListener listener : packetListeners.getOrderedListeners()) {
                if (Command.STOP.equals(listener.receive(sw, msg, cntx)))
                    break;
            }
        }

        IRoutingDecision decision =
            IRoutingDecision.rtStore.get(cntx,
                                         IRoutingDecision.CONTEXT_DECISION);

        if (null == decision) {
            if (eth.isBroadcast()) {
                return this.processBroadcastPacket(sw.getId(), pi.getInPort(), eth, cntx);
            }
            else if (eth.isMulticast()) {
                return this.processMulticastPacket(sw.getId(), pi.getInPort(), eth, cntx);
            }
            else {
                return this.processUnicastPacket(sw.getId(), pi.getInPort(), eth, cntx, fAction);
            }
        }
        return Command.CONTINUE;
    }

    @Override
    public String getName() {
        return "virtualrouting";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // Follows netVirtmanager
        return ((type == OFType.PACKET_IN || type == OFType.FLOW_MOD)
                && name.equals("netVirtmanager"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    // ***************
    // Internal Methods - Storage Related
    // ***************

    /**
     * Clears cached ACL state
     */
    protected void clearCachedAclState() {
        acls.clear();
        inIfToAcls.clear();
        outIfToAcls.clear();
    }

    protected void clearCachedState() {
        flowQueryRespHandlerCallCount = 0;
        lastFCQueryResp = null;
    }

    /**
     * Need to optimize if we support a large number of ACL entries.
     */
    private void queueConfigUpdate() {
        configUpdateTask.reschedule(5, TimeUnit.SECONDS);
    }

    /**
     * Read the entire ACL table from storage, including the interface
     * to ACL associations.
     * To scale to a large number of entries, we may need to do selective
     * invalidation and reading.
     */
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                       message="Failed to parse ACL entry {ID}, entry " +
                               "ignored {exception}",
                       explanation="The ACL entry was improperly formatted " +
                               "and could not be read",
                       recommendation="If you created this using the API " +
                               "or a third-party orchestration component, you " +
                               "should report this as a bug in that application. " +
                               "Otherwise, report this as a bug in " +
                               "the controller."),
        @LogMessageDoc(level="ERROR",
                       message="Invalid direction {direction} in " +
                               "{interface name}->{ID} association, ignored",
                       explanation="The ACL entry was improperly formatted " +
                                 "and could not be read",
                       recommendation="If you created this using the API " +
                                 "or a third-party orchestration component, you " +
                                 "should report this as a bug in that application. " +
                                 "Otherwise, report this as a bug in " +
                                 "the controller."),

    })
    protected void readAclTablesFromStorage() {
        IResultSet aclResultSet = storageSource.executeQuery(VNS_ACL_TABLE_NAME,
                null, null, null);
        IResultSet aclEntryResultSet = storageSource.executeQuery(VNS_ACL_ENTRY_TABLE_NAME,
                null, null, null);
        IResultSet ifAclResultSet = storageSource.executeQuery(VNS_INTERFACE_ACL_TABLE_NAME,
                null, null, null);

        logger.trace("Reading ACL tables from storage");

        aclLock.writeLock().lock();

        try {
            // clear cached acls.
            clearCachedAclState();

            while (aclResultSet.next()) {
                String aclName = aclResultSet.getString(ID_COLUMN_NAME);
                VNSAccessControlList acl = new VNSAccessControlList(aclName);
                acl.setPriority(aclResultSet.getInt(PRIORITY_COLUMN_NAME));
                acls.put(aclName, acl);
                logger.debug("Added ACL {}", acl);
            }

            while (aclEntryResultSet.next()) {
                String aclName = aclEntryResultSet.getString(ACL_ENTRY_VNS_ACL_COLUMN_NAME);
                VNSAccessControlList acl = acls.get(aclName);
                if (acl == null) {
                    String aclEntryId = aclEntryResultSet.getString(ID_COLUMN_NAME);
                    logger.error("ACL entry {} has no parent ACL {}",
                                 aclEntryId, aclName);
                    continue;
                }

                try {
                    // Create and the ACL entry and add to acl
                    String seqNoStr = aclEntryResultSet.getString(ACL_ENTRY_RULE_COLUMN_NAME);
                    int seqNo = Integer.parseInt(seqNoStr);
                    VNSAccessControlListEntry entry = new VNSAccessControlListEntry(seqNo, acl);
                    String aclType = aclEntryResultSet.getString(TYPE_COLUMN_NAME);
                    entry.setType(aclType);
                    entry.setAction(aclEntryResultSet.getString(ACTION_COLUMN_NAME));

                    if ("mac".equals(aclType)) {
                        entry.setSrcMac(aclEntryResultSet.getString(SRC_MAC_COLUMN_NAME));
                        entry.setDstMac(aclEntryResultSet.getString(DST_MAC_COLUMN_NAME));
                        if (aclEntryResultSet.containsColumn(ETHER_TYPE_COLUMN_NAME)) {
                            entry.setEtherType(aclEntryResultSet.getInt(ETHER_TYPE_COLUMN_NAME));
                        } else {
                            entry.setEtherType(VNSAccessControlListEntry.ETHERTYPE_ALL);
                        }
                        if (aclEntryResultSet.containsColumn(VLAN_COLUMN_NAME)) {
                            entry.setVlan(aclEntryResultSet.getInt(VLAN_COLUMN_NAME));
                        } else {
                            entry.setVlan(VNSAccessControlListEntry.VLAN_ALL);
                        }
                    } else {
                        // common fields for ip/ipproto/icmp/udp/tcp
                        entry.setSrcIp(aclEntryResultSet.getString(SRC_IP_COLUMN_NAME));
                        entry.setSrcIpMask(aclEntryResultSet.getString(SRC_IP_MASK_COLUMN_NAME));
                        entry.setDstIp(aclEntryResultSet.getString(DST_IP_COLUMN_NAME));
                        entry.setDstIpMask(aclEntryResultSet.getString(DST_IP_MASK_COLUMN_NAME));
                        // type-specific fields
                        if ("icmp".equals(aclType)) {
                            if (aclEntryResultSet.containsColumn(ICMP_TYPE_COLUMN_NAME)) {
                                entry.setIcmpType(aclEntryResultSet.getInt(ICMP_TYPE_COLUMN_NAME));
                            } else {
                                entry.setIcmpType(VNSAccessControlListEntry.ICMPTYPE_ALL);
                            }
                        } else if ("tcp".equals(aclType) || "udp".equals(aclType)) {
                            String op = aclEntryResultSet.getString(SRC_TP_PORT_OP_COLUMN_NAME);
                            entry.setSrcTpPortOp(op);
                            if (op != null && !"any".equals(op)) {
                                // Call getInt() only if we expect it to exist
                                entry.setSrcTpPort(aclEntryResultSet.getInt(SRC_TP_PORT_COLUMN_NAME));
                            }
                            op = aclEntryResultSet.getString(DST_TP_PORT_OP_COLUMN_NAME);
                            entry.setDstTpPortOp(op);
                            if (op != null && !"any".equals(op)) {
                                // Call getInt() only if we expect it to exist
                                entry.setDstTpPort(aclEntryResultSet.getInt(DST_TP_PORT_COLUMN_NAME));
                            }
                        }
                    }
                    acl.addAclEntry(entry);
                    logger.debug("Added ACL entry {}", entry);
                } catch (Exception e) {
                    String aclEntryId = aclEntryResultSet.getString(ID_COLUMN_NAME);
                    logger.error("Failed to parse ACL entry {}, entry ignored", aclEntryId, e);
                }
            }

            while (ifAclResultSet.next()) {
                String ifName = ifAclResultSet.getString(INTERFACE_COLUMN_NAME);
                String aclName = ifAclResultSet.getString(ACL_NAME_COLUMN_NAME);
                String inOut = ifAclResultSet.getString(IN_OUT_COLUMN_NAME);
                VNSAccessControlList acl = acls.get(aclName);
                if (acl == null) {
                    logger.error("Invalid ACL in {}->{}, entry ignored",
                                 ifName, aclName);
                    continue;
                }

                List<VNSAccessControlList> aclList;
                Map<String, List<VNSAccessControlList>> ifAclMap;
                if ("in".equals(inOut)) {
                    ifAclMap = inIfToAcls;
                } else if ("out".equals(inOut)) {
                    ifAclMap = outIfToAcls;
                } else {
                    logger.error("Invalid direction {} in {}->{} association, ignored",
                                 new Object[] {inOut, ifName, aclName});
                    continue;
                }
                aclList = ifAclMap.get(ifName);
                if (aclList == null) {
                    aclList = Collections.synchronizedList(new ArrayList<VNSAccessControlList>(1));
                    ifAclMap.put(ifName, aclList);
                }

                // add ACL in sorted order
                int i;
                for (i = 0; i < aclList.size(); i++) {
                    if (acl.compareTo(aclList.get(i)) >= 0)
                        break;
                }
                aclList.add(i, acl);
                logger.debug("Attach ACL {} to interface {}, {}",
                             new Object[] {aclName, ifName, inOut});
            }
        } finally {
            aclLock.writeLock().unlock();
        }

        submitQueryforAclConfigChange();

    }

    protected void readVirtRtrTablesFromStorage() {
        VirtualRouterManager vRtrManager = new VirtualRouterManager();
        vRtrManager.setDeviceManager(deviceManager);
        vRtrManager.setRewriteService(rewriteService);
        vRtrManager.setvMacManager(this);
        vRtrManager.setNetVirtManager(netVirtManager);
        vRtrManager.setLinkDiscovery(linkDiscovery);
        vRtrManager.setTopology(topology);
        vRtrManager.setRoutingService(routingService);
        vRtrManager.setTunnelManager(tunnelManager);

        IResultSet tenantSet = storageSource.executeQuery(TENANT_TABLE_NAME,
                                                          null, null, null);
        /* Create all tenants */
        while (tenantSet.next()) {
            String name = tenantSet.getString(NAME_COLUMN_NAME);
            boolean active = tenantSet.getBoolean(ACTIVE_COLUMN_NAME);
            vRtrManager.createTenant(name, active);
        }

        IResultSet virtRtrSet = storageSource.executeQuery(VIRT_RTR_TABLE_NAME,
                                                           null, null, null);

        /* Create all virtual routers */
        while (virtRtrSet.next()) {
            String rtrName = virtRtrSet.getString(VIRT_RTR_COLUMN_NAME);
            String tenant = virtRtrSet.getString(TENANT_COLUMN_NAME);
            try {
                vRtrManager.createVirtualRouter(rtrName, tenant);
            } catch (VirtualMACExhaustedException e) {
                logger.error("Virtual MAC exhaustion while creating virtual " +
                             "router {}, entry ignored", rtrName, e);
            } catch (IllegalArgumentException e) {
                logger.error("Error while creating virtual router {}, entry " +
                             "ignored", rtrName, e);
            }
        }

        IResultSet vRtrIfaceSet =
                storageSource.executeQuery(VIRT_RTR_IFACE_TABLE_NAME, null,
                                           null, null);
        /* Create all interfaces */
        while (vRtrIfaceSet.next()) {
            /* The owner string is of the format: <tenant_name>|<router_name> */
            String owner = vRtrIfaceSet.getString(VIRT_RTR_ID_COLUMN_NAME);
            String ifaceName = vRtrIfaceSet.getString(VIRT_RTR_IFACE_COLUMN_NAME);
            boolean active = vRtrIfaceSet.getBooleanObject(ACTIVE_COLUMN_NAME);
            /* the vns string is of the format: <tenant_name>|<vns_name> */
            String vnsName = vRtrIfaceSet.getString(VNS_CONNECTED_COLUMN_NAME);
            /* the router string is of the format: <tenant_name>|<router_name>*/
            String rtrName = vRtrIfaceSet.getString(RTR_CONNECTED_COLUMN_NAME);
            try {
                vRtrManager.addVRouterIface(owner, ifaceName, vnsName, rtrName,
                                            active);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse virtual interface {} {}, entry " +
                             "ignored", new Object[]{owner, ifaceName}, e);
            }
        }

        /* Create all gateway pools */
        IResultSet gatewayPoolSet =
                storageSource.executeQuery(VIRT_RTR_GATEWAY_POOL_TABLE_NAME,
                                           null, null, null);
        while (gatewayPoolSet.next()) {
            String gatewayPoolName =
                gatewayPoolSet.getString(VIRT_RTR_GATEWAY_POOL_COLUMN_NAME);
            /* the owner string is of the format: <tenant_name>|<router_name>*/
            String owner = gatewayPoolSet.getString(VIRT_RTR_ID_COLUMN_NAME);
            try {
                vRtrManager.addGatewayPool(owner, gatewayPoolName);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse gateway pool {} {}, entry " +
                             "ignored", new Object[]{owner, gatewayPoolName}, e);
            }
        }

        /* Create and Associate the gateway nodes with the right gateway pools */
        IResultSet gatewayNodeSet =
                storageSource.executeQuery(GATEWAY_NODE_TABLE_NAME,
                                           null, null, null);
        while (gatewayNodeSet.next()) {
            String ipAddr =
                gatewayNodeSet.getString(IP_ADDRESS_COLUMN_NAME);
            String gatewayNodeId =
                gatewayNodeSet.getString(VIRT_RTR_GATEWAY_POOL_ID_COLUMN_NAME);
            try {
                vRtrManager.addGatewayPoolNode(gatewayNodeId, ipAddr);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse gateway node ip {} for owner " +
                             "{}, entry ignored",
                             new Object[]{ipAddr, gatewayNodeId}, e);
            }
        }

        IResultSet ifaceAddrPoolSet =
                storageSource.executeQuery(IFACE_ADDRESS_POOL_TABLE_NAME,
                                           null, null, null);
        /* Assign IP addresses to interfaces */
        while (ifaceAddrPoolSet.next()) {
            String owner =
                    ifaceAddrPoolSet.getString(VIRT_RTR_IFACE_ID_COLUMN_NAME);
            String ipAddr = ifaceAddrPoolSet.getString(IP_ADDRESS_COLUMN_NAME);
            String subnetMask =
                    ifaceAddrPoolSet.getString(SUBNET_MASK_COLUMN_NAME);
            try {
                vRtrManager.addIfaceIp(owner, ipAddr, subnetMask);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse interface address {} for owner " +
                             "{}, entry ignored", new Object[]{ipAddr, owner},
                             e);
            }
        }

        IResultSet vRtrRuleSet =
                storageSource.executeQuery(VIRT_RTR_ROUTING_RULE_TABLE_NAME,
                                           null, null, null);
        /* Populate routers with routing table entry rules */
        while (vRtrRuleSet.next()) {
            RoutingRuleParams p = new RoutingRuleParams();
            p.owner = vRtrRuleSet.getString(VIRT_RTR_ID_COLUMN_NAME);
            p.srcVNS = vRtrRuleSet.getString(SRC_VNS_COLUMN_NAME);
            p.srcTenant = vRtrRuleSet.getString(SRC_TENANT_COLUMN_NAME);
            p.srcIp = vRtrRuleSet.getString(SRC_IP_COLUMN_NAME);
            p.srcMask = vRtrRuleSet.getString(SRC_IP_MASK_COLUMN_NAME);
            p.dstVNS = vRtrRuleSet.getString(DST_VNS_COLUMN_NAME);
            p.dstTenant = vRtrRuleSet.getString(DST_TENANT_COLUMN_NAME);
            p.dstIp = vRtrRuleSet.getString(DST_IP_COLUMN_NAME);
            p.dstMask = vRtrRuleSet.getString(DST_IP_MASK_COLUMN_NAME);
            p.outIface = vRtrRuleSet.getString(OUTGOING_INTF_COLUMN_NAME);
            p.nextHopIp = vRtrRuleSet.getString(NEXT_HOP_IP_COLUMN_NAME);
            p.action = vRtrRuleSet.getString(ACTION_COLUMN_NAME);
            p.nextHopGatewayPool =
                vRtrRuleSet.getString(NEXT_HOP_GATEWAY_POOL_COLUMN_NAME);
            try {
                vRtrManager.addRoutingRule(p);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse routing rule for router {}, " +
                             "entry ignored", p.owner, e);
            }
        }

        /* Transfer the static ARP table */
        VirtualRouterManager old = this.vRouterManager;
        if (old != null)
            vRtrManager.setStaticArpTable(old.getStaticArpTable());
        this.vRouterManager = vRtrManager;
        /* The new virtual router manager has its own virtual MACs. Let go of
         * old MACs
         */
        if (old != null) {
            old.relinquishVMacs();

            FCQueryObj fcQueryObj =
                    new FCQueryObj(this,
                                   IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                                   null,   // null vlan
                                   null,   // null srcDevice
                                   null,   // null destDevice
                                   getName(),
                                   FCQueryEvType.ACL_CONFIG_CHANGED,
                                   null);
            betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        }
    }

    protected void readStaticArpTableFromStorage() {
        Map<Integer, MACAddress> staticArpMap =
                new HashMap<Integer, MACAddress>();
        IResultSet staticArpSet =
                storageSource.executeQuery(STATIC_ARP_TABLE_NAME, null, null,
                                           null);
        while (staticArpSet.next()) {
            String ip = staticArpSet.getString(IP_COLUMN_NAME);
            String mac = staticArpSet.getString(MAC_COLUMN_NAME);
            Integer ipAddr = null;
            MACAddress macAddr = null;
            try {
                ipAddr = Integer.valueOf(IPv4.toIPv4Address(ip));
                macAddr = MACAddress.valueOf(mac);
                staticArpMap.put(ipAddr, macAddr);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse static arp entry ip:{} mac:{}, " +
                             "entry ignored", ipAddr, macAddr);
            }
        }
        vRouterManager.setStaticArpTable(staticArpMap);
    }

    private void submitQueryforAclConfigChange() {
        /*
         * Submit flow query to get the flows in each of the VNSes
         * where acl configuration has changed.
         */
        if (logger.isTraceEnabled()) {
            logger.trace("Set of VNSes to query for flow reconciliation for " +
                         "ACl config chage: {}", vnsWithAclChangedList);
        }
        for (String vnsName : vnsWithAclChangedList) {
            /* For each of this NetVirtes submit flow query for all the flows in
             * the NetVirt.
             *  Submit flow query to the flow cache. The flows are reconciled
             * in virtual routing.
             */
            FCQueryObj fcQueryObj = new FCQueryObj(this,
                                                   vnsName,
                                                   null,   // null vlan
                                                   null,   // null srcDevice
                                                   null,   // null destDevice
                                                   getName(),
                                                   FCQueryEvType.ACL_CONFIG_CHANGED,
                                                   null);
            if (logger.isTraceEnabled()) {
                logger.trace("Submitted Flow Query {}", fcQueryObj.toString());
            }
            betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        }
        /* Clear the list now */
        vnsWithAclChangedList.clear();
        /* XXX For now we need to reconcile all virtual routing flows since
         * ACL config change may affect any flow
         */
        FCQueryObj fcQueryObj =
                new FCQueryObj(this,
                               IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                               null,   // null vlan
                               null,   // null srcDevice
                               null,   // null destDevice
                               getName(),
                               FCQueryEvType.ACL_CONFIG_CHANGED,
                               null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);

    }

    private void queueVirtRtrConfigUpdate() {
        virtRtrConfigUpdateTask.reschedule(VR_UPDATE_TASK_BATCH_DELAY_MS,
                                           TimeUnit.MILLISECONDS);
    }

    private boolean isAclTable(String tableName) {
        if (tableName == null) {
            return false;
        }

        if (tableName.equalsIgnoreCase(VNS_ACL_TABLE_NAME) ||
            tableName.equals(VNS_ACL_ENTRY_TABLE_NAME)     ||
            tableName.equals(VNS_INTERFACE_ACL_TABLE_NAME)) {
            return true;
        }

        return false;
    }

    private boolean isVirtualRoutingTable(String tableName) {
        if (tableName == null) {
            return false;
        }

        if (tableName.equals(TENANT_TABLE_NAME) ||
                tableName.equals(VIRT_RTR_TABLE_NAME) ||
                tableName.equals(VIRT_RTR_IFACE_TABLE_NAME) ||
                tableName.equals(VIRT_RTR_ROUTING_RULE_TABLE_NAME) ||
                tableName.equals(IFACE_ADDRESS_POOL_TABLE_NAME) ||
                tableName.equals(VIRT_RTR_GATEWAY_POOL_TABLE_NAME) ||
                tableName.equals(GATEWAY_NODE_TABLE_NAME)) {
            return true;
        }

        return false;
    }

    // ***************
    // IStorageSourceListener
    // ***************

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        logger.debug("Row Modified: TableName={} RowKeys={}", tableName, rowKeys);
        if (isAclTable(tableName)) {
            String tenantName = rowKeys.toString().substring(1).split("\\|")[0];
            String vnsName = rowKeys.toString().substring(1).split("\\|")[1];
            vnsName = tenantName + "|" + vnsName;
            logger.debug("ACL config changed in VNS: {}", vnsName);
            if (!vnsWithAclChangedList.contains(vnsName)) {
                vnsWithAclChangedList.add(vnsName);
            }
            queueConfigUpdate();
        } else if (isVirtualRoutingTable(tableName)) {
            queueVirtRtrConfigUpdate();
        } else if (tableName.equals(STATIC_ARP_TABLE_NAME)) {
            readStaticArpTableFromStorage();
            FCQueryObj fcQueryObj =
                    new FCQueryObj(this,
                                   IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                                   null,   // null vlan
                                   null,   // null srcDevice
                                   null,   // null destDevice
                                   getName(),
                                   FCQueryEvType.ACL_CONFIG_CHANGED,
                                   null);
            betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        } else {
            logger.warn("Received row modified callback for unknonwn table {}",
                        tableName);
        }
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        logger.debug("Row Deleted: TableName={} RowKeys={}", tableName, rowKeys);
        if (isAclTable(tableName)) {
            String tenantName = rowKeys.toString().substring(1).split("\\|")[0];
            String vnsName = rowKeys.toString().substring(1).split("\\|")[1];
            vnsName = tenantName + "|" + vnsName;
            logger.debug("ACL config deleted in VNS: {}", vnsName);
            if (!vnsWithAclChangedList.contains(vnsName)) {
                vnsWithAclChangedList.add(vnsName);
            }
            queueConfigUpdate();
        } else if (isVirtualRoutingTable(tableName)) {
            queueVirtRtrConfigUpdate();
        } else if (tableName.equals(STATIC_ARP_TABLE_NAME)) {
            readStaticArpTableFromStorage();
            FCQueryObj fcQueryObj =
                    new FCQueryObj(this,
                                   IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                                   null,   // null vlan
                                   null,   // null srcDevice
                                   null,   // null destDevice
                                   getName(),
                                   FCQueryEvType.ACL_CONFIG_CHANGED,
                                   null);
            betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        } else {
            logger.warn("Received row deleted callback for unknonwn table {}",
                        tableName);
        }
    }

    // ***************
    // IModule
    // ***************

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IVirtualRoutingService.class);
        l.add(IVirtualMacService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m =
            new HashMap<Class<? extends IPlatformService>,
                        IPlatformService>();
        m.put(IVirtualRoutingService.class, this);
        m.put(IVirtualMacService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IDeviceService.class);
        l.add(INetVirtManagerService.class);
        l.add(IControllerService.class);
        l.add(IStorageSourceService.class);
        l.add(IFlowReconcileService.class);
        l.add(IFlowCacheService.class);
        l.add(IForwardingService.class);
        l.add(IThreadPoolService.class);
        l.add(ITopologyService.class);
        l.add(ITunnelManagerService.class);
        l.add(IRewriteService.class);
        l.add(ILinkDiscoveryService.class);
        l.add(IRoutingService.class);

        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        deviceManager =
            context.getServiceImpl(IDeviceService.class);
        netVirtManager =
            context.getServiceImpl(INetVirtManagerService.class);
        controllerProvider =
            context.getServiceImpl(IControllerService.class);
        storageSource =
            context.getServiceImpl(IStorageSourceService.class);
        flowReconcileMgr =
                context.getServiceImpl(IFlowReconcileService.class);
        betterFlowCacheMgr =
                context.getServiceImpl(IFlowCacheService.class);
        forwarding =
                context.getServiceImpl(IForwardingService.class);
        threadPool =
                context.getServiceImpl(IThreadPoolService.class);
        topology =
                context.getServiceImpl(ITopologyService.class);
        tunnelManager =
                context.getServiceImpl(ITunnelManagerService.class);
        rewriteService =
                context.getServiceImpl(IRewriteService.class);
        linkDiscovery =
                context.getServiceImpl(ILinkDiscoveryService.class);
        routingService =
                context.getServiceImpl(IRoutingService.class);

        // initialize global locks and maps
        acls = new ConcurrentHashMap<String, VNSAccessControlList>();
        inIfToAcls =
                new ConcurrentHashMap<String, List<VNSAccessControlList>>();
        outIfToAcls =
                new ConcurrentHashMap<String, List<VNSAccessControlList>>();
        aclLock = new ReentrantReadWriteLock();
        packetListeners = new ListenerDispatcher<OFType, IOFMessageListener>();
        flowReconcileListeners =
                new ListenerDispatcher<OFType, IFlowReconcileListener>();
        vnsWithAclChangedList =
                        Collections.synchronizedList(new ArrayList<String>());

        arpManager = new ArpManager();
        arpManager.setControllerProvider(controllerProvider);
        arpManager.setDeviceManager(deviceManager);
        arpManager.setVirtualRouting(this);
        arpManager.setTopology(topology);
        arpManager.setTunnelManager(tunnelManager);
        arpManager.addArpListener(this);

        dhcpManager = new DhcpManager();
        dhcpManager.setControllerProvider(controllerProvider);
        dhcpManager.setDeviceManager(deviceManager);
        this.addPacketListener(dhcpManager);

        icmpManager = new IcmpManager();
        this.addPacketListener(icmpManager);
        icmpManager.addIcmpListener(this);

        reclaimedVMacs = new ArrayList<Long>();
        nextAvailableVMac = MIN_VIRTUAL_MAC;
    }

    @Override
    public void startUp(ModuleContext context) {
        // Our 'constructor'
        arpManager.startUp();
        dhcpManager.init();
        // Create our storage tables
        storageSource.createTable(VNS_ACL_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VNS_ACL_TABLE_NAME,
                                             NAME_COLUMN_NAME);
        storageSource.createTable(VNS_ACL_ENTRY_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VNS_ACL_ENTRY_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(VNS_INTERFACE_ACL_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VNS_INTERFACE_ACL_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(TENANT_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(TENANT_TABLE_NAME,
                                             NAME_COLUMN_NAME);
        storageSource.createTable(VIRT_RTR_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VIRT_RTR_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(VIRT_RTR_IFACE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VIRT_RTR_IFACE_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(VIRT_RTR_GATEWAY_POOL_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VIRT_RTR_GATEWAY_POOL_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(VIRT_RTR_ROUTING_RULE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VIRT_RTR_ROUTING_RULE_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(IFACE_ADDRESS_POOL_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(IFACE_ADDRESS_POOL_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(GATEWAY_NODE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(GATEWAY_NODE_TABLE_NAME,
                                             ID_COLUMN_NAME);
        storageSource.createTable(STATIC_ARP_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(STATIC_ARP_TABLE_NAME,
                                             IP_COLUMN_NAME);

        // thread to get ACL updates from storage
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        configUpdateTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                readAclTablesFromStorage();
            }
        });

        // Thread to get virtual routing table updates from storage
        virtRtrConfigUpdateTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                readVirtRtrTablesFromStorage();
            }
        });

        // listen for packetIns
        controllerProvider.addOFMessageListener(OFType.PACKET_IN, this);
        controllerProvider.addInfoProvider("summary", this);
        controllerProvider.addHAListener(this);

        // listen for storage notifcations for these tables
        storageSource.addListener(VNS_ACL_TABLE_NAME, this);
        storageSource.addListener(VNS_ACL_ENTRY_TABLE_NAME, this);
        storageSource.addListener(VNS_INTERFACE_ACL_TABLE_NAME, this);
        storageSource.addListener(TENANT_TABLE_NAME, this);
        storageSource.addListener(VIRT_RTR_TABLE_NAME, this);
        storageSource.addListener(VIRT_RTR_IFACE_TABLE_NAME, this);
        storageSource.addListener(VIRT_RTR_GATEWAY_POOL_TABLE_NAME, this);
        storageSource.addListener(VIRT_RTR_ROUTING_RULE_TABLE_NAME, this);
        storageSource.addListener(IFACE_ADDRESS_POOL_TABLE_NAME, this);
        storageSource.addListener(GATEWAY_NODE_TABLE_NAME, this);
        storageSource.addListener(STATIC_ARP_TABLE_NAME, this);

        deviceManager.addListener(dhcpManager.getDeviceListener());

        // Listen for flow reconciliation
        flowReconcileMgr.addFlowReconcileListener(this);

        // load ACL settings from storage
        readAclTablesFromStorage();
        // load Virtual routing settings from storage
        readVirtRtrTablesFromStorage();
        // load static arp settings from storage
        readStaticArpTableFromStorage();
    }

    // ***************
    // IFlowReconcileListener
    // ***************

    @Override
    public Command reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList) {
        ListIterator<OFMatchReconcile> iter = ofmRcList.listIterator();
        while (iter.hasNext()) {
            OFMatchReconcile ofm = iter.next();
            if (ofm == null) {
                iter.remove();
                continue;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Reconciling flow: match={}",
                             ofm.ofmWithSwDpid.getOfMatch());
            }

            List<VNSInterface> srcIfaces =
                    INetVirtManagerService.bcStore.get(ofm.cntx,
                                        INetVirtManagerService.CONTEXT_SRC_IFACES);
            if (srcIfaces == null) {
                logger.debug("Null src vnsInterface for {}",
                       HexString.toHexString(
                          ofm.ofmWithSwDpid.getOfMatch().getDataLayerSource()));
                ofm.rcAction = OFMatchReconcile.ReconcileAction.DROP;
                continue;
            }

            OFMatch ofMatch = ofm.ofmWithSwDpid.getOfMatch();
            long srcMAC = Ethernet.toLong(ofMatch.getDataLayerSource());
            long dstMAC = Ethernet.toLong(ofMatch.getDataLayerDestination());
            short vlan = ofMatch.getDataLayerVirtualLan();
            short ethType = ofMatch.getDataLayerType();
            int srcIp = ofMatch.getNetworkSource();
            int dstIp = ofMatch.getNetworkDestination();
            ForwardingAction fAction;
            fAction = vRouterManager.getForwardingAction(srcMAC, dstMAC, vlan,
                                                         ethType, srcIp, dstIp,
                                                         ofm.cntx);

            if (logger.isTraceEnabled()) {
                logger.trace("Reconcile flow: {}, sIface={}, forwarding " +
                             "action:{}", new Object[]{ofm, srcIfaces.get(0),
                                                       fAction.toString()});
            }

            if (fAction.getAction() == RoutingAction.DROP) {
                // IF next hop is unknown, do not insert drop flow mod
                // Instead delete the flow so that we can discover the dest
                // device on getting a consecutive packet-in
                if (fAction.getDropReason() == DropReason.NEXT_HOP_UNKNOWN) {
                    ofm.rcAction = OFMatchReconcile.ReconcileAction.DELETE;
                    if (logger.isDebugEnabled()) {
                        logger.debug("Destination Unknown DELETE flow: {}", ofm);
                    }
                    continue;
                }
                // Drop the flow mod
                ofm.rcAction = OFMatchReconcile.ReconcileAction.DROP;
                if (logger.isDebugEnabled()) {
                    logger.debug("flow mod action is changed to DROP " +
                                 "since no matched VNS: {}",
                                 ofm);
                }
                continue;
            }

            VNSInterface dIface = INetVirtManagerService.bcStore.get(ofm.cntx,
                                  INetVirtManagerService.CONTEXT_DST_IFACES).get(0);
            VNSInterface sIface = INetVirtManagerService.bcStore.
                    get(ofm.cntx, INetVirtManagerService.CONTEXT_SRC_IFACES).get(0);
            if (logger.isTraceEnabled()) {
                logger.trace("Reconcile flow: {} dIface {}",
                             new Object[]{ofm, dIface});
            }

            String newAppName = (String)ofm.cntx.getStorage().
                    get(IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME);
            /* The flow is allowed to be forwarded, remove drop flows
             * if any */
            if (ofm.action == FlowCacheObj.FCActionDENY) {
                ofm.rcAction = OFMatchReconcile.ReconcileAction.DELETE;
                logger.debug("******** DELETE flow: {}", ofm);
                continue;
            } else if (!newAppName.equals(ofm.appInstName)) {
                logger.debug("*** New FC APP Name {}, old {}", newAppName,
                             ofm.appInstName);
                /* The flow is now in a different VNS or virtual routed */
                ofm.rcAction =
                        OFMatchReconcile.ReconcileAction.APP_INSTANCE_CHANGED;
                ofm.newAppInstName = newAppName;
            }

            /* The flow cache entry is a FCActionPERMIT entry */

            MutableInteger hint = new MutableInteger(DEFAULT_HINT);
            if (fAction.getAction() == RoutingAction.FORWARD
                    && fAction.isVirtualRouted()) {
                /* In case the packet is virtual routed, we need to NOT wildcard
                 * both the source and dest IP. This is because the source and
                 * dest IPs are used to determine flow permit/deny policy and we
                 * will require this information for flow reconciliation.
                 * XXX There is a better way to do this without affecting actual
                 * flows on the switch...redesign flow cache
                 */
                hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_DST_MASK)));
                hint.setValue((hint.intValue() & ~(OFMatch.OFPFW_NW_SRC_MASK)));
            }

            VNSAclMatchResult aclResult = applyAclToReconciledFlow(ofm.cntx,
                        ofm.ofmWithSwDpid.getOfMatch(),
                        sIface, dIface, hint);

            /* Delete the flow if the new wildcards that acl needs and the
             * wildcard in the flow mod are not same.
             */
            if (ofm.ofmWithSwDpid.getOfMatch().getWildcards() !=
                    hint.intValue()) {
                /* Delete the flow */
                ofm.rcAction = OFMatchReconcile.ReconcileAction.DELETE;
                if (logger.isDebugEnabled()) {
                    logger.debug("Deleted flow mod on acl-wildcard change: {}",
                            ofm);
                }
                continue;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Reconcile flow: {} ofm.action {} aclResult {}",
                             new Object[]{ofm, ofm.action, aclResult});
            }

            /* Wildcards are same */
            switch (aclResult) {
                case ACL_PERMIT:
                    /* If the flow cache entry is permit then leave the flow
                     * as-is.
                     */
                    break;

                case ACL_DENY:
                    /* If the flow cache entry is deny then leave the flow
                     * as-is. If the flow cache entry is permit then change
                     * the entry's action to drop.
                     */
                    if (ofm.action == FlowCacheObj.FCActionPERMIT) {
                        ofm.rcAction = OFMatchReconcile.ReconcileAction.DROP;
                        if (logger.isTraceEnabled()) {
                            logger.trace(
                                "Changed flow mod to drop on acl change: {}",
                                ofm);
                        }
                    }
                    break;

                case ACL_NO_MATCH:
                    /* No op. */
                    break;
            }

            if (ofm.rcAction != OFMatchReconcile.ReconcileAction.DROP) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Virtual Routing Reconciled flowmod {}", ofm);
                }
                IRoutingDecision decision = IRoutingDecision.rtStore.get(
                                            ofm.cntx,
                                            IRoutingDecision.CONTEXT_DECISION);
                if (decision == null) {
                    IDevice srcDevice =
                            IDeviceService.fcStore.
                                get(ofm.cntx, IDeviceService.CONTEXT_SRC_DEVICE);
                    IDevice dstDevice =
                            IDeviceService.fcStore.
                                get(ofm.cntx, IDeviceService.CONTEXT_DST_DEVICE);
                    RoutingDecision d = new RoutingDecision(
                                        ofm.ofmWithSwDpid.getSwitchDataPathId(),
                                        ofm.ofmWithSwDpid.getOfMatch().getInputPort(),
                                        srcDevice, RoutingAction.FORWARD);
                    d.setWildcards(hint.intValue());
                    if (dstDevice != null)
                        d.addDestinationDevice(dstDevice);
                    d.addToContext(ofm.cntx);
                } else {
                    decision.setRoutingAction(RoutingAction.FORWARD);
                }
            }
        }

        IFlowReconcileListener.Command retCmd;
        List<IFlowReconcileListener> listeners =
                flowReconcileListeners.getOrderedListeners();
        if (listeners != null && listeners.size() > 0) {
            for (IFlowReconcileListener flowReconciler :
                flowReconcileListeners.getOrderedListeners()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Reconciling flow: call listener {}",
                                 flowReconciler.getName());
                }
                retCmd = flowReconciler.reconcileFlows(ofmRcList);
                if (retCmd == IFlowReconcileListener.Command.STOP) {
                    break;
                }
            }
        }

        if (ofmRcList.size() > 0) {
            return Command.CONTINUE;
        } else {
            return Command.STOP;
        }
    }

    // ***************
    // IFlowQueryHandler
    // ***************

    @Override
    public void flowQueryRespHandler(FlowCacheQueryResp flowResp) {
        /* for each flow check if they belong to a common VNS. If not then the
         * flow needs to be deleted. If the flow does belong to a common VNS
         * then check if is it the same VNS the flow is in now. If not then
         * tell flow cache to move the flow to a different NetVirt. If this case
         * flow-cache manager should send the flow to the other listeners
         * including the virtual routing listener so that the acls of the new
         * VNS can be applied.
         */
        flowQueryRespHandlerCallCount++;
        lastFCQueryResp = flowResp;
        if (logger.isTraceEnabled()) {
            logger.trace("Executing flowQueryRespHandler {} flowCnt={}",
                                flowResp.toString(),
                                lastFCQueryResp.qrFlowCacheObjList.size());
        }

        flowReconcileMgr.flowQueryGenericHandler(flowResp);
        return;
    }

    // ***************
    // IInfoProvider
    // ***************

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();
        info.put("# Access Control Lists", acls.size());
        info.put("# VNS Interfaces with ACL applied",
                 inIfToAcls.size() + outIfToAcls.size());

        return info;
    }

    // ***************
    // IHAListener
    // ***************

    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    logger.debug("Re-reading ACLs and virtual routing config " +
                            "from storage due to HA change from SLAVE->MASTER");
                    readAclTablesFromStorage();
                    readVirtRtrTablesFromStorage();
                    readStaticArpTableFromStorage();
                }
                break;
            case SLAVE:
                logger.debug("Clearing cached ACL state due to " +
                        "HA change to SLAVE");
                clearCachedAclState();
                clearCachedState();
                break;
            default:
                break;
        }
    }

    @Override
    public void controllerNodeIPsChanged(
            Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {
        // ignore
    }

    // ***************
    // IVirtualMacService
    // ***************

    @Override
    public boolean acquireVirtualMac(long vMac) {
        // Make sure the MAC address is in the managed range.
        if (vMac < MIN_VIRTUAL_MAC || vMac > MAX_VIRTUAL_MAC) {
            return true;
        }
        synchronized (reclaimedVMacs) {
            if (vMac < nextAvailableVMac) {
                if (reclaimedVMacs.contains(vMac)) {
                    // The request vMac is available.
                    reclaimedVMacs.remove(Long.valueOf(vMac));
                    return true;
                } else {
                    // The request vMac is not available.
                    return false;
                }
            } else if (vMac == nextAvailableVMac) {
                nextAvailableVMac++;
                return true;
            } else {
                // Add MAC addresses between nextAvailableVMac and vMAC to
                // reclaimedVMacs list.
                for (long mac = nextAvailableVMac; mac < vMac; mac++) {
                    reclaimedVMacs.add(mac);
                }
                nextAvailableVMac = vMac + 1;
                return true;
            }
        }
    }

    @Override
    public long acquireVirtualMac() throws VirtualMACExhaustedException {
        long vMac = 0;
        synchronized (reclaimedVMacs) {
            if (!reclaimedVMacs.isEmpty()) {
                vMac = reclaimedVMacs.remove(0);
            } else {
                if (nextAvailableVMac <= MAX_VIRTUAL_MAC) {
                    vMac = nextAvailableVMac++;
                } else {
                    throw new VirtualMACExhaustedException();
                }
            }
            return vMac;
        }
    }

    @Override
    public void relinquishVirtualMAC (long vMac) {
        // Make sure the MAC address is in the managed range.
        if (vMac < MIN_VIRTUAL_MAC || vMac > MAX_VIRTUAL_MAC) {
            return;
        }
        synchronized (reclaimedVMacs) {
            reclaimedVMacs.add(vMac);
        }
    }

    // ***************
    // IARPListener
    // ***************

    /**
     * Creates an ARP reply encapsulated in an Ethernet frame.
     * @param srcIp The source IP (host that sent the ARP request).
     * @param dstIp The destination IP (The IP the host is querying for).
     * @param srcMac The MAC address of the host that sent the ARP request.
     * @param dstMac The MAC address the host with the corresponding IP.
     * @param vlanId The VLAN the host is on.
     * @param priorityCode The Ethernet priority code.
     * @return An Ethernet packet with an ARP reply encapsulated in it.
     */
    protected Ethernet createArpReplyPacket(int srcIp, int dstIp, long srcMac,
                            long dstMac, short vlanId, byte priorityCode) {
        byte[] dstMacByte = MACAddress.valueOf(dstMac).toBytes();
        byte[] srcMacByte = MACAddress.valueOf(srcMac).toBytes();

        IPacket arpReply = new Ethernet()
        .setSourceMACAddress(dstMacByte)
        .setDestinationMACAddress(srcMacByte)
        .setEtherType(Ethernet.TYPE_ARP)
        .setVlanID(vlanId)
        .setPriorityCode(priorityCode)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setTargetHardwareAddress(srcMacByte)
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes(srcIp))
                .setSenderHardwareAddress(dstMacByte)
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes(dstIp)));

        return (Ethernet) arpReply;
    }

    @Override
    public ARPCommand ARPRequestHandler(IOFSwitch sw, OFPacketIn pi,
                                        ListenerContext cntx,
                                        ARPMode configMode) {
        IDevice srcDevice =
                IDeviceService.fcStore.get(cntx,
                                           IDeviceService.CONTEXT_SRC_DEVICE);
        if (srcDevice == null) return ARPCommand.CONTINUE;

        Ethernet eth =
                IControllerService.bcStore.get(cntx,
                        IControllerService.CONTEXT_PI_PAYLOAD);
        ARP arp = (ARP) eth.getPayload();
        int dstip = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
        int srcip = IPv4.toIPv4Address(arp.getSenderProtocolAddress());
        List<VNSInterface> vnsIfaces = netVirtManager.getInterfaces(srcDevice);

        if (vnsIfaces == null) return ARPCommand.CONTINUE;

        long vMacChosen = 0;
        String addressSpace = null;
        VNS vnsChosen = null;
        for (VNSInterface iface : vnsIfaces) {
            /* Choose the highest priority VNS connected to a router with an
             * interface IP of dstip
             */
            VNS vns = iface.getParentVNS();
            long vMac = vRouterManager.getRtrVMac(vns, srcip, dstip);
            if (vMac != 0 &&
                    (vnsChosen == null || vns.compareTo(vnsChosen) < 0)) {
                vMacChosen = vMac;
                vnsChosen = vns;
                addressSpace = vns.getAddressSpaceName();
            }
        }

        if (vMacChosen == 0) {
            /* The destination IP does not belong to any virtual router */
            return ARPCommand.CONTINUE;
        }

        Ethernet arpReply = createArpReplyPacket(srcip, dstip,
                                                 srcDevice.getMACAddress(),
                                                 vMacChosen, eth.getVlanID(),
                                                 eth.getPriorityCode());
        short inPort = OFPort.OFPP_NONE.getValue();
        short outPort = pi.getInPort();
        SwitchPort dap = new SwitchPort(sw.getId(), pi.getInPort());
        if (forwarding.pushPacketOutToEgressPort(arpReply, inPort, dap, false,
                                                 addressSpace, eth.getVlanID(),
                                                 null, true)) {
            logger.trace("Writing fake ARP reply for virtual routing {}," +
                    "ARP = {}", arpReply.getPayload());
        } else {
            logger.warn("Failed to send fake ARP reply for virtual " +
                    "routing, at {}/{} from inPort={}",
                    new Object[]{ HexString.toHexString(dap.getSwitchDPID()),
                                  outPort, inPort});
        }

        return ARPCommand.STOP;
    }

    @Override
    public ARPCommand ARPReplyHandler(IOFSwitch sw, OFPacketIn arp,
                                      ListenerContext cntx,
                                      ARPMode configMode) {
        return ARPCommand.CONTINUE;
    }

    @Override
    public ARPCommand RARPRequestHandler(IOFSwitch sw, OFPacketIn pi,
                                         ListenerContext cntx,
                                         ARPMode configMode) {
        return ARPCommand.CONTINUE;
    }

    @Override
    public ARPCommand RARPReplyHandler(IOFSwitch sw, OFPacketIn pi,
                                       ListenerContext cntx,
                                       ARPMode configMode) {
        return ARPCommand.CONTINUE;
    }

    // ***************
    // IICMPListener
    // ***************

    /**
     * Creates an ICMP reply encapsulated in an Ethernet frame.
     * @param srcIp The source IP (host that sent the ICMP request).
     * @param dstIp The destination IP (The IP the host is querying for).
     * @param srcMac The MAC address of the host that sent the ICMP request.
     * @param dstMac The MAC address of the host with the corresponding IP.
     * @param request The original ICMP request.
     * @return An Ethernet packet with an ICMP reply encapsulated in it.
     */
    protected Ethernet createICMPReplyPacket(int srcIp, int dstIp, long srcMac,
                                             long dstMac, Ethernet request) {
        byte[] dstMacByte = MACAddress.valueOf(dstMac).toBytes();
        byte[] srcMacByte = MACAddress.valueOf(srcMac).toBytes();

        Ethernet icmpReply = (Ethernet) request.clone();
        icmpReply.setSourceMACAddress(dstMacByte);
        icmpReply.setDestinationMACAddress(srcMacByte);
        icmpReply.setEtherType(Ethernet.TYPE_IPv4);

        IPv4 ipv4 = (IPv4) icmpReply.getPayload();
        ipv4.setSourceAddress(dstIp);
        ipv4.setDestinationAddress(srcIp);
        ipv4.setChecksum((short)0);

        ICMP icmp = (ICMP) ipv4.getPayload();
        icmp.setIcmpType(ICMP.ECHO_REPLY);
        icmp.setChecksum((short)0);

        return icmpReply;
    }

    @Override
    public ICMPCommand ICMPRequestHandler(IOFSwitch sw, OFPacketIn pi,
                                          ListenerContext cntx) {
        List<VNSInterface> srcIfaces = INetVirtManagerService.bcStore.get(cntx,
                INetVirtManagerService.CONTEXT_SRC_IFACES);
        if (srcIfaces == null)
            return ICMPCommand.CONTINUE;

        Ethernet eth =
                IControllerService.bcStore.get(cntx,
                        IControllerService.CONTEXT_PI_PAYLOAD);
        IPv4 ipv4 = (IPv4) eth.getPayload();
        int srcIp = ipv4.getSourceAddress();
        int dstIp = ipv4.getDestinationAddress();

        /* Iterate over source VNSes and find one that allows reachability */
        VNS chosenVNS = null;
        for (VNSInterface iface : srcIfaces) {
            VNS srcVNS = iface.getParentVNS();
            if (srcVNS == null)
                continue;

            if (logger.isTraceEnabled()) {
                logger.trace("Checking router reachability {} -> {}, srcVNS: {}",
                             new Object[] { IPv4.fromIPv4Address(srcIp),
                                            IPv4.fromIPv4Address(dstIp),
                                            srcVNS.toString()});
            }

            if (vRouterManager.getRtrVMac(srcVNS, srcIp, dstIp) != 0) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Router is reachable from srcVNS: {}", srcVNS);
                }
                chosenVNS = srcVNS;
                break;
            }
        }

        if (chosenVNS == null)
            return ICMPCommand.CONTINUE;

        long srcMac = Ethernet.toLong(eth.getSourceMACAddress());
        long dstMac = Ethernet.toLong(eth.getDestinationMACAddress());
        Ethernet icmpReply = createICMPReplyPacket(srcIp, dstIp, srcMac, dstMac,
                                                   eth);
        short inPort = OFPort.OFPP_NONE.getValue();
        short outPort = pi.getInPort();
        SwitchPort dap = new SwitchPort(sw.getId(), pi.getInPort());
        if (forwarding.pushPacketOutToEgressPort(icmpReply, inPort, dap, false,
                chosenVNS.getAddressSpaceName(), eth.getVlanID(), null, true)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Writing fake ICMP reply for virtual routing");
            }
        } else {
            logger.warn("Failed to send fake ICMP reply for virtual routing, " +
                        "at {}/{} from inPort={}",
                        new Object[]{ HexString.toHexString(dap.getSwitchDPID()),
                                      outPort, inPort});
        }
        return ICMPCommand.STOP;
    }

    @Override
    public ICMPCommand ICMPReplyHandler(IOFSwitch sw, OFPacketIn pi,
                                        ListenerContext cntx) {
        return ICMPCommand.CONTINUE;
    }

    // ***************
    // Traceroute Handler
    // ***************

    /**
     * Creates an ICMP Time Exceeded encapsulated in an Ethernet frame.
     * @param srcIp The source IP (host that sent the request).
     * @param vIp The virtual router IP.
     * @param srcMac The MAC address of the host that sent the request.
     * @param vMac The MAC address of the virtual router.
     * @param request The original request packet.
     * @return An Ethernet packet with an ICMP Time Exceeded encapsulated in it.
     */
    protected Ethernet createICMPTimeExceededPacket(int srcIp, int vIp,
                                                    long srcMac, long vMac,
                                                    Ethernet request) {
        byte[] vMacByte = MACAddress.valueOf(vMac).toBytes();
        byte[] srcMacByte = MACAddress.valueOf(srcMac).toBytes();

        IPacket icmpTimeExceeded = new Ethernet()
        .setSourceMACAddress(vMacByte)
        .setDestinationMACAddress(srcMacByte)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setVlanID(request.getVlanID())
        .setPriorityCode(request.getPriorityCode())
        .setPayload(
                new IPv4()
                .setSourceAddress(vIp)
                .setDestinationAddress(srcIp)
                .setProtocol(IPv4.PROTOCOL_ICMP)
                .setTtl((byte) 64)
                .setPayload(
                        new ICMP()
                        .setIcmpType(ICMP.TIME_EXCEEDED)
                        .setIcmpCode((byte) 0)
                        .setPayload(request.getPayload())));

        return (Ethernet) icmpTimeExceeded;
    }

    /**
     * Creates an ICMP Destination Unreachable (Port Unreachable) encapsulated
     * in an Ethernet frame.
     * @param srcIp The source IP (host that sent the request).
     * @param vIp The virtual router IP.
     * @param srcMac The MAC address of the host that sent the request.
     * @param vMac The MAC address of the virtual router.
     * @param request The original request packet.
     * @return An Ethernet packet with an ICMP Destination Unreachable (Port
     *         Unreachable) in it.
     */
    protected Ethernet createICMPUnreachablePacket(int srcIp, int vIp,
                                                   long srcMac, long vMac,
                                                   Ethernet request) {
        byte[] vMacByte = MACAddress.valueOf(vMac).toBytes();
        byte[] srcMacByte = MACAddress.valueOf(srcMac).toBytes();

        IPacket icmpUnreachable = new Ethernet()
        .setSourceMACAddress(vMacByte)
        .setDestinationMACAddress(srcMacByte)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setVlanID(request.getVlanID())
        .setPriorityCode(request.getPriorityCode())
        .setPayload(
                new IPv4()
                .setSourceAddress(vIp)
                .setDestinationAddress(srcIp)
                .setProtocol(IPv4.PROTOCOL_ICMP)
                .setTtl((byte) 64)
                .setPayload(
                        new ICMP()
                        .setIcmpType(ICMP.DESTINATION_UNREACHABLE)
                        .setIcmpCode(ICMP.CODE_PORT_UNREACHABLE)
                        .setPayload(request.getPayload())));

        return (Ethernet) icmpUnreachable;
    }

    /**
     * Checks that the incoming packet is a traceroute packet.
     * @param cntx SDN Platform context.
     * @return true if incoming packet is a traceroute packet, and false
     *         otherwise.
     */
    public static boolean isTraceroutePacket(ListenerContext cntx) {
        Ethernet eth =
                IControllerService.bcStore.get(cntx,
                        IControllerService.CONTEXT_PI_PAYLOAD);

        if (eth.getEtherType() != Ethernet.TYPE_IPv4)
            return false;

        IPv4 ipv4 = (IPv4) eth.getPayload();
        if (ipv4.getProtocol() != IPv4.PROTOCOL_UDP)
            return false;

        UDP udp = (UDP) ipv4.getPayload();
        short dstPort = udp.getDestinationPort();
        if (dstPort < TRACEROUTE_PORT_START || dstPort > TRACEROUTE_PORT_END)
            return false;

        return true;
    }

    /**
     * Handles traceroute packets.
     * @param sw The switch on which the traceroute packet is received on.
     * @param pi The packet-in.
     * @param cntx The SDN Platform context.
     * @return true if a traceroute response happens and packet-out is pushed,
     *         and false otherwise.
     */
    private boolean handleTraceroute(IOFSwitch sw, OFPacketIn pi,
                                     ListenerContext cntx) {
        List<VNSInterface> srcIfaces = INetVirtManagerService.bcStore.get(cntx,
                INetVirtManagerService.CONTEXT_SRC_IFACES);
        if (srcIfaces == null)
            return false;

        Ethernet eth =
                IControllerService.bcStore.get(cntx,
                        IControllerService.CONTEXT_PI_PAYLOAD);

        IPv4 ipv4 = (IPv4) eth.getPayload();
        int srcIp = ipv4.getSourceAddress();
        int dstIp = ipv4.getDestinationAddress();
        byte ttl = ipv4.getTtl();
        long srcMac = Ethernet.toLong(eth.getSourceMACAddress());
        long dstMac = Ethernet.toLong(eth.getDestinationMACAddress());
        VNS chosenVNS = null;
        Ethernet replyPacket = null;

        for (VNSInterface iface : srcIfaces) {
            VNS srcVNS = iface.getParentVNS();
            if (srcVNS == null)
                continue;

            int vIp = vRouterManager.getRtrIp(srcVNS, srcIp, dstIp);
            if (vIp == 0)
                continue;

            if (vIp == dstIp) { // VR as a destination
                if (vRouterManager.getRtrVMac(srcVNS, srcIp, dstIp) != 0) {
                    chosenVNS = srcVNS;
                    replyPacket = createICMPUnreachablePacket(srcIp, dstIp,
                                                              srcMac, dstMac,
                                                              eth);
                    break;
                }
            } else if (ttl == 1) { // VR as a hop
                chosenVNS = srcVNS;
                replyPacket = createICMPTimeExceededPacket(srcIp, vIp, srcMac,
                                                           dstMac, eth);
                break;
            }
        }

        if (chosenVNS == null)
            return false;

        short inPort = OFPort.OFPP_NONE.getValue();
        short outPort = pi.getInPort();
        SwitchPort dap = new SwitchPort(sw.getId(), pi.getInPort());
        if (forwarding.pushPacketOutToEgressPort(replyPacket, inPort, dap,
                false, chosenVNS.getAddressSpaceName(), eth.getVlanID(), null,
                true)) {
            if (logger.isTraceEnabled()) {
                logger.info("Writing fake traceroute reply for virtual routing");
            }
        } else {
            logger.info("Failed to send fake traceroute reply for virtual routing, " +
                        "at {}/{} from inPort={}",
                        new Object[]{ HexString.toHexString(dap.getSwitchDPID()),
                                      outPort, inPort});
        }
        return true;
    }
}
