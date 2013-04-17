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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.linkdiscovery.LinkInfo;
import org.sdnplatform.linkdiscovery.internal.LinkDiscoveryManager;
import org.sdnplatform.routing.Link;
import org.sdnplatform.topology.NodePortTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to provide visibility to internal in-memory data of various components 
 * for debugging purposes.
 * 
 * URI must be in one of the following forms: " +
 * "http://<controller-hostname>:8080/wm/netVirt/internal-debugs/
 *       topology-manager/<query>/json 
 * or
 * 
 * "http://<controller-hostname>:8000/rest/v1/internal-debugs/
 *       topology-manager/<query>
 *
 *       where <query> must be one of (no quotes)
 *       all
 * 
 *  The information can be retrieved using rest API or CLI
 * 
 * @author subrata
 *
 */
public class InternalDebugsTopoMgrResource extends InternalDebugsResource {
    private static final String COMPONENT_NAME = "Topology-Manager";
    protected static Logger logger = 
                LoggerFactory.getLogger(InternalDebugsTopoMgrResource.class);

    // This is the output structure of the JSON return object
    public class InternalDebugsTopoMgrOutput extends InternalDebugsOutput {
        // Component specific members
        public Map<String, SwitchDebugs> controllerProviderSwitches;
        public Map<String, LinkInfo> links;
        public int lldpFrequency;
        public int lldpTimeout;
        public Map<String, LinkTupleSetDebugs> portLinks;
        public boolean shuttingDown;
        public Map<String, LinkTupleSetDebugs> switchLinks;
        public Map<String, SwitchClusterDebugs> switchClusterMap;

        public Map<String, SwitchDebugs> getControllerProviderSwitches() {
            return controllerProviderSwitches;
        }

        public Set<SwitchClusterDebugs> clusters;

        public Map<String, LinkInfo> getLinks() {
            return links;
        }

        public int getLldpFrequency() {
            return lldpFrequency;
        }

        public int getLldpTimeout() {
            return lldpTimeout;
        }

        public Map<String, LinkTupleSetDebugs> getPortLinks() {
            return portLinks;
        }

        public boolean isShuttingDown() {
            return shuttingDown;
        }

        public Map<String, LinkTupleSetDebugs> getSwitchLinks() {
            return switchLinks;
        }

        public Map<String, SwitchClusterDebugs> getSwitchClusterMap() {
            return switchClusterMap;
        }

        public Set<SwitchClusterDebugs> getClusters() {
            return clusters;
        }

        public InternalDebugsTopoMgrOutput(String compName) {
            super(compName);
            controllerProviderSwitches = new HashMap<String, SwitchDebugs>();
            links            = new HashMap<String, LinkInfo>();
            portLinks        = new HashMap<String, LinkTupleSetDebugs>();
            switchLinks      = new HashMap<String, LinkTupleSetDebugs>();
            switchClusterMap = new HashMap<String, SwitchClusterDebugs>();
            clusters         = new HashSet<SwitchClusterDebugs>();
        }
    }

    public Option getChoice(String [] params) {

        Option choice = Option.ERROR;

        if (params.length == 1) {
            if (params[0].equals("all")) {
                choice = Option.ALL;
            } 
        }
        return choice;
    }

    public void populateControllerProviderSwitches(
                                    InternalDebugsTopoMgrOutput output) {
        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());      
        Map<Long, IOFSwitch> bpSwitches = controllerProvider.getSwitches(); 
        SwitchDebugs swD;
        for (Long dpid : bpSwitches.keySet()) {
            swD = new SwitchDebugs(bpSwitches.get(dpid));
            output.controllerProviderSwitches.put(
                                            HexString.toHexString(dpid), swD);
        }
    }

    private void populateLinksDebugs(LinkDiscoveryManager topology, 
                                        InternalDebugsTopoMgrOutput output) {
        Map<Link, LinkInfo> links = topology.getLinks();
        if (links == null) {
            return;
        }
        String ltDebugs;
        for (Link lt : links.keySet()) {
            ltDebugs = lt.toString();
            output.links.put(ltDebugs, links.get(lt));
        }
    }

    private void populatePortLinksDebugs(LinkDiscoveryManager topology, 
                                        InternalDebugsTopoMgrOutput output) {
        Map<NodePortTuple, Set<Link>> pl = topology.getPortLinks();
        if (pl == null) {
            return;
        }
        String swPortDebugs;
        LinkTupleSetDebugs linkTupleSetDebugs;
        for (NodePortTuple swPort : pl.keySet()) {
            swPortDebugs = swPort.toString();
            linkTupleSetDebugs = new LinkTupleSetDebugs(pl.get(swPort));
            output.portLinks.put(swPortDebugs, linkTupleSetDebugs);
        }
    }

    private void populateSwitchLinksDebugs(LinkDiscoveryManager topology, 
                                        InternalDebugsTopoMgrOutput output) {

        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());

        Map<Long, Set<Link>> sl = topology.getSwitchLinks();
        if (sl == null) {
            return;
        }
        String swDebugs;
        LinkTupleSetDebugs ltSetDebugs;
        for (long swId: sl.keySet()) {
            IOFSwitch sw = controllerProvider.getSwitches().get(swId);
            swDebugs = sw.toString();
            ltSetDebugs = new LinkTupleSetDebugs(sl.get(swId));
        output.switchLinks.put(swDebugs, ltSetDebugs);
        }
    }

    /*
    private void populateSwitchClusterMapDebugs(TopologyImpl topology, 
                                        InternalDebugsTopoMgrOutput output) {
        Map<Long, SwitchCluster> scm = topology.getSwitchClusterMap();
        if (scm == null) {
            return;
        }
        String        swDebugs;
        SwitchClusterDebugs scDebugs;
        SwitchCluster       sc;
        for (Long sw : scm.keySet()) {
            swDebugs = sw.toString();
            sc = scm.get(sw);
            scDebugs = new SwitchClusterDebugs(sc);
            output.switchClusterMap.put(swDebugs, scDebugs);
        }
    } */
    
    /*
    private void populateClusters(TopologyImpl topology, 
                                        InternalDebugsTopoMgrOutput output) {
        Set<SwitchCluster> swClusters = topology.getClusters();
        SwitchClusterDebugs scDebugs;
        for (SwitchCluster sc : swClusters) {
            scDebugs = new SwitchClusterDebugs(sc);
            output.clusters.add(scDebugs);
        }
    } */

    @Get("json")
    public InternalDebugsTopoMgrOutput handleInternalBTopoMgrDebugsRequest() {

        final String BAD_PARAM =
            "Incorrect URI: URI must be in one of the following forms: " +
            "http://<controller-hostname>:8080/wm/netVirt/internal-debugs/"  +
            "topology-manager/<query>/json  where <query> must be all";

        InternalDebugsTopoMgrOutput output = 
                    new InternalDebugsTopoMgrOutput(COMPONENT_NAME);

        // Get the parameter
        String param = (String)getRequestAttributes().get("param");
        if (param == null) {
            param = "all";
        }

        String [] params = param.split("=");
        Option choice = getChoice(params);

        if (logger.isDebugEnabled()) {
            logger.debug("Received request for Topology Mgrs internal debugs"+
                    "Param size: {}", params.length);
            for (int idx=0; idx < params.length; idx++) {
                logger.debug("param[{}]={}", idx, params[idx]);
            }
            logger.debug("Topology Debug Request type: {}", choice);
        }

        LinkDiscoveryManager topology = 
            (LinkDiscoveryManager)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());

        switch (choice) {
            case ALL:
                populateControllerProviderSwitches(output);
                populateLinksDebugs(topology, output);
                output.lldpFrequency    = topology.getLldpFrequency();
                output.lldpTimeout      = topology.getLldpTimeout();
                populatePortLinksDebugs(topology, output);
                output.shuttingDown     = topology.isShuttingDown();
                populateSwitchLinksDebugs(topology, output);
                //populateSwitchClusterMapDebugs(topology, output); 
                //populateClusters(topology, output); 
                break;

            default:
                output.status = STATUS_ERROR;
                output.reason = BAD_PARAM;
        }
        return output;
    }
}
