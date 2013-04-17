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

package org.sdnplatform.util;

import org.openflow.util.U32;
import org.sdnplatform.packet.IPv4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IPV4Subnet implements Comparable<IPV4Subnet> {
    protected static Logger logger = LoggerFactory.getLogger(IPV4Subnet.class);

    public int address = 0;
    /* The maskBits is number of prefix bits that are actually significant
     * eg. maskBits = 24 translates to 255.255.255.0 or an inverted netmask of
     * 0.0.0.255
     */
    public short maskBits = 32;

    public IPV4Subnet(int address, short maskBits) {
        super();
        this.address = address;
        this.maskBits = checkMaskbits(maskBits);
    }

    public IPV4Subnet() {
        super();
    }

    public IPV4Subnet(String subnet) {
        String[] r = subnet.split("/");
        try {
            this.address = IPv4.toIPv4Address(r[0]);
            if (r.length > 1) {
                maskBits = Short.parseShort(r[1]);
            }
        } catch (Exception e) {
            logger.error("Invalid IP subnet: " + subnet);
        }
        maskBits = checkMaskbits(maskBits);
    }

    /**
     * Takes an inverted IP subnet mask and returns the masklen.
     * eg. 0.0.0.255 and returns 24
     * This is a basic bitcount function
     * @param x The IP subnet mask
     * @return The mask len
     */
    public static short invertedMaskIpToLen(int x) {
        x = ~x;
        x = x - ((x >>> 1) & 0x55555555);
        x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
        x = (x + (x >>> 4)) & 0x0f0f0f0f;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        return (short) (x & 0x0000003f);
    }

    private short checkMaskbits(short mb) {
        if (mb < 0) return 0;
        if (mb > 32) return 32;
        return mb;
    }

    /**
     * Returns -1, 0, or +1 if this subnet is less than, equal or greater than the
     * argument passed in. Should be an unsigned comparison where:
     *     - first we check which masked IP is greater
     *         eg. this = 192.168.2.0/24 > arg = 192.168.1.0/24
     *         eg. this = 192.0.0.0/8    > arg = 1.1.1.1/32
     *     - if masked integer values are equal then check for more specific masklengths
     *         eg. this = 192.168.2.0/25 > arg = 192.168.2.0/24
     */
    @Override
    public int compareTo(IPV4Subnet arg0) {
        if (this == arg0) return 0;
        this.maskBits = checkMaskbits(this.maskBits);
        arg0.maskBits = checkMaskbits(arg0.maskBits);

        long allfs = U32.f(~0);
        Long v  = U32.f( address & U32.t(~(allfs >> this.maskBits)) );
        Long ov = U32.f( arg0.address & U32.t(~(allfs >> arg0.maskBits)) );

        int c = v.compareTo(ov); // unsigned comparison by using longs
        if (c == 0) {
            if (this.maskBits < arg0.maskBits)
                return -1;
            else if (this.maskBits > arg0.maskBits)
                return 1;
            return 0;
        }
        return c;
    }

    /**
     * Returns whether 'ip' is present in this IPV4 subnet
     * @param ip The IP address to be checked
     * @return true, if ip is present in this subnet
     *         false, otherwise
     */
    public boolean contains(int ip) {
        long allfs = U32.f(~0);

        Long v  = U32.f( address & U32.t(~(allfs >> maskBits)) );
        Long ov = U32.f( ip & U32.t(~(allfs >> maskBits)) );
        if (v.equals(ov)) {
            return true;
        } else {
            return false;
        }
    }
}
