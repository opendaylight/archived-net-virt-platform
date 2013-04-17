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

import java.util.Map;

import org.sdnplatform.devicemanager.IDevice;


/**
 * An Interface for a Gateway Pool provider.
 * Gateway Pools represent a set of gateway devices in the network.
 * They can be used as a next hop interface in a virtual routing rule.
 * This interface describes the methods a Gateway Pool provides to the Virtual
 * Routing Service
 *
 * @author vemmadi
 */
public interface IGatewayPool {
    /**
     * @return The name of the Gateway Pool
     */
    String getName();

    /**
     * Add a Gateway Node to the Gateway Pool
     * @param ip The IP Address of the Gateway Node
     */
    void addGatewayNode(String ip);

    /**
     * Remove the Gateway node from the Gateway Pool
     * @param ip The IP Address of the Gateway Node
     */
    void removeGatewayNode(String ip);

    /**
     * Returns the IP Address of the Gateway Node in the pool
     * that is closest to the given source device.
     * @param srcDevice The source identified by an IDevice.
     * @param vlan The VLAN on which the IDevice was last seen.
     * @return The Gateway Node Info for the gateway node in the pool
     *         that is closest to the source
     */
    GatewayNode getOptimalGatewayNodeInfo(IDevice srcDevice, Short vlan);

    /**
     * @return an unmodifiable map of gateway nodes that belong to this gateway
     * pool.
     */
    public Map<String, GatewayNode> getGatewayNodes();
}
