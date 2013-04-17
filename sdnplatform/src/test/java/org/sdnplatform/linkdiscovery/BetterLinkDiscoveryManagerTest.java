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

package org.sdnplatform.linkdiscovery;

import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.linkdiscovery.BetterLinkDiscoveryManager;
import org.sdnplatform.linkdiscovery.ILinkDiscovery;
import org.sdnplatform.linkdiscovery.LinkInfo;
import org.sdnplatform.routing.Link;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;



public class BetterLinkDiscoveryManagerTest extends PlatformTestCase{

    BetterLinkDiscoveryManager bldm;
    IOFSwitch sw1, sw2;
    OFPhysicalPort p1, p2;
    Link lt;
    LinkInfo info;
    ITunnelManagerService tunnelManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        ModuleContext cntx = new ModuleContext();
        MockThreadPoolService tp = new MockThreadPoolService();
        tunnelManager = createMock(ITunnelManagerService.class);
        cntx.addService(ITunnelManagerService.class, tunnelManager);
        cntx.addService(IControllerService.class, getMockControllerProvider());
        cntx.addService(IThreadPoolService.class, tp);
        cntx.addService(IStorageSourceService.class, new MemoryStorageSource());

        bldm = new BetterLinkDiscoveryManager();
        bldm.init(cntx);
        tp.init(cntx);
        bldm.startUp(cntx);
        tp.startUp(cntx);

        sw1 = createNiceMock(IOFSwitch.class);
        sw2 = createNiceMock(IOFSwitch.class);

        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw2.getId()).andReturn(2L).anyTimes();

        p1 = new OFPhysicalPort();
        p2 = new OFPhysicalPort();
        expect(sw1.getPort((short)1)).andReturn(p1).anyTimes();
        expect(sw2.getPort((short)1)).andReturn(p2).anyTimes();
        lt = new Link(1L, (short)1, 2L, (short)1);
        info = new LinkInfo(System.currentTimeMillis(),
                            System.currentTimeMillis(), null, 0, 0);

        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        getMockControllerProvider().setSwitches(switches);

        replay(sw1, sw2);

    }

    /**
     * Test to verify if the tunnel link classification works fine or not.
     * It is sufficient to specify the tunnelManager expect methods for
     * switch 1 due to the short-circuiting nature of the if () statement
     * in the getLinkType() method.
     */
    @Test
    public void testLinkTypeTunnel() {
        expect(tunnelManager.getTunnelPortNumber(1L)).andReturn(new Short((short)1)).once();
        replay(tunnelManager);
        assertTrue(bldm.getLinkType(lt, info) == ILinkDiscovery.LinkType.TUNNEL);
        verify(tunnelManager);

        reset(tunnelManager);
        expect(tunnelManager.getTunnelPortNumber(1L)).andReturn(null);
        expect(tunnelManager.getTunnelPortNumber(2L)).andReturn(null);
        replay(tunnelManager);
        assertTrue(bldm.getLinkType(lt, info) == ILinkDiscovery.LinkType.DIRECT_LINK);
        verify(tunnelManager);
    }

    /**
     * In this case, autoportfast is disabled; and autoneg is ON.
     * @throws Exception
     */
    @Test
    public void testSwitchAddedCase1() throws Exception {
        BetterLinkDiscoveryManager linkDiscovery = bldm;
        Capture<OFMessage> wc;
        Capture<ListenerContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in controllerProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockControllerProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=10; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<ListenerContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(false).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();
        expect(sw1.getInetAddress()).andReturn(null);

        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();

        replay(sw1, tunnelManager);

        // Set autoportfast feature to false
        linkDiscovery.setAutoPortFastFeature(false);
        // set the port autoneg feature to ON.
        p1.setCurrentFeatures(OFPortFeatures.OFPPF_AUTONEG.getValue());

        linkDiscovery.addedSwitch(sw1);
        verify(sw1, tunnelManager);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }

    /**
     * In this case, autoportfast is enabled; and autoneg is ON.
     * @throws Exception
     */
    @Test
    public void testSwitchAddedCase2() throws Exception {
        BetterLinkDiscoveryManager linkDiscovery = bldm;
        Capture<OFMessage> wc;
        Capture<ListenerContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in controllerProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockControllerProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=10; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<ListenerContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(false).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();
        expect(sw1.getInetAddress()).andReturn(null);

        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();

        replay(sw1, tunnelManager);

        // Set autoportfast feature to true
        linkDiscovery.setAutoPortFastFeature(true);
        // set the port autoneg feature to ON.
        p1.setCurrentFeatures(OFPortFeatures.OFPPF_AUTONEG.getValue());

        linkDiscovery.addedSwitch(sw1);
        verify(sw1, tunnelManager);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }


    /**
     * In this case, autoportfast is disabled; and autoneg is OFF.
     * @throws Exception
     */
    @Test
    public void testSwitchAddedCase3() throws Exception {
        BetterLinkDiscoveryManager linkDiscovery = bldm;
        Capture<OFMessage> wc;
        Capture<ListenerContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in controllerProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockControllerProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=10; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<ListenerContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        // Fast ports
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(true).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();
        expect(sw1.getInetAddress()).andReturn(null);

        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null).anyTimes();

        replay(sw1, tunnelManager);

        // Set autoportfast feature to false
        linkDiscovery.setAutoPortFastFeature(false);

        linkDiscovery.addedSwitch(sw1);
        verify(sw1, tunnelManager);

        // Since all ports are fast ports, none of them are quarantined.
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        Thread.sleep(300);

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }

    /**
     * In this case, autoportfast is disabled; and autoneg is OFF.
     * @throws Exception
     */
    @Test
    public void testSwitchAddedCase4() throws Exception {
        BetterLinkDiscoveryManager linkDiscovery = bldm;
        Capture<OFMessage> wc;
        Capture<ListenerContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        // set the port autoneg feature to OFF, thus fast port.
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in controllerProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockControllerProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=15; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<ListenerContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        // fast ports on
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(true).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();
        expect(sw1.getInetAddress()).andReturn(null);
        replay(sw1);

        // Set autoportfast feature to true
        linkDiscovery.setAutoPortFastFeature(true);

        linkDiscovery.addedSwitch(sw1);
        verify(sw1);

        // Since all ports are fast ports, none of them are quarantined.
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Since autoportfast feature is on and all ports are fastports,
        // no LLDP or BDDP is sent out.
        assertFalse(wc.hasCaptured());
    }


    /**
     * Ensure that LLDPs are not sent through the tunnel ports.
     * @throws Exception
     */
    @Test
    public void testNoDiscoveryOnTunnelPorts() throws Exception {
        BetterLinkDiscoveryManager linkDiscovery = bldm;
        Capture<OFMessage> wc;
        Capture<ListenerContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in controllerProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockControllerProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=10; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<ListenerContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(false).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();
        expect(sw1.getInetAddress()).andReturn(null);

        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(new Short((short)1)).anyTimes();

        replay(sw1, tunnelManager);

        linkDiscovery.addedSwitch(sw1);
        verify(sw1);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();

        // Port #1 will not get LLDP as it is a tunnel port.
        assertTrue(msgList.size() == (ports.size() - 1) * 2);
    }

}
