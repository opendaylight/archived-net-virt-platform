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

package org.sdnplatform.addressspace;

import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.SwitchPort;


/**
 * The address space manager component is responsible for maintaining 
 * address space configurations and for providing entity classification
 * (i.e., classifying entities into address spaces) to device manager.
 * @author gregor
 */
public interface IAddressSpaceManagerService extends IPlatformService { 
    
    public static final String DEFAULT_ADDRESS_SPACE_NAME = "default";
    
    /**
     * Verifies if the given vlan is allowed on the given switch port 
     * and whether the VLAN is tagged or native on the given switch port
     * It returns Ethernet.VLAN_UNTAGGED if the vlan is native
     * It returns the input vlan if the vlan is allowed if tagged
     * It returns null if the vlan is not allowed on this port.
     * 
     * This method should only be called for *attachment point ports*
     * and not for internal ports! This method will not check if a 
     * port is internal 
     * 
     * @param swp
     * @param vlan 
     * @return 
     * @throws NullPointerException if swp oraddressSpaceVlan is null
     * @throws IllegalArgumentException if vlan is outside the allowed
     *         range of 1..4095
     */
    public Short getSwitchPortVlanMode(SwitchPort swp, 
                                       String addressSpaceName,
                                       Short currentVlan,
                                       boolean tunnelEnabled)
            throws NullPointerException, IllegalArgumentException;
    
    
    /**
     * Get the BetterEntityClass instance associated with the given address space
     * name. Returns null if the address space doesn't exist
     * @param addressSpaceName
     * @return
     */
    public IEntityClass getEntityClassByName(String addressSpaceName);
}
