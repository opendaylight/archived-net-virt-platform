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

package org.sdnplatform.ovsdb;


import org.codehaus.jackson.JsonNode;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelUpstreamHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.sdnplatform.ovsdb.internal.JSONMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class OVSDBServerHandler extends IdleStateAwareChannelUpstreamHandler {
    protected static Logger logger = 
        LoggerFactory.getLogger(OVSDBServerHandler.class);
    Channel ch;
    OVSDBImplTest ot;
    
    public OVSDBServerHandler(OVSDBImplTest ot) {
        this.ot = ot;
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        ch = ctx.getChannel();
        JsonNode jn = (JsonNode)e.getMessage();
        logger.debug(e.toString());
        if (jn.get("method") != null) {
            String method = jn.get("method").getValueAsText();
            if (method.equals("monitor")) {
                processShowRequest(jn);
            } else if (method.equals("transact")) {
                processTransaction(jn);
            }
        }  
     
    }
 
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
        if (e.getCause() instanceof IllegalStateException) {
            logger.debug("Illegal State exception {}", 
                    e.getCause().toString());
        } else {
            super.exceptionCaught(ctx, e);
        }
    }
    
    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e)
            throws Exception {
      ot.shutdownOVSDBServer();
    }
    
    private void processShowRequest(JsonNode jn) {
        int msgid = jn.get("id").getValueAsInt();
        logger.debug("received show request with id {}", msgid);
        int testid = ot.getCurrentTestID();
        
        logger.debug("got current testid in show {}", testid);
        if (testid < 13 || testid >= 22) {
            JSONServerReply sr = new JSONServerReply(testid,msgid);
            ch.write(sr);
        } else {
            logger.debug("unknown test case");
        }
    }

    private void processTransaction(JsonNode jn) {
        int msgid = jn.get("id").getValueAsInt();
        int testid = ot.getCurrentTestID();
        logger.debug("rcvd current testid {}", testid);
        if (testid >= 13 && testid < 17 ) {
            logger.debug("received add-port request with id {}", msgid);
            JSONServerReply sr = new JSONServerReply(testid,msgid);
            ch.write(sr);
        } else if (testid >= 17 && testid < 21) {
            logger.debug("received del-port request with id {}", msgid);
            JSONServerReply sr = new JSONServerReply(testid,msgid);
            ch.write(sr);
        } else if (testid == 21) {
            logger.debug("received add-port for non tunnel port id{}", msgid);
            JSONServerReply sr = new JSONServerReply(testid, msgid);
            ch.write(sr);
        } else if (testid == 24) {
            logger.debug("received set-dpid msg id{}", msgid);
            JSONServerReply sr = new JSONServerReply(testid+1, msgid+1);
            ch.write(sr);
        } else {
            logger.debug("unknown test case");
        }
    }
    
    
    private class JSONServerReply extends JSONMsg{
        String replystr;
        int msgid;
        
        public JSONServerReply(int testid, int msgid) {
            this.msgid = msgid;
            if (testid < 10) createGarbageShowReply(testid);
            else if (testid < 13) createValidShowReply(testid);
            else if (testid < 17) createAddPortReply(testid);
            else if (testid < 21) createDelPortReply(testid);
            else if (testid == 21) createAddNonTunnelPortReply(testid);
            else if (testid >= 22) createGetSetDpidReply(testid);
        }
        
        private void createGarbageShowReply(int testid) {
            switch (testid) {
                case 0: // unknown value
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"result\":{ "+ " blah }} ";
                    break;
                case 1: // missing value
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"result\":{ "+ " \"blah\" }}";
                    break;
                case 2: //unknown record "blah"
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"blah\":{ }}";
                    break;
                case 3: //missing value 
                    replystr = " {\"error\":,\"id\":"+msgid+
                    ",\"result\":{ }}";
                    break; 
                case 4: //missing comma separator
                    replystr = " {\"error\":null\"id\":"+msgid+
                    ",\"result\":{ }}";
                    break; 
                case 5: //missing id
                    replystr = " {\"id\":null}";
                    break;
                case 6: // blank reply
                    replystr = "{}";
                    break;
                case 7: 
                    replystr = "blah";
                    break;
                case 8: 
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"result\":{ "+ "\"Prt\":{}  }} "; 
                    break;
                case 9: 
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"result\":{ "+ "\"Port\":[]  }} "; 
                    break;
                  
            }

        }
        
        private void createValidShowReply(int testid) {
            switch (testid) {
                case 10: 
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"result\":{ "+ "  }} "; 
                    break;
                case 11: 
                    replystr = " {\"error\":null,\"id\":"+msgid+
                    ",\"result\":{ "+ "\"Port\":{}  }} "; 
                    break;
                case 12: 
                    replystr = 
                    "{\"id\":0,\"error\":null,\"result\":"+
                    "{\"Port\":{\"355b1c92-f0d8-44f2-bfbb-9e65feb0ac05\":{" +
                    "\"new\":{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid" +
                    "\",\"523a3b14-2b1d-4fbf-95e8-78228"+
                    "951b365\"],\"name\":\"eth1\",\"tag\":[\"set\",[]]}}," +
                    "\"e8df8c86-c106-41d3-98b2-71bdd83b7628\":{\"new\":{" +
                    "\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\"," +
                    "\"ba0c4e7d-7dd5-4dd1-a66f-5488d82e91e3\"],\"name\":" +
                    "\"tunnelswitch\",\"tag\":[\"set\",[]]}}},"+
                    "\"Controller\":{\"b017abcc-6d49-4cc8-9d42-5973175e4f26" +
                    "\":{\"new\":{\"target\":\"tcp:172.16.22.1\""+
                    ",\"is_connected\":false}}},"+
                    "\"Interface\":{\"523a3b14-2b1d-4fbf-95e8-78228951b365\"" +
                    ":{\"new\":{\"name\":\"eth1\",\"type\":\"\",\"options\"" +
                    ":[\"map\",[]]}},\"ba0c4e7d-7dd5-4dd1-a66f-5488d82e91e3" +
                    "\":{\"new\":{\"name\":\"tunnelswitch\",\"type\":" +
                    "\"internal\",\"options\":[\"map\",[]]}}},"+
                    "\"Open_vSwitch\":{\"aa8711b0-a2ea-4acd-926f-" +
                    "9dbc0831a6c3\":{\""+
                    "new\":{\"ovs_version\":[\"set\",[]],\"cur_cfg\":6," +
                    "\"bridges\":[\"uuid\",\"755e444d-5455-44ff-9c2e-5" +
                    "636f9bfb8e2\"],\"manager_options\":[\"set\",[]]}}},"+
                    "\"Bridge\":{\"755e444d-5455-44ff-9c2e-5636f9bfb8e2\":" +
                    "{\"new\":{\"name\":\"tunnelswitch\",\"ports\":[\"set\"," +
                    "[[\"uuid\",\"355b1c92-f0d8-44f2-bfbb-9e65feb0ac05\"]," +
                    "[\"uuid\",\"e8df8c86-c106-4"+
                    "1d3-98b2-71bdd83b7628\"]]],\"other_config\":[\"map\"," +
                    "[[\"datapath-id\",\"0000000000000018\"],[\"datapath" +
                    "_type\",\"system\"],[\"tunnel-ip\",\"192.168.158.129\"" +
                    "]]],\"controller\":[\"uuid\",\"b017abcc-6d49-4cc8-" +
                    "9d42-5973175e4f26\"],\"fail_mode\":\"secure\"}}}}}";
                    break;
            }
        }
        
        private void createAddPortReply(int testid) {
            switch (testid) {
                case 13: // successful addition
                    replystr = "{\"id\":"+msgid+",\"error\":null," +
                            "\"result\":[{},{}," +
                            "{},{},{\"uuid\":[\"uuid\",\"d241e4c1-1b65-431a" +
                            "-83ad-71c3d5ba3809\"]},{\"count\":1},{\"uuid\"" +
                            ":[\"uuid\",\"bd49f36c-ff34-41ca-8d83-4d6da7" +
                            "1b5fc2\"]},{\"count\":1},{\"rows\":[{" +
                            "\"next_cfg\":1}]},{}]}";
                    break;
                case 14: // error on adding same port
                    replystr =    "{\"id\":"+msgid+",\"error\":null,\"" +
                            "result\":[{}," +
                            "{},{},{},{},{\"uuid\":[\"uuid\",\"5012ab9f-" +
                            "0df4-4c5d-aa92-a0b3f68d62b9\"]},{\"count\":1}" +
                            ",{\"uuid\":[\"uuid\",\"99c6e7b3-8718-4d0e-" +
                            "b186-1cd70932dd58\"]},{\"count\":1},{\"rows\"" +
                            ":[{\"next_cfg\":8}]},{},{\"error\":" +
                            "\"constraint violation\",\"details\":" +
                            "\"Transaction causes multiple rows in Port " +
                            "table to have identical values (tunn2) for " +
                            "index on column name.  First row, with " +
                            "UUID 79e7c1c5-71ba-4853-89ae-976d79dfa1c3, " +
                            "existed in the database before this transaction " +
                            "and was not modified by the transaction.  Second"+
                            "row, with UUID 5012ab9f-0df4-4c5d-aa92-" +
                            "a0b3f68d62b9, " +
                            "was inserted by this transaction.\"}]}";
                    break;
                case 15: // empty reply
                    replystr = "{\"id\":"+msgid+",\"error\":null,\"" +
                            "result\":[]}";
                    break;
                case 16: // garbage reply
                    replystr = "{\"id\":"+msgid+",\"error\":null,\"" +
                    "result\":[{},{},{},{},{}, {blah}, {}, {}, {}]}";
                    break;
            }
        }
        
        private void createDelPortReply(int testid) {
            switch (testid) {
                case 17: // successful deletion
                    replystr = "{\"id\":"+msgid+",\"error\":null,\"result\":" +
                            "[{},{},{},{},{},{},{\"count\":1},{\"count\":1}," +
                            "{\"rows\":[{\"next_cfg\":37}]},{}]}";
                    break;
                case 18: // error on deleting unknown port
                    replystr = "{\"id\":"+msgid+",\"error\":null,\"result\"" +
                            ":[{}," +
                            "{},{},{},{\"error\":\"timed out\",\"details\":" +
                            "\"wait timed out\"},null,null,null,null]}";
                    break;
                case 19: // empty reply
                    replystr = "{\"id\":"+msgid+",\"error\":null,\"" +
                            "result\":[]}";
                    break;
                case 20: // garbage reply
                    replystr = "{\"id\":"+msgid+",\"error\":null,\"" +
                    "result\":[{},{},{},{},{}, blah, {}, {}, {}]}";
                    break;
            }
        }
        
        private void createAddNonTunnelPortReply(int testid) {
            switch(testid) {
                case 21: // successful addition
                    replystr = "{\"id\":"+msgid+",\"error\":null," +
                    "\"result\":[{},{}," +
                    "{},{},{\"uuid\":[\"uuid\",\"d241e4c1-ccdd-431a" +
                    "-83ad-71c3d5ba3809\"]},{\"count\":1},{\"uuid\"" +
                    ":[\"uuid\",\"bd49f36c-ff34-41ca-ddcc-4d6da7" +
                    "1b5fc2\"]},{\"count\":1},{\"rows\":[{" +
                    "\"next_cfg\":1}]},{}]}";
                    break;
            }
        }
        
        private void createGetSetDpidReply(int testid) {
            switch(testid) {
                case 22: // dpid not set anywhere
                        // valid controller-ip 
                    replystr = "{\"id\":0,\"error\":null,\"result\":{"+
                    
                        "\"Port\":{" +
                        "\"c5538a39-8074-403d-bdef-a298f07903cf\":{\"new\":" +
                        "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\"," +
                        "\"a8d4f4aa-67e7-4539-923c-987a7e4ada52\"],\"name\":"+
                        "\"virbr0\",\"tag\":[\"set\",[]]}}," +
                        "\"52742937-27a8-46ec-8ced-c68d2b6153f7\":{\"new\":"+
                        "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\","+
                        "\"e5c62049-0299-467c-b3bc-da2b4ca01f52\"],\"name\":"+
                        "\"eth1\",\"tag\":[\"set\",[]]}},"   +
                        "\"8214a945-f4ee-4671-98af-5c75272aa569\":{\"new\":"+
                        "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\","+
                        "\"cde08e29-f0e8-447d-9c1b-650206f19800\"],\"name\":"+
                        "\"ovs-br0\",\"tag\":[\"set\",[]]}}},"+
                      
                        "\"Controller\":"+
                        "{\"924b152e-6a68-4fec-871f-71c28a6e57d1\":{\"new\":"+
                        "{\"target\":\"tcp:192.168.247.132\",\"is_" +
                        "connected\":true}}},"+

                        "\"Interface\":{"+
                        "\"e5c62049-0299-467c-b3bc-da2b4ca01f52\":{\"new\":" +
                        "{\"name\":\"eth1\",\"type\":\"\",\"options\":[" +
                        "\"map\",[]]}}," +
                        "\"a8d4f4aa-67e7-4539-923c-987a7e4ada52\":{\"new\":" +
                        "{\"name\":\"virbr0\",\"type\":\"internal\",\"opti" +
                        "ons\":[\"map\",[]]}},"+
                        "\"cde08e29-f0e8-447d-9c1b-650206f19800\":{\"new\":"+
                        "{\"name\":\"ovs-br0\",\"type\":\"internal\",\"opti" +
                        "ons\":[\"map\",[]]}}}," +

                        "\"Open_vSwitch\":{"+
                        "\"f4949a56-f807-4e82-b20d-601619b18cd6\":{\"new\":" +
                        "{\"ovs_version\":\"1.3.0+build0\",\"cur_cfg\":14," +
                        "\"bridges\":[\"set\",[[\"uuid\",\"0447bb39-6839" +
                        "-44bc-9653-49d7d86c295a\"],[\"uuid\",\"862a0dc7" +
                        "-20de-415c-8377-20529c7f6cde\"]]],\"manager_" +
                        "options\":[\"set\",[]]}}}," +
                         

                        "\"Bridge\":{" +
                        "\"862a0dc7-20de-415c-8377-20529c7f6cde\":{\"new\":" +
                        "{\"name\":\"ovs-br0\"," + "\"datapath_id\":\"\"," +
                        
                        "\"ports\":[\"set\",[[\"uuid\"," +
                        "\"52742937-27a8-46ec-8ced-c68d2b6153f7\"],[\"uuid\"," +
                        "\"8214a945-f4ee-4671-98af-5c75272aa569\"]]],\"other_" +
                        "config\":[\"map\",[[\"datapath-id\",\"" +
                        "\"], [\"tunnel-ip\",\"192.168.20.138\"]]]," +
                        "\"controller\":[\"uuid\",\"924b152e-6a68-4fec-871f" +
                        "-71c28a6e57d1\"],\"fail_mode\":\"secure\"}},"+

                        "\"0447bb39-6839-44bc-9653-49d7d86c295a\":{\"new\":" +
                        "{\"name\":\"virbr0\",\"ports\":[\"uuid\",\"c5538a39" +
                        "-8074-403d-bdef-a298f07903cf\"],\"other_config\":" +
                        "[\"map\",[]],\"controller\":[\"set\",[]],\"fail_" +
                        "mode\":[\"set\",[]]}}}}}";

        
                        break;
                     
                case 23: //other-config missing and datapath_id not set
                        // multiple controller ips
                    replystr = "{\"id\":0,\"error\":null,\"result\":{"+
                    
                    "\"Port\":{" +
                    "\"c5538a39-8074-403d-bdef-a298f07903cf\":{\"new\":" +
                    "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\"," +
                    "\"a8d4f4aa-67e7-4539-923c-987a7e4ada52\"],\"name\":"+
                    "\"virbr0\",\"tag\":[\"set\",[]]}}," +
                    "\"52742937-27a8-46ec-8ced-c68d2b6153f7\":{\"new\":"+
                    "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\","+
                    "\"e5c62049-0299-467c-b3bc-da2b4ca01f52\"],\"name\":"+
                    "\"eth1\",\"tag\":[\"set\",[]]}},"   +
                    "\"8214a945-f4ee-4671-98af-5c75272aa569\":{\"new\":"+
                    "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\","+
                    "\"cde08e29-f0e8-447d-9c1b-650206f19800\"],\"name\":"+
                    "\"ovs-br0\",\"tag\":[\"set\",[]]}}},"+
                  
                    "\"Controller\":{"+
                    "\"924b152e-6a68-4fec-871f-71c28a6e57d1\":{\"new\":"+
                    "{\"target\":\"tcp:192.168.247.132\",\"is_" +
                    "connected\":true}}," +
                    "\"00ba152e-6a68-4fec-871f-71c28a6e57d1\":{\"new\":"+
                    "{\"target\":\"tcp:192.168.204.111\",\"is_" +
                    "connected\":true}}" +
                    "},"+

                    "\"Interface\":{"+
                    "\"e5c62049-0299-467c-b3bc-da2b4ca01f52\":{\"new\":" +
                    "{\"name\":\"eth1\",\"type\":\"\",\"options\":[" +
                    "\"map\",[]]}}," +
                    "\"a8d4f4aa-67e7-4539-923c-987a7e4ada52\":{\"new\":" +
                    "{\"name\":\"virbr0\",\"type\":\"internal\",\"opti" +
                    "ons\":[\"map\",[]]}},"+
                    "\"cde08e29-f0e8-447d-9c1b-650206f19800\":{\"new\":"+
                    "{\"name\":\"ovs-br0\",\"type\":\"internal\",\"opti" +
                    "ons\":[\"map\",[]]}}}," +

                    "\"Open_vSwitch\":{"+
                    "\"f4949a56-f807-4e82-b20d-601619b18cd6\":{\"new\":" +
                    "{\"ovs_version\":\"1.3.0+build0\",\"cur_cfg\":14," +
                    "\"bridges\":[\"set\",[[\"uuid\",\"0447bb39-6839" +
                    "-44bc-9653-49d7d86c295a\"],[\"uuid\",\"862a0dc7" +
                    "-20de-415c-8377-20529c7f6cde\"]]],\"manager_" +
                    "options\":[\"set\",[]]}}}," +
                     

                    "\"Bridge\":{" +
                    "\"862a0dc7-20de-415c-8377-20529c7f6cde\":{\"new\":" +
                    "{\"name\":\"ovs-br0\"," + "\"datapath_id\":" +
                    "\"\"," +
                    "\"ports\":[\"set\",[[\"uuid\"," +
                    "\"52742937-27a8-46ec-8ced-c68d2b6153f7\"],[\"uuid\"," +
                    "\"8214a945-f4ee-4671-98af-5c75272aa569\"]]],\"other_" +
                    "config\":[\"map\",[" +
                    "[\"tunnel-ip\",\"192.168.20.138\"]]]," +
                    "\"controller\":[\"uuid\",\"924b152e-6a68-4fec-871f" +
                    "-71c28a6e57d1\"],\"fail_mode\":\"secure\"}},"+

                    "\"0447bb39-6839-44bc-9653-49d7d86c295a\":{\"new\":" +
                    "{\"name\":\"virbr0\",\"ports\":[\"uuid\",\"c5538a39" +
                    "-8074-403d-bdef-a298f07903cf\"],\"other_config\":" +
                    "[\"map\",[]],\"controller\":[\"set\",[]],\"fail_" +
                    "mode\":[\"set\",[]]}}}}}";

    
                    break;
                    
                case 24: //other config missing but datapath_id set
                    // no controller ips
                    replystr = "{\"id\":0,\"error\":null,\"result\":{"+
                    
                    "\"Port\":{" +
                    "\"c5538a39-8074-403d-bdef-a298f07903cf\":{\"new\":" +
                    "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\"," +
                    "\"a8d4f4aa-67e7-4539-923c-987a7e4ada52\"],\"name\":"+
                    "\"virbr0\",\"tag\":[\"set\",[]]}}," +
                    "\"52742937-27a8-46ec-8ced-c68d2b6153f7\":{\"new\":"+
                    "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\","+
                    "\"e5c62049-0299-467c-b3bc-da2b4ca01f52\"],\"name\":"+
                    "\"eth1\",\"tag\":[\"set\",[]]}},"   +
                    "\"8214a945-f4ee-4671-98af-5c75272aa569\":{\"new\":"+
                    "{\"trunks\":[\"set\",[]],\"interfaces\":[\"uuid\","+
                    "\"cde08e29-f0e8-447d-9c1b-650206f19800\"],\"name\":"+
                    "\"ovs-br0\",\"tag\":[\"set\",[]]}}},"+
                  
                    "\"Controller\":"+
                    "{\"924b152e-6a68-4fec-871f-71c28a6e57d1\":{\"new\":"+
                    "{\"target\":\"\",\"is_" +
                    "connected\":false}}},"+

                    "\"Interface\":{"+
                    "\"e5c62049-0299-467c-b3bc-da2b4ca01f52\":{\"new\":" +
                    "{\"name\":\"eth1\",\"type\":\"\",\"options\":[" +
                    "\"map\",[]]}}," +
                    "\"a8d4f4aa-67e7-4539-923c-987a7e4ada52\":{\"new\":" +
                    "{\"name\":\"virbr0\",\"type\":\"internal\",\"opti" +
                    "ons\":[\"map\",[]]}},"+
                    "\"cde08e29-f0e8-447d-9c1b-650206f19800\":{\"new\":"+
                    "{\"name\":\"ovs-br0\",\"type\":\"internal\",\"opti" +
                    "ons\":[\"map\",[]]}}}," +

                    "\"Open_vSwitch\":{"+
                    "\"f4949a56-f807-4e82-b20d-601619b18cd6\":{\"new\":" +
                    "{\"ovs_version\":\"1.3.0+build0\",\"cur_cfg\":14," +
                    "\"bridges\":[\"set\",[[\"uuid\",\"0447bb39-6839" +
                    "-44bc-9653-49d7d86c295a\"],[\"uuid\",\"862a0dc7" +
                    "-20de-415c-8377-20529c7f6cde\"]]],\"manager_" +
                    "options\":[\"set\",[]]}}}," +
                     

                    "\"Bridge\":{" +
                    "\"862a0dc7-20de-415c-8377-20529c7f6cde\":{\"new\":" +
                    "{\"name\":\"ovs-br0\"," + "\"datapath_id\":" +
                    "\"0000000033333334\"," +
                    "\"ports\":[\"set\",[[\"uuid\"," +
                    "\"52742937-27a8-46ec-8ced-c68d2b6153f7\"],[\"uuid\"," +
                    "\"8214a945-f4ee-4671-98af-5c75272aa569\"]]],\"other_" +
                    "config\":[\"map\",[" +
                    "[\"tunnel-ip\",\"192.168.20.138\"]]]," +
                    "\"controller\":[\"uuid\",\"924b152e-6a68-4fec-871f" +
                    "-71c28a6e57d1\"],\"fail_mode\":\"secure\"}},"+

                    "\"0447bb39-6839-44bc-9653-49d7d86c295a\":{\"new\":" +
                    "{\"name\":\"virbr0\",\"ports\":[\"uuid\",\"c5538a39" +
                    "-8074-403d-bdef-a298f07903cf\"],\"other_config\":" +
                    "[\"map\",[]],\"controller\":[\"set\",[]],\"fail_" +
                    "mode\":[\"set\",[]]}}}}}";

                    break;
                    
                case 25: //valid set reply
                    replystr = 
                    "{\"method\":\"update\",\"id\":null,\"params\":[null,{" +
                    "\"Bridge\":{\"28a4b61c-a392-476f-bb63-841d12aaf11d\":" +
                    "{\"old\":{\"other_config\":[\"map\",[" +
                    "[\"datapath-id\",\"0000000033333334\"],[\"datapath" +
                    "_type\",\"system\"],[\"tunnel-ip\",\"192.168.158.132\"]]" +
                    "]},\"new\":{\"name\":\"ovs-br0\",\"other_config\":" +
                    "[\"map\",[[\"datapath-id\",\"0000000000000003\"]," +
                    "[\"datapath_type\",\"system\"],[\"tunnel-ip\",\"192.168." +
                    "158.132\"]]]}}}}]}{\"id\":1,\"error\":null,\"result\"" +
                    ":[{\"count\":1},{}]}";
                    break;
            }
            
        }
        
        @Override
        public int getLengthU() {
            return replystr.length();
        }

        @Override
        public void writeTo(ChannelBuffer buf) {
            logger.info("sent server reply");
            buf.writeBytes(replystr.getBytes());
        }
    }
   
}
