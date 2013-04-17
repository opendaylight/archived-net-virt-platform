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

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.topology.IBetterTopologyService;
import org.sdnplatform.topology.TunnelEvent;


/**
 * Retrieve the set of tunnels that failed over the last time interval.
 * The default time interval in topology manager is 60 seconds.
 *
 * @author srini
 */
public class TunnelStatusResource extends ServerResource {

    @Get("json")
    public List<TunnelEvent> retrieve() {
        List<TunnelEvent> eventList = new ArrayList<TunnelEvent>();
        List<TunnelEvent> tunnelStatus;
        IBetterTopologyService topology =
                (IBetterTopologyService)getContext().getAttributes().
                    get(IBetterTopologyService.class.getCanonicalName());

        String srcDpid = (String) getRequestAttributes().get("src-dpid");
        String dstDpid = (String) getRequestAttributes().get("dst-dpid");

        if (srcDpid.equals("all") || dstDpid.equals("all")) {
            tunnelStatus = topology.getTunnelLivenessState();
            if (tunnelStatus != null)
                eventList.addAll(tunnelStatus);
        } else {
            long src = HexString.toLong(srcDpid);
            long dst = HexString.toLong(dstDpid);
            tunnelStatus = topology.getTunnelLivenessState(src, dst);
            if (tunnelStatus != null)
                eventList.addAll(tunnelStatus);
        }
        return eventList;
    }
}
