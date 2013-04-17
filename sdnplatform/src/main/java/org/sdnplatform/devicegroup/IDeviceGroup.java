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

package org.sdnplatform.devicegroup;

/**
 * Represents a group of devices. Or more precisely the configuration for
 * groups of devices. E.g., an AddressSpace or a NetVirt is a group of devices
 * @author gregor
 *
 */
public interface IDeviceGroup extends Comparable<IDeviceGroup> {
    
    /**
     * User friendly, unique name. Likely derived from the config. 
     * E.g., the NetVirt name
     * 
     * @return
     */
    public String getName();
    
    /**
     * Set the name. name must not be NULL
     * @param name 
     */
    public void setName(String name) throws NullPointerException;
    
    /**
     * Set the description.
     * @param description 
     */
    public void setDescription(String description);
    
    /**
     * Get the description.
     * @return description 
     */
    public String getDescription();
    
    /**
     * Orders the IDeviceGroups by their priority. 
     * 
     * If two device groups have the same priority they will be ordered 
     * deterministically using their name.
     * 
     * Higher values have higher priority
     * 
     * @throws IllegalArgumentException if other is not same runtime class
     * as this. Unfortunately, we can't check this at compile time.
     * IDeviceGroup as this. E.g., if a NetVirt is compared to an address-space
     */
    @Override
    public int compareTo(IDeviceGroup other) throws IllegalArgumentException;
    
    /**
     * Returns this device groups priority
     * @return 
     */
    public int getPriority();
    
    /**
     * Set the priority of this device group
     * @param priority
     */
    public void setPriority(int priority);
    
    /**
     * Check if this device group is currently active, i.e., it is enabled
     * in the config
     * @return
     */
    public boolean isActive();
    
    /**
     * Set the active flag
     * @return
     */
    public void setActive(boolean active);
}
