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
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;

public class JSONSetCIPMsg extends JSONMsg {
    private ArrayList<String> ciplist;
    private OVSDBImpl ovs;
    private int id;
    private String setcipstr;
    private ArrayList<String> existingCIP;
    private String bridgeuuid;
    
    public JSONSetCIPMsg(ArrayList<String> cntrIP, OVSDBImpl ovsdbImpl,
            int msgId) throws OVSDBBridgeUnknown {
        this.ciplist = cntrIP;
        this.ovs = ovsdbImpl;
        this.id = msgId;
        this.setcipstr = null;
        this.existingCIP = new ArrayList<String>();
        buildSetCntlIPString();
    }

    private void buildSetCntlIPString() throws OVSDBBridgeUnknown {
        setcipstr = "{\"method\":\"transact\",\"id\":" + id + ",\"params\":" +
                "[\"Open_vSwitch\",";
        buildRowsExistingCIPs();
        buildRowsController();
        buildRowsBridge();
        buildInserts();
        buildComment();
    }

    private void buildRowsExistingCIPs() {
        Iterator<Entry<String, OVSController>> iter = ovs.controller.entrySet()
                                                            .iterator();
        while (iter.hasNext()) {
            Entry<String, OVSController> e = iter.next();
            String uuid = e.getKey();
            OVSController c = e.getValue();
            String ecip = c.getNew().getTarget();
            if (ecip != null) { 
                existingCIP.add(uuid);
                setcipstr += "{\"rows\":[{\"target\":\""+ ecip +"\"}]," +
                    "\"until\":\"==\",\"where\":[[\"_uuid\",\"==\"," +
                    "[\"uuid\",\""+ uuid +"\"]]],\"timeout\":0,\"op\":" +
                    "\"wait\",\"table\":\"Controller\",\"columns\":" +
                    "[\"target\"]},";
            }
        }
    }

    private void buildRowsController() throws OVSDBBridgeUnknown {
        if (existingCIP.size() == 0) {
            log.warn("no existing controller-IPs on sw {}", ovs.getDpid());
            return;
        }
        if (existingCIP.size() > 1) {
            setcipstr += "{\"rows\":[{\"controller\":[\"set\",[";
            for (int i=0; i<existingCIP.size(); i++) {
                setcipstr += "[\"uuid\",\""+ existingCIP.get(i) +"\"]";
                if ((i+1) == existingCIP.size()) {
                    setcipstr += "]]}]";
                } else {
                    setcipstr += ",";
                }
            }
        } else {
            setcipstr += "{\"rows\":[{\"controller\":[" +
                "\"uuid\",\""+ existingCIP.get(0) +"\"]}]";
        }                                    

        bridgeuuid = getOvsbr0Bridgeuuid();
        if (bridgeuuid == null) {
            throw new RuntimeException("ovs.bridge.get("+ bridgeuuid + ")" +
            " returned Null ub set-controller-ip message");
        }
        setcipstr += ",\"until\":\"==\",\"where\":[[\"_uuid\",\"==\"," +
            "[\"uuid\",\""+ bridgeuuid +"\"]]]," +
            "\"timeout\":0,\"op\":\"wait\",\"table\":\"Bridge\",\"columns\":" +
            "[\"controller\"]},";
    }
    
    private void buildRowsBridge() {
         String dbuuid = null;
        Set<String> dbset = ovs.open_vswitch.keySet();
        if (dbset.size() > 1) {
            log.warn("More than one database in ovs @ dpid {}", ovs.getDpid());
        } else if (dbset.size() == 0) {
            throw new RuntimeException("no OVSDB database Open_vSwitch found");
        } 
        dbuuid = dbset.iterator().next();
        if (ovs.bridge.size() == 1) {
            setcipstr += "{\"rows\":[{\"bridges\":[\"uuid\",\"" + bridgeuuid +
                "\"]}],\"until\":\"==\",\"where\":[[\"_uuid\",\"==\",[\"uuid\""
                + ",\""+ dbuuid +"\"]]],\"timeout\":0,\"op\":\"wait\",\"table" +
                "\":\"Open_vSwitch\",\"columns\":[\"bridges\"]},";
        } else {
            setcipstr += "{\"rows\":[{\"bridges\":[\"set\",[";
            Iterator<String> iter = ovs.bridge.keySet().iterator();
            while (iter.hasNext()) {
                setcipstr += "[\"uuid\",\"" + iter.next() + "\"]";
                if (iter.hasNext()) {
                    setcipstr += ",";
                } else {
                    setcipstr += "]]}],";
                }
            }
            setcipstr += "\"until\":\"==\",\"where\":[[\"_uuid\",\"==\"," +
                "[\"uuid\""
                + ",\""+ dbuuid +"\"]]],\"timeout\":0,\"op\":\"wait\",\"table" +
                "\":\"Open_vSwitch\",\"columns\":[\"bridges\"]},";
        }
    }
    
    private void buildInserts() {
        ArrayList<String> fakerowlist = new ArrayList<String>();
        Random rint = new Random(ovs.getDpid());
        for (int i=0; i<ciplist.size(); i++) {
            String fakerow = "row" +
                String.format("%8x", rint.nextInt()) + "_" +
                String.format("%4x", (short)rint.nextInt()) + "_" +
                String.format("%4x", (short)rint.nextInt()) + "_" +
                String.format("%4x", (short)rint.nextInt()) + "_" +
                String.format("%8x", rint.nextInt()) + 
                String.format("%4x", (short)rint.nextInt());
            fakerow = fakerow.replace(' ', 'd');
            setcipstr += "{\"uuid-name\":\""+ fakerow +"\",\"op\":\"insert\"" +
                    ",\"table\":\"Controller\",\"row\":{\"target\":" +
                    "\""+ ciplist.get(i) +"\"}},";
            fakerowlist.add(fakerow);
        }
        // insert where
        setcipstr += "{\"where\":[[\"_uuid\",\"==\",[\"uuid\",\""+ bridgeuuid + 
            "\"]]],\"op\":\"update\",\"table\":\"Bridge\"," +
            "\"row\":{\"controller\":";
        if (fakerowlist.size() == 1) {
            setcipstr += "[\"named-uuid\",\""+ fakerowlist.get(0) +"\"]}},";
        } else {
            setcipstr += "[\"set\",[";
            for (int k=0; k<fakerowlist.size(); k++) {
                setcipstr += "[\"named-uuid\",\""+ fakerowlist.get(k) +"\"]";
                if (k+1 == fakerowlist.size()) {
                    setcipstr += "]]}},";
                } else {
                    setcipstr += ",";
                }
            }
        }
    }
    
    private void buildComment() {
        setcipstr += "{\"comment\":\"ovs-vsctl: ovs-vsctl --no-wait " +
                "set-controller ovs-br0 tcp:192.168." +
                "200.199\",\"op\":\"comment\"}]}";
    }
    
    private String getOvsbr0Bridgeuuid() throws OVSDBBridgeUnknown {
        Iterator<Entry<String, OVSBridge>> iter = ovs.bridge.entrySet()
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
    
    @Override
    public void writeTo(ChannelBuffer buf) {
        if (log.isDebugEnabled()) {
            
            log.debug("sent set-cntl-IP message to sw@: {} MSG: {} ", 
                    ovs.getHexDpid(), setcipstr);
        }
        buf.writeBytes(setcipstr.getBytes());
    }
    
    @Override
    public int getLengthU() {
        return setcipstr.length();
    }
}
