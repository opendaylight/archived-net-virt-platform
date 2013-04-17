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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;


import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.routing.BroadcastTree;
import org.sdnplatform.routing.Link;
import org.sdnplatform.routing.Route;
import org.sdnplatform.routing.RouteId;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BetterTopologyInstance extends TopologyInstance {
    protected static Logger log = LoggerFactory.getLogger(BetterTopologyInstance.class);

    protected Set<BroadcastDomain> broadcastDomains; // set of broadcast domains
    protected Map<NodePortTuple, BroadcastDomain> nodePortBroadcastDomainMap; // node port tuple to broadcast domain map

    protected Map<Long, Long> clusterKeyMap;    // map from a cluster to its key in the nodeMap
    protected Map<BroadcastDomain, Long> broadcastDomainKeyMap; // map from a broadcast domain to its key in the nodeMap

    protected Map<Long, Object> htNodes;  // Long is the id, Object could be either broadcast domain or cluster
    protected Map<Long, Set<Long>> htNeighbors; // set of Neighboring Nodes
    protected Map<Long, Map<Long, Long>> htNextHop; // for every source, a map
                                                    // of next hop for every
                                                    // destination.
    protected Map<Long, Long> clusterL2DomainIdMap; // Mapping of a cluster to
                                                    // an L2 domain id.

    // protected Set<NodePair> allowedNodePairs; // node pairs that can
    // communicate in teh higher layer.
    protected Map<OrderedNodePair, Set<NodePortTuple>> allowedUnicastPorts;
    protected Map<OrderedNodePair, NodePortTuple> allowedIncomingBroadcastPorts;
    protected Map<NodePortTuple, Set<Long>> permittedSwitches;
    protected Map<NodePortTuple, Set<Long>> permittedPortToBroadcastDomains;

    // routing tree for multipath routing
    protected Map<Long, BroadcastTreeMultipath> destinationRootedTreesMultipath;

    // Tunnel domain identifier.
    Long tunnelDomain;
    IControllerService controllerProvider;
    ITunnelManagerService tunnelManager;

    private final long LONG_PRIME = 304250263527209L;

    public BetterTopologyInstance(Map<Long, Set<Short>> switchPorts,
                               Set<NodePortTuple> blockedPorts,
                               Map<NodePortTuple, Set<Link>> switchPortLinks,
                               Set<NodePortTuple> broadcastDomainPorts,
                               Set<NodePortTuple> tunnelPorts,
                               Set<BroadcastDomain> bDomains,
                               Long tunnelDomain,
                               IControllerService controllerProvider,
                               ITunnelManagerService tunnelManager) {

        super(switchPorts, blockedPorts, switchPortLinks,
              broadcastDomainPorts, tunnelPorts);

        destinationRootedTreesMultipath = new HashMap<Long, BroadcastTreeMultipath>();

        broadcastDomains = new HashSet<BroadcastDomain>();
        nodePortBroadcastDomainMap = new HashMap<NodePortTuple, BroadcastDomain>();
        clusterKeyMap = new HashMap<Long, Long>();
        broadcastDomainKeyMap = new HashMap<BroadcastDomain, Long>();

        htNodes = new HashMap<Long, Object>();
        htNeighbors = new HashMap<Long, Set<Long>>();
        htNextHop = new HashMap<Long, Map<Long, Long>>();
        clusterL2DomainIdMap = new HashMap<Long, Long>();

        allowedUnicastPorts = new HashMap<OrderedNodePair, Set<NodePortTuple>>();
        allowedIncomingBroadcastPorts = new HashMap<OrderedNodePair, NodePortTuple>();
        permittedSwitches = new HashMap<NodePortTuple, Set<Long>>();
        permittedPortToBroadcastDomains = new HashMap<NodePortTuple, Set<Long>>();

        // Copy the broadcast domains.
        if (bDomains != null) {
            for (BroadcastDomain bd : bDomains) {
                BroadcastDomain copyBd = new BroadcastDomain();
                copyBd.setId(bd.getId());
                broadcastDomains.add(copyBd);
                Set<NodePortTuple> nptList = bd.getPorts();
                if (nptList == null) continue;
                for (NodePortTuple npt : nptList) {
                    copyBd.add(npt);
                    nodePortBroadcastDomainMap.put(npt, copyBd);
                }
            }
        }

        this.tunnelDomain = tunnelDomain;
        this.controllerProvider = controllerProvider;
        this.tunnelManager = tunnelManager;
    }

    @Override
    public boolean isBroadcastDomainPort(NodePortTuple npt) {
        return nodePortBroadcastDomainMap.containsKey(npt);
    }

    @Override
    public void compute() {
        // Step 0. Create tunnel domain links.
        createTunnelDomainLinks();

        // Step 1. Compute clusters ignoring broadcast domain links
        // Create nodes for clusters in the higher level topology
        // Ignores blocked ports.
        identifyOpenflowDomains();

        // Step 3. Create inter-cluster links in the higher-level
        // topology. This step also assigns intra-cluster links to clusters.
        // Ignores blocked ports.
        addLinksToOpenflowDomains();

        // Step 6. Add cluster nodes to higher-level topology.
        // Create L2 domain.
        // Ignores blocked ports.
        createHigherLevelTopology();

        // Step 7. Given the higher-level topology, compute forest
        // in the higher-level topology. If the higher level topology
        // is connected, then this step will result in a tree.
        // Blocked ports are not relevant here.
        createForestInHigherLevelTopology();
        computeClusterL2DomainMap();

        // Step 8. Classify the switch ports by ordered node pairs
        // in the higher-level topology.
        // Ignores blocked ports.
        classifyPorts();

        /* **************************************************************** */

        // Step 4. Compute shortest path trees in each cluster for
        // unicast routing. The trees are rooted at the destination.
        // Cost for tunnel links and direct links are the same.
        // Blocked ports not relevant.
        calculateShortestPathTreeInClusters();

        // Step 5. Compute broadcast tree in each cluster.
        // Cost for tunnel links are high to discourage use of
        // tunnel links. The cost is set to the number of nodes
        // in the cluster + 1, to use as minimum number of
        // clusters as possible.
        // Blocked ports not relevant.
        calculateBroadcastNodePortsInClusters();

        /* **************************************************************** */

        // Step 9. Compute switches and broadcast switches assigned
        // Blocked ports are not relevant as this procedure
        // considers only those switchports that belong to allowedUnicastPort
        // mapping.
        calculateSwitchPortMappings();

        // Step 10. print topology.
        if (log.isTraceEnabled()) {
            printTopology();
        }
    }

    @Override
    public void printTopology() {
        super.printTopology();
        if (log.isTraceEnabled()) {
            log.trace("-----------------------------------------------");
            log.trace("destinationRootedTreesMultipath: {}",
                      destinationRootedTreesMultipath);
            log.trace("Computed Broadcast Domains: {}", broadcastDomains);
            log.trace("Higher Topology (HT) Nodes: {}", htNodes);
            log.trace("HT neighbors: ", htNeighbors);
            log.trace("HT Reachability: {}", htNextHop);
            log.trace("L2 Domain Ids: {}", clusterL2DomainIdMap);
            log.trace("Allowed Ports for Unicast: {}", allowedUnicastPorts);
            log.trace("Allowed Ports for Broadcast: {}",
                      allowedIncomingBroadcastPorts);
            log.trace("permitted switches:");
            for (NodePortTuple npt : permittedSwitches.keySet()) {
                log.trace("    Port: {}", npt);
                for (long swid : permittedSwitches.get(npt)) {
                    log.trace("        {}", HexString.toHexString(swid));
                }
            }
            log.trace("permitted ports to broadcast domain: {}",
                      permittedPortToBroadcastDomains);
            log.trace("-----------------------------------------------");
        }
    }

    /**
     * Given the tunnel domain switches, this procedure creates
     * bidirectional links between the tunnel domain and other
     * switches that are tunnel enabled.
     */
    private void createTunnelDomainLinks() {

        if (tunnelDomain == null) return;

        if (switches == null) {
            log.trace("No switches to create tunnel links.");
        }

        short tport = 1;
        for(NodePortTuple npt: tunnelPorts) {
            addTunnelLinks(npt.getNodeId(), npt.getPortId(),
                          tunnelDomain.longValue(), tport);
            tport++;
        }

        // Add all the tunnel domain ports to tunnel ports.
        for (short i=1; i<tport; i++) {
            NodePortTuple npt =
                    new NodePortTuple(tunnelDomain.longValue(), i);
            tunnelPorts.add(npt);
        }
    }

    private void addTunnelLinks(long sw1, short port1, long sw2, short port2) {
        NodePortTuple npt1 = new NodePortTuple(sw1, port1);
        NodePortTuple npt2 = new NodePortTuple(sw2, port2);

        // if switches doesn't contain a switch, then add it there.
        // It is possible that switches don't exist as there wer no
        // links identified.
        if (!switches.contains(sw1)) {
            switches.add(sw1);
        }
        if (!switches.contains(sw2)) {
            switches.add(sw2);
        }

        // if switchPorts don't contain the ports, add the ports.
        if (!switchPorts.containsKey(sw1)) {
            switchPorts.put(sw1, new HashSet<Short>());
        }
        if (!switchPorts.containsKey(sw2)) {
            switchPorts.put(sw2, new HashSet<Short>());
        }

        switchPorts.get(sw1).add(port1);
        switchPorts.get(sw2).add(port2);

        // if switchportLinks doesn't contain the keys, add the keys.
        if (!switchPortLinks.containsKey(npt1)) {
            switchPortLinks.put(npt1, new HashSet<Link>());
        }
        if (!switchPortLinks.containsKey(npt2)) {
            switchPortLinks.put(npt2, new HashSet<Link>());
        }

        Link link12 = new Link(sw1, port1, sw2, port2);
        Link link21 = new Link(sw2, port2, sw1, port1);

        // Add the two links to both switch ports.
        switchPortLinks.get(npt1).add(link12);
        switchPortLinks.get(npt2).add(link12);
        switchPortLinks.get(npt1).add(link21);
        switchPortLinks.get(npt2).add(link21);
    }

    @Override
    protected Set<NodePortTuple>
    getBroadcastNodePortsInCluster(long sw) {
        long clusterId = getOpenflowDomainId(sw);
        Set<NodePortTuple> ports = clusterBroadcastNodePorts.get(clusterId);
        ports.removeAll(tunnelPorts);
        return ports;
    }

    private static void
            addToPortMapping(Map<NodePortTuple, Set<Long>> permitted,
                             NodePortTuple p, long s) {
        if (permitted.containsKey(p) == false) {
            permitted.put(p, new HashSet<Long>());
        }
        permitted.get(p).add(s);
    }

    private void calculateSwitchPortMappings() {
        int cost;
        NodePortTuple resultOut;
        NodePortTuple resultIn;

        // for every cluster.
        for(Cluster c: clusters) {
            long n = clusterKeyMap.get(c.getId());
            for(long nbr: htNeighbors.get(n)) {
                OrderedNodePair onp = new OrderedNodePair(n, nbr);
                // We need to compute the assignments only if the
                // nodes are allowed to communicate in the higher level topology
                // If a port was blocked here, then it woudln't have been added
                // to the allowedUnicastPorts. Thus, we don't need to take care
                // of it here.
                if (allowedUnicastPorts.containsKey(onp) == false) continue;

                // map every switch in the cluster to a port.
                for (long s : c.getNodes()) {
                    cost = MAX_PATH_WEIGHT;
                    resultOut = null;
                    for (NodePortTuple npt : allowedUnicastPorts.get(onp)) {
                        if (this.getCost(s, npt.getNodeId()) < cost) {
                            cost = this.getCost(s, npt.getNodeId());
                            resultOut = npt;
                            Long temp = new Long(s *
                                                 npt.getNodeId() *
                                                 npt.getPortId() *
                                                 LONG_PRIME);
                            temp.hashCode();
                        } 
                    }
                    if (resultOut != null) {
                        addToPortMapping(permittedSwitches, resultOut, s);
                    }
                }

                // map every other cluster/broadcast domain to the switch ports
                for (long othernbr : htNeighbors.get(n)) {
                    // the switch-port assignment is decided by the node
                    // with the shortest id in the higher level topology
                    if (othernbr >= nbr) continue;
                    OrderedNodePair otheronp = new OrderedNodePair(othernbr,
                                                                   n);
                    // If a port was blocked here, then it woudln't have been
                    // added
                    // to the allowedUnicastPorts. Thus, we don't need to take
                    // care
                    // of it here.
                    if (allowedUnicastPorts.containsKey(otheronp) == false)
                                                                           continue;
                    cost = MAX_PATH_WEIGHT;
                    resultOut = null;
                    resultIn = null;
                    for (NodePortTuple input : allowedUnicastPorts.get(otheronp)) {
                        for (NodePortTuple output : allowedUnicastPorts.get(onp)) {
                            if (this.getCost(input.getNodeId(),
                                             output.getNodeId()) < cost) {
                                cost = this.getCost(input.getNodeId(),
                                                    output.getNodeId());
                                resultOut = output;
                                resultIn = input;
                                Long temp = new Long(input.getNodeId() *
                                                     input.getPortId() *
                                                     output.getNodeId() *
                                                     output.getPortId() *
                                                     LONG_PRIME);
                                temp.hashCode();
                            } 
                        }
                    }

                    if (resultOut != null) {
                        addToPortMapping(permittedPortToBroadcastDomains,
                                         resultOut, othernbr);
                        addToPortMapping(permittedPortToBroadcastDomains,
                                         resultIn, nbr);
                    }
                }
            }
        }
    }

    /**
     * This method classifies all the ports as either allowedUnicast ports or
     * blocked ports. In addition, it identifies one of the unicast ports to
     * accept incoming broadcast traffic from a broadcast domain.
     */
    protected void classifyPorts() {
        for (long s : switches) {
            if (switchPorts.get(s) == null) continue;
            for (short p : switchPorts.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (switchPortLinks.get(np) == null) continue;
                for (Link l : switchPortLinks.get(np)) {
                    if (isBroadcastDomainLink(l)) {
                        // The link is in broadcast domain, so create
                        Cluster c1 = switchClusterMap.get(l.getSrc());
                        Cluster c2 = switchClusterMap.get(l.getDst());
                        long n1 = clusterKeyMap.get(c1.getId());
                        long n2 = clusterKeyMap.get(c2.getId());

                        NodePortTuple npt1 = new NodePortTuple(
                                                               l.getSrc(),
                                                               l.getSrcPort());
                        NodePortTuple npt2 = new NodePortTuple(
                                                               l.getDst(),
                                                               l.getDstPort());

                        BroadcastDomain bd1 = nodePortBroadcastDomainMap.get(npt1);
                        BroadcastDomain bd2 = nodePortBroadcastDomainMap.get(npt2);
                        if (bd1 == null) {
                            if (log.isTraceEnabled()) {
                                log.trace("No broadcastDomain for a "
                                          + "broadcastPort, {}", npt1);
                            }
                            bd1 = bd2;
                        }

                        if (bd2 == null) {
                            if (log.isTraceEnabled()) {
                                log.trace("No broadcastDomain for a broadcastPort, "
                                                  + "{}", npt2);
                            }
                        }

                        if (bd1 != bd2) {
                            if (log.isTraceEnabled()) {
                                log.trace("BroadcastDomainLink {} connects "
                                                  + "to two different broadcastDomains {} {}",
                                          new Object[] { l, bd1, bd2 });
                            }
                        }
                        long z = broadcastDomainKeyMap.get(bd1);

                        OrderedNodePair onp1z = new OrderedNodePair(n1, z);
                        OrderedNodePair onpz1 = new OrderedNodePair(z, n1);

                        if (allowedUnicastPorts.containsKey(onp1z)) {
                            allowedUnicastPorts.get(onp1z).add(npt1);
                            allowedUnicastPorts.get(onpz1).add(npt1);

                            NodePortTuple prev =
                                    allowedIncomingBroadcastPorts.get(onp1z);

                            if (prev == null || npt1.compareTo(prev) < 0)
                            {
                                allowedIncomingBroadcastPorts.put(onp1z,
                                                                  npt1);
                                allowedIncomingBroadcastPorts.put(onpz1,
                                                                  npt1);
                            }
                        } else {
                            // if the allowed unicast port doesn't contain
                            // this higher level topology link, this switch
                            // port must be blocked.
                            this.blockedPorts.add(npt1);
                        }

                        OrderedNodePair onp2z = new OrderedNodePair(n2, z);
                        OrderedNodePair onpz2 = new OrderedNodePair(z, n2);

                        if (allowedUnicastPorts.containsKey(onp2z)) {
                            allowedUnicastPorts.get(onp2z).add(npt2);
                            allowedUnicastPorts.get(onpz2).add(npt2);

                            NodePortTuple prev =
                                    allowedIncomingBroadcastPorts.get(onp2z);
                            if (prev == null || npt2.compareTo(prev) < 0)
                            {
                                allowedIncomingBroadcastPorts.put(onp2z,
                                                                  npt2);
                                allowedIncomingBroadcastPorts.put(onpz2,
                                                                  npt2);
                            }
                        } else {
                            // if the allowed unicast port doesn't contain
                            // this higher level topology link, this switch
                            // port must be blocked.
                            this.blockedPorts.add(npt2);
                        }
                    } else {
                        Cluster c1 = switchClusterMap.get(l.getSrc());
                        Cluster c2 = switchClusterMap.get(l.getDst());
                        if (c1 == c2) continue;
                        long n1 = clusterKeyMap.get(c1.getId());
                        long n2 = clusterKeyMap.get(c2.getId());

                        NodePortTuple npt1 = new NodePortTuple(
                                                               l.getSrc(),
                                                               l.getSrcPort());
                        NodePortTuple npt2 = new NodePortTuple(
                                                               l.getDst(),
                                                               l.getDstPort());

                        OrderedNodePair onp1 = new OrderedNodePair(n1, n2);
                        OrderedNodePair onp2 = new OrderedNodePair(n2, n1);

                        if (allowedUnicastPorts.containsKey(onp1)) {
                            allowedUnicastPorts.get(onp1).add(npt1);
                            allowedUnicastPorts.get(onp2).add(npt2);

                            NodePortTuple prev =
                                    allowedIncomingBroadcastPorts.get(onp1);
                            if (prev == null || npt1.compareTo(prev) < 0) {
                                allowedIncomingBroadcastPorts.put(onp1, npt1);
                            }

                            prev = allowedIncomingBroadcastPorts.get(onp2);
                            if (prev == null || npt2.compareTo(prev) < 0) {
                                allowedIncomingBroadcastPorts.put(onp2, npt2);
                            }
                        } else {
                            // if the allowed unicast port doesn't contain
                            // this higher level topology link, this switch
                            // port must be blocked.
                            this.blockedPorts.add(npt1);
                            this.blockedPorts.add(npt2);
                        }
                    }
                }
            }
        }
    }

    /**
     * This method computes higher level topology involving clusters and
     * broadcast domains.
     */
    private void createHigherLevelTopology() {
        // Create cluster nodes.
        for (Cluster c : clusters) {
            long nid = htNodes.size() + 1;
            htNodes.put(nid, c);
            clusterKeyMap.put(c.getId(), nid);
        }

        // Create broadcast domain nodes.
        for (BroadcastDomain bd : broadcastDomains) {
            long nid = htNodes.size() + 1;
            htNodes.put(nid, bd);
            broadcastDomainKeyMap.put(bd, nid);
        }

        // clear the neighbors list.
        htNeighbors.clear();

        // create an entry for every node in the higher level topology.
        for (long nid : htNodes.keySet()) {
            htNeighbors.put(nid, new HashSet<Long>());
        }

        // Create links between cluster nodes through unidirectional link
        for (long s : switches) {
            if (switchPorts.get(s) == null) continue;
            for (short p : switchPorts.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (switchPortLinks.get(np) == null) continue;
                if (isBroadcastDomainPort(np)) continue;
                for (Link l : switchPortLinks.get(np)) {
                    // Ignore blocked links.
                    if (isBlockedLink(l)) continue;
                    if (isBroadcastDomainLink(l)) continue;
                    Cluster c1 = switchClusterMap.get(l.getSrc());
                    Cluster c2 = switchClusterMap.get(l.getDst());
                    if (c1 != c2) {
                        long n1 = clusterKeyMap.get(c1.getId());
                        long n2 = clusterKeyMap.get(c2.getId());
                        htNeighbors.get(n1).add(n2);
                        htNeighbors.get(n2).add(n1);
                    }
                }
            }
        }

        // Create links between broadcastdomains and clusters
        for (NodePortTuple npt : nodePortBroadcastDomainMap.keySet()) {
            Cluster c = switchClusterMap.get(npt.getNodeId());
            long cid = clusterKeyMap.get(c.getId());
            BroadcastDomain bd = nodePortBroadcastDomainMap.get(npt);
            long nid = broadcastDomainKeyMap.get(bd);
            htNeighbors.get(cid).add(nid);
            htNeighbors.get(nid).add(cid);
        }
    }

    @Override
    protected void calculateBroadcastTreeInClusters() {
        clusterBroadcastTrees.clear();
        // Make every tunnel link have a weight that's more than the
        // number of switches in the network.
        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = switchPorts.size() + 1;

        for (NodePortTuple npt : tunnelPorts) {
            if (switchPortLinks.get(npt) == null) continue;
            for (Link link : switchPortLinks.get(npt)) {
                if (link == null) continue;
                linkCost.put(link, tunnel_weight);
            }
        }

        for (Cluster c : clusters) {
            BroadcastTree tree = dijkstra(c, c.getId(), linkCost, true);
            clusterBroadcastTrees.put(c.getId(), tree);
        }
    }

    /**
     * For every node (cluster) in the higher level topology, this method
     * computes the smallest cluster id that is connected to it. This is the
     * equivalent of clusterId, considering non-openflow links as well. The id
     * is the smallest switch id that to which a switch has L2 connectivity to.
     */
    private void computeClusterL2DomainMap() {
        // For every node (cluster) in the higher level topology, compute
        // what is the smallest cluster Id it is connected to.
        clusterL2DomainIdMap.clear();
        for (long n : htNodes.keySet()) {
            long l2DomainId;
            // ignore non-openflow domains.
            if (htNodes.get(n) instanceof BroadcastDomain) continue;
            Cluster c = (Cluster) htNodes.get(n);
            // We have computed the L2 domain id already.
            if (clusterL2DomainIdMap.containsKey(n)) continue;

            l2DomainId = c.getId();

            // get the minimum among all the connected openflow clusters
            for (long nbr : htNextHop.get(n).keySet()) {
                if (htNodes.get(nbr) instanceof BroadcastDomain) continue;
                Cluster nbrCluster = (Cluster) htNodes.get(nbr);
                if (nbrCluster.getId() < l2DomainId)
                                                    l2DomainId = nbrCluster.getId();
            }

            // Add to the L2DomainId Map.
            // note that n is also in its neighbor list, so n will also
            // get assigned here.
            for (long nbr : htNextHop.get(n).keySet()) {
                if (htNodes.get(nbr) instanceof BroadcastDomain) continue;
                clusterL2DomainIdMap.put(nbr, l2DomainId);
            }
        }
    }

    protected void createForestInHigherLevelTopology() {
        // Using only neighbors, we have to pull out a
        // forests.
        Set<Long> visitedNodes = new HashSet<Long>();
        Queue<Long> queue = new LinkedList<Long>();

        // Let's do a breadth-first search here.
        for (long s : htNeighbors.keySet()) {
            if (visitedNodes.contains(s)) continue;
            queue.clear();
            queue.add(s);
            visitedNodes.add(s);

            while (queue.peek() != null) {
                long u = queue.remove();
                for (long nbr : htNeighbors.get(u)) {
                    if (visitedNodes.contains(nbr) == false) {
                        visitedNodes.add(nbr);
                        queue.add(nbr);
                        OrderedNodePair onp1 = new OrderedNodePair(u, nbr);
                        OrderedNodePair onp2 = new OrderedNodePair(nbr, u);
                        if (allowedUnicastPorts.containsKey(onp1) == false) {
                            allowedUnicastPorts.put(onp1,
                                                    new HashSet<NodePortTuple>());
                        }
                        if (allowedUnicastPorts.containsKey(onp2) == false) {
                            allowedUnicastPorts.put(onp2,
                                                    new HashSet<NodePortTuple>());
                        }
                    }
                }
            }
        }

        // Once the forest is computed, now compute how every node can reach
        // every other node using only the forest computed in the higher
        // level topology.
        for (long s : htNeighbors.keySet()) {
            Map<Long, Long> nh = new HashMap<Long, Long>();
            htNextHop.put(s, nh);
            nh.put(s, s); // self

            for (long nbr : htNeighbors.get(s)) {
                // ignore reachability through this neighbor if
                // the node pair is not in the allowedports key
                OrderedNodePair onp_s_nbr = new OrderedNodePair(s, nbr);
                if (allowedUnicastPorts.containsKey(onp_s_nbr) == false) {
                    continue;
                }

                nh.put(nbr, nbr); // add the first hop neighbor

                // the following two statements could be put outside the
                // for(long:nbr.. loop as well.
                // add node s to the visitedNodes to ignore nbr -> s.
                visitedNodes.clear();
                visitedNodes.add(s);

                // run a breadth-first search from nbr, considering
                // all neighbors except s. [s is already added to visited.]
                visitedNodes.add(nbr);
                queue.clear();
                queue.add(nbr);
                while (queue.peek() != null) {
                    long u = queue.remove();
                    for (long v : htNeighbors.get(u)) {
                        // ignore reachability through this neighbor if
                        // the node pair is not in the allowedPorts key
                        OrderedNodePair onp_uv = new OrderedNodePair(u, v);
                        if (allowedUnicastPorts.containsKey(onp_uv) == false) {
                            continue;
                        }

                        if (visitedNodes.contains(v)) continue;
                        visitedNodes.add(v);
                        queue.add(v);
                        nh.put(v, nbr); // v can be reached through nbr from s.
                    }
                }
            }
        }
    }

    private long getHTNodeId(long sw, short port) {
        long result = -1;
        NodePortTuple npt = new NodePortTuple(sw, port);
        if (isBroadcastDomainPort(npt)) {
            // this is a broadcast domain port
            BroadcastDomain bd = nodePortBroadcastDomainMap.get(npt);
            result = broadcastDomainKeyMap.get(bd);
        } else if (switchPortLinks.containsKey(npt)) {
            // there is another cluster this link connects to
            // we need the other cluster id
            // look at the first link and get the other switch port
            // and find out where it belongs to.
            NodePortTuple otherNpt;
            for (Link l : switchPortLinks.get(npt)) {
                if (l.getSrc() == npt.getNodeId()
                    && l.getSrcPort() == npt.getPortId()) {
                    otherNpt = new NodePortTuple(l.getDst(), l.getDstPort());
                } else {
                    otherNpt = new NodePortTuple(l.getSrc(), l.getSrcPort());
                }

                // otherNpt cannot belong to broadcast doamain.
                // as we have already computed broadcast domain ports
                // based on reachability
                Cluster c = switchClusterMap.get(otherNpt.getNodeId());
                if (c==null)  result = -1;
                else result = clusterKeyMap.get(c.getId());
            }
        } else {
            // this is not a port known to topology
            Cluster c = switchClusterMap.get(sw);
            if (c==null)  result = -1;
            else result = clusterKeyMap.get(c.getId());
        }
        return result;
    }

    //
    // Public methods for accessing some data structures
    //
    protected Set<BroadcastDomain> getBroadcastDomains() {
        return broadcastDomains;
    }

    protected Set<NodePortTuple> getBroadcastDomainPorts(NodePortTuple npt) {
        if (npt == null) return null;
        BroadcastDomain bd = nodePortBroadcastDomainMap.get(npt);
        if (bd == null) return null;
        return (bd.getPorts());
    }

    protected Map<Long, Object> getHTNodes() {
        return htNodes;
    }

    protected Map<Long, Set<Long>> getHTNeighbors() {
        return htNeighbors;
    }

    protected Map<Long, Map<Long, Long>> getHTNextHops() {
        return htNextHop;
    }

    protected Set<OrderedNodePair> getAllowedNodePairs() {
        return allowedUnicastPorts.keySet();
    }

    protected Map<Long, Map<Long, Long>> getNextHopMap() {
        return htNextHop;
    }

    protected Map<OrderedNodePair, Set<NodePortTuple>> getAllowedPorts() {
        return allowedUnicastPorts;
    }

    protected Map<OrderedNodePair, NodePortTuple>
            getAllowedIncomingBroadcastPorts() {
        return allowedIncomingBroadcastPorts;
    }

    protected Map<Long, Long> getL2DomainIds() {
        return clusterL2DomainIdMap;
    }

    protected Map<NodePortTuple, Set<Long>>
            getAllowedPortsToBroadcastDomains() {
        return permittedPortToBroadcastDomains;
    }

    //
    // ITopologyService interface method helpers
    //
    @Override
    public boolean isAttachmentPointPort(long switchid, short port) {
        NodePortTuple npt = new NodePortTuple(switchid, port);

        // if the switchport is not known to topology, then it is an
        // attachment point port (i.e. if the port is not an internal port - a
        // port on direct OF links)
        if (switchPortLinks.containsKey(npt) == false) return true;

        // the switchport is known to topology.
        // A switch port belonging to a broadcast domain is an
        // attachment point port.
        if (isBroadcastDomainPort(npt)) return true;

        // switch port is not in broadcast domain.
        // Thus, the switch port has either one direct link or two
        // direct links.
        // If it has only one link, then we need to check if the
        // other switch belongs to the same broadcast domain or not.
        // Even though this is a for loop, there's only one element
        // in the set.
        if (switchPortLinks.get(npt).size() == 1) {
            for (Link link : switchPortLinks.get(npt)) {
                if (inSameOpenflowDomain(link.getSrc(), link.getDst()) == false)
                    return true;
            }
        }

        return false;
    }

    @Override
    protected Set<Long> getSwitchesInOpenflowDomain(long switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) {
            // The switch is not known to topology as there
            // are no links connected to it.
            Set<Long> nodes = new HashSet<Long>();
            nodes.add(switchId);
            return nodes;
        }
        Set<Long> nodes = c.getNodes();
        nodes.remove(tunnelDomain);
        return (nodes);
    }

    /**
     * Indicates if the given switch port is allowed for sending/receiving
     * packets by the higher-level topology or not.
     */
    @Override
    public boolean isAllowed(long sw, short portId) {
        NodePortTuple npt = new NodePortTuple(sw, portId);

        if (blockedPorts.contains(npt)) return false;
        /*
         * if (nodePortBroadcastDomainMap.containsKey(npt)) { BroadcastDomain bd
         * = nodePortBroadcastDomainMap.get(npt); long n1 =
         * broadcastDomainKeyMap.get(bd); long n =
         * clusterKeyMap.get(switchClusterMap.get(sw)); OrderedNodePair onp =
         * new OrderedNodePair(n, n1); return
         * (allowedUnicastPorts.containsKey(onp)); }
         */

        return true;
    }

    @Override
    protected boolean isIncomingBroadcastAllowedOnSwitchPort(long sw,
                                                             short portId) {
        NodePortTuple npt = new NodePortTuple(sw, portId);

        if (isInternalToOpenflowDomain(sw, portId)) {
            long clusterId = getOpenflowDomainId(sw);
            if (clusterBroadcastNodePorts.get(clusterId).contains(npt))
                return true;
            else
                return false;
        } else if (nodePortBroadcastDomainMap.containsKey(npt)) {
            long n = clusterKeyMap.get(switchClusterMap.get(sw).getId());
            for(long nbr: htNeighbors.get(n)) {
                OrderedNodePair onp = new OrderedNodePair(n, nbr);
                NodePortTuple othernpt = allowedIncomingBroadcastPorts.get(onp);
                if (othernpt != null) {
                    if (othernpt.equals(npt)) return true;
                }
            }
            return false;
        }
        return true;
    }

    private NodePortTuple
            getPermitted(OrderedNodePair onp,
                         Map<NodePortTuple, Set<Long>> permitted, long s) {
        if (allowedUnicastPorts.get(onp) != null) {
            for (NodePortTuple npt : allowedUnicastPorts.get(onp)) {
                Set<Long> pSet = permitted.get(npt);
                if (pSet == null) continue;
                if (pSet.contains(s)) return npt;
            }
        }
        return null;
    }

    @Override
    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        List<NodePortTuple> nptList = buildNodePortList(src, srcPort, dst,
                                                        dstPort, 0); //cookie = 0 for now as it does not alter incoming port
        if (nptList == null || nptList.size() == 0) {
            // if the nptList is null or nptList is zero size, then
            // a path doesn't exist, if the two switches are different.
            if (src != dst) {
                return null;
            } else
                return new NodePortTuple(src, srcPort);
        } else
            return nptList.get(0);
    }

    @Override
    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        List<NodePortTuple> nptList = buildNodePortList(src, srcPort, dst,
                                                        dstPort, 0); //cookie = 0 for now as it does not alter incoming port
        if (nptList == null || nptList.size() == 0) {
            // if the nptList is null or nptList is zero size, then
            // a path doesn't exist, if the two switches are different.
            if (src != dst) {
                return null;
            } else
                return new NodePortTuple(dst, dstPort);
        } else
            return nptList.get(nptList.size() - 1);
    }

    @Override
    protected long getL2DomainId(long switchId) {
        // return getOpenflowDomainId(switchId);

        Cluster c = switchClusterMap.get(switchId);
        // if c is null, then this is a stand-alone switch with no links.
        if (c == null) return switchId;
        Long n = clusterKeyMap.get(c.getId());
        return clusterL2DomainIdMap.get(n);

    }

    @Override
    public boolean inSameL2Domain(long switch1, long switch2) {
        // These are in the same island, if the clusters to
        // which the two switches belong to are connected in
        // the higher-level topology.
        if (switch1 == switch2) return true;

        Cluster c1 = switchClusterMap.get(switch1);
        Cluster c2 = switchClusterMap.get(switch2);
        if (c1 == null || c2 == null) return false;

        long n1 = clusterKeyMap.get(c1.getId());
        long n2 = clusterKeyMap.get(c2.getId());

        Map<Long, Long> rMap = htNextHop.get(n1);
        if (rMap == null) return false;
        if (rMap.containsKey(n2) == false) return false;

        return true;
    }

    @Override
    public boolean
            inSameBroadcastDomain(long s1, short p1, long s2, short p2) {
        NodePortTuple npt1 = new NodePortTuple(s1, p1);
        NodePortTuple npt2 = new NodePortTuple(s2, p2);
        BroadcastDomain bd1, bd2;
        bd1 = nodePortBroadcastDomainMap.get(npt1);
        bd2 = nodePortBroadcastDomainMap.get(npt2);
        if (bd1 == null || bd2 == null)
            return (s1 == s2 && p1 == p2);
        else
            return (bd1 == bd2);
    }

    public Set<Short> getBroadcastDomainPorts(long sw) {
        Set<Short> result = new HashSet<Short>();
        for (NodePortTuple npt : nodePortBroadcastDomainMap.keySet()) {
            if (npt.getNodeId() == sw) {
                result.add(npt.getPortId());
            }
        }
        return result;
    }

    /**
     * Given a target switch and a source attachment point (src, srcPort),
     * compute the ports on the targetSw where this packet should be broadcast.
     * Note that src,srcPort may not be in the same openflow domain as the
     * target switch.
     */
    @Override
    public Set<Short> getBroadcastPorts(long targetSw, long src,
                                        short srcPort) {
        Set<Short> result = new HashSet<Short>();
        NodePortTuple nptSrc;
        long n1;

        // Get the consistent attachment point for the source in the
        // cluster containing targetSw.
        nptSrc = getConsistentBroadcastAttachmentPoint(targetSw, src,
                                                       srcPort);
        if (nptSrc == null) return result;
        if (inSameOpenflowDomain(targetSw, nptSrc.getNodeId()) == false)
                                                                        return result;

        /*
        // Add the internal broadcast ports
        long clusterId = getOpenflowDomainId(targetSw);
        if (clusterBroadcastNodePorts.get(clusterId) != null)
            for(NodePortTuple npt: clusterBroadcastNodePorts.get(clusterId)) {
                if (npt.getNodeId() == targetSw) {
                    result.add(npt.getPortId());
                }
            }
         */

        // Add external broadcast ports
        if (isBroadcastDomainPort(nptSrc)) {
            BroadcastDomain bd = nodePortBroadcastDomainMap.get(nptSrc);
            long bdId = broadcastDomainKeyMap.get(bd);
            Cluster c = switchClusterMap.get(targetSw);
            if (c!=null) {
                n1 = clusterKeyMap.get(c.getId());

                if (htNeighbors.get(n1) != null)
                                                for (long nbr : htNeighbors.get(n1)) {
                                                    // for each higher-level
                                                    // node
                                                    OrderedNodePair onp = new OrderedNodePair(
                                                                                              n1,
                                                                                              nbr);
                                                    NodePortTuple x = getPermitted(onp,
                                                                                   permittedPortToBroadcastDomains,
                                                                                   bdId);
                                                    if (x == null) continue;
                                                    if (x.getNodeId() == targetSw)
                                                                                  result.add(x.getPortId());
                                                }
            }
        } else {
            // if the srcPort is not a broadcast domain port.
            Cluster c = switchClusterMap.get(src);
            if (c!=null) {
                n1 = clusterKeyMap.get(c.getId());

                if (htNeighbors.get(n1) != null)
                                                for (long nbr : htNeighbors.get(n1)) {
                                                    // for each higher-level
                                                    // node
                                                    OrderedNodePair onp = new OrderedNodePair(
                                                                                              n1,
                                                                                              nbr);
                                                    NodePortTuple x = getPermitted(onp,
                                                                                   permittedSwitches,
                                                                                   src);
                                                    if (x == null) continue;
                                                    if (x.getNodeId() == targetSw)
                                                                                  result.add(x.getPortId());
                                                }
            }
        }
        return result;
    }

    @Override
    public NodePortTuple getAllowedIncomingBroadcastPort(long src,
                                                         short srcPort) {
        NodePortTuple resultNpt = null;
        NodePortTuple srcNpt = new NodePortTuple(src, srcPort);

        /**
         * If the src is not a broadcast domain port, return null.
         */
        if (!nodePortBroadcastDomainMap.containsKey(srcNpt)) {
            return null;
        }

        Cluster srcCluster = switchClusterMap.get(src);
        if (srcCluster == null) return null;
        long htSrcClusterId = clusterKeyMap.get(srcCluster.getId());

        long htSrc = getHTNodeId(src, srcPort);
        if (htSrc < 0) return null;

        OrderedNodePair onp = new OrderedNodePair(htSrc, htSrcClusterId);
        if (htSrc == htSrcClusterId) {
            /**
             * This should not happen since src is a broadcast domain port,
             * which connects a broadcast domain to the local cluster.
             */
            log.warn("BroadcastDomain port {} {} without any broadcast domain",
                     HexString.toHexString(src), srcPort);
            resultNpt = srcNpt;
        } else {
            resultNpt = allowedIncomingBroadcastPorts.get(onp);
        }

        return resultNpt;
    }

    @Override
    public NodePortTuple getAllowedOutgoingBroadcastPort(long src,
                                                         short srcPort,
                                                         long dst,
                                                         short dstPort) {
        NodePortTuple resultNpt = null;

        Cluster srcCluster = switchClusterMap.get(src);
        if (srcCluster == null) {
            NodePortTuple dstNpt = new NodePortTuple(dst, dstPort);
            if (src == dst)
                return dstNpt;
            else
                return null;
        }
        long htSrcClusterId = clusterKeyMap.get(srcCluster.getId());

        long htSrc = getHTNodeId(src, srcPort);
        long htDst = getHTNodeId(dst, dstPort);
        if (htSrc < 0 || htDst < 0) return null;

        /**
         * in the higher level topology htSrc is trying to reach htDst through
         * the cluster htSrcClusterId htSrc may be the same as htSrcClusterId
         * The packet-in is from cluster htSrcClusterId If we have to go from
         * htSrcClusterId to htDst, what's the nexthop?
         */
        Map<Long, Long> rMap = htNextHop.get(htSrcClusterId);
        if (rMap == null) return null;
        if (rMap.containsKey(htDst) == false) return null;
        long nextHopNodeId = rMap.get(htDst);
        OrderedNodePair onp = new OrderedNodePair(htSrcClusterId,
                                                  nextHopNodeId);

        if (htSrcClusterId == htDst) {
            NodePortTuple dstNpt = new NodePortTuple(dst, dstPort);
            if (!isBroadcastDomainPort(dstNpt)) {
                return dstNpt;
            }
        }

        if (htSrc == htSrcClusterId) {
            /**
             * if src switch is an internal switch, get the permitted outgoing
             * port to the destination broadcast domain.
             */
            resultNpt = getPermitted(onp, permittedSwitches, src);
        } else {
            /**
             * If src switch belongs to a broadcast domain, x, and dst belongs
             * to a different broadcast domain, y, then get the permitted
             * incoming nodePort, z, for traffic from x -> y, then look up the
             * permitted outgoing port to y for z.
             */
            resultNpt = getPermitted(onp, permittedPortToBroadcastDomains,
                                     htSrc);
            if (resultNpt != null) {
                resultNpt = getPermitted(onp, permittedSwitches,
                                         resultNpt.getNodeId());
            }
        }

        return resultNpt;
    }

    @Override
    public boolean isConsistent(long oldSw, short oldPort, long newSw,
                                short newPort) {
        NodePortTuple newNpt = new NodePortTuple(newSw, newPort);

        if (oldSw == newSw && oldPort == newPort) return true;
        if (isInternalToOpenflowDomain(newSw, newPort)) return true;
        if (!isBroadcastDomainPort(newNpt)) return false;

        Cluster newCluster = switchClusterMap.get(newSw);
        if (newCluster == null) return false;
        long htNewClusterId = clusterKeyMap.get(newCluster.getId());

        long htOld = getHTNodeId(oldSw, oldPort);
        long htNew = getHTNodeId(newSw, newPort);
        if (htOld < 0 || htNew < 0) return false;

        Map<Long, Long> rMap = htNextHop.get(htNewClusterId);
        if (rMap == null) return false;
        if (rMap.containsKey(htOld) == false) return false;
        long nextHopNodeId = rMap.get(htOld);

        return (htNew == nextHopNodeId);
    }

    /**
     * Returns the path from the higher level topology
     *
     * @param htSrc
     * @param htDst
     * @return
     */
    List<Long> getHTPath(long htSrc, long htDst) {
        List<Long> result = new ArrayList<Long>();
        if (htNextHop.get(htSrc) == null) return null;
        if (htNextHop.get(htSrc).get(htDst) == null) return null;

        result.add(htSrc);
        long currNode = htSrc;
        while (currNode != htDst) {
            currNode = htNextHop.get(currNode).get(htDst);
            result.add(currNode);
        }
        return result;
    }

    private void
            addNodePortsToList(List<NodePortTuple> nptList, Route route) {
        if (route == null) return;
        /*
         * List<Link> links = route.getPath(); for(int i=0; i<links.size(); ++i)
         * { NodePortTuple npt; npt = new NodePortTuple(links.get(i).getSrc(),
         * links.get(i).getSrcPort()); nptList.add(npt); npt = new
         * NodePortTuple(links.get(i).getDst(), links.get(i).getDstPort());
         * nptList.add(npt); }
         */
        nptList.addAll(route.getPath());
    }

    protected List<NodePortTuple> getFirstHopRoute(long sw, long htNode, long cookie) {
        // The goal here is to compute the path from the switch
        // to the output port connected to the htNode.
        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();

        // get the output nodeport tuple.
        Cluster c1 = switchClusterMap.get(sw);
        if (c1 == null) return null;
        long n1 = clusterKeyMap.get(c1.getId());

        OrderedNodePair onp = new OrderedNodePair(n1, htNode);
        NodePortTuple dstNpt = getPermitted(onp, permittedSwitches, sw);
        if (dstNpt == null) return null;

        // get the path from sw to dstNpt.node
        Route route = getRoute(sw, dstNpt.getNodeId(), cookie);
        addNodePortsToList(nptList, route);

        nptList.add(dstNpt);

        return nptList;
    }

    /**
     * This method computes the last hop route to the destination switch. htNode
     * is the top-level node that is before the cluster to which switch sw
     * belongs to.
     *
     * @param htNode
     * @param sw
     * @return
     */
    protected List<NodePortTuple> getLastHopRoute(long htNode, long sw, long cookie) {
        // The goal here is to compute the path from the switch
        // to the output port connected to the htNode.
        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();

        // get the output nodeport tuple.
        Cluster c1 = switchClusterMap.get(sw);
        if (c1 == null) return null;
        long n1 = clusterKeyMap.get(c1.getId());

        OrderedNodePair onp = new OrderedNodePair(n1, htNode);
        NodePortTuple srcNpt = getPermitted(onp, permittedSwitches, sw);
        if (srcNpt == null) return null;

        nptList.add(srcNpt);

        // get the path from sw to dstNpt.node
        Route route = getRoute(srcNpt.getNodeId(), sw, cookie);
        addNodePortsToList(nptList, route);
        return nptList;
    }

    /**
     * This method computes the route from [fromNode] to [toNode] in the higher
     * level topology through the [throughNode] in the higher level topology
     * (which is a cluster). The route is a sequence of nodeports, which
     * includes the correct ingress and egress nodeports to be used for the
     * traffic to enter and exit. This method is intended to be used for only
     * adjacent nodes in the higher-level topology. Thus, if [fromNode] and
     * [toNode] are not connected to the cluster [throughNode], it will return
     * null.
     *
     * @param fromNode
     * @param toNode
     * @param throughNode
     * @return
     */
    protected List<NodePortTuple> getRouteThroughCluster(long fromNode,
                                                         long toNode,
                                                         long throughNode, long cookie) {

        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();

        OrderedNodePair onp1 = new OrderedNodePair(throughNode, toNode);
        OrderedNodePair onp2 = new OrderedNodePair(throughNode, fromNode);
        // get the ingress switchport.
        NodePortTuple dstNpt = getPermitted(onp1,
                                            permittedPortToBroadcastDomains,
                                            fromNode);
        NodePortTuple srcNpt = getPermitted(onp2,
                                            permittedPortToBroadcastDomains,
                                            toNode);

        if (srcNpt == null || dstNpt == null) return null;

        // add the source nodeports.
        nptList.add(srcNpt);

        // Get the route and add intermediate nodeports.
        Route route = getRoute(srcNpt.getNodeId(), dstNpt.getNodeId(), cookie);
        addNodePortsToList(nptList, route);

        // Add the egress switch port.
        nptList.add(dstNpt);

        return nptList;
    }

    protected List<NodePortTuple> multiroute(long srcId, long dstId, long cookie) {
        return multiroute(srcId, false, dstId, false, cookie);
    }

    // Overrides the one in TopologyInstance.
    protected List<NodePortTuple> multiroute(long srcId, boolean srcBDFlag,
                                             long dstId, boolean dstBDFlag, long cookie) {

        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
        long n1, n2;
        Cluster c1 = null, c2 = null;

        // if the two switches are not in the same L2 domain,
        // there should not be a path.
        if (srcBDFlag == false && dstBDFlag == false
            && inSameL2Domain(srcId, dstId) == false) {
            return null;
        }

        if (srcBDFlag == false) {
            c1 = switchClusterMap.get(srcId);
            if (c1 == null) return null;
            n1 = clusterKeyMap.get(c1.getId());
        } else {
            if (htNodes.get(srcId) instanceof BroadcastDomain == false)
                                                                       return null;
            BroadcastDomain bd = (BroadcastDomain) htNodes.get(srcId);
            n1 = broadcastDomainKeyMap.get(bd);
        }

        if (dstBDFlag == false) {
            c2 = switchClusterMap.get(dstId);
            if (c2 == null) return null;
            n2 = clusterKeyMap.get(c2.getId());
        } else {
            if (htNodes.get(dstId) instanceof BroadcastDomain == false)
                                                                       return null;
            BroadcastDomain bd = (BroadcastDomain) htNodes.get(dstId);
            n2 = broadcastDomainKeyMap.get(bd);
        }

        // Both are switches and are in the same openflow domain.
        if (srcBDFlag == false && dstBDFlag == false
            && c1.getId() == c2.getId()) // get the direct path.
        {
            Route route = getRoute(srcId, dstId, cookie);
            addNodePortsToList(nptList, route);
            return nptList;
        }

        List<Long> htPath = getHTPath(n1, n2);

        // if the first ht-node is broadcast domain, we don't have
        // to anything.
        // if the first ht-node is a cluster, then we have to compute
        // a path from the switch to the 2nd switch.
        if (htNodes.get(htPath.get(0)) instanceof BroadcastDomain == false) {
            nptList.addAll(getFirstHopRoute(srcId, htPath.get(1), cookie));
        }

        // skip the first and the last nodes.
        for (int i = 1; i < htPath.size() - 1; ++i) {
            if (htNodes.get(htPath.get(i)) instanceof BroadcastDomain)
                                                                      continue;
            List<NodePortTuple> list;
            list = getRouteThroughCluster(htPath.get(i - 1),
                                          htPath.get(i + 1), htPath.get(i), cookie);
            if (list != null)
                nptList.addAll(list);
            else if (log.isTraceEnabled()) {
                log.trace("No route found src id {}, dst id {}",
                          HexString.toHexString(srcId),
                          HexString.toHexString(dstId));
            }
        }

        if (htNodes.get(htPath.get(htPath.size() - 1)) instanceof BroadcastDomain == false) {
            nptList.addAll(getLastHopRoute(htPath.get(htPath.size() - 2),
                                           dstId, cookie));
        }

        // At this point, we have the entire route that we need.
        // we can create a route.
        return nptList;
    }

    @Override
    protected Route getRoute(long srcId, short srcPort, long dstId,
                             short dstPort, long cookie) {

        // Return null the route source and desitnation are the
        // same switchports.
        if (srcId == dstId && srcPort == dstPort) return null;

        List<NodePortTuple> nptList = buildNodePortList(srcId, srcPort,
                                                        dstId, dstPort, cookie);

        // nptList should already include source-destination ports.
        if (nptList == null) return null;

        RouteId id = new RouteId(srcId, dstId);
        return new Route(id, nptList);
    }

    /**
     * The route is from a source switch port to a destination port.
     *
     * @param srcId
     * @param srcPort
     * @param dstId
     * @param dstPort
     * @return
     */
    protected List<NodePortTuple>
            buildNodePortList(long srcId, short srcPort, long dstId,
                              short dstPort, long cookie) {

        long n1, n2;
        boolean srcBDFlag, dstBDFlag;

        NodePortTuple srcNpt = new NodePortTuple(srcId, srcPort);
        NodePortTuple dstNpt = new NodePortTuple(dstId, dstPort);

        if (isBroadcastDomainPort(srcNpt) == true) {
            BroadcastDomain bd = nodePortBroadcastDomainMap.get(srcNpt);
            n1 = broadcastDomainKeyMap.get(bd);
            srcBDFlag = true;
        } else {
            n1 = srcId;
            srcBDFlag = false;
        }

        if (isBroadcastDomainPort(dstNpt) == true) {
            BroadcastDomain bd = nodePortBroadcastDomainMap.get(dstNpt);
            n2 = broadcastDomainKeyMap.get(bd);
            dstBDFlag = true;
        } else {
            n2 = dstId;
            dstBDFlag = false;
        }

        List<NodePortTuple> nptList = multiroute(n1, srcBDFlag, n2,
                                                 dstBDFlag, cookie);

        if (nptList == null && srcId != dstId) return null;

        if (nptList == null) {
            nptList = new ArrayList<NodePortTuple>();
        }

        if (srcBDFlag == false) {
            nptList.add(0, srcNpt);
        }

        if (dstBDFlag == false) {
            nptList.add(dstNpt);
        }

        return nptList;
    }

    /**
     * This method returns the attachment for the device on the cluster in the
     * cluster where targetSw is present.
     *
     * @param apSwitch
     * @param apPort
     * @param targetSw
     * @return
     */
    protected
            NodePortTuple
            getConsistentBroadcastAttachmentPoint(long targetSwitch,
                                                  long apSwitch, short apPort) {

        if (isAttachmentPointPort(apSwitch, apPort) == false) return null;

        long apNode, targetNode;
        Cluster targetCluster = switchClusterMap.get(targetSwitch);
        NodePortTuple npt = new NodePortTuple(apSwitch, apPort);

        if (nodePortBroadcastDomainMap.containsKey(npt)) {
            // the node port belongs to a broadcast domain.
            if (targetCluster == null) return null;

            BroadcastDomain bd = nodePortBroadcastDomainMap.get(npt);
            apNode = this.broadcastDomainKeyMap.get(bd);

        } else {
            // the node port belongs to a cluster.
            Cluster apCluster = switchClusterMap.get(apSwitch);
            if (apSwitch == targetSwitch && apCluster == null) {
                return npt;
            }

            // if either one is null, then return null
            if (apCluster == null || targetCluster == null) return null;

            // if targetCluster and apCluster are the same, then
            // apSwitch, apPort should be the attachment point port
            // in that cluster.
            if (targetCluster.getId() == apCluster.getId()) {
                if (this.isAttachmentPointPort(apSwitch, apPort))
                    return npt;
                else
                    return null;
            }
            apNode = clusterKeyMap.get(apCluster.getId());
        }
        // at this time, we have the apNode.
        // get the targetNode.
        targetNode = clusterKeyMap.get(targetCluster.getId());

        // Get the higher-level path from apNode to targetNode
        List<Long> htPath = this.getHTPath(apNode, targetNode);

        // These two are not in the same L2 domain.
        if (htPath == null) return null;

        // the path should consist of at least two nodes.
        int pathLength = htPath.size();

        // we need to get the correct switchport to go from
        // htPath[pathlength-2] to htPath[pathlength-1].
        // Since we are dealing with two nodes in higher level
        // topology, we can get the right switchport from
        // the broadcast domain.
        OrderedNodePair onp = new OrderedNodePair(
                                                  htPath.get(pathLength - 1),
                                                  htPath.get(pathLength - 2));

        return this.allowedIncomingBroadcastPorts.get(onp);
    }

    protected BroadcastTreeMultipath dijkstraMultipath(Cluster c, Long root,
                                             Map<Link, Integer> linkCost,
                                             boolean isDstRooted) {
        HashMap<Long, ArrayList<Link>> nexthoplinks = new HashMap<Long, ArrayList<Link>>();
        HashMap<Long, Integer> cost = new HashMap<Long, Integer>();
        int w;

        for (Long node : c.getLinks().keySet()) {
            nexthoplinks.put(node, new ArrayList<Link>());
            cost.put(node, MAX_PATH_WEIGHT);
        }

        // HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
        PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
        nodeq.add(new NodeDist(root, 0));
        cost.put(root, 0);
        while (nodeq.peek() != null) {
            NodeDist n = nodeq.poll();
            Long cnode = n.getNode();
            int cdist = n.getDist();
            if (cdist >= MAX_PATH_WEIGHT) break;

            // not checking seen nodes can cause sub-optimal tree
            // in some cases
            // if (seen.containsKey(cnode))
            // continue;
            // seen.put(cnode, true);

            for (Link link : c.getLinks().get(cnode)) {
                Long neighbor;

                if (isDstRooted == true)
                    neighbor = link.getSrc();
                else
                    neighbor = link.getDst();

                if (linkCost == null || linkCost.get(link) == null)
                    w = 1;
                else
                    w = linkCost.get(link);

                int ndist = cdist + w; // the weight of the link, always 1 in
                                       // current version of sdnplatform.

                if (ndist < cost.get(neighbor)) {
                    cost.put(neighbor, ndist);
                    nexthoplinks.get(neighbor).clear();
                    nexthoplinks.get(neighbor).add(link);
                    nodeq.add(new NodeDist(neighbor, ndist));
                } else if (ndist == cost.get(neighbor)) {
                    nexthoplinks.get(neighbor).add(link);
                    nodeq.add(new NodeDist(neighbor, ndist));
                }
            }
        }

        // sorting multipath links
        for (Long node : c.getLinks().keySet()) {
                Collections.sort(nexthoplinks.get(node));
        }

        BroadcastTreeMultipath ret = new BroadcastTreeMultipath(nexthoplinks, cost);
        return ret;
    }

    @Override
    protected void calculateShortestPathTreeInClusters() {
        pathcache.invalidateAll();
        destinationRootedTrees.clear();
        destinationRootedTreesMultipath.clear();

        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = switchPorts.size() + 1;

        for (NodePortTuple npt : tunnelPorts) {
            if (switchPortLinks.get(npt) == null) continue;
            for (Link link : switchPortLinks.get(npt)) {
                if (link == null) continue;
                linkCost.put(link, tunnel_weight);
            }
        }

        for (Cluster c : clusters) {
            for (Long node : c.getLinks().keySet()) {
                BroadcastTree tree = dijkstra(c, node, linkCost, true);
                BroadcastTreeMultipath treeMultipath = dijkstraMultipath(c, node, linkCost,
                                                          true);
                destinationRootedTrees.put(node, tree);

                destinationRootedTreesMultipath.put(node, treeMultipath);
            }
        }
    }

    @Override
    protected Route buildroute(RouteId id) {
        NodePortTuple npt;
        int routeCount = 0;
        long srcId = id.getSrc();
        long dstId = id.getDst();
        long cookie = id.getCookie();

        LinkedList<NodePortTuple> switchPorts = new LinkedList<NodePortTuple>();

        if (destinationRootedTreesMultipath == null) return null;
        if (destinationRootedTreesMultipath.get(dstId) == null) return null;

        HashMap<Long, ArrayList<Link>> nexthoplinks = destinationRootedTreesMultipath.get(dstId)
              .getLinks();
        if (log.isTraceEnabled()) {
            log.trace("buildrouteMultipath: find multipath for srcId {} to dstId {} cookie "
                    + cookie, HexString.toHexString(srcId), HexString.toHexString(dstId));
        }
        if (!switches.contains(srcId) || !switches.contains(dstId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not
            // in the network)
            if (log.isTraceEnabled()) {
                log.info("buildrouteMultipath: Standalone switch: {}", srcId);
            }
            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []

        } else if ((nexthoplinks != null) && (nexthoplinks.get(srcId) != null)) {
            while (srcId != dstId) {
                int choicesThisHop = nexthoplinks.get(srcId).size();

                long linkIndex = cookie % choicesThisHop;
                Link l = nexthoplinks.get(srcId).get((int) linkIndex);

                routeCount = (routeCount > choicesThisHop) ? routeCount : choicesThisHop;

                npt = new NodePortTuple(l.getSrc(), l.getSrcPort());
                switchPorts.addLast(npt);
                npt = new NodePortTuple(l.getDst(), l.getDstPort());
                switchPorts.addLast(npt);

                // proceed to next hop
                srcId = l.getDst();
            }
        }
        // else, no path exists, and path equals null

        // Eliminate any tunnel domain switch ports from the list.
        LinkedList<NodePortTuple> resultPorts = new LinkedList<NodePortTuple>();
        for(int i=0; i<switchPorts.size(); ++i) {
            NodePortTuple sp = switchPorts.get(i);
            if (sp.getNodeId() == tunnelDomain.longValue()) continue;
            resultPorts.addLast(sp);
        }
        switchPorts = resultPorts;
        // tunnel domain switch ports eliminated.

        Route result = null;
        if (switchPorts != null && !switchPorts.isEmpty()) {
            result = new Route(id, switchPorts);
            // set routeCount to record total available routes; useful for
            // purpose like REST API retrieval
            result.setRouteCount(routeCount);
        }
        if (log.isTraceEnabled()) {
            log.trace("buildrouteMultipath: {}", result);
        }

        return result;
    }

    // cookie based getRoute, needed by multipath
    // The only difference between this and the super method is that
    // the route id uses cookie here, the super does not.
    // NOTE: Return a null route if srcId equals dstId.  The null route
    // need not be stored in the cache.  Moreover, the LoadingCache will
    // throw an exception if null route is returned.
    @Override
    protected Route getRoute(long srcId, long dstId, long cookie) {
        cookie=0;

        // Return null route if srcId equals dstId
        if (srcId == dstId) return null;

        RouteId id = new RouteId(srcId, dstId, cookie);
        Route result = null;

        try {
            result = pathcache.get(id);
        } catch (Exception e) {
            log.error("{}", e);
        }

        if (log.isTraceEnabled()) {
            log.trace("getRoute: {} -> {} cookie: " + cookie, id, result);
        }
        return result;
    }

    public ArrayList<Route> getRoutes(long srcDpid, long dstDpid) {
        ArrayList<Route> routes = new ArrayList<Route>();

        Route firstRoute = getRoute(srcDpid, dstDpid, 0);

        if(firstRoute != null) {
            routes.add(firstRoute);

            for (int i=1; i < firstRoute.getRouteCount(); i++)
                routes.add(getRoute(srcDpid, dstDpid, i));
        }

        return routes;
    }
}