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
 * A base class for DeviceGroups (e.g., NetVirt, AddressSpace) to group common
 * functionality like priority and comparison
 * @author gregor
 */
public class DeviceGroupBase implements IDeviceGroup {
    protected String name;
    protected String description;
    protected int priority;
    protected boolean active;
    
    public DeviceGroupBase(String name) {
        setName(name);
    }

    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#getName()
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#setName(java.lang.String)
     */
    @Override
    public void setName(String name) throws NullPointerException {
        if (name == null)
            throw new NullPointerException("name cannot be null");

        this.name = name;
    }

    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#setName(java.lang.String)
     */
    @Override
    public void setDescription(String name) {
        if (name != null) {
            this.description = name;
        }
    }

    @Override
    public String getDescription () {
        return description;
    }
   
    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#getPriority()
     */
    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#setPriority(int)
     */
    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#isActive()
     */
    @Override
    public boolean isActive() {
        return active;
    }

    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#setActive()
     */
    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * @see org.sdnplatform.devicegroup.IDeviceGroup#compareTo(org.sdnplatform.devicegroup.IDeviceGroup)
     */
    @Override
    public int compareTo(IDeviceGroup other) {
        if (!this.getClass().equals(other.getClass())) {
            throw new IllegalArgumentException("Cannot compare"
                    + " " + this.getClass().getName()
                    + " " + other.getClass().getName()
                    );
        }
        if (this.getPriority() != other.getPriority())
            return (new Integer(other.getPriority())).
                compareTo(this.getPriority());
        return (this.getName().compareTo(other.getName()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        DeviceGroupBase other = (DeviceGroupBase) obj;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        return true;
    }
}
