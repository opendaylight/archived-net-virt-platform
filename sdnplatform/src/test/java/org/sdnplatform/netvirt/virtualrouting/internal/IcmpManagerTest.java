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

package org.sdnplatform.netvirt.virtualrouting.internal;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IListener.Command;
import org.sdnplatform.netvirt.virtualrouting.IICMPListener;
import org.sdnplatform.netvirt.virtualrouting.IICMPListener.ICMPCommand;
import org.sdnplatform.netvirt.virtualrouting.internal.IcmpManager;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.routing.RoutingDecision;
import org.sdnplatform.test.PlatformTestCase;


public class IcmpManagerTest extends PlatformTestCase{
    private IcmpManager icmpManager;

    protected static OFPacketIn packetInICMPRequest;
    protected static IPacket icmpRequestPacket;
    protected static byte[] icmpRequestSerialized;
    protected static OFPacketIn packetInICMPReply;
    protected static IPacket icmpReplyPacket;
    protected static byte[] icmpReplySerialized;
    protected static OFPacketIn packetInNonICMP;
    protected static IPacket nonIcmpPacket;
    protected static byte[] nonIcmpSerialized;

    static {
        icmpRequestPacket = new Ethernet()
        .setEtherType(Ethernet.TYPE_IPv4)
        .setSourceMACAddress("00:00:00:00:00:22")
        .setDestinationMACAddress("00:00:00:00:00:11")
        .setPayload(
                new IPv4()
                .setSourceAddress("10.0.1.3")
                .setDestinationAddress("10.0.1.1")
                .setTtl((byte) 64)
                .setProtocol(IPv4.PROTOCOL_ICMP)
                .setPayload(
                        new ICMP()
                        .setIcmpType(ICMP.ECHO_REQUEST)
                        .setIcmpCode((byte) 0)));
        icmpRequestSerialized = icmpRequestPacket.serialize();

        icmpReplyPacket = new Ethernet()
        .setEtherType(Ethernet.TYPE_IPv4)
        .setSourceMACAddress("00:00:00:00:00:22")
        .setDestinationMACAddress("00:00:00:00:00:11")
        .setPayload(
                new IPv4()
                .setSourceAddress("10.0.1.3")
                .setDestinationAddress("10.0.1.1")
                .setTtl((byte) 64)
                .setProtocol(IPv4.PROTOCOL_ICMP)
                .setPayload(
                        new ICMP()
                        .setIcmpType(ICMP.ECHO_REPLY)
                        .setIcmpCode((byte) 0)));
        icmpReplySerialized = icmpReplyPacket.serialize();

        nonIcmpPacket = new Ethernet()
        .setEtherType(Ethernet.TYPE_IPv4)
        .setSourceMACAddress("00:00:00:00:00:22")
        .setDestinationMACAddress("00:00:00:00:00:11")
        .setPayload(
                new IPv4()
                .setSourceAddress("10.0.1.3")
                .setDestinationAddress("10.0.1.1")
                .setTtl((byte) 64)
                .setProtocol(IPv4.PROTOCOL_TCP));
        nonIcmpSerialized = nonIcmpPacket.serialize();

        packetInICMPRequest =
                ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(icmpRequestSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) icmpRequestSerialized.length);

        packetInICMPReply =
                ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(icmpReplySerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) icmpReplySerialized.length);

        packetInNonICMP =
                ((OFPacketIn) (new BasicFactory()).getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(nonIcmpSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) nonIcmpSerialized.length);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        icmpManager = new IcmpManager();
    }

    @Test
    public void testICMPRequest() throws Exception {
        IcmpManager im = getIcmpManager();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        replay(mockSwitch);

        ListenerContext bc = new ListenerContext();
        IControllerService.bcStore.put(bc,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet) icmpRequestPacket);

        IICMPListener il = createMock(IICMPListener.class);
        expect(il.getName()).andReturn("mockICMPListener").anyTimes();
        expect(il.ICMPRequestHandler(mockSwitch, packetInICMPRequest, bc))
        .andReturn(ICMPCommand.STOP)
        .times(1);
        replay(il);

        im.addIcmpListener(il);
        Command cmd = im.receive(mockSwitch, packetInICMPRequest, bc);

        RoutingDecision vrd = (RoutingDecision) IRoutingDecision.rtStore.get(bc,
                IRoutingDecision.CONTEXT_DECISION);
        assertEquals(vrd.getRoutingAction(),
                     IRoutingDecision.RoutingAction.NONE);
        assertEquals(cmd, Command.STOP);
        verify(mockSwitch, il);
    }

    @Test
    public void testICMPReply() throws Exception {
        IcmpManager im = getIcmpManager();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        replay(mockSwitch);

        ListenerContext bc = new ListenerContext();
        IControllerService.bcStore.put(bc,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet) icmpReplyPacket);

        IICMPListener il = createMock(IICMPListener.class);
        expect(il.getName()).andReturn("mockICMPListener").anyTimes();
        expect(il.ICMPReplyHandler(mockSwitch, packetInICMPReply, bc))
        .andReturn(ICMPCommand.CONTINUE)
        .times(1);
        replay(il);

        im.addIcmpListener(il);
        Command cmd = im.receive(mockSwitch, packetInICMPReply, bc);
        assertEquals(cmd, Command.CONTINUE);
        verify(mockSwitch, il);
    }

    @Test
    public void testMultipleListenersStop() throws Exception {
        IcmpManager im = getIcmpManager();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        replay(mockSwitch);

        ListenerContext bc = new ListenerContext();
        IControllerService.bcStore.put(bc,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet) icmpReplyPacket);

        IICMPListener il1 = createMock(IICMPListener.class);
        expect(il1.getName()).andReturn("mockICMPListener1").anyTimes();
        expect(il1.ICMPReplyHandler(mockSwitch, packetInICMPReply, bc))
        .andReturn(ICMPCommand.CONTINUE)
        .times(1);
        replay(il1);

        IICMPListener il2 = createMock(IICMPListener.class);
        expect(il2.getName()).andReturn("mockICMPListener2").anyTimes();
        expect(il2.ICMPReplyHandler(mockSwitch, packetInICMPReply, bc))
        .andReturn(ICMPCommand.STOP)
        .times(1);
        replay(il2);

        IICMPListener il3 = createMock(IICMPListener.class);
        expect(il3.getName()).andReturn("mockICMPListener3").anyTimes();
        replay(il3);

        im.addIcmpListener(il1);
        im.addIcmpListener(il2);
        im.addIcmpListener(il3);
        Command cmd = im.receive(mockSwitch, packetInICMPReply, bc);

        RoutingDecision vrd = (RoutingDecision) IRoutingDecision.rtStore.get(bc,
                IRoutingDecision.CONTEXT_DECISION);
        assertEquals(vrd.getRoutingAction(),
                     IRoutingDecision.RoutingAction.NONE);
        assertEquals(cmd, Command.STOP);
        verify(mockSwitch, il1, il2, il3);
    }

    @Test
    public void testMultipleListenersContinue() throws Exception {
        IcmpManager im = getIcmpManager();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        replay(mockSwitch);

        ListenerContext bc = new ListenerContext();
        IControllerService.bcStore.put(bc,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet) icmpRequestPacket);

        IICMPListener il1 = createMock(IICMPListener.class);
        expect(il1.getName()).andReturn("mockICMPListener1").anyTimes();
        expect(il1.ICMPRequestHandler(mockSwitch, packetInICMPRequest, bc))
        .andReturn(ICMPCommand.CONTINUE)
        .times(1);
        replay(il1);

        IICMPListener il2 = createMock(IICMPListener.class);
        expect(il2.getName()).andReturn("mockICMPListener2").anyTimes();
        expect(il2.ICMPRequestHandler(mockSwitch, packetInICMPRequest, bc))
        .andReturn(ICMPCommand.CONTINUE)
        .times(1);
        replay(il2);

        IICMPListener il3 = createMock(IICMPListener.class);
        expect(il3.getName()).andReturn("mockICMPListener3").anyTimes();
        expect(il3.ICMPRequestHandler(mockSwitch, packetInICMPRequest, bc))
        .andReturn(ICMPCommand.CONTINUE)
        .times(1);
        replay(il3);

        im.addIcmpListener(il1);
        im.addIcmpListener(il2);
        im.addIcmpListener(il3);
        Command cmd = im.receive(mockSwitch, packetInICMPRequest, bc);
        assertEquals(cmd, Command.CONTINUE);
        verify(mockSwitch, il1, il2, il3);
    }

    @Test
    public void testNonICMP() throws Exception {
        IcmpManager im = getIcmpManager();

        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        replay(mockSwitch);

        ListenerContext bc = new ListenerContext();
        IControllerService.bcStore.put(bc,
                IControllerService.CONTEXT_PI_PAYLOAD,
                (Ethernet) nonIcmpPacket);

        IICMPListener il1 = createMock(IICMPListener.class);
        expect(il1.getName()).andReturn("mockICMPListener1").anyTimes();
        replay(il1);

        IICMPListener il2 = createMock(IICMPListener.class);
        expect(il2.getName()).andReturn("mockICMPListener2").anyTimes();
        replay(il2);

        IICMPListener il3 = createMock(IICMPListener.class);
        expect(il3.getName()).andReturn("mockICMPListener3").anyTimes();
        replay(il3);

        im.addIcmpListener(il1);
        im.addIcmpListener(il2);
        im.addIcmpListener(il3);
        Command cmd = im.receive(mockSwitch, packetInNonICMP, bc);
        assertEquals(cmd, Command.CONTINUE);
        verify(mockSwitch, il1, il2, il3);
    }

    protected IcmpManager getIcmpManager() {
        return icmpManager;
    }
}
