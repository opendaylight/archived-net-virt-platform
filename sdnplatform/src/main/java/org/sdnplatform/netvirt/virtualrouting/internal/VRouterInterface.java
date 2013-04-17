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

package org.sdnplatform.netvirt.virtualrouting.internal;

import java.util.HashSet;
import java.util.Set;

import org.sdnplatform.netvirt.virtualrouting.IVRouter;
import org.sdnplatform.util.IPV4Subnet;


/**
 * Models a virtual router interface
 */
public class VRouterInterface {
    /* Is this interface connected to a NetVirt */
    private final boolean isNetVirt;
    /* Name of the NetVirt connected to this interface */
    private final String netVirt;
    /* Name of the router connected to this interface */
    private final String vRouter;
    /* Name of this interface */
    private final String name;
    /* Is the interface active */
    private boolean active;
    /* Set of IP address/subnet assigned to this interface */
    private Set<IPV4Subnet> addrs;
    /* The virtual router that owns this interface */
    private final IVRouter owner;

    VRouterInterface(IVRouter owner, String name, String netVirt, String vRouter,
                     boolean active) {
        this.owner = owner;
        this.name = name;
        this.netVirt = netVirt;
        this.vRouter = vRouter;
        if (netVirt != null)
            isNetVirt = true;
        else
            isNetVirt = false;
        this.active = active;
        addrs = new HashSet<IPV4Subnet>();
    }

    public boolean isNetVirt() {
        return isNetVirt;
    }

    public String getNetVirt() {
        return netVirt;
    }

    public String getvRouter() {
        return vRouter;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<IPV4Subnet> getAddrs() {
        return addrs;
    }

    public void addAddr(IPV4Subnet addr) {
        addrs.add(addr);
    }

    public IVRouter getOwner() {
        return owner;
    }
}
