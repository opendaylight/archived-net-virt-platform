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

package org.sdnplatform.core.web;

import java.util.HashMap;

import org.openflow.util.HexString;
import org.restlet.resource.ServerResource;


import org.restlet.resource.Get;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.RoleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchRoleResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(SwitchRoleResource.class);

    @Get("json")
    public Object getRole() {
        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());

        String switchId = (String) getRequestAttributes().get("switchId");
        
        RoleInfo roleInfo;
        
        if (switchId.equalsIgnoreCase("all")) {
            HashMap<String,RoleInfo> model = new HashMap<String,RoleInfo>();
            for (IOFSwitch sw: controllerProvider.getSwitches().values()) {
                switchId = sw.getStringId();
                roleInfo = new RoleInfo(sw.getHARole(), null);
                model.put(switchId, roleInfo);
            }
            return model;
        }
        
        Long dpid = HexString.toLong(switchId);
        IOFSwitch sw = controllerProvider.getSwitches().get(dpid);
        if (sw == null)
            return null;
        roleInfo = new RoleInfo(sw.getHARole(), null);
        return roleInfo;
    }
}
