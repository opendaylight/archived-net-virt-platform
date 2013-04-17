/*
 * Copyright (c) 2011,2012,2013 Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University 
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.AttachmentPoint;
import org.sdnplatform.devicemanager.internal.Device;
import org.sdnplatform.devicemanager.internal.DeviceManagerImpl;
import org.sdnplatform.devicemanager.internal.Entity;


/**
 * This mock device removes the dependency on topology and a parent device
 * manager and simply assumes all its entities are current and correct
 */
public class MockDevice extends Device {

    public MockDevice(DeviceManagerImpl deviceManager,
                      Long deviceKey,
                      Entity entity, 
                      IEntityClass entityClass)  {
        super(deviceManager, deviceKey, entity, entityClass);
    }

    public MockDevice(Device device, Entity newEntity, int insertionpoint) {
        super(device, newEntity, insertionpoint);
    }
    
    public MockDevice(DeviceManagerImpl deviceManager, Long deviceKey,
                      List<AttachmentPoint> aps,
                      List<AttachmentPoint> trueAPs,
                      Collection<Entity> entities,
                      IEntityClass entityClass) {
        super(deviceManager, deviceKey, null, aps, trueAPs,
              entities, entityClass);
    }

    @Override
    public Integer[] getIPv4Addresses() {
        TreeSet<Integer> vals = new TreeSet<Integer>();
        for (Entity e : entities) {
            if (e.getIpv4Address() == null) continue;
            vals.add(e.getIpv4Address());
        }
        
        return vals.toArray(new Integer[vals.size()]);
    }

    @Override
    public SwitchPort[] getAttachmentPoints() {
        ArrayList<SwitchPort> vals = 
                new ArrayList<SwitchPort>(entities.length);
        for (Entity e : entities) {
            if (e.getSwitchDPID() != null &&
                e.getSwitchPort() != null &&
                deviceManager.isValidAttachmentPoint(e.getSwitchDPID(), e.getSwitchPort())) {
                SwitchPort sp = new SwitchPort(e.getSwitchDPID(), 
                                               e.getSwitchPort());
                vals.add(sp);
            }
        }
        return vals.toArray(new SwitchPort[vals.size()]);
    }

    @Override
    public String toString() {
        return "MockDevice [getEntityClass()=" + getEntityClass()
               + ", getEntities()=" + Arrays.toString(getEntities()) + "]";
    }
    
}
