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
 * OVSDatabase maps the ovsdb table "Open vSwitch"
 * Used by Jackson to decode JSON to POJO
 * 
 * @author Saurav Das
 *
 */
public class OVSDatabase {

    private DbMap newmap;
    private DbMap oldmap;
    
    public DbMap getNew() {
        return newmap;
    }
    public void setNew(DbMap newmap) {
        this.newmap = newmap;
    }
    public DbMap getOld() {
        return oldmap;
    }
    public void setOld(DbMap oldmap) {
        this.oldmap = oldmap;
    }
    
    @Override
    public String toString() {
        return newmap.toString();
    }
    
    public static class DbMap {
        //ovs_version maps to an array ["set",[]]
        private Object ovs_version;
        
        private int cur_cfg;
        
        // bridges maps to array ["uuid","xxx"] if there is a single bridge
        // but maps to array ["set", [["uuid","xxx"], ["uuid","xyz"]]] if there
        // are multiple bridges
        private ArrayList<Object> bridges;
        
        //manager_options maps to an array ["set",[]]
        private Object manager_options;

        public Object getOvs_version() {
            return ovs_version;
        }

        public void setOvs_version(Object ovs_version) {
            this.ovs_version = ovs_version;
        }

        public int getCur_cfg() {
            return cur_cfg;
        }

        public void setCur_cfg(int cur_cfg) {
            this.cur_cfg = cur_cfg;
        }

        public ArrayList<Object> getBridges() {
            return bridges;
        }

        public void setBridges(ArrayList<Object> bridges) {
            this.bridges = bridges;
        }

        public Object getManager_options() {
            return manager_options;
        }

        public void setManager_options(Object manager_options) {
            this.manager_options = manager_options;
        }
        
        @Override
        public String toString() {
            return "cur_cfg:"+cur_cfg+" "+bridges.toString();
        }

    }
}
