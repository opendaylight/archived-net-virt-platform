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

package org.sdnplatform.staticflowentry.web;

import java.util.HashMap;
import java.util.Map;


import org.openflow.protocol.OFFlowMod;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.web.ControllerSwitchesResource;
import org.sdnplatform.staticflowentry.IStaticFlowEntryPusherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListStaticFlowEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ListStaticFlowEntriesResource.class);
    
    @Get
    public Map<String, Map<String, OFFlowMod>> ListStaticFlowEntries() {
        IStaticFlowEntryPusherService sfpService =
                (IStaticFlowEntryPusherService)getContext().getAttributes().
                    get(IStaticFlowEntryPusherService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("switch");
        if (log.isDebugEnabled())
            log.debug("Listing all static flow entires for switch: " + param);
        
        if (param.toLowerCase().equals("all")) {
            return sfpService.getFlows();
        } else {
            try {
                Map<String, Map<String, OFFlowMod>> retMap = 
                        new HashMap<String, Map<String, OFFlowMod>>();
                retMap.put(param, sfpService.getFlows(param));
                return retMap;
                
            } catch (NumberFormatException e){
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, 
                          ControllerSwitchesResource.DPID_ERROR);
            }
        }
        return null;
    }
}
