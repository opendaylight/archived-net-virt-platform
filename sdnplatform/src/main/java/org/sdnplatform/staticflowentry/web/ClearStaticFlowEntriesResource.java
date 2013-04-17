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


import org.openflow.util.HexString;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.web.ControllerSwitchesResource;
import org.sdnplatform.staticflowentry.IStaticFlowEntryPusherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearStaticFlowEntriesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(ClearStaticFlowEntriesResource.class);
    
    @Get
    public void ClearStaticFlowEntries() {
        IStaticFlowEntryPusherService sfpService =
                (IStaticFlowEntryPusherService)getContext().getAttributes().
                    get(IStaticFlowEntryPusherService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("switch");
        if (log.isDebugEnabled())
            log.debug("Clearing all static flow entires for switch: " + param);
        
        if (param.toLowerCase().equals("all")) {
            sfpService.deleteAllFlows();
        } else {
            try {
                sfpService.deleteFlowsForSwitch(HexString.toLong(param));
            } catch (NumberFormatException e){
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, 
                          ControllerSwitchesResource.DPID_ERROR);
                return;
            }
        }
    }
}
