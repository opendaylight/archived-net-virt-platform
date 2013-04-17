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

package org.sdnplatform.topology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryListener;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.routing.IRoutingService;
import org.sdnplatform.routing.Link;
import org.sdnplatform.routing.Route;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.TunnelEvent.TunnelLinkStatus;
import org.sdnplatform.topology.web.TopologyWebRoutable;
import org.sdnplatform.tunnelmanager.ITunnelManagerListener;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.vendor.OFActionTunnelDstIP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class BetterTopologyManager extends TopologyManager
    implements IModule, IBetterTopologyService, IRoutingService,
    ILinkDiscoveryListener, ITunnelManagerListener {
    protected static final Logger log =
            LoggerFactory.getLogger(BetterTopologyManager.class);

    ITunnelManagerService tunnelManager = null;

    Set<BroadcastDomain> broadcastDomains;
    Long tunnelDomain;

    // Declarations for tunnel liveness detection
    // The ordered node pairs here are switch DPIDs.
    /**
     * Queue to detect tunnel failures.  The queue consists of ordered nodepair
     * (srcDPID, dstDPID) that's created whenever we see a flow-mod that is
     * pushed to a switch where the flow starts from the tunnel loopback port.
     * When a flow-mod is pushed to a switch that ends at a tunnel loopback
     * port, we remove the nodepair.  If we do not receive the corresponding
     * flow-mod that ends at tunnel loopback, it is an indication of a potential
     * tunnel failure.  To validate this, we send LLDP on the tunnel ports,
     * and add the tunnel endpoints to the verification queue.  If the LLDP is
     * recieved within a certain time interval, then the tunnel assumed to be
     * alive.  Otherwise, then the tunnel is assumed to have failed.  All tunnel
     * failures are logged as warning messages. The status queue is cleaned
     * every 60 seconds and may be accessed through the CLI.
     */
    BlockingQueue<OrderedNodePair> tunnelDetectionQueue;
    int TUNNEL_DETECTION_TIMEOUT_MS = 2000;  // 2 seconds
    int tunnelDetectionTime = TUNNEL_DETECTION_TIMEOUT_MS;

    /**
     * This queue holds tunnel events for which an LLDP has been sent
     * and waiting for the LLDP to arrive at the other end of the tunnel.
     */
    BlockingQueue<OrderedNodePair> tunnelVerificationQueue;
    int TUNNEL_VERIFICATION_TIMEOUT_MS = 2000;  // 2 seconds
    int tunnelVerificationTime = TUNNEL_VERIFICATION_TIMEOUT_MS;

    /**
     * This queue holds the tunnel events for which LLDP was sent, but
     * the LLDP was not received within the desired time.
     */
    ConcurrentHashMap<Long, ConcurrentHashMap<Long, TunnelEvent>> tunnelStatusMap;

    // *****************
    // Topology Computation
    // *****************

    /** This is the main entry point into the creation of a new topology
     *  given a link discovery update or other events such as enabling/disabling
     *  multipath, HA etc. It is important to note that just because there is a
     *  link update, topology need not necessarily be recomputed. For example,
     *  broadcast links continue to appear/disappear given our BDDP probing mechanism;
     *  but the set of broadcast ports (that make up the broadcast domain) stays
     *  the same (in stable conditions) - in these situations topology is not
     *  recomputed and new instances are not formed.
     */
    @Override
    public boolean createNewInstance() {
        boolean recomputeTopologyFlag = (dtLinksUpdated || tunnelPortsUpdated);

        // Create a new tunnel domain. If the tunnel domain identifier
        // is different from the previous one, topology needs to be
        // recomputed.  We may optimize this logic by checking only
        // if switches were added/updated.
        Long newTunnelDomain = new Long(createTunnelDomain());
        if (tunnelDomain == null || !tunnelDomain.equals(newTunnelDomain)) {
            tunnelDomain = newTunnelDomain;
            recomputeTopologyFlag = true;
            log.trace("Topology recomputed due to tunnel domain id change.");
        }

        Set<NodePortTuple> blockedPorts = new HashSet<NodePortTuple>();

        Map<NodePortTuple, Set<Link>> linksWithoutTunnels;
        linksWithoutTunnels =
                new HashMap<NodePortTuple, Set<Link>>(switchPortLinks);

        Set<NodePortTuple> broadcastDomainPorts =
                identifyBroadcastDomainPorts();

        Set<BroadcastDomain> bDomains =
                identifyNonOpenflowDomains(broadcastDomainPorts);

        if (broadcastDomains == null ||
                broadcastDomains.equals(bDomains) == false) {
            broadcastDomains = bDomains;
            recomputeTopologyFlag = true;
        }

        // Quit if topology re-computation is not necessary.
        if (!recomputeTopologyFlag) return false;

        // Remove tunnel links
        for (NodePortTuple npt: tunnelPorts) {
            linksWithoutTunnels.remove(npt);
        }

        // First, create a topology instance excluding the tunnel links.
        // There could be a few blocked links, hence ports.  Get the
        // collection of blocked ports from this topology instance.
        //
        // ignore tunnel ports.
        BetterTopologyInstance ntNoTunnels =
                new BetterTopologyInstance(switchPorts,
                                        blockedPorts,
                                        linksWithoutTunnels,
                                        broadcastDomainPorts,
                                        new HashSet<NodePortTuple>(), // no tunnel ports
                                        broadcastDomains,
                                        tunnelDomain,
                                        controllerProvider,
                                        tunnelManager);
        ntNoTunnels.compute();


        // Now including the tunnel ports
        BetterTopologyInstance nt =
                new BetterTopologyInstance(switchPorts,
                                        blockedPorts,
                                        switchPortLinks,
                                        broadcastDomainPorts,
                                        tunnelPorts,
                                        broadcastDomains,
                                        tunnelDomain,
                                        controllerProvider,
                                        tunnelManager);
        nt.compute();

        currentInstanceWithoutTunnels = ntNoTunnels;
        currentInstance = nt;
        return true;
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This function computes the groups of
     * switch ports that connect to  non-openflow domains. They are
     * grouped together based on whether there's a broadcast
     * leak from one to another or not.  We will currently
     * ignore directionality of the links from the switch ports.
     *
     * Assuming link tuple as undirected, the goal is to simply
     * compute connected components.
     *
     */
    protected Set<BroadcastDomain>
    identifyNonOpenflowDomains(Set<NodePortTuple> broadcastDomainPorts) {
        Set<BroadcastDomain> broadcastDomains = new HashSet<BroadcastDomain>();

        Set<NodePortTuple> visitedNpt = new HashSet<NodePortTuple>();
        // create an queue of NPT to be examined.
        Queue<NodePortTuple> nptQueue = new LinkedList<NodePortTuple>();
        // Do a breadth first search to get all the connected components
        for(NodePortTuple npt: broadcastDomainPorts) {
            if (visitedNpt.contains(npt)) continue;

            BroadcastDomain bd = new BroadcastDomain();
            bd.add(npt);
            bd.setId(broadcastDomains.size()+1);
            broadcastDomains.add(bd);

            visitedNpt.add(npt);
            nptQueue.add(npt);

            while(nptQueue.peek() != null) {
                NodePortTuple currNpt = nptQueue.remove();
                if (switchPortLinks.containsKey(currNpt) == false) continue;
                for(Link l: switchPortLinks.get(currNpt)) {
                    NodePortTuple otherNpt;
                    if (l.getSrc() == currNpt.getNodeId() &&
                            l.getSrcPort() == currNpt.getPortId()) {
                        otherNpt = new NodePortTuple(l.getDst(), l.getDstPort());
                    } else {
                        otherNpt = new NodePortTuple(l.getSrc(), l.getSrcPort());
                    }

                    if (visitedNpt.contains(otherNpt) == false) {
                        nptQueue.add(otherNpt);
                        visitedNpt.add(otherNpt);
                        bd.add(otherNpt);
                    }
                }
            }
        }

        if (broadcastDomains.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("No broadcast domains exist.");
            }
        } else {
            if (log.isTraceEnabled()) {
                StringBuffer bds = new StringBuffer();
                for(BroadcastDomain bd:broadcastDomains) {
                    bds.append(bd);
                    bds.append(" ");
                }
                log.trace("Broadcast domains found in the network: {}", bds);
            }
        }

        return broadcastDomains;
    }

    /**
     * Get the set of ports to eliminate for sending out BDDP. The method
     * returns all the ports that are suppressed for link discovery on the
     * switch and the tunnel port on the switch.
     * @param sid
     * @return
     */
    @Override
    protected Set<Short> getPortsToEliminateForBDDP(long sid) {
        Set<NodePortTuple> suppressedNptList = linkDiscovery.getSuppressLLDPsInfo();
        if (suppressedNptList == null) return null;

        Set<Short> resultPorts = new HashSet<Short>();
        for(NodePortTuple npt: suppressedNptList) {
            if (npt.getNodeId() == sid) {
                resultPorts.add(npt.getPortId());
            }
        }

        Short tunnelPort = tunnelManager.getTunnelPortNumber(sid);
        if (tunnelPort != null)
            resultPorts.add(tunnelPort);

        return resultPorts;
    }

    /**
     * We model tunnel domain as a switch.  In order to assign a
     * DPID to the switch, we start with a DPID value and check if
     * it is already part of the regular set of switches.  If so,
     * we increment and check again.  This procedure continues
     * until we are able to assign a DPID for the tunnel domain
     * switch.
     */
    private long createTunnelDomain() {
        long tid = 0x00FFFFFFFFFFFFFFL;
        if (controllerProvider.getSwitches() != null) {
            Set<Long> switches = controllerProvider.getSwitches().keySet();
            // Get an id for the tunnel domain such that that id does not
            // correspond to an already existing switch.
            while(switches.contains(tid)) {
                tid++;
            }
        }
        return tid;
    }

    /**
     * Return tunnel domain identifier.
     * @return
     */
    public Long getTunnelDomainId() {
        return new Long(tunnelDomain);
    }

    // ghetto hax
    @Override
    public BetterTopologyInstance getCurrentInstance(boolean tunnelEnabled) {
        if (tunnelEnabled)
            return (BetterTopologyInstance)currentInstance;
        else return (BetterTopologyInstance)this.currentInstanceWithoutTunnels;
    }

    @Override
    public BetterTopologyInstance getCurrentInstance() {
        return this.getCurrentInstance(true);
    }


    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        super.init(context);
        tunnelManager = context.getServiceImpl(ITunnelManagerService.class);
        this.tunnelDetectionQueue = new LinkedBlockingQueue<OrderedNodePair>();
        this.tunnelVerificationQueue = new LinkedBlockingQueue<OrderedNodePair>();
        this.tunnelStatusMap = new ConcurrentHashMap<Long,
                ConcurrentHashMap<Long, TunnelEvent>>();
        tunnelDetectionTime = this.TUNNEL_DETECTION_TIMEOUT_MS;
        tunnelVerificationTime = this.TUNNEL_VERIFICATION_TIMEOUT_MS;
    }

    @Override
    public void startUp(ModuleContext context) {
        super.startUp(context);
        if (tunnelManager != null) {
            tunnelManager.addListener(this);
        } else {
            log.warn("Cannot listen to tunnel manager as the module is not loaded.");
        }
     }

    // ***************
    // IRoutingService
    // ***************

    @Override
    public ArrayList<Route> getRoutes(long srcDpid, long dstDpid, boolean tunnelEnabled) {
        // return multipath routes
        BetterTopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getRoutes(srcDpid, dstDpid);
    }

    // *****************
    // IModule
    // *****************

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(ITopologyService.class);
        l.add(IBetterTopologyService.class);
        l.add(IRoutingService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m =
            new HashMap<Class<? extends IPlatformService>,
                IPlatformService>();
        // We are the class that implements the service
        m.put(ITopologyService.class, this);
        m.put(IBetterTopologyService.class, this);
        m.put(IRoutingService.class, this);
        return m;

    }

    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(ILinkDiscoveryService.class);
        l.add(IThreadPoolService.class);
        l.add(IControllerService.class);
        l.add(IRestApiService.class);
        l.add(ITunnelManagerService.class);
        return l;
    }

    @Override
    protected void addRestletRoutable() {
        restApi.addRestletRoutable(new TopologyWebRoutable());
    }

    // *****************
    // IBetterTopologyService
    // *****************

    @Override
    public Set<BroadcastDomain> getBroadcastDomains() {
        return getCurrentInstance(true).getBroadcastDomains();
    }

    @Override
    public Map<Long, Object> getHigherTopologyNodes() {
        return getCurrentInstance(true).getHTNodes();
    }

    @Override
    public Map<Long, Set<Long>> getHigherTopologyNeighbors() {
        return getCurrentInstance(true).getHTNeighbors();
    }

    @Override
    public Map<Long, Map<Long, Long>> getHigherTopologyNextHops() {
        return getCurrentInstance(true).getHTNextHops();
    }

    @Override
    public Map<Long, Long> getL2DomainIds() {
        return getCurrentInstance(true).getL2DomainIds();
    }

    @Override
    public Map<OrderedNodePair, Set<NodePortTuple>> getAllowedUnicastPorts() {
        return getCurrentInstance(true).getAllowedPorts();
    }

    @Override
    public Map<OrderedNodePair, NodePortTuple> getAllowedIncomingBroadcastPorts() {
        return getCurrentInstance(true).getAllowedIncomingBroadcastPorts();
    }

    @Override
    public Map<NodePortTuple, Set<Long>> getAllowedPortToBroadcastDomains() {
        return getCurrentInstance(true).getAllowedPortsToBroadcastDomains();
    }

    // *****************
    //  Tunnel liveness methods and IBetterTopology Service
    // *****************
    private void addToTunnelStatus(TunnelEvent event) {
        long src = event.getSrcDPID();
        long dst = event.getDstDPID();

        // Only add to tunnel status if the switch is
        // known and is in active state. This is to ensure that
        // we will have a valid event in the future that would
        // allow us to remove this entry from the status queue.
        if (controllerProvider.getSwitches().get(src) == null ||
                tunnelManager.isTunnelActiveByDpid(src) == false ||
                controllerProvider.getSwitches().get(dst) == null ||
                tunnelManager.isTunnelActiveByDpid(dst) == false)
            return;


        tunnelStatusMap.putIfAbsent(src,
                new ConcurrentHashMap<Long, TunnelEvent>());
        tunnelStatusMap.get(src).put(dst, event);

        tunnelStatusMap.putIfAbsent(dst,
                new ConcurrentHashMap<Long, TunnelEvent>());
        tunnelStatusMap.get(dst).put(src, event);
    }

    private void removeFromTunnelStatus(OrderedNodePair onp) {
        long src = onp.getSrc();
        long dst = onp.getDst();

        ConcurrentHashMap<Long, TunnelEvent> map;
        map = tunnelStatusMap.get(src);
        if (map != null) {
            map.remove(dst);
        }

        map = tunnelStatusMap.get(dst);
        if (map != null) {
            map.remove(src);
        }
    }

    private void removeFromTunnelStatus(long sw) {
        tunnelStatusMap.remove(sw);
        for(Long othersw: tunnelStatusMap.keySet()) {
            tunnelStatusMap.get(othersw).remove(sw);
        }
    }

    /**
     * The tunnel liveness detection and verification logic works as part
     * of the miscellaneous periodic events.
     */
    @Override
    protected void handleMiscellaneousPeriodicEvents() {
        tunnelDetectionTime -= TOPOLOGY_COMPUTE_INTERVAL_MS;
        tunnelVerificationTime -= TOPOLOGY_COMPUTE_INTERVAL_MS;

        if (tunnelVerificationTime <= 0) {
            tunnelVerificationTime = this.TUNNEL_VERIFICATION_TIMEOUT_MS;
            Set<OrderedNodePair> npSet = new HashSet<OrderedNodePair>();
            npSet.addAll(tunnelVerificationQueue);
            tunnelVerificationQueue.clear();
            if (!npSet.isEmpty()) {
                for (OrderedNodePair onp: npSet) {
                    TunnelEvent event = new TunnelEvent(onp.getSrc(),
                                                        onp.getDst(),
                                                        TunnelLinkStatus.DOWN);
                    log.warn("Tunnel link failed. src-dpid: {}, dst-dpid: {}",
                                HexString.toHexString(onp.getSrc()),
                                HexString.toHexString(onp.getDst()));

                    addToTunnelStatus(event);
                }
            }
        }

        if (tunnelDetectionTime <= 0) {
            tunnelDetectionTime = this.TUNNEL_DETECTION_TIMEOUT_MS;
            while (tunnelDetectionQueue.peek() != null) {
                OrderedNodePair onp = tunnelDetectionQueue.remove();
                this.verifyTunnelLiveness(onp.getSrc(), onp.getDst());
            }
        }
    }

    public BlockingQueue<OrderedNodePair> getTunnelDetectionQueue() {
        return tunnelDetectionQueue;
    }

    public BlockingQueue<OrderedNodePair> getTunnelVerificationQueue() {
        return tunnelVerificationQueue;
    }

    /**
     *  When a tunnel link addorUpdate event occurs, we need to remove
     *  the corresponding tunnel from the tunnelDetectionQueue as we have
     *  detected the tunnel destination switch port.
     */
    @Override
    protected void addOrUpdateTunnelLink(long srcId, short srcPort, long dstId,
                                         short dstPort) {
        // Here, we need to remove the link from the check
        // and add the status that the tunnel is up.
        OrderedNodePair onp = new OrderedNodePair(srcId, dstId);
        tunnelDetectionQueue.remove(onp);
        tunnelVerificationQueue.remove(onp);
        removeFromTunnelStatus(onp);
    }

    @Override
    public void detectTunnelSource(long srcDPID, long dstDPID) {

        OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);

        // Add this event to the detection queue only if this event
        // is not already in the detection or verification queues.
        if (!tunnelVerificationQueue.contains(onp) &&
                !tunnelDetectionQueue.contains(onp))
            tunnelDetectionQueue.add(onp);
    }

    @Override
    public void detectTunnelDestination(long srcDPID, long dstDPID) {
        OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);
        tunnelDetectionQueue.remove(onp);
        tunnelVerificationQueue.remove(onp);
        removeFromTunnelStatus(onp);
    }

    @Override
    public void verifyTunnelOnDemand(long srcDPID, long dstDPID) {
        detectTunnelSource(srcDPID, dstDPID);
        detectTunnelSource(dstDPID, srcDPID);
    }

    private void verifyTunnelLiveness(long srcDPID, long dstDPID) {

        if (tunnelManager == null) {
            log.warn("Cannot veirfy tunnel without tunnel manager.");
            return;
        }

        // If the tunnel end-points are not active, there's no point in
        // verifying liveness.
        if (!tunnelManager.isTunnelActiveByDpid(srcDPID)) {
            if (log.isTraceEnabled()) {
                log.trace("Switch {} is not in tunnel active state," +
                        " cannot verify tunnel liveness.", srcDPID);
            }
            return;
        }
        if (!tunnelManager.isTunnelActiveByDpid(dstDPID)) {
            if (log.isTraceEnabled()) {
                log.trace("Switch {} is not in tunnel active state," +
                        " cannot verify tunnel liveness.", dstDPID);
            }
            return;
        }

        // At this point, both endpoints are tunnel active.
        Short srcPort = tunnelManager.getTunnelPortNumber(srcDPID);
        Integer dstIpAddr = tunnelManager.getTunnelIPAddr(dstDPID);

        IOFSwitch iofSwitch = controllerProvider.getSwitches().get(srcDPID);
        if (iofSwitch == null) {
            if (log.isTraceEnabled()) {
                log.trace("Cannot send tunnel LLDP as switch object does " +
                        "not exist for DPID {}", srcDPID);
            }
            return;
        }

        // Generate and send an LLDP to the tunnel port of srcDPID
        OFPacketOut po = linkDiscovery.generateLLDPMessage(srcDPID,
                                                           srcPort.shortValue(),
                                                           true, false);

        List<OFAction> actions = new ArrayList<OFAction>();
        short actionsLength = 0;

        // Set the tunnel destination action
        OFActionTunnelDstIP tunnelDstAction =
                new OFActionTunnelDstIP(dstIpAddr.intValue());
        actions.add(tunnelDstAction);
        actionsLength += tunnelDstAction.getLengthU();

        // Set the output port action
        OFActionOutput outputAction = new OFActionOutput();
        outputAction.setPort(srcPort.shortValue());
        actions.add(outputAction);
        actionsLength += outputAction.getLengthU();

        po.setActions(actions);
        po.setActionsLength(actionsLength);
        po.setLengthU(po.getLengthU() + actionsLength);

        try {
            iofSwitch.write(po, null);
            iofSwitch.flush();
            // once the LLDP is written, add the tunnel event to the
            // checkTunnelLivenessQueue
            OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);
            this.tunnelVerificationQueue.add(onp);
        } catch (IOException e) {
            log.error("Failure sending LLDP out port {} on switch {}",
                      new Object[] { srcPort, iofSwitch.getStringId() }, e);
        }
    }

    @Override
    public void clearTunnelLivenessState() {
        this.tunnelStatusMap.clear();
    }

    /**
     * Get all the tunnel events from the hashmap.
     * There could be duplicates as the events are indexed based on
     * src and dst DPIDs. So, it is first put into a set and then
     * changed to a list.
     */
    public List<TunnelEvent> getTunnelLivenessState() {
        HashSet<TunnelEvent> eventSet = new HashSet<TunnelEvent>();

        for(Long src: tunnelStatusMap.keySet()) {
            ConcurrentHashMap<Long, TunnelEvent> map =
                    tunnelStatusMap.get(src);
            if (map != null)
                eventSet.addAll(map.values());
        }
        return new ArrayList<TunnelEvent>(eventSet);
    }

    /**
     * Get the tunnel liveness state -- bidirectional -- between a given
     * source destination pair.
     */
    public List<TunnelEvent> getTunnelLivenessState(long srcDPID,
                                                    long dstDPID) {
        List<TunnelEvent> eventList = new ArrayList<TunnelEvent>();
        TunnelEvent event;

        event = getTunnelLivenessStateDirectional(srcDPID, dstDPID);
        eventList.add(event);
        event = getTunnelLivenessStateDirectional(dstDPID, srcDPID);
        eventList.add(event);

        return eventList;
    }

    /**
     * Get the status of a specific directed tunnel from
     * srcDPID to dstDPID.
     */
    private TunnelEvent getTunnelLivenessStateDirectional(long srcDPID,
                                                    long dstDPID) {
        OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);
        TunnelEvent event = null;
        if (tunnelStatusMap.containsKey(onp.getSrc())) {
            event = tunnelStatusMap.get(onp.getSrc()).get(onp.getDst());
        }

        if (event == null) {
            // The tunnel event doesn't exist.  This could either mean
            // that one or both switch ports are not tunnel capable/active
            // or the tunnel is up.
            Short srcPort = tunnelManager.getTunnelPortNumber(srcDPID);
            Short dstPort = tunnelManager.getTunnelPortNumber(dstDPID);
            if (srcPort == null || dstPort == null) {
                event = new TunnelEvent(srcDPID, dstDPID,
                                        TunnelLinkStatus.NOT_ENABLED);
            } else if (!tunnelManager.isTunnelActiveByDpid(srcDPID) ||
                    !tunnelManager.isTunnelActiveByDpid(dstDPID)) {
                // As one or both of the endpoints is not in active
                // state, tunnel link is in down state.
                event = new TunnelEvent(srcDPID, dstDPID,
                        TunnelLinkStatus.DOWN);
            }
            else {
                // If no entry is present and the tunnel endpoints are active
                // then the tunnel is up.
                event = new TunnelEvent(srcDPID, dstDPID,
                                        TunnelLinkStatus.UP);
            }
        }
        return event;
    }

    /**
     * Get the timeout to detect if the last hop tunnel flowmod was received
     * or not. The value is in milliseconds.
     * @return
     */
    public int geTunnelDetectionTimeout() {
        return TUNNEL_DETECTION_TIMEOUT_MS;
    }

    /**
     * Set the timetout to detect if the last hop tunnel flowmod was received
     * or not.  The value is in milliseconds.
     * @param time_ms
     */
    public void setTunnelDetectionTimeout(int time_ms) {
        TUNNEL_DETECTION_TIMEOUT_MS = time_ms;
    }

    /**
     * Get the timeout for LLDP reception on tunnel ports.  The value is
     * in milliseconds.
     * @return
     */
    public int getTunnelVerificationTimeout() {
        return TUNNEL_VERIFICATION_TIMEOUT_MS;
    }

    /**
     * Set the timeout for LLDP reception on tunnel ports. The value is
     * in milliseconds.
     * @param time_ms
     */
    public void setTunnelVerificationTimeout(int time_ms) {
        TUNNEL_VERIFICATION_TIMEOUT_MS = time_ms;
    }

    /**
     * To use this method to finally check for tunnel status when flowmods
     * are being removed.
     */
    private Command flowRemoved(IOFSwitch sw, OFFlowRemoved msg,
                                ListenerContext cntx) {

        /*
        if (tunnelManager == null) return Command.CONTINUE;
        OFMatch match = msg.getMatch();

        long dpid = sw.getId();
        int srcIp = match.getNetworkSource();
        int dstIp = match.getNetworkDestination();

        Long srcDPID = tunnelManager.getSwitchDpid(srcIp);
        Long dstDPID = tunnelManager.getSwitchDpid(dstIp);

        if (srcDPID != null && dstDPID != null && !srcDPID.equals(dstDPID)) {
            if (srcDPID.equals(dpid)) {
                // the traffic is from the tunnel IP to tunnel IP.
                this.detectTunnelSource(srcDPID, dstDPID);
            } else if (dstDPID.equals(dpid)) {
                // the traffic is destined
                this.detectTunnelDestination(srcDPID, dstDPID);
            }
        }
        */
        return Command.CONTINUE;
    }

    @Override
    public void removeTunnelPort(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        tunnelPorts.remove(npt);
        tunnelPortsUpdated = true;

        // This call is not present in TopologyManager
        removeFromTunnelStatus(sw);
    }

    // *****************
    //  ITunnelManagerListener methods
    // *****************

    @Override
    public void tunnelPortActive(long dpid, short tunnelPortNumber) {
        LDUpdate ldupdate = new LDUpdate(dpid, tunnelPortNumber,
                                UpdateOperation.TUNNEL_PORT_ADDED);
        ldUpdates.add(ldupdate);
    }

    @Override
    public void tunnelPortInactive(long dpid, short tunnelPortNumber) {
        LDUpdate ldupdate = new LDUpdate(dpid, tunnelPortNumber,
                                         UpdateOperation.TUNNEL_PORT_REMOVED);
        ldUpdates.add(ldupdate);
    }

    // *****************
    // IOFMessageListener methods
    // *****************

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           ListenerContext cntx) {
        switch (msg.getType()) {
            case FLOW_REMOVED:
                // this is meant for tunnel liveness detection but is currently unused
                return this.flowRemoved(sw, (OFFlowRemoved) msg, cntx);

            case PACKET_IN:
                // this takes the sdnplatform Topology Manager path
                return this.processPacketInMessage(sw,
                                                   (OFPacketIn) msg, cntx);
            default:
                break;
        }

        return Command.CONTINUE;
    }
}
