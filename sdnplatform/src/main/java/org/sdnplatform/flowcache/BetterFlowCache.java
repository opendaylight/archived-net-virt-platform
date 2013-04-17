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
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
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
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.FlowCacheObj.FCEntry;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/**
 * Flow Cache Class - for maintaining the flow cache object and for
 * supporting the APIs to query flows from the flow cache.
 * <p>
 * This class is a IOFMessageListener for listening to flow-mod removal
 * messages. It implements the public interfaces in IFlowCache.java
 *
 * @author subrata
 *
 */
public class BetterFlowCache
        implements IModule, IOFMessageListener, IFlowCacheService,
                   IOFSwitchListener, IHAListener {

    /** The Constant for maximum number of flows that flow cache can keep. */
    final protected static int MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT=100000;

    /** The Constant for the flow-cache occupancy percentage after which flows
     *  are deleted from the cache instead of being deactivated. */
    final private static int ALMOST_FULL_PERCENTAGE = 80;

    /** The Constant ALMOST_FULL_SIZE. */
    final private static int ALMOST_FULL_SIZE =
            (MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT*ALMOST_FULL_PERCENTAGE)/100;

    /** The Constant QUERY_RSP_BATCH_SIZE. */
    final private static int QUERY_RSP_BATCH_SIZE = 100;

    /** 
     * Scan Count of the flow. The scan count is incremented every time the
     * flow cache is scanned for stale flows. This is done in 
     * scanForStaleFlows() which is scheduled to run periodically. If the
     * scanCount is > STALE_SCAN_COUNT then the entry is deleted. Periodically
     * the flow tables of the switches are queried. Then the flows received
     * from the switches are looked up in the flow cache. If there is a match
     * then the scan count is reset to zero. Thus for all active flows the
     * scan count is expected to be 0 or 1.
     */
    final private static int STALE_SCAN_COUNT=2;

    /* Scan flow tables of each switch every 15 minutes in a staggered way */
    final private static int SWITCH_FLOW_TBL_SCAN_INITIAL_DELAY_MSEC = 5*60*1000;
    final private static int SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC  = 15*60*1000;

    /** The logger. */
    protected static Logger logger = LoggerFactory.getLogger(BetterFlowCache.class);
    protected IControllerService controllerProvider;
    protected IDeviceService      deviceManager;
    protected IThreadPoolService threadPool;
    protected int periodicSwScanInitDelayMsec; // in ms
    protected int periodicSwScanIntervalMsec;  // in ms
    
    protected SendPeriodicFlowQueryToSwitches fqTask;
    
    public class FCCounters {
        public FCCounters() {}
        
        // Copy constructor
        public FCCounters(FCCounters other) {
            this.activeCnt = other.activeCnt;
            this.inactiveCnt = other.inactiveCnt;
            this.addCnt = other.addCnt;
            this.delCnt = other.delCnt;
            this.activatedCnt = other.activatedCnt;
            this.deactivatedCnt = other.deactivatedCnt;
            this.cacheHitCnt = other.cacheHitCnt;
            this.missCnt = other.missCnt;
            this.dampenCnt = other.dampenCnt;
            this.notStoredFullCnt = other.notStoredFullCnt;
            this.fcObjFreedCnt = other.fcObjFreedCnt;
            this.notDampenedCnt = other.notDampenedCnt;
            this.unknownOperCnt = other.unknownOperCnt;
        }
        
        /** Number of active entries in flow cache. */
        protected long activeCnt;

        /** Number of inactive entries in flow cache. Total number of flows in 
         * flow cache is activeFlowCnt + inactiveFlowCnt.
         */
        protected long inactiveCnt;
        
        /* The following numbers are used to track performance and operations of
         * flow cache. */

        /** Number of successful add operations - this could result in a new
         *  entry being added or a matching inactive entry to be activated. */
        private long addCnt;

        /** Number of successful delete operations. */
        protected long delCnt;

        /** Number of times an inactive flow was activated. */
        protected long activatedCnt;

        /** Number of times a flow was marked inactive. */
        protected long deactivatedCnt; // 

        /** Number of times a flow lookup resulted in success. */
        private long cacheHitCnt;

        /** Number of times a flow lookup was unsuccessful. */
        private long missCnt;

        /** Number of times a flow add was dampened. */
        private long dampenCnt;

        /** Number of times add operation failed because the flow cache was full. */
        private long notStoredFullCnt;

        /** Number of times a flow cache object was freed. */
        private long fcObjFreedCnt;

        /** Number of times an add operation was not dampened because the flow was
         *  programmed earlier than the damp threshold time. */
        private long notDampenedCnt;

        /** Number of unknown operations to flow cache. Expected to remain zero. */
        private long unknownOperCnt;
        
        protected void clearCounts() {
            activeCnt = 0;
            inactiveCnt = 0;
            addCnt = 0;
            delCnt = 0;
            activatedCnt = 0;
            deactivatedCnt = 0;
            cacheHitCnt = 0;
            missCnt = 0;
            dampenCnt = 0;
            notStoredFullCnt = 0;
            fcObjFreedCnt = 0;
            notDampenedCnt = 0;
            unknownOperCnt = 0;
        }
        
        /**
         * Update flow cache counters.
         *
         * @param oper the operation type
         */
        protected void updateCounts(FCOper oper) {
            switch (oper) {
                case NEW_ENTRY:
                    activeCnt++;
                    addCnt++;
                    break;
                case DAMPENED:
                    cacheHitCnt++;
                    addCnt++;
                    dampenCnt++;
                    break;
                case NOT_FOUND:
                    missCnt++;
                    break;
                case NOT_STORED_FULL:
                    addCnt++;
                    notStoredFullCnt++;
                    break;
                case ACTIVATED:
                    cacheHitCnt++;
                    addCnt++;
                    activeCnt++;
                    activatedCnt++;
                    inactiveCnt--;
                    break;
                case DELETED_ACTIVE:
                    cacheHitCnt++;
                    activeCnt--;
                    delCnt++;
                    break;
                case DELETED_INACTIVE:
                    cacheHitCnt++;
                    inactiveCnt--;
                    delCnt++;
                    break;
                case FCOBJ_FREE:
                    fcObjFreedCnt++;
                    break;
                case NOT_DAMPENED:
                    cacheHitCnt++;
                    addCnt++;
                    notDampenedCnt++;
                    break;
                case DEACTIVATED:
                    cacheHitCnt++;
                    inactiveCnt++;
                    deactivatedCnt++;
                    activeCnt--;
                    break;
                case NOP:
                case NOT_ACTIVE:
                    /* No operations to be done */
                    break;
                case CLEAR_COUNTERS:
                    activeCnt=0;
                    deactivatedCnt=0;
                    break;
                default:
                    unknownOperCnt++; /* should be zero! */
                    break;
            }
        }
    }
    
    //************************
    // Flow Cache Core Members
    //************************
    public class BfcDb {

        /**
         * Application name for specific flow cache instance. All flows
         * stored an instance of flow cache are from this application
         * A flow cache is created for each application that needs 
         * flow cache service for flows programmed by that application
         */
        private String applName;

        /**
         * ApplInstanceName -> vlan -> dest-Mac -> src-Mac -> flow-cache-objects
         * <p>
         * For XYZ application, the ApplInstanceName is the XYZ name
         * Flow cache is maintained as hierarchical hash tables so that we are
         * efficiently able to respond to queries such as "get all flows in XYZ"
         * or "get all flows to/from a host in the XYZs that the host is member
         * of" or "get all flows in a VLAN within a XYZ" etc.</p>
         */
        private ConcurrentHashMap<String, ConcurrentHashMap 
                    <Short, ConcurrentHashMap<Long,
                            ConcurrentHashMap<Long, FlowCacheObj>>>> flowCache;

        /** Maximum size of the flow cache in terms of number of flows. This is
         * initialized to its default value of MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT. */
        private long maxFlows;
        
        /** Flowcache counters */
        protected FCCounters fcCounters;

        protected final ThreadLocal<FCCounters> fcLocalCounters =
                new ThreadLocal<FCCounters>() {
            @Override
            protected FCCounters initialValue() {
                return new FCCounters();
            }
        };
        
        /** Number of times a flow-cache entry had to be purged explicitly by
         *  querying the switch flow table.*/
        private long flowModRemovalMsgLossCnt;

        /**
         * List of switches for which the flow-cache doesn't have the complete
         * set of flows and hence must be queried in order to respond to a 
         * flow-cache query. We maintain the datapath ids of the switches and
         * get the switch objects using the data path ids from sdnplatform 
         * provider when needed.
         */
        private ArrayList<Long> switchesToQuery;

        /**
         * Default constructor
         */
        public BfcDb() {
            this.flowCache = new ConcurrentHashMap<String, ConcurrentHashMap<Short,
                    ConcurrentHashMap<Long, 
                    ConcurrentHashMap<Long, FlowCacheObj>>>>();
            this.maxFlows  = MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT;
            this.switchesToQuery = new ArrayList<Long>(); // Empty initially
            fcCounters           = new FCCounters();
        }
        /**
         * Instantiates a new flow cache and copies the counters from 
         * another BetterFlowCache object. Used in supporting the REST API to
         * retrieve the flow cache counters.
         * <p>
         * This constructor is used to create a new instance of BigFlow Cache
         * copying only the counters and the applInst name and NOT copying the
         * actual flow cache object
         * This is used for quick verification of the flow cache operations
         * and in various flow-cache related tests. If {param} == counters then
         * the REST API GET handler used this constructor to get only the
         * counters and return those
         *
         * @param bfc the bfc to copy into the new instance
         * @param countersOnly copy the the counters only, not the flows
         */
        public BfcDb(BfcDb bfcCore, boolean countersOnly) {
            applName           = bfcCore.applName;
            maxFlows           = bfcCore.maxFlows;
            flowModRemovalMsgLossCnt = bfcCore.flowModRemovalMsgLossCnt;
            fcCounters         = new FCCounters(bfcCore.fcCounters);
            if (!countersOnly) {
                // flowCache = bfc.flowCache.copy(); TODO
            }
        }

        /**
         * Update thread-local counters
         * @param oper
         */
        public void updateCountsLocal(FCOper oper) {
            FCCounters fcLocalCounters = this.fcLocalCounters.get();
            fcLocalCounters.updateCounts(oper);
        }
        
        public void updateFlush() {
            FCCounters fcLocalCounters = this.fcLocalCounters.get();
            synchronized (this.fcCounters) {
                this.fcCounters.activeCnt += fcLocalCounters.activeCnt;
                this.fcCounters.inactiveCnt += fcLocalCounters.inactiveCnt;
                this.fcCounters.addCnt += fcLocalCounters.addCnt;
                this.fcCounters.delCnt += fcLocalCounters.delCnt;
                this.fcCounters.activatedCnt += fcLocalCounters.activatedCnt;
                this.fcCounters.deactivatedCnt += fcLocalCounters.deactivatedCnt;
                this.fcCounters.cacheHitCnt += fcLocalCounters.cacheHitCnt;
                this.fcCounters.missCnt += fcLocalCounters.missCnt;
                this.fcCounters.dampenCnt += fcLocalCounters.dampenCnt;
                this.fcCounters.notStoredFullCnt += fcLocalCounters.notStoredFullCnt;
                this.fcCounters.fcObjFreedCnt += fcLocalCounters.fcObjFreedCnt;
                this.fcCounters.notDampenedCnt += fcLocalCounters.notDampenedCnt;
                this.fcCounters.unknownOperCnt += fcLocalCounters.unknownOperCnt;
                
                fcLocalCounters.clearCounts();
            }
        }
        
        //*************************************************
        // Public getters used for support of REST API call
        // ************************************************

        /**
         * Gets the application name.
         *
         * @return the application name
         */
        public String getApplName() {
            return applName;
        }

        public void setApplName(String applName) {
            this.applName = applName;
        }

        /**
         * Gets the flow cache object. Used in REST API handler.
         *
         * @return the flow cache object
         */
        public ConcurrentHashMap<String, ConcurrentHashMap<Short, 
            ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>>>>
                                                                getFlowCache() {
            return flowCache;
        }

        /**
         * Gets the count of active flows in the flow cache.
         *
         * @return the active flow count
         */
        public long getActiveCnt() {
            return fcCounters.activeCnt;
        }

        /**
         * Gets the count of inactive flows in the flow cache.
         *
         * @return the inactive flow count
         */
        public long getInactiveCnt() {
            return fcCounters.inactiveCnt;
        }

        /**
         * Gets the max flows that the flow cache can store.
         *
         * @return the max flow count
         */
        public long getMaxFlows() {
            return maxFlows;
        }

        /**
         * Gets the count of the add operations.
         *
         * @return the add count
         */
        public long getAddCnt() {
            return fcCounters.addCnt;
        }

        /**
         * Gets the delete count.
         *
         * @return the delete count
         */
        public long getDelCnt() {
            return fcCounters.delCnt;
        }

        /**
         * Gets the activated count.
         *
         * @return the activated count
         */
        public long getActivatedCnt() {
            return fcCounters.activatedCnt;
        }

        /**
         * Gets the deactivated count.
         *
         * @return the deactivated count
         */
        public long getDeactivatedCnt() {
            return fcCounters.deactivatedCnt;
        }

        /**
         * Gets the flow cache hit count.
         *
         * @return the cache hit count
         */
        public long getCacheHitCnt() {
            return fcCounters.cacheHitCnt;
        }

        /**
         * Gets the flow cache miss count.
         *
         * @return the miss count
         */
        public long getMissCnt() {
            return fcCounters.missCnt;
        }

        /**
         * Gets the number of add operations that were dampened as the same flow
         * was programmed within dampen time threshold.
         *
         * @return the dampen count
         */
        public long getDampenCnt() {
            return fcCounters.dampenCnt;
        }

        /**
         * Gets the count of add operations when the cache was full.
         *
         * @return the not stored full count
         */
        public long getNotStoredFullCnt() {
            return fcCounters.notStoredFullCnt;
        }

        /**
         * Gets the flow cache object freed count
         *
         * @return the flow cache object freed count
         */
        public long getFcObjFreedCnt() {
            return fcCounters.fcObjFreedCnt;
        }

        /**
         * Gets the not dampened count.
         *
         * @return the not dampened count
         */
        public long getNotDampenedCnt() {
            return fcCounters.notDampenedCnt;
        }

        /**
         * Gets the unknown operations count.
         *
         * @return the unknown operations count
         */
        public long getUnknownOperCnt() {
            return fcCounters.unknownOperCnt;
        }

        /**
         * Gets the flow mod removal msg loss count. These are the flows that 
         * had to be removed explicitely from the flow cache after querying for 
         * then from their respective switch flow tables.
         *
         * @return the flow mod removal msg loss count
         */
        public long getFlowModRemovalMsgLossCnt() {
            return flowModRemovalMsgLossCnt;
        }

        /**
         * Checks if is flow cache almost full.
         *
         * @return true, if is flow cache is almost full
         */
        public boolean isFlowCacheAlmostFull() {
            if ((bfcDb.fcCounters.activeCnt +
                    bfcDb.fcCounters.inactiveCnt) > ALMOST_FULL_SIZE) {
                return true;
            }
            return false;
        }

        /**
         * Checks if is flow cache full.
         *
         * @return true, if is flow cache full
         */
        public boolean isFlowCacheFull() {
            if ((bfcDb.fcCounters.activeCnt +
                    bfcDb.fcCounters.inactiveCnt) >= 
                                        MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT) {
                return true;
            }
            return false;
        }

    }

    private BfcDb bfcDb;
    private long fcQueryRespSeqNum;

    public BfcDb getBfcCore() {
        return bfcDb;
    }

    /** The pending query list. */
    private BlockingQueue<PendingQuery> pendingQueryList;
    
    public void setAppName(String name) {
        this.bfcDb.applName = name;
    }

    /**
     * The Class PendingQuery.
     */
    protected class PendingQuery {

        /** The query obj. */
        protected FCQueryObj  queryObj;

        /** The query rcvd time stamp_ms. */
        protected long        queryRcvdTimeStamp_ms;

        /** The pending switch resp. */
        protected ArrayList<PendingSwitchResp> pendingSwitchResp;

        /**
         * Instantiates a new pending query.
         *
         * @param query the query
         */
        protected PendingQuery(FCQueryObj query) {
            queryObj = query;
            queryRcvdTimeStamp_ms = System.currentTimeMillis();
            pendingSwitchResp = new ArrayList<PendingSwitchResp>();
        }
    }

    /**
     * The Class PendingSwitchResp. This object is used to track the pending
     * responses to switch flow table queries.
     */
    protected class PendingSwitchResp {
        protected FCQueryEvType evType;
        protected Long swClusterId;

        protected PendingSwitchResp(
                FCQueryEvType evType, Long swClusterId) {
            this.evType      = evType;
            this.swClusterId = swClusterId;
        }
    }

    //************
    // Constructor
    //************
    /**
     * Instantiates a new flow cache.
     *
     * @param applicationName the application name
     */
    public BetterFlowCache() {
        bfcDb = new BfcDb();
        bfcDb.setApplName("netVirt");
        pendingQueryList = new LinkedBlockingQueue<PendingQuery>();
        fcQueryRespSeqNum = Long.MAX_VALUE << 2;
        periodicSwScanInitDelayMsec = SWITCH_FLOW_TBL_SCAN_INITIAL_DELAY_MSEC;
        periodicSwScanIntervalMsec  = SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC;
    }

    //*****************************
    // Flow Cache Public Interfaces
    //*****************************

    /**
     * These public methods can be used to query the flow-cache by application
     * instance name, vlan id, dest mac and source mac. If all requested flows
     * are all in the flow-cache then the query operation is expected to
     * complete faster compared to the case when all the queried flows are not
     * in the cache and the flow-tables in the switches must be queried to get
     * all the requested flows. The queried flows are returned via the 
     * flowQueryRespHandler() callback.
     * <p>
     * The caller of this public method can use the response for various
     * operations including and other than flow-reconciliation. For example,
     * ACL manager may query the flows to apply a specific egress acl on the
     * returned flows instead of applying all the acls configured. A reporting
     * application may query the flow-cache to compare the number of flows in
     * vlan A vs vlan B and so on.
     *
     * @param query the query
     * 
     */

    @Override
    @LogMessageDoc(level="WARN",
            message="Failed to post a query {flowCacheQuery}",
            explanation="Encounter error in posting flowCache query",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public void submitFlowCacheQuery(FCQueryObj query) {
        if (logger.isDebugEnabled()) {
            logger.debug("submit Query: {}", query);
        }

        PendingQuery pq = new PendingQuery(query);
        boolean retCode = pendingQueryList.offer(pq);
        if (!retCode) {
            logger.warn("Failed to post a query {}", pq);
        }
    }

    @Override
    public boolean moveFlowToDifferentApplInstName(OFMatchReconcile ofmRc) {
        if (logger.isTraceEnabled()) {
            logger.trace("Moving flows from appl {} to {}",
                    ofmRc.appInstName, ofmRc.newAppInstName);
        }
        String curApplInstName = ofmRc.appInstName;
        short  priority = ofmRc.priority;
        OFMatchWithSwDpid ofmWithSwitchDpid = ofmRc.ofmWithSwDpid;

        FCOper oper = FCOper.NOT_FOUND;
        ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
            ConcurrentHashMap<Long, FlowCacheObj>>> applInstMap =
                curApplInstName==null?null:bfcDb.flowCache.get(curApplInstName);
        if (applInstMap != null) {
            ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> 
                    vlanMap = applInstMap.get(ofmWithSwitchDpid.getOfMatch()
                                              .getDataLayerVirtualLan());
            if (vlanMap != null) {
                ConcurrentHashMap<Long, FlowCacheObj> destMap =
                    vlanMap.get(Ethernet.toLong(ofmWithSwitchDpid.getOfMatch()
                                                .getDataLayerDestination()));
                if (destMap != null) {
                    synchronized(destMap) {
                        FlowCacheObj fco =
                            destMap.get(Ethernet.toLong(ofmWithSwitchDpid
                                                        .getOfMatch()
                                                        .getDataLayerSource()));
                        if (fco != null) {
                            oper = fco.deleteFCEntry(ofmWithSwitchDpid,
                                                     priority, this);
                            if (oper == FCOper.FCOBJ_FREE) {
                                /* Free the FlowCacheObj and the hash tables
                                 * if they are empty to reclaim space
                                 */
                                destMap.remove(
                                    Ethernet.toLong(ofmWithSwitchDpid
                                                    .getOfMatch()
                                                    .getDataLayerSource()));
                            }
                        }
                        if (oper != FCOper.NOT_FOUND)
                            bfcDb.updateCountsLocal(oper);
                    }
                }
            }
        }
        if (oper == FCOper.NOT_FOUND)
            bfcDb.updateCountsLocal(oper);
        if (oper == FCOper.NOT_FOUND) {
            return false; /* flow was not found */
        }
        /* flow was deleted from the flowCache - now add it under the new
         * application instance name 
         */
        String newApplInstName = ofmRc.newAppInstName;
        long cookie = ofmRc.cookie;
        byte action = ofmRc.action;
        boolean addStatus = addFlow(newApplInstName, ofmWithSwitchDpid, cookie, 
            ofmWithSwitchDpid.getSwitchDataPathId(), ofmWithSwitchDpid.getOfMatch().getInputPort(), 
            priority, action);
        return addStatus;
    }

    //**********************************
    // Flow Cache Internal Query Methods
    //**********************************

    /**
     * Private API to get the all flows by application instance.
     *
     * @param appInstName the application instance name
     * @return nested hash map of all flows by application instance
     */
    protected ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
                    ConcurrentHashMap<Long, FlowCacheObj>>>
                            getAllFlowsByApplInstInternal(String appInstName) {
        if (appInstName == null) {
            return null;
        }
        return bfcDb.flowCache.get(appInstName);
    }

    /**
     * Private API to get all flows by application instance and vlan id.
     *
     * @param appInstName the app inst name
     * @param vlan the vlan id.
     * @return nested hash map of all the queried flows
     */
    private ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>>
            getAllFlowsByApplInstVlanInternal(String appInstName, short vlan) {
        ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
                ConcurrentHashMap<Long, FlowCacheObj>>> applInstMap =
                                getAllFlowsByApplInstInternal(appInstName);
        if (applInstMap != null) {
            return applInstMap.get(vlan);
        }
        return null;
    }

    /**
     * Private API to get all flows by application instance and  destination
     * device.
     *
     * @param appInstName the application instance name
     * @param vlan
     * @param dstMac the destination device object
     * @return hash map of all the queried flows
     */
    private ConcurrentHashMap<Long, FlowCacheObj> 
                                    getAllFlowsByApplInstDestDeviceInternal(
            String appInstName, short vlan, long dstMac) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> vlanMap =
                getAllFlowsByApplInstVlanInternal(appInstName, vlan);

        if (vlanMap != null) {
            return vlanMap.get(dstMac);
        }
        return null;
    }

    /**
     * Private API to get all the flows by application instance, vlan id.,
     * source device, and source device.
     *
     * @param appInstName the app inst name
     * @param dSrc the source device
     * @param dDest the destination device
     * @return FlowCacheObj with all the queried flows
     */
    protected ConcurrentHashMap<Long, FlowCacheObj>
                    getAllFlowsByApplInstSrcDevicesInternal(
          String appInstName, short vlan, long srcMac) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> vlanMap =
            getAllFlowsByApplInstVlanInternal(appInstName, vlan);

        ConcurrentHashMap<Long, FlowCacheObj> srcFlows = 
            new ConcurrentHashMap<Long, FlowCacheObj>();
        if (vlanMap != null) {
            for (Long dstMac : vlanMap.keySet()) {
                ConcurrentHashMap<Long, FlowCacheObj> srcDevFlows = 
                    vlanMap.get(dstMac);
                for (Long srcDev : srcDevFlows.keySet()) {
                    if (srcDev.equals(srcMac)) {
                        srcFlows.put(dstMac, srcDevFlows.get(srcDev));
                    }
                }
            }
        }
        return srcFlows;
    }
    
    /**
     * Private API to get all the flows by application instance, vlan id.,
     * source device, and destination device.
     *
     * @param appInstName the app inst name
     * @param dSrc the source device
     * @param dDest the destination device
     * @return FlowCacheObj with all the queried flows
     */
    protected FlowCacheObj getAllFlowsByApplInstVlanSrcDestDevicesInternal(
          String appInstName, short vlan, long srcMac, long dstMac) {
        ConcurrentHashMap<Long, FlowCacheObj> destMap = 
                getAllFlowsByApplInstDestDeviceInternal(appInstName, vlan, dstMac);
        if (destMap != null && destMap.size() > 0) {
            return destMap.get(srcMac);
        }
        return null;
    }

    /**
     * Clear all entries from the flow cache.
     *
     */
    protected void clearFlowCache() {
        bfcDb.flowCache.clear();
        bfcDb.fcCounters.clearCounts();
    }

    /**
     * Delete the inactive flows from flow cache - to be called periodically or
     * when flow cache is running low in space.
     */
    protected void deleteInactiveFlows() {
        for (String appInstName : bfcDb.flowCache.keySet()) {
            ConcurrentHashMap <Short, ConcurrentHashMap<Long, 
                        ConcurrentHashMap<Long, FlowCacheObj>>> appInstMap =
                                            bfcDb.flowCache.get(appInstName);
            for (Short vlan : appInstMap.keySet()) {
                ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> 
                                            vlanMap = appInstMap.get(vlan);
                for (Long dMac : vlanMap.keySet()) {
                    ConcurrentHashMap<Long, FlowCacheObj> 
                                                    destMap = vlanMap.get(dMac);
                    if (destMap != null) {
                        synchronized(destMap) {
                            for (Long sMac : destMap.keySet()) {
                                FlowCacheObj fco = destMap.get(sMac);
                                if (fco.deleteInactiveFlows()) {
                                    destMap.remove(sMac);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void deleteFlowCacheBySwitch(long switchDpid) {
        for (String appInstName : bfcDb.flowCache.keySet()) {
            ConcurrentHashMap <Short, ConcurrentHashMap<Long, 
                    ConcurrentHashMap<Long, FlowCacheObj>>> appInstMap =
                                                bfcDb.flowCache.get(appInstName);
            for (Short vlan : appInstMap.keySet()) {
                ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> 
                                        vlanMap = appInstMap.get(vlan);
                for (Long dMac : vlanMap.keySet()) {
                    ConcurrentHashMap<Long, FlowCacheObj> destMap = 
                                                            vlanMap.get(dMac);
                    if (destMap != null) {
                        synchronized(destMap) {
                            for (Long sMac : destMap.keySet()) {
                                FlowCacheObj fco = destMap.get(sMac);
                                if (fco.deleteFlowsBySwitch(switchDpid, this)) {
                                    destMap.remove(sMac);
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * The Enum listing the types of operations on flow cache.
     */
    public enum FCOper {

        /**New flow-entry. New flow-mod was stored in the cache */
        NEW_ENTRY,
        /** The flow-mod was already in the FC and it was dampened. */
        DAMPENED,
        /** The flow-mod was already in the FC and but was not dampened. */
        NOT_DAMPENED,
        /** The flow-mod was in inactive state and it was activated. */
        ACTIVATED,
        /** The flow mod was not stored as flow cache was full */
        NOT_STORED_FULL,
        /** The entry was active and it was deleted from cache. */
        DELETED_ACTIVE,
        /** The entry was inactive and it was deleted from cache. */
        DELETED_INACTIVE,
        /** The entry was not deleted but was marked as inactive */
        DEACTIVATED,
        /** The entry not found in flow cache. */
        NOT_FOUND,    // Entry not found in flow cache
        /** Entry was deleted from flow cache and as a result the corresponding
         * container - FlowCacheObj is empty, it can be freed now */
        FCOBJ_FREE,
        /** Flow to be removed was not active - error case */
        NOT_ACTIVE,
        /** No action to be taken by the caller */
        NOP,
        /** Clear all the counters */
        CLEAR_COUNTERS,
    }

    /**
     * Flush Local Counter Updates
     *
     */
    @Override
    public void updateFlush() {
        bfcDb.updateFlush();
    }
    
    @Override
    public boolean addFlow(ListenerContext cntx, OFMatchWithSwDpid ofm,
                        Long cookie, SwitchPort swPort,
                        short priority, byte action) {

        String appName = (String)cntx.getStorage().get(FLOWCACHE_APP_INSTANCE_NAME);
        if (appName == null) {
            // TODO
            if (logger.isTraceEnabled()) {
                logger.trace("FLOWCACHE_APPNAME is null");
            }
            return false;
        } else {
            long srcSwDpid = swPort.getSwitchDPID();
            short srcInPort = (short)swPort.getPort();
            return addFlow(appName, ofm, cookie, srcSwDpid, srcInPort,
                                                            priority, action);
        }
    }

    @Override
    public boolean addFlow(String applInstName, OFMatchWithSwDpid ofmWithSwDpid,
                           Long cookie, 
                           long srcSwDpid, short srcInPort,
                           short priority, byte action) {

        /* Skip storing ARP packet in flow-cache since it is a fully specified 
         * entry (no wildcard) and it is expected to expire in short timeframe
         * (less than 5s). Note that ARP flow entries are programmed only for
         * ARP response packets from the ARP responder to the ARP query sender.
         */
        if (logger.isDebugEnabled()) {
            logger.debug("Adding flow to cache: appl={} match={} action={}",
                          new Object[]{applInstName, ofmWithSwDpid, action});
        }
        if (ofmWithSwDpid.getOfMatch().getDataLayerType() == Ethernet.TYPE_ARP) {
            return false;
        }

        if (applInstName == null) {
            return false;
        }

        ofmWithSwDpid.setSwitchDataPathId(srcSwDpid);
        ofmWithSwDpid.getOfMatch().setInputPort(srcInPort);

        ConcurrentHashMap<Short, ConcurrentHashMap<Long,
                        ConcurrentHashMap<Long, FlowCacheObj>>> applInstMap;
        applInstMap = bfcDb.flowCache.get(applInstName);
        if (applInstMap == null) {
            final ConcurrentHashMap<Short, ConcurrentHashMap<Long,
                    ConcurrentHashMap<Long, FlowCacheObj>>> applInstMapTemp;
            applInstMap = new ConcurrentHashMap<Short,
                                ConcurrentHashMap<Long, ConcurrentHashMap<Long,
                                                    FlowCacheObj>>>();
            applInstMapTemp = bfcDb.flowCache.putIfAbsent(
                                                applInstName, applInstMap);
            if (applInstMapTemp != null) {
                applInstMap = applInstMapTemp;
            }
        }

        Short vlan = ofmWithSwDpid.getOfMatch().getDataLayerVirtualLan();
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> vlanMap;
        vlanMap = applInstMap.get(vlan);
        if (vlanMap == null) {
            final ConcurrentHashMap<Long, ConcurrentHashMap<Long, 
                                                    FlowCacheObj>> vlanMapTemp;
            vlanMap = new ConcurrentHashMap<Long, 
                                ConcurrentHashMap<Long, FlowCacheObj>>();
            vlanMapTemp = applInstMap.putIfAbsent(vlan, vlanMap);
            if (vlanMapTemp != null) {
                vlanMap = vlanMapTemp;
            }
        }

        ConcurrentHashMap<Long, FlowCacheObj> destMap;
        Long destMac = Ethernet.toLong(ofmWithSwDpid.getOfMatch().getDataLayerDestination());
        destMap = vlanMap.get(destMac);
        if (destMap == null) {
            ConcurrentHashMap<Long, FlowCacheObj> destMapTemp;
            destMap = new ConcurrentHashMap<Long, FlowCacheObj>();
            destMapTemp = vlanMap.putIfAbsent(destMac, destMap);
            if (destMapTemp != null) {
                destMap = destMapTemp;
            }
        }

        /** serialized add and delete from destMap since fco may be deleted
         * before storeFCEntry is called
         */
        FCOper fcOper;
        synchronized(destMap) {
            Long srcMac = Ethernet.toLong(ofmWithSwDpid.getOfMatch().getDataLayerSource());
            FlowCacheObj fco;
            fco = destMap.get(srcMac);
            if (fco == null) {
                FlowCacheObj fcoTemp;
                fco = new FlowCacheObj();
                fcoTemp = destMap.putIfAbsent(srcMac, fco);
                if (fcoTemp != null) {
                    fco = fcoTemp;
                }
            }
    
            fcOper = fco.storeFCEntry(ofmWithSwDpid, cookie, priority, action, this);
            if (fcOper == FCOper.NOT_STORED_FULL) {
                /* Flow-cache was full; add the switch to the list of switches to
                 * query
                 */
                bfcDb.switchesToQuery.add(srcSwDpid);
            }
            bfcDb.updateCountsLocal(fcOper);
        }

        if ((fcOper == FCOper.DAMPENED)) {
            return false; /* skip the flow mod. */
        }
            
        return true; /* program the flow mod */
    }

    /**
     * Mark the given flow as inactive in the flow cache.
     * <p>
     * Note that the flow was NOT deleted from the cache so that if the same
     * flow restarts it can be stored in the cache with reduced overhead of
     * object creation etc. If the flow cache is running low on space then
     * deleteFlow() should be called instead. All the inactive flow are
     * deleted from the cache periodically when flow-cache is low on space
     *
     * @param appInst the app inst
     * @param ofmWithSwDpid the ofm
     * @param priority the priority
     * @return true, if successful
     * Returns true if the flow was successfully deactivated in the cache,
     * returns false otherwise
     *
     */
    protected boolean deactivateFlow(String appInst,
                                     OFMatchWithSwDpid ofmWithSwDpid,
                                     short priority){

        FCOper oper = FCOper.NOT_FOUND;
        ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
                ConcurrentHashMap<Long, FlowCacheObj>>> applInstMap =
                                                bfcDb.flowCache.get(appInst);
        if (applInstMap != null) {
            ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> 
                    vlanMap = applInstMap.get(ofmWithSwDpid.getOfMatch()
                                              .getDataLayerVirtualLan());
            if (vlanMap != null) {
                ConcurrentHashMap<Long, FlowCacheObj> destMap =
                    vlanMap.get(Ethernet.toLong(ofmWithSwDpid.getOfMatch()
                                                .getDataLayerDestination()));
                if (destMap != null) {
                    synchronized(destMap) {
                        FlowCacheObj fco =
                            destMap.get(Ethernet.toLong(ofmWithSwDpid
                                                        .getOfMatch()
                                                        .getDataLayerSource()));
                        if (fco != null) {
                            oper = fco.removeFCEntry(ofmWithSwDpid, priority);
                        } else {
                            oper = FCOper.NOT_FOUND;
                        }
                        if (oper != FCOper.NOT_FOUND)
                            bfcDb.updateCountsLocal(oper);
                    }
                }
            }
        }
        if (oper == FCOper.NOT_FOUND)
            bfcDb.updateCountsLocal(oper);

        // No need to flush since it is called as part of the packetIn processing.
        // bfcDb.updateFlush();
        if (oper == FCOper.NOT_FOUND) {
            return false; /* flow was not found in the flow cache */
        } else {
            return true;  /* flow was deactivated in the flow cache */
        }
    }

    /**
     * Deactivate a flow in the flow-cache - called when flow-mod removal mesg
     * is received. For performance reasons the flow may be marked as inactive
     * and the space used by the flow may not be released. When flow-cache is
     * low on memory then deleteFlow() should be called instead.
     *
     * @param appInst the app inst
     * @param flowRemMsg the flow rem msg
     * @return true:  flow was found in the cache and was deactivated
     * false: flow was not found in the cache
     */
    protected boolean deactivateFlow(String appInst, OFFlowRemoved flowRemMsg, long swDpid) {
        OFMatchWithSwDpid ofmWithSwDpid = new OFMatchWithSwDpid(flowRemMsg.getMatch(), swDpid);
        return deactivateFlow(
                    appInst, ofmWithSwDpid, flowRemMsg.getPriority());
    }

    /**
     * Delete a flow from the flow-cache - called when flow-mod removal mesg
     * is received an flow cache is low on space. The space used by the flow is
     * released
     *
     * @param appInst the app inst
     * @param ofmWithSwDpid the ofm
     * @param priority the priority
     * @return true:  flow was found in the cache and was deactivated
     * false: flow was not found in the cache
     */
    protected boolean deleteFlow(String appInst,
                                 OFMatchWithSwDpid ofmWithSwDpid,
                                 short priority) {

        FCOper oper = FCOper.NOT_FOUND;
        ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
            ConcurrentHashMap<Long, FlowCacheObj>>> applInstMap =
                                                bfcDb.flowCache.get(appInst);
        if (applInstMap != null) {
            ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>> 
                    vlanMap =
                    applInstMap.get(ofmWithSwDpid.getOfMatch()
                                    .getDataLayerVirtualLan());
            if (vlanMap != null) {
                ConcurrentHashMap<Long, FlowCacheObj> destMap =
                    vlanMap.get(Ethernet.toLong(ofmWithSwDpid.getOfMatch()
                                                .getDataLayerDestination()));
                if (destMap != null) {
                    synchronized(destMap) {
                        FlowCacheObj fco =
                            destMap.get(Ethernet.toLong(ofmWithSwDpid
                                                        .getOfMatch()
                                                        .getDataLayerSource()));
                        if (fco == null) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("FlowCache Failed to delete " +
                                         "unknown flow {}.", ofmWithSwDpid);
                            }
                            return false;
                        }
                        
                        oper = fco.deleteFCEntry(ofmWithSwDpid, priority, this);
                        if (oper == FCOper.FCOBJ_FREE) {
                            /* Free the FlowCacheObj and the hash tabled if they
                             * are empty to reclaim space
                             */
                            destMap.remove(
                                Ethernet.toLong(ofmWithSwDpid.getOfMatch()
                                                .getDataLayerSource()));
                            /** Don't delete maps to avoid race condition
                             * with addFlow
                             */
                        }
                        if (oper != FCOper.NOT_FOUND)
                            bfcDb.updateCountsLocal(oper);
                    }
                }
            }
        }
        if (oper == FCOper.NOT_FOUND)
            bfcDb.updateCountsLocal(oper);
        if (oper != FCOper.NOT_FOUND && oper != FCOper.FCOBJ_FREE) {
            return false; /* flow was not found */
        }
        return true; /* flow was deleted from the flowCache */
    }

    /**
     * Delete a flow from the flow-cache - called when flow-mod removal mesg
     * is received an flow cache is low on space. The space used by the flow is
     * released
     *
     * @param appInst the app inst
     * @param flowRemMsg the flow rem msg
     * @return true:  flow was found in the cache and was deactivated
     * false: flow was not found in the cache
     */
    protected boolean deleteFlow(String appInst, OFFlowRemoved flowRemMsg, long swDpid) {
        OFMatchWithSwDpid ofmWithSwDpid = new OFMatchWithSwDpid(flowRemMsg.getMatch(), swDpid);
        return deleteFlow(
                appInst, ofmWithSwDpid, flowRemMsg.getPriority());
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.core.IListener#getName()
     */
    @Override
    public String getName() {
        return "BetterFlowCacheMgr";
    }

    /**
     * Method to be called at shut down of flow-cache service.
     */
    public void shutDown() {
        /* De-register as flow-mod removal listener */
        controllerProvider.removeOFMessageListener(OFType.FLOW_REMOVED, this);
    }

    protected void processFlowModRemovalMsg(IOFSwitch sw, 
                      OFFlowRemoved flowRemMsg, ListenerContext cntx) {
        if (logger.isTraceEnabled()) {
            logger.trace("Recvd. flow-mod removal message from switch {}, wildcard=0x{} fm={}",
                new Object[]{HexString.toHexString(sw.getId()), 
                             Integer.toHexString(flowRemMsg.getMatch().getWildcards()),
                             flowRemMsg.getMatch().toString()});
        }
        boolean remStatus = false;
        /* if one or both the source and destination devices have moved to a
         * different app then we won't know in which app they were when the
         * flow-mod was programmed. (Perhaps we do need to use the cookie for
         * this.) So if the deletion from flow-cache fails we then try to delete
         * the flow in other apps, stopping on success. Assumption here is that
         * the flow can belong to exactly one app, the source app.
         */
        for (String applIName : bfcDb.flowCache.keySet()) {
            if (bfcDb.isFlowCacheAlmostFull()) {
                remStatus = deleteFlow(applIName, flowRemMsg, sw.getId());
            } else {
                remStatus = deactivateFlow(applIName, flowRemMsg, sw.getId());
            }
            if ((remStatus == true) && (logger.isTraceEnabled())) {
                logger.trace("Removed flow from  appl. inst. name: {}",
                             applIName);
                break;
            }
        }
    }

    protected void processStatsReplyMsg(IOFSwitch sw,
            OFStatisticsReply statsReplyMsg, ListenerContext cntx) {
        if (logger.isTraceEnabled()) {
            logger.trace("Recvd. stats reply message from {} count = {}",
                   sw.getStringId(), statsReplyMsg.getStatistics().size());
        }
        List<? extends OFStatistics> statsList = statsReplyMsg.getStatistics();
        for (OFStatistics rspIdx : statsList) {
            OFFlowStatisticsReply rspOne = (OFFlowStatisticsReply)rspIdx;
            OFMatchWithSwDpid ofmWithSwDpid = new OFMatchWithSwDpid(rspOne.getMatch(), sw.getId());

            /* Skip storing ARP packet in flow-cache since it is a fully specified
             * entry (no wildcard) and it is expected to expire in short timeframe
             * (less than 5s). Note that ARP flow entries are programmed only for
             * ARP response packets from the ARP responder to the ARP query sender.
             */
            if (ofmWithSwDpid.getOfMatch().getDataLayerType() == Ethernet.TYPE_ARP) {
                continue;
            }

            /* Only consider flows with specific source and destination
             * addresses
             */
            if (!((ofmWithSwDpid.getOfMatch().getWildcards() & 
                           BetterFlowReconcileManager.ofwSrcDestValid) == 0)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Not a specific flow, ignoring: wildcard={}",
                        ofmWithSwDpid.getOfMatch().getWildcards());
                }
                continue;
            }

            /* Also, only consider flows at the attachment point of the 
             * source device.
             */

            for (String appName: bfcDb.flowCache.keySet()) {
                ConcurrentHashMap<Short, ConcurrentHashMap
                    <Long, ConcurrentHashMap<Long, FlowCacheObj>>> 
                                        aMap = bfcDb.flowCache.get(appName);
                if (aMap != null) {
                    ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>>
                                vMap = aMap.get(ofmWithSwDpid.getOfMatch().getDataLayerVirtualLan());
                    if (vMap != null) {
                        ConcurrentHashMap<Long, FlowCacheObj> 
                                dMap = vMap.get(Ethernet.toLong(
                                                ofmWithSwDpid.getOfMatch().getDataLayerDestination()));
                        if (dMap != null) {
                            synchronized(dMap) {
                                FlowCacheObj fco = dMap.get(Ethernet.toLong(
                                                    ofmWithSwDpid.getOfMatch().getDataLayerSource()));
                                if (fco != null) {
                                    /* Find the action as permit or deny */
                                    byte action;
                                    if (rspOne.getActions().isEmpty()) {
                                        action = FlowCacheObj.FCActionDENY;
                                    } else {
                                        action = FlowCacheObj.FCActionPERMIT;
                                    }
                                    FCEntry fce = fco.getFCEntryWithCookie(
                                                        ofmWithSwDpid, rspOne.getCookie(),
                                                        rspOne.getPriority(), action);
                                    if (fce == null) {
                                        if (logger.isTraceEnabled()) {
                                            logger.trace(
                                            "Switch flow table scan: entry not in "+
                                            " flow cache: Inserting {}", rspOne.toString());
                                        }
                                        FCOper fcOper = fco.storeFCEntry(
                                            ofmWithSwDpid, rspOne.getCookie(), 
                                            rspOne.getPriority(), action, this);
                                        bfcDb.updateCountsLocal(fcOper);
                                    } else {
                                        if (logger.isTraceEnabled()) {
                                            logger.trace("Switch flow table scan: entry " +
                                            "in flow cache: resetting scanCnt: {}",
                                            rspOne.toString());
                                        }
                                        fce.scanCnt = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                                                    ListenerContext cntx) {
        switch (msg.getType()) {
            case FLOW_REMOVED:
                OFFlowRemoved flowRemMsg = (OFFlowRemoved) msg;
                processFlowModRemovalMsg(sw, flowRemMsg, cntx);
                break;

            case STATS_REPLY:
                OFStatisticsReply   statsReplyMsg = (OFStatisticsReply)msg;
                processStatsReplyMsg(sw, statsReplyMsg, cntx);
                /** Stats_Reply is not called from pktIn pipeline, but
                 * directly as a switch callback.
                 * It needs to flush the counters.
                 */
                updateFlush();
                break;

            default:
                if (logger.isDebugEnabled()) {
                    logger.debug("Ignoring mesg type: {}", msg.getType());
                }
                break;
        }
        return Command.CONTINUE;
    }

    /* Don't define getter for controllerProvider so that Jackson doesn't try
     * to serialize it for the REST API response
     */

    /**
     * Sets the sdnplatform provider.
     *
     * @param flProvider the new sdnplatform provider
     */
    public void setControllerProvider(IControllerService flProvider) {
        controllerProvider = flProvider;
    }

    public void setThreadPoolService(IThreadPoolService tp) {
        this.threadPool = tp;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    /**
     * Helper function to convert a FlowCacheObj to query response format.
     *
     * @param resp   Response entries are added to this object
     * @param fcObj the flow cache object
     * @param vlan the vlan id
     * @param srcMac the source mac
     * @param dstMac the destination mac
     */
    private void flowCacheObjResp(FlowCacheQueryResp resp,
            FlowCacheObj fcObj, Short vlan, Long srcMac, Long dstMac) {
        FCEntry fce;
        QRFlowCacheObj respEntry;

        fce = fcObj.fce;
        if (fce.state == FlowCacheObj.FCStateACTIVE) {
            respEntry = new QRFlowCacheObj(fce.ofPri, fce.action, fcObj.cookie);
            fce.toOFMatchWithSwDpid(respEntry.ofmWithSwDpid, vlan, srcMac, dstMac);
            resp.qrFlowCacheObjList.add(respEntry);
        }
        if (fcObj.fceList != null) {
            int numEntries = fcObj.fceList.size();
            for (int idx=0; idx < numEntries; idx++) {
                fce = fcObj.fceList.get(idx);
                if (fce.state == FlowCacheObj.FCStateACTIVE) {
                    respEntry =
                            new QRFlowCacheObj(
                                        fce.ofPri, fce.action, fcObj.cookie);
                    fce.toOFMatchWithSwDpid(respEntry.ofmWithSwDpid, vlan, srcMac, dstMac);
                    resp.qrFlowCacheObjList.add(respEntry);
                }
            }
        }
    }

    /**
     * Process one flow cache object by populating the flow cache response
     * object with the flows in the given FlowCacheObj
     *
     * @param fcObj the flow cache object
     * @param queryObj the flow cache query object
     * @param resp the resp object to be filled in with flows
     * @param vlan the vlan id
     * @param srcMac the source mac
     * @param destMac the destination mac
     * @param moreFlag the more flag
     */
    private void processOneFCObj( FlowCacheObj fcObj, 
                                  FCQueryObj queryObj,
                                  FlowCacheQueryResp resp,
                                  short vlan, 
                                  long srcMac, 
                                  long destMac, 
                                  boolean moreFlag) {
        flowCacheObjResp(resp, fcObj, vlan, srcMac, destMac);
        if ((resp.qrFlowCacheObjList.size() >= QUERY_RSP_BATCH_SIZE) ||
            (moreFlag == false)) {
            resp.moreFlag = moreFlag;
            /* Call the callback function of the caller */
            queryObj.fcQueryHandler.flowQueryRespHandler(resp);
            resp.hasSent = true;
            if (moreFlag) {
                resp.qrFlowCacheObjList.clear();
                resp.moreFlag = false;
            }
        }
    }

    private boolean processDestFlowQuery(
            Map<Long, FlowCacheObj> destMap,
            short vlan,
            long destMac,
            FCQueryObj queryObj,
            int moreCntApp,
            int moreCntVlan,
            int moreCntDst,
            FlowCacheQueryResp resp) {
        boolean moreFlag = true;
        FlowCacheObj fcObj;
        
        if ((destMap == null) || (destMap.size() == 0)) {
            return false;
        }
        Map<Long, FlowCacheObj> srcFlows = new HashMap<Long, FlowCacheObj> ();
        int moreCntFlow = destMap.size();
        if (queryObj.srcDevice != null) {
            // SrcDevice is specified
            for (Long sMac : destMap.keySet()) {
                if (sMac.equals(queryObj.srcDevice.getMACAddress())) {
                    srcFlows.put(sMac, destMap.get(sMac));
                }
            }
            moreCntFlow = srcFlows.size();
        } else {
            // srcDevice is null
            srcFlows = destMap;
        }
        
        for (Long sMac : srcFlows.keySet()) {
            moreCntFlow--;
            if (moreCntApp+moreCntVlan+moreCntDst+moreCntFlow == 0) {
                moreFlag = false;
            }
            fcObj = destMap.get(sMac);
            processOneFCObj(fcObj, queryObj, resp, vlan,
                                   sMac, destMac, moreFlag);
        }
        return moreFlag;
    }
    
    private boolean processVlanFlowQuery(String applInsName,
                                      FCQueryObj queryObj,
                                      int moreCntApp,
                                      FlowCacheQueryResp resp) {
        boolean moreFlag = true;
        int moreCntDst = 0;
        Short[] vlans = null;
        if (queryObj.vlans != null) {
            vlans = queryObj.vlans;
        } else {
            ConcurrentHashMap<Short, ConcurrentHashMap<Long, 
            ConcurrentHashMap<Long, FlowCacheObj>>> appMap = 
                getAllFlowsByApplInstInternal(applInsName);
            if (appMap != null) {
                vlans = appMap.keySet().toArray(new Short[0]);
            }
        }
        if (vlans == null || vlans.length == 0) {
            return false;
        }
        
        int moreCntVlan = vlans.length;
        for (Short vlan : vlans) {
            moreCntVlan--;
            /* Get the flows in the flow cache in a given VLAN */
            ConcurrentHashMap<Long,
            ConcurrentHashMap<Long, FlowCacheObj>> vlanMap =
            getAllFlowsByApplInstVlanInternal(applInsName,
                                              vlan);
            if ((vlanMap == null) || (vlanMap.size() == 0)) {
                continue;
            }
            if (queryObj.dstDevice != null) {
                // DestDevice is specified.
                long destMac = queryObj.dstDevice.getMACAddress();
                Map<Long, FlowCacheObj> destMap = vlanMap.get(destMac);
                moreFlag = processDestFlowQuery(destMap,
                                    vlan,
                                    destMac,
                                    queryObj,
                                    moreCntApp,
                                    moreCntVlan,
                                    moreCntDst,
                                    resp);
            } else {
                // DestDevice is null
                moreCntDst = vlanMap.size();
                for (Long dst : vlanMap.keySet()) {
                    moreCntDst--;
                    Map<Long, FlowCacheObj> destMap = vlanMap.get(dst);
                    moreFlag = processDestFlowQuery(destMap,
                                         vlan,
                                         dst,
                                         queryObj,
                                         moreCntApp,
                                         moreCntVlan,
                                         moreCntDst,
                                         resp);
                }
            }
        }
        
        return moreFlag;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IFlowCacheService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> 
                                                    getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m = 
            new HashMap<Class<? extends IPlatformService>,
                IPlatformService>();
        m.put(IFlowCacheService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> 
                                                    getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IControllerService.class);
        l.add(IDeviceService.class);
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        controllerProvider =
                context.getServiceImpl(IControllerService.class);
        deviceManager =
                context.getServiceImpl(IDeviceService.class);
        threadPool =
                context.getServiceImpl(IThreadPoolService.class);
        bfcDb.flowCache = new ConcurrentHashMap<String, ConcurrentHashMap<Short,
                ConcurrentHashMap<Long, 
                ConcurrentHashMap<Long, FlowCacheObj>>>>();
        bfcDb.maxFlows = MAX_FLOW_CACHE_SIZE_AS_FLOW_COUNT;
        pendingQueryList = new LinkedBlockingQueue<PendingQuery>();
        fqTask = new SendPeriodicFlowQueryToSwitches(this);
    }

    @Override
    public void startUp(ModuleContext context) {
        // Register to get all the flow-mod removal open flow messages
        controllerProvider.addOFSwitchListener(this);
        controllerProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        controllerProvider.addHAListener(this);
        threadPool.getScheduledExecutor().scheduleAtFixedRate(
                fqTask,
                periodicSwScanInitDelayMsec, 
                periodicSwScanIntervalMsec,
                TimeUnit.MILLISECONDS);

        Thread flowReconcileQueryTask = new Thread() {
            @Override
            @LogMessageDoc(level="WARN",
                message="Exception in doReconcile(): {exception}",
                explanation="Encouter error when retrieving flowCache query",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
            public void run() {
                while (true) {
                    try {
                        PendingQuery pendingQuery = pendingQueryList.take();
                        FCQueryObj queryObj = pendingQuery.queryObj;
                        FlowCacheQueryResp resp =
                                new FlowCacheQueryResp(queryObj);
                        boolean moreFlag = true;
    
                        if (logger.isTraceEnabled()) {
                            logger.trace("Handle query: {}", queryObj);
                        }
                        
                        int moreCntApp = 0;
                        if (queryObj.applInstName != null) {
                            moreFlag = processVlanFlowQuery(queryObj.applInstName,
                                                 queryObj,
                                                 moreCntApp,
                                                 resp);
                        } else {
                            moreCntApp = bfcDb.flowCache.size();
                            for (String appName : bfcDb.flowCache.keySet()) {
                                moreCntApp--;
                                queryObj.applInstName = appName;
                                moreFlag = processVlanFlowQuery(appName,
                                                     queryObj,
                                                     moreCntApp,
                                                     resp);
                            }
                        }
                            
                        /* Check if anything is left over */
                        if (moreFlag || !resp.hasSent) {
                            resp.moreFlag = false;
                            if (queryObj.fcQueryHandler != null)
                                queryObj.fcQueryHandler.flowQueryRespHandler(resp);
                        }
                    } catch (Exception e) {
                        logger.warn("Exception in doReconcile(): {}",
                                    e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        };
        
        flowReconcileQueryTask.start();
    }

    /**
     * Gets the next fc query seq num.
     *
     * @return the next fc query seq num
     */
    public synchronized long getNextFcQuerySeqNum() {
        fcQueryRespSeqNum++;
        return fcQueryRespSeqNum;
    }

    @Override
    public void deleteAllFlowsAtASourceSwitch(IOFSwitch sw) {
        long swId =sw.getId();
        FCOper fcOper;
        for (String appName : bfcDb.flowCache.keySet()) {
            ConcurrentHashMap<Short, ConcurrentHashMap<Long,
                ConcurrentHashMap<Long, FlowCacheObj>>> aMap =
                    bfcDb.flowCache.get(appName);
            if (aMap == null) {
                continue;
            }
            for (Short vlan : aMap.keySet()) {
                ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>>
                vMap = aMap.get(vlan);
                if (vMap == null) {
                    continue;
                }
                Iterator<Entry<Long, ConcurrentHashMap<Long, FlowCacheObj>>>
                                            vIter = vMap.entrySet().iterator();
                while (vIter.hasNext()) {
                    ConcurrentHashMap<Long, FlowCacheObj> dMap = 
                                                    vIter.next().getValue();
                    if (dMap == null) {
                        continue;
                    }
                    synchronized(dMap) {
                        Iterator<Entry<Long, FlowCacheObj>> dIter =
                                                dMap.entrySet().iterator();
                        FlowCacheObj fco;
                        while (dIter.hasNext()) {
                            fco = dIter.next().getValue();
                            if (fco != null) {
                                fcOper = fco.deleteFCEntry(swId, this);
                                if (fcOper == FCOper.FCOBJ_FREE) {
                                    dIter.remove();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void scanForStaleFlows(int staleScanCnt) {
        for (String appName : bfcDb.flowCache.keySet()) {
            ConcurrentHashMap<Short, ConcurrentHashMap<Long,
                ConcurrentHashMap<Long, FlowCacheObj>>> aMap =
                    bfcDb.flowCache.get(appName);
            if (aMap == null) {
                continue;
            }
            for (Short vlan : aMap.keySet()) {
                ConcurrentHashMap<Long, ConcurrentHashMap<Long, FlowCacheObj>>
                vMap = aMap.get(vlan);
                if (vMap == null) {
                    continue;
                }
                Iterator<Entry<Long, ConcurrentHashMap<Long, FlowCacheObj>>>
                                            vIter = vMap.entrySet().iterator();
                while (vIter.hasNext()) {
                    ConcurrentHashMap<Long, FlowCacheObj> dMap = 
                                                    vIter.next().getValue();
                    if (dMap == null) {
                        continue;
                    }
                    synchronized(dMap) {
                        Iterator<Entry<Long, FlowCacheObj>> dIter =
                                                 dMap.entrySet().iterator();
                        FlowCacheObj fco;
                        while (dIter.hasNext()) {
                            fco = dIter.next().getValue();
                            if (fco != null) {
                                fco.deactivateStaleFlows(staleScanCnt, this);
                            }
                        }
                    }
                }
            }
        }
    }
    
    @LogMessageDoc(level="ERROR",
            message="Failure to send stats request to switch {sw}, {exception}",
            explanation="Controller is not able to send request to switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    public void querySwitchStats(long swId,
                                 IOFMessageListener callbackHandler) {
        IOFSwitch sw = controllerProvider.getSwitches().get(swId);
        if ((sw == null) || (!sw.isConnected())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to send flow-stats request to switch Id {}",
                        swId);
            }
            return;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace(
                    "Sending flow scan request to switch {} Id={}", 
                    sw, swId);
            }
        }
        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = req.getLengthU();

        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        OFMatch match = new OFMatch();
        match.setWildcards(FlowCacheObj.WILD_ALL);
        specificReq.setMatch(match);
        specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
        specificReq.setTableId((byte) 0xff);
        req.setStatistics(Collections.singletonList(
                                            (OFStatistics)specificReq));
        requestLength += specificReq.getLength();

        req.setLengthU(requestLength);
        try {
            sw.sendStatsQuery(req, sw.getNextTransactionId(), callbackHandler);
        } catch (Exception e) {
            logger.error("Failure to send stats request to switch {}, {}",
                                                                    sw, e);
        }
    }
    
    protected class SwitchFlowTablePeriodicScanTask implements Runnable {
        protected long    swId;
        protected IOFMessageListener callbackHandler;

        protected SwitchFlowTablePeriodicScanTask(
                long swId, IOFMessageListener callbackHandler) {
            this.swId = swId;
            this.callbackHandler = callbackHandler;
        }

        @Override
        public void run() {
            querySwitchStats(swId, callbackHandler);
        }
    }

    protected class SendPeriodicFlowQueryToSwitches implements Runnable {
        private IOFMessageListener callbackHandler;
        protected boolean enableFlowQueryTask = false;
        protected SendPeriodicFlowQueryToSwitches(IOFMessageListener c) {
            callbackHandler = c;
            enableFlowQueryTask = true;
        }
        
        public boolean isEnableFlowQueryTask() {
            return enableFlowQueryTask;
        }

        public void setEnableFlowQueryTask(boolean enableFlowQueryTask) {
            this.enableFlowQueryTask = enableFlowQueryTask;
        }

        @Override
        public void run() {
            if (!enableFlowQueryTask) return;
            
            /* delete all stale entries from flow cache */
            scanForStaleFlows(STALE_SCAN_COUNT);
            bfcDb.updateFlush();
            Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
            if (switches == null) {
                return;
            }
            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Sending flow scan messages to switches {}", switches.keySet());
            }
            int numSwitches = switches.size();
            if (numSwitches == 0) {
                return;
            }
            /* We would like to scan each switch every
             * SWITCH_FLOW_TBL_SCAN_INTERVAL_SEC. The interval
             * below calculates the time interval between sending query to one 
             * switch and the next in milliseconds so that they are staggered 
             * over the SWITCH_FLOW_TBL_SCAN_INTERVAL_SEC time.
             */
            int interval_ms =
                    (periodicSwScanIntervalMsec) / numSwitches;
            int idx = 0;
            for (Long swId : switches.keySet()) {
                SwitchFlowTablePeriodicScanTask scanTask =
                        new SwitchFlowTablePeriodicScanTask(swId, 
                                                            callbackHandler);
                /* Schedule the queries to different switches in a 
                 * staggered way */
                threadPool.getScheduledExecutor().schedule(scanTask,
                        interval_ms*idx, TimeUnit.MILLISECONDS);
                idx++;
            }
        }
    }

    @Override
    public void querySwitchFlowTable(long swDpid) {
        querySwitchStats(swDpid, this);
    }
    
    //***********************
    // IOFSwitchListener events
    //***********************
    
    @Override
    public void addedSwitch(IOFSwitch sw) {
        if (logger.isTraceEnabled()) {
            logger.trace("Handling switch added notification for {}", sw.getStringId());
        }

        // Query the switch for its flow entries 
        querySwitchFlowTable(sw.getId());
        return;
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        if (logger.isTraceEnabled()) {
            logger.trace("Handling switch removed notification for {}", sw.getStringId());
        }
        /* Delete all the flows in the flow cache that has this removed switch
         * as the source switch as we are not going to get flow mod removal
         * notifications from this switch. If the switch reconnects later with
         * active entries then we would query the flow table of the switch and
         * re-populate the flow cache. This is done in addedSwitch method.
         */
        deleteAllFlowsAtASourceSwitch(sw);
        // Flush the counters
        updateFlush();
        return;
    }
    
    @Override
    public void switchPortChanged(Long switchId) {
        // no-op
    }
    
    // IHARoleListener
    
    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                // no-op for now, assume it will re-learn all it's state
                break;
            case SLAVE:
                if (logger.isDebugEnabled()) {
                    logger.debug("Clearing flowcache due to " +
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
            Map<String, String> removedControllerNodeIPs) {
        // ignore
    }
    
    protected void clearCachedState() {
        if (this.bfcDb == null) return;
        pendingQueryList.clear();
        fcQueryRespSeqNum = Long.MAX_VALUE << 2;
        bfcDb.flowCache.clear();
        bfcDb.switchesToQuery.clear();
        bfcDb.fcCounters.clearCounts();
        bfcDb.flowModRemovalMsgLossCnt = 0;
    }

}
