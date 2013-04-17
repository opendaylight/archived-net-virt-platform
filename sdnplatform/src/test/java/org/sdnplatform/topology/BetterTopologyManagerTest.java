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

package org.sdnplatform.topology;

import static org.easymock.EasyMock.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.linkdiscovery.BetterLinkDiscoveryManager;
import org.sdnplatform.linkdiscovery.ILinkDiscovery;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.LDUpdate;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.UpdateOperation;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.BetterTopologyManager;
import org.sdnplatform.topology.OrderedNodePair;
import org.sdnplatform.topology.TunnelEvent;
import org.sdnplatform.topology.TunnelEvent.TunnelLinkStatus;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class BetterTopologyManagerTest extends PlatformTestCase {

    protected static Logger log = LoggerFactory.getLogger(TopologyManagerTest.class);
    protected BetterTopologyManager tm;
    protected ModuleContext fmc;
    protected BetterLinkDiscoveryManager ldm;
    protected ILinkDiscoveryService linkDiscoveryService;
    protected ITunnelManagerService tunnelService;
    protected IRestApiService restApiService;

    private IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        return mockSwitch;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        fmc = new ModuleContext();
        ldm = new BetterLinkDiscoveryManager();
        tm  = new BetterTopologyManager();
        tunnelService = createMock(ITunnelManagerService.class);
        restApiService = createMock(IRestApiService.class);


        fmc.addService(IControllerService.class, getMockControllerProvider());
        fmc.addService(ILinkDiscoveryService.class, ldm);
        fmc.addService(ITunnelManagerService.class, tunnelService);
        fmc.addService(IRestApiService.class, restApiService);
        MockThreadPoolService tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);

        // Set the timers shorter for faster completion of unit tests.
        tm.setTopologyComputeInterval(30);
        tm.setTunnelDetectionTimeout(90);
        tm.setTunnelVerificationTimeout(90);

        ldm.init(fmc);
        tp.init(fmc);
        tm.init(fmc);
        ldm.startUp(fmc);
        tp.startUp(fmc);
        tm.startUp(fmc);

        // Create two physical ports.
        OFPhysicalPort p1 = new OFPhysicalPort();
        p1.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1.setCurrentFeatures(0);
        OFPhysicalPort p2 = new OFPhysicalPort();
        p2.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:02"));
        p2.setCurrentFeatures(0);

        // Create mock switches.
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        getMockControllerProvider().setSwitches(switches);

        Set<Short> ports = new HashSet<Short>();
        ports.add((short)1);

        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw2.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw2.getPort(EasyMock.anyShort())).andReturn(p2).anyTimes();
        replay(sw1, sw2);

        // Set tunnel manager expectations.  Switches 1 and 2 contain
        // one tunnel port, the port number is 1.
        expect(tunnelService.getTunnelPortNumber(EasyMock.anyLong())).andReturn(new Short((short)1)).anyTimes();
        expect(tunnelService.getTunnelIPAddr(2L)).andReturn(1).anyTimes();
        expect(tunnelService.isTunnelActiveByDpid(EasyMock.anyLong())).andReturn(true).anyTimes();
        replay(tunnelService);
    }


    /**
     * This test ensures that when a tunnel event is removed properly from
     * the tunnel detection queue.
     * @throws Exception
     */
    @Test
    public void testDetectionCorrectness() throws Exception {

        BlockingQueue<OrderedNodePair> dqueue;
        tm.detectTunnelSource(1L, 2L);

        dqueue = tm.getTunnelDetectionQueue();

        assertTrue(dqueue.contains(new OrderedNodePair(1L, 2L)));
        tm.detectTunnelDestination(1L, 2L);
        assertTrue(dqueue.isEmpty());
    }

    /**
     * This test ensures that when a tunnel event is removed properly from
     * the tunnel verification queue.
     * @throws Exception
     */
    @Test
    public void testTunnelVerificationCorrectness() throws Exception {

        BlockingQueue<OrderedNodePair> dqueue, vqueue;
        tm.detectTunnelSource(1L, 2L);

        dqueue = tm.getTunnelDetectionQueue();
        vqueue = tm.getTunnelVerificationQueue();

        assertTrue(dqueue.contains(new OrderedNodePair(1L, 2L)));

        Thread.sleep(tm.TUNNEL_DETECTION_TIMEOUT_MS+ tm.getTopologyComputeInterval());
        assertTrue(dqueue.isEmpty());
        assertTrue(vqueue.contains(new OrderedNodePair(1L, 2L)));

        // This addOrUpdateTunnelLink is generated by LinkDiscoveryManager.
        LDUpdate update = new LDUpdate(1L, (short)1, 2L, (short)1,
                                       ILinkDiscovery.LinkType.TUNNEL,
                                       UpdateOperation.LINK_UPDATED);
        tm.linkDiscoveryUpdate(update);
        Thread.sleep(tm.getTopologyComputeInterval());
        assertTrue(vqueue.isEmpty());
    }


    /**
     * This test ensures that the entire sequence of tunnel liveness detection
     * goes through all the three queues.
     * @throws Exception
     */
    @Test
    public void testDetectFailureSequence() throws Exception {

        BlockingQueue<OrderedNodePair> dqueue, vqueue;
        List<TunnelEvent> statusList;
        tm.detectTunnelSource(1L, 2L);

        dqueue = tm.getTunnelDetectionQueue();
        vqueue = tm.getTunnelVerificationQueue();

        assertTrue(dqueue.contains(new OrderedNodePair(1L, 2L)));

        Thread.sleep(tm.TUNNEL_DETECTION_TIMEOUT_MS+ tm.getTopologyComputeInterval());
        assertTrue(dqueue.isEmpty());
        assertTrue(vqueue.contains(new OrderedNodePair(1L, 2L)));

        Thread.sleep(tm.TUNNEL_VERIFICATION_TIMEOUT_MS+tm.getTopologyComputeInterval());
        statusList = tm.getTunnelLivenessState();
        assertTrue(vqueue.isEmpty());
        assertTrue(statusList.size() == 1);
        assertTrue(statusList.get(0).getSrcDPID() == 1);
        assertTrue(statusList.get(0).getDstDPID() == 2);
        assertTrue(statusList.get(0).getStatus() == TunnelLinkStatus.DOWN);
    }

}
