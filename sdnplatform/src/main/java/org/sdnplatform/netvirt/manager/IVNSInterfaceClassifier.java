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

import java.util.List;

import org.sdnplatform.core.IListener;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.netvirt.core.VNSInterface;



/**
 * This is the plug-in interface to classify devices, which are not
 * classified by NetVirtManager.
 */
public interface IVNSInterfaceClassifier extends IListener<String> {
    public static final String CLASSIFIER = "Classifier";
    
    /**
     * This method is called to classify device.
     * 
     * @param addressSpace    The address space in which the device will be classified.
     *                        NULL means in all possible address spaces.
     * @param deviceMac       The MAC address of the device to be classified
     * @param deviceVlan     The VLAN of the device to be classified
     * @param deviceIpv4     The IPv4 address of the device to be classified
     * @param switchPort     The switch port where the device is attached.
     * @return List of matched NetVirtInterfaces or NULL if no match. 
     */
    public List<VNSInterface> classifyDevice(String addressSpace,
                                             Long deviceMac,
                                             Short deviceVlan,
                                             Integer deviceIpv4,
                                             SwitchPort switchPort);
    
    /**
     * This method is called to classify device.
     * 
     * @param device  The device to be classified
     * @return List of matched NetVirtInterfaces or NULL if no match.
     */
    public List<VNSInterface> classifyDevice(IDevice device);
}
