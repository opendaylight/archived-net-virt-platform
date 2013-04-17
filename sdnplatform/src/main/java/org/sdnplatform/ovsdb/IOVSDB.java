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

package org.sdnplatform.ovsdb;

import java.util.ArrayList;
import java.util.Collection;

import org.sdnplatform.ovsdb.internal.JSONShowReplyMsg;
import org.sdnplatform.ovsdb.internal.JSONShowReplyMsg.ShowResult;


/**
 * Allows manipulating the OVS configuration database (ovsdb) for a particular
 * connected OVS
 */
public interface IOVSDB {
    
    /**
     * getTunnelIPAddress returns the IP address of the switch interface
     * used as a *local* tunnel-endpoint 
     * 
     * @return the local IP address for tunnel port as a dotted decimal String.
     *         Returns null if the local tunnel IP address is not available 
     *         at this time. 
     */
    public String getTunnelIPAddress();
    
    /**
     * refresh version of getTunnelIPAddress. When refresh is true, this method
     * always fetches the value from OVS; when false it returns the value cached
     * in the controller
     */
    public String getTunnelIPAddress(boolean refresh);
    
    /** 
     * getTunnelIPAddrName returns the name for the local tunnel IP address
     * For example, if the tunnel IP address is 192.168.1.53, the return value
     * of this method is a string "vta192168001053".
     * Returns null if the tunnel local IP address is not known at this time
     */
    public String getTunnelIPAddrName();
    
    /**
     * getMgmtIPAddr returns the IP address of the OVS VTA intf (eth0) 
     * used to communicate with the controller (for OpenFlow as well as 
     * JSON RPC)
     * @return the management interface IP address as a dotted decimal String.
     */
    public String getMgmtIPAddr();
    
    /**
     * getTunnelPortNames can be called to get the tunnel-port names configured
     * on the OVS.Tunnel port names are strings of the form "vta192168002003"
     * where the numbers correspond to the IP address at the *remote* end of the
     * tunnel i.e. "192.168.2.3".
     * 
     * @return A collection of strings representing tunnel port names.
     *         The collection could be empty if there aren't any tunnels. 
     */
    public Collection<String> getTunnelPortNames();

    /**
     * addPort adds a regular port or a CAPWAP tunnel port on this OVS with 
     * name "name". If "isTunnelPort" is true then the port is a CAPWAP tunnel 
     * port with remote IP address "remoteIPAddr". If such a tunnel already 
     * exists this method will have no effect. 
     * Example: name =  "vta192168002003"; remoteIPAddr = "192.168.2.3";
     * If "isTunnelPort" is false, then "remoteIPAddr" is ignored and can be
     * any string or null.
     */
    public void addPort(String name, String localIPAddr, String remoteIPAddr,
                        boolean isTunnelPort);

    /**
     * delPort deletes a port on this OVS with the given name, where the port 
     * can be a tunnel-port where name is of the form "vta192168002003"; or
     * a regular port where name could be of the form "eth2.vlan20" (or not).
     * If such a port does not exist on the OVS, this method will have no 
     * effect. 
     */
    public void delPort(String name);

    /**
     * sendShowMessage sends a JSON RPC show command to this OVS and updates
     * the OVSDB state to mirror the OVS state learned from the reply to this
     * show message. addTunnelPort and delTunnelPort method implementations
     * first synchronize the OVSDB state with the OVS's state by calling this
     * method; and then try to add or delete a port. 
     */
    public void sendShowMessage();
    
    /**
     * get the OVS's dpid as a long. This method returns the value cached in
     * the controller's OVSDB object.
     */
    public long getDpid();

    /**
     * get the OVS's dpid as a ':' separated hexadecimal string. This method 
     * returns the value cached in the controller's OVSDB object.
     */
    public String getHexDpid();
    
    /**
     * get the OVS's bridge ovs-br0 dpid as a hex string eg. 'aabb112233445566'
     * Note that this method necessarily causes communication with the ovs  
     * to retrieve the most-recent value in the Bridge table. It first tries to 
     * read the other-config column for datapath-id in the bridge table. 
     * If that is not set, then we try to read the datapath_id column. If 
     * neither is set, we return null. If we get a returned dpid, we update
     * the cached dpid and hexdpid values in the OVSDB object (values returned
     * by the getDpid() and getHexDpid() methods). 
     */
    public String getBridgeDpid();
    
    /**
     * Get the OVS switch description for this OVS
     * @return the description
     */
    public String getDescription();
    
   
    public void updateTunnelSwitchFromShow(JSONShowReplyMsg showReply);
    
    public void updateTunnelSwitchFromUpdate(ShowResult update);
    
    public int getExpectedMessage(int messageReplyId);

    public ArrayList<String> getControllerIPs();

    public void setBridgeDpid(String dpidstr);

    public void setControllerIPs(ArrayList<String> cntrIP);

    public void setDescription(String description);

    
    
}
