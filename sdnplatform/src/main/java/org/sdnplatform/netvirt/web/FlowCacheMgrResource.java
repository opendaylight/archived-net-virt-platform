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

/**
 * 
 */
package org.sdnplatform.netvirt.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.annotate.JsonProperty;
import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.flowcache.BetterFlowCache;
import org.sdnplatform.flowcache.FlowCacheObj;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.BetterFlowCache.BfcDb;
import org.sdnplatform.flowcache.FlowCacheObj.FCEntry;
import org.sdnplatform.packet.IPv4;

import com.google.common.collect.MinMaxPriorityQueue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author subrata
 *
 */
public class FlowCacheMgrResource extends ServerResource {
    public static final int MAX_ENTRIES_TO_RETURN = 1000;
    /**
     * A dummy class to flatten flow cache entry and then serialize them
     * for the REST API. 
     * This is a rather ugly hack but given the current state of FlowCache 
     * it's the best we can do at this time and we'll throw it away once we
     * actually fix FC. 
     *
     */
    @SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
            justification="Fields are serialized by Jackson")
    public static class BetterFlowCacheRestEntry 
                implements Comparable<BetterFlowCacheRestEntry>{
        @JsonProperty("Appl")
        public String applName;
        // These properties are from the HashMap structures
        @JsonProperty("VLAN")
        public short vlan;
        @JsonProperty("AppInst")
        public String appInstName;
        @JsonProperty("DestMAC")
        public String dstMAC;
        @JsonProperty("SrcMAC")
        public String srcMAC;
        
        // These properties are from FlowCacheObj
        @JsonProperty("Cookie")
        public long cookie;
        @JsonProperty("Time")
        public Long installTimeMs;
        
        // These properties are from FCEntry
        @JsonProperty("EtherType")
        public String etherType;
        @JsonProperty("PCP")
        public int pcp;
        @JsonProperty("SrcIPAddr")
        public String srcIpAddr;
        @JsonProperty("DstIPAddr")
        public String dstIpAddr;
        @JsonProperty("Protocol")
        public int protocol;
        @JsonProperty("TOS")
        public int tos;
        @JsonProperty("SrcPort")
        public int srcPort;
        @JsonProperty("DstPort")
        public int dstPort;
        @JsonProperty("Source-Switch")
        public String srcSwitch;
        @JsonProperty("InputPort")
        public int inputPort;
        @JsonProperty("Wildcards")
        public String wildcards;
        @JsonProperty("OFPri")
        public int ofPriority;
        @JsonProperty("Action")
        public String fcAction;
        @JsonProperty("State")
        public String fcState;
        @JsonProperty("SC")
        public byte scanCount;
        
        /**
         * Creates a new entry with the given fields. 
         * @param appInstName
         * @param vlan
         * @param dstMAC
         * @param srcMAC
         * @param cookie
         * @param installTimeNs
         * @param fce
         */
        @SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
                            justification="Fields are serialized by Jackson")
        public BetterFlowCacheRestEntry(String appInstName,
                                     short vlan,
                                     long dstMAC,
                                     long srcMAC,
                                     long cookie,
                                     long installTimeNs,
                                     FCEntry fce) {
            this.applName = "netVirt";
            this.appInstName = appInstName;
            this.vlan = vlan;
            this.dstMAC = HexString.toHexString(dstMAC, 6);
            this.srcMAC = HexString.toHexString(srcMAC, 6);
            this.cookie = cookie;
            long curTimeNs = System.nanoTime();
            long elapsedMs=(curTimeNs - installTimeNs)/1000000;
            this.installTimeMs=System.currentTimeMillis()-elapsedMs;
            
            this.etherType = String.format("0x%04x", fce.getEtherType());
            this.pcp = fce.getPcp();
            this.srcIpAddr = IPv4.fromIPv4Address(fce.getSrcIpAddr());
            this.dstIpAddr = IPv4.fromIPv4Address(fce.getDestIpAddr());
            this.protocol = fce.getProtocol();
            this.tos = fce.getNwTos();
            this.srcPort = fce.getSrcL4Port();
            this.dstPort = fce.getDestL4Port();
            this.srcSwitch = HexString.toHexString(fce.getSrcSwitchDpid(), 8);
            this.inputPort = fce.getInputPort();
            // wildcards field is 22 bits as per OF 1.0
            this.wildcards = String.format("0x%06x", fce.getWildcards());
            this.ofPriority = fce.getOfPri();
            if (fce.getAction() == FlowCacheObj.FCActionPERMIT) {
                this.fcAction = "PERMIT";
            } else {
                this.fcAction = "DENY";
            }
            if (fce.getState() == FlowCacheObj.FCStateACTIVE) {
                this.fcState = "ACTIVE";
            } else {
                this.fcState = "INACTIVE";
            }
            this.scanCount = fce.getScanCount();
        }

        @Override
        public int compareTo(BetterFlowCacheRestEntry o) {
            return -installTimeMs.compareTo(o.installTimeMs);
        }
    }
    
    /**
     * A dummy helper class representing the counters and stats of the 
     * flow cache to return via REST.
     * @author gregor
     *
     */
    @SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
            justification="Fields are serialized by Jackson")
    public static class BetterFlowCacheRestCounters {
        public BetterFlowCacheRestCounters(BfcDb bfcCore) {
            applName = bfcCore.getApplName();
            maxFlows = bfcCore.getMaxFlows();
            activeCnt = bfcCore.getActiveCnt();
            inactiveCnt = bfcCore.getInactiveCnt();
            addCnt = bfcCore.getAddCnt();
            delCnt = bfcCore.getDelCnt();
            activatedCnt = bfcCore.getActivatedCnt();
            deactivatedCnt = bfcCore.getDeactivatedCnt();
            cacheHitCnt = bfcCore.getCacheHitCnt();
            missCnt = bfcCore.getMissCnt();
            flowModRemovalMsgLossCnt = bfcCore.getFlowModRemovalMsgLossCnt();
            notStoredFullCnt = bfcCore.getNotStoredFullCnt();
            fcObjFreedCnt = bfcCore.getFcObjFreedCnt();
            unknownOperCnt = bfcCore.getUnknownOperCnt();
            flowCacheAlmostFull = bfcCore.isFlowCacheAlmostFull();
        }
        public String applName;
        public long maxFlows;
        public long activeCnt;
        public long inactiveCnt;
        public long addCnt;
        public long delCnt;
        public long activatedCnt;
        public long deactivatedCnt;
        public long cacheHitCnt;
        public long missCnt;
        public long flowModRemovalMsgLossCnt;
        public long notStoredFullCnt;
        public long fcObjFreedCnt;
        public long unknownOperCnt;
        public boolean flowCacheAlmostFull;
    }
    
    /**
     * A dummy class that represents the return value for flow cache
     * rest calls.
     *
     */
    @SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
            justification="Fields are serialized by Jackson")
    public static class BetterFlowCacheRestData {
        public MinMaxPriorityQueue<BetterFlowCacheRestEntry> flows;
        public BetterFlowCacheRestCounters counters;
        public String description;
        public String status;
        
        /**
         * A helper method. Populate flow entries to the "flows" list for
         * a single app instance name. 
         * 
         * @param appInstName The appInstanceName
         * @param vlanEntriesMap The map for this app instance name (from 
         * BfcDB)
         */
        private void addAllEntriesForOneAppInstance(
                String appInstName, 
                ConcurrentHashMap<Short,
                                  ConcurrentHashMap<Long,
                                  ConcurrentHashMap<Long,
                                  FlowCacheObj>>> vlanEntriesMap) {
            for(Map.Entry<Short,
                    ConcurrentHashMap<Long,
                    ConcurrentHashMap<Long,
                    FlowCacheObj>>> vlanEntry: vlanEntriesMap.entrySet()) {
                Short vlan = vlanEntry.getKey();
                for(Map.Entry<Long,
                        ConcurrentHashMap<Long,
                        FlowCacheObj>> dstMacEntry: 
                            vlanEntry.getValue().entrySet()) {
                    
                    Long dstMac = dstMacEntry.getKey();
                    for(Map.Entry<Long, FlowCacheObj> srcMacEntry:
                            dstMacEntry.getValue().entrySet()) {
                        Long srcMac = srcMacEntry.getKey();
                        FlowCacheObj fcObj = srcMacEntry.getValue();
                        addAllEntriesForOneFCObj(appInstName,
                                                 vlan,
                                                 dstMac,
                                                 srcMac,
                                                 fcObj);
                    }
                }
            }
        }
        
        /**
         * A helper method. Populate flow entries to the "flows" kust for 
         * a particular FlowCacheObject
         * @param appInstName
         * @param vlan
         * @param dstMac
         * @param srcMac
         * @param fcObj
         */
        private void addAllEntriesForOneFCObj(String appInstName,
                                              Short vlan,
                                              Long dstMac,
                                              Long srcMac,
                                              FlowCacheObj fcObj) {
            if (fcObj.fce != null) {
                flows.add(new BetterFlowCacheRestEntry(appInstName, 
                                                    vlan,
                                                    dstMac,
                                                    srcMac,
                                                    fcObj.cookie, 
                                                    fcObj.installTimeNs, 
                                                    fcObj.fce));
            }
            if (fcObj.fceList != null && fcObj.fceList.size() > 0) {
                for(FCEntry fce: fcObj.fceList) {
                    flows.add(new BetterFlowCacheRestEntry(appInstName, 
                                                        vlan,
                                                        dstMac,
                                                        srcMac,
                                                        fcObj.cookie, 
                                                        fcObj.installTimeNs, 
                                                        fce));
                }
            }
        }
        
        private BetterFlowCacheRestData() {
            super();
        }
        
        private BetterFlowCacheRestData(BfcDb bfcCore) {
            super();
            this.counters = new BetterFlowCacheRestCounters(bfcCore);
            this.description = "Flow Cache for application=" +
                                  this.counters.applName +
                                  " application instance=all" +
                                  " query type=counters";
            this.status = "OK";
            this.flows = MinMaxPriorityQueue
                    .maximumSize(MAX_ENTRIES_TO_RETURN)
                    .create();
        }
        
        /**
         * Perform a query for all flows in the cache and return
         * {@link BetterFlowCacheRestData} instance representing the result
         * @param bfcCore
         * @return
         */
        public static BetterFlowCacheRestData queryAllFlows(BfcDb bfcCore) {
            BetterFlowCacheRestData rv = new BetterFlowCacheRestData(bfcCore);
            rv.description = "Flow Cache for application=" +
                                  rv.counters.applName +
                                  " application instance=all" +
                                  " query type=all";
            ConcurrentHashMap<String, 
                              ConcurrentHashMap<Short,
                              ConcurrentHashMap<Long, 
                              ConcurrentHashMap<Long,
                              FlowCacheObj>>>> cache = bfcCore.getFlowCache();
            
            for (Map.Entry<String,
                    ConcurrentHashMap<Short,
                    ConcurrentHashMap<Long,
                    ConcurrentHashMap<Long,
                    FlowCacheObj>>>> netVirtEntry: cache.entrySet()) {
                String appInstName = netVirtEntry.getKey();
                rv.addAllEntriesForOneAppInstance(appInstName, 
                                                  netVirtEntry.getValue());
            }
            return rv;
        }
        
        /**
         * Perform a query for just the counters and return a new 
         * {@link BetterFlowCacheRestData} instance representing the result
         * @param bfcCore
         * @return
         */
        public static BetterFlowCacheRestData queryCounters(BfcDb bfcCore) {
            BetterFlowCacheRestData rv = new BetterFlowCacheRestData(bfcCore);
            return rv; 
        }
        
        /**
         * Perform a query for all flows for a single app instance in the 
         * cache and return {@link BetterFlowCacheRestData} instance representing the 
         * result
         * @param bfcCore
         * @return
         */
        public static BetterFlowCacheRestData queryOneAppInst(BfcDb bfcCore,
                                                           String appInstName) {
            BetterFlowCacheRestData rv = new BetterFlowCacheRestData(bfcCore);
            rv.description = "Flow Cache for application=" +
                                  rv.counters.applName +
                                  " application instance=" +
                                  appInstName +
                                  " query type=all";
            
            ConcurrentHashMap<Short,
                              ConcurrentHashMap<Long, 
                              ConcurrentHashMap<Long,
                              FlowCacheObj>>> entries =
                   bfcCore.getFlowCache().get(appInstName);
            rv.addAllEntriesForOneAppInstance(appInstName, entries);
            return rv;
        }
        
        /**
         * Return a {@link BetterFlowCacheRestData} representing a query error
         * @param description
         * @param status
         * @return
         */
        public static BetterFlowCacheRestData queryError(String description,
                                                      String status) {
            BetterFlowCacheRestData rv = new BetterFlowCacheRestData();
            rv.description = description;
            rv.status = status;
            return rv;
        }
    }

    @Get("json")
    public BetterFlowCacheRestData handleApiQuery() {

        String applName    = (String)getRequestAttributes().get("applName");
        String applInstName= (String)getRequestAttributes().get("applInstName");
        String queryType   = (String)getRequestAttributes().get("querytype");
        if (applInstName.contains("%7C")) {
            applInstName=applInstName.replace("%7C", "|");
        }

        String errorDescription = "Flow Cache for application=" +
                                  applName +
                                  " application instance=" +
                                  applInstName +
                                  " query type=" +
                                  queryType;

        // Currently flow cache for netVirt application only is supported
        if (!applName.equalsIgnoreCase("netVirt")) {
            String status = "ERROR: Application "+applName+" not found" ;
            return BetterFlowCacheRestData.queryError(errorDescription, status);
        }

        // Get the flow cache object
        BetterFlowCache bfc = (BetterFlowCache)getContext().getAttributes().
                                get(IFlowCacheService.class.getCanonicalName());
        BfcDb bfcCore = bfc.getBfcCore();

        // If the querytype is counters then appl name and instance are ignored
        if (queryType.equals("counters")) {
            return BetterFlowCacheRestData.queryCounters(bfcCore);
        } else {
            // Check queryType, it should be "all"
            if (!queryType.equalsIgnoreCase("all")) {
                String status = "ERROR: Unknown query type: " + queryType;
                return BetterFlowCacheRestData.queryError(errorDescription, status);
            }
            if (applInstName.equalsIgnoreCase("all")) {
                return BetterFlowCacheRestData.queryAllFlows(bfcCore);
            } else {
                // Only return flows for the given appl. instance name
                if (bfcCore.getFlowCache().containsKey(applInstName)) {
                    return BetterFlowCacheRestData.queryAllFlows(bfcCore);
                } else {
                    // FIXME: we shouldn't return an error here but well. 
                    // More things are broken here.
                    String status = "ERROR: Application instance " + 
                                         applInstName + " not found";
                    return BetterFlowCacheRestData.queryError(errorDescription,
                                                           status);
                }
            }
        }
    }
}
