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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.ovsdb.IOVSDB;
import org.sdnplatform.ovsdb.internal.JSONAddPortMsg;
import org.sdnplatform.ovsdb.internal.JSONDelPortMsg;
import org.sdnplatform.ovsdb.internal.JSONShowMsg;
import org.sdnplatform.ovsdb.internal.JSONShowReplyMsg;
import org.sdnplatform.ovsdb.internal.OVSDBBridgeUnknown;
import org.sdnplatform.ovsdb.internal.OVSDBClientPipelineFactory;
import org.sdnplatform.ovsdb.internal.OVSDBImpl;
import org.sdnplatform.test.PlatformTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class OVSDBImplTest extends PlatformTestCase {
    protected static Logger logger = 
        LoggerFactory.getLogger(OVSDBImplTest.class);
    private NioServerSocketChannelFactory sfactory;
    private NioClientSocketChannelFactory cfactory;
    private OVSDBClientPipelineFactory clientpipefact;
    private ClientBootstrap bootstr;
    private Channel clientChannel;
    private int testid=0, msgid=0;
    static final ChannelGroup allChannels = 
        new DefaultChannelGroup("ovsdbserver");
    IOVSDB dbsw1 = createNiceMock(IOVSDB.class);
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        //ovsdb server setup
        setUpOVSDBServer();
        //ovsdb client setup
        setUpOVSDBClient();
    }
    
    public void setUpOVSDBServer() {
        sfactory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());
        
        ServerBootstrap bootstrap = new ServerBootstrap(sfactory);
        OVSDBServerPipelineFactory serverpipefact = 
            new OVSDBServerPipelineFactory(this);
        bootstrap.setPipelineFactory(serverpipefact);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
            
        Channel channel = bootstrap.bind(new InetSocketAddress(6635));
        allChannels.add(channel);
        
    }
    
    public void setUpOVSDBClient() {
        cfactory = new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());
        bootstr = new ClientBootstrap(cfactory);
        //logger.info("in client setup");
        clientpipefact = new OVSDBClientPipelineFactory();
        clientpipefact.setUseSSL(false);
        bootstr.setPipelineFactory(clientpipefact);
        bootstr.setOption("reuseAddr", true);
        bootstr.setOption("child.keepAlive", true);
        bootstr.setOption("child.tcpNoDelay", true);
    }
    
    public void shutdownOVSDBServer() {
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();
        sfactory.releaseExternalResources();
        
    }
    
    private  void connectClient(IOVSDB sw) {
        Object statusObj = new Object();
        clientpipefact.setCurSwitch(sw);
        clientpipefact.setStatusObject(statusObj);
        ChannelFuture connectFuture = bootstr.connect(
                new InetSocketAddress("localhost", 6635));
        clientChannel = connectFuture.awaitUninterruptibly().getChannel();
    }
    
    @Test
    public void testGarbageShowMessageReply() throws InterruptedException {
        for (int i = 0; i< 10; i++ ){
            connectClient(dbsw1);
            //expect no calls to the mock object
            replay(dbsw1);
            JSONShowMsg jshow = new JSONShowMsg(msgid); 
            clientChannel.write(jshow);
            waitForClientReceive();
            verify(dbsw1);
            clientChannel.close().awaitUninterruptibly();
            msgid++; testid++;
            reset(dbsw1);
        }
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
   
    @Test
    public void testValidShowMessageReply() throws InterruptedException {
        msgid = 10; testid = 10;
        for (int i = 0; i< 2; i++ ){
            connectClient(dbsw1);
            expect(dbsw1.getExpectedMessage(msgid)).andReturn(0);
            dbsw1.updateTunnelSwitchFromShow(isA(JSONShowReplyMsg.class));
            expectLastCall().atLeastOnce();
            replay(dbsw1);
            JSONShowMsg jshow = new JSONShowMsg(msgid); 
            clientChannel.write(jshow);
            waitForClientReceive();
            verify(dbsw1);
            
            clientChannel.close().awaitUninterruptibly();
            msgid++; testid++;
            reset(dbsw1);
        }
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
    
    @Test
    public void testAddPortMessageReply() throws InterruptedException,
                OVSDBBridgeUnknown {
        msgid = 12; testid = 12;
        OVSDBImpl ovs = new OVSDBImpl(24L, "localhost", clientpipefact,
                bootstr, new Object());
        ovs.getTunnelIPAddress();
        
        msgid = 13; testid = 13;
        for (int i = 0; i< 4; i++ ){
            connectClient(dbsw1);
            if (msgid != 16)
                expect(dbsw1.getExpectedMessage(msgid)).andReturn(1);
            replay(dbsw1);
            JSONAddPortMsg jadd = new JSONAddPortMsg("vta010010001001", 
                    "0.0.0.0", "10.10.1.1", ovs, msgid, true); 
            clientChannel.write(jadd);
            waitForClientReceive();
            verify(dbsw1);
            clientChannel.close().awaitUninterruptibly();
            msgid++; testid++;
            reset(dbsw1);
        }
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
    
    @Test
    public void testDelPortMessageReply() throws InterruptedException, 
                OVSDBBridgeUnknown {
        msgid = 12; testid = 12;
        OVSDBImpl ovs = new OVSDBImpl(24L, "localhost", clientpipefact,
                bootstr, new Object());
        ovs.getTunnelIPAddress();

        msgid = 17; testid = 17;
        for (int i = 0; i< 4; i++ ){
            connectClient(dbsw1);
            if (msgid != 20)
                expect(dbsw1.getExpectedMessage(msgid)).andReturn(2);
            replay(dbsw1);
            JSONDelPortMsg jshow = new JSONDelPortMsg("vta010010001001", 
                    "355b1c92-f0d8-44f2-bfbb-9e65feb0ac05", ovs, msgid); 
            clientChannel.write(jshow);
            waitForClientReceive();
            verify(dbsw1);
            clientChannel.close().awaitUninterruptibly();
            msgid++; testid++;
            reset(dbsw1);
        }
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
    
    public void testAddNonTunnelPortMessageReply() throws InterruptedException,
            OVSDBBridgeUnknown {
        msgid = 12; testid = 12;
        OVSDBImpl ovs = new OVSDBImpl(24L, "localhost", clientpipefact,
                bootstr, new Object());
        ovs.getTunnelIPAddress();

        
        msgid = 21; testid = 21;
        for (int i = 0; i< 1; i++ ){
            connectClient(dbsw1);
            expect(dbsw1.getExpectedMessage(msgid)).andReturn(1);
            replay(dbsw1);
            JSONAddPortMsg jadd = new JSONAddPortMsg("eth2.vlan2000", 
                    null, null, ovs, msgid, true); 
            clientChannel.write(jadd);
            waitForClientReceive();
            verify(dbsw1);
            clientChannel.close().awaitUninterruptibly();
            msgid++; testid++;
            reset(dbsw1);
        }
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
    
    public void testGetNSetBridgeDpid() throws InterruptedException, 
        OVSDBBridgeUnknown {
        msgid = 22; testid = 22;
        OVSDBImpl ovs = new OVSDBImpl(-1, "localhost", clientpipefact,
                bootstr, new Object());
        assertTrue (ovs.getBridgeDpid() == "");

        msgid = 23; testid = 23;
        OVSDBImpl ovs1 = new OVSDBImpl(-1, "localhost", clientpipefact,
                bootstr, new Object());
        assertTrue (ovs1.getBridgeDpid() == "");

        msgid = 24; testid = 24;
        OVSDBImpl ovs2 = new OVSDBImpl(-1, "localhost", clientpipefact,
                bootstr, new Object());
        assertTrue (ovs2.getBridgeDpid().equals("0000000033333334"));
        
        msgid = 24; testid = 24;
        OVSDBImpl ovs3 = new OVSDBImpl(-1, "localhost", clientpipefact,
                bootstr, new Object());
        ovs3.setBridgeDpid("0000000000000003");
        assertTrue (ovs3.getDpid() == (3L));
                
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
    
    public void testGetNSetControllerIP() throws InterruptedException,
    OVSDBBridgeUnknown {
        msgid = 22; testid = 22;
        OVSDBImpl ovs = new OVSDBImpl(42L, "localhost", clientpipefact,
                bootstr, new Object());
        ArrayList<String> retval = ovs.getControllerIPs(); 
        assertTrue (retval.get(0).equals("tcp:192.168.247.132"));
        
        msgid = 23; testid = 23;
        OVSDBImpl ovs2 = new OVSDBImpl(42L, "localhost", clientpipefact,
                bootstr, new Object());
        ArrayList<String> retval2 = ovs2.getControllerIPs(); 
        assertTrue (retval2.get(0).equals("tcp:192.168.247.132"));
        assertTrue (retval2.get(1).equals("tcp:192.168.204.111"));
        
        msgid = 24; testid = 24;
        OVSDBImpl ovs3 = new OVSDBImpl(42L, "localhost", clientpipefact,
                bootstr, new Object());
        ArrayList<String> retval3 = ovs3.getControllerIPs(); 
        assertTrue(retval3.size() == 0);
        
        msgid = 24; testid = 24;
        OVSDBImpl ovs4 = new OVSDBImpl(-1, "localhost", clientpipefact,
                bootstr, new Object());
        ArrayList<String> newips = new ArrayList<String>();
        newips.add("tcp:10.100.1.1");
        ovs4.setControllerIPs(newips);
        
        cfactory.releaseExternalResources();
        shutdownOVSDBServer();
    }
    
    private void waitForClientReceive() throws InterruptedException {
        // wait for client to receive server's response
        // before closing channel
        Thread.sleep(50);
    }

    public int getCurrentTestID() {
        return testid;
    }

}
