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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.topology.BroadcastDomain;
import org.sdnplatform.topology.IBetterTopologyService;
import org.sdnplatform.topology.NodePortTuple;


public class ExternalPortsResource extends ServerResource {
    @Get("json")
    public Map<Long, List<NodePortTuple>> retrieve() {
        IBetterTopologyService topology = 
                (IBetterTopologyService)getContext().getAttributes().
                    get(IBetterTopologyService.class.getCanonicalName());

        Set<BroadcastDomain> bDomains = topology.getBroadcastDomains();
        Map<Long, List<NodePortTuple>> result = new HashMap<Long, List<NodePortTuple>>();

        for(BroadcastDomain bd: bDomains) {
            List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
            for(NodePortTuple npt: bd.getPorts()) {
                nptList.add(npt);
            }
            result.put(bd.getId(), nptList);
        }
        return result;
    }
}
