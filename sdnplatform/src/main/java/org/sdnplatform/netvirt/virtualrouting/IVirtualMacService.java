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

package org.sdnplatform.netvirt.virtualrouting;

import org.sdnplatform.core.module.IPlatformService;

public interface IVirtualMacService extends IPlatformService {
    /**
     * Allocate the requested virtual MAC.
     * @param vMac The virtual MAC that is requested
     * @return true if it is available to use in the MAC address space managed
     *         by this service or the address is out of the address space
     *         managed by this service.
     *         false if it is an address managed by this service but already
     *         in use
     */
    public boolean acquireVirtualMac(long vMac);

    /**
     * Allocate an unused MAC address from the space managed by this manager
     * @return an unused MAC address
     * @throws Exception if there is no available MAC address.
     */
    public long acquireVirtualMac() throws VirtualMACExhaustedException;

    /**
     * Return the MAC address back to virtual MAC address service pool
     * @param vMac The virtual MAC to be relinquished
     */
    public void relinquishVirtualMAC (long vMac);
}
