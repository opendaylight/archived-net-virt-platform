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

package org.sdnplatform.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


import org.codehaus.jackson.annotate.JsonProperty;
import org.sdnplatform.core.IControllerService.Role;


public class RoleInfo {
    protected String role;
    protected String roleChangeDescription;
    protected Date roleChangeDateTime;

    public RoleInfo() {
    }

    public RoleInfo(String role) {
        setRole(role);
    }

    public RoleInfo(Role role, String description) {
        this.role = (role != null) ? role.name() : "DISABLED";
        this.roleChangeDescription = description;
    }

    public RoleInfo(Role role, String description, Date dt) {
        this.role = (role != null) ? role.name() : "DISABLED";
        this.roleChangeDescription = description;
        this.roleChangeDateTime = dt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @JsonProperty(value="change-description")
    public String getRoleChangeDescription() {
        return roleChangeDescription;
    }
    @JsonProperty(value="change-description")
    public void setRoleChangeDescription(String roleChangeDescription) {
        this.roleChangeDescription = roleChangeDescription;
    }
    @JsonProperty(value="change-date-time")
    public String getRoleChangeDateTime() {
        SimpleDateFormat formatter =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return roleChangeDateTime == null ?
                  "" : formatter.format(roleChangeDateTime);
    }

}