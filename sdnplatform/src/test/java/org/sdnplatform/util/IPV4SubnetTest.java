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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;


import org.junit.Test;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.util.IPV4Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPV4SubnetTest {
    protected static Logger logger = LoggerFactory.getLogger(IPV4SubnetTest.class);

    private IPV4Subnet getSub(String address, int i) {
        IPV4Subnet s = new IPV4Subnet();
        s.maskBits = (short)i;
        s.address = IPv4.toIPv4Address(address);
        return s;
    }

    @Test
    public void subnetComparison() {
        IPV4Subnet sub1 = getSub("192.168.0.1", 24);

        IPV4Subnet sub2 = getSub("192.168.0.1", 24);
        assertEquals(0, sub1.compareTo(sub2));

        sub2 = getSub("192.168.1.1", 24);
        assertEquals(-1, sub1.compareTo(sub2));

        sub2 = getSub("192.167.255.1", 24);
        assertEquals(1, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.1", 23);
        assertEquals(1, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.1", 25);
        assertEquals(-1, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.0", 24);
        assertEquals(0, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.255", 24);
        assertEquals(0, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.255", 32);
        assertEquals(-1, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.1", 31);
        assertEquals(-1, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.255", 1);
        assertEquals(1, sub1.compareTo(sub2));

        sub2 = getSub("192.168.0.255", 0);
        assertEquals(1, sub1.compareTo(sub2));

        sub1 = getSub("192.0.0.0", 8);
        sub2 = getSub("1.1.1.1", 32);
        assertEquals(1, sub1.compareTo(sub2));

        sub1 = getSub("192.0.0.0", -1);
        sub2 = getSub("1.1.1.1", 64);
        assertEquals(-1, sub1.compareTo(sub2));

        sub1 = getSub("0.0.0.0", 0);
        sub2 = getSub("255.255.255.255", 32);
        assertEquals(-1, sub1.compareTo(sub2));

        try {
            sub1 = getSub("192.256.0.1", 24);
        } catch (IllegalArgumentException e) {
            logger.error("{}", e.toString());
        }
    }

    @Test
    public void testContains() {
        IPV4Subnet sub1 = getSub("192.168.0.1", 24);
        int ip1 = IPv4.toIPv4Address("192.168.0.20");
        int ip2 = IPv4.toIPv4Address("192.168.1.1");
        assertEquals(true, sub1.contains(ip1));
        assertEquals(false, sub1.contains(ip2));
    }

    @Test
    public void testListOfSubnets() {
        IPV4Subnet sub1 = getSub("192.168.0.1", 24);
        IPV4Subnet sub2 = getSub("192.168.0.12", 25);
        IPV4Subnet sub3 = getSub("192.168.0.12", 24);

        List<IPV4Subnet> ssub = new ArrayList<IPV4Subnet>();
        int ip1 = IPv4.toIPv4Address("192.168.0.128");

        ssub.add(sub1);
        assertEquals(1, ssub.size());

        if (sub1.compareTo(sub2) != 0) {
            ssub.add(sub2);
        }
        assertEquals(2, ssub.size());
        assertEquals(-1, sub1.compareTo(sub2));
        assertEquals(true, sub1.contains(ip1));
        assertEquals(false, sub2.contains(ip1));

        if (sub1.compareTo(sub3) != 0 && sub2.compareTo(sub3) != 0) {
            ssub.add(sub3);
        }
        assertEquals(2, ssub.size());
        assertEquals(0, sub1.compareTo(sub3));
        assertEquals(1, sub2.compareTo(sub3));

        assertEquals(true, sub1.contains(ip1));
        assertEquals(false, sub2.contains(ip1));
        assertEquals(true, sub3.contains(ip1));
    }

}
