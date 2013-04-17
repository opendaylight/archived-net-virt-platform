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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.EasyMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.routing.Route;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.BetterTopologyInstance;
import org.sdnplatform.topology.BetterTopologyManager;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.topology.OrderedNodePair;



public class BetterTopologyInstanceTest extends TopologyInstanceTest {

    @Before
    public void SetUp() throws Exception {
        fmc = new ModuleContext();
        linkDiscovery = EasyMock.createMock(ILinkDiscoveryService.class);
        mockControllerProvider = new MockControllerProvider();
        fmc.addService(IControllerService.class, mockControllerProvider);
        MockThreadPoolService tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ILinkDiscoveryService.class, linkDiscovery);
        topologyManager  = new BetterTopologyManager();
        topologyManager.init(fmc);
        tp.init(fmc);
        tp.startUp(fmc);
    }

    private void verifyBroadcastDomains(int [][][] ebd) {
        verifyBroadcastDomains(ebd, true);
    }

    private void verifyBroadcastDomains(int [][][] ebd, boolean tunnelsEnabled){
        NodePortTuple npt = null;
        Set<NodePortTuple> expected = new HashSet<NodePortTuple>();
        for(int i=0; i<ebd.length; ++i) {
            int [][] nptList = ebd[i];
            expected.clear();
            for(int j=0; j<nptList.length; ++j) {
                npt = new NodePortTuple((long)nptList[j][0], (short)nptList[j][1]);
                expected.add(npt);
            }
            BetterTopologyInstance ti = (BetterTopologyInstance) 
                    topologyManager.getCurrentInstance(tunnelsEnabled);

            Set<NodePortTuple> computed = ti.getBroadcastDomainPorts(npt);

            if (computed != null)
                assertTrue(computed.equals(expected));
            else if (computed == null)
                assertTrue(expected.isEmpty());
        }
    }

    private void verifyBlockedPorts(int [][] bports) {
        Set<NodePortTuple> expected = new HashSet<NodePortTuple>();
        for(int i=0; i<bports.length; ++i) {
            int [] entry = bports[i];
            NodePortTuple npt = new NodePortTuple((long)entry[0], (short)entry[1]);
            expected.add(npt);
        }

        BetterTopologyInstance ti = (BetterTopologyInstance) topologyManager.getCurrentInstance();
        Set<NodePortTuple> computed = ti.getBlockedPorts();

        if (bports.length == 0) {
            assertTrue (ti.getBlockedPorts().isEmpty() == true);
        } else {
            assertTrue(expected.equals(computed));
        }
    }
    
    private void verifyExternalBroadcastPorts (int [][][] ebports) {
        BetterTopologyInstance ti = (BetterTopologyInstance) topologyManager.getCurrentInstance();
        int count;
        for(int i=0; i<ebports.length; ++i) {
            int [][] nptList = ebports[i];
            count = 0;
            for(int j=0; j<nptList.length; ++j) {
                if (ti.isIncomingBroadcastAllowedOnSwitchPort(
                        (long)nptList[j][0], (short)nptList[j][1])) {
                    count++;
                }
            }
            assertTrue(count == 0 || count == 1);
        }
    }

    private void verifyHigherLevelTopology(int numberOfNodes) {
        BetterTopologyInstance ti = (BetterTopologyInstance) topologyManager.getCurrentInstance();
        assertTrue(ti.getHTNeighbors().keySet().size() == numberOfNodes);
        Set<OrderedNodePair> allowedNodePairs = ti.getAllowedNodePairs();
        assertTrue(allowedNodePairs.size() <= 2*(numberOfNodes-1));
        Map<Long, Map<Long,Long>> nexthop = ti.getNextHopMap();

        for(long s: nexthop.keySet()) {
            HashSet<Long> neighbors = new HashSet<Long>(nexthop.get(s).values());
            for(long nbr: neighbors) {
                if (s == nbr) continue;
                OrderedNodePair onp = new OrderedNodePair(s,nbr);
                assertTrue(allowedNodePairs.contains(onp));
                assertTrue(nexthop.get(s).size() == nexthop.get(nbr).size());
            }
        }
    }

    @Test
    public void testBroadcastDomains() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();
        tm.clear();
        {
            int [][] linkArray = {
                    {1, 2, 2, 1, MULTIHOP_LINK},
                    {2, 1, 1, 2, MULTIHOP_LINK},
                    {2, 2, 3, 1, DIRECT_LINK},
                    {3, 1, 2, 2, DIRECT_LINK},
                    {3, 2, 4, 1, MULTIHOP_LINK},
                    {4, 1, 3, 2, MULTIHOP_LINK},
                    {4, 2, 1, 1, DIRECT_LINK},
                    {1, 1, 4, 2, DIRECT_LINK},
                    {1, 2, 4, 5, MULTIHOP_LINK},
                    {4, 5, 1, 2, MULTIHOP_LINK},
                    {1, 5, 3, 2, MULTIHOP_LINK},
                    {3, 2, 1, 5, MULTIHOP_LINK},

            };
            int [][] expectedClusters = {
                    {1,4},
                    {2,3},
            };
            int [][][] ebd = {
                    {{1,2},{2,1},{4,5}},
                    {{1,5},{3,2},{4,1}},
            };
            int [][][] ebports = {
                    {{1,2},{4,5}},
                    {{2,1}},
                    {{3,2}},
                    {{4,1},{1,5}}
            };
            int [][]blockedPorts = {
                    {1,2}, {4,5},
            };

            createTopologyFromLinks(linkArray);

            topologyManager.updateTopology();
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd, false);
            verifyExternalBroadcastPorts(ebports);
            verifyBlockedPorts(blockedPorts);
            verifyHigherLevelTopology(4); // give the number of nodes
        }

        tm.clear();
        {
            int [][] linkArray = {
                    {1, 2, 2, 1, MULTIHOP_LINK},
                    {2, 1, 1, 2, MULTIHOP_LINK},
                    {2, 2, 3, 1, DIRECT_LINK},
                    {3, 1, 2, 2, DIRECT_LINK},
                    {3, 2, 4, 1, MULTIHOP_LINK},
                    {4, 1, 3, 2, MULTIHOP_LINK},
                    {4, 2, 1, 1, DIRECT_LINK},
                    {1, 1, 4, 2, DIRECT_LINK},

            };
            int [][] expectedClusters = {
                    {1,4},
                    {2,3},
            };
            int [][][] ebd = {
                    {{1,2},{2,1}},
                    {{3,2},{4,1}},
            };

            int [][][] ebports = {
                    {{1,2}},
                    {{2,1}},
                    {{3,2}},
                    {{4,1}},
            };
            int [][]blockedPorts = {
                    {1,2}
            };
            createTopologyFromLinks(linkArray);
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
            verifyBlockedPorts(blockedPorts);
            verifyExternalBroadcastPorts(ebports);
        }

        // The following are a bit of weird test cases.
        tm.clear();
        {
            int [][] linkArray = {
                    {1, 1, 2, 1, MULTIHOP_LINK},
                    {2, 1, 1, 1, MULTIHOP_LINK},

                    {2, 1, 3, 1, DIRECT_LINK},
                    {3, 1, 2, 1, DIRECT_LINK},

                    {3, 1, 4, 1, DIRECT_LINK},
                    {4, 1, 3, 1, DIRECT_LINK},

                    {4, 1, 5, 1, MULTIHOP_LINK},
                    {5, 1, 4, 1, MULTIHOP_LINK},

            };
            int [][] expectedClusters = {
                    {1},
                    {2},
                    {3},
                    {4},
                    {5}
            };
            int [][][] ebd = {
                    {{1,1},{2,1},{3,1},{4,1},{5,1}}
            };

            createTopologyFromLinks(linkArray);
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
        }
        {
            int [][] linkArray = {
                                  {1, 1, 3, 1, MULTIHOP_LINK},
                                  {3, 1, 1, 1, MULTIHOP_LINK},
            };
            createTopologyFromLinks(linkArray);
            boolean flag = tm.updateTopology();
            assertFalse(flag);
        }
        {
            tm.removeLink((long)1,(short)1,(long)3,(short)1);
            tm.removeLink((long)3,(short)1,(long)1,(short)1);
            boolean flag = tm.updateTopology();
            assertFalse(flag);
        }
        {
            tm.removeLink((long)1,(short)1,(long)2,(short)1);
            tm.removeLink((long)2,(short)1,(long)1,(short)1);
            int [][] expectedClusters = {
                    {2},
                    {3},
                    {4},
                    {5}
            };
            int [][][] ebd = {
                    {{2,1},{3,1},{4,1},{5,1}}
            };

            tm.applyUpdates();
            boolean flag = tm.updateTopology();
            assertTrue(flag);
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
        }

        {
            tm.removeLink((long)4,(short)1,(long)5,(short)1);
            tm.removeLink((long)5,(short)1,(long)4,(short)1);
            int [][] expectedClusters = {
                    {2}, {3}, {4},
            };
            int [][][] ebd = {};

            topologyManager.updateTopology();
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
        }

        {
            tm.removeLink((long)3,(short)1,(long)4,(short)1);
            tm.removeLink((long)4,(short)1,(long)3,(short)1);
            int [][] expectedClusters = {
                    {2,3}, {4},
            };
            int [][][] ebd = {};

            topologyManager.updateTopology();
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
        }

    }

    /**
     *  Same test as in sdnplatform, but the tunnel links should be ignored
     *  in the SDN Platform part of the code and must only be used as
     *  needed.
     */
    @Test @Override
    public void testLoopDetectionWithIslands() throws Exception {

        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |   2   |
        //      +-------+             +-------+
        //          |                     |
        //          |                     |
        //      +-------+                 |
        //      |   1   |                 |
        //      |   3  2|-----------------+
        //      |   3   |
        //      +-------+
        //
        //
        //      +-------+
        //      |   1   |
        //      |   4  2|----------------+
        //      |   3   |                |
        //      +-------+                |
        //          |                    |
        //          |                    |
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+
        {
            int [][] linkArray = {
                    {1, 1, 2, 1, DIRECT_LINK},
                    {2, 1, 1, 1, DIRECT_LINK},
                    {1, 2, 3, 1, DIRECT_LINK},
                    {3, 1, 1, 2, DIRECT_LINK},
                    {2, 2, 3, 2, DIRECT_LINK},
                    {3, 2, 2, 2, DIRECT_LINK},

                    {4, 2, 6, 2, DIRECT_LINK},
                    {6, 2, 4, 2, DIRECT_LINK},
                    {4, 3, 5, 1, DIRECT_LINK},
                    {5, 1, 4, 3, DIRECT_LINK},
                    {5, 2, 6, 1, DIRECT_LINK},
                    {6, 1, 5, 2, DIRECT_LINK},

            };

            int [][] expectedClusters = {
                    {1, 2, 3},
                    {4, 5, 6}
            };
            int [][][] expectedBroadcastPorts = {
                    {{1,2}, {3,1}, {1,1}, {2,1}},
                    {{4,2}, {4,3}, {5,1}, {6,2}},
            };

            createTopologyFromLinks(linkArray);
            topologyManager.updateTopology();
            verifyClusters(expectedClusters);
            verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);

            BetterTopologyInstance ti = (BetterTopologyInstance)
                    topologyManager.getCurrentInstance(false);
            assertNull(ti.multiroute(3L, 5L, 0));

        }

        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |   2   |
        //      +-------+             +-------+
        //          |                     |
        //          |                     |
        //      +-------+                 |
        //      |   1   |                 |
        //      |   3  2|-----------------+
        //      |   3   |
        //      +-------+
        //          |
        //          |
        //          |
        //      +-------+
        //      |   1   |
        //      |   4  2|----------------+
        //      |   3   |                |
        //      +-------+                |
        //          |                    |
        //          |                    |
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+

        {
            int [][] linkArray = {
                    {3, 3, 4, 1, DIRECT_LINK},
                    {4, 1, 3, 3, DIRECT_LINK},

            };
            int [][] expectedClusters = {
                    {1, 2, 3, 4, 5, 6}
            };
            int [][][] expectedBroadcastPorts = {
                 {{1,1}, {2,1}, {1,2}, {3,1}, {3,3}, {4,1}, 
                       {4,3}, {5,1}, {4,2}, {6,2}}
            };

            createTopologyFromLinks(linkArray);
            verifyClusters(expectedClusters);
            verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);
        }
    }

    /**
     * This test ensures that tunnel links are not getting added to topology.
     * @throws Exception
     */
    @Test
    public void testBroadcastDomainLoopDetection() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();

        tm.clear();
        {
            int [][] linkArray = {
                    {1, 2, 2, 1, MULTIHOP_LINK},
                    {2, 1, 1, 2, MULTIHOP_LINK},

                    {2, 2, 3, 1, TUNNEL_LINK},
                    {3, 1, 2, 2, TUNNEL_LINK},

                    {3, 2, 4, 1, MULTIHOP_LINK},
                    {4, 1, 3, 2, MULTIHOP_LINK},

                    {4, 2, 1, 1, TUNNEL_LINK},
                    {1, 1, 4, 2, TUNNEL_LINK},
                    {4, 3, 5, 3, TUNNEL_LINK},
                    {5, 3, 4, 3, TUNNEL_LINK},
                    {5, 4, 6, 4, TUNNEL_LINK},
                    {6, 5, 1, 5, TUNNEL_LINK},
            };
            int [][] expectedClusters = {
                                         {1},{2},{3},{4}
            };
            int [][][] ebd = {
                    {{1,2},{2,1}},
                    {{3,2},{4,1}},
            };

            createTopologyFromLinks(linkArray);

            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
            verifyHigherLevelTopology(6); // give the number of nodes
        }
    }

    @Test
    public void testBroadcastPortsBetweenClusters() throws Exception {
        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |   2   |
        //      +-------+             +-------+
        //          |                     |
        //          |                     |
        //      +-------+                 |
        //      |   1   |                 |
        //      |   3  2|-----------------+
        //      |   3   |
        //      +-------+
        //
        //
        //
        //      +-------+
        //      |   1   |
        //      |   4  2|----------------+
        //      |   3   |                |
        //      +-------+                |
        //          |                    |
        //          |                    |
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();

        {
            int [][] linkArray = {
                    {1, 1, 2, 1, DIRECT_LINK},
                    {2, 1, 1, 1, DIRECT_LINK},
                    {1, 2, 3, 1, DIRECT_LINK},
                    {3, 1, 1, 2, DIRECT_LINK},
                    {2, 2, 3, 2, DIRECT_LINK},
                    {3, 2, 2, 2, DIRECT_LINK},

                    {4, 2, 6, 2, DIRECT_LINK},
                    {6, 2, 4, 2, DIRECT_LINK},
                    {4, 3, 5, 1, DIRECT_LINK},
                    {5, 1, 4, 3, DIRECT_LINK},
                    {5, 2, 6, 1, DIRECT_LINK},
                    {6, 1, 5, 2, DIRECT_LINK},

                    {1, 7, 4, 7, DIRECT_LINK},
                    {2, 7, 5, 7, DIRECT_LINK},
                    {3, 7, 6, 7, DIRECT_LINK},
            };

            int [][] expectedClusters = {
                    {1, 2, 3},
                    {4, 5, 6}
            };
            int [][][] expectedBroadcastPorts = {
                    {{1,1}, {2,1}, {1,2}, {3,1}},
                    {{4,3}, {5,1}, {4,2}, {6,2}},
            };

            // Use the same switch object for all values.
            IOFSwitch sw1 = EasyMock.createMock(IOFSwitch.class);
            Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
            switchMap.put(1L, sw1);
            switchMap.put(2L, sw1);
            switchMap.put(3L, sw1);
            switchMap.put(4L, sw1);
            switchMap.put(5L, sw1);
            switchMap.put(6L, sw1);
            expect(linkDiscovery.isTunnelPort(1L, (short)7)).andReturn(false).anyTimes();
            expect(linkDiscovery.isTunnelPort(2L, (short)7)).andReturn(false).anyTimes();
            expect(linkDiscovery.isTunnelPort(3L, (short)7)).andReturn(false).anyTimes();
            expect(linkDiscovery.isTunnelPort(4L, (short)7)).andReturn(false).anyTimes();
            expect(linkDiscovery.isTunnelPort(5L, (short)7)).andReturn(false).anyTimes();
            expect(linkDiscovery.isTunnelPort(6L, (short)7)).andReturn(false).anyTimes();
            mockControllerProvider.setSwitches(switchMap);
            expect(sw1.portEnabled(EasyMock.anyShort())).andReturn(true).anyTimes();
            replay(sw1, linkDiscovery);

            createTopologyFromLinks(linkArray);
            verifyClusters(expectedClusters);
            verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);


            assertTrue(tm.isAttachmentPointPort(1L, (short)7));
            assertTrue(tm.isAttachmentPointPort(2L, (short)7));
            assertTrue(tm.isAttachmentPointPort(3L, (short)7));
            assertTrue(tm.isAttachmentPointPort(4L, (short)7));
            assertTrue(tm.isAttachmentPointPort(5L, (short)7));
            assertTrue(tm.isAttachmentPointPort(6L, (short)7));
            verify(sw1, linkDiscovery);
        }
    }

    @Test
    public void testUnicastPortMappings() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();
        tm.clear();
        {
            int [][] linkArray = {
                    {1, 1, 3, 1, MULTIHOP_LINK},
                    {3, 1, 1, 1, MULTIHOP_LINK},
                    {1, 1, 4, 1, MULTIHOP_LINK},
                    {4, 1, 1, 1, MULTIHOP_LINK},
                    {2, 1, 3, 1, MULTIHOP_LINK},
                    {3, 1, 2, 1, MULTIHOP_LINK},
                    {2, 1, 4, 1, MULTIHOP_LINK},
                    {4, 1, 2, 1, MULTIHOP_LINK},
                    {3, 3, 5, 3, MULTIHOP_LINK},
                    {5, 3, 3, 3, MULTIHOP_LINK},
                    {3, 3, 6, 3, MULTIHOP_LINK},
                    {6, 3, 3, 3, MULTIHOP_LINK},
                    {4, 3, 5, 3, MULTIHOP_LINK},
                    {5, 3, 4, 3, MULTIHOP_LINK},
                    {4, 3, 6, 3, MULTIHOP_LINK},
                    {6, 3, 4, 3, MULTIHOP_LINK},
                    {1, 2, 2, 2, DIRECT_LINK},
                    {2, 2, 1, 2, DIRECT_LINK},
                    {3, 2, 4, 2, DIRECT_LINK},
                    {4, 2, 3, 2, DIRECT_LINK},
                    {5, 2, 6, 2, DIRECT_LINK},
                    {6, 2, 5, 2, DIRECT_LINK},
            };
            int [][] expectedClusters = {
                    {1,2},
                    {3,4},
                    {5,6}
            };
            int [][][] ebd = {
                    {{1,1},{2,1},{3,1},{4,1}},
                    {{3,3},{4,3},{5,3},{6,3}}
            };
            int [][][] ebports = {
                    {{1,1},{2,1}},
                    {{3,1},{4,1}},
                    {{3,3},{4,3}},
                    {{5,3},{6,3}}
            };

            createTopologyFromLinks(linkArray);
            topologyManager.updateTopology();
            verifyClusters(expectedClusters);
            verifyBroadcastDomains(ebd);
            verifyExternalBroadcastPorts(ebports);
            verifyHigherLevelTopology(5); // give the number of nodes

            NodePortTuple nptOut, nptIn;
            NodePortTuple npt11 = new NodePortTuple((long)1, (short)1);
            NodePortTuple npt21 = new NodePortTuple((long)2, (short)1);
            NodePortTuple npt15 = new NodePortTuple((long)1, (short)5);
            NodePortTuple npt25 = new NodePortTuple((long)2, (short)5);
            NodePortTuple npt31 = new NodePortTuple((long)3, (short)1);
            NodePortTuple npt33 = new NodePortTuple((long)3, (short)3);
            new NodePortTuple((long)4, (short)1);
            NodePortTuple npt43 = new NodePortTuple((long)4, (short)3);

            // There are some cases, where the switchport that will be used
            // will be either (3,1) & (3,3)   or  (4,1) or (4,3). For those
            // cases, it would be easier define a single variable that's
            // assigned one of the values depending on whether
            // nofTrafficSpreading is enabled or not.
            NodePortTuple npt31or41;
            NodePortTuple npt33or43;
            npt31or41 = new NodePortTuple((long)3, (short)1);
            npt33or43 = new NodePortTuple((long)3, (short)3);

            BetterTopologyInstance ti =
                    (BetterTopologyInstance) topologyManager.getCurrentInstance();

            assertTrue(ti.inSameBroadcastDomain(1L, (short)1, 2L, (short)1));
            assertFalse(ti.inSameBroadcastDomain(1L, (short)1, 3L, (short)3));
            assertTrue(ti.inSameBroadcastDomain(1L, (short)10, 1L, (short)10));
            assertFalse(ti.inSameBroadcastDomain(1L, (short)10, 1L, (short)11));

            nptIn  = ti.getIncomingSwitchPort((long)1, (short)5, (long)1, (short)1);
            assertTrue(nptIn.equals(npt15));
            nptOut = ti.getOutgoingSwitchPort((long)1, (short)5, (long)1, (short)1);
            assertTrue(nptOut.equals(npt11));

            nptIn  = ti.getIncomingSwitchPort((long)1, (short)5, (long)2, (short)1);
            assertTrue(nptIn.equals(npt15));
            nptOut = ti.getOutgoingSwitchPort((long)1, (short)5, (long)2, (short)1);
            assertTrue(nptOut.equals(npt11));

            nptIn  = ti.getIncomingSwitchPort((long)1, (short)5, (long)2, (short)5);
            assertTrue(nptIn.equals(npt15));
            nptOut = ti.getOutgoingSwitchPort((long)1, (short)5, (long)2, (short)5);
            assertTrue(nptOut.equals(npt25));


            nptIn  = ti.getIncomingSwitchPort((long)1, (short)1, (long)1, (short)5);
            assertTrue(nptIn.equals(npt11));
            nptOut = ti.getOutgoingSwitchPort((long)1, (short)1, (long)1, (short)5);
            assertTrue(nptOut.equals(npt15));

            nptIn = ti.getIncomingSwitchPort((long)1, (short)1, (long)2, (short)5);
            assertTrue(nptIn.equals(npt21));
            nptOut = ti.getOutgoingSwitchPort((long)2, (short)1, (long)2, (short)5);
            assertTrue(nptOut.equals(npt25));

            nptIn = ti.getIncomingSwitchPort((long)3, (short)3, (long)4, (short)3);
            nptOut = ti.getOutgoingSwitchPort((long)3, (short)3, (long)4, (short)3);
            assertTrue(nptIn  == null);
            assertTrue(nptOut == null);

            nptIn = ti.getIncomingSwitchPort((long)3, (short)3, (long)4, (short)3);
            nptOut = ti.getOutgoingSwitchPort((long)3, (short)3, (long)4, (short)3);
            assertTrue(nptIn  == null);
            assertTrue(nptOut == null);

            // the broadcast domain to broadcast domain traffic should
            // traverse through ports (4,3) and (4,1)
            nptIn  = ti.getIncomingSwitchPort((long)4, (short)1, (long)4, (short)3);
            nptOut = ti.getOutgoingSwitchPort((long)4, (short)1, (long)4, (short)3);
            assertTrue(nptIn.equals(npt31));
            assertTrue(nptOut.equals(npt33));

            // the broadcast domain to broadcast domain traffic should
            // traverse through ports (4,3) and (4,1)
            nptIn  = ti.getIncomingSwitchPort((long)4, (short)1, (long)5, (short)3);
            nptOut = ti.getOutgoingSwitchPort((long)4, (short)1, (long)5, (short)3);
            assertTrue(nptIn.equals(npt31or41));
            assertTrue(nptOut.equals(npt33or43));

            // Get the incoming and outgoing switchports when the src and dst
            // are in different openflow domains.
            nptIn  = ti.getIncomingSwitchPort((long)1, (short)5, (long)4, (short)3);
            nptOut = ti.getOutgoingSwitchPort((long)1, (short)5, (long)4, (short)3);
            assertTrue(nptIn.equals(npt15));
            assertTrue(nptOut.equals(npt33or43));

            // Get the incoming and outgoing switchports when the src and dst
            // are in different openflow domains.
            nptIn  = ti.getIncomingSwitchPort((long)1, (short)5, (long)5, (short)3);
            nptOut = ti.getOutgoingSwitchPort((long)1, (short)5, (long)5, (short)3);
            assertTrue(nptIn.equals(npt15));
            assertTrue(nptOut.equals(npt33or43));

            // check for outgoing broacast port assignment
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)1, (short)5, (long)6, (short)5);
            assertTrue(nptOut.equals(npt11));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)2, (short)5, (long)6, (short)5);
            assertTrue(nptOut.equals(npt21));
            NodePortTuple npt67 = new NodePortTuple(6L, (short)7);
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)6, (short)5, (long)6, (short)7);
            assertTrue(nptOut.equals(npt67));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)5, (short)5, (long)6, (short)7);
            assertTrue(nptOut.equals(npt67));

            NodePortTuple npt1010 = new NodePortTuple(10L, (short)10);
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)10, (short)5, (long)10, (short)10);
            assertTrue(nptOut.equals(npt1010));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)10, (short)5, (long)20, (short)10);
            assertNull(nptOut);

            // There are two paths, one out of 3,3 and one out of 4,3
            // The system could pick either one of them.
            // No matter where the attachment points are, the method should
            // provide the right broadcast port
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)3, (short)1, (long)6, (short)5);
            assertTrue(nptOut.equals(npt33) || nptOut.equals(npt43));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)4, (short)1, (long)6, (short)5);
            assertTrue(nptOut.equals(npt33) || nptOut.equals(npt43));
            // *****
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)3, (short)5, (long)6, (short)5);
            assertTrue(nptOut.equals(npt33));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)4, (short)5, (long)6, (short)5);
            assertTrue(nptOut.equals(npt43));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)3, (short)3, (long)6, (short)5);
            assertTrue(nptOut == null);
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)4, (short)3, (long)6, (short)5);
            assertTrue(nptOut == null);
            // *****
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)3, (short)1, (long)4, (short)3);
            assertTrue(nptOut.equals(npt33) || nptOut.equals(npt43));
            nptOut = ti.getAllowedOutgoingBroadcastPort((long)3, (short)1, (long)5, (short)3);
            assertTrue(nptOut.equals(npt33) || nptOut.equals(npt43));
            // End of AllowedOutgoingBroadcastPort tests.

            // Let's check consistency on the checkpoints here.
            assertTrue(ti.isConsistent((long)1, (short)5, (long)3,(short)1));
            assertTrue(ti.isConsistent((long)1, (short)5, (long)4,(short)1));
            assertTrue(!ti.isConsistent((long)1, (short)5, (long)1,(short)1));
            assertTrue(!ti.isConsistent((long)1, (short)5, (long)3,(short)3));
            assertTrue(!ti.isConsistent((long)1, (short)5, (long)3,(short)3));
            assertTrue(ti.isConsistent((long)1, (short)5, (long)5,(short)3));
            // test set 2
            assertTrue(ti.isConsistent((long)1, (short)1, (long)2,(short)1));
            assertTrue(ti.isConsistent((long)1, (short)1, (long)3,(short)1));
            assertTrue(ti.isConsistent((long)2, (short)1, (long)3,(short)1));
            assertTrue(ti.isConsistent((long)2, (short)1, (long)4,(short)1));
            // test 3
            assertTrue(!ti.isConsistent((long)2, (short)5, (long)4,(short)5));
            assertTrue(!ti.isConsistent((long)10, (short)1, (long)12,(short)5));
            assertTrue(!ti.isConsistent((long)1, (short)1, (long)12,(short)5));
            assertTrue(!ti.isConsistent((long)12, (short)5, (long)1,(short)1));
            // any internal switch port should be consistent.
            assertTrue(ti.isConsistent((long)12, (short)5, (long)1,(short)2));
            assertTrue(ti.isConsistent((long)1, (short)2, (long)2,(short)2));
            // any non-broadcast domain attachment point port should be
            // consistent with itself
            assertTrue(ti.isConsistent((long)12, (short)5, (long)12,(short)5));

            // test inSameIsland
            assertTrue(ti.inSameL2Domain(1L, 2L));
            assertTrue(ti.inSameL2Domain(1L, 3L));
            assertTrue(ti.inSameL2Domain(1L, 5L));
            assertTrue(ti.inSameL2Domain(4L, 6L));
            assertTrue(!ti.inSameL2Domain(1L, 12L));
            assertTrue(!ti.inSameL2Domain(12L, 4L));
            assertTrue(!ti.inSameL2Domain(20L, 30L));
            assertTrue(ti.inSameL2Domain(15L, 15L));

            // test the same island membership differently.
            assertTrue(ti.getL2DomainId(1L) == ti.getL2DomainId(2L));
            assertTrue(ti.getL2DomainId(1L) == ti.getL2DomainId(3L));
            assertTrue(ti.getL2DomainId(1L) == ti.getL2DomainId(5L));
            assertTrue(ti.getL2DomainId(4L) == ti.getL2DomainId(6L));
            assertTrue(ti.getL2DomainId(1L) != ti.getL2DomainId(12L));
            assertTrue(ti.getL2DomainId(12L) != ti.getL2DomainId(4L));
            assertTrue(ti.getL2DomainId(20L) != ti.getL2DomainId(30L));

            // test getBroadcastPorts
            Set<Short> ports = ti.getBroadcastPorts((long)3, (long)3, (short)5);
            assertTrue(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertTrue(ports.contains((short)3));

            ports = ti.getBroadcastPorts((long)3, (long)4, (short)3);
            assertTrue(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertFalse(ports.contains((short)3));

            ports = ti.getBroadcastPorts((long)4, (long)4, (short)1);
            assertFalse(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertFalse(ports.contains((short)3));

            // Even though 4,1 is not the right node port for the broadcast
            // traffic from the broadcast domain to enter this cluster,
            // the output port assignment should be right.  It is up to the
            // forwarding module to drop this traffic as the incoming
            // broadcast is not allowed on the switchport (4,1).
            ports = ti.getBroadcastPorts((long)4, (long)4, (short)1);
            assertFalse(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertFalse(ports.contains((short)3));

            ports = ti.getBroadcastPorts((long)4, (long)3, (short)1);
            assertFalse(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertFalse(ports.contains((short)3));

            ports = ti.getBroadcastPorts((long)3, (long)12, (short)1);
            assertTrue(ports.isEmpty());

            // test Broadcast ports for a host in a different cluster.
            ports = ti.getBroadcastPorts((long)4, (long)1, (short)1);
            assertFalse(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertFalse(ports.contains((short)3));

            // test Broadcast ports for a host in a different cluster.
            ports = ti.getBroadcastPorts((long)4, (long)1, (short)1);
            assertFalse(ports.contains((short)1));
            assertFalse(ports.contains((short)2));
            assertFalse(ports.contains((short)3));

            // test routes.
            List<NodePortTuple> nptList;
            List<NodePortTuple> expectedNptList = new ArrayList<NodePortTuple>();;

            nptList = ti.getFirstHopRoute(1, 5, 0);
            expectedNptList.add(new NodePortTuple(1L, (short)1));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.getFirstHopRoute(2, 5, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(2L, (short)1));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.getLastHopRoute(4, 5, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(5L, (short)3));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.getLastHopRoute(4, 6, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(6L, (short)3));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.getRouteThroughCluster(4, 5, 2, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(3L, (short)3));
            expectedNptList.add(new NodePortTuple(3L, (short)1));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.multiroute(1, 6, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(1L, (short)1));
            expectedNptList.add(new NodePortTuple(3L, (short)1));
            expectedNptList.add(new NodePortTuple(3L, (short)3));
            expectedNptList.add(new NodePortTuple(6L, (short)3));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.multiroute(5, true, 6, false, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(3L, (short)1));
            expectedNptList.add(new NodePortTuple(3L, (short)3));
            expectedNptList.add(new NodePortTuple(6L, (short)3));
            assertTrue(nptList.equals(expectedNptList));

            nptList = ti.multiroute(5, true, 4, true, 0);
            expectedNptList.clear();
            expectedNptList.add(new NodePortTuple(3L, (short)1));
            expectedNptList.add(new NodePortTuple(3L, (short)3));
            assertTrue(nptList.equals(expectedNptList));

            // Ensure that for every cluster -- broadcast domain connection,
            // the lowest switch port is chosen for incoming broadcast traffic.
            NodePortTuple npt;
            assertTrue(ti.isIncomingBroadcastAllowedOnSwitchPort(1L, (short)1));
            assertFalse(ti.isIncomingBroadcastAllowedOnSwitchPort(2L, (short)1));
            assertTrue(ti.isIncomingBroadcastAllowedOnSwitchPort(3L, (short)1));
            assertFalse(ti.isIncomingBroadcastAllowedOnSwitchPort(4L, (short)1));
            assertTrue(ti.isIncomingBroadcastAllowedOnSwitchPort(3L, (short)3));
            assertFalse(ti.isIncomingBroadcastAllowedOnSwitchPort(4L, (short)3));
            assertTrue(ti.isIncomingBroadcastAllowedOnSwitchPort(5L, (short)3));
            assertFalse(ti.isIncomingBroadcastAllowedOnSwitchPort(6L, (short)3));

            NodePortTuple npt53;
            npt53 = new NodePortTuple(5L, (short)3);
            npt = ti.getConsistentBroadcastAttachmentPoint(6L, 1L, (short)10);
            log.info("{}", npt);
            assertTrue(npt.equals(npt53));
            npt = ti.getConsistentBroadcastAttachmentPoint(6L, 1L, (short)2);
            assertTrue(npt == null);
            npt = ti.getConsistentBroadcastAttachmentPoint(6L, 4L, (short)1);
            assertTrue(npt.equals(npt53));
            npt = ti.getConsistentBroadcastAttachmentPoint(6L, 4L, (short)1);
            assertTrue(npt.equals(npt53));
            npt = ti.getConsistentBroadcastAttachmentPoint(6L, 15L, (short)1);
            assertTrue(npt == null);

            npt = ti.getConsistentBroadcastAttachmentPoint(15L, 15L, (short)1);
            assertTrue(npt.equals(new NodePortTuple(15L, (short)1)));

            npt = ti.getConsistentBroadcastAttachmentPoint(15L, 1L, (short)1);
            assertTrue(npt == null);
            npt = ti.getConsistentBroadcastAttachmentPoint(15L, 1L, (short)2);
            assertTrue(npt == null);
        }
    }

    /**
     * This test checks if the tunnel domain is created when topology
     * manager is created.
     * @throws Exception
     */
    @Test
    public void testTunnelDomainCreation() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();
        tm.clearCurrentTopology();
        Long tunnelDomain = tm.getTunnelDomainId();
        assertTrue(tunnelDomain != null);
    }

    /**
     * Ensure that tunnel ports are added and removed.
     */
    @Test
    public void testTunnelDomainPorts() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();
        tm.clearCurrentTopology();

        assertTrue(tm.getTunnelPorts().isEmpty());
        tm.addTunnelPort(1L, (short)10);
        tm.addTunnelPort(2L, (short)10);
        assertTrue(tm.getTunnelPorts().size() == 2);
        tm.removeTunnelPort(2L, (short)10);
        assertTrue(tm.getTunnelPorts().size() == 1);
    }

    /**
     * Ensure that tunnel ports are added and removed.
     */
    @Test
    public void testSwitchClusterWithTunnelDomain() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();
        tm.clearCurrentTopology();

        //      +-------+             +-------+
        //      |       |             |       |
        //      |   1  1|-------------|1  2   |
        //      |   2   |             |   2   |
        //      +-------+             +-------+
        //          |                     |
        //          |                     |
        //      +-------+                 |
        //      |   1   |                 |
        //      |   3  2|-----------------+
        //      |   3   |
        //      +-------+
        //
        //
        //
        //      +-------+
        //      |   1   |
        //      |   4  2|----------------+
        //      |   3   |                |
        //      +-------+                |
        //          |                    |
        //          |                    |
        //      +-------+             +-------+
        //      |   1   |             |   2   |
        //      |   5  2|-------------|1  6   |
        //      |       |             |       |
        //      +-------+             +-------+

        {
            int [][] linkArray = {
                    {1, 1, 2, 1, DIRECT_LINK},
                    {2, 1, 1, 1, DIRECT_LINK},
                    {1, 2, 3, 1, DIRECT_LINK},
                    {3, 1, 1, 2, DIRECT_LINK},
                    {2, 2, 3, 2, DIRECT_LINK},
                    {3, 2, 2, 2, DIRECT_LINK},

                    {4, 2, 6, 2, DIRECT_LINK},
                    {6, 2, 4, 2, DIRECT_LINK},
                    {4, 3, 5, 1, DIRECT_LINK},
                    {5, 1, 4, 3, DIRECT_LINK},
                    {5, 2, 6, 1, DIRECT_LINK},
                    {6, 1, 5, 2, DIRECT_LINK},
            };

            int [][] expectedClusters = {
                    {1, 2, 3}, {4, 5, 6}
            };

            // Use the same switch object for all values.
            IOFSwitch sw1 = EasyMock.createMock(IOFSwitch.class);
            Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
            switchMap.put(1L, sw1);
            switchMap.put(2L, sw1);
            switchMap.put(3L, sw1);
            switchMap.put(4L, sw1);
            switchMap.put(5L, sw1);
            switchMap.put(6L, sw1);
            mockControllerProvider.setSwitches(switchMap);
            expect(sw1.portEnabled(EasyMock.anyShort())).andReturn(true).anyTimes();
            replay(sw1);

            createTopologyFromLinks(linkArray);
            verify(sw1);

            verifyClusters(expectedClusters);

        }
        {
            int [][] expectedClusters = {
                                         {1, 2, 3, 4, 5, 6}
                                 };

            int [][][] expectedBroadcastPorts = {
                     {{1,1}, {2,1}, {1,2}, {3,1}, {4,3}, {5,1}, {4,2}, {6,2}},
                                         };

            // Add two tunnel ports.
            tm.addTunnelPort(1L, (short)10);
            tm.addTunnelPort(4L, (short)10);

            // Create new instance, this should create a new instance to update
            // the tunnel port features.
            tm.createNewInstance();

            verifyClusters(expectedClusters);
            verifyExpectedBroadcastPortsInClusters(expectedBroadcastPorts);

            // Get routes to see if the tunnel domain ports are eliminated.
            Route route = tm.getRoute(1L, 4L, 0);
            assertTrue(route != null);
            assertTrue(route.getPath().size() == 2);

            // Route doesn't exist in the tunnel-less topology.
            route = tm.getRoute(1L, 4L, 0, false);
            assertTrue(route == null || route.getPath().isEmpty());

            // Verify if the routes to others are computed correctly.
            // The route is through the tunnel.
            route = tm.getRoute(1L, 5L, 0);
            assertTrue(route.getPath().size() == 4);
            assertTrue(route.getPath().contains(new NodePortTuple(1L, (short)10)));
            assertTrue(route.getPath().contains(new NodePortTuple(4L, (short)10)));

        }
        {
            // Add two tunnel ports.
            tm.removeTunnelPort(1L, (short)10);
            int [][] expectedClusters = {
                                         {1, 2, 3}, {4, 5, 6}
                                 };
            tm.createNewInstance();
            verifyClusters(expectedClusters);
        }
    }
}
