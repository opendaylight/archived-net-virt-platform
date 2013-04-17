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

import java.util.Map;
import java.util.Set;


import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.topology.IBetterTopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.topology.OrderedNodePair;


public class AllowedUnicastResource extends ServerResource {
    @Get("json")
    public Map<OrderedNodePair, Set<NodePortTuple>> retrieve() {
        IBetterTopologyService topology = 
                (IBetterTopologyService)getContext().getAttributes().
                    get(IBetterTopologyService.class.getCanonicalName());

        return topology.getAllowedUnicastPorts();
    }
}
