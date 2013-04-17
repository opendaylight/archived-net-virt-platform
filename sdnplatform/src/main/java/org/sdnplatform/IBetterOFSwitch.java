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

package org.sdnplatform;

/** 
 * This interface mostly contains attribute key strings for switch features,
 * attributes, etc. that are only used / supported in SDN Platform
 * 
 * @author gregor
 *
 */
public interface IBetterOFSwitch {
    public static final String SUPPORTS_BSN_SET_TUNNEL_DST_ACTION = 
            "org.sdnplatform.SupportsSetTunnelDstAction";
    public static final String SUPPORTS_OVSDB_TUNNEL_SETUP =
            "org.sdnplatform.SupportsOvsdbTunnelSetup";
    public static final String IS_VTA_SWITCH = 
            "org.sdnplatform.IsVtaSwitch";
    public static final String SUPPORTS_NX_TTL_DECREMENT = 
            "org.sdnplatform.SupportsNxTtlDecrement";

}
