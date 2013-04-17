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

package org.sdnplatform.netvirt.web;


import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;
import org.sdnplatform.restserver.RestletRoutable;

/**
 * Routable for NetVirt REST APIs
 * @author readams
 */
public class NetVirtWebRoutable implements RestletRoutable {
    /**
     * Set the base path for NetVirtWebRouteable
     */
    @Override
    public String basePath() {
        return "/wm/vns";
    }
    
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/", NetVirtResource.class);
        router.attach("/interface/", NetVirtInterfaceResource.class);
        router.attach("/device-interface/", NetVirtDeviceInterfaceResource.class);
        router.attach("/explain-packet/json", ExplainResource.class);
        router.attach("/packettrace/json",    PacketTraceResource.class);
        router.attach("/flow/{netVirtName}/json", NetVirtFlowResource.class);
        router.attach("/forwarding/flags/json",
                      InternalDebugsForwardingResource.class);
        router.attach("/internal-debugs/topology-manager/{param}/json", 
                      InternalDebugsTopoMgrResource.class);
        router.attach("/internal-debugs/forwarding/{param}/json",
                      InternalDebugsForwardingResource.class);
        // applName = Application Name (e.g. "netVirt")
        // applInstName = Application Instance Name (e.g., name of the netVirt)
        // querytype = { counters | all } 
        // When querytype = counters only counters are returned and applInstName
        // must be all
        router.attach("/flow-cache/{applName}/{applInstName}/{querytype}/json", 
                      FlowCacheMgrResource.class);
        router.attach("/tunnel-manager/{param}/json", 
                      TunnelManagerResource.class);
        return router;
    }
}
