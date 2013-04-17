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

import org.restlet.data.Status;
import org.restlet.resource.ServerResource;


import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.RoleInfo;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerRoleResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(ControllerRoleResource.class);

    @Get("json")
    public RoleInfo getRole() {
        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());
        return controllerProvider.getRoleInfo();
    }
    
    @Post("json")
    @LogMessageDoc(level="WARN",
                   message="Invalid role value specified in REST API to " +
                      "set controller role",
                   explanation="An HA role change request was malformed.",
                   recommendation=LogMessageDoc.CHECK_CONTROLLER)
    public void setRole(RoleInfo roleInfo) {
        //Role role = Role.lookupRole(roleInfo.getRole());
        Role role = null;
        try {
            role = Role.valueOf(roleInfo.getRole().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            // The role value in the REST call didn't match a valid
            // role name, so just leave the role as null and handle
            // the error below.
        }
        if (role == null) {
            log.warn ("Invalid role value specified in REST API to " +
            		  "set controller role");
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid role value");
            return;
        }
        
        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());
        
        controllerProvider.setRole(role, roleInfo.getRoleChangeDescription());
    }
}
