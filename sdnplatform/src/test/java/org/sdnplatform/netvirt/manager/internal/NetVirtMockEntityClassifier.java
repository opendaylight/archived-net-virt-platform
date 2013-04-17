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

package org.sdnplatform.netvirt.manager.internal;

import java.util.Collection;
import java.util.EnumSet;

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IEntityClassListener;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;
import org.sdnplatform.devicemanager.internal.Entity;


public class NetVirtMockEntityClassifier implements IEntityClassifierService {
    
    @Override
    public IEntityClass reclassifyEntity(IDevice curDevice, Entity entity) {
        return null;
    }
    
    @Override
    public EnumSet<DeviceField> getKeyFields() {
        return EnumSet.of(DeviceField.MAC, DeviceField.VLAN);
    }
    
    @Override
    public void deviceUpdate(IDevice oldDevice,
                             Collection<? extends IDevice> newDevices) {
        
    }
    
    @Override
    public IEntityClass classifyEntity(Entity entity) {
        return new IEntityClass() {
            @Override
            public String getName() {
                return "default";
            }
            
            @Override
            public EnumSet<DeviceField> getKeyFields() {
                return EnumSet.of(DeviceField.MAC, DeviceField.VLAN);
            }
        };
    }
    
    @Override
    public void addListener(IEntityClassListener listener) {

    }
}
