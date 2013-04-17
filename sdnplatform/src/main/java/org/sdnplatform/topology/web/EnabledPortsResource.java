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

package org.sdnplatform.topology.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;

public class EnabledPortsResource extends ServerResource {
    @Get("json")
    public List<NodePortTuple> retrieve() {
        List<NodePortTuple> result = new ArrayList<NodePortTuple>();

        IControllerService controllerProvider =
                (IControllerService)getContext().getAttributes().
                get(IControllerService.class.getCanonicalName());

        ITopologyService topology= 
                (ITopologyService)getContext().getAttributes().
                get(ITopologyService.class.getCanonicalName());

        if (controllerProvider == null || topology == null)
            return result;

        Set<Long> switches = controllerProvider.getSwitches().keySet();
        if (switches == null) return result;

        for(long sw: switches) {
            Set<Short> ports = topology.getPorts(sw);
            if (ports == null) continue;
            for(short p: ports) {
                result.add(new NodePortTuple(sw, p));
            }
        }
        return result;
    }
}
