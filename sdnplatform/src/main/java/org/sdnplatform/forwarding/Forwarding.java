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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
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
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.counter.ICounterStoreService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowReconcileListener;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.OFMatchReconcile;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.packet.ARP;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.routing.ForwardingBase;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.routing.RouteId;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.topology.IBetterTopologyService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.util.OFMessageDamper;
import org.sdnplatform.vendor.OFActionNiciraTtlDecrement;
import org.sdnplatform.vendor.OFActionTunnelDstIP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@LogMessageCategory("Flow Programming")
public class Forwarding extends ForwardingBase
       implements IModule, IForwardingService,
                  IFlowReconcileListener, IStorageSourceListener, IHAListener {
    protected static Logger log = LoggerFactory.getLogger(Forwarding.class);

    public static final String TABLE_NAME = "controller_forwardingconfig";
    public static final String COLUMN_PRIMARY_KEY = "id";
    public static final String VALUE_PRIMARY_KEY = "forwarding";
    public static final String COLUMN_ACCESS_PRIORITY = "access_priority";
    public static String ColumnNames[] = {
        COLUMN_PRIMARY_KEY,
        COLUMN_ACCESS_PRIORITY,
    };

    public static final short DEFAULT_ACCESS_PRIORITY = 10;
    protected short accessPriority;

    protected IFlowCacheService betterFlowCacheMgr;
    protected int numberOfTruncatedPacketsSeen = 0;

    protected IStorageSourceService storageSource;
    protected IFlowReconcileService flowReconcileMgr;
    protected ITunnelManagerService tunnelManager;
    protected IRewriteService rewriteService;
    protected IRestApiService restApi;
    // This is the same instance as topology in ForwardingBase
    protected IBetterTopologyService betterTopology;

    protected void setControllerProvider(IControllerService fps) {
        this.controllerProvider = fps;
    }

    @Override
    public Command reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList) {
        /* All flow reconcilers have provided updates on the action to be
         * taken. Execute them. */
        for (OFMatchReconcile ofm : ofmRcList) {
            if (log.isTraceEnabled()) {
                log.trace("Reconciling flow: match={}, rcAction={}",
                             ofm.ofmWithSwDpid.getOfMatch(), ofm.rcAction);
            }
            switch (ofm.rcAction) {
                case UPDATE_PATH:
                    doPushReconciledFlowMod(ofm);
                    break;

                case DROP:
                    doDropReconciledFlowMod(ofm);
                    break;

                case NEW_ENTRY:
                    doPushReconciledFlowMod(ofm);
                    break;

                case DELETE:
                    doDeleteReconciledFlowMod(ofm);
                    break;

                case NO_CHANGE:
                    /* NOP - leave the flow as is */
                    break;

                case APP_INSTANCE_CHANGED:
                    betterFlowCacheMgr.moveFlowToDifferentApplInstName(ofm);
                    break;
            }
        }
        return Command.STOP;
    }
    
    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, ListenerContext cntx) {
        assert(pi != null);
        if (decision == null) {
            log.debug("No decision made for this packet-in={}", pi);
            return Command.CONTINUE;
        }

        switch(decision.getRoutingAction()) {
            case NONE:
                // No decision has been made, this module can not handle the packet.
                // Pass it to the next handler.
                return Command.CONTINUE;
            case DROP:
                doDropFlow(pi, decision, cntx);
                return Command.STOP;
            case FORWARD:
                doForwardFlow(pi, sw, decision, cntx);
                return Command.STOP;
            case FORWARD_OR_FLOOD:
                doForwardFlow(pi, sw, decision, cntx);
                return Command.STOP;
            case MULTICAST:
                if (isInBroadcastCache(sw, pi, cntx)==false)
                    doMulticast(pi, decision, cntx);
                return Command.STOP;
        }
        return Command.CONTINUE;
    }
    
    @Override
    public void setBroadcastCache(boolean state) {
        this.broadcastCacheFeature = state;
    }
    
    @Override
    public boolean getBroadcastCache() {
        return this.broadcastCacheFeature;
    }
    
    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Can not push packetOut with null addressSpace",
                explanation="The address space for a packet could not be " +
                        "determined",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
                @LogMessageDoc(level="WARN",
                message="Unable to push packet to a null switchPort",
                explanation="The destination switch and port for a " +
                        "packet could not be determined",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
       @LogMessageDoc(level="WARN",
                message="Unable to push packet, switch at DPID {dpid} " +
                        "not available",
                explanation="The switch has likely disconnected before a " +
                        "packet could be sent to it",
                recommendation=LogMessageDoc.TRANSIENT_CONDITION),
    })
    public boolean pushPacketOutToEgressPort(Ethernet packet,
                                             short inPort,
                                             SwitchPort swp,
                                             boolean tunnelEnabled,
                                             String addressSpace,
                                             short vlan,
                                             ListenerContext cntx,
                                             boolean flush) {
        if (addressSpace == null) {
            log.warn("Can not push packetOut with null addressSpace");
            return false;
        }
        
        if (swp == null) {
            log.warn("Unable to push packet to a null switchPort");
            return false;
        }
        
        long switchDpid = swp.getSwitchDPID();
        short outPort = (short)swp.getPort();
        IOFSwitch sw = controllerProvider.getSwitches().get(switchDpid);
        if (sw == null) {
            log.warn("Unable to push packet, switch at DPID {} " +
                    "not available", HexString.toHexString(switchDpid));
            return false;
        }
        
        Short egressVlan = null;
        // FIXME: add tunnelEnabled as parameter and currentVlan as parameter
        Ethernet packetOutEth = (Ethernet)packet.clone();
        egressVlan = rewriteService.getSwitchPortVlanMode(swp,
                                                          addressSpace, 
                                                          vlan,
                                                          true);
        if (egressVlan == null) {
            if(log.isDebugEnabled()) {
                log.debug("Address-space {} is forbidden for "
                        + "outgoing port {}. Dropping packet.",
                        new Object[] { addressSpace, swp });
            }
            return false;
        }
        packetOutEth.setVlanID(egressVlan);

        pushPacket(packetOutEth, sw, OFPacketOut.BUFFER_ID_NONE,
                   inPort, outPort, null, cntx, flush);
        
        return true;
    }
    
    public int getNumberOfTruncatedPacketsSeen() {
        return this.numberOfTruncatedPacketsSeen;
    }
    
    /**
     * Attempts to discover a route to the destination by ARPing or flooding for it
     * @param pi The original PacketIn we coulnd't find a route for.
     * @param sw The switch the PacketIn came on
     * @param decision The IRoutingDecision to decide if we should ARP or flood
     * @param cntx The ListenerContext that stores the PacketIn payload
     */
    protected void discoverDstRoute(OFPacketIn pi, IOFSwitch sw, 
                                    IRoutingDecision decision, 
                                    ListenerContext cntx) {
        if (decision.getRoutingAction() == RoutingAction.FORWARD_OR_FLOOD) {
            doFlood(pi, decision, cntx);
        } else if (decision.getRoutingAction() == RoutingAction.FORWARD) {
            // TODO There might a small optimization here. A subcase of this
            // is that we only don't know how to route on one island.
            // If we can find the destination attachment point on only that
            // island we could avoid dropping the packet

            // Since the dest device is unknown, we inject a fake arp
            // only on the broadcast port to discover/identify the device
            injectFakeArpOnAllowedIncomingBroadcastNodePort(pi, sw, cntx, false);
        }
    }
    
    protected void doForwardFlow(OFPacketIn pi, 
                                 IOFSwitch sw, 
                                 IRoutingDecision decision, 
                                 ListenerContext cntx) {
        if (!validateDecision(decision)) {
            return;
        }

        // Initialize data from decision (validation above ensures that they all not null)
        // pin prefix indicates packet-in
        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
        IDevice srcDevice = decision.getSourceDevice();
        long pinSwitchDPID = decision.getSourcePort().getSwitchDPID();
        IOFSwitch pinSwitch = switches.get(pinSwitchDPID);
        if (pinSwitch == null) return;

        Integer pinPort = decision.getSourcePort().getPort();
        Long pinCluster = topology.getL2DomainId(pinSwitchDPID);
        IDevice dstDevice = null;
        SwitchPort[] dstDaps = null;

        if (decision.getDestinationDevices().size() > 0) {
            dstDevice = decision.getDestinationDevices().get(0);
            dstDaps = dstDevice.getAttachmentPoints();
        }

        if (log.isTraceEnabled()) {
            log.trace("doForwardFlow pi={} decision={} srcDevice={} " + 
                      "srcSwitch={} srcPort={} dstDevice={}",
                      new Object[] { pi, decision, srcDevice,
                                     HexString.toHexString(decision.getSourcePort().getSwitchDPID()),
                                     pinPort, dstDevice });
        }

        if (dstDevice == null || dstDaps == null || (dstDaps.length == 0)) {
            discoverDstRoute(pi, sw, decision, cntx);
            return;
        }
        
        boolean isTunnelTraffic = tunnelManager.isTunnelEndpoint(srcDevice)
                || tunnelManager.isTunnelEndpoint(dstDevice);
        boolean tunnelEnabled = !isTunnelTraffic;

        // Validate that we have a destination known on the same island
        // Validate that the source and destination are not on the same 
        // switchport, otherwise the detination has be discovered on this
        // L2 domain.
        boolean on_same_island = false;
        boolean on_same_if = false;
        for (SwitchPort dstTuple : dstDaps) {
            long dstSwDPID = dstTuple.getSwitchDPID();
            Long dstIsland = topology.getL2DomainId(dstSwDPID);
            if ((dstIsland != null) && dstIsland.equals(pinCluster)) {
                on_same_island = true;
                if ((pinSwitchDPID == dstSwDPID) &&
                    (pinPort.shortValue() == dstTuple.getPort())) {
                    on_same_if = true;
                }
                break;
            }
        }

        if (!on_same_island) {
            if (log.isTraceEnabled()) {
                log.trace("No first hop island found for dest device: {}",
                          dstDevice);
            }
            discoverDstRoute(pi, sw, decision, cntx);
            return;
        }

        if (on_same_if) {
            if (log.isTraceEnabled()) {
                log.trace("NOP since both source {} and destination {} " + 
                        " are on the same switch {} and port {}",
                        new Object[] {srcDevice.toString(),
                                      dstDevice.toString(),
                                      HexString.toHexString(pinSwitchDPID),
                                      pinPort});
            }
            return;
        }

        // Install all the routes where both src and dst have attachment
        // points.  Since the lists are stored in sorted order we can traverse
        // the attachment points in O(m+n) time
        SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
        Map<Long, SwitchPort> srcMap = getSwitchPortMap(srcDaps, tunnelEnabled);
        Map<Long, SwitchPort> dstMap = getSwitchPortMap(dstDaps, tunnelEnabled);

        // If we are forwarding packets whose original destination was a
        // tunnel end-point, get the map of all the attachment points for the
        // original tunnel destination.
        IDevice origDstDevice = IDeviceService.fcStore.get(cntx,
                                        IDeviceService.CONTEXT_ORIG_DST_DEVICE);
        Map<Long, SwitchPort> origTunnDstMap = null;
        if (origDstDevice != null &&
            tunnelManager.isTunnelEndpoint(origDstDevice)) {
            SwitchPort[] origTunnDstDaps = origDstDevice.getAttachmentPoints();
            origTunnDstMap = getSwitchPortMap(origTunnDstDaps, tunnelEnabled);
        }

        for(long l2id: srcMap.keySet()) {
            SwitchPort srcDap = srcMap.get(l2id);
            SwitchPort dstDap = dstMap.get(l2id);
            if (dstDap == null) continue;
            if (srcDap.equals(dstDap)) continue;

            SwitchPort origTunnDap = null;
            if (origTunnDstMap != null) {
                origTunnDap = origTunnDstMap.get(l2id);
                if (origTunnDap == null) {
                    if (log.isTraceEnabled()) {
                        log.trace("Failed to find dap for {} on l2id {}",
                                  origDstDevice, l2id);
                    }
                    continue;
                }
            }

            Route route = null;
            NodePortTuple modifiedSrc;
            SwitchPort dstSwitchPort = null;
            long cookie = this.getHashByMac(dstDevice.getMACAddress());
            /*
             * Note I:
             * -------
             * If the packet was originally destined to a Tunnel loopback port,
             * the computation of getIncomingSwitchPort() is done against
             * the original tunnel destination.
             *
             * Why ?:
             * ------
             * in this case, it is *assumed* that the tunnel loopback port is
             * used as a next hop to reach hosts in the OF cluster from the
             * NOF domain. As a result, packets to the hosts in the OF cluster
             * from the NOF will only be addressed to the tunnel loopback
             * port mac address.
             *
             * As long as we ensure that the packet came in on the correct port
             * designated for the tunnel port's attachment point, we can
             * ensure correct packet forwarding semantics to "dst".
             *
             * Note II:
             * --------
             * Route Computation for this case cannot be procured
             * "out-of-box"(in a single call) from
             * RoutingEngine(backed by BetterTopology).
             *
             * Why?
             * ----
             * Say, we have 2 switches SW1 and SW2 both in same OF Cluster.
             * Both switches' P1 port is connected to the NOF. "dest" is on (SW2, P1).
             * If the packet came in on (SW1, P1) addressed to the MAC of the
             * TEP on SW2, IP of "dest", the route returned by
             * getRoute(SW1, P1, SW2, P2) would be (SW2, P1), (SW2, P2).
             * To workaround this, we compute the SW1 --> SW2 path and then
             * prepend (SW1, P1) and append (SW2, P2) to that path.
             * See getRouteInCluster()
             */
            if (origTunnDap != null) {
                modifiedSrc = topology.
                        getIncomingSwitchPort(srcDap.getSwitchDPID(),
                                              (short)srcDap.getPort(),
                                              origTunnDap.getSwitchDPID(),
                                              (short)origTunnDap.getPort(),
                                              tunnelEnabled);
                if (modifiedSrc == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("SourceDAP: {}, origTunnDap: null", srcDap);
                        log.debug("Dropping packet due to topology " +
                                  "restriction: srcDev: {}, srcDap: {}, " +
                                  "origDstDevice {}, origTunnDap: {}",
                                  new Object[]
                                    { srcDevice.getMACAddressString(),
                                      srcDap,
                                      origDstDevice.getMACAddressString(),
                                      origTunnDap } );
                    }
                    continue;
                }
                route = getRouteInCluster(modifiedSrc.getNodeId(),
                                          (short)modifiedSrc.getPortId(),
                                          dstDap.getSwitchDPID(),
                                          (short)dstDap.getPort(), cookie);
                dstSwitchPort = new SwitchPort(dstDap.getSwitchDPID(),
                                               (short)dstDap.getPort());
            } else {
                // The packet is going to be routed from srdDap.getSwitchPort() to
                // dstDap.getSwitchPort().  We need to validate this traffic.
                NodePortTuple modifiedDst;

                modifiedSrc = topology.
                    getIncomingSwitchPort(srcDap.getSwitchDPID(),
                                          (short)srcDap.getPort(),
                                          dstDap.getSwitchDPID(),
                                          (short)dstDap.getPort(),
                                          tunnelEnabled);

                modifiedDst = topology.
                    getOutgoingSwitchPort(srcDap.getSwitchDPID(),
                                          (short)srcDap.getPort(),
                                          dstDap.getSwitchDPID(),
                                          (short)dstDap.getPort(),
                                          tunnelEnabled);

                if (modifiedDst == null || modifiedSrc == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("SourceDAP: {}, DestDAP: {}", srcDap, dstDap);
                        log.debug("Modified SourceDAP: {}, Modified DestDAP: {}",
                                  modifiedSrc, modifiedDst);
                        log.debug("Dropping packet due to topology " +
                                  "restriction: srcDev: {}, srcDap: {}, " +
                                  "dstDev {}, dstDap: {}",
                                  new Object[]
                                    { srcDevice.getMACAddressString(),
                                      srcDap,
                                      dstDevice.getMACAddressString(),
                                      dstDap } );
                    }
                    continue;
                }

                // Rewrite the source and destination switch ports
                dstSwitchPort =
                    new SwitchPort(modifiedDst.getNodeId(),
                                   modifiedDst.getPortId());

                cookie = this.getHashByMac(dstDevice.getMACAddress());
                route = routingEngine.getRoute(modifiedSrc.getNodeId(),
                                               modifiedSrc.getPortId(),
                                               modifiedDst.getNodeId(),
                                               modifiedDst.getPortId(),
                                               cookie,
                                               tunnelEnabled);

                if (log.isTraceEnabled()) {
                    log.trace("SourceDAP: {}, DestDAP: {}", srcDap, dstDap);
                    log.trace("Modified SourceDAP: {}, Modified DestDAP: {}",
                              modifiedSrc, modifiedDst);
                    log.trace("Route = {}", route);
                }
            }

            if (route != null) {

                NodePortTuple pinNpt = new NodePortTuple(pinSwitchDPID,
                                                        pinPort.shortValue());
                // Make sure that if the packet-in switch port is an
                // attachment point port, then this port is present on the
                // route.  Otherwise, do not install the route.
                if (topology.isAttachmentPointPort(pinSwitchDPID,
                        pinPort.shortValue(),
                        tunnelEnabled) &&
                        !route.getPath().contains(pinNpt)){
                    if (log.isTraceEnabled()) {
                        log.trace("Dropping packet and not installing " +
                                "flow-mod as packet-in switchport {} is not " +
                                "present on route {}",
                                decision.getSourcePort(), route);
                    }
                    /*
                     * ARP for the destination IP on both the permitted
                     * broadcast and unicast ports, so that the destination can
                     * respond to the ARP and source transmits on the correct
                     * port to reach the destination IP
                     *
                     * Note: We inject on both the permitted broadcast and
                     * unicast ports. However, only one of them will be
                     * processed by ARP manager. See ArpManager.receive()
                     */
                    injectFakeArpOnAllowedIncomingUnicastNodePort(modifiedSrc,
                                                                  cntx, true);
                    injectFakeArpOnAllowedIncomingBroadcastNodePort(pi, sw,
                                                                    cntx, true);
                } else  {
                    if (log.isTraceEnabled()) {
                        log.trace("pushRoute pi={} piSwitch={} " +
                                "route={} destination={}",
                                new Object[] {pi, pinSwitch.getStringId(),
                                route, dstSwitchPort});
                    }

                    if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                        // Store the route for this cluster in the explain
                        // packet route object in the context
                        NetVirtExplainPacket.ExplainPktRoute.
                        ExplainPktAddRouteToContext(cntx, route, l2id,
                                srcDap, dstDap);
                    } else {
                        pushRewriteRoute(route,
                                decision.getSourceDevice(),
                                null,
                                pi,
                                pinSwitch.getId(),
                                appCookie,
                                decision.getWildcards(),
                                true,
                                false,
                                OFFlowMod.OFPFC_ADD,
                                tunnelEnabled,
                                cntx);
                    }
                }
            }
        }
        return;
    }

    /**
     * Computes the route between the given endpoints assuming
     * srcSwitchId and dstSwitchId are in the same OF Cluster.
     * The route is computed by computing the switch-switch
     * route from srcSwitchId to dstSwitchId and then prepending
     * (srcSwitchId, srcPort) and appending (dstSwitchId, dstPort) to get
     * the final route.
     * Note: The topology instance with tunnels included is consulted.
     * @param srcSwitchId
     * @param srcPort
     * @param dstSwitchId
     * @param dstPort
     * @param cookie
     * @return
     */
    private Route getRouteInCluster(long srcSwitchId, short srcPort,
                                    long dstSwitchId, short dstPort,
                                    long cookie) {
        Route route = null;
        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
        Route intraClusterRoute = null;

        if (srcSwitchId != dstSwitchId) {
            intraClusterRoute = routingEngine.getRoute(srcSwitchId,
                                                       dstSwitchId,
                                                       cookie,
                                                       true);
            if (intraClusterRoute == null) {
                if (log.isTraceEnabled()) {
                    log.trace("Failed to find intra-cluster route from " +
                               "{} --> {}", srcSwitchId, dstSwitchId);
                 }
                 return null;
             }
         }

         nptList.add(new NodePortTuple(srcSwitchId, srcPort));
         if (intraClusterRoute != null && intraClusterRoute.getPath() != null) {
             nptList.addAll(intraClusterRoute.getPath());
         }
         nptList.add(new NodePortTuple(dstSwitchId, dstPort));

         route = new Route(new RouteId(srcSwitchId, dstSwitchId), nptList);
         return route;
    }

    private Map<Long, SwitchPort> getSwitchPortMap(SwitchPort[] sp,
                                                   boolean tunnelEnabled) {
        Map<Long, SwitchPort> resultMap = new HashMap<Long, SwitchPort>();
        for(int i=0; i<sp.length; i++) {
            long l2id = topology.getL2DomainId(sp[i].getSwitchDPID(), tunnelEnabled);
            resultMap.put(l2id, sp[i]);
        }
        return resultMap;
    }

    /**
     *
     * @param applInstName application instance name
     * @param ofmWithSwDpid open-flow match
     * @param swClusterId if not null then the flow-mods only in this cluster
     *                    are reprogrammed
     */
    @Override
    @LogMessageDoc(level="WARN",
            message="Could not push reconciled flow with unknown source {dpid}",
            explanation="There was a flow on the specified switch for which " +
                    "no associated source device could be found",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    public void doPushReconciledFlowMod(OFMatchReconcile ofmRc) {
        if (ofmRc == null) return;
        
        OFMatchWithSwDpid ofmWithSwDpid = ofmRc.ofmWithSwDpid;
        
        if (log.isDebugEnabled())
            log.debug("doPushReconciledFlowMod: OFMatchReconcile = {}", ofmRc);
        
        long macAddress = Ethernet.toLong(ofmWithSwDpid.getOfMatch().getDataLayerSource());
        IDevice srcDevice = 
                deviceManager.findDevice(macAddress,
                                         ofmWithSwDpid.getOfMatch().getDataLayerVirtualLan(), 
                                         ofmWithSwDpid.getOfMatch().getNetworkSource(), 
                                         ofmWithSwDpid.getSwitchDataPathId(), 
                                         (int)ofmWithSwDpid.getOfMatch().getInputPort()); 
        if (srcDevice == null) {
            log.warn("Could not push reconciled flow with unknown source {}",
                     ofmWithSwDpid);
            return;
        }
        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
        IOFSwitch srcSwitch = switches.get(ofmWithSwDpid.getSwitchDataPathId());
        if (srcSwitch == null) return;

        /** 
         * TODO Flow cache should implement passing the packet/flow mod through
         * the topology manager to compute whether tunnels are allowed or not.
         */
        IOFSwitch pinSwitch = controllerProvider.getSwitches().get(ofmWithSwDpid.getSwitchDataPathId());
        Short pinPort = ofmWithSwDpid.getOfMatch().getInputPort();
        NodePortTuple pinNpt = new NodePortTuple(srcSwitch.getId(),
                ofmWithSwDpid.getOfMatch().getInputPort());

        IRoutingDecision decision = null;
        IDevice dstDevice = null;
        IDevice origDstDevice = null;
        if (ofmRc.cntx != null) {
             decision = IRoutingDecision.rtStore.get(ofmRc.cntx,
                                                  IRoutingDecision.CONTEXT_DECISION);
             if (decision != null && decision.getDestinationDevices().size() > 0) {
                 dstDevice = decision.getDestinationDevices().get(0);
             }
             origDstDevice = IDeviceService.fcStore.get(ofmRc.cntx,
                                       IDeviceService.CONTEXT_ORIG_DST_DEVICE);
        }
        
        if (dstDevice == null) {
            macAddress = Ethernet.toLong(ofmWithSwDpid.getOfMatch().getDataLayerDestination());
            dstDevice = deviceManager.findClassDevice(srcDevice.getEntityClass(),
                                                 macAddress,
                                                 ofmWithSwDpid.getOfMatch().getDataLayerVirtualLan(),
                                                 ofmWithSwDpid.getOfMatch().getNetworkDestination());
        }
        
        if (dstDevice == null) {
            if (log.isDebugEnabled()) {
                log.debug("Delete flow to unknown destination {}",
                     HexString.toHexString(ofmRc.ofmWithSwDpid.getOfMatch().getDataLayerDestination()));
            }
            doDeleteReconciledFlowMod(ofmRc);
            return;
        }

        boolean isTunnelTraffic = tunnelManager.isTunnelEndpoint(srcDevice)
                || tunnelManager.isTunnelEndpoint(dstDevice);
        boolean tunnelEnabled = !isTunnelTraffic;

        //Long swClusterId = topology.getSwitchClusterId(srcSwitch.getId());
        Long swClusterId = topology.getL2DomainId(srcSwitch.getId(), tunnelEnabled);

        // Install all the routes where both src and dst have attachment points
        // Since the lists are stored in sorted order we can traverse the
        // attachment points in O(m+n) time
        SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
        SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();

        // If destination device doesn't have any attachment point, drop the flow.
        if (dstDaps == null || dstDaps.length == 0) {
            if (log.isDebugEnabled()) {
                log.debug("delete flow to unknow destination, {} from {}",
                          srcDevice.getMACAddressString(),
                          dstDevice.getMACAddressString());
            }
            doDeleteReconciledFlowMod(ofmRc);
            return;
        }

        // If we are forwarding packets whose original destination was a
        // tunnel end-point, get the map of all the attachment points for the
        // original tunnel destination.
        Map<Long, SwitchPort> origTunnDstMap = null;
        if (origDstDevice != null &&
            tunnelManager.isTunnelEndpoint(origDstDevice)) {
            SwitchPort[] origTunnDstDaps = origDstDevice.getAttachmentPoints();
            if (origTunnDstDaps == null || origTunnDstDaps.length == 0) {
                if (log.isDebugEnabled()) {
                    log.debug("delete flow via unknow tunn dest {} from {}",
                              srcDevice.getMACAddressString(),
                              origDstDevice.getMACAddressString());
                }
                doDeleteReconciledFlowMod(ofmRc);
                return;
            }
            origTunnDstMap = getSwitchPortMap(origTunnDstDaps, tunnelEnabled);
        }

        Map<Long, SwitchPort> srcMap = getSwitchPortMap(srcDaps, tunnelEnabled);
        Map<Long, SwitchPort> dstMap = getSwitchPortMap(dstDaps, tunnelEnabled);

        for(long l2id: srcMap.keySet()) {
            SwitchPort srcDap = srcMap.get(l2id);
            SwitchPort dstDap = dstMap.get(l2id);
            if (dstDap == null) continue;
            if (srcDap.equals(dstDap)) continue;

            IOFSwitch srcSw = switches.get(srcDap.getSwitchDPID());
            IOFSwitch dstSw = switches.get(dstDap.getSwitchDPID());
            if (srcSw == null || dstSw == null)
                continue;

            SwitchPort origTunnDap = null;
            if (origTunnDstMap != null) {
                origTunnDap = origTunnDstMap.get(l2id);
                if (origTunnDap == null) {
                    if (log.isTraceEnabled()) {
                        log.trace("Failed to find dap for {} on l2id {}",
                                  origDstDevice, l2id);
                    }
                    continue;
                }
            }

            long cookie = this.getHashByMac(dstDevice.getMACAddress());
            Route route = null;
            NodePortTuple modifiedSrc = null;
            if (origTunnDap != null) {
                modifiedSrc = topology.
                        getIncomingSwitchPort(srcDap.getSwitchDPID(),
                                              (short)srcDap.getPort(),
                                              origTunnDap.getSwitchDPID(),
                                              (short)origTunnDap.getPort(),
                                              tunnelEnabled);
            } else {
                modifiedSrc =
                        topology.
                        getIncomingSwitchPort(srcDap.getSwitchDPID(),
                                              (short)srcDap.getPort(),
                                              dstDap.getSwitchDPID(),
                                              (short)dstDap.getPort(),
                                              tunnelEnabled);
            }
            NodePortTuple modifiedDst = topology.getOutgoingSwitchPort(
                                              srcDap.getSwitchDPID(),
                                              (short)srcDap.getPort(),
                                              dstDap.getSwitchDPID(),
                                              (short)dstDap.getPort(),
                                              tunnelEnabled);

            if (modifiedDst == null || modifiedSrc == null) {
                //  This packet should be dropped.
                if (log.isDebugEnabled()) {
                    log.debug("Delete the flow due to topology " +
                              "restriction: srcDap: {}, dstDap: {}",
                              srcDap, dstDap);
                }
                doDeleteReconciledFlowMod(ofmRc);
                continue;
            }

            // if the packet-in switch and the modified switch are
            // in the same cluster, then verify both of them match.
            // the packet-in switch port must be either internal
            // or if external, it should match the modifiedSrc
            if (topology.getL2DomainId(pinSwitch.getId(), tunnelEnabled) ==
                    topology.getL2DomainId(modifiedSrc.getNodeId(), tunnelEnabled) &&
                    topology.isAttachmentPointPort(pinSwitch.getId(), pinPort, tunnelEnabled) == true &&
                    pinNpt.equals(modifiedSrc) == false) {
                if (log.isDebugEnabled()) {
                    log.debug("Delete the flow as packet-in comes " +
                            "from external switch port that's not " +
                            "allowed for this communication: " +
                            "srcDev: {}, srcDap: {}, " +
                            "dstDev {}, dstDap: {}",
                            new Object[]
                                    { srcDevice.getMACAddressString(),
                                      srcDap,
                                      dstDevice.getMACAddressString(),
                                      dstDap } );
                }
                doDeleteReconciledFlowMod(ofmRc);
                continue;
            }

            // Rewrite the source and destination switch ports
            srcSw = switches.get(modifiedSrc.getNodeId());
            dstSw = switches.get(modifiedDst.getNodeId());

            if (origTunnDap != null) {
                route = getRouteInCluster(modifiedSrc.getNodeId(),
                                          (short)modifiedSrc.getPortId(),
                                          modifiedDst.getNodeId(),
                                          (short)modifiedDst.getPortId(),
                                          cookie);
            } else {
                route = routingEngine.getRoute(modifiedSrc.getNodeId(),
                                               modifiedSrc.getPortId(),
                                               modifiedDst.getNodeId(),
                                               modifiedDst.getPortId(),
                                               cookie,
                                               tunnelEnabled);
            }
            if (route != null) {
                /*
                 * pi and listener context are set to null
                 * command is set to MODIFY so that the packet and byte
                 * counters are preserved after the flow has been
                 * rerouted.
                 * Do flush is set to true so that the reconciled
                 * flow mod is pushed to the switch immediately. Also
                 * reconciled flows are pushed via a different thread,
                 * not the one that handled the corresponding packet in
                 * or the flow reconcile trigger event.
                 */
                if (log.isDebugEnabled()) {
                    log.debug("route: {}", route);
                }
                pushRewriteRoute(route,
                                 srcDevice,
                                 ofmWithSwDpid.getOfMatch(),
                                 null,
                                 pinSwitch.getId(),
                                 appCookie,
                                 ofmWithSwDpid.getOfMatch().getWildcards(),
                                 true,
                                 true,
                                 OFFlowMod.OFPFC_MODIFY,
                                 tunnelEnabled,
                                 ofmRc.cntx);
            } else {
                /**
                 * delete the flow if no route between two devices in a L2 domain.
                 * However, there should never be a case for null route if the two devices
                 * are in the same L2 domain.
                 */
                if ((swClusterId != null) &&
                        (swClusterId.equals(l2id))) {
                    doDeleteReconciledFlowMod(ofmRc);
                    break;
                }
            }
        }
        return;
    }

    protected static final String ioerrorFlowMod =
            "Could not insert a flow mod because of an I/O error.";
    
    @LogMessageDoc(level="ERROR",
            message="Failure writing deny flow mod",
            explanation=ioerrorFlowMod,
            recommendation=LogMessageDoc.CHECK_SWITCH)
    protected void doDropFlow(OFPacketIn pi, 
                              IRoutingDecision decision, 
                              ListenerContext cntx) {        
        if (!validateDecision(decision)) {
            return;
        }

        // Initialize data from decision (validation above ensures that they 
        // all not null)
        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
        IOFSwitch srcSwitch = 
                switches.get(decision.getSourcePort().getSwitchDPID());

        if (log.isTraceEnabled()) {
            log.trace("doDropFlow pi={} decision={} srcSwitch={}",
                    new Object[] { pi, decision, srcSwitch });
        }

        if (srcSwitch == null)
            return;

        // Create flow-mod based on packet-in and src-switch
        OFFlowMod fm = 
                (OFFlowMod) controllerProvider.getOFMessageFactory().
                    getMessage(OFType.FLOW_MOD);
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        List<OFAction> actions = new ArrayList<OFAction>();
        match = wildcard(match, srcSwitch, decision.getWildcards());
        fm.setCookie(appCookie)
        .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setHardTimeout(decision.getHardTimeout())
        .setPriority(accessPriority)
        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
        .setMatch(match)
        .setActions(actions)
        .setLengthU(OFFlowMod.MINIMUM_LENGTH); // +OFActionOutput.MINIMUM_LENGTH);
        fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
        try {
            if (log.isTraceEnabled()) {
                log.trace("write drop flow-mod srcSwitch={} match={} " + 
                          "pi={} flow-mod={}",
                          new Object[] {srcSwitch, match, pi, fm});
            }
            counterStore.updatePktOutFMCounterStoreLocal(srcSwitch, fm);
            srcSwitch.write(fm, cntx);
            OFMatchWithSwDpid ofmWithSwDpid = new OFMatchWithSwDpid(match, srcSwitch.getId());
            betterFlowCacheMgr.addFlow(cntx, ofmWithSwDpid, appCookie,
                    decision.getSourcePort(), fm.getPriority(),
                    FlowCacheObj.FCActionDENY);
        }
        catch (IOException e) {
            log.error("Failure writing deny flow mod", e);
        }
        return;
    }

    @Override
    @LogMessageDoc(level="WARN",
        message="No matching switch with dpid {dpid} when trying to" +
                " install a DROP flow.",
        explanation="The switch has likely disconnected from the controller.",
        recommendation=LogMessageDoc.CHECK_SWITCH)
    public void doDropReconciledFlowMod(OFMatchReconcile ofmRc) {
        // Get the source switch
        IOFSwitch swsrcSwitch = controllerProvider.getSwitches().
                get(ofmRc.ofmWithSwDpid.getSwitchDataPathId());
        
        // If the switch is not connected, NO-OP
        if (swsrcSwitch == null) {
            log.warn("No matching switch with dpid {} when trying to install a DROP flow.",
                        HexString.toHexString(ofmRc.ofmWithSwDpid.getSwitchDataPathId()));
            return;
        }
        
        /* Set the action to drop at the source switch for this
         * flow-mod. If we delete the flow-mod instead then
         * controller may get a large number of packet-ins.
         */
        OFFlowMod fm = (OFFlowMod) controllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        List<OFAction> actions = new ArrayList<OFAction>();
        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
        .setPriority(accessPriority)
        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
        .setCookie(ofmRc.cookie)
        .setIdleTimeout((short)5)
        .setMatch(ofmRc.ofmWithSwDpid.getOfMatch())
        .setActions(actions)
        .setLengthU(OFFlowMod.MINIMUM_LENGTH);
        fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
        fm.setCommand(OFFlowMod.OFPFC_MODIFY);
        /* Request flow mod */
        try {
            swsrcSwitch.write(fm, null);
            swsrcSwitch.flush();
            if (log.isTraceEnabled()) {
                log.trace("Programmed drop flow-mod {} at {}", fm, swsrcSwitch);
            }
            betterFlowCacheMgr.addFlow(
                    ofmRc.appInstName, ofmRc.ofmWithSwDpid, ofmRc.cookie,
                    swsrcSwitch.getId(),
                    ofmRc.ofmWithSwDpid.getOfMatch().getInputPort(),
                    fm.getPriority(), FlowCacheObj.FCActionDENY);
        } catch (IOException e) {
            log.error("Failure writing deny flow mod", e);
        }
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
            message="No matching switch with dpid {dpid} when trying " +
                    "to delete a flow.",
            explanation="The switch has likely disconnected from the controller.",
            recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
            message="Failed to delete flow mod: {flow} at {dpid}",
            explanation=ioerrorFlowMod,
            recommendation=LogMessageDoc.CHECK_SWITCH)
    })
    public void doDeleteReconciledFlowMod(OFMatchReconcile ofmRc) {
        /* Delete the drop flow mod since the source and
         * destination are now in the same NetVirt
         */
        /* Get the source switch */
        IOFSwitch swsrcSwitch = controllerProvider.getSwitches().
                get(ofmRc.ofmWithSwDpid.getSwitchDataPathId());

        // If the switch is not connected, NO-OP
        if (swsrcSwitch == null) {
            log.warn("No matching switch with dpid {} when trying to delete a flow.",
                        HexString.toHexString(ofmRc.ofmWithSwDpid.getSwitchDataPathId()));
            return;
        }
        
        /* Set the action to drop at the source switch for this
         * flow-mod. If we delete the flow-mod instead then
         * controller may get a large number of packet-ins.
         */
        OFFlowMod fm = (OFFlowMod) controllerProvider.
                getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
        .setCookie(ofmRc.cookie)
        .setPriority(ofmRc.priority)
        .setMatch(ofmRc.ofmWithSwDpid.getOfMatch())
        .setOutPort(OFPort.OFPP_NONE)
        .setCommand(OFFlowMod.OFPFC_DELETE)
        .setLengthU(OFFlowMod.MINIMUM_LENGTH);
        try {
            swsrcSwitch.write(fm, null);
            swsrcSwitch.flush();
            if (log.isDebugEnabled()) {
                log.debug("Deleted flow-mod {} at {}", fm, swsrcSwitch);
            }
        } catch (IOException e) {
            log.error("Failed to delete flow mod: {} at {}",
                    fm, swsrcSwitch);
        }
    }

    /** A helper function. 
     * @param interfaces a map that maps switches & vlan to the set 
     * of ports on this switch for which flood/multicast should use this
     * vlan for outgoing packets. 
     * @param sw the switch 
     * @param vlan the vlan
     * @param port the port number to include. This port will be added to the
     * list of ports
     */
    private void addPortToSwitchVlanMap(
            HashMap <IOFSwitch, HashMap<Short,HashSet<Integer>>> interfaces,
            IOFSwitch sw, Short vlan, Integer port) {
        HashMap<Short, HashSet<Integer>> vlanMap;
        vlanMap = interfaces.get(sw);
        if (vlanMap == null) {
            vlanMap = new HashMap<Short,HashSet<Integer>>();
            interfaces.put(sw, vlanMap);
        }
        HashSet<Integer> ports;
        ports = vlanMap.get(vlan);
        if (ports == null) {
            ports = new HashSet<Integer>();
            vlanMap.put(vlan, ports);
        }
        ports.add(port);
    }

    protected void doMulticast(OFPacketIn pi, IRoutingDecision decision, ListenerContext cntx) {
        if (!validateDecision(decision)) {
            return;
        }
        
        boolean tunnelEnabled = 
                !tunnelManager.isTunnelEndpoint(decision.getSourceDevice());
        

        // Check if the payload has the entire packet.  Otherwise, just drop
        // the packet.
        Ethernet eth = IControllerService.
                bcStore.get(cntx, 
                            IControllerService.CONTEXT_PI_PAYLOAD);
        if (eth.getPayload() instanceof IPv4) {
            IPv4 packet = (IPv4) eth.getPayload();
            //ignore the packet if the packet is truncated.
            if (packet.isTruncated()) {
                numberOfTruncatedPacketsSeen++;
                return;
            }
        }

        // Initialize data from decision (validation above ensures that they
        // all not null/empty) 
        SwitchPort sourceswt = decision.getSourcePort();
        long pinSw = sourceswt.getSwitchDPID();
        short pinPort = (short)sourceswt.getPort();
        long clusterid = topology.getOpenflowDomainId(pinSw, tunnelEnabled);

        if (topology.isIncomingBroadcastAllowed(pinSw, pinPort, tunnelEnabled)== false) {
            if (log.isDebugEnabled()) {
                log.debug("doMulticast, drop packet, pi={}, as input port " +
                        "is not enabled for incoming broadcast, " +
                        "packet-in sw={}, packet-in port = {}",
                        new Object[] {pi, pinSw, pinPort});
            }
            return;
        }


        // We add the switch/ports we want to include in the multicast to this
        // map. We first index by the switch, we then index by the 
        // VLAN-tag to use. The value is the set of ports on this switch for
        // which we should use the VLAN tag when sending.  
        // This allows us to use multi-action packet outs per switch
        HashMap <IOFSwitch, 
                 HashMap<Short,HashSet<Integer>>> interfaces = 
                new HashMap <IOFSwitch,HashMap<Short,HashSet<Integer>>> ();

        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();

        // The vlan the current address space uses for transit
        String sourceAddressSpace = decision.getSourceDevice()
                .getEntityClass().getName();
        List<SwitchPort> dstInterfaces = null;                
        dstInterfaces = decision.getMulticastInterfaces();

        if (dstInterfaces != null) {
            for (SwitchPort swt : dstInterfaces) {
                // get the modified tunnel port.
                // this should also ensure that only ports within
                // the same cluster are considered.
                NodePortTuple modifiedNpt =
                        topology.getOutgoingSwitchPort(pinSw, pinPort,
                                                       swt.getSwitchDPID(), 
                                                       (short)swt.getPort(), 
                                                       tunnelEnabled);
                if (modifiedNpt == null) continue;
    
                swt = new SwitchPort(modifiedNpt.getNodeId(), 
                                     modifiedNpt.getPortId());
                IOFSwitch dstSw = switches.get(swt.getSwitchDPID());
    
                if ((topology.getOpenflowDomainId(swt.getSwitchDPID(),
                                                  tunnelEnabled) == 
                                                  clusterid) &&
                                                  (!sourceswt.equals(swt)))
                {
                    // it is not the same switch port tuple as the source.
    
                    Short egressVlan = rewriteService.getSwitchPortVlanMode(swt,
                            sourceAddressSpace, eth.getVlanID(), tunnelEnabled);
                    if (egressVlan==null) {
                        if(log.isDebugEnabled()) {
                            log.debug("Output to port {} forbidden for " +
                                      "address-sapce {}, currentVlan {} "
                                    + "(pi={} decision={})",
                                    new Object[] { swt, sourceAddressSpace, 
                                                   eth.getVlanID(), pi,
                                                   decision } 
                                    );
                        }
                        continue;
                    }
                    
                    // TODO: This might be too much logging even for trace...
                    if (log.isTraceEnabled()) {
                        log.trace("doMulticast: adding switch-port-" + 
                                "tuple= {} with vlan {} (pi={} decision={})",
                                new Object[] { swt, egressVlan, pi, decision });
                    }
                    addPortToSwitchVlanMap(interfaces, 
                                           dstSw, 
                                           egressVlan,
                                           swt.getPort());
                }
            }
        }

        // Modified for multi-action packet out.
        List<IDevice> dstDevices = 
                decision.getDestinationDevices();
        if (dstDevices != null) {
            for (IDevice d : dstDevices) {
                SwitchPort[] daps = d.getAttachmentPoints();
                if (daps == null) continue;

                for (SwitchPort swt : daps) {
                    // Ignore this switch port if it is not in the same
                    // L2 domain.
                    if (!topology.inSameL2Domain(swt.getSwitchDPID(),
                                                 sourceswt.getSwitchDPID()))
                        continue;

                    NodePortTuple npt = 
                            topology.getAllowedOutgoingBroadcastPort(sourceswt.getSwitchDPID(),
                                                                   (short)sourceswt.getPort(),
                                                                   swt.getSwitchDPID(),
                                                                   (short)swt.getPort(),
                                                                   tunnelEnabled);

                    if (npt == null) continue;

                    if (topology.isInSameBroadcastDomain(sourceswt.getSwitchDPID(),
                                                         (short)sourceswt.getPort(),
                                                         npt.getNodeId(), npt.getPortId(),
                                                         tunnelEnabled)) {
                        continue;
                    }

                    // dstSw is the switch to which the packet must be
                    // sent to.
                    IOFSwitch dstSw = switches.get(npt.getNodeId());
                    if (dstSw == null) continue;

                    // it is not the same switch port tuple as the
                    // source.

                    Short egressVlan = null;
                    egressVlan = rewriteService.getSwitchPortVlanMode(swt,
                            sourceAddressSpace, eth.getVlanID(), tunnelEnabled);
                    if (egressVlan==null) {
                        if(log.isDebugEnabled()) {
                            log.debug("Output to port {} forbidden for " +
                                    "address-sapce {}, currentVlan {} "
                                  + "(pi={} decision={})",
                                  new Object[] { swt, sourceAddressSpace, 
                                                 eth.getVlanID(), pi,
                                                 decision } 
                                  );
                        }
                        continue;
                    }
                    if (log.isTraceEnabled()) {
                        // TODO: This might be too much logging even for trace...
                        log.trace("doMulticast: adding switch-port-" + 
                                "tuple= {} with vlan {} (pi={} decision={})",
                                new Object[] { swt, egressVlan, pi, decision });
                    }

                    addPortToSwitchVlanMap(interfaces, 
                                           dstSw, 
                                           egressVlan,
                                           (int)npt.getPortId());

                } 
            }
        }


        //----------------------
        // rewrite packet
        //----------------------
        eth = (Ethernet)eth.clone();
        Long newDstMac = rewriteService.getFinalIngressDstMac(cntx);
        if (newDstMac != null)
            eth.setDestinationMACAddress(Ethernet.toByteArray(newDstMac));
        Long newSrcMac = rewriteService.getFinalEgressSrcMac(cntx);
        if (newSrcMac != null)
            eth.setSourceMACAddress(Ethernet.toByteArray(newSrcMac));
        Integer decrement = rewriteService.getTtlDecrement(cntx);
        if (decrement != null) {
            if (decrementTtl(eth, decrement) == false) {
                // TTL expired
                if (log.isTraceEnabled()) {
                    log.trace("Dropping packet from {}: TTL expired",
                              decision.getSourceDevice());
                }
                return; 
            }
        }
        
        //----------------------
        // Push the packet. 
        //----------------------
        for (IOFSwitch sw: interfaces.keySet()) {
            HashMap<Short, HashSet<Integer>> vlanMap = interfaces.get(sw);
            for (Short vlan: vlanMap.keySet()) {
                HashSet<Integer> al = vlanMap.get(vlan);
                eth.setVlanID(vlan);
                pi.setPacketData(eth.serialize());
                packetOutMultiPort(eth, sw, OFPort.OFPP_NONE.getValue(), al, cntx);
            }
        }
    }
    
    /**
     * Creates a OFPacketOut with the OFPacketIn data that is flooded on all 
     * ports unless the port is blocked, in which case the packet will be 
     * dropped.
     * @param pi The OFPacketIn that came to the switch
     * @param decision The Forwarding decision
     * @param cntx The ListenerContext associated with this OFPacketIn
     */
    protected void doFlood(OFPacketIn pi, 
                           IRoutingDecision decision, 
                           ListenerContext cntx) {
        if (log.isTraceEnabled()) {
            log.trace("doFlood: pi={} decision={}", pi, decision);
        }
        // Validation not required since it is called from a validated context
        //if (!validateDecision(decision)) {
        //    return;
        //}
        boolean tunnelEnabled = 
                !tunnelManager.isTunnelEndpoint(decision.getSourceDevice());

        // Check if the target IP address is in the tunnel subnet or not.
        Ethernet ethtry = IControllerService.bcStore
                .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);
        if (tunnelEnabled && ethtry.getEtherType() == Ethernet.TYPE_ARP) {
            ARP a = (ARP) ethtry.getPayload();
            int x = IPv4.toIPv4Address(a.getTargetProtocolAddress());
            if (tunnelManager.getSwitchDpid(x) != null) {
                tunnelEnabled = false;
            }
        }

        // Initialize data from decision (validation above ensures that they all not null/empty)
        SwitchPort pinSwitchPort = decision.getSourcePort();
        if (topology.isIncomingBroadcastAllowed(pinSwitchPort.getSwitchDPID(), 
                                                (short)pinSwitchPort.getPort(),
                                                tunnelEnabled) == false) {
            if (log.isTraceEnabled()) {
                log.trace("doFlood: drop broadcast packet, pi={}, " +
                          "from a blocked port, srcSwitchTuple={}, tunnelEnabled={}",
                          new Object[] {pi, pinSwitchPort, tunnelEnabled});
            }
            return;
        }

        IOFSwitch pinIofSwitch = controllerProvider.getSwitches().
                get(pinSwitchPort.getSwitchDPID());
        long pinSwitch = pinSwitchPort.getSwitchDPID();
        short pinPort = (short) pinSwitchPort.getPort();

        // If the packet was flooded recently on the switch, 
        // don't do it again
        if (isInSwitchBroadcastCache(pinIofSwitch, pi, cntx)) {
            if (log.isTraceEnabled()) {
                log.trace("doFlood: drop broadcast packet, pi={}, " +
                          "since it is in switchBroadcastCache", pi);
            }
            return;
        }

        // Get the attachment point of the source in the L2 domain.
        long apSwitch = 0;
        short apPort = 0;
        IDevice srcDevice = decision.getSourceDevice();
        for(SwitchPort dap: srcDevice.getAttachmentPoints()) {
            apSwitch = dap.getSwitchDPID();
            apPort = (short)dap.getPort();
            if (topology.getL2DomainId(apSwitch, tunnelEnabled) == 
                    topology.getL2DomainId(pinSwitch, tunnelEnabled))
                break;
        }

        // drop the packet if attachment point for a source is not available
        // in the cluster.
        if (apSwitch == 0 && apPort == 0) {
            if (log.isTraceEnabled()) {
                log.trace("doFlood: Dropping packet as source attachment point is " +
                        "not available in the cluster. pi={}", pi);
            }
            return;
        }

        // Drop the packet if the packet-in switch port is inconsistent with
        // the attachment point switch port.
        if (topology.isConsistent(apSwitch, apPort, pinSwitch, pinPort,
                                  tunnelEnabled) == false) {
            if (log.isTraceEnabled()) {
                log.trace("doFlood: Dropping packet as packet-in port is " +
                        "inconsistent with attachment point. pi={}", pi);
            }
            return;
        }

        // The packet is now eligible for forwarding to all nodes.
        // We will flood the packet to all the switches in the openflow
        // domain.

        // Get all the switches in that openflow domain.
        Set<Long> switchesInOpenflowDomain = 
                topology.getSwitchesInOpenflowDomain(pinSwitch, tunnelEnabled);

        if (log.isTraceEnabled()) {
            Set<String> switches = new HashSet<String>();
            for (Long dpid : switchesInOpenflowDomain) {
                switches.add(HexString.toHexString(dpid));
            }
            log.trace("doFlood: OF switches {} ",
                    Arrays.toString(switches.toArray()));
        }

        for (long sw: switchesInOpenflowDomain) {
            IOFSwitch iofSwitch = controllerProvider.getSwitches().get(sw);
            if (iofSwitch == null) continue;

            // Get the list of switch ports. and compute the multi-action packet-out.
            Set<Short> resultPorts = new HashSet<Short>();
            resultPorts.addAll(topology.getPorts(sw));
            // remove internal links
            Set<Short> portsWithLinks = topology.getPortsWithLinks(sw);
            if (portsWithLinks != null && !portsWithLinks.isEmpty())
                resultPorts.removeAll(portsWithLinks);
            // add the internal links in the broadcast tree
            // The broadcast ports computed here will only be to the
            // external broadcast domains.
            resultPorts.addAll(topology.getBroadcastPorts(sw,
                                                          apSwitch, apPort,
                                                          tunnelEnabled));

            // TODO: When tunnel scalability is completed, we need to
            // remove tunnel ports from the list as we will not find
            // links through the tunnel ports.
            Short tunnelPort= tunnelManager.getTunnelPortNumber(sw);
            if (tunnelPort != null)
                resultPorts.remove(tunnelPort);

            // Remove the incoming port on the packet-in switch
            if (sw == pinSwitch)
                resultPorts.remove(Short.valueOf((short)pinSwitchPort.getPort()));

            if (resultPorts.isEmpty()) {
                if (log.isTraceEnabled()) {
                    log.trace("doFlood: Dropping flood packet on switch {} as " +
                            "resulting port set is empty.", HexString.toHexString(sw));
                }
                continue;
            }

            Ethernet eth = (Ethernet)IControllerService.bcStore
                    .get(cntx, IControllerService.CONTEXT_PI_PAYLOAD)
                    .clone();
            short origVlan = eth.getVlanID();

            if (log.isTraceEnabled()) {
                log.trace("doFlood: resultPorts {} ", Arrays.toString(resultPorts.toArray()));
            }
            // We need to send a multi-action packet-out to only the ports in resultPorts.

            // Separate output ports by the VLAN we need to use 
            HashMap<Short,Set<Integer>> perVlanPorts =  
                    new HashMap<Short,Set<Integer>>();
            String sourceAddressSpace = srcDevice.getEntityClass().getName();
            for (Short port: resultPorts) {
                Short egressVlan;
                SwitchPort swp = new SwitchPort(sw, port);
                egressVlan = rewriteService.getSwitchPortVlanMode(swp,
                                                                  sourceAddressSpace, origVlan, tunnelEnabled);
                if (egressVlan==null) {
                    if (log.isDebugEnabled()) {
                        Short transportVlan = rewriteService.getTransportVlan(cntx);
                        log.debug("doFlood: Transport vlan {} is forbidden for "
                                + "outgoing port {} (pi={} decision={})",
                                new Object[] { transportVlan, swp, pi,
                                               decision } 
                                );
                    }
                    continue;
                }
                Set<Integer> curVlanPorts = perVlanPorts.get(egressVlan);
                if (curVlanPorts == null) {
                    curVlanPorts = new HashSet<Integer>();
                    perVlanPorts.put(egressVlan, curVlanPorts);
                }

                if (log.isTraceEnabled()) {
                    log.trace("doFlood: Add port {}/{} to vlan {})",
                            new Object[] { swp, pi, egressVlan });
                }
                curVlanPorts.add(port.intValue());
            }

            //----------------------
            // Packet rewrites
            //----------------------
            Long newDstMac = rewriteService.getFinalIngressDstMac(cntx);
            if (newDstMac != null)
                eth.setDestinationMACAddress(Ethernet.toByteArray(newDstMac)); 
            Long newSrcMac = rewriteService.getFinalEgressSrcMac(cntx);
            if (newSrcMac != null)
                eth.setSourceMACAddress(Ethernet.toByteArray(newSrcMac));
            Integer decrement = rewriteService.getTtlDecrement(cntx);
            if (decrement != null) {
                if (decrementTtl(eth, decrement) == false) {
                    // TTL expired
                    if (log.isTraceEnabled()) {
                        log.trace("doFlood: Dropping packet from {}: TTL expired",
                                  srcDevice);
                    }
                    return; 
                }
            }
            
            //----------------------
            // Push the packet
            //----------------------
            for (Short vlan: perVlanPorts.keySet()) {
                Set<Integer> al = perVlanPorts.get(vlan);
                eth.setVlanID(vlan);
                pi.setPacketData(eth.serialize());
                // TODO: we could use the buffer-id for unmodified packets
                // but packetOutMultiPort doesn't support it. 
                short inPort;
                if (pinSwitch == sw) {
                    inPort = pi.getInPort();
                } else {
                    inPort = OFPort.OFPP_NONE.getValue();
                }
                packetOutMultiPort(eth, iofSwitch, inPort, al, cntx);
            }
        }
        return;
    }

    /**
     * Detects if the traffic is a tunneled traffic or not.  The way
     * we identify is to check if the traffic originates or is destined
     * to a tunnel loopback port.  If both are true, then it is a tunneled
     * traffic.  As in this case, we would push both first and last flow-mods
     *
     * @param firstSwp
     * @param lastSwp
     * @param srcIPAddress
     * @param dstIPAddress
     */
    private void detectTunnelTraffic(NodePortTuple firstSwp,
                                     NodePortTuple lastSwp,
                                     Integer srcIPAddress,
                                     Integer dstIPAddress) {

        if (tunnelManager == null) return;

        long firstSw = firstSwp.getNodeId();
        short firstPort = firstSwp.getPortId();
        long lastSw = lastSwp.getNodeId();
        short lastPort = lastSwp.getPortId();

        Short firstLoopback = tunnelManager.getTunnelLoopbackPort(firstSw);
        Short lastLoopback = tunnelManager.getTunnelLoopbackPort(lastSw);

        // The first hop switchport has to be tunnel loopback port
        if (firstLoopback != null && firstLoopback.equals(firstPort)) {
            if (lastLoopback != null && lastLoopback.equals(lastPort)) {
                // both first and last hop are to loopback
                // We don't need to check this case, here as we are
                // already sending the flowmods within the same
                // L2 domain.
                // betterTopology.detectTunnelSource(firstSw, lastSw);
                // betterTopology.detectTunnelDestination(firstSw, lastSw);
            } else if (dstIPAddress != null) {
                // The first hop is a tunnel loopback, but last hop is
                // not.  Check if the destination
                Long dstSw = tunnelManager.getSwitchDpid(dstIPAddress);
                if (dstSw != null) {
                    betterTopology.detectTunnelSource(firstSw, dstSw);
                }
            }
        } else if (srcIPAddress != null) {
            // The first hop is not a tunnel loopback.
            // If the last hop is a tunnel loopback and the srcIP address
            // belongs to a tunnel port, then tunnel destination is detected.
            if (lastLoopback != null && lastLoopback.equals(lastPort)) {
                Long srcSw = tunnelManager.getSwitchDpid(srcIPAddress);
                if (srcSw != null) {
                    betterTopology.detectTunnelDestination(srcSw, lastSw);
                }
            }
        }
    }

    /**
     * Sigh. 
     * @param npt
     * @return
     */
    private SwitchPort npt2swp(NodePortTuple npt) {
        return new SwitchPort(npt.getNodeId(), npt.getPortId());
    }
    
    
    /**
     * Push routes from back to front
     * @param route        Route to push
     * @param srcDevice    Source Device
     * @param ofMatch      ofMatch that needs to be routed (for flow reconcile)
     * @param pi           packetIn that needs to be routed (for packet in)
     * @param pinSwitchId  The switch where the packet in or ofMatch was from
     * @param cookie       The cookie to set in each flow_mod
     * @param wildcards    The wildcard hints
     * @param reqeustFlowRemovedNotifn if set to true then the switch would
     *                     send a flow mod removal notification when the flow
     *                     mod expires
     * @param doFlush      if set to true then the flow mod would be immediately
     *                     written to the switch
     * @param flowModCommand command to use, e.g. 
     *                     OFFlowMod.OFPFC_ADD,
     *                     OFFlowMod.OFPFC_MODIFY etc.
     * @param tunnelEnabled
     * @param cntx         The listener context
     * @return srcSwitchIincluded True if the source switch is included in this route
     * 
     * TODO: overwrite ForwardingBase.pushRoute and unify argument list
     * TODO: handle and test MAC rewriting (i.e., add second FlowMod on
     *       ingress switch)
     */
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="None or more than one VLAN for device {} on input" +
                        " port {}. Cannot push route",
                explanation="Unexpected VLAN tags appears on a device while " +
                        "trying to insert flow action with rewrite rule",
                recommendation="This would generally be caused by a missing " +
                        "match vlans rule in the address space configuration.  " +
                        "Verify the address space configuration."), 
        @LogMessageDoc(level="WARN",
            message="Unable to push route, switch at DPID {dpid} " +
                    "not available",
            explanation="Unexpected VLAN tags appears on a device while " +
                "trying to insert flow action with rewrite rule",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)            
    })
    public boolean pushRewriteRoute(Route route, 
                                    IDevice srcDevice,
                                    OFMatch ofMatch,
                                    OFPacketIn pi,
                                    long pinSwitchId,
                                    long cookie,
                                    Integer wildcards,
                                    boolean reqeustFlowRemovedNotifn,
                                    boolean doFlush,
                                    short flowModCommand,
                                    boolean tunnelEnabled,
                                    ListenerContext cntx) {

        boolean srcSwitchIncluded = false;
        Short pinPort = null;
        if (pi != null) pinPort = pi.getInPort();
        Integer srcIPAddress = null;
        Integer dstIPAddress = null;

        List<NodePortTuple> switchPortList = route.getPath();
        if (switchPortList.size() % 2 != 0) {
            throw new IllegalStateException("Odd number of ports on the route "
                                        + switchPortList.size()
                                        + ": "
                                        + switchPortList);
        }
        if (switchPortList.size() == 0) {
            throw new IllegalStateException("Route is empty");
        }
        
        // Build the Ethernet packet we expect to see on the first hop
        // switch 
        String sourceAddressSpace = srcDevice.getEntityClass().getName();
        SwitchPort firstHopInputPort = npt2swp(switchPortList.get(0));
        
        Ethernet eth = new Ethernet();;
        if (pi != null) {
            eth = new Ethernet();
            byte[] packetData = pi.getPacketData();
            eth.deserialize(packetData, 0, packetData.length);
            if (eth.getPayload() instanceof IPv4) {
                IPv4 packet = (IPv4) eth.getPayload();
                srcIPAddress = new Integer(packet.getSourceAddress());
                dstIPAddress = new Integer(packet.getDestinationAddress());
            }
            
            ofMatch = new OFMatch();
            ofMatch.loadFromPacket(packetData, pi.getInPort());
        }
        
        // The ethernet frame we'll want to push out the pin switch
        Ethernet packetOutEth = null;
        
        if (firstHopInputPort.getSwitchDPID() != pinSwitchId) {
            // The packetIn switch is different from the first hop 
            // switch. We need to reconstruct the packet we would see
            // on the first hop switch by:
            // a) Find out which VLAN the srcDevice would use and set that
            // b) Check if there is a destination MAC rewrite action. If there
            //    is, then we have an ambiguous situation. The packet on the
            //    first hop switch could actually be addressed to the final
            //    destination MAC (direct communication with backend service
            //    node) OR it could be addresses to the original dest MAC
            //    (talking to network service vMAC). We assume the former case, 
            //    i.e., that the packet on the first hop switch is addressed
            //    to the vMAC. In general packet_ins from a non first-hop switch
            //    are likely rare. If they do happen, it's likely that it's due
            //    to races in the network and that the first hop switch 
            //    already/still has the right flow mod anyways. Even if not 
            //    and even if we "guess" wrong, we will just elicit another
            //    packet_in from the first hop switch (but this should be very
            //    rare). Alternatively we could install two FlowMods on the
            //    first hop switch but that seems more intrusive.
            
            // We need to find the VLAN the srcDevice would use on the input
            // port. If the first hop port is on a BD domain it is possible that 
            // we haven't seen an entity on the first hop port. Thus we need 
            // to look at the device's attachment points. 
            Short[] firstHopInputVlans = null;
            for (SwitchPort ap: srcDevice.getAttachmentPoints()) {
                if (topology.isInSameBroadcastDomain(ap.getSwitchDPID(), 
                                         (short)ap.getPort(),
                                         firstHopInputPort.getSwitchDPID(),
                                         (short)firstHopInputPort.getPort(),
                                         tunnelEnabled)) {
                    firstHopInputVlans = srcDevice.getSwitchPortVlanIds(ap);
                    break;
                }
            }
                                         
            if (firstHopInputVlans==null || firstHopInputVlans.length != 1) {
                log.warn("None or more than one VLAN for device {} on input" +
                        " port {}. Cannot push route", 
                        srcDevice,
                        firstHopInputPort);
                return srcSwitchIncluded;
            }
            
            eth.setVlanID(firstHopInputVlans[0]);
            ofMatch.setDataLayerVirtualLan(firstHopInputVlans[0]);
            
            Long origDstMac = rewriteService.getOrigIngressDstMac(cntx);
            if (origDstMac != null) {
                // We handle the case that the packet was addressed to the
                // origDstMac here, since this resembles the case that the
                // first hop switch is the packetIn switch. 
                eth.setDestinationMACAddress(Ethernet.toByteArray(origDstMac));
                ofMatch.setDataLayerDestination(Ethernet.toByteArray(origDstMac));
            }
        }

        // Traverse the route from first to last switch and build the flowMods
        // on each step 
        OFFlowMod[] fmArray = new OFFlowMod[switchPortList.size()];
        for (int indx = 1; indx < switchPortList.size(); indx += 2) {
            // indx and indx-1 will always have the same switch DPID.
            long switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = controllerProvider.getSwitches().get(switchDPID);
            if (sw == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to push route, switch at DPID {} " +
                            "not available", switchDPID);
                }
                return srcSwitchIncluded;
            }
            SwitchPort inSwp = npt2swp(switchPortList.get(indx-1));
            SwitchPort outSwp = npt2swp(switchPortList.get(indx));
            Long nextHopSwitchDPID = null;
            // Next hop switch DPID is needed in case the switchport
            // at indx is a tunnel port.  The condition is checked for
            // index + 2.
            if (indx + 2 < switchPortList.size()) {
                nextHopSwitchDPID = new Long(switchPortList.get(indx+1).getNodeId());
            }

            List<OFAction> actions = new ArrayList<OFAction>();
            int actionsLength = 0;
            OFFlowMod fm =
                    (OFFlowMod) controllerProvider.getOFMessageFactory()
                                                  .getMessage(OFType.FLOW_MOD);

            boolean isFirstOrLastHop = (indx == 1 ||
                                        indx+2 > switchPortList.size());
            
            OFMatch match = ofMatch.clone();
            match.setInputPort((short)inSwp.getPort());
            match = wildcard(match, sw, wildcards, tunnelEnabled,
                             isFirstOrLastHop);
            
            Short egressVlan;
            egressVlan = rewriteService.getSwitchPortVlanMode(outSwp, 
                   sourceAddressSpace, eth.getVlanID(), tunnelEnabled);
            if (egressVlan == null)
                return srcSwitchIncluded;
            OFAction action = getVlanRewriteAction(match.getDataLayerVirtualLan(), egressVlan);
            if (action != null) {
                actions.add(action);
                actionsLength += action.getLengthU();
            }
            eth.setVlanID(egressVlan);
            ofMatch.setDataLayerVirtualLan(egressVlan);
            
            if (1 == indx) {
                // Set the flag to request flow-mod removal notifications 
                // only for the source switch. The removal message is used to
                // maintain the flow cache. Don't set the flag for ARP 
                // messages - TODO generalize check
                if ((reqeustFlowRemovedNotifn)
                        && (ofMatch.getDataLayerType() != Ethernet.TYPE_ARP)) {
                    fm.setFlags((short) 
                                (fm.getFlags()|OFFlowMod.OFPFF_SEND_FLOW_REM));
                }
                // Rewrite DstMAC address if required
                Long finalDstMac = rewriteService.getFinalIngressDstMac(cntx);
                if (finalDstMac != null) {
                    action = getDstMacRewriteAction(Ethernet.toLong(ofMatch.getDataLayerDestination()),
                                                             finalDstMac);
                    if (action != null) {
                        actions.add(action);
                        actionsLength += action.getLengthU();
                    }
                    eth.setDestinationMACAddress(Ethernet.toByteArray(finalDstMac));
                    ofMatch.setDataLayerDestination(Ethernet.toByteArray(finalDstMac));
                }
                // Decrement TTL if required
                Integer decrementHops = rewriteService.getTtlDecrement(cntx);
                boolean switchSupportsTtlDec = sw.attributeEquals(
                        IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true);
                if (switchSupportsTtlDec && decrementHops != null) {
                    // TODO: decrement by n 
                    if (! decrementTtl(eth, decrementHops)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Dropping packet. TTL expired for " +
                                    "srcDev {}.", srcDevice);
                        }
                        return srcSwitchIncluded;
                    }
                    action = new OFActionNiciraTtlDecrement();
                    actions.add(action);
                    actionsLength += action.getLengthU();
                }
            }
            
            if (indx == switchPortList.size()-1) {
                // Last hop. Add SrcMac rewrite if necessary 
                Long finalScrMac = rewriteService.getFinalEgressSrcMac(cntx);
                if (finalScrMac != null) {
                    action = getSrcMacRewriteAction(Ethernet.toLong(ofMatch.getDataLayerSource()),
                                                             finalScrMac);
                    if (action != null) {
                        actions.add(action);
                        actionsLength += action.getLengthU();
                    }
                    eth.setSourceMACAddress(Ethernet.toByteArray(finalScrMac));
                    // no need to update match
                }
            }
        

            // Output is a tunnel port.
            Short tunnelPort = tunnelManager.getTunnelPortNumber(switchDPID);
            if (tunnelPort != null &&
                tunnelPort.shortValue() == (short)outSwp.getPort()) {

                if (nextHopSwitchDPID == null) {
                    log.error("Output to a tunnel port does not have a " +
                            "next switch DPID defined. tunnel port = {}",
                            outSwp);
                    return srcSwitchIncluded;
                }

                Integer ipAddr = 
                        tunnelManager.getTunnelIPAddr(nextHopSwitchDPID.longValue());

                if (ipAddr == null) {
                    log.error("IP Address of tunnel port is not defined. {}",
                              nextHopSwitchDPID);
                    return srcSwitchIncluded;
                }

                OFActionTunnelDstIP tunnelDstAction = 
                        new OFActionTunnelDstIP(ipAddr.intValue());
                actions.add(tunnelDstAction);
                actionsLength += tunnelDstAction.getLengthU();
            }

            OFActionOutput outputAction = new OFActionOutput();
            outputAction.setPort((short)outSwp.getPort());
            outputAction.setMaxLength((short)0xffff);
            actions.add(outputAction);
            actionsLength += outputAction.getLengthU();

            short hardTimeout = FLOWMOD_DEFAULT_HARD_TIMEOUT;
            IRoutingDecision decision = IRoutingDecision.rtStore.get(cntx,
                                             IRoutingDecision.CONTEXT_DECISION);
            if (decision != null) {
                hardTimeout = decision.getHardTimeout();
            }

            fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
                .setHardTimeout(hardTimeout)
                .setPriority(accessPriority)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setCookie(cookie)
                .setCommand(flowModCommand)
                .setMatch(match)
                .setActions(actions)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH+actionsLength);

            fmArray[indx] = fm;
            
            if (pi != null && inSwp.getSwitchDPID() == pinSwitchId
                    && pinPort.equals((short)inSwp.getPort())) {
                // This is the packetIn switch. Clone the current eth, we'll
                // need it to push the packet. 
                // TODO: if the eth is unmodified we can also set the bufferId
                //       What will that do? Will it force an implicit packetOut?
                srcSwitchIncluded = true;
                packetOutEth = (Ethernet)eth.clone();
            } else if (pi != null && inSwp.getSwitchDPID() == pinSwitchId) {
                log.info("Not sending packet-out on the switch port as inport is different.");
                log.info("Switch DPID = {}", HexString.toHexString(pinSwitchId));
                log.info("pinPort = {}, inSwPort = {}", pinPort, inSwp.getPort());
                log.info("Route = {}", route);
            }
        }

        // Before sending out the flow-mods, detect for flows from or to
        // tunnel loopback ports.  As the flowmods are sent from last hop
        // switch to first hop switch, we need to look at the flow mods before
        // we start pushing the flow-mods out.
        // srcIPAddress and dstIPAddresses are already
        int last = switchPortList.size()-1;
        detectTunnelTraffic(switchPortList.get(0), switchPortList.get(last),
                            srcIPAddress, dstIPAddress);


        // Now we'll actually push the flow mods to the switches. We'll
        // go from last to first switch.
        for (int indx = switchPortList.size()-1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            long switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = controllerProvider.getSwitches().get(switchDPID);
            if (sw == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to push route, switch at DPID {} " +
                            "not available", switchDPID);
                }
                return srcSwitchIncluded;
            }

            short inPortNum = switchPortList.get(indx-1).getPortId();
            short outPortNum = switchPortList.get(indx).getPortId();
            Long nextHopSwitchDPID = null;
            // Next hop switch DPID is needed in case the switchport
            // at indx is a tunnel port.  The condition is checked for
            // index + 2.
            if (indx + 2 < switchPortList.size()) {
                nextHopSwitchDPID = new Long(switchPortList.get(indx+1).getNodeId());
            }

            OFFlowMod fm = fmArray[indx];
            try {
                counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
                if (log.isDebugEnabled()) {
                    log.debug("Pushing Route flowmod routeIndx={} " +
                            "sw={} inPort={} outPort={}, fm={}",
                            new Object[] {indx,
                                          HexString.toHexString(sw.getId()),
                                          fm.getMatch().getInputPort(),
                                          switchPortList.get(indx).getPortId(),
                                          fm
                                          });
                }
                messageDamper.write(sw, fm, cntx);
                if (doFlush) {
                    sw.flush();
                    counterStore.updateFlush();
                }

                // Push the packet out the source switch
                if (packetOutEth != null &&
                        sw.getId() == pinSwitchId &&
                        pinPort.equals(inPortNum)) {
                    pushPacket(packetOutEth, sw, OFPacketOut.BUFFER_ID_NONE,
                            inPortNum, outPortNum, nextHopSwitchDPID, cntx, false);
                    srcSwitchIncluded = true;
                }

                /** 
                 * Only cache the flowmod at index 1.
                 * Flowmod at index 1 is the ingress flow that may have mac rewrite action.
                 */
                if (indx == 1) {
                    OFMatch match = fm.getMatch().clone();
                    OFMatchWithSwDpid ofmWithSwDpid = 
                        new OFMatchWithSwDpid(match, 
                                              switchPortList.get(indx).getNodeId());
                    SwitchPort srcSwitchPort = 
                        new SwitchPort(switchPortList.get(indx-1).getNodeId(), 
                                       switchPortList.get(indx-1).getPortId());
                    if (log.isTraceEnabled()) {
                        log.trace("Cache the flow {}", ofmWithSwDpid);
                    }
                    betterFlowCacheMgr.addFlow(cntx, ofmWithSwDpid,
                                            appCookie,
                                            srcSwitchPort, fm.getPriority(),
                                            FlowCacheObj.FCActionPERMIT);
                }
            } catch (IOException e) {
                log.error("Failure writing flow mod", e);
            }
        }

        return srcSwitchIncluded;
    }

    public void pushPacket(IPacket packet, 
                           IOFSwitch sw,
                           int bufferId,
                           short inPort,
                           short outPort,
                           Long nextHopSwitchDPID,
                           ListenerContext cntx,
                           boolean flush) {

        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} inPort={} outPort={}", 
                      new Object[] {sw, inPort, outPort});
        }

        OFPacketOut po =
                (OFPacketOut) controllerProvider.getOFMessageFactory()
                                                .getMessage(OFType.PACKET_OUT);

        // set actions
        int actionsLength = 0;
        List<OFAction> actions = new ArrayList<OFAction>();

        Short tunnelPort = tunnelManager.getTunnelPortNumber(sw.getId());
        if (tunnelPort != null && tunnelPort.shortValue() == outPort) {
            if (nextHopSwitchDPID == null) {
                log.error("No IP address assigned for tunnel port. sw = {}",
                        nextHopSwitchDPID);
                return;
            }
            Integer ipAddr = 
                    tunnelManager.getTunnelIPAddr(nextHopSwitchDPID.longValue());

            if (ipAddr == null) {
                log.error("IP Address of tunnel port is not defined. {}",
                          nextHopSwitchDPID);
                return;
            }
            OFActionTunnelDstIP tunnelDstAction =
                    new OFActionTunnelDstIP(ipAddr.intValue());
            actions.add(tunnelDstAction);
            actionsLength += tunnelDstAction.getLengthU();
        }

        // Output action should be set after the tunnel destination, if present
        actions.add(new OFActionOutput(outPort, (short) 0xffff));

        po.setActions(actions)
          .setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH +
                            actionsLength));
        short poLength =
                (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

        // set buffer_id, in_port
        po.setBufferId(bufferId);
        po.setInPort(inPort);

        // set data - only if buffer_id == -1
        if (po.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            if (packet == null) {
                log.error("BufferId is not set and packet data is null. " +
                          "Cannot send packetOut. " +
                        "srcSwitch={} inPort={} outPort={}",
                        new Object[] {sw, inPort, outPort});
                return;
            }
            byte[] packetData = packet.serialize();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }

        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            messageDamper.write(sw, po, cntx, flush);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }
    

    /**
     * This wildcard implementation is different from Forwarding base.  It has
     * two additional parameters, isTunnelEnabled and isFirstOrLastHop.
     * These two parameters are used to not wildcard layer 3 addresses on
     * the first and last hop of non-tunnelEanbled traffic.
     * @param match
     * @param sw
     * @param wildcards_in_decision
     * @param isTunnelEnabled
     * @param isFirstOrLastHop
     * @return
     */
    protected OFMatch wildcard(OFMatch match, IOFSwitch sw,
                               Integer wildcards_in_decision,
                               boolean isTunnelEnabled,
                               boolean isFirstOrLastHop) {
        int wildcards = OFMatch.OFPFW_ALL;
        if (wildcards_in_decision != null) {
            wildcards = wildcards_in_decision.intValue();
        }

        wildcards &= ~OFMatch.OFPFW_IN_PORT & ~OFMatch.OFPFW_DL_VLAN;   // Required: For host mobility detection
        wildcards &= ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST;     // Required: For stats collection by mac
        wildcards &= ~OFMatch.OFPFW_DL_TYPE;

        // If IP Match is required
        if (sw.hasAttribute(IOFSwitch.PROP_REQUIRES_L3_MATCH)) {
            wildcards &= ~OFMatch.OFPFW_DL_TYPE;
            wildcards &= ~OFMatch.OFPFW_NW_SRC_MASK;
            wildcards &= ~OFMatch.OFPFW_NW_DST_MASK;
        }

        int fastwildcards = OFMatch.OFPFW_ALL;
        if (sw.hasAttribute(IOFSwitch.PROP_FASTWILDCARDS)) {
            fastwildcards = ((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue();
        }
        wildcards &= fastwildcards;                                     // Ignore wildcards not in fastwildcards
        if (log.isTraceEnabled()) {
            log.trace("Setting wildcards for switch. sw={}," +
                      "wildcards_in_decision=0x{}, wildcards=0x{}",
                      new Object[] {sw,
                                    (wildcards_in_decision == null ? "null" : Integer.toHexString(wildcards_in_decision)),
                                    Integer.toHexString(wildcards)});
        }

        // For tunnel traffic, on the first and last hops, IP addresses
        // need to be mentioned in the packet.
        if (isTunnelEnabled == false && isFirstOrLastHop) {
            wildcards &= ~OFMatch.OFPFW_DL_TYPE;
            wildcards &= ~OFMatch.OFPFW_NW_SRC_MASK;
            wildcards &= ~OFMatch.OFPFW_NW_DST_MASK;
        }

        return match.clone().setWildcards(wildcards);

    }

    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="No source device found for decision={decision}",
                explanation="There was no source device found for the policy " +
                        "decision for the current flow.",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
        @LogMessageDoc(level="ERROR",
            message="No source switchport-tuple for decision={}",
            explanation="There was no source attachment point found " +
                    "for the policy decision for the current flow.",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
        @LogMessageDoc(level="ERROR",
            message="No source switch found for switchport-" + 
                    "tuple={}, in decision={}",
            explanation="The source switch is no longer connected to " +
                    "the controller.",
            recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
            message="No destination interfaces or devices found " + 
                    "for decision={}",
            explanation="A multicast flow has no destination interfaces.",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })
    private boolean validateDecision(IRoutingDecision decision) {
        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();

        switch(decision.getRoutingAction()) {
            case FORWARD: // Same validation as FORWARD_OR_FLOOD
            case FORWARD_OR_FLOOD:
                IDevice srcDevice = decision.getSourceDevice();
                if (srcDevice == null) {
                    log.error("No source device found for decision={}", 
                              decision);
                    return false;
                }

                SwitchPort srcTuple = decision.getSourcePort();
                if (srcTuple == null) {
                    log.error("No source switchport-tuple for decision={}", 
                              decision);
                    return false;
                }

                IOFSwitch srcSwitch = switches.get(srcTuple.getSwitchDPID());
                if (srcSwitch == null) {
                    log.error("No source switch found for switchport-" + 
                              "tuple={}, in decision={}", srcTuple, decision);
                    return false;
                }

                // Valid decision for forward/flood
                break;

            case MULTICAST:
                srcTuple = decision.getSourcePort();
                if (srcTuple == null) {
                    log.error("No source switchport-tuple for decision={}", decision);
                    return false;
                }
                
                srcSwitch = switches.get(srcTuple.getSwitchDPID());
                if (srcSwitch == null) {
                    log.error("No source switch found for switchport-" + 
                              "tuple={}, in decision={}", srcTuple, decision);
                    return false;
                }

                List<IDevice> dstDevices = decision.getDestinationDevices(); 
                List<SwitchPort> dstInterfaces = 
                        decision.getMulticastInterfaces();
                
                if ((dstDevices == null || dstDevices.isEmpty()) && 
                    (dstInterfaces == null || dstInterfaces.isEmpty())) {
                    log.error("No destination interfaces or devices found " + 
                              "for decision={}", decision);
                    return false;
                }

                // Valid decision for broadcast
                break;

            case DROP:
                srcDevice = decision.getSourceDevice();
                if (srcDevice == null) {
                    log.error("No source device found for decision={}", 
                              decision);
                    return false;
                }

                srcTuple = decision.getSourcePort();
                if (srcTuple == null) {
                    log.error("No source switchport-tuple for decision={}", 
                              decision);
                    return false;
                }

                srcSwitch = switches.get(srcTuple.getSwitchDPID());
                if (srcSwitch == null) {
                    log.error("No source switch found for switchport-" + 
                              "tuple={}, in decision={}", srcTuple, decision);
                    return false;
                }

                // Valid decision for drop
                break;

            case NONE:
            default:
                break;
        }

        // Valid decision
        return true;
    }

    @LogMessageDoc(level="WARN",
            message="Failed to inject Fake ARP since no incoming" +
                    "broadcast is allowed port for port {dpid} {port}",
            explanation="Could not generate an ARP request for the " +
                    "destination of this flow since broadcast is disabled " +
                    "for source of the flow.",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private boolean injectFakeArp(Ethernet eth, IOFSwitch arpPktInSw,
                                  short arpPktInPort, int targetIp,
                                  boolean broadcastPort) {
        if (targetIp == 0) {
            if (log.isDebugEnabled()) {
                log.debug("Skip ARP injection to discover invalid destination " +
                          "0.0.0.0 on Sw/[Bcast, Ucast]Port {}",
                          new Object[] {arpPktInSw.getStringId(),
                                        broadcastPort ? "Bcast" : "Ucast",
                                        arpPktInPort});
            }
            return false;
        }

        IPv4 payload = (IPv4) eth.getPayload();

        // We only do this if it's an IPv4 and not broadcast
        // We create an ARP probe (RFC 5227), sender IP and target MAC 
        // set to 0 to prevent incorrect learning.
        // XXX: if the destination we are probing happens to be router 
        // interface we might probe an IP address that's external to the
        // subnet. Nothing much we can do about it though.
        IPacket arpRequest = new Ethernet()
        .setSourceMACAddress(eth.getSourceMACAddress())
        .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
        .setEtherType(Ethernet.TYPE_ARP)
        .setVlanID(eth.getVlanID())
        .setPriorityCode(eth.getPriorityCode())
        .setPayload(
                    new ARP()
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareAddressLength((byte) 6)
                    .setProtocolAddressLength((byte) 4)
                    .setOpCode(ARP.OP_REQUEST)
                    .setSenderHardwareAddress(eth.getSourceMACAddress())
                    .setSenderProtocolAddress(0)
                    .setTargetHardwareAddress(new byte[] { 0, 0, 0, 0, 0, 0 })
                    .setTargetProtocolAddress(targetIp));
        byte[] arpRequestSerialized = arpRequest.serialize();


        OFPacketIn fakePi =
                (OFPacketIn) controllerProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN);
        fakePi.setInPort(arpPktInPort);
        fakePi.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        fakePi.setReason(OFPacketInReason.NO_MATCH);
        fakePi.setPacketData(arpRequestSerialized);
        fakePi.setTotalLength((short) arpRequestSerialized.length);
        fakePi.setLength(OFPacketIn.MINIMUM_LENGTH);

        // Inject the fake ARP into the processing chain
        if (log.isDebugEnabled()) {
            log.debug("Injecting ARP to discover destination {} on " +
                      "Sw/[Bcast, Ucast]Port {}",
                      IPv4.fromIPv4Address(payload.getDestinationAddress()),
                      new Object[] {arpPktInSw.getStringId(),
                                    broadcastPort ? "Bcast" : "Ucast",
                                    fakePi.getInPort()});
        }
        return controllerProvider.injectOfMessage(arpPktInSw, fakePi);
    }

    /**
     * Helper method to compute the ip address for which we should ARP for when
     * injecting a fake arp.
     *
     * We have 2 choices: dstDevice, origDstDevice in the listener context.
     *
     * If an orig dst exists in the context and origDstHigherPriTarget is true
     *     Use the orig dst's ip as the arp target ip
     * else:
     *     Use the destination ip in the packet as the arp target ip
     */
    private int getTargetIpForFakeArp(IPv4 payload, ListenerContext cntx,
                                      boolean origDstHigherPriTarget) {
        int targetIp = payload.getDestinationAddress();
        IDevice origDstDevice = IDeviceService.fcStore.get(cntx,
                                    IDeviceService.CONTEXT_ORIG_DST_DEVICE);
        if (origDstDevice != null && origDstHigherPriTarget) {
            Integer[] ipAddrs = origDstDevice.getIPv4Addresses();
            if (ipAddrs != null && ipAddrs.length > 0) {
                targetIp = ipAddrs[0].intValue();
            }
        }
        return targetIp;
    }

    /**
     * Inject a Fake ARP on the broadcast port that is allowed for the
     * switch that receives the packet in
     * @param pi - packet In
     * @param sw - switch receiving the packet in
     * @param cntx - listener context
     * @param origDstHigherPriTarget
     *
     * If an orig dst exists in the context and origDstHigherPriTarget is true
     *     Use the orig dst's ip as the arp target ip
     * else:
     *     Use the destination ip in the packet as the arp target ip
     * @return true - if arp was successfully injected, false - otherwise
     */
    private boolean injectFakeArpOnAllowedIncomingBroadcastNodePort(
                                              OFPacketIn pi,
                                              IOFSwitch sw,
                                              ListenerContext cntx,
                                              boolean origDstHigherPriTarget) {
        Ethernet eth = IControllerService.bcStore.get(cntx,
                                IControllerService.CONTEXT_PI_PAYLOAD);
        if (eth == null) return false;
        if (!(eth instanceof Ethernet)) return false;
        if (eth.isBroadcast()) return false;
        if ((eth.getPayload() instanceof IPv4) == false) return false;

        /**
         * If the input port is an internal port in a OF cluster, (rare case), then
         * use the original input sw and port.
         * If the input port is a broadcastDomain port, pick the equivalent
         * broadcast-allowed broadcastDomain port.
         */
        long pinSw = sw.getId();
        short pinPort = pi.getInPort();
        short packetInputPort = pi.getInPort();
        IOFSwitch packetInputSwitch = sw;
        int srcIP, dstIP;

        IPv4 payload = (IPv4) eth.getPayload();
        srcIP = payload.getSourceAddress();
        dstIP = payload.getDestinationAddress();

        // Check if the source of destination IP address belongs to tunnel
        // subnet, if so, we need to disable tunnels.
        boolean tunnelEnabled = !(tunnelManager.isTunnelSubnet(srcIP) ||
                tunnelManager.isTunnelSubnet(dstIP));

        if (topology.isBroadcastDomainPort(pinSw, pinPort, tunnelEnabled)) {
            NodePortTuple broadcastAllowedSrcPort =
                    topology.getAllowedIncomingBroadcastPort(pinSw, pinPort, tunnelEnabled);
            if (broadcastAllowedSrcPort == null) {
                log.warn("Failed to inject Fake ARP since no incoming" +
                        "broadcast is allowed port for port {} {}",
                        HexString.toHexString(pinSw), pinPort);
                return false;
            }
            /**
             * Overwrite the input switch port to the allowed one.
             */
            packetInputPort = broadcastAllowedSrcPort.getPortId();
            packetInputSwitch = controllerProvider.getSwitches().get(broadcastAllowedSrcPort.getNodeId());
        }

        int targetIp = getTargetIpForFakeArp(payload, cntx,
                                             origDstHigherPriTarget);
        return injectFakeArp(eth, packetInputSwitch, packetInputPort, targetIp,
                             true);
    }

    /**
     * Inject a Fake ARP on the port that is permitted to receive unicast
     * traffic destined to the destDevice
     * @param pi - packet In
     * @param sw - switch receiving the packet in
     * @param cntx - listener context
     * @param origDstHigherPriTarget
     *
     * If an orig dst exists in the context and origDstHigherPriTarget is true
     *     Use the orig dst's ip as the arp target ip
     * else:
     *     Use the destination ip in the packet as the arp target ip
     * @return true - if arp was successfully injected, false - otherwise
     */
    private boolean
    injectFakeArpOnAllowedIncomingUnicastNodePort(NodePortTuple allowedSrcPort,
                                              ListenerContext cntx,
                                              boolean origDstHigherPriTarget) {
        Ethernet eth = IControllerService.bcStore.get(cntx,
                                IControllerService.CONTEXT_PI_PAYLOAD);
        if (eth == null) return false;
        if (!(eth instanceof Ethernet)) return false;
        if (eth.isBroadcast()) return false;
        if ((eth.getPayload() instanceof IPv4) == false) return false;

        Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
        IOFSwitch arpPktInSw = switches.get(allowedSrcPort.getNodeId());
        short arpPktInPort = allowedSrcPort.getPortId();

        IPv4 payload = (IPv4) eth.getPayload();
        int targetIp = getTargetIpForFakeArp(payload, cntx,
                                             origDstHigherPriTarget);
        return injectFakeArp(eth, arpPktInSw, arpPktInPort, targetIp, false);
    }

    /**
     * Return an OFAction the rewrites the source MAC to mac if
     * necessary. If the given mac is null or if the given mac matches
     * the packet's current mac null is returned. Otherwise the approriate
     * rewrite action is returned.
     * @param origMac the original / current MAC address
     * @param mac The mac to rewrite to 
     * @return
     */
    protected OFAction getSrcMacRewriteAction(Long origSrcMac, Long mac) {
        if (mac == null)
            return null;  // no action
        if (mac.equals(origSrcMac))
            return null;  // no action
        byte[] macArray = Ethernet.toByteArray(mac);
        return new OFActionDataLayerSource(macArray);
        
    }
    
    /**
     * @see getSrcMacRewriteAction
     */
    protected OFAction getDstMacRewriteAction(Long origDstMac, Long mac) {
        if (mac == null)
            return null;  // no action
        if (mac.equals(origDstMac))
            return null;  // no action
        byte[] macArray = Ethernet.toByteArray(mac);
        return new OFActionDataLayerDestination(macArray);
    }
    
    /**
     * @see getSrcMacRewriteAction
     * Doesn't sanity check the vlan range.
     */
    protected OFAction getVlanRewriteAction(Short origVlan, Short vlan) {
        if (vlan == null)
            return null;  // no action
        if (vlan.equals(origVlan))
            return null; // no action
        if (vlan.equals(Ethernet.VLAN_UNTAGGED))
            return new OFActionStripVirtualLan();
        else
            return new OFActionVirtualLanIdentifier(vlan);
    }
    
    /**
     * Decrement the TTL of an IP packet. If the ethernet packet is an IPv4
     * packet decrement the TTL by n. Will return false if the TTL expired.
     * If the ethernet is not IPv4 will return true and not leave the packet
     * unchanged
     * @param eth Ethernet packet. 
     * @param n
     * @return false if the TTL expired, true otherwise
     */
    protected boolean decrementTtl(Ethernet eth, int n) {
        if (eth.getPayload() instanceof IPv4) {
            IPv4 ipPkt = (IPv4)eth.getPayload();
            short ttl = U8.f(ipPkt.getTtl());
            if (n > 255)
                n = 255;
            short newTtl = (short) (ttl - (short)n);
            if (ttl <= n) {
                ipPkt.setTtl((byte)0);
                ipPkt.resetChecksum();
                return false;
            }
            ipPkt.setTtl(U8.t(newTtl));
            ipPkt.resetChecksum();
            return true;
        }
        return true;
    }
    

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (((type.equals(OFType.PACKET_IN) || (type.equals(OFType.FLOW_MOD))) 
                && name.equals("virtualrouting")) ||
                super.isCallbackOrderingPrereq(type, name));
    }

    // IModule

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IForwardingService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m =
        new HashMap<Class<? extends IPlatformService>,
        IPlatformService>();
        m.put(IForwardingService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IControllerService.class);
        l.add(IDeviceService.class);
        l.add(IRoutingService.class);
        l.add(ITopologyService.class);
        l.add(IBetterTopologyService.class);
        l.add(ICounterStoreService.class);
        l.add(IFlowCacheService.class);
        l.add(ITunnelManagerService.class);
        l.add(IRewriteService.class);
        l.add(IAddressSpaceManagerService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    @LogMessageDoc(level="WARN",
        message="Error parsing flow idle timeout, using default of {} seconds",
        explanation="The flow idle timeout in the configuration file could not " +
                "be understood",
        recommendation="Update the flow idle timeout value in the" +
                " configuration file to a valid value.")
    public void init(ModuleContext context)
            throws ModuleException {
        super.init();
        this.controllerProvider = context.getServiceImpl(IControllerService.class);
        this.deviceManager = context.getServiceImpl(IDeviceService.class);
        this.routingEngine = context.getServiceImpl(IRoutingService.class);
        this.topology = context.getServiceImpl(ITopologyService.class);
        this.betterTopology = context.getServiceImpl(IBetterTopologyService.class);
        this.counterStore = context.getServiceImpl(ICounterStoreService.class);
        this.storageSource =
                context.getServiceImpl(IStorageSourceService.class);
        this.betterFlowCacheMgr =
                context.getServiceImpl(IFlowCacheService.class);
        this.flowReconcileMgr = 
                context.getServiceImpl(IFlowReconcileService.class);
        this.tunnelManager = 
                context.getServiceImpl(ITunnelManagerService.class);
        this.rewriteService = 
                context.getServiceImpl(IRewriteService.class);
        this.restApi = context.getServiceImpl(IRestApiService.class);
        
        // read our config options
        Map<String, String> configOptions = context.getConfigParams(this);
        try {
            String idleTimeout = configOptions.get("idletimeout");
            if (idleTimeout != null) {
                FLOWMOD_DEFAULT_IDLE_TIMEOUT = Short.parseShort(idleTimeout);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing flow idle timeout, using default of {} seconds",
                    FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        }
        try {
            String hardTimeout = configOptions.get("hardtimeout");
            if (hardTimeout != null) {
                FLOWMOD_DEFAULT_HARD_TIMEOUT = Short.parseShort(hardTimeout);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing flow hard timeout, using default of {} seconds",
                    FLOWMOD_DEFAULT_HARD_TIMEOUT);
        }
        log.debug("FlowMod idle timeout set to {} seconds", FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        log.debug("FlowMod hard timeout set to {} seconds", FLOWMOD_DEFAULT_HARD_TIMEOUT);
    }

    @Override
    public void startUp(ModuleContext context) {
        super.startUp();
        storageSource.createTable(TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_PRIMARY_KEY);
        storageSource.addListener(TABLE_NAME, this);
        readConfigurationFromStorage();
        flowReconcileMgr.addFlowReconcileListener(this);
        restApi.addRestletRoutable(new ForwardingWebRoutable());
    }

    public void setBetterFlowCache(IFlowCacheService betterFlowCacheMgr) {
        this.betterFlowCacheMgr = betterFlowCacheMgr;
    }
    
    protected void setMessageDamper(OFMessageDamper messageDamper) {
        this.messageDamper = messageDamper;
    }

    public IStorageSourceService getStorageSource() {
        return storageSource;
    }

    public void setStorageSource(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }

    public short getAccessPriority() {
        return accessPriority;
    }

    public void setAccessPriority(short priority) {
        this.accessPriority = priority;
    }

    @Override
    public long getHashByMac(long macAddress) {
        // disabled
        return 0;
    }

    // IStorageSourceListener
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        log.debug("Table modified (rows modified)", tableName);
        readConfigurationFromStorage();
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        log.debug("Table modified (rows deleted): {}", tableName);
        readConfigurationFromStorage();
    }

    @LogMessageDoc(level="ERROR",
            message="failed to access storage: {reason}",
            explanation="Could not retrieve forwarding configuration",
            recommendation=LogMessageDoc.CHECK_CONTROLLER)
    private void readConfigurationFromStorage() {
        // Initialize flow-mod priorities to defaults
        this.setAccessPriority(DEFAULT_ACCESS_PRIORITY);

        try {
            Map<String, Object> row;
            IResultSet resultSet = storageSource.executeQuery(
                TABLE_NAME, ColumnNames, null, null);
            if (resultSet == null)
                return;

            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                row = it.next().getRow();
                if (row.containsKey(COLUMN_PRIMARY_KEY)) {
                    String primary_key = (String) row.get(COLUMN_PRIMARY_KEY);
                    if (primary_key.equals(VALUE_PRIMARY_KEY)) {
                        if (row.containsKey(COLUMN_ACCESS_PRIORITY)) {
                            this.setAccessPriority(
                                Short.valueOf((String) row.get(COLUMN_ACCESS_PRIORITY)));
                        }
                    }
                }
            }
        }
        catch (StorageException e) {
            log.error("Failed to access storage for forwarding configuration: {}",
                      e.getMessage());
        }
    }

    // IHAListener
    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    log.debug("Re-reading forwarding configuration from storage due " +
                              "to HA role change from SLAVE -> MASTER");
                    readConfigurationFromStorage();
                }
                // else ignore MASTER -> MASTER
                break;

            case SLAVE:
                // ignore if the new role is slave
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

}
