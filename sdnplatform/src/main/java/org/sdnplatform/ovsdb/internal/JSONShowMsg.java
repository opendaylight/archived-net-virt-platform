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

/**
 * 
 */
package org.sdnplatform.ovsdb.internal;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * show-message RPC to request database state from ovsdb-server
 * @author Saurav Das
 *
 */
public class JSONShowMsg extends JSONMsg{
    String showstr;
    
    public JSONShowMsg(int id) {
        showstr = " {\"method\":\"monitor\",\"id\":"+id+",\"params\":[ "+
        " \"Open_vSwitch\", "+
        " null, "+
        " {\"Port\":{\"columns\":[\"interfaces\",\"name\",\"tag\",\"trunks\"]},"
        +
        " \"Controller\":{\"columns\":[\"is_connected\",\"target\"]}, "+
        " \"Interface\":{\"columns\":[\"name\",\"options\",\"type\"]}, "+
        " \"Open_vSwitch\":{\"columns\":[\"bridges\",\"cur_cfg\"," +
        "\"manager_options\",\"ovs_version\"]}, "+
        " \"Manager\":{\"columns\":[\"is_connected\",\"target\"]}, "+ 
        " \"Bridge\":{\"columns\":[\"controller\",\"fail_mode\",\"name\"," +
        "\"ports\",\"datapath_id\",\"other_config\"]}} "+
        " ] "+
        " } ";
        
    }

    @Override
    public int getLengthU() {
        return showstr.length();
    }

    @Override
    public void writeTo(ChannelBuffer buf) {
        if (log.isTraceEnabled()) {
            log.trace("sent show message");
        }
        buf.writeBytes(showstr.getBytes());
    }

}
