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

import java.util.Collections;
import java.util.Iterator;


import org.restlet.data.Form;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.util.FilterIterator;


/**
 * Server resource for accessing NetVirt interfaces
 */
public class NetVirtInterfaceResource extends ServerResource {
    
    @Get("json")
    public Iterator<VNSInterface> getInterfaces() {
        INetVirtManagerService netVirtManager = 
                (INetVirtManagerService)getContext().getAttributes().
                    get(INetVirtManagerService.class.getCanonicalName());
        
        Form form = getQuery();
        Iterator<VNSInterface> iter = null;
        final String netVirt = form.getFirstValue("netVirt", true);
        String name = form.getFirstValue("name", true);

        if (name == null || netVirt == null) {
            final Iterator<VNSInterface> allIfaces = 
                    netVirtManager.getAllInterfaces();
            if (netVirt == null) 
                iter = allIfaces;
            else {
                iter = new FilterIterator<VNSInterface>(allIfaces) {
                    @Override
                    protected boolean matches(VNSInterface value) {
                        return netVirt.equals(next.getParentVNS().getName());
                    }
                };
            }
        } else {
            VNSInterface iface = netVirtManager.getInterface(netVirt + "|" + name);
            if (iface != null)
                iter = Collections.singleton(iface).iterator();
            else
                iter = Collections.<VNSInterface>emptyList().iterator();
        }
        
        final String netVirtStartsWith = 
                form.getFirstValue("netVirt__startswith", true);
        final String nameStartsWith = 
                form.getFirstValue("name__startswith", true);
        
        return new FilterIterator<VNSInterface>(iter) {
            @Override
            protected boolean matches(VNSInterface value) {
                if (netVirtStartsWith != null &&
                    !value.getParentVNS().getName().startsWith(netVirtStartsWith))
                        return false;
                if (nameStartsWith != null &&
                    !value.getName().startsWith(nameStartsWith))
                        return false;
                return true;
            }
        };
    }
}
