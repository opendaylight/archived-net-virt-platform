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

import org.codehaus.jackson.annotate.JsonIgnore;
import org.openflow.protocol.statistics.OFDescriptionStatistics;


/**
 * OFDescriptionStatistics
 *   Vendor (Manufacturer Desc.): Big Switch Networks
 *   Make (Hardware Desc.)      : Open vSwitch 
 *   Model (Datapath Desc.)     : None
 *   Software                   : 1.9.90 (or whatever version + build)
 *   Serial                     : None
 *
 *   @author Saurav
 */
public class BetterOFSwitchBSNOVS extends BetterOFSwitchOVS {
    
    @JsonIgnore
    @Override
    public void setSwitchProperties(OFDescriptionStatistics description) {
        super.setSwitchProperties(description);
        setAttribute(IBetterOFSwitch.SUPPORTS_BSN_SET_TUNNEL_DST_ACTION, true);
        removeAttribute(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP);
        setAttribute(IBetterOFSwitch.SUPPORTS_NX_TTL_DECREMENT, true);
    }

}
