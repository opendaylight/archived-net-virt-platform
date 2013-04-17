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

package org.sdnplatform.netvirt.virtualrouting;

import org.sdnplatform.devicegroup.IDeviceGroup;
import org.sdnplatform.devicemanager.IDevice;


/**
 * @author sdmodi
 *
 */
public interface IVRouter {
    /**
     * @return The Name of the router
     */
    public String getName();

    /**
     * @return The name of the tenant to which this router belongs
     */
    public String getTenant();

    /**
     * Creates an interface and connects a NetVirt to the interface
     * @param ifaceName The name of the interface
     * @param netVirtName The name of the NetVirt
     * @param rtrName The name of the router connected to this interface
     * @param active Whether this interface is active or not
     */
    public void createInterface(String ifaceName, String netVirtName,
                                String rtrName, boolean active);

    /**
     * Assign an IP address to an interface. An interface can have multiple IP
     * addresses
     * @param ifaceName Name of the interface
     * @param ip IP address
     * @param subnet The subnet mask eg. 0.0.0.255
     * @throws IllegalArgumentException If the parameters are incorrect
     */
    public void assignInterfaceAddr(String ifaceName, String ip, String subnet)
                                    throws IllegalArgumentException;

    /**
     * Add a routing rule to the router
     * @param srcTenant The source tenant name
     * @param srcNetVirt The source NetVirt name
     * @param srcIp The source IP
     * @param srcMask The subnet mask for source IP
     * @param dstTenant The dest tenant name
     * @param dstNetVirt The dest NetVirt name
     * @param dstIp The dest IP
     * @param dstMask The subnet mask for dest IP
     * @param outIface The outgoing interface name
     * @param nextHop The next hop IP
     * @param action The action to take (PERMIT/DENY)
     * @param nextHopGatewayPool The next hop specified as a GatewayPool
     * @throws IllegalArgumentException if the rule parameters are incorrect
     */
    public void addRoutingRule(String srcTenant, String srcNetVirt, String srcIp,
                               String srcMask, String dstTenant, String dstNetVirt,
                               String dstIp, String dstMask, String outIface,
                               String nextHop, String action,
                               String nextHopGatewayPool)
                                       throws IllegalArgumentException;
    /**
     * Add a routing rule to the router
     * @param srcTenant The source tenant name
     * @param srcNetVirt The source NetVirt name
     * @param srcIp The source IP
     * @param srcMask The subnet mask for source IP
     * @param dstTenant The dest tenant name
     * @param dstNetVirt The dest NetVirt name
     * @param dstIp The dest IP
     * @param dstMask The subnet mask for dest IP
     * @param outIface The outgoing interface name
     * @param nextHop The next hop IP
     * @param action The action to take (PERMIT/DENY)
     * @throws IllegalArgumentException if the rule parameters are incorrect
     */
    public void addRoutingRule(String srcTenant, String srcNetVirt, String srcIp,
                               String srcMask, String dstTenant, String dstNetVirt,
                               String dstIp, String dstMask, String outIface,
                               String nextHop, String action)
                                       throws IllegalArgumentException;

    /**
     * @param entityName The name of the netVirt/router connected to this router
     * @return true if the interface connected to the entity is down. False
     *         otherwise
     */
    public boolean isIfaceDown(String entityName);

    /**
     * Apply routing logic and return the forwarding action
     * @param srcIfaceEntity The entity name from where the packet is coming
     *                       to this router
     * @param src The source device entity
     * @param srcIp The source IP
     * @param dst The dest device entity
     * @param dstIp The dest IP
     * @return The forwarding action
     */
    public Object getForwardingAction(String srcIfaceEntity,
                                      IDeviceGroup src, int srcIp,
                                      IDeviceGroup dst, int dstIp);

    /**
     * Return the virtual MAC for the IP address belonging to the interface to
     * which netVirtName is connected
     * @param ip The IP address for which a MAC is desired
     * @param netVirtName The name of the NetVirt connected to this router
     * @return The virtual MAC for this ip. 0 if this IP does not belong
     *         to this router
     */
    public long getVMac(String netVirtName, int ip);

    /**
     * Return the virtual router IP belonging to the interface to which netVirtName
     * is connected
     * @param ip The IP address of the host in the NetVirt
     * @param netVirtName The name of the NetVirt connected to this router
     * @return The virtual router interface IP for the subnet containing 'ip'.
     *         0 if the NetVirt is not connected to this router or there is no
     *         subnet on the interface to which 'ip' belongs
     */
    public int getRtrIp(String netVirtName, int ip);

    /**
     * Create a gateway pool on the router
     * @param gatewayPoolName name of the gateway pool
     */
    public void createGatewayPool(String gatewayPoolName);

    /**
     * Get the gateway pool associated with the given name
     * @param gatewayPoolName the name of the gateway pool
     * @return the gateway pool object associated with the given name
     */
    public IGatewayPool getGatewayPool(String gatewayPoolName);

    /**
     * Add a gateway pool node to the specified gateway pool
     * @param gatewayPoolName The Gateway Pool's name to which the node should
     *        be added to
     * @param ip The IP Address of the Gateway Pool Node
     */
    public void addGatewayPoolNode(String gatewayPoolName, String ip)
                                   throws IllegalArgumentException;
    /**
     * Remove a gateway pool node from the specified gateway pool
     * @param gatewayPoolName The Gateway Pool's name from which the node should
     *        be removed from
     * @param ip The IP Address of the Gateway Pool Node
     */
    public void removeGatewayPoolNode(String gatewayPoolName, String ip)
                                      throws IllegalArgumentException;

    /**
     * Find the optimal gateway node that is closest to the source device in
     * the gateway pool
     * @param gatewayPoolName Gateway Pool's Name
     * @param srcDev IDevice corresponding to the source of the packet.
     * @param vlan The VLAN on which the packet was received
     * @return The Gateway Node Info for the gateway node that is closest
     *         to the source
     * device.
     * @throws IllegalArgumentException
     */
    public GatewayNode getOptimalGatewayNodeInfo(String gatewayPoolName,
                                               IDevice srcDev, Short vlan)
                                               throws IllegalArgumentException;
}
