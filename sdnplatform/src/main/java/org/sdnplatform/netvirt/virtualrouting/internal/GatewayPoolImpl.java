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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.netvirt.virtualrouting.GatewayNode;
import org.sdnplatform.netvirt.virtualrouting.IGatewayPool;
import org.sdnplatform.routing.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class GatewayPoolImpl implements IGatewayPool {
    protected static final Logger logger =
            LoggerFactory.getLogger(GatewayPoolImpl.class);
    private String name;
    private Map<String, GatewayNode> gatewayNodeMap;
    private VirtualRouterManager vRtrManager;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void addGatewayNode(String ip) {
        GatewayNode gwNode = new GatewayNode(ip);
        gatewayNodeMap.put(ip, gwNode);
    }

    @Override
    public void removeGatewayNode(String ip) {
        if (gatewayNodeMap.containsKey(ip)) {
            gatewayNodeMap.remove(ip);
        }
    }

    @Override
    public Map<String, GatewayNode> getGatewayNodes() {
        return Collections.unmodifiableMap(gatewayNodeMap);
    }

    /**
     * @param sp An array of switch-port tuples
     * @return a map of cluster id --> switch ports for the given switch port
     * array
     */
    private Map<Long, SwitchPort> getSwitchPortMap(SwitchPort[] sp) {
        Map<Long, SwitchPort> resultMap = new HashMap<Long, SwitchPort>();
        for(int i=0; i < sp.length; i++) {
            long l2id = vRtrManager.getTopology().getL2DomainId(sp[i].getSwitchDPID());
            resultMap.put(l2id, sp[i]);
        }
        return resultMap;
    }

    @Override
    public GatewayNode getOptimalGatewayNodeInfo(IDevice srcDevice,
                                                 Short vlan) {
        GatewayNode optimalGatewayNode = null;
        int hopsToOptimalGatewayNode = Integer.MAX_VALUE;
        SwitchPort[] sdAPs = srcDevice.getAttachmentPoints();
        Map<Long, SwitchPort> sdMap = getSwitchPortMap(sdAPs);

        for (GatewayNode node : getGatewayNodes().values()) {
            if (node.getIp() == 0) {
                logger.warn("GW Pool {} node ip is zero", this.getName());
                continue;
            }
            IDevice gatewayDevice = vRtrManager.findDevice(null,
                                        srcDevice.getEntityClass(),
                                        0L,
                                        (vlan != null) ? vlan.shortValue() : 0,
                                        node.getIp());
            if (gatewayDevice == null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Failed to get dev for {}", node);
                }
                continue;
            }

            SwitchPort[] gdAPs = gatewayDevice.getAttachmentPoints();
            Map<Long, SwitchPort> gdMap = getSwitchPortMap(gdAPs);

            /*
             *  Add route hops for each openflow cluster that is in the path.
             *  TODO, now there isn't a way to tell which clusters are in the
             *  path.
             *  For now, we compare the sum of routeHops for all clusters.
             *  Initialize the numHops to MAX to assume there is no route
             */
            int numHops = Integer.MAX_VALUE;
            for (long l2id : sdMap.keySet()) {
                int hopsInCluster = 0;
                SwitchPort srcDap = sdMap.get(l2id);
                SwitchPort dstDap = gdMap.get(l2id);

                if (dstDap == null) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("{} not seen on l2id {}",
                                     node, l2id);
                    }
                    continue;
                }

                if (srcDap.equals(dstDap)) {
                    hopsInCluster = 0;
                } else {
                    Route route =  vRtrManager.getRoutingService().
                                            getRoute(srcDap.getSwitchDPID(),
                                                     (short)srcDap.getPort(),
                                                     dstDap.getSwitchDPID(),
                                                     (short)dstDap.getPort(),
                                                     0);
                    if (route != null && route.getPath() != null) {
                        hopsInCluster = route.getPath().size();
                    }
                }

                if (numHops == Integer.MAX_VALUE) {
                    numHops = hopsInCluster;
                } else {
                    numHops += hopsInCluster;
                }
            } // while through clusters
            if (numHops < hopsToOptimalGatewayNode) {
                // Found a better service node
                hopsToOptimalGatewayNode = numHops;
                optimalGatewayNode = new GatewayNode(node.getIp(), gatewayDevice);
            } else if (numHops != Integer.MAX_VALUE &&
                       numHops == hopsToOptimalGatewayNode) {
                // Equal distance, then pick the node with smaller ip
                if (node.getIp() < optimalGatewayNode.getIp()) {
                    optimalGatewayNode = new GatewayNode(node.getIp(), gatewayDevice);
                }
            }
        }
        return optimalGatewayNode;
    }

    public GatewayPoolImpl(String name, VirtualRouterManager vRtrManager) {
        super();
        this.name = name;
        this.gatewayNodeMap = new HashMap<String, GatewayNode>();
        this.vRtrManager = vRtrManager;
    }
}
