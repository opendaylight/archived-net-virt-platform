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

package org.sdnplatform.devicemanager.test;

import static org.sdnplatform.devicemanager.IDeviceService.DeviceField.MAC;
import static org.sdnplatform.devicemanager.IDeviceService.DeviceField.PORT;
import static org.sdnplatform.devicemanager.IDeviceService.DeviceField.SWITCH;
import static org.sdnplatform.devicemanager.IDeviceService.DeviceField.VLAN;

import java.util.EnumSet;

import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.internal.Entity;


/** A simple IEntityClassifier. Useful for tests that need an IEntityClassifier
 * with switch/port as key fields. 
 */
public class MockEntityClassifierMac extends DefaultEntityClassifier {
    public static class TestEntityClassMac implements IEntityClass {
        protected String name;
        public TestEntityClassMac(String name) {
            this.name = name;
        }
        
        @Override
        public EnumSet<DeviceField> getKeyFields() {
            return EnumSet.of(MAC, VLAN);
        }

        @Override
        public String getName() {
            return name;
        }
    }
    public static IEntityClass testECMac1 = 
            new MockEntityClassifierMac.TestEntityClassMac("testECMac1");
    public static IEntityClass testECMac2 = 
            new MockEntityClassifierMac.TestEntityClassMac("testECMac2");
    
    @Override
    public IEntityClass classifyEntity(Entity entity) {
        if (entity.getSwitchDPID() == 1L) {
            return testECMac1;
        } else if (entity.getSwitchDPID() == 2L) {
            return testECMac2;
        } else if (entity.getSwitchDPID() == -1L) {
            return null;
        }
        return DefaultEntityClassifier.entityClass;
    }

    @Override
    public EnumSet<IDeviceService.DeviceField> getKeyFields() {
        return EnumSet.of(MAC, VLAN, SWITCH, PORT);
    }
}