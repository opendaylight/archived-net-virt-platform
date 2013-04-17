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

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.routing.Link;
import org.sdnplatform.topology.NodePortTuple;

/**
 * Class to provide visibility to internal in-memory data of various components 
 * for debugging purposes.
 * 
 * URI must be in one of the following forms: " +
 * "http://<controller-hostname>:8080/wm/netVirt/internal-debugs/
 *       device-manager/<query>/json  
 *
 *       where <query> must be one of (no quotes)
 *       all 
 *       device=<macaddr>
 *       device=all
 *       switch=<dpid>
 *       switch=all
 *       network-address=all
 *       network-address=<ip address as x..y.z.w>
 *       switch-port=all
 *       switch-port=<dpid>-<port>
 * 
 *  The information can be retrieved using rest API or CLI
 * 
 * @author subrata
 *
 */
public class InternalDebugsResource extends ServerResource {

    final String BAD_MAC      = "Malformed MAC address";
    final String BAD_DPID     = "Malformed switch DPID";
    final String BAD_NW_ADDR  = "Malformed network address";
    final String BAD_SW_PORT  = "Malformed switch-port";
    final String STATUS_ERROR = "Error"; 


    public String dateToStr(Date date) {
        if (date == null) {
            return null;
        } else {
            return date.toString();
        }
    }

    /**
     * Used for printing contents of a Switch object for debugging in a format
     * that can be handled by auto-converter of java object to json object 
     * for example Jackson converter
     * 
     * If any new member is added to the IOFSwitch class then that needs to
     * be added to the SwitchDebugs class below for visibility in the debug
     * outputs
     * 
     * (Jackson couldn't convert IOFSwitch class, hence had to create a
     * Debug version of it
     * 
     * @author subrata
     *
     */

    public class SwitchDebugs{
        protected Map<Object, Object> attributes;
        protected String connectedSince;
        protected OFFeaturesReply featuresReply;
        protected String stringId;
        protected String channel;
        protected Collection<OFPhysicalPort> ports;
        // Broadcast cache hit by port
        protected Map<Short, Long> portBroadcastCacheHits;
        protected boolean connected;
        protected int actions;
        protected int buffers;
        protected int capabilities;
        protected byte tables;

        public Map<Object, Object> getAttributes() {
            return attributes;
        }
        public String getConnectedSince() {
            return connectedSince;
        }
        public OFFeaturesReply getFeaturesReply() {
            return featuresReply;
        }
        public String getStringId() {
            return stringId;
        }
        public String getChannel() {
            return channel;
        }
        public Collection<OFPhysicalPort> getPorts() {
            return ports;
        }
        public Map<Short, Long> getPortBroadcastHits() {
            return portBroadcastCacheHits;
        }
        public boolean isConnected() {
            return connected;
        }

        public SwitchDebugs(IOFSwitch sw) {
            attributes       = sw.getAttributes();
            connectedSince   = dateToStr(sw.getConnectedSince());
            stringId         = sw.getStringId();
            channel          = sw.getInetAddress().toString();
            ports            = sw.getPorts();
            portBroadcastCacheHits = sw.getPortBroadcastHits();
            connected        = sw.isConnected();
            actions          = sw.getActions();
            buffers          = sw.getBuffers();
            capabilities     = sw.getCapabilities();
            tables           = sw.getTables();
        }
    }

    /**
     * Used for printing contents of a Switch-Port object for debugging in a 
     * format that can be handled by auto-converter of java object to json 
     * object for example Jackson converter
     * 
     * @author subrata
     */

    public class SwitchPortTupleDebugs {
        public SwitchDebugs swD;
        Integer port;

        public SwitchDebugs getSwD() {
            return swD;
        }
        public Integer getPort() {
            return port;
        }

        public SwitchPortTupleDebugs(NodePortTuple npt) {
            IControllerService controllerProvider = 
                    (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());
            IOFSwitch sw = controllerProvider.getSwitches().get(npt.getNodeId());
            swD = new SwitchDebugs(sw);
            port = (int)(npt.getPortId());
        }
    }

    public class SwitchClusterDebugs {
        protected Long ClusterId;
        protected Set<SwitchDebugs> SwitchSetDebugs;

        // Getters
        public Long getClusterId() {
            return ClusterId;
        }

        public Set<SwitchDebugs> getSwitchSetDebugs() {
            return SwitchSetDebugs;
        }

        /*  Commented out by Srini.

        // Constructor
        public SwitchClusterDebugs(SwitchCluster swCluster) {
            ClusterId = swCluster.getId();
            SwitchDebugs swDebugs;
            SwitchSetDebugs = new HashSet<SwitchDebugs>();
            for (IOFSwitch sw : swCluster.getSwitches()) {
                swDebugs = new SwitchDebugs(sw);
                SwitchSetDebugs.add(swDebugs);
            }
        }
        */
    }

    public class LinkTupleDebugs {
        protected SwitchPortTupleDebugs srcSwitchPortDebugs;
        protected SwitchPortTupleDebugs dstSwitchPortDebugs;

        public SwitchPortTupleDebugs getSrcSwitchPortDebugs() {
            return srcSwitchPortDebugs;
        }

        public SwitchPortTupleDebugs getDstSwitchPortDebugs() {
            return dstSwitchPortDebugs;
        }

        public LinkTupleDebugs(Link link) {
            NodePortTuple srcNpt = new NodePortTuple(link.getSrc(), link.getSrcPort());
            NodePortTuple dstNpt = new NodePortTuple(link.getDst(), link.getDstPort());
            srcSwitchPortDebugs = new SwitchPortTupleDebugs(srcNpt);
            dstSwitchPortDebugs = new SwitchPortTupleDebugs(dstNpt);
        }
    }

    public class LinkTupleSetDebugs {
        protected Set<LinkTupleDebugs> linkTupleSetDebugs;

        public LinkTupleSetDebugs(Set<Link> linkTupleSet) {
            LinkTupleDebugs ltDebugs;
            linkTupleSetDebugs = new HashSet<LinkTupleDebugs>();
            if (linkTupleSet == null) return;
            for (Link lt : linkTupleSet) {
                ltDebugs = new LinkTupleDebugs(lt);
                linkTupleSetDebugs.add(ltDebugs);
            }
        }

        public Set<LinkTupleDebugs> getSwTupleSetDebugs() {
            return linkTupleSetDebugs;
        }
    }

    // This is the output structure of the JSON return object    
    public class InternalDebugsOutput {

        public String status; // OK or ERROR
        public String reason; // reason for ERROR if any
        public String date;
        public String componentName;

        public String getComponentName() {
            return componentName;
        }

        public String getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public String getDate() {
            return date;
        }

        public InternalDebugsOutput(String compName) {
            status = "OK";
            reason = "None";
            date   = new Date().toString();
            componentName = compName;
        }
    }

    public enum Option {
        ALL, ALL_DEVICES, ALL_SWITCHES, ONE_DEVICE, ONE_SWITCH, 
        ALL_NW_ADDRS, ONE_NW_ADDR, ALL_SW_PORTS, ONE_SW_PORT, 
        ERROR_BAD_DPID, ERROR_BAD_MAC, ERROR, ERROR_BAD_NW_ADDR,
        ERROR_BAD_SW_PORT
    }
}
