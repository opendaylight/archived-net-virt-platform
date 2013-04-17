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

import java.util.EnumSet;

import org.sdnplatform.devicegroup.DeviceGroupBase;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;



// TODO: should this be an inner-class of AddressSpaceServiceImpl?
public class BetterEntityClass
extends DeviceGroupBase
implements IEntityClass {
    // The key field. MAC addresses uniquely identify a device in
    // an address-space. Except in the default address space: here
    // VLAN+Mac is the identifier
    protected static EnumSet<DeviceField> keyFieldsMac;
    static {
        keyFieldsMac = EnumSet.of(DeviceField.MAC);
    }
    protected static EnumSet<DeviceField> keyFieldsVlanMac;
    static {
        keyFieldsVlanMac = EnumSet.of(DeviceField.MAC, DeviceField.VLAN);
    }


    protected Short vlan;

    /* Used to garbage collect stale config data */
    protected boolean marked;

    /**
     * TODO: do we need the vlan here? probably. What else do we want
     * from BetterEntityClass
     * @param name
     * @param vlan
     */
    public BetterEntityClass(String name, Short vlan) {
        super(name);
        this.vlan = vlan;
    }

    /**
     * Instantiate a default entity class
     */
    public BetterEntityClass() {
        super(IAddressSpaceManagerService.DEFAULT_ADDRESS_SPACE_NAME);
        this.vlan = null;
    }

    @Override
    public EnumSet<DeviceField> getKeyFields() {
        // Make sure nobody can accidentally change our keyFiels by returning
        // a clone since there's no ImmutableEnumSet :-(
        if (name.equals("default"))
            return keyFieldsVlanMac;
        else
            return keyFieldsMac.clone();
    }

    @Override
    public String toString() {
        if (name.equals(IAddressSpaceManagerService.DEFAULT_ADDRESS_SPACE_NAME))
            return "BetterEntityClass[" + name + "]";
        else
            return "BetterEntityClass[" + name + ", vlan=" + vlan +"]";
    }

    public boolean isMarked() {
        return marked;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public Short getVlan () {
        return vlan;
    }

    public void setVlan (Short vlan) {
        this.vlan = vlan;
    }
}
