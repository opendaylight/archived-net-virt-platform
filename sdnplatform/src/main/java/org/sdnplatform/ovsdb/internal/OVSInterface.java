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

import java.util.ArrayList;

/**
 * OVSInterface maps the ovsdb table "Interface"
 * Used by Jackson to map JSON to POJO
 *  
 * @author Saurav Das
 *
 */
public class OVSInterface {

    private InterfaceMap newmap;
    private InterfaceMap oldmap;
    
    public InterfaceMap getNew() {
        return newmap;
    }
    public void setNew(InterfaceMap newmap) {
        this.newmap = newmap;
    }
    public InterfaceMap getOld() {
        return oldmap;
    }
    public void setOld(InterfaceMap oldmap) {
        this.oldmap = oldmap;
    }
    
    @Override
    public String toString() {
        return newmap.toString();
    }
    
    public static class InterfaceMap {
        private String name;
        private String type;
        //options = ["map",[["remote_ip","10.20.30.40"]]]
        private ArrayList<Object> options;
        
        
        public String getName() {
            return name;
        }


        public void setName(String name) {
            this.name = name;
        }


        public String getType() {
            return type;
        }


        public void setType(String type) {
            this.type = type;
        }


        public ArrayList<Object> getOptions() {
            return options;
        }


        public void setOptions(ArrayList<Object> options) {
            this.options = options;
        }

        @Override
        public String toString() {
            return name+" "+type+" "+options.toString();
        }

        public String getRemoteIP() {
            if(options.toString().contains("remote_ip")) {
                int index = options.toString().indexOf("remote_ip");
                String left = options.toString().substring(index+11);
                return left.substring(0, left.indexOf("]"));
            } else {
                return null;
            }
        }
        
        @SuppressWarnings("unchecked")
        public ArrayList<String> getRemoteIPs() {
            ArrayList<String> iplist = new ArrayList<String>();
            ArrayList<Object> o = (ArrayList<Object>) options.get(1);
            for(int i =0; i<o.size(); i++) {
                ArrayList<String> str = (ArrayList<String>)o.get(i);
                iplist.add(str.get(1)); // 0 is "remote_ip"
            }
            return iplist;
        }
        
        
         
    }
    
}
