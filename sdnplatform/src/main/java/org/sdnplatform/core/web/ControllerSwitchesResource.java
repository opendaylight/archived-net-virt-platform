/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *    Originally created by David Erickson, Stanford University 
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the
 *    License. You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an "AS
 *    IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language
 *    governing permissions and limitations under the License. 
 */

package org.sdnplatform.core.web;

import java.util.Collections;
import java.util.Iterator;


import org.openflow.util.HexString;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.util.FilterIterator;

/**
 * Get a list of switches connected to the controller
 * @author readams
 */
public class ControllerSwitchesResource extends ServerResource {
    public static final String DPID_ERROR = 
            "Invalid Switch DPID: must be a 64-bit quantity, expressed in " + 
            "hex as AA:BB:CC:DD:EE:FF:00:11";
    
    @Get("json")
    public Iterator<IOFSwitch> retrieve() {
        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());

        Long switchDPID = null;
        
        Form form = getQuery();
        String dpid = form.getFirstValue("dpid", true);
        if (dpid != null) {
            try {
                switchDPID = HexString.toLong(dpid);
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, DPID_ERROR);
                return null;
            }
        }
        if (switchDPID != null) {
            IOFSwitch sw = 
                    controllerProvider.getSwitches().get(switchDPID);
            if (sw != null)
                return Collections.singleton(sw).iterator();
            return Collections.<IOFSwitch>emptySet().iterator();
        }
        final String dpidStartsWith = 
                form.getFirstValue("dpid__startswith", true);
        Iterator<IOFSwitch> switer = 
                controllerProvider.getSwitches().values().iterator();
        if (dpidStartsWith != null) {
            return new FilterIterator<IOFSwitch>(switer) {
                @Override
                protected boolean matches(IOFSwitch value) {
                    return value.getStringId().startsWith(dpidStartsWith);
                }
            };
        } 
        return switer;
    }
}
