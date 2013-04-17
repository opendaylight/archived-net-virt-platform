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

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * OVSController maps the ovsdb "Controller" table
 * Used by Jackson to decode JSON to POJO
 * 
 * @author Saurav Das
 *
 */
public class OVSController {

    private ControllerMap newmap;
    private ControllerMap oldmap;

    public ControllerMap getNew() {
        return newmap;
    }

    public void setNew(ControllerMap newmap) {
        this.newmap = newmap;
    }
    
    public ControllerMap getOld() {
        return oldmap;
    }

    public void setOld(ControllerMap oldmap) {
        this.oldmap = oldmap;
    }

    @Override
    public String toString() {
        return newmap.toString();
    }
    
    public static class ControllerMap {
        private String target;
        private boolean isConnected;
        
        public String getTarget() {
            return target;
        }
        
        public void setTarget(String target) {
            this.target = target;
        }
        
        @JsonProperty("is_connected")
        public boolean isConnected() {
            return isConnected;
        }
        
        @JsonProperty("is_connected")
        public void setConnected(boolean isConnected) {
            this.isConnected = isConnected;
        }
        
        public String toString() {
            return getTarget()+" "+"isConnected:"+isConnected();
        }
        
    }

    
}
