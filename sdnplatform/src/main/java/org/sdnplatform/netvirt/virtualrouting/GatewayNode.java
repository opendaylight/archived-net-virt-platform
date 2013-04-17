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

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.packet.IPv4;


public class GatewayNode {
    IDevice device;
    int ip;

    public GatewayNode(String ip) {
        super();
        this.ip = IPv4.toIPv4Address(ip);
    }

    public GatewayNode(int ip) {
        super();
        this.ip = ip;
    }

    public GatewayNode(String ip, IDevice device) {
        super();
        this.ip = IPv4.toIPv4Address(ip);
        this.device = device;
    }

    public GatewayNode(int ip, IDevice device) {
        super();
        this.ip = ip;
        this.device = device;
    }

    public int getIp() {
        return ip;
    }

    public IDevice getDevice() {
        return device;
    }

    @Override
    public String toString() {
        return "GatewayNode = " + IPv4.fromIPv4Address(ip);
    }
}
