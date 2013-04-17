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

package org.sdnplatform.forwarding;

import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.flowcache.OFMatchReconcile;
import org.sdnplatform.packet.Ethernet;


// TODO - get rid of this interface. BetterFlowCache should not be pushing flow mods
// directly and should not depend upon forwarding

public interface IForwardingService extends IPlatformService {

    /**
     * API to re-provision the forwarding path, for example, when a device
     * moves.
     *
     * @param applInstName application instance name
     * @param match open-flow match
     */
    public void doPushReconciledFlowMod(OFMatchReconcile ofmRc);

    /**
     * API to change the action of an existing flow to drop. This API can be
     * used, for example, when two devices are no longer in a common NetVirt after
     * a NetVirt configuration change.
     * 
     * @param ofmRc the flow-mod reconcile object
     */
    public void doDropReconciledFlowMod(OFMatchReconcile ofmRc);

    /**
     * API to delete an existing flow mod. This API can be used to delete 
     * existing drop flow-mods, for example, between two devices that were 
     * not initially in a common NetVirt and after a NetVirt configuration change 
     * they now do belong in a common NetVirt.
     * 
     * @param ofmRc the open flow mod reconcile object
     */
    public void doDeleteReconciledFlowMod(OFMatchReconcile ofmRc);
    
    /**
     * API to turn on/off the broadcast cache
     * 
     * @param state set broadcast cache on or off. Default is on.
     */
    public void setBroadcastCache(boolean state);

    /**
     * Return the state of broadcast cache
     * 
     * @return boolean
     */
    public boolean getBroadcastCache();
    
    /**
     * Push a packet to an egress port. The proper vlan will be
     * added based on the specified addressSpace.
     *
     * @param packet        packet data to send.
     * @param inPort        InPort where the packet was received.
     * @param swp           The switch/port which the packet will be sent out.
     * @param tunnelEnabled This specifies the topology to be used for swp verification.
     * @param addressSpace  The address space, to which the packet belong.
     * @param vlan          The vlan to be used in packetOut
     * @param cntx          context
     * @param flush         flush the packetOut right away
     * @return true if succeeded, false otherwise.
     */
    public boolean pushPacketOutToEgressPort(Ethernet packet,
                                             short inPort,
                                             SwitchPort swp,
                                             boolean tunnelEnabled,
                                             String addressSpace,
                                             short vlan,
                                             ListenerContext cntx,
                                             boolean flush);

    /**
     * Return MAC-based hash based on Forwarding's chosen hash method
     * 
     * @return long cookie
     */
    public long getHashByMac(long macAddress);
}
