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
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.util.FilterIterator;


/**
 * Server resource for getting list of NetVirt
 */
public class NetVirtResource extends ServerResource {
    @Get("json")
    public Iterator<VNS> getNetVirt() {
        INetVirtManagerService netVirtManager = 
                (INetVirtManagerService)getContext().getAttributes().
                    get(INetVirtManagerService.class.getCanonicalName());
        
        Form form = getQuery();
        Iterator<VNS> iter = null;
        String name = form.getFirstValue("name", true);
        if (name == null)
            iter = netVirtManager.getAllVNS();
        else {
            VNS netVirt = netVirtManager.getVNS(name);
            if (netVirt != null)
                iter = Collections.singleton(netVirt).iterator();
            else
                iter = Collections.<VNS>emptyList().iterator();
        }
        
        final String nameStartsWith = 
                form.getFirstValue("name__startswith", true);
        return new FilterIterator<VNS>(iter) {
            @Override
            protected boolean matches(VNS value) {
                if (nameStartsWith != null &&
                    !value.getName().startsWith(nameStartsWith))
                            return false;
                return true;
            }
        };
    }
}
