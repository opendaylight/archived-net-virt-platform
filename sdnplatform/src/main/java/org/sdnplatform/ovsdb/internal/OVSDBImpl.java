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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.ssl.SslHandler;
import org.openflow.util.HexString;
import org.sdnplatform.ovsdb.IOVSDB;
import org.sdnplatform.ovsdb.internal.JSONShowReplyMsg.ShowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * IOVDBImpl maps the ovsdb state of the OVS switch that is configured for 
 * tunneling. It creates tunnels in this switch via JSON RPC calls to 
 * ovsdb-server. It offers a public API for managing tunnels in this switch
 * potentially used by TunnelManager and  VCenterManager
 * 
 * @author Saurav Das
 *
 */
public class OVSDBImpl implements IOVSDB {
    private static Logger logger = LoggerFactory.getLogger(OVSDBImpl.class);
    private long dpid;
    private String hexDpid;
    private Channel channel;
    private boolean useSSL;
    private AtomicInteger messageid;
    private String mgmtIPAddr;
    private String tunnelIPAddr;
    private String tunnelIPAddrName;
    private OVSDBClientPipelineFactory ovsdbcfact;
    private ClientBootstrap bootstr; 
    private Object statusObj;
    private String description;
    
    // OVS-db state 
    protected HashMap<String, OVSPort> port;
    protected HashMap<String, OVSController> controller;
    protected HashMap<String, OVSInterface> intf;
    protected HashMap<String, OVSDatabase> open_vswitch;
    protected HashMap<String, OVSBridge> bridge;
    
    //JSON RPC reply messages
    private static final int SHOW_REPLY = 0;
    private static final int ADD_PORT_REPLY = 1;
    private static final int DEL_PORT_REPLY = 2;
    private static final int SET_DPID_REPLY = 3;
    private static final int SET_CIP_REPLY = 4;
    private static int OVSDB_SERVER_PORT = 6635;
    private static int OVSDB_SSL_SERVER_PORT = 6636;
    
    private int expectedMessage;
    private int expectedMessageReplyId;
    
   
    
    /**
     * Constructor
     * @param dpid         of ovs
     * @param mgmtIPAddr   the IP address used by JSON RPC messaging
     * @param ovsdbcfact   netty client pipeline factory for plaintext
     * @param bootstr      netty client bootstrap
     * @param statusObj    ad-hoc object for RPC synchronization
     */
    public OVSDBImpl(long dpid, String mgmtIPAddr,
           OVSDBClientPipelineFactory ovsdbcfact,
           ClientBootstrap bootstr, Object statusObj) {
        this.dpid = dpid;
        this.mgmtIPAddr = mgmtIPAddr;
        this.hexDpid = (this.dpid != -1) ? HexString.toHexString(this.dpid)
                            : null;
        this.ovsdbcfact = ovsdbcfact;
        this.bootstr = bootstr;
        this.statusObj = statusObj;
        this.useSSL = false; // Disable SSL usage for now
        this.tunnelIPAddr = null;
        this.messageid = new AtomicInteger(0);
        
        port = new HashMap<String, OVSPort>() ;
        controller = new HashMap<String, OVSController>();
        intf = new HashMap<String, OVSInterface>();
        open_vswitch = new HashMap<String, OVSDatabase>();
        bridge = new HashMap<String, OVSBridge>();
 
    }
    
    private void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return hexDpid;
    }
    
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    public String getMgmtIPAddr() {
        return mgmtIPAddr;
    }
    
    //*********************
    // DPID related
    //*********************
    
    public long getDpid() {
        return dpid;
    }
    
    public String getHexDpid() {
        return hexDpid;
    }
    
    @Override
    public String getBridgeDpid() {
        sendShowMessage();
        synchronized(this) {
            if (bridge.size() != 0) {
               for (OVSBridge b : bridge.values()) {
                   if (b.getNew().getName().equals("ovs-br0")) {
                       String dpidstr = b.getNew().getReportedDpidString();
                       if (dpidstr != null && !dpidstr.equals("")) {
                           //set cached values in this object
                           dpid = Long.parseLong(dpidstr, 16);
                           hexDpid = HexString.toHexString(dpid);
                       }
                       return dpidstr;
                   }
               }
            }
        }
        return null;
    }
    
    @Override
    public void setBridgeDpid(String dpidstr) { 
        if (Long.parseLong(dpidstr, 16) == dpid) {
            return;
        }
        // set locally
        dpid = Long.parseLong(dpidstr, 16);
        hexDpid = HexString.toHexString(dpid);
        // set dpid in ovs
        sendShowMessage(false);
        expectedMessageReplyId = peekNextMessageId();
        expectedMessage = SET_DPID_REPLY;
        synchronized(this) {
            JSONSetDpidMsg jsdpid;
            try {
                jsdpid = new JSONSetDpidMsg(dpidstr, this, getNextMessageId());
                if (channel != null) channel.write(jsdpid);
            } catch (OVSDBBridgeUnknown e) {
                logger.error("Couldn't set-bridge-dpid {} for sw @ {}: could" +
                             " not find ovs-br0 bridge", dpidstr, mgmtIPAddr);
            }
        }   
        //wait for an update
        synchronized(statusObj) {
            try {
                statusObj.wait(1000);
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for set bridge", e);
            }
        }
        if (logger.isDebugEnabled()) 
            logger.debug("channel closing - set bridge");
        if (channel != null) channel.close();
        resetMessageId();
    }

    //*********************
    // Controller IP related
    //*********************

    @Override
    public ArrayList<String> getControllerIPs() {
        ArrayList<String> cntrlIPAddrs = new ArrayList<String>();
        sendShowMessage();
        synchronized(this) {
            if (controller.size() != 0) {
                for (OVSController c : controller.values()) {
                    String cip = c.getNew().getTarget();
                    if (cip != null && !cip.equals("")) cntrlIPAddrs.add(cip);
                }
            }
        }
        return cntrlIPAddrs;
    }
    
    @Override
    public void setControllerIPs(ArrayList<String> cntrIP) {
        sendShowMessage(false);
        // set dpid
        expectedMessageReplyId = peekNextMessageId();
        expectedMessage = SET_CIP_REPLY;
        synchronized(this) {
            JSONSetCIPMsg jsetcip;
            try {
                jsetcip = new JSONSetCIPMsg(cntrIP, this, getNextMessageId());
                if (channel != null) channel.write(jsetcip);
            } catch (OVSDBBridgeUnknown e) {
                logger.error("Couldn't set-controller-ips for  {}: could" +
                             " not find ovs-br0 bridge", dpid);
            }
        }   
        //wait for an update
        synchronized(statusObj) {
            try {
                statusObj.wait(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (logger.isDebugEnabled()) 
            logger.debug("channel closing - set controller IP");
        if (channel != null) channel.close();
        resetMessageId();
        
    }

    //*********************
    // Tunnel IP related
    //*********************
      
    public String getTunnelIPAddress() {
        if (tunnelIPAddr != null) {
            return tunnelIPAddr;
        }
        return discoverIPAddress();
    }
    
    public String getTunnelIPAddress(boolean refresh) {
        if (refresh) {
            return discoverIPAddress();
        } else {
            return getTunnelIPAddress();
        }
    }
    
    protected String discoverIPAddress() {
        // discover IP addr from ovsdb server
        sendShowMessage();
        synchronized(this) {
            if (bridge.size() != 0) {
                //FIXME only works iff one of the bridges is tunnel endpoint
                for(OVSBridge b : bridge.values()) {
                    tunnelIPAddr = b.getNew().getTunnelIPAddress();
                    if (tunnelIPAddr != null) return tunnelIPAddr;
                }
            }
            return null;
        }
    }
    
    public String getTunnelIPAddrName() {
        if (tunnelIPAddrName != null) {
            return tunnelIPAddrName;
        }
        if (tunnelIPAddr == null) {
            return null;
        }
        // convert tunnelIPAddr to tunnelIPAddrName
        String after = tunnelIPAddr;
        String before = "";
        tunnelIPAddrName = "vta";
        
        for (int i=0; i<4; i++) {
            int index = after.indexOf('.');
            if (index == -1) {
                before = after;
            } else {
                before = after.substring(0, index);
                after = after.substring(index+1);
            }
            if (before.length() == 1) tunnelIPAddrName += "00" + before;
            else if (before.length() == 2) tunnelIPAddrName += "0"+before;
            else tunnelIPAddrName += before;
        }
        return tunnelIPAddrName;
    }
    
    public ArrayList<String> getTunnelPortNames() {
        sendShowMessage();
        ArrayList<String> tunnports = new ArrayList<String>();
        synchronized(this) {
            if (port.size() != 0) {
                for (OVSPort op : port.values()) {
                    if (op.getNew().getName().startsWith("vta")) {
                        tunnports.add(op.getNew().getName());
                    }
                }
            }
        }
        return tunnports;
    }
    
    //***************************
    // JSON RPC Message ID related
    //***************************
    
    /**
     * Transaction id for JSON RPC messages. Calling this function 
     * returns the next message Id and internally increments the counter
     * To query messageId without changing it use peekNextMessageId
     * 
     */
    private int getNextMessageId() {
        return messageid.getAndIncrement();
    }
    
    /** 
     * Transaction id for JSON RPC messages. Calling this function returns
     * the next message id, but does NOT increment the counter.
     * No message should be sent by the caller with the returned messageId
     */
    private int peekNextMessageId() {
        return messageid.get();
    }
    
    private void resetMessageId() {
        messageid.set(0);
    }
    
    
    //**************************
    // JSON Show related  
    //**************************
        
    /**
     * Start a connection to the OVS DB server.
     * 
     * Initially we try to establish an SSL connection. If it succeeds (and we
     * handshake successfully) we contiune using SSL. 
     * If *any* SSL connection attempt fails we retry with plaintext and will  
     * use plaintext from then on. 
     */
    protected Channel connect(boolean newChannelForShow) {
        // Make a new connection. For standalone show-commands a separate 
        // channel is created which wouldn't clash with other commands
        Channel showCh = null;
        if (!newChannelForShow) { 
           setChannel(null);
        }
        synchronized(ovsdbcfact) {
            ovsdbcfact.setCurSwitch(this);
            ovsdbcfact.setStatusObject(statusObj);
            ovsdbcfact.setUseSSL(true);
            if (useSSL) {
                // Try to connect via SSL
                ChannelFuture connectFuture = bootstr.connect(
                        new InetSocketAddress(mgmtIPAddr, 
                                              OVSDB_SSL_SERVER_PORT));
                connectFuture.awaitUninterruptibly();
                if (connectFuture.isDone() && connectFuture.isSuccess()) {
                    SslHandler h = connectFuture.getChannel().getPipeline()
                                        .get(SslHandler.class);
                    ChannelFuture hsFuture = h.handshake();
                    hsFuture.awaitUninterruptibly();
                    if (hsFuture.isDone() && hsFuture.isSuccess()) {
                        if (!newChannelForShow)
                            setChannel(connectFuture.getChannel());
                        else 
                            showCh = connectFuture.getChannel();
                    } else { 
                        useSSL = false;
                    }
                }
                else {  // connection failed
                    useSSL = false;
                }
                if (!useSSL) {
                    // TODO: SSL failed, fall back now
                    logger.info("OVSDB on {} <{}> does not listen for SSL " +
                            "connections. Using plain text.",
                                hexDpid, mgmtIPAddr);
                }
            }
            if (!useSSL) {
                ovsdbcfact.setUseSSL(false);
                // Now try plain connection. If the SSL connection failed we  
                // will fall through and now try the plain connection. 
                // FIXME: add option to never fall back to plain
                ChannelFuture connectFuture = bootstr.connect(
                        new InetSocketAddress(mgmtIPAddr, OVSDB_SERVER_PORT));
                connectFuture.awaitUninterruptibly();
                if (connectFuture.isDone() && connectFuture.isSuccess()) { 
                    if (!newChannelForShow)
                        setChannel(connectFuture.getChannel());
                    else
                        showCh = connectFuture.getChannel();
                }
            }  
        }
        if (channel == null && showCh == null)
            logger.error("Failed to connect to OVSDB on tunnel switch {} <{}>", 
                    hexDpid, mgmtIPAddr);
        return showCh;
    }
    
    /**
     * sendShowMessage sends a JSON RPC show command to this OVS and updates
     * the OVSDB state to mirror the OVS state learned from the reply to this
     * show message. addTunnelPort and delTunnelPort method implementations
     * first synchronize the OVSDB state with the OVS's state by calling this
     * method; and then try to add or delete a port. 
     */
    public void sendShowMessage() {
        sendShowMessage(true);
    }
    
    private void sendShowMessage(boolean closeAfterReceive) {
        Channel showCh = null;
        showCh  = connect(closeAfterReceive);
        
        expectedMessageReplyId = peekNextMessageId();
        expectedMessage = SHOW_REPLY;
        synchronized(this) {
            JSONShowMsg jshow = new JSONShowMsg(getNextMessageId()); 
            if (closeAfterReceive) {
                if (showCh != null) showCh.write(jshow);
                else logger.error("Show failed on connect to ovs {}", hexDpid);
            } else {
                if (channel != null) channel.write(jshow);
                else logger.error("Show failed to connect to ovs {}", hexDpid);
            }
        }
        
        //wait for a reply
        synchronized(statusObj) {
            try {
                statusObj.wait(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        if (closeAfterReceive) {
            if (showCh != null) {
                if (logger.isDebugEnabled())
                    logger.debug("closing channel {} after show-only",
                                    showCh.toString());
                showCh.close();
            } else {
                logger.error("Failed to connect to ovsdb on switch {}", 
                        hexDpid);
            }
            resetMessageId();
        }
        
    }
    
    
    //**************************
    // JSON Add/Del Port related  
    //**************************
    
    /**
     * addPort adds a regular port or a CAPWAP tunnel port on this OVS with 
     * name "name". If "isTunnelPort" is true then the port is a CAPWAP tunnel 
     * port with remote IP address "remoteIPAddr". If such a tunnel already 
     * exists this method will have no effect. 
     * Example: name =  "vta192168002003"; remoteIPAddr = "192.168.2.3";
     * If "isTunnelPort" is false, then "remoteIPAddr" is ignored and can be
     * any string or null.
     */
    public void addPort(String name, String localIPAddr, String remoteIPAddr, 
                        boolean isTunnelPort) {
        if ((localIPAddr == null || remoteIPAddr == null) && isTunnelPort) {
            logger.debug("Error in call: cannot add a tunnel-port on switch " +
                    hexDpid + " local IP " + localIPAddr +
                    " remote IP " + remoteIPAddr);
            return;
        }
        
        //Make a new connection with the show message
        sendShowMessage(false);
        //ensure port does not exist
        boolean exists = false;
        for(OVSPort p : port.values()) {
            if (p.getNew().getName().equals(name)) {
                exists = true;
                break;
            }
        }
        if (exists) {
            logger.debug("port {} already exists on switch {}",
                    name, hexDpid);
            if (channel != null) channel.close();
            resetMessageId();
            return;
        }
        
        //otherwise create port
        expectedMessageReplyId = peekNextMessageId();
        expectedMessage = ADD_PORT_REPLY;
        synchronized(this) {
            JSONAddPortMsg jadd;
            try {
                jadd = new JSONAddPortMsg(name, localIPAddr, remoteIPAddr, this,
                        getNextMessageId(), isTunnelPort);
                if (channel != null) channel.write(jadd);
            } catch (OVSDBBridgeUnknown e) {
                logger.error(String.format(
                        "Couldn't add port %s for sw %s with remote IP %s ::"+
                                " no bridge found", name, hexDpid, 
                                remoteIPAddr));
            }
        }
        //wait for a reply and an update
        for (int i=0; i<2; i++) {
            synchronized(statusObj) {
                try {
                    statusObj.wait(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        if (logger.isDebugEnabled()) 
            logger.debug("channel {} closing - add port", channel.toString());
        if (channel != null) channel.close();
        resetMessageId();

    }

    /**
     * delPort deletes a port on this OVS with the given name, where the port 
     * can be a tunnel-port where name is of the form "vta192168002003"; or
     * a regular port where name could be of the form "eth2.vlan20" (or not).
     * If such a port does not exist on the OVS, this method will have no 
     * effect. 
     */ 
    public void delPort(String name) {
        //Make a new connection with the show message
        sendShowMessage(false);
        // ensure port exists
        Iterator<Entry<String, OVSPort>> iter = port.entrySet().iterator();
        String portHash = null;
        while(iter.hasNext()) {
            Entry<String, OVSPort> e = iter.next();
            ArrayList<String> intfHash = e.getValue().getNew().getInterfaces();
            for (int j=0; j<intfHash.size(); j++) {
                if (intf.containsKey(intfHash.get(j))) {
                    if (intf.get(intfHash.get(j)).getNew().getName()
                            .equals(name)) {
                        portHash = e.getKey();
                        break;
                    }
                }
            }
            if (portHash != null) break;
        }
        if (portHash == null) {
            logger.debug("port {} does not exist on switch {}",
                    name, HexString.toHexString(dpid));
            if (channel != null) channel.close();
            resetMessageId();
            return;
        }
        //otherwise delete port
        expectedMessageReplyId = peekNextMessageId();
        expectedMessage = DEL_PORT_REPLY;
        synchronized(this) {
            JSONDelPortMsg jdel;
            try {
                jdel = new JSONDelPortMsg(name, portHash, this,
                        getNextMessageId());
                if (channel != null) channel.write(jdel);
            } catch (OVSDBBridgeUnknown e) {
                logger.error(String.format(
                        "Couldn't del port %s for switch %s ::"+
                                " no bridge found", name, hexDpid));
            }
            
        }
        //wait for a reply and an update
        for (int i=0; i<2; i++) {
            synchronized(statusObj) {
                try {
                    statusObj.wait(1000);
                } catch (InterruptedException e) {
                    // ingore
                }
            }
        }
        if (logger.isDebugEnabled()) 
            logger.debug("channel {} closing - del port", channel.toString());
        if (channel != null) channel.close();
        resetMessageId();
        
    }

    //**************************
    // JSON RPC Message 'from' OVS 
    //**************************

    /**
     * public method not exposed by interface - called by JSONMsgHandler
     * to correlate a received JSON RPC transaction id to the id of a sent 
     * JSON RPC request.
     */
    public int getExpectedMessage(int messageReplyId) {
        if (messageReplyId == expectedMessageReplyId) {
            return expectedMessage;
        } else {
            return -1;
        }
    }
    
    /**
     * public method not exposed by interface - called by JSONMsgHandler
     * to update OVSDB with a showReply received in response to a sent showMsg
     */
    public synchronized void updateTunnelSwitchFromShow(
            JSONShowReplyMsg showReply) {
        ShowResult sr = showReply.getResult(); 
        if(sr.getError() != null){
            logger.error("Received error from tunnesw:{} of type {}", dpid,
                    sr.getDetails());
            return;
        }

        if(!sr.getOpen_vSwitch().toString().equals(open_vswitch.toString())) {
            open_vswitch = sr.getOpen_vSwitch();
        }
        if(!sr.getController().toString().equals(controller.toString())) {
            controller = sr.getController();
        }
        if(!sr.getInterface().toString().equals(intf.toString())) {
            intf = sr.getInterface();
        }
        if(!sr.getPort().toString().equals(port.toString())) {
            port = sr.getPort();
        }
        if(!sr.getBridge().toString().equals(bridge.toString())) {
            bridge = sr.getBridge();
        }
        
    }
    
    public synchronized void updateTunnelSwitchFromUpdate(
            ShowResult update) {
        // FIXME - not needed or used right now
        
    }

  
   
}
