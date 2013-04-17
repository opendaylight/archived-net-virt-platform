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

package org.sdnplatform.netvirt.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.web.AllSwitchStatisticsResource;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Implements REST API to get flows categorized on NetVirt
 * @author subrata
 * @param netVirt, switch are optional parameters, by default it gets flows for all NetVirtes from all switches
 *
 */
@LogMessageCategory("Network Virtualization")
public class NetVirtFlowResource extends AllSwitchStatisticsResource {
    protected static Logger log = LoggerFactory.getLogger(ExplainResource.class);
        
    public class OFFlowStatsReplywithDpid {
        String                 dpid;
        OFFlowStatisticsReply  flowEntry;
        
        public String getDpid() {
            return dpid;
        }
        public void setDpid(String dpid) {
            this.dpid = dpid;
        }
        
        public OFFlowStatisticsReply getFlowEntry() {
            return flowEntry;
        }
        public void setFlowEntry(OFFlowStatisticsReply flowEntry) {
            this.flowEntry = flowEntry;
        }
    }
    
    public class PerNetVirtFlowOutput {             
        protected int    flowCount=1;
        protected List<OFFlowStatsReplywithDpid> flowList;
        
        public int getFlowCount() {
            return flowCount;
        }
        public void setFlowCount(int flowCount) {
            this.flowCount = flowCount;
        }
        public List<OFFlowStatsReplywithDpid> getFlowList() {
            return flowList;
        }
        public void setFlowList(List<OFFlowStatsReplywithDpid> flowList) {
            this.flowList = flowList;
        }
    }
    
    // This is the output structure of the JSON return object    
    public class MultiNetVirtFlowOutput {
        
        int netVirtCount=0;        
        // Key = netVirt name (String)
        protected HashMap<String, PerNetVirtFlowOutput> netVirtFlowMap = new HashMap<String, PerNetVirtFlowOutput>();
        protected HashMap<String, String> netVirtAddressSpaceMap = new HashMap<String, String>();
    
        public int getNetVirtCount() {
            return netVirtCount;
        }

        public void setNetVirtCount(int netVirtCount) {
            this.netVirtCount = netVirtCount;
        }

        public HashMap<String, PerNetVirtFlowOutput> getNetVirtFlowMap() {
            return netVirtFlowMap;
        }
        
        public void setNetVirtFlowMap(HashMap<String, PerNetVirtFlowOutput> netVirtFlowMap) {
            this.netVirtFlowMap = netVirtFlowMap;
        }

        public HashMap<String, String> getNetVirtAddressSpaceMap() {
            return netVirtAddressSpaceMap;
        }

        public void setNetVirtAddressSpaceMap(
                        HashMap<String, String> netVirtAddressSpaceMap) {
            this.netVirtAddressSpaceMap = netVirtAddressSpaceMap;
        }
    }
    
    /**
     * Example of the outout
     *   {
     *       "netVirtCount": 1, 
     *       "netVirtAddressSpaceMap" : { "default": "default", },
     *       "netVirtFlowMap": {
     *           "default": {
     *               "flowCount": 20, 
     *               "flowList": [
     *                   {
     *                     "dpid": "00:00:00:00:00:00:00:0f",
     *                     "flowEntry": { 
     *                       "actions": [
     *                           {
     *                               "length": 8, 
     *                               "lengthU": 8, 
     *                               "maxLength": 0, 
     *                               "port": 2, 
     *                               "type": "OUTPUT"
     *                           }
     *                       ], 
     *                       "byteCount": 6664, 
     *                       "cookie": 9007199254740992, 
     *                       "durationNanoseconds": 162000000, 
     *                       "durationSeconds": 67, 
     *                       "hardTimeout": 0, 
     *                       "idleTimeout": 5, 
     *                       "length": 96, 
     *                       "match": {
     *                           "dataLayerDestination": "00:00:00:00:00:08", 
     *                           "dataLayerSource": "00:00:00:00:00:01", 
     *                           "dataLayerType": 2048, 
     *                           "dataLayerVirtualLan": -1, 
     *                           "dataLayerVirtualLanPriorityCodePoint": 0, 
     *                           "inputPort": 3, 
     *                           "networkDestination": "0.0.0.0", 
     *                           "networkDestinationMaskLen": 0, 
     *                           "networkProtocol": 0, 
     *                           "networkSource": "0.0.0.0", 
     *                           "networkSourceMaskLen": 0, 
     *                           "networkTypeOfService": 0, 
     *                           "transportDestination": 0, 
     *                           "transportSource": 0, 
     *                           "wildcards": 4194272
     *                       }, 
     *                       "packetCount": 68, 
     *                       "priority": 0, 
     *                       "tableId": 1
     *                     }
     *                   }, ...
     *
     */    

    @Get("json")
    @LogMessageDoc(level="ERROR",
            message="findDevice() called without switch/port information.",
            explanation="Invalid REST API request",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    public MultiNetVirtFlowOutput handleNetVirtFlow() {
                
        MultiNetVirtFlowOutput output = new MultiNetVirtFlowOutput();
        
        IDeviceService deviceManager = 
            (IDeviceService)getContext().getAttributes().
                get(IDeviceService.class.getCanonicalName());
        INetVirtManagerService netVirtManager = 
            (INetVirtManagerService)getContext().getAttributes().
                get(INetVirtManagerService.class.getCanonicalName());
        
        // Get the NetVirt Name
        String paramNetVirtName = (String)getRequestAttributes().get("netVirtName");        
        if (paramNetVirtName == null) {
            paramNetVirtName = "all";
        }
        if (paramNetVirtName.contains("%7C")) {
            paramNetVirtName=paramNetVirtName.replace("%7C", "|");
        }
        if ((!paramNetVirtName.contains("|")) && (!paramNetVirtName.equals("all"))) {
            paramNetVirtName="default|".concat(paramNetVirtName);
            }
        // Get all the flows from all the switches
        Map<String, Object> allSwitchFlows = retrieveInternal("flow");
        
        // Extract the src-mac, dst-mac and vlan from the flows and then 
        // find out which netVirt the flow belongs
        for (String switchIdString : allSwitchFlows.keySet()) {
            @SuppressWarnings("unchecked")
            List<OFFlowStatisticsReply>  oneSwitchStatsList = 
                    (List<OFFlowStatisticsReply>)(allSwitchFlows.get(switchIdString));
            Long switchId = HexString.toLong(switchIdString);
            
            for (OFFlowStatisticsReply oneStatsEntry : oneSwitchStatsList) {                
                OFMatch match  = oneStatsEntry.getMatch();                
                long srcMac = Ethernet.toLong(match.getDataLayerSource());
                long dstMac = Ethernet.toLong(match.getDataLayerDestination());
                
                // Find the NetVirt name for the flow
                IDevice src = null;
                IDevice dst = null;
                try {
                    src = deviceManager.findDevice(srcMac,
                                                     match.getDataLayerVirtualLan(),
                                                     match.getNetworkSource(),
                                                     switchId,
                                                     Integer.valueOf(match.getInputPort()));
                    if (src != null) {
                        dst = deviceManager.findClassDevice(src.getEntityClass(), 
                                                         dstMac,
                                                         match.getDataLayerVirtualLan(),
                                                         match.getNetworkDestination());
                    }
                } 
                catch (IllegalArgumentException e) {
                    log.error("findDevice() called without switch/port information.");
                    continue;
                }
                
                // Retrieve interfaces for source and destination
                List<VNSInterface> srcIfaces = null;
                List<VNSInterface> dstIfaces = null;
                if (src != null) {        
                    // Retrieve interfaces for source and destination        
                    srcIfaces = netVirtManager.getInterfaces(src);
                }
                if (dst != null) {        
                    // Retrieve interfaces for source and destination        
                    dstIfaces = netVirtManager.getInterfaces(dst);
                }
                if (srcIfaces == null || dstIfaces == null) continue;
                VNS netVirtChosen=null;
                // Find the matching NetVirt with the highest priority
                for (VNSInterface sface : srcIfaces) {
                    for (VNSInterface dface : dstIfaces) {
                        if (sface.getParentVNS() == dface.getParentVNS()) {
                             if (netVirtChosen == null || 
                                 sface.getParentVNS().compareTo(netVirtChosen) < 0) {
                                 netVirtChosen = sface.getParentVNS();                                 
                             }
                             break;  // NetVirt can't repeat in dstIfaces
                        }
                    }
                }
                if (netVirtChosen == null) {
                    // Skip if src and dst devices is not in any common netVirt.
                    continue;
                }

                if ((!paramNetVirtName.equalsIgnoreCase("all")) && 
                    (!paramNetVirtName.equals(netVirtChosen.getName()))) {
                    // Skip this flow
                    continue;
                }
                // Found the NetVirt, now add it to the output structure. Don't sort the output here
                // as it has to be sorted anyways in the cli handler by aliases etc.
                String netVirtName = netVirtChosen.getName();
                // Get the flow map for the netVirt if any
                PerNetVirtFlowOutput perNetVirtfout = output.netVirtFlowMap.get(netVirtName); 
                if (perNetVirtfout == null) {
                    // First time seeing this netVirt
                    output.netVirtCount++;
                    // Add this netVirt to the hashmap                    
                    perNetVirtfout = new PerNetVirtFlowOutput();
                    perNetVirtfout.flowList = new ArrayList<OFFlowStatsReplywithDpid>(); 
                    OFFlowStatsReplywithDpid statsEntry = new OFFlowStatsReplywithDpid();
                    statsEntry.dpid = switchIdString;
                    statsEntry.flowEntry = oneStatsEntry;
                    perNetVirtfout.flowList.add(statsEntry);
                    output.netVirtFlowMap.put(netVirtName, perNetVirtfout);
                    output.netVirtAddressSpaceMap.put(
                        netVirtName, netVirtChosen.getAddressSpaceName());
                } else {
                    // netVirt already exists in the output object, Just add this flow to the list
                    perNetVirtfout.flowCount++;
                    OFFlowStatsReplywithDpid statsEntry = new OFFlowStatsReplywithDpid();
                    statsEntry.dpid = switchIdString;
                    statsEntry.flowEntry = oneStatsEntry;
                    perNetVirtfout.flowList.add(statsEntry);                    
                }
            }
        }
        // Return the output , jackson and restlet infra will convert output to json object
        // and would return it to the REST API caller
        return output;
    }    
}
