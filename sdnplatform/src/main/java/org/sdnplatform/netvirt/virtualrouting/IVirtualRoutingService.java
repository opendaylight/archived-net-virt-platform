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

import java.util.List;

import org.sdnplatform.core.ListenerContextStore;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.flowcache.IFlowReconcileListener;
import org.sdnplatform.netvirt.core.VNSAccessControlList;



public interface IVirtualRoutingService extends IPlatformService {
    public static final String NetVirt_NAME =
        "org.sdnplatform.netVirt.virtualrouting.netVirtName";
    
    // FIXME: a temporary workaround for virtual routing flows that are
    // route between NetVirtes. This is uses as the flowcache app instance name
    // for such flows. 
    public static final String VRS_FLOWCACHE_NAME = "||VirtualRoutingSystem||";

    /**
     * A ListenerContextStore object that can be used to interact with the
     * ListenerContext information created by VirtualRouting.
     */
    public static final ListenerContextStore<String> vrStore =
        new ListenerContextStore<String>();


    /**
     * Adds an OpenFlow message listener
     * @param listener The component that wants to listen for the message
     */
    public void addPacketListener(IOFMessageListener listener);

    /**
     * Removes a OpenFlow message listener
     * @param listener The component that no longer wants to listen for the message
     */
    public void removePacketListener(IOFMessageListener listener);

    /**
     * Adds a listener for ARP packets. Currently the ordering
     * that components receive the message is the order
     * that they were added in.
     * @param al The component that wants to listen for ARPs.
     */
    public void addARPListener(IARPListener al);

    /**
     * Add a flow reconcile listener
     * @param listener The module that can reconcile flows
     */
    public void addFlowReconcileListener(IFlowReconcileListener listener);

    /**
     * Remove a flow reconcile listener
     * @param listener The module that no longer reconcile flows
     */
    public void removeFlowReconcileListener(IFlowReconcileListener listener);

    /**
     * Remove all flow reconcile listeners
     */
    public void clearFlowReconcileListeners();

    /**
     * Returns the list of INPUT ACLs for a certain interface.
     * @param ifaceKey The interface key <netVirtname>|<ifacename>
     * @return The list of ACLs on this interface, null if none exist
     */
    public List<VNSAccessControlList> getInIfaceAcls(String ifaceKey);

    /**
     * Returns the list of OUTPUT ACLs for a certain interface.
     * @param ifaceKey The interface key <netVirtname>|<ifacename>
     * @return The list of ACLs on this interface, null if none exist
     */
    public List<VNSAccessControlList> getOutIfaceAcls(String ifaceKey);

    /**
     * Return whether device1 is allowed to communicate to device2 based on
     * the configured policy
     * @param device1
     * @param dev1Ip
     * @param device2
     * @param dev2Ip
     * @return true if the devices are allowed to communicate, false otherwise
     */
    public boolean connected(IDevice device1, int dev1Ip, IDevice device2,
                             int dev2Ip);
}
