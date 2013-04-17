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

import java.util.Set;


import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;

public class TunnelLinksResource extends ServerResource {
    @Get("json")
    public Set<NodePortTuple> retrieve() {
        ITopologyService topology = 
                (ITopologyService)getContext().getAttributes().
                    get(ITopologyService.class.getCanonicalName());
        
        return topology.getTunnelPorts();
    }
}
