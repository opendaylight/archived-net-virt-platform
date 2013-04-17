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

package org.sdnplatform.ovsdb.internal;

import java.util.HashMap;

import org.codehaus.jackson.annotate.JsonProperty;


/**
 * Class for JSON RPC show reply message  
 * used by Jackson to decode JSON to POJO
 * 
 * @author Saurav Das
 *
 */
public class JSONShowReplyMsg {
    //show reply message has "id", "error" and "result" 
    private int id;
    private Object error;
    private ShowResult result;
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public Object getError() {
        return error;
    }
    public void setError(Object error) {
        this.error = error;
    }
    public ShowResult getResult() {
        return result;
    }
    public void setResult(ShowResult result) {
        this.result = result;
    }
    
    public static class ShowResult {
        private HashMap<String, OVSPort> port;
        private HashMap<String, OVSController> controller;
        private HashMap<String, OVSInterface> intf;
        private HashMap<String, OVSDatabase> open_vswitch;
        private HashMap<String, OVSBridge> bridge;
      
        @JsonProperty("Port")
        public HashMap<String,OVSPort> getPort() {
            return port;
        }
        @JsonProperty("Port")
        public void setPort(HashMap<String,OVSPort> port) {
            this.port = port;
        }
        @JsonProperty("Controller")
        public HashMap<String, OVSController> getController() {
            return controller;
        }
        @JsonProperty("Controller")
        public void setController(HashMap<String, OVSController> controller) {
            this.controller = controller;
        }
        @JsonProperty("Interface")
        public HashMap<String, OVSInterface> getInterface() {
            return intf;
        }
        @JsonProperty("Interface")
        public void setInterface(HashMap<String, OVSInterface> intf) {
            this.intf = intf;
        }
        @JsonProperty("Open_vSwitch")
        public HashMap<String, OVSDatabase> getOpen_vSwitch() {
            return open_vswitch;
        }
        @JsonProperty("Open_vSwitch")
        public void setOpen_vSwitch(HashMap<String, OVSDatabase> open_vswitch) {
            this.open_vswitch = open_vswitch;
        }
        @JsonProperty("Bridge")
        public HashMap<String, OVSBridge> getBridge() {
            return bridge;
        }
        @JsonProperty("Bridge")
        public void setBridge(HashMap<String, OVSBridge> bridge) {
            this.bridge = bridge;
        }
        
        // to deal with error messages
        private String syntax;
        private String error;
        private String details;

        public String getSyntax() {
            return syntax;
        }
        public void setSyntax(String syntax) {
            this.syntax = syntax;
        }
        public String getError() {
            return error;
        }
        public void setError(String error) {
            this.error = error;
        }
        public String getDetails() {
            return details;
        }
        public void setDetails(String details) {
            this.details = details;
        }
        
    }
    
        
        
    
    
}
