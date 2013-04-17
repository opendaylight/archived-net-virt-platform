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

package org.sdnplatform.netvirt.manager;

import java.util.Iterator;
import java.util.List;

import org.sdnplatform.core.ListenerContextStore;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicegroup.MembershipRule;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;



/**
 * The NetVirt manager component is responsible for maintaining a mapping 
 * between devices on the network and the NetVirts and NetVirt interfaces to 
 * which they belong. NetVirt manager does not make any forwarding decision:
 * its responsibility is limited to NetVirt ID updates.
 * @author readams
 * @param <NetVirtMgrPendingFlowQueryResp>
 */
public interface INetVirtManagerService extends IPlatformService { 
    /**
     * a List<NetVirtInterface> containing the source interfaces
     */
    public static final String CONTEXT_SRC_IFACES = 
            "org.sdnplatform.netVirt.manager.srcIFaces";

    /**
     * a List<NetVirtInterface> containing the destination interfaces
     */
    public static final String CONTEXT_DST_IFACES = 
            "org.sdnplatform.netVirt.manager.dstIFaces";

    /**
     * A ListenerContextStore object that can be used to interact with the 
     * ListenerContext information created by NetVirt manager.
     */
    public static final ListenerContextStore<List<VNSInterface>> bcStore =
        new ListenerContextStore<List<VNSInterface>>();

    /**
     * Add a NetVirt listener for netVirt events
     * @param listener
     */
    public void addNetVirtListener(INetVirtListener listener);

    /**
     * Remove a NetVirt listener
     * @param listener
     */
    public void removeNetVirtListener(INetVirtListener listener);
    
    /**
     * Add a NetVirtInterfaceClassifier
     * @param classifier
     */
    public void addVNSInterfaceClassifier(IVNSInterfaceClassifier classifier);

    /**
     * Get an iterator over all the existing NetVirt
     * @return the iterator
     */
    public Iterator<VNS> getAllVNS();
    
    /**
     * Get a particular NetVirt
     * @param name the name of the NetVirt
     * @return the {@link VNS} object
     */
    public VNS getVNS(String name);
    
    /**
     * Get the list of NetVirt interfaces associated with the given device.
     * The list of interfaces returned uses copy-on-write semantics.
     * 
     * @param d the device to get interfaces for
     * @return the list of NetVirt interfaces. Highest priority comes first
     */
    public List<VNSInterface> getInterfaces(IDevice d);

    /**
     * Get an iterator over all NetVirt interfaces that currently exist
     * @return the iterator
     */
    public Iterator<VNSInterface> getAllInterfaces();
    
    /**
     * Get a particular NetVirt interface
     * @param name the interface name
     * @return the {@link VNSInterface} 
     */
    public VNSInterface getInterface(String name);

    /**
     * Get an interface from an interface name, creating it if needed
     * @param iname the components of the interface name
     * @param netVirt the netVirt for the interface (may be null, in which case
     * use the default NetVirt)
     * @param rule the NetVirt rule for the interface (may be null)
     * @return
     */
    public VNSInterface getIfaceFromName(String[] iname,
                                         VNS netVirt, IDevice device,
                                         MembershipRule<VNS> rule);

    /**
     * Get the list of broadcast switch port tuples.
     * These are interfaces over which all broadcast packets will be 
     * transmitted, irrespective of whether a device attachment was 
     * learned on that interface or not.
     * @return List of switch port tuples for broadcast
     */
    public List<SwitchPort> getBroadcastSwitchPorts();
    
    /**
     * Clear the device's NetVirtInterface cache
     * @param deviceKey
     */
    public void clearCachedDeviceState(long deviceKey);
}
