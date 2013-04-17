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
import java.util.Iterator;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.HexString;


/**
 * JSONDelPortMsg builds the JSON RPC message to request the deletion
 * of a port 
 * 
 * @author Saurav Das
 *
 */
public class JSONDelPortMsg extends JSONMsg {
    private String name;
    private String portHash;
    private OVSDBImpl tsw;
    private int id;
    private String completeString;
    private String dbuuid = "";

    /**
     * Constructor
     * @param name          name of tunnel-port to delete
     * @param portHash      hash-value of tunnel-port used by ovsdb
     * @param tsw           the tunnel-switch on which to delete port
     * @param messageId     message-id for this RPC message
     * @throws OVSDBBridgeUnknown 
     */
    public JSONDelPortMsg(String name, String portHash,
            OVSDBImpl tsw, int messageId) throws OVSDBBridgeUnknown {
        this.name = name;
        this.portHash = portHash;
        this.tsw = tsw;
        this.id = messageId;
        buildDelPortMsg();
    }

    private void buildDelPortMsg() throws OVSDBBridgeUnknown {
        completeString = "{\"method\":\"transact\",\"id\":"+id+",\"params\":["+
        " \"Open_vSwitch\", ";
        completeString += rowsUntil() +  update() + mutations() + comment();
    }

    private String rowsUntil() {
        String rows = "";
        
        //first the interfaces
        String start = "{\"rows\":[{\"interfaces\":[\"uuid\",\"";
        String mid = "\"]}],\"until\":\"==\",\"where\":[[\"_uuid\",\"==\"" +
                ",[\"uuid\",\"";
        String end = "\"]]],\"timeout\":0,\"op\":\"wait\",\"table\":\"Port\"" +
                ",\"columns\":[\"interfaces\"]},";
        Iterator<Entry<String, OVSPort>> iter = tsw.port.entrySet().iterator();
        while(iter.hasNext()) {
            Entry<String, OVSPort> e = iter.next();
            String portuuid = e.getKey(); 
            String intfuuid = e.getValue().getNew().getInterfaces().get(1); 
            rows += start + intfuuid + mid + portuuid + end; 
        }
        
        // then the bridges -- assumes there is a single db called Open_vSwitch
        int numbridges = getNumBridges();
        if (numbridges > 1) {
            start = "{\"rows\":[{\"bridges\":[\"set\",[[\"uuid\",\""; 
            String mid2 = "\"],[\"uuid\",\"";
            String mid3 = "\"]]]}],\"until\":\"==\",\"where\":[[\"_uuid\"," +
                    "\"==\",[\"uuid\",\"";
            end = "\"]]],\"timeout\":0,\"op\":\"wait\",\"table\":\"Open_" +
                    "vSwitch\"" + ",\"columns\":[\"bridges\"]},";
            Iterator<Entry<String, OVSDatabase>> iter1 = 
                tsw.open_vswitch.entrySet().iterator();
            rows += start;
            Iterator<String> iter2 = tsw.bridge.keySet().iterator();
            while (iter2.hasNext()) {
                String bridgeuuid = iter2.next(); 
                rows += bridgeuuid;
                if (iter2.hasNext())    
                    rows += mid2;
                else 
                    rows += mid3;
            }
            while (iter1.hasNext()) {
                Entry<String, OVSDatabase> e1 = iter1.next();
                dbuuid = e1.getKey(); 
                break; // only one database
            }
            rows += dbuuid + end;
                
        } else {
            start = "{\"rows\":[{\"bridges\":[\"uuid\",\"";
            end = "\"]]],\"timeout\":0,\"op\":\"wait\",\"table\":\"Open_" +
                    "vSwitch\"" + ",\"columns\":[\"bridges\"]},";
            Iterator<Entry<String, OVSDatabase>> iter1 = 
                tsw.open_vswitch.entrySet().iterator();
            while(iter1.hasNext()) {
                Entry<String, OVSDatabase> e1 = iter1.next();
                dbuuid = e1.getKey();
                String bridgeuuid = (String) e1.getValue().getNew()
                                        .getBridges().get(1); 
                rows += start + bridgeuuid + mid + dbuuid + end;
                break; // just one database
            }    
        }
        
      //finally the ports per bridge - a separate entry per bridge
        Iterator<Entry<String, OVSBridge>> iter5 = tsw.bridge.entrySet()
                                                        .iterator();
        while (iter5.hasNext()) {
            Entry<String, OVSBridge> e = iter5.next();
            OVSBridge br = e.getValue();
            String bridgeuuid = e.getKey();
            start = "{\"rows\":[{\"ports\":[\"set\",[";
            rows += start;
            String porthdr = "[\"uuid\",\"";
            String porttlr = "\"]";
            ArrayList<String> brportuuids = br.getNew().getPortUuids();
            for (int i=0; i< brportuuids.size(); i++) {
                rows += porthdr + brportuuids.get(i) + porttlr;
                if (i+1 < brportuuids.size()) rows += ",";
            }
            end = "\"]]],\"timeout\":0,\"op\":\"wait\",\"table\":\"Bridge\"" +
            ",\"columns\":[\"ports\"]},";
            rows += "]" + mid.substring(1) + bridgeuuid + end;
        }
           
        return rows; 
    }

    private int getNumBridges() {
        // only a single database per ovs is expected
        for( OVSDatabase odb : tsw.open_vswitch.values()) {
            ArrayList<Object> brs = odb.getNew().getBridges();
            return brs.size();
        }
        return 0;
    }

    private String update() throws OVSDBBridgeUnknown {
        String bridgeuuid = getBridgeuuid();
        String start1 = "{\"where\":[[\"_uuid\",\"==\",[\"uuid\",\"" +
        bridgeuuid + "\"]]],\"op\":\"update\",\"table\":\"Bridge\"," +
        "\"row\":{\"ports\":[\"set\",[";
        String ports = "";
        String porthdr = "[\"uuid\",\"";
        String porttlr = "\"]";
        OVSBridge br = tsw.bridge.get(bridgeuuid);
        if ( br == null ) {
            throw new RuntimeException("tsw.bridge.get("+ bridgeuuid +
                    ") returned Null");
        }
        ArrayList<String> pl = br.getNew().getPortUuids(); //for this bridge
        for (int i=0; i< pl.size(); i++) {
            String portkey = pl.get(i);
            if (portkey.equals(portHash)) {
                if (i+1 >= pl.size()) { 
                    ports = ports.substring(0, ports.length()-1);
                }
                continue;
            }
            ports += porthdr + portkey + porttlr;
            if (i+1 < pl.size()) ports += ",";
        }
        return start1 + ports + "]]}},";
    }
    
    private String getBridgeuuid() throws OVSDBBridgeUnknown {
        long dpid = tsw.getDpid();
        Iterator<Entry<String, OVSBridge>> iter = tsw.bridge.entrySet().
                                                        iterator();
        while (iter.hasNext()) {
            Entry<String, OVSBridge> e = iter.next();
            String bruuid = e.getKey();
            OVSBridge br = e.getValue();
            if (br.getNew().getReportedDpid() == dpid) {
                return bruuid;
            }
        }
        throw new OVSDBBridgeUnknown(tsw.getDpid());
    }
    
    private String mutations() {
        return "{\"mutations\":[[\"next_cfg\",\"+=\",1]]," +
        "\"where\":[[\"_uuid\",\"==\",[\"uuid\",\"" + dbuuid + "\"]]]," +
        "\"op\":\"mutate\",\"table\":\"Open_vSwitch\"}," +
        "{\"where\":[[\"_uuid\",\"==\",[\"uuid\",\"" + dbuuid + "\"]]]," +
        "\"op\":\"select\",\"table\":\"Open_vSwitch\"," +
        "\"columns\":[\"next_cfg\"]},";
    }
    
    private String comment() {
        return "{\"comment\":\"ovs-vsctl: ./ovs-vsctl --db=tcp:" +
        tsw.getMgmtIPAddr() + ":6635 del-port tunnelswitch " + name +
        "\",\"op\":\"comment\"}]}";
    }
    
    @Override
    public void writeTo(ChannelBuffer buf) {
        if (log.isTraceEnabled()) {
            log.trace("sent del-port message to:" + name + " msg-id:"+ id +
                    " @sw: {} ", HexString.toHexString(tsw.getDpid()));
        }
        buf.writeBytes(completeString.getBytes());
    }
    
    @Override
    public int getLengthU() {
        return completeString.length();
    }

    

}
