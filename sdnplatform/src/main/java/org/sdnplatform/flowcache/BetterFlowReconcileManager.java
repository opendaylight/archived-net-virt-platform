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

package org.sdnplatform.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.util.AppCookie;
import org.sdnplatform.core.util.ListenerDispatcher;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceListener;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.LDUpdate;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.UpdateOperation;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.routing.ForwardingBase;
import org.sdnplatform.topology.ITopologyListener;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * This class registers for various network events that may require flow 
 * reconciliation. Examples include host-move, new attachment-point,
 * switch connection etc. 
 * 
 */
public class BetterFlowReconcileManager extends FlowReconcileManager
        implements IFlowQueryHandler, ITopologyListener,
        IOFMessageListener, IHAListener {

    /** The logger. */
    private static Logger logger =
                        LoggerFactory.getLogger(BetterFlowReconcileManager.class);
    /** The Constant ofwSrcDestValid which is true when both source and 
     * destination data layer addresses are not wildcarded. */
    public static final int ofwSrcDestValid =
                                OFMatch.OFPFW_DL_SRC | OFMatch.OFPFW_DL_DST;
    /** The Constant ofwDestValidSrcAny which is true when destination 
     * data layer address is not wildcarded and the source data layer
     * address is wildcarded. */
    public static final int ofwDestValidSrcAny = OFMatch.OFPFW_DL_DST;
    
    /** The controllerProvider. */
    protected IControllerService controllerProvider;
    protected IDeviceService deviceManager;
    protected ILinkDiscoveryService linkDiscoveryMgr;
    protected ITopologyService topology;
    protected IFlowCacheService betterFlowCacheMgr;

    /** The number of times flow query resp handler method was called. */
    protected AtomicInteger flowQueryRespHandlerCallCount;
    
    /** The number of times switch query resp handler method was called. */
    protected AtomicInteger switchQueryRespHandlerCallCount;
    
    /** The last fc query resp. */
    protected volatile FlowCacheQueryResp lastFCQueryResp;
    
    /**
     * Data structure for pending switch query responses
     */
    protected ConcurrentHashMap<PendingSwRespKey,
                    PendingSwitchResp>pendSwRespMap;

    protected DeviceListenerImpl deviceListener;

    @Override
    protected void updateFlush() {
        betterFlowCacheMgr.updateFlush();
    }
    
    // ******************
    // IOFMessageListener
    // ******************

    @Override
    public IOFMessageListener.Command receive(IOFSwitch sw, OFMessage msg,
                                              ListenerContext cntx) {
        switchQueryRespHandlerCallCount.incrementAndGet();
        
        switch (msg.getType()) {    
            case STATS_REPLY:
                OFStatisticsReply statsReplyMsg = (OFStatisticsReply)msg;
                processStatsReplyMsg(sw, statsReplyMsg, cntx);
                return IOFMessageListener.Command.STOP;

            default:
                return IOFMessageListener.Command.STOP;
        }
    }
    
    /**
     * Updates the flows to a device after the device moved to a new location
     * <p>
     * Queries the flow-cache to get all the flows destined to the given device.
     * Reconciles each of these flows by potentially reprogramming them to its
     * new attachment point
     *
     * @param device      device that has moved
     * @param fcEvType    Event type that triggered the update
     *
     */
    @Override
    public void updateFlowForDestinationDevice(IDevice device,
                                               IFlowQueryHandler handler,
                                               FCQueryEvType fcEvType) {

        // Get the flows to this host by querying the flow-cache service
        FCQueryObj fcQueryObj = new FCQueryObj(handler,
                                               null,   // null appName
                                               null,   // null vlan
                                               null,   // null srcDevice
                                               device,
                                               getName(),
                                               fcEvType,
                                               null);
        if (logger.isTraceEnabled()) {
            logger.trace("Update Flow Dest Dev: Submitted fQuery {}",
                                                fcQueryObj.toString());
        }
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        /* We need to also query all routed flows. Routed flows can have
         * a vMAC as match on the first hop switch (and thus in FlowCache) so
         * we won't be able 
         */
        updateVirtualRoutingFlows(fcEvType);
        return;
    }
    
    /**
     * Updates the flows from a device
     * <p>
     * Queries the flow-cache to get all the flows source from the given device.
     * Reconciles each of these flows by potentially reprogramming them to its
     * new attachment point
     *
     * @param device      device where the flow originates
     * @param fcEvType    Event type that triggered the update
     *
     */
    public void updateFlowForSourceDevice(IDevice device,
                                          FCQueryEvType fcEvType) {

        // Get the flows to this host by querying the flow-cache service
        FCQueryObj fcQueryObj = new FCQueryObj(this,
                                               null,   // null appName
                                               null,   // null vlan
                                               device,
                                               null,   // null destDevice
                                               getName(),
                                               fcEvType,
                                               null);
        if (logger.isDebugEnabled()) {
            logger.debug("Update Flow Source Dev: Submitted fQuery {}",
                                                fcQueryObj.toString());
        }
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        /* no need to query VRS flows here */
        return;
    }
    
    
    /**
     * Updates all flows installed by VirtualRoutingService 
     * 
     * @param fcEvType Event type that triggered the update
     */
    protected void updateVirtualRoutingFlows(FCQueryEvType fcEvType) {
        FCQueryObj fcQueryObj = new FCQueryObj(this,
                                   IVirtualRoutingService.VRS_FLOWCACHE_NAME,
                                   null,   // null vlan
                                   null,   // null srcDevice
                                   null,   // null destDevice
                                   getName(),
                                   fcEvType,
                                   null);
        if (logger.isDebugEnabled()) {
            logger.debug("Update Flow Virtual Routing: Submitted fQuery {}",
                                                fcQueryObj.toString());
        }
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
        return;
    }
    

    // *******************
    // IDeviceListener
    // *******************
    class DeviceListenerImpl implements IDeviceListener {
        @Override
        public void deviceAdded(IDevice device) {
            /** NO-OP, if device was connected before, we would get a deviceMoved
             * event and flows will be reconciled.
             */
            // NO-OP
            if (logger.isTraceEnabled()) {
                logger.trace("Reconciling flows: Device Added: {}", device);
            }
        }

        @Override
        public void deviceRemoved(IDevice device) {
            // NO-OP
            if (logger.isTraceEnabled()) {
                logger.trace("Reconciling flows: Device Removed: {}", device);
            }
        }

        @Override
        public void deviceMoved(IDevice device) {
            /* No need to reconcile flows if the device is removed.
             * The flows destined to the device are not removed.
             * The behavior is the same as the current network.
             */
            if (device.getAttachmentPoints().length > 0) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Reconciling flows: Device moved: {}", device);
                }
                updateFlowForDestinationDevice(device,
                                               BetterFlowReconcileManager.this,
                                               FCQueryEvType.DEVICE_MOVED);

            }
        }

        @Override
        public void deviceIPV4AddrChanged(IDevice device) {
         // NO-OP
        }

        @Override
        public void deviceVlanChanged(IDevice device) {
            // NO-OP
        }

        @Override
        public String getName() {
            return BetterFlowReconcileManager.this.getName();
        }

        @Override
        public boolean isCallbackOrderingPrereq(String type, String name) {
            return name.equals("netVirtmanager") || name.equals("serviceinsertion");
        }

        @Override
        public boolean isCallbackOrderingPostreq(String type, String name) {
            return false;
        }
    }
    
    //***********************
    // ITopologyListener
    //***********************
    @Override
    public void topologyChanged() {
        // Reconcile flows on removed links
        List<LDUpdate> linkUpdates = topology.getLastLinkUpdates();
        for (LDUpdate update : linkUpdates) {
            if (update.getOperation() == UpdateOperation.LINK_REMOVED) {
                removedLink(update.getSrc(), update.getSrcPort(),
                            update.getDst(), update.getDstPort());
            }
        }

        // Reconcile flows on disabled ports
        Set<NodePortTuple> disabledPorts = topology.getBlockedPorts();
        for (NodePortTuple port : disabledPorts) {
            removedPort(port.getNodeId(), port.getPortId());
        }
    }
    
    protected void removedPort(long swid, short port) {
        /* We might get removed port after the switch itself has disconnected
         * In that case handle reprogramming of flows in switch-disconnected
         * event and not in the link down event here */
        IOFSwitch srcSw = controllerProvider.getSwitches().get(swid);

        if ((srcSw == null) || (!srcSw.isConnected())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Ignoring link down of disconnected switch {}",
                                           srcSw==null?"SrcSwitchNull":srcSw);
            }
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Reprogramming flows around the disabled port {}",
            srcSw.getStringId().concat("/".concat(Short.toString(port))));
        }

        // Find all the flows in source switch with the failed output-port
        PendingSwitchResp pendQ =
            new PendingSwitchResp(FCQueryEvType.LINK_DOWN);
        getSwitchFlowsMatchOutputPort(srcSw, port, pendQ);
    }
    
    /* Note that if this link removal causes this cluster to split into two
     * clusters then the attachment point of the devices would change and
     * new attachment points would be learned which would then trigger flow
     * reconciliation via the device-moved code-path. This function handles
     * the case where the cluster is not split, i.e. there are alternate paths
     * in the cluster.
     */
    protected void removedLink(long srcId, short srcPort, long dstId,
                                                            short dstPort) {
        IOFSwitch srcSw = controllerProvider.getSwitches().get(srcId);
        IOFSwitch dstSw = controllerProvider.getSwitches().get(dstId);
        
        if (!topology.isInSameBroadcastDomain(srcId, srcPort, dstId, dstPort)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Reprogramming flows around failed link from {} to {}",
                srcSw==null?"srcSwitchNull":
                    srcSw.getStringId().concat("/".concat(Short.toString(srcPort))),
                dstSw==null?"dstSwitchNull":
                    dstSw.getStringId().concat("/".concat(Short.toString(dstPort))));
            }

            removedPort(srcId, srcPort);
        }
    }
    
    /**
     * Gets the flows from a switch that matches a given output port. This 
     * method is used to handle link down event.
     * TODO - Refactor to use async response from switch
     *
     * @param sw the switch object
     * @param outPort the output port of the switch to match
     * @return the flows in the switch that match the output port
     */
    @LogMessageDoc(level="ERROR",
            message="Failure retrieving flows from switch {switch}, {exception}",
            explanation="Controller is not able to retrieve flows from switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    public void getSwitchFlowsMatchOutputPort(IOFSwitch sw, short outPort,
                                                    PendingSwitchResp pendQ) {
        if (sw != null) {
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.FLOW);
            int requestLength = req.getLengthU();

            OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
            OFMatch match = new OFMatch();
            match.setWildcards(0xffffffff);
            specificReq.setMatch(match);
            specificReq.setOutPort(outPort);
            specificReq.setTableId((byte) 0xff);
            req.setStatistics(Collections.singletonList(
                                                (OFStatistics)specificReq));
            requestLength += specificReq.getLength();

            req.setLengthU(requestLength);
            try {
                if (sw.isConnected()) {
                    int transId = sw.getNextTransactionId();
                    /* Need to add the pending resp. to the map before the
                     * request is sent to the switch; otherwise the response
                     * from the switch may come before it is added to map
                     * resulting in "unexpected" response.
                     */
                    PendingSwRespKey pendSwRespKey =
                            new PendingSwRespKey(sw.getId(), transId);
                    pendSwRespMap.put(pendSwRespKey, pendQ);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Added key {} to pending map",
                                                    pendSwRespKey.toString());
                    }
                    sw.sendStatsQuery(req, transId, this);
                    return;
                }
            } catch (Exception e) {
                logger.error("Failure retrieving flows from switch {}, {}",
                             sw, e);
            }
        }
        return;
    }
    
    private void processStatsReplyMsg(IOFSwitch sw, 
                                      OFStatisticsReply statsReplyMsg,
                                      ListenerContext cntx) {
        /* Match the response with expected response */
        PendingSwRespKey pRKey = new PendingSwRespKey(sw.getId(),
                                                      statsReplyMsg.getXid());
        PendingSwitchResp pRResp = pendSwRespMap.get(pRKey);
        
        if (pRResp == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unexpected reply from switch {} key {}",
                        sw.getId(), pRKey.toString());
            }
            return;
        }
        
        if (pRResp.getEvType() != FCQueryEvType.LINK_DOWN) {
            if (logger.isDebugEnabled()) {
                logger.debug("Unexpected event type {} for key {}",
                        pRResp.getEvType(), pRKey);
            }
            return;
        }
        List<? extends OFStatistics> rsp = statsReplyMsg.getStatistics();
        if ((rsp == null) || (rsp.isEmpty())) {
            /* No flow to reroute */
            return;
        }
        IDevice dSrc;
        IDevice dDest;
        FCQueryObj fcQueryObj;
        
        if (logger.isTraceEnabled()) {
            logger.trace("Handle statsReply msg from switch {}",
                         HexString.toHexString(sw.getId()));
        }
        for (OFStatistics rspIdx : rsp) {
            OFFlowStatisticsReply rspOne = (OFFlowStatisticsReply)rspIdx;
            OFMatch match = rspOne.getMatch();
            /* Check if the flow mod is:
             *   (a) specific source mac to specific dest mac 
             *       (wildcard = 
             *   (b) from any source mac to specific destination mac
             *       (wildcard = 
             */
            if (logger.isTraceEnabled()) {
                logger.trace("Handle statsReply msg from switch {} OFStats: " +
                           "{}, match 0x{}",
                           new Object[] {HexString.toHexString(sw.getId()),
                           rspOne, Integer.toHexString(match.getWildcards())});
            }
            
            if (AppCookie.extractApp(rspOne.getCookie()) !=
                    ForwardingBase.FORWARDING_APP_ID)
                continue;
        
            if ((match.getWildcards() &
                    BetterFlowReconcileManager.ofwSrcDestValid) == 0) {
                /* Case (a) */
                long srcMac = Ethernet.toLong(match.getDataLayerSource());
                long dstMac = Ethernet.toLong(match.getDataLayerDestination());
                short vlan = match.getDataLayerVirtualLan();
                int srcNWAddr = match.getNetworkSource();
                int dstNWAddr = match.getNetworkDestination();
                long srcDpid = sw.getId();
                int srcPort = match.getInputPort();

                dSrc = deviceManager.findDevice(srcMac, 
                                            vlan,
                                            srcNWAddr,
                                            srcDpid,
                                            srcPort);
                if (dSrc == null) {
                    // Get all the flows to dest from flow cache
                    Iterator<? extends IDevice> dIter =
                            deviceManager.queryDevices(dstMac, vlan, dstNWAddr,
                                                       null, null);
                    while (dIter.hasNext()) {
                        dDest = dIter.next();
                        fcQueryObj = new FCQueryObj(this,
                                                   null,   // null appName
                                                   null,   // null vlan
                                                   null,   // null srcDevice
                                                   dDest,
                                                   getName(),
                                                   FCQueryEvType.LINK_DOWN,
                                                   null);
                        
                        if (logger.isDebugEnabled()) {
                            logger.debug("Update Flow Link Down, Src-Only: " +
                                         "Submittedd fQuery {}",
                                          fcQueryObj.toString());
                        }
                        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);

                    }
                } else {
                    dDest =  deviceManager.findClassDevice(dSrc.getEntityClass(),
                                                      dstMac,
                                                      vlan,
                                                      dstNWAddr);
                    if (dDest == null) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("dest {} is known. skip core switch " +
                                "flow reconciliation.",
                                HexString.toHexString(
                                 match.getDataLayerDestination()).substring(6));
                        }
                        continue;
                    }
                    /* Get all the flows between src and dest from flow cache */
                    fcQueryObj = new FCQueryObj(this,
                                               null,   // null appName
                                               null,   // null vlan
                                               dSrc,
                                               dDest,
                                               getName(),
                                               FCQueryEvType.LINK_DOWN,
                                               null);
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("Update Flow Link Down: src & dst, " +
                                     "Submitted fQuery {}",
                                     fcQueryObj.toString());
                    }
                    betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);

                }
            } else {
                if ((match.getWildcards() &
                        BetterFlowReconcileManager.ofwDestValidSrcAny) == 0) {
                    /* Case (b)
                     * Src address is ANY - find all flows to the destination
                     * address */
                    long dstMac =
                            Ethernet.toLong(match.getDataLayerDestination());
                    Short vlan = null;

                    if ((match.getWildcards() & OFMatch.OFPFW_DL_VLAN) == 0) {
                        vlan = match.getDataLayerVirtualLan();
                    }

                    Iterator<? extends IDevice> dIter =
                            deviceManager.queryDevices(dstMac, vlan,
                                                       null, null, null);
                    while (dIter.hasNext()) {
                        dDest = dIter.next();
                        // Get all the flows to dest from flow cache
                        fcQueryObj = new FCQueryObj(this,
                                                   null,   // null appName
                                                   null,   // null vlan
                                                   null,   // null srcDevice
                                                   dDest,
                                                   getName(),
                                                   FCQueryEvType.LINK_DOWN,
                                                   null);
                        
                        if (logger.isDebugEnabled()) {
                            logger.debug("Update Flow Link Down, Src-Any: " +
                                         "Submitted fQuery {}",
                                         fcQueryObj.toString());
                        }
                        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);

                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Link Removed: Unknown wildcard 0x{}",
                                Integer.toHexString(match.getWildcards()));
                    }
                }
            }
        }
        /* need to update all VRS flows. Since they can have a vMAC on the 
         * first hop flow mod we can't find them by querying for the dest
         * device. 
         */
        updateVirtualRoutingFlows(FCQueryEvType.LINK_DOWN);
    }

    /** Handle the flowQuery Response.
     *  It handles deviceMove, link events, and topologyChanges.
     */
    @Override
    public void flowQueryRespHandler(FlowCacheQueryResp flowResp) {
        lastFCQueryResp = flowResp;
        flowQueryRespHandlerCallCount.incrementAndGet();
        flowQueryGenericHandler(flowResp);
    }
    
    // IModule

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
            new ArrayList<Class<? extends IPlatformService>>();
        l.add(IFlowReconcileService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> 
                                                            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m = 
            new HashMap<Class<? extends IPlatformService>,
                IPlatformService>();
        m.put(IFlowReconcileService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> 
                                                    getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IControllerService.class);
        l.add(IDeviceService.class);
        l.add(IFlowCacheService.class);
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        super.init(context);
        controllerProvider =
                context.getServiceImpl(IControllerService.class);
        deviceManager =
            context.getServiceImpl(IDeviceService.class);
        betterFlowCacheMgr =
                context.getServiceImpl(IFlowCacheService.class);
        linkDiscoveryMgr =
            context.getServiceImpl(ILinkDiscoveryService.class);
        topology =
            context.getServiceImpl(ITopologyService.class);
        flowReconcileListeners = 
                new ListenerDispatcher<OFType, IFlowReconcileListener>();
        flowQueryRespHandlerCallCount = new AtomicInteger();
        switchQueryRespHandlerCallCount = new AtomicInteger();
        pendSwRespMap = new ConcurrentHashMap<PendingSwRespKey,
                PendingSwitchResp>();
        deviceListener = new DeviceListenerImpl();
    }

    @Override
    public void startUp(ModuleContext context) {
        super.startUp(context);
        /* To get switch add and remove notifications */
        controllerProvider.addOFMessageListener(OFType.STATS_REPLY, this);
        controllerProvider.addHAListener(this);
        topology.addListener(this);
        deviceManager.addListener(this.deviceListener);
    }
    
    // IHAListener
    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                // no-op for now, assume it will re-learn all it's state
                break;
            case SLAVE:
                if (logger.isDebugEnabled()) {
                    logger.debug("Clearing state due to " +
                        "HA change from MASTER->SLAVE");
                }
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
            Map<String, String> removedControllerNodeIPs
            ) {
        
    }
    
    protected void clearCachedState() {
        flowQueryRespHandlerCallCount.set(0);
        switchQueryRespHandlerCallCount.set(0);
        lastFCQueryResp = null;
    }
    
    @Override
    public String getName() {
        return "FlowReconcileManager";
    }
    
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }
}
