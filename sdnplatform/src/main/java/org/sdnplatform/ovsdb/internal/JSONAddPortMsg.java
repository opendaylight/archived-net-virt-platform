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
import java.util.Random;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.HexString;


/**
 * JSONAddPortMsg builds the JSON RPC message to request the addition
 * of a port
 * 
 * @author Saurav Das
 *
 */
public class JSONAddPortMsg extends JSONMsg {
    private String localIPAddr;
    private String remoteIPAddr;
    private String name;
    private String completeString;
    private OVSDBImpl tsw;
    private int id;

    private String dbuuid = "";
    private boolean capwap;
    
    /**
     * Constructor
     * @param name          name of tunnel-port to add
     * @param remoteIPAddr  IP addr of remote tunnel-endpoint
     * @param tsw           the tunnel-switch on which to add port
     * @param messageId     message-id for this RPC message
     * @param capwap        true indicates creation of CAPWAP tunneling port 
     */
    public JSONAddPortMsg(String name, String localIPAddr,
            String remoteIPAddr, OVSDBImpl tsw,
            int messageId, boolean capwap) throws OVSDBBridgeUnknown {
        this.localIPAddr = localIPAddr;
        this.remoteIPAddr = remoteIPAddr;
        if (this.remoteIPAddr == null) this.remoteIPAddr = "";
        this.name = name;
        this.tsw = tsw;
        this.id = messageId;
        this.capwap = capwap;
        buildAddPortMsg();
    }
    
    private void buildAddPortMsg() throws OVSDBBridgeUnknown {
        //first build the header
        completeString = "{\"method\":\"transact\",\"id\":"+id+",\"params\":["+
        " \"Open_vSwitch\", ";
        completeString += rowsUntil() + inserts() + mutations();
        if (capwap) {
            completeString += comment();
        } else {
            completeString += commentNonCapwap();
        }
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

    private String inserts() throws OVSDBBridgeUnknown {
        String start = "{\"uuid-name\":\"";
        Random rint = new Random(tsw.getDpid());
        String fakeport = "row" +
            String.format("%8x", rint.nextInt()) + "_" +
            String.format("%4x", (short)rint.nextInt()) + "_" +
            String.format("%4x", (short)rint.nextInt()) + "_" +
            String.format("%4x", (short)rint.nextInt()) + "_" +
            String.format("%8x", rint.nextInt()) + 
            String.format("%4x", (short)rint.nextInt());
        String mid = "\",\"op\":\"insert\",\"table\":\"Port\",\"row\":" +
                "{\"interfaces\":[\"named-uuid\",\"";    
        String fakeinterface = "row" +
        String.format("%8x", rint.nextInt()) + "_" +
        String.format("%4x", (short)rint.nextInt()) + "_" +
        String.format("%4x", (short)rint.nextInt()) + "_" +
        String.format("%4x", (short)rint.nextInt()) + "_" +
        String.format("%8x", rint.nextInt()) + 
        String.format("%4x", (short)rint.nextInt());
        String end = "\"],\"name\":\""+name+"\"}},";
        fakeport = fakeport.replace(' ', 'd');
        fakeinterface = fakeinterface.replace(' ', 'd');
        String portinsert = start + fakeport + mid + fakeinterface + end; 
        
        String bridgeuuid = getBridgeuuid();       
        String start1 = "{\"where\":[[\"_uuid\",\"==\",[\"uuid\",\"" +
        bridgeuuid + "\"]]],\"op\":\"update\",\"table\":\"Bridge\"," +
        "\"row\":{\"ports\":[\"set\",[[\"named-uuid\",\"" + fakeport + "\"],";
        String ports = "";
        String porthdr = "[\"uuid\",\"";
        String porttlr = "\"]";
        OVSBridge br = tsw.bridge.get(bridgeuuid);
        if ( br == null ) {
            throw new RuntimeException("tsw.bridge.get("+ bridgeuuid + ")" +
                    " returned Null");
        }
        ArrayList<String> pl = br.getNew().getPortUuids(); //for this bridge
        for (int i=0; i< pl.size(); i++) {
            ports += porthdr + pl.get(i) + porttlr;
            if (i+1 < pl.size()) ports += ",";
        }
        String bridgeupdate = start1 + ports + "]]}},";
        
        String intfinsert = "{\"uuid-name\":\"" + fakeinterface + "\",\"op\":" +
        "\"insert\",\"table\":\"Interface\",\"row\":{\"name\":\"" +
        name + "\",\"type\":\"gre\",\"options\":[\"map\"," +
        "[[\"remote_ip\",\"" + remoteIPAddr + "\"],[\"local_ip\",\""
        + localIPAddr + "\"]]]}},";
        
        String intfinsertNonCapwap = "{\"uuid-name\":\"" + fakeinterface + 
        "\",\"op\":" +
        "\"insert\",\"table\":\"Interface\",\"row\":{\"name\":\"" +
        name + "\"}},";
         
        if (capwap) {
            return portinsert + bridgeupdate + intfinsert;
        } else {
            return portinsert + bridgeupdate + intfinsertNonCapwap;
        }
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
        tsw.getMgmtIPAddr() + ":6635 add-port tunnelswitch " + name +" -- set " +
        "interface "+ name + " type=capwap options:remote_ip=" +
        remoteIPAddr + "\",\"op\":\"comment\"}]}";
    }
    
    private String commentNonCapwap() {
        return "{\"comment\":\"ovs-vsctl: ./ovs-vsctl --db=tcp:" +
        tsw.getMgmtIPAddr() + ":6635 add-port tunnelswitch " + name +
        "\",\"op\":\"comment\"}]}";
    }
    
    @Override
    public void writeTo(ChannelBuffer buf) {
        if (log.isTraceEnabled()) {
            log.trace("sent add-port message to:" + name + " msg-id:"+ id +
                    " @sw: {} ", HexString.toHexString(tsw.getDpid()));
        }
        buf.writeBytes(completeString.getBytes());
    }
    
    @Override
    public int getLengthU() {
        return completeString.length();
    }
}

