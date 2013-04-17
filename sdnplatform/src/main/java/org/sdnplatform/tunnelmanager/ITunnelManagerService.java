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

package org.sdnplatform.tunnelmanager;

import java.util.ArrayList;

import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.tunnelmanager.TunnelManager.SwitchTunnelInfo;



public interface ITunnelManagerService extends IPlatformService {

    /**
     * getTunnelsOnSwitch is used to get current information on tunnels
     * for a switch, possibly for display on CLI or to respond to REST API calls.
     *
     * @param swDpid dpid of switch for which tunnel information is requested
     * @return SwitchTunnelInfo object containing tunnel information for swDpid
     *         or null if the switch is not connected to the controller.
     */
    public SwitchTunnelInfo getTunnelsOnSwitch(long swDpid);


    /**
     * getAllTunnels returns tunnel information on all switches that are connected to the
     * controller, irrespective of whether they are tunnel capable or not.
     * @return ArrayList of SwitchTunnelInfo objects. The ArrayList may be
     *         empty if there are no switches connected to the controller.
     */
    public ArrayList<SwitchTunnelInfo> getAllTunnels();


    /**
     * isTunnelEndpoint checks to see if the enclosed MAC or IPv4 addresses
     * corresponds to a known tunnel endpoint. This method does NOT check to
     * see if tunneling is ACTIVE on the switch.
     * @param dev  the device to check if it is a tunnel-endpoint
     * @return     true if either the mac or any one of the possible IP addresses
     *             corresponds to a known tunnel endpoint.
     *             Note that tunneling may or may not be active
     *             on the switch the tunnel-endpoint belongs to.
     */
    public boolean isTunnelEndpoint(IDevice dev);

    /**
     * isTunnelSubnet checks to see if the given IP address  is part of any
     * known tunnel subnets. Returns true if the IP address is part of the subnet
     * irrespective of whether the address is a tunnel-endpoint, gateway IP or
     * some other IP. Subnets are discovered from querying tunnel capable switches.
     * Once discovered, subnets don't go away, even if all the tunnel endpoints
     * in that subnet become inactive.
     * @param ipAddress
     */
    boolean isTunnelSubnet(int ipAddress);


    /**
     * getTunnelIPAddr returns the known tunnel-endpoint IP Address for a given dpid.
     * It does so only for ACTIVE tunnel-switches. If tunneling has been disabled
     * or the tunnel IP Address has not been configured, the switch is not tunnel
     * ACTIVE and the method will return null.
     * @param dpid  the switch dpid for which the tunnel-endpoint IP is requested
     * @return ipv4 address for tunnel-endpoint iff the switch is in
     *         tunnel-ACTIVE state; otherwise returns null.
     */
    Integer getTunnelIPAddr(long dpid);


    /**
     * isTunnelActiveByIP returns true if the given ipAddr corresponds to the
     * tunnel-endpoint IP of a switch in tunnel-ACTIVE state.
     * @param ipAddr the tunnel-endpoint IP addr to check for active state
     */
    boolean isTunnelActiveByIP(int ipAddr);


    /**
     * isTunnelActiveByDpid returns true if the given switch dpid is in
     * tunnel-ACTIVE state
     * @param dpid
     */
    boolean isTunnelActiveByDpid(long dpid);


    /**
     * getTunnelPortNumber returns the OpenFlow port number for the 'tun-bsn'
     * port on switch with given dpid. This method does NOT check to see if the
     * tunnel is ACTIVE or not - if the port exists, it will return the port number.
     * @param dpid the switch to check
     * @return OpenFlow port number for 'tun-bsn' port if one exists; otherwise null.
     */
    Short getTunnelPortNumber(long dpid);


    /**
     * updateTunnelIP is used for liveness detection. It is possible that the
     * tunnelEndpoint IP may change -- since TunnelManager does not poll for
     * the IP address beyond the first time it learns the address, it may become
     * necessary to call this method to force TunnelManager to poll the switches
     * again. Since tunnel liveness detection is typically probed between two
     * switches, the method accepts two dpids (instead of calling the method once
     * for each dpid).
     *
     * Note that this method simply updates the tunnel-endpoint IP. It does NOT
     * actively try to send anything through the tunnel. It is simply the first
     * step in diagnosing the problem. It is the job of the caller to see if the
     * the tunnel is actually working (after the IPs potentially get updated).
     * @param dpid1
     * @param dpid2
     */
    void updateTunnelIP(long dpid1, long dpid2);

    /**
     * Retrieves the switch DPID for a given tunnel IP address. This method does
     * NOT check to see if the tunnel is ACTIVE or not. To check if a given IP
     * corresponds to an active-tunnel, use 'isTunnelActiveByIP' instead.
     * @param tunnelIPAddress
     * @return switch dpid
     */
    Long getSwitchDpid(int tunnelIPAddress);

    /**
     * returns true if 'portnum' corresponds to an OpenFlow port with port-name
     * 'tun-loopback' on a tunnel capable switch
     * @param switchDPID
     * @param port
     * @return
     */
    boolean isTunnelLoopbackPort(long switchDPID, short portnum);

    /**
     * checks if the given port number on the switch corresponds to the
     * tunnel-loopback port.
     * @param switchDPID
     * @return
     */
    Short getTunnelLoopbackPort(long switchDPID);

    /**
     * Adds a listener to listen for ITunnelManagerListener notification messages.
     * Notifications from TunnelManager include switches that become tunnel-ACTIVE
     * or cease being tunnel-ACTIVE
     * @param listener The listener that wants the notifications
     */
    public void addListener(ITunnelManagerListener listener);


    /**
     * Remove a ITunnelManager listener
     * Notifications from TunnelManager include switches that become tunnel-ACTIVE
     * or cease being tunnel-ACTIVE
     * @param listener The module that no longer wants to listen for events
     */
    public void removeListener(ITunnelManagerListener listener);


}
