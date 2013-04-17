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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.tunnelmanager.TunnelManager;
import org.sdnplatform.tunnelmanager.TunnelManager.SwitchTunnelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Return the ovs (vta) tunnel data for the get rest api call
 *
 * URI must be in one of the following forms: " +
 * "http://<controller-hostname>:8080/wm/netVirt/tunnel-manager/<param>/json
 *
 *  where <param> must be one of (no quotes)
 *       all
 *       switch=<dpid>
 *
 *  The information can be retrieved using rest API or CLI
 *
 * @author Saurav Das
 */
public class TunnelManagerResource extends ServerResource {
    protected static Logger logger =
        LoggerFactory.getLogger(TunnelManagerResource.class);


    /**
     * The output JSON model that contains the tunnel information
     */
    public static class TunnelInfoOutput {
        public Map<String, SwitchTunnelInfo> tunnMap;
        public String error;

        TunnelInfoOutput() {
            tunnMap = new HashMap<String, SwitchTunnelInfo>();
            error = null;
        }
        public Map<String, SwitchTunnelInfo> getTunnMap() {
            return tunnMap;
        }

        public String getError() {
            return error;
        }

    }

    public enum Option {
        ALL, ONE_SWITCH, ERROR_BAD_DPID, ERROR_BAD_PARAM
    }

    @Get("json")
    public TunnelInfoOutput handleTunnelInfoQuery() {


        TunnelInfoOutput output = new TunnelInfoOutput();

        ITunnelManagerService tm = (TunnelManager)getContext().getAttributes()
                                    .get(ITunnelManagerService.class.getCanonicalName());

        // Get the parameter
        Option choice = Option.ERROR_BAD_PARAM;
        String swDpidStr = "";
        String param = (String)getRequestAttributes().get("param");
        if (param == null) {
            param = "all";
            choice = Option.ALL;
        } else if (param.startsWith("switch=")) {
            choice = Option.ONE_SWITCH;
            int index = param.indexOf("=");
            if (index < param.length()-1) {
                swDpidStr = param.substring(index+1);
            } else {
                choice = Option.ERROR_BAD_DPID;
            }

        } else if (param.equals("all")) {
            choice = Option.ALL;
        } else {
            choice = Option.ERROR_BAD_PARAM;
        }

        switch (choice) {
            case ALL:
                populateAllSwitchTunnels(tm.getAllTunnels(), output);
                break;
            case ONE_SWITCH:
                long swDpid = -1;
                try {
                    swDpid = HexString.toLong(swDpidStr);
                } catch (NumberFormatException e) {
                    output.error = "switch dpid does not evaluate to a valid" +
                        " long";
                    return output;
                }
                populateSwitchTunnel(tm.getTunnelsOnSwitch(swDpid), output);
                break;
            case ERROR_BAD_DPID:
                output.error = "Incorrect dpid";
                break;
            case ERROR_BAD_PARAM:
                output.error = "param should be (without quotes) either" +
                        " \'all\' or a valid switch dpid expressed as " +
                        "\'switch=xx:xx:xx:xx:xx:xx:xx:xx\' where xx is in hex";
        }
        return output;
    }


    private void populateAllSwitchTunnels(
            ArrayList<SwitchTunnelInfo> allTunnels, TunnelInfoOutput output) {
        for (SwitchTunnelInfo sti : allTunnels) {
            populateSwitchTunnel(sti, output);
        }
    }


    private void populateSwitchTunnel(SwitchTunnelInfo sti, TunnelInfoOutput
            output) {
        if (sti != null) {
            output.tunnMap.put(sti.hexDpid, sti);
        } else {
            output.error = "switch dpid not found";
        }

    }


}
