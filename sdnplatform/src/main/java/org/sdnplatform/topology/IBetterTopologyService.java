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

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface IBetterTopologyService extends ITopologyService {
    public Set<BroadcastDomain> getBroadcastDomains();
    public Map<Long, Object> getHigherTopologyNodes();
    public Map<Long, Set<Long>> getHigherTopologyNeighbors();
    public Map<Long, Map<Long, Long>> getHigherTopologyNextHops();
    public Map<Long, Long> getL2DomainIds();
    public Map<OrderedNodePair, Set<NodePortTuple>> getAllowedUnicastPorts();
    public Map<OrderedNodePair, NodePortTuple> getAllowedIncomingBroadcastPorts();
    public Map<NodePortTuple, Set<Long>> getAllowedPortToBroadcastDomains();


    // For tunnel liveness detection
    /**
     * This method is called whenever forwarding module sends a flow-mod
     * that starts at a tunnel loopback port.
     * @param srcDPID: DPID of source switch
     * @param dstDPID: DPID of destination switch
     */
    public void detectTunnelSource(long srcDPID, long dstDPID);

    /**
     * This method is called whenever forwarding module sends a flow-mod
     * that ends at a tunnel loopback port.
     * @param srcDPID: DPID of source switch
     * @param dstDPID: DPID of destination switch
     */
    public void detectTunnelDestination(long srcDPID, long dstDPID);

    /**
     * Unidirectional tunnel liveness verification.  This call would
     * send an LLDP to the tunnel port of srcDPID and expect it to
     * arrive at the dstDPID tunnel port.  This method is used only
     */
    public void verifyTunnelOnDemand(long srcDPID, long dstDPID);

    /**
     * Clears the liveness state of tunnels that were recorded.
     */
    public void clearTunnelLivenessState();

    /**
     * Gives the state of tunnel links verified.  The values represent
     * whether the tunnel is up or down and the last time the verification
     * was performed.
     * @return
     */
    public List<TunnelEvent> getTunnelLivenessState();

    /**
     * Get the status of tunnel from srcDPID to dstDPID.
     * @return
     */
    public List<TunnelEvent> getTunnelLivenessState(long srcDPID, long dstDPID);
}