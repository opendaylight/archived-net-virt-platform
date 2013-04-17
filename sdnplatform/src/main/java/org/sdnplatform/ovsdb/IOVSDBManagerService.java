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

/**
 * 
 */
package org.sdnplatform.ovsdb;

import java.util.ArrayList;
import java.util.Collection;

import org.sdnplatform.core.module.IPlatformService;


/**
 * Manage connected OVS switches and interact with
 * the OVS configuration database.
 */
public interface IOVSDBManagerService extends IPlatformService {
    /**
     * Get an IOVSDB object for all currently-connected
     * OVS switches.
     * @return a collection of {@link IOVSDB} objects
     */
    Collection<IOVSDB> getOVSDB();
    
    /**
     * Get an OVS DB object associated with the given 
     * OVS switch DPID
     * @param dpid the switch DPID for the OVS switch
     * @return an {@link IOVSDB} object
     */
    IOVSDB getOVSDB(long dpid);
    
    /**
     * Add an OVS DB listener to the list of listeners
     * @param l the listener to add
     */
    void addOVSDBListener(IOVSDBListener l);

    /**
     * Removes an OVS DB object (if it exists) associated with the given
     * OVS switch DPID
     * @param dpid
     */
    IOVSDB removeOVSDB(long dpid);

    /**
     * Creates and adds an OVS DB object associated with the given
     * OVS switch DPID 
     * @param dpid
     */
    IOVSDB addOVSDB(long dpid);
    
    /**
     * Add a regular (non-tunnel) port to the OVS associated with the
     * given dpid. If the port already exists then this call has no effect.
     * @param  dpid     OVS dpid
     * @param  portname name of the non-tunnel port
     * @return returns  true if the dpid was found in the OVSDB manager database
     *                    
     */
     boolean addPort(long dpid, String portname);
     
     /**
      * Delete a regular (non-tunnel) port from the OVS associated with the 
      * given dpid. If the port does not exist then this call has no effect.
      * @param  dpid     OVS dpid
      * @param  portname name of the non-tunnel port
      * @return returns  true if the dpid was found in the OVSDB manager database
      * 
      */
     boolean delPort(long dpid, String portname);
     
     /**
      * Set bridge Dpid sets the dpid on the ovs-br0 bridge at the given
      * management-IP addr. This method does NOT assume that the ovs is
      * connected to the controller on the OpenFlow channel. It creates an
      * OVSDB object in the controller which is managed by the OVSDBManager
      * (which implements this interface). The bridge dpid is set in the
      * other-config column as well as the datapath_id column.
      * @param mgmtIPAddr the management IP address of the ovs in dotted
      *                   decimal notation eg. "192.168.12.12"
      * @param dpidstr    the dpid to set on the ovs-br0 bridge as a hexstring
      *                   eg. "aabb112233445566"
      */
     void setBridgeDpid(String mgmtIPAddr, String dpidstr);
     
     /**
      * Get bridge Dpid from an OVS for the ovs-br0 bridge given an ovs 
      * management-IP. This method does NOT assume that the ovs is connected
      * to the controller on the OpenFlow channel. However it DOES assume that
      * the dpid has already been set by a prior call to setBridgeDpid; and so 
      * the OVSDB object has already been created.
      * @param mgmtIPAddr  the management-IP address for the ovs as a dotted
      *                     decimal string eg. 192.168.10.22
      * @return returns the dpid as a hexstring for the ovs-br0 bridge
      *         eg. "aabb112233445566"
      *         or null if the dpid has not been set
      */
     String getBridgeDpid(String mgmtIPAddr);
     
     /**
      *  Set Controller IP addresses on the bridge with the given dpid. This
      *  method assumes that the dpid has already been set by a prior call to
      *  setBridgeDpid. It does not assume that the OVS is connected to the
      *  controller on the OpenFlow channel. Note: Each call to this method 
      *  replaces the entire set of controller-IP addresses on the ovs-br0 
      *  bridge - i.e it does NOT add on to the existing IPs. So if the caller
      *  wishes to add-on a new IP address, it must specify the entire set of
      *  controller-IP addresses including the existing ones.
      *  @param  dpid   the dpid of the bridge
      *  @param  cntrIP ArrayList of strings of the form
      *             <transport-type>:<controller IP in dotted decimal notation>
      *             eg. "tcp:172.16.12.2"
      */
     void setControllerIPAddresses(long dpid, ArrayList<String> cntrIP);
     
     /**
      * Get controller IP addresses from an OVS associated with the given 
      * dpid. This method assumes that the dpid has already been set on ovsdb
      * for the OVS and the OVSDB object has already been created by a prior
      * call to setBridgeDpid. This method does NOT assume that the OVS is 
      * connected to the controller on the OpenFlow channel.
      * @param  dpid     the dpid of the ovs-br0 bridge on the OVS
      * @return an ArrayList of strings representing 
      *         <transport-type>:<controller IP in dotted decimal notation>
      *         eg. "tcp:172.16.12.2"
      *         The list may be empty if no controller-IP's have been configured
      */
     ArrayList<String> getControllerIPAddresses(long dpid);
     
     
}
