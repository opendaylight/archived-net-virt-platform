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

package org.sdnplatform.netvirt.web;

import java.util.Iterator;
import java.util.List;

import org.restlet.resource.Get;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.web.AbstractDeviceResource;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;


/**
 * Server resource for querying device/NetVirt interface mappings
 */
public class NetVirtDeviceInterfaceResource extends AbstractDeviceResource {
    public class NetVirtDeviceInterface {
        IDevice device;
        List<VNSInterface> iface;

        public NetVirtDeviceInterface(IDevice device, List<VNSInterface> iface) {
            super();
            this.iface = iface;
            this.device = device;
        }
        
        public List<VNSInterface> getIface() {
            return iface;
        }
        
        public void setIface(List<VNSInterface> iface) {
            this.iface = iface;
        }
        
        public IDevice getDevice() {
            return device;
        }
        
        public void setDevice(IDevice device) {
            this.device = device;
        }
    }
    
    public class NetVirtDeviceIterator implements Iterator<NetVirtDeviceInterface> {
        private Iterator<? extends IDevice> subIter;
        private INetVirtManagerService netVirtManager;

        public NetVirtDeviceIterator(Iterator<? extends IDevice> subIter,
                                 INetVirtManagerService netVirtManager) {
            super();
            this.subIter = subIter;
            this.netVirtManager = netVirtManager;
        }

        @Override
        public boolean hasNext() {
            return subIter.hasNext();
        }

        @Override
        public NetVirtDeviceInterface next() {
            IDevice device = subIter.next();
            List<VNSInterface> ifaces = netVirtManager.getInterfaces(device);
            return new NetVirtDeviceInterface(device, ifaces);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }        
    }
    
    @Get("json")
    public Iterator<NetVirtDeviceInterface> getDeviceInterfaces() {
        INetVirtManagerService netVirtManager = 
                (INetVirtManagerService)getContext().getAttributes().
                    get(INetVirtManagerService.class.getCanonicalName());
        Iterator<? extends IDevice> iter = super.getDevices();
        return new NetVirtDeviceIterator(iter, netVirtManager);
    }
}
