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

package org.sdnplatform.messagefilter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.OFType;
import org.openflow.protocol.OFMessage;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.OFMessageFilterManager;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class MessageFilterManager extends OFMessageFilterManager {
    
    /**
     * @author Srini
     * 
     * This class overrides the getMatchedFilters function of OFMessageFilterManager.
     * This class will have visibility to NetVirt name through the context while the 
     * OFMessageFilter resides on the open-source side of the project, which is unaware of NetVirt.
     * 
     * @param bp  sdnplatform provider
     */
    protected static Logger log = LoggerFactory.getLogger(MessageFilterManager.class);
    
    private HashSet<String> getNetVirtsByInterface(List<VNSInterface> ifaces) {
        HashSet<String> netVirts = new HashSet<String>();
        
        if (ifaces != null && !ifaces.isEmpty()) {
            for (VNSInterface iface : ifaces) {
                netVirts.add(iface.getParentVNS().getName());
            }
        }
        
        return netVirts;
    }
    
    @Override
    public HashSet<String> getMatchedFilters(OFMessage m, ListenerContext cntx) {  
        // If no context, no NetVirt affiliation can be determined.
        if (cntx == null) {
            log.debug("Empty context for packet {}", m);
            return null;
        }
        
        // Since the BigMessageFilterManager is after virtual routing, 
        // it is guaranteed that the source and destination interfaces
        // will be identical at this stage.
        List<VNSInterface> srcIface = INetVirtManagerService.bcStore.get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);
        HashSet<String> netVirts = getNetVirtsByInterface(srcIface);
        
        HashSet<String> matchedFilters = new HashSet<String>();

        Iterator<String> filterIt = filterMap.keySet().iterator();
        while (filterIt.hasNext()) {   // for every filter
            boolean filterMatch = false;
            String filterSessionId = filterIt.next();
            ConcurrentHashMap<String,String> filter = filterMap.get(filterSessionId);
            
            // If the filter has empty fields, then it is not considered as a match.
            if (filter == null || filter.isEmpty()) continue;                  
            Iterator<String> fieldIt = filter.keySet().iterator();
            while (fieldIt.hasNext()) {   // for every filter field  // and if even one fails, the filter match fails.
                String filterFieldType = fieldIt.next();
                String filterFieldValue = filter.get(filterFieldType);
                
                // Match on direction
                log.debug("Looking for {}:{} in NetVirts {}", new Object[] {filterFieldType, filterFieldValue, m.getType().toString()});
                if (filterFieldType.equals("direction")) {
                    if (filterFieldValue.equalsIgnoreCase("both")) {
                        filterMatch = true;
                    } else if (filterFieldValue.equalsIgnoreCase("in") && m.getType() == OFType.PACKET_IN) {
                        filterMatch = true;
                    } else if (filterFieldValue.equalsIgnoreCase("out") && 
                               (m.getType() == OFType.FLOW_MOD ||
                                m.getType() == OFType.PACKET_OUT)) {
                        filterMatch = true;   // matches at least one NetVirt name
                    } else {
                        filterMatch = false;
                        break;
                    }
                } 
                    
                // Match on netVirt
                log.debug("Looking for {} in NetVirts {}", filterFieldValue, netVirts.toString());
                if (filterFieldType.equals("netVirt")) {
                    if (netVirts.contains(filterFieldValue)) {
                        log.debug("packet {} matches netVirt {} for session {}", 
                                new Object[]{m, filterFieldValue, filterSessionId});
                        filterMatch = true;   // matches at least one NetVirt name
                    } else {
                        filterMatch = false;
                        break;
                    }
                }
                // currently the check is performed only for NetVirt.  If we need more
                // checks, we can append this section.
            }
            
            if (filterMatch) {
                matchedFilters.add(filterSessionId);
            }
        }

        if (matchedFilters.isEmpty()) {
            return null;    
        } else {
            return matchedFilters;
        }
   }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("virtualrouting"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("forwarding"));
    }
}
