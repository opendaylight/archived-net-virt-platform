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

import java.util.Iterator;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;

public class JSONSetDpidMsg extends JSONMsg {
    private String setdpidmsg;
    private String dpidstr;
    private OVSDBImpl dsw;
    private int id;
    private String tunnelIP;
    
    public JSONSetDpidMsg(String dpidstr, OVSDBImpl dsw,
            int messageId) throws OVSDBBridgeUnknown {
        this.dpidstr = dpidstr;
        this.dsw = dsw;
        this.id = messageId;
        this.tunnelIP = null;
        buildSetDpidMsgString();
    }
    
    private void buildSetDpidMsgString() throws OVSDBBridgeUnknown {
        String bridgeuuid = getOvsbr0Bridgeuuid();       
        OVSBridge br = dsw.bridge.get(bridgeuuid);
        if (br == null ) {
            throw new RuntimeException("tsw.bridge.get("+ bridgeuuid + ")" +
            " returned Null in setDpid msg");
        }
        setdpidmsg = "{\"method\":\"transact\",\"id\":" + id +
            ",\"params\":[\"Open_vSwitch\", {\"where\":[[\"_uuid\",\"==\"," +
            "[\"uuid\",\""+ bridgeuuid +"\"]]],\"op\":\"update\",\"table\":" +
            "\"Bridge\",\"row\":{\"other_config\":[\"map\"," +
            "[[\"datapath-id\",\"" + dpidstr + "\"],[\"datapath_type\"," +
            "\"system\"]";

        
        if (hasTunnelIp(bridgeuuid)) {
            setdpidmsg += ",[\"tunnel-ip\",\""+tunnelIP+"\"]]]}},";
        } else {
            setdpidmsg += "]]}},";
        }
            
        setdpidmsg += "{\"comment\":\"ovs-vsctl: ovs-vsctl --no-wait set " +
                "bridge ovs-br0 other-config:datapath-id="+ dpidstr +"\"," +
                "\"op\":\"comment\"}]}";
    }

    private String getOvsbr0Bridgeuuid() throws OVSDBBridgeUnknown {
        Iterator<Entry<String, OVSBridge>> iter = dsw.bridge.entrySet()
                                                    .iterator();
        while (iter.hasNext()) {
            Entry<String, OVSBridge> e = iter.next();
            String bruuid = e.getKey();
            OVSBridge br = e.getValue();
            if (br.getNew().getName().equals("ovs-br0")) {
                return bruuid;
            }
        }
        return null;
    }

    private boolean hasTunnelIp(String bridgeuuid) {
        tunnelIP = dsw.bridge.get(bridgeuuid).getNew().getTunnelIPAddress();
        if (tunnelIP == null) return false;
        return true;
    }

    @Override
    public void writeTo(ChannelBuffer buf) {
        if (log.isTraceEnabled()) {
            log.trace("sent set-dpid message (id:{}) to sw@: {} ", 
                    id, dsw.getMgmtIPAddr());
        }
        buf.writeBytes(setdpidmsg.getBytes());
    }
    
    @Override
    public int getLengthU() {
        return setdpidmsg.length();
    }
}
