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

package org.sdnplatform.vendor;


import org.jboss.netty.buffer.ChannelBuffer;
import org.sdnplatform.packet.IPv4;

public class OFActionTunnelDstIP extends OFActionBigSwitchVendor {
    public final static int MINIMUM_LENGTH_TUNNEL_DST = 16;
    public  final static int SET_TUNNEL_DST_SUBTYPE = 2;

    protected int dstIPAddr;

    public OFActionTunnelDstIP() {
        super(SET_TUNNEL_DST_SUBTYPE);
        super.setLength((short)MINIMUM_LENGTH_TUNNEL_DST);
    }

    public OFActionTunnelDstIP(int dstIPAddr) {
        this();
        this.dstIPAddr = dstIPAddr;
    }

    public int getTunnelDstIP() {
        return this.dstIPAddr;
    }

    public void setTunnelDstIP(int ipAddr) {
        this.dstIPAddr = ipAddr;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.dstIPAddr = data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.dstIPAddr);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + dstIPAddr;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFActionTunnelDstIP other = (OFActionTunnelDstIP) obj;
        if (dstIPAddr != other.dstIPAddr) return false;
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + "; dstIP=" + IPv4.fromIPv4Address(dstIPAddr);
    }
}
