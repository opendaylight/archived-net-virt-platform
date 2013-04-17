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

package org.sdnplatform.linkdiscovery.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.linkdiscovery.LinkInfo;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.LinkType;
import org.sdnplatform.routing.Link;

public class LinksResource extends ServerResource {

    @Get("json")
    public Set<LinkWithType> retrieve() {
        ILinkDiscoveryService ld = (ILinkDiscoveryService)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());
        Map<Link, LinkInfo> links = new HashMap<Link, LinkInfo>();
        Set<LinkWithType> returnLinkSet = new HashSet<LinkWithType>();

        if (ld != null) {
            links.putAll(ld.getLinks());
            for (Link link: links.keySet()) {
                LinkInfo info = links.get(link);
                LinkType type = ld.getLinkType(link, info);
                LinkWithType lwt = new LinkWithType(link,
                                                    info.getSrcPortState(),
                                                    info.getDstPortState(),
                                                    type);

                if (type == LinkType.DIRECT_LINK || type == LinkType.TUNNEL)
                    returnLinkSet.add(lwt);
            }
        }
        return returnLinkSet;
    }
}
