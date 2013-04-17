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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.forwarding.Forwarding;
import org.sdnplatform.forwarding.IForwardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class to provide visibility to internal in-memory data of various components 
 * for debugging purposes.
 * 
 * URI must be in one of the following forms: " +
 * "http://<controller-hostname>:8080/wm/netVirt/internal-debugs/
 *       forwarding/<query>/json  
 *
 *       where <query> must be one of (no quotes)
 *       all 
 *       switch=<dpid>
 *       switch=all
 *       switch-port=all
 *       switch-port=<dpid>-<port>
 * 
 *  The information can be retrieved using rest API or CLI
 * 
 *
 */
public class InternalDebugsForwardingResource extends InternalDebugsResource {
    
    private static final String COMPONENT_NAME = "Forwarding";

    protected static Logger logger = 
                LoggerFactory.getLogger(InternalDebugsForwardingResource.class);

    // This is the output structure of the JSON return object
    public class InternalDebugsFwdOutput extends InternalDebugsOutput {

        // Broadcast cache hit count for each switch port
        public Map<Long, Map<Short, Long>> broadcacheHitMapDebugs;
        public int numberOfTruncatedPacketsSeenDebugs;
        public boolean broadcastCacheFeatureDebugs;

        public InternalDebugsFwdOutput(String compName) {
            super(compName);
            broadcacheHitMapDebugs = new HashMap<Long, Map<Short, Long>>();
            numberOfTruncatedPacketsSeenDebugs = 0;
            broadcastCacheFeatureDebugs = true;
        }
    }
    
    public static class ForwardFlagParameters {
        private Integer broadcastCache;
        
        public int getBroadcastCache() {
            return this.broadcastCache;
        }
        
        public void setBroadcastCache(Integer broadcastCache) {
            this.broadcastCache = broadcastCache;
        }
        
        @Override
        public String toString() {
            return "BroadcastCache = " + this.broadcastCache;
        }
    }
    /**
     * Handle POST requests: set broadcastCacheFeature flag
     */
    @Post("json")
    public Map<String,Object> flags (ForwardFlagParameters input) throws Exception{
        HashMap<String, Object> model = new HashMap<String,Object>();
        if (input == null) {
            model.put("output", "Invalid output");
            return model;
        }
        
        IForwardingService fwdService = 
            (IForwardingService)getContext().getAttributes().
                get(IForwardingService.class.getCanonicalName());
        fwdService.setBroadcastCache(input.broadcastCache==0 ? false : true);
        
        model.put("output", "OK");
        return model;
    }

    public enum Option {
        ALL, ALL_SWITCHES, ONE_SWITCH, ALL_SW_PORTS, ONE_SW_PORT, 
        ERROR_BAD_DPID, ERROR_BAD_DPIDERROR, ERROR_BAD_SW_PORT, ERROR
      }

    public Option getChoice(String [] params) {

        final Pattern dpidPattern =
            Pattern.compile("([A-Fa-f\\d]{2}:?){7}[A-Fa-f\\d]{2}");
        final Pattern swPortPattern = Pattern.compile(
            "([A-Fa-f\\d]{2}:?){7}[A-Fa-f\\d]{2}-[0-9]+");
        Matcher m;
        Option choice = Option.ERROR;

        if (params.length == 1) {
            if (params[0].equals("all")) {
                choice = Option.ALL;
            } 
        }

        if (params.length == 2) {
            if (params[0].equals("switch")) {
                if (params[1].equals("all")) {
                    choice = Option.ALL_SWITCHES;
                } else {
                    // check for valid dpid
                    m = dpidPattern.matcher(params[1]);
                    if (m.matches()) {
                        choice = Option.ONE_SWITCH;
                    } else {
                        choice = Option.ERROR_BAD_DPID;
                    }
                }
            }

            if (params[0].equals("switch-port")) {
                if (params[1].equals("all")) {
                    choice = Option.ALL_SW_PORTS;
                } else {
                    // check for valid switch-port
                    // expected as, for example (port=45)
                    // switch-port=11:22:33:44:55:66:77:88-45
                    m = swPortPattern.matcher(params[1]);
                    if (m.matches()) {
                        choice = Option.ONE_SW_PORT;
                    } else {
                        choice = Option.ERROR_BAD_SW_PORT;
                    }
                }
            }
        }

        // If params length is > 2 then choice would be Option.ERROR as
        // per initialization of choice

        return choice;
    }

    public void getAllSwitchDebugs(InternalDebugsFwdOutput output,
            Map<Long, IOFSwitch> switches) {
        Set<Long> swSet = null;

        swSet = switches.keySet();
        for (Long swIdx : swSet) {
            IOFSwitch sw = switches.get(swIdx);
            if (sw != null) {
                output.broadcacheHitMapDebugs.put(swIdx, sw.getPortBroadcastHits());
            }
        }
    }
    
    @Get("json")
    public InternalDebugsFwdOutput handleInternalFwdDebugsRequest() {

        final String BAD_PARAM =
            "Incorrect URI: URI must be in one of the following forms: " +
            "http://<controller-hostname>:8080/wm/netVirt/internal-debugs/"  +
            "forwarding/<query>/json  where <query> must be all or " +
            "switch=all or switch=<dpid> or switch-port=<dpid>-<port>";
        final String BAD_DPID ="Malformed switch DPID";
        final String BAD_SW_PORT = "Malformed switch-port";
        final String STATUS_ERROR ="Error"; 

        Long   dpid   = null;
        IOFSwitch sw = null;

        InternalDebugsFwdOutput output = new 
                                InternalDebugsFwdOutput(COMPONENT_NAME);

        // Get the Device dataLayerAddress
        String param = (String)getRequestAttributes().get("param");
        if (param == null) {
            param = "all";
        }

        String [] params = param.split("=");
        Option choice = getChoice(params);

        if (logger.isDebugEnabled()) {
            logger.debug("Received request for Device Mgrs internal debugs"+
                    "Param size={}", params.length);
            for (int idx=0; idx < params.length; idx++) {
                logger.debug("param[{}]={}", idx, params[idx]);
            }
            logger.debug("Choice = {}", choice);
        }

        IControllerService flService = 
            (IControllerService)getContext().getAttributes().
                get(IControllerService.class.getCanonicalName());
        IForwardingService fwdService = 
            (IForwardingService)getContext().getAttributes().
                get(IForwardingService.class.getCanonicalName());

        Map<Long, IOFSwitch> switches = flService.getSwitches();

        boolean found = false;
        
        switch (choice) {
            case ALL:
            case ALL_SWITCHES:
                output.broadcastCacheFeatureDebugs = fwdService.getBroadcastCache();
                output.numberOfTruncatedPacketsSeenDebugs = 
                    ((Forwarding)fwdService).getNumberOfTruncatedPacketsSeen();
                getAllSwitchDebugs(output, switches);
                break;

            case ONE_SWITCH:
                dpid = HexString.toLong(params[1]);
                for (Long swIdx : switches.keySet()) {
                    if (swIdx.equals(dpid)) {
                        // found
                        sw = switches.get(swIdx);
                        found = true;
                        break;
                    }
                }

                if (found) {
                    output.broadcacheHitMapDebugs.put(sw.getId(), sw.getPortBroadcastHits());;
                } else {
                    output.status = STATUS_ERROR;
                    output.reason = "Switch not found";
                }
                break;

            case ONE_SW_PORT:
                String [] swport = params[1].split("-");
                logger.debug("swport {} {}", swport[0], swport[1]);
                Short port = Short.parseShort(swport[1]);

                for (Long swIdx : switches.keySet()) {
                    sw = switches.get(swIdx);
                    if (sw.getStringId().equals(swport[0])) {
                        Map<Short, Long> portBroadcastHits = sw.getPortBroadcastHits();
                        if (portBroadcastHits.containsKey(port)) {
                            Map<Short, Long> onePortHit = new HashMap<Short, Long>();
                            onePortHit.put(port, portBroadcastHits.get(port));
                            output.broadcacheHitMapDebugs.put(swIdx, onePortHit);
                            found = true;
                        } else {
                            output.broadcacheHitMapDebugs.put(swIdx, null);
                            found = false;
                        }
                        break;
                    }
                }

                if (!found) {
                    output.status = STATUS_ERROR;
                    output.reason = "Switch-Port not found";
                }
                break;

            case ERROR_BAD_DPID:
                output.status = STATUS_ERROR;
                output.reason = BAD_DPID;
                break;

            case ERROR_BAD_SW_PORT:
                output.status = STATUS_ERROR;
                output.reason = BAD_SW_PORT;
                break;

            default:
                output.status = STATUS_ERROR;
                output.reason = BAD_PARAM;
        }
        return output;
    }
}
