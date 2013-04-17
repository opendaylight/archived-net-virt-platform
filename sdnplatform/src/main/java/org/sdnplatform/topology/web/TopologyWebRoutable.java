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

import org.restlet.Context;
import org.restlet.routing.Router;
import org.sdnplatform.linkdiscovery.web.ExternalLinksResource;
import org.sdnplatform.linkdiscovery.web.LinksResource;
import org.sdnplatform.restserver.RestletRoutable;


public class TopologyWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/links/json", LinksResource.class);
        router.attach("/external-links/json", ExternalLinksResource.class);
        router.attach("/tunnellinks/json", TunnelLinksResource.class);
        router.attach("/switchclusters/json", SwitchClustersResource.class);
        router.attach("/broadcastdomainports/json", BroadcastDomainPortsResource.class);
        router.attach("/enabledports/json", EnabledPortsResource.class);
        router.attach("/blockedports/json", BlockedPortsResource.class);
        router.attach("/route/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/json", RouteResource.class);
        router.attach("/externalports/json", ExternalPortsResource.class);
        router.attach("/highertopology/openflowdomains/json", HigherTopologyOpenflowDomainsResource.class);
        router.attach("/highertopology/broadcastdomains/json", HigherTopologyBroadcastDomainsResource.class);
        router.attach("/highertopology/neighbors/json", HigherToplogyNeighborsResource.class);
        router.attach("/highertopology/nexhops/json", HigherToplogyNextHopsResource.class);
        router.attach("/layer2/domains/json", Layer2DomainsResource.class);
        router.attach("/allowedports/unicast/json", AllowedUnicastResource.class);
        router.attach("/allowedports/broadcast/json", AllowedBroadcastResource.class);
        router.attach("/allowedports/external/json", AllowedPortsToBroadcastDomainResource.class);
        router.attach("/tunnelstatus/{src-dpid}/{dst-dpid}/json", TunnelStatusResource.class);
        router.attach("/tunnelverify/{src-dpid}/{dst-dpid}/json", TunnelVerifyResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    @Override
    public String basePath() {
        return "/wm/topology";
    }
}
