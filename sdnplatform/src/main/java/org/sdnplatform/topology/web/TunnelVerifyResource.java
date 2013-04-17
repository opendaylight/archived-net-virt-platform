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

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.topology.IBetterTopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * REST API for verifying liveness of a tunnel.
 * @author srini
 */
public class TunnelVerifyResource extends ServerResource {

    protected static Logger log =
            LoggerFactory.getLogger(TunnelVerifyResource.class);

    @Get("json")
    public void retrieve() {
        IBetterTopologyService topology =
                (IBetterTopologyService)getContext().getAttributes().
                get(IBetterTopologyService.class.getCanonicalName());

        String srcDpid = (String) getRequestAttributes().get("src-dpid");
        String dstDpid = (String) getRequestAttributes().get("dst-dpid");
        log.debug( "Verifying Tunnel: SrcDpid: "+ srcDpid + " DstDpid: " + dstDpid);

        long longSrcDpid = HexString.toLong(srcDpid);
        long longDstDpid = HexString.toLong(dstDpid);

        topology.verifyTunnelOnDemand(longSrcDpid, longDstDpid);
    }
}
