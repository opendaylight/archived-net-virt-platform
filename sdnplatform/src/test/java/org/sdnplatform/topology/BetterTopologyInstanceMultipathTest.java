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

import java.util.List;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.linkdiscovery.ILinkDiscovery;
import org.sdnplatform.linkdiscovery.ILinkDiscoveryService;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.BetterTopologyInstance;
import org.sdnplatform.topology.BetterTopologyManager;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.topology.TopologyManager;

public class BetterTopologyInstanceMultipathTest {
    protected BetterTopologyManager topologyManager;
    protected ModuleContext fmc;
    protected ILinkDiscoveryService linkDiscovery;
    protected MockControllerProvider mockControllerProvider;

    protected int DIRECT_LINK = 1;
    protected int MULTIHOP_LINK = 2;
    protected int TUNNEL_LINK = 3;
    
    public void createTopologyFromLinks(int [][] linkArray) throws Exception {
        ILinkDiscovery.LinkType type = ILinkDiscovery.LinkType.DIRECT_LINK;

        // Use topologymanager to write this test, it will make it a lot easier.
        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            if (r[4] == DIRECT_LINK)
                type= ILinkDiscovery.LinkType.DIRECT_LINK;
            else if (r[4] == MULTIHOP_LINK)
                type= ILinkDiscovery.LinkType.MULTIHOP_LINK;
            else if (r[4] == TUNNEL_LINK)
                type = ILinkDiscovery.LinkType.TUNNEL;

            topologyManager.addOrUpdateLink((long)r[0], (short)r[1], (long)r[2], (short)r[3], type);
        }
        topologyManager.createNewInstance();
    }
    
    public TopologyManager getTopologyManager() {
        return topologyManager;
    }
    
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
    
    @Test
    public void testTwoClustersMultipath() throws Exception {
        BetterTopologyManager tm = (BetterTopologyManager) getTopologyManager();
        tm.clear();
        {
            int [][] linkArray = {
                    {1, 1, 3, 1, DIRECT_LINK},
                    {3, 1, 1, 1, DIRECT_LINK},
                    {1, 2, 4, 1, DIRECT_LINK},
                    {4, 1, 1, 2, DIRECT_LINK},
                    {1, 3, 5, 1, DIRECT_LINK},
                    {5, 1, 1, 3, DIRECT_LINK},
                    {2, 1, 3, 2, DIRECT_LINK},
                    {3, 2, 2, 1, DIRECT_LINK},
                    {2, 2, 4, 2, DIRECT_LINK},
                    {4, 2, 2, 2, DIRECT_LINK},
                    {2, 3, 5, 2, DIRECT_LINK},
                    {5, 2, 2, 3, DIRECT_LINK},
                    
                    {3, 3, 13, 3, MULTIHOP_LINK},
                    {13, 3, 3, 3, MULTIHOP_LINK},
                    {5, 3, 15, 3, MULTIHOP_LINK},                     
                    {15, 3, 5, 3, MULTIHOP_LINK}, 
                    
                    {11, 1, 13, 1, DIRECT_LINK},
                    {13, 1, 11, 1, DIRECT_LINK},
                    {11, 2, 14, 1, DIRECT_LINK},
                    {14, 1, 11, 2, DIRECT_LINK},
                    {11, 3, 15, 1, DIRECT_LINK},
                    {15, 1, 11, 3, DIRECT_LINK},
                    {12, 1, 13, 2, DIRECT_LINK},
                    {13, 2, 12, 1, DIRECT_LINK},
                    {12, 2, 14, 2, DIRECT_LINK},
                    {14, 2, 12, 2, DIRECT_LINK},
                    {12, 3, 15, 2, DIRECT_LINK},
                    {15, 2, 12, 3, DIRECT_LINK}

            };

            createTopologyFromLinks(linkArray);
            topologyManager.updateTopology();

            BetterTopologyInstance ti =
                    (BetterTopologyInstance) topologyManager.getCurrentInstance();
           
            List<NodePortTuple> crossClusterPath;
            NodePortTuple expectedBroadcastPort = new NodePortTuple(5, 3);

            crossClusterPath = ti.multiroute(3, 13, 0);
            assertTrue(crossClusterPath.contains(expectedBroadcastPort));

            crossClusterPath = ti.multiroute(13, 3, 0);
            assertTrue(crossClusterPath.contains(expectedBroadcastPort));

            crossClusterPath = ti.multiroute(4, 14, 0);
            assertTrue(crossClusterPath.contains(expectedBroadcastPort));

            crossClusterPath = ti.multiroute(5, 15, 0);
            assertTrue(crossClusterPath.contains(expectedBroadcastPort));

        }
    }
}
