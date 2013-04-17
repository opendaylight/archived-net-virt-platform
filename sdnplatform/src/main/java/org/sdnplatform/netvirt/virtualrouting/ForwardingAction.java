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

import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;


public class ForwardingAction {
    public enum DropReason {
        UNKNOWN_SRC_RTR,
        IFACE_DOWN,
        SRC_IFACE_NOT_FOUND,
        DROP_RULE,
        DST_IFACE_NOT_FOUND,
        ROUTE_ERROR,
        NEXT_HOP_UNKNOWN,
        NetVirt_MISMATCH,
        NONE,
    }
    RoutingAction action;
    String nextRtrName;
    String dstNetVirtName;
    int nextHopIp;
    String nextHopGatewayPool;
    IVRouter nextHopGatewayPoolRouter;
    boolean virtualRouted;
    boolean destinedToVRMac;
    long newSrcMac;
    DropReason dropReason;
    String dropInfo;

    public ForwardingAction() {
        action = RoutingAction.DROP;
        nextRtrName = null;
        dstNetVirtName = null;
        nextHopIp = 0;
        nextHopGatewayPool = null;
        nextHopGatewayPoolRouter = null;
        virtualRouted = false;
        destinedToVRMac = false;
        newSrcMac = 0;
        dropReason = DropReason.NONE;
    }

    public RoutingAction getAction() {
        return action;
    }
    public void setAction(RoutingAction action) {
        this.action = action;
    }
    public String getNextRtrName() {
        return nextRtrName;
    }
    public void setNextRtrName(String router) {
        this.nextRtrName = router;
    }

    public String getDstNetVirtName() {
        return dstNetVirtName;
    }

    public void setDstNetVirtName(String netVirt) {
        this.dstNetVirtName = netVirt;
    }

    public int getNextHopIp() {
        return nextHopIp;
    }

    public void setNextHopIp(int nextHopIp) {
        this.nextHopIp = nextHopIp;
    }

    public String getNextHopGatewayPool() {
        return nextHopGatewayPool;
    }

    public void setNextHopGatewayPool(String nextHopGatewayPool) {
        this.nextHopGatewayPool = nextHopGatewayPool;
    }

    public IVRouter getNextHopGatewayPoolRouter() {
        return nextHopGatewayPoolRouter;
    }

    public void
    setNextHopGatewayPoolRouter(IVRouter nextHopGatewayPoolRouter) {
        this.nextHopGatewayPoolRouter = nextHopGatewayPoolRouter;
    }

    public boolean isVirtualRouted() {
        return virtualRouted;
    }

    public void setVirtualRouted(boolean virtualRouted) {
        this.virtualRouted = virtualRouted;
    }

    public boolean isDestinedToVirtualRouterMac() {
        return destinedToVRMac;
    }

    public void setDestinedToVirtualRouterMac(boolean destinedToVRMac) {
        this.destinedToVRMac = destinedToVRMac;
    }

    public long getNewSrcMac() {
        return newSrcMac;
    }

    public void setNewSrcMac(long newSrcMac) {
        this.newSrcMac = newSrcMac;
    }

    public DropReason getDropReason() {
        return dropReason;
    }

    public void setDropReason(DropReason reason) {
        this.dropReason = reason;
    }

    public String getDropInfo() {
        return dropInfo;
    }

    public void setDropInfo(String dropInfo) {
        this.dropInfo = dropInfo;
    }

    public String getReasonStr() {
        String reasonStr = "";
        switch (dropReason) {
            case UNKNOWN_SRC_RTR:
                reasonStr = "UNKNOWN_SRC_RTR";
                break;
            case IFACE_DOWN:
                reasonStr = "IFACE_DOWN";
                break;
            case SRC_IFACE_NOT_FOUND:
                reasonStr = "SRC_IFACE_NOT_FOUND";
                break;
            case DROP_RULE:
                reasonStr = "DROP_RULE";
                break;
            case DST_IFACE_NOT_FOUND:
                reasonStr = "DST_IFACE_NOT_FOUND";
                break;
            case ROUTE_ERROR:
                reasonStr = "ROUTE_ERROR";
                break;
            case NEXT_HOP_UNKNOWN:
                reasonStr = "NEXT_HOP_UNKNOWN";
                break;
            case NetVirt_MISMATCH:
                reasonStr = "NetVirt_MISMATCH";
                break;
            case NONE:
                reasonStr = "NONE";
                break;
        }
        return reasonStr + " " + dropInfo;
    }

    @Override
    public String toString() {
        String actStr;
        switch (action) {
            case DROP:
                actStr = "DROP";
                break;
            case FORWARD:
                actStr = "FORWARD";
                break;
             default:
                 actStr = "unknonwn";
                 break;
        }
        String retStr = "Action=" + actStr + " dstNetVirt=" + dstNetVirtName +
                        " nhIP=" + IPv4.fromIPv4Address(nextHopIp) +
                        " nhGatewayPool=" + nextHopGatewayPool;
        if (action.equals(RoutingAction.DROP)) {
            retStr = retStr + " reason=" + getReasonStr();
        }
        return retStr;
    }
}
