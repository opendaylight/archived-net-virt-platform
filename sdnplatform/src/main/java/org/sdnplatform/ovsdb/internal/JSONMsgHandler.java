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

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;


import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.openflow.util.HexString;
import org.sdnplatform.ovsdb.IOVSDB;
import org.sdnplatform.ovsdb.internal.JSONShowReplyMsg.ShowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JSONMsgHandler extends SimpleChannelUpstreamHandler {
    protected static Logger logger = 
        LoggerFactory.getLogger(JSONMsgHandler.class);
    private IOVSDB tsw;
    private Object statusObject;
    
    private static final int SHOW_REPLY = 0;
    private static final int ADD_PORT_REPLY = 1;
    private static final int DEL_PORT_REPLY = 2;
    private static final int SET_DPID_REPLY = 3;
    private static final int SET_CIP_REPLY = 4;
    private static int MSG = -1;
    
    
    public JSONMsgHandler(IOVSDB tsw, Object statusObject) {
        this.tsw = tsw;
        this.statusObject = statusObject;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        throws Exception {
        JsonNode jn = (JsonNode)e.getMessage();
        if (logger.isTraceEnabled())
            logger.trace("receveid message: {}", e.toString());
        if (jn.get("id") == null) return;
        if (jn.get("id").isNumber()) {
            if(logger.isDebugEnabled()) {
                logger.debug("got result for id: {}",
                        jn.get("id").getValueAsInt());
            }
            
            MSG = tsw.getExpectedMessage(jn.get("id").getValueAsInt());
            switch (MSG) {
                case SHOW_REPLY:
                    handleShowReply(jn);
                    synchronized(statusObject) {
                        statusObject.notify();
                    }
                    break;
                case ADD_PORT_REPLY:
                    handleAddPortReply(jn);
                    synchronized(statusObject) {
                        statusObject.notify();
                    }
                    break;
                case DEL_PORT_REPLY:
                    handleDelPortReply(jn);
                    synchronized(statusObject) {
                        statusObject.notify();
                    }
                    break;
                case SET_DPID_REPLY:
                    //noop
                    break;
                case SET_CIP_REPLY:
                    //noop
                    break; //FIXME check for errors
                default :
                    //noop
                    logger.error("Unexpected Message Reply id {}", 
                            jn.get("id").getValueAsInt());
                    
            }
            
           
        } else {
            if (jn.get("method") == null) return;
            // handle JSON RPC notifications
            if (jn.get("id").getValueAsText().equals("null") &&
                    jn.get("method").getValueAsText().equals("update")) {
                // got an update message
                if (logger.isDebugEnabled()) logger.debug("GOT an UPDATE");
                handleUpdateNotification(jn);
                synchronized(statusObject) {
                    statusObject.notify();
                }
            } else if (jn.get("id").getValueAsText().equals("echo") &&
                    jn.get("method").getValueAsText().equals("echo")) {
                // no op
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
        if (e.getCause() instanceof IllegalStateException) {
            // hiding exception logging - expected because of the way we do
            // JSON message decoding
            // logger.debug("Illegal State exception ", 
            //        e.getCause().toString());
        } else if (e.getCause() instanceof UnrecognizedPropertyException) {
            logger.error("Jackson unrecognized property error {}", 
                    e.getCause());
        } else if (e.getCause() instanceof JsonMappingException) {
            logger.error("Jackson mapping error {}", 
                    e.getCause());
        } else if (e.getCause() instanceof JsonParseException) {
            logger.error("Jackson parsing error {}", 
                    e.getCause());
        } else if (e.getCause() instanceof ClosedChannelException) {
            logger.error("Netty closed channel error", e.getCause());
        } else if (e.getCause() instanceof ConnectException) {
            logger.error("Connection refused", e.getCause());
        } else if (e.getCause() instanceof IOException) {
            logger.error("IO problem", e.getCause());
        } else {
            super.exceptionCaught(ctx, e);
        }
    }
    
    private void handleAddPortReply(JsonNode jn) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JSONAddPortReplyMsg rep = 
            mapper.treeToValue(jn, JSONAddPortReplyMsg.class);
        //just check for errors
        String returned = rep.getResult().toString();
        if (returned.contains("error")) {
            logger.error("ovsdb-server at sw {} returned error {}",
                    HexString.toHexString(tsw.getDpid()), 
                    returned.substring(returned.indexOf("error")));
        }
        
    }
    
    private void handleDelPortReply(JsonNode jn) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JSONDelPortReplyMsg rep = 
            mapper.treeToValue(jn, JSONDelPortReplyMsg.class);
        //just check for errors
        String returned = rep.getResult().toString();
        if (returned.contains("error")) {
            logger.error("ovsdb-server at sw {} returned error {}",
                    HexString.toHexString(tsw.getDpid()), 
                    returned.substring(returned.indexOf("error")));
        }
        
    }
    
    private void handleUpdateNotification(JsonNode jn) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JSONUpdateMsg rep = mapper.treeToValue(jn, JSONUpdateMsg.class);
        ShowResult res = rep.getParams().get(1);
        tsw.updateTunnelSwitchFromUpdate(res);
        //debugUpdateOrShow(res);
    }
    
    private void handleShowReply(JsonNode jn) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JSONShowReplyMsg rep = mapper.treeToValue(jn, JSONShowReplyMsg.class);
        tsw.updateTunnelSwitchFromShow(rep);
        //debugUpdateOrShow(rep.getResult());
    }
    
    public void debugUpdateOrShow(ShowResult sr) {
        if (logger.isDebugEnabled()) {
            if (sr.getOpen_vSwitch() != null) {
                logger.debug("DB UPDATE: " + sr.getOpen_vSwitch().toString());
            }
            if (sr.getController() != null) {
                logger.debug("CNTL UPDATE: " + sr.getController().toString());
            }
            if (sr.getInterface() != null) {
                logger.debug("INTF UPDATE: " + sr.getInterface().toString());
            }
            if (sr.getPort() != null) {
                logger.debug("PORT UPDATE: " + sr.getPort().toString());
            }
            if (sr.getBridge() != null) {
                logger.debug("BRIDGE UPDATE: " + sr.getBridge().toString());
            }
        }
    }
    
}
