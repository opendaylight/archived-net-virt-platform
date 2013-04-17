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

import java.util.List;
import java.util.Map.Entry;


import org.junit.Test;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.util.IPV4Subnet;
import org.sdnplatform.util.IPV4SubnetTrie;


import junit.framework.TestCase;

public class IPv4TrieTest extends TestCase {

    private static class TrieTest {
        public int address;
        public short bits;
        public String resultExpected;
        
        public TrieTest(String address, String resultExpected) {
            super();
            this.address = IPv4.toIPv4Address(address);
            this.bits = 32;
            this.resultExpected = resultExpected;
        }
        public TrieTest(String address, int bits, String resultExpected) {
            super();
            this.address = IPv4.toIPv4Address(address);
            this.bits = (short)bits;
            this.resultExpected = resultExpected;
        }
    }
    
    private void addToTrie(IPV4SubnetTrie<String> ipTrie, IPV4Subnet subnet) {
        ipTrie.put(subnet, IPv4.fromIPv4Address((int)subnet.address) + "/" + subnet.maskBits);
    }
    
    private void doTriePrefixTest(IPV4SubnetTrie<String> ipTrie, TrieTest test) {
        IPV4Subnet s = new IPV4Subnet();
        s.address = test.address;
        s.maskBits = test.bits;
        boolean foundmatch = false;
        StringBuffer sb = new StringBuffer("Prefix matches (expecting: ");
        sb.append(test.resultExpected);
        sb.append("), got: ");
        List<Entry<IPV4Subnet, String>> psearch = ipTrie.prefixSearch(s);
        for (Entry<IPV4Subnet, String> curEntry : psearch) {
            sb.append(curEntry.getValue() + ",");
            if (test.resultExpected.equals(curEntry.getValue())) {
                foundmatch = true;
            }
        }
        assertTrue(sb.toString(), foundmatch);
    }
    
    private void doTrieGetTest(IPV4SubnetTrie<String> ipTrie, TrieTest test) {
        IPV4Subnet s = new IPV4Subnet();
        s.maskBits = test.bits;
        s.address = test.address;
        assertEquals(test.resultExpected, ipTrie.get(s));
    }
   
    private IPV4Subnet getSub(String address, int i) {
        IPV4Subnet s = new IPV4Subnet();
        s.maskBits = (short)i;
        s.address = IPv4.toIPv4Address(address);
        return s;
    }
    
    @Test
    public void testIsBitSet() {
        IPV4Subnet s = getSub("192.168.1.1", 32);
        assertTrue(IPV4SubnetTrie.isBitSet(s, 0));
        assertTrue(IPV4SubnetTrie.isBitSet(s, 1));
        assertFalse(IPV4SubnetTrie.isBitSet(s, 2));
        assertFalse(IPV4SubnetTrie.isBitSet(s, 7));
        assertTrue(IPV4SubnetTrie.isBitSet(s, 8));
        assertFalse(IPV4SubnetTrie.isBitSet(s, 9));
        assertTrue(IPV4SubnetTrie.isBitSet(s, 31));
    }
    
    @Test
    public void testIsPrefix() {
        IPV4Subnet s1 = getSub("192.168.1.1", 32);
        IPV4Subnet s2 = getSub("192.168.1.1", 16);
        IPV4Subnet s3 = getSub("192.168.2.1", 24);
        IPV4Subnet s4 = getSub("192.168.3.1", 24);
        IPV4Subnet s5 = getSub("192.168.3.127", 32);

        assertTrue(IPV4SubnetTrie.isPrefix(s1, s2));
        assertFalse(IPV4SubnetTrie.isPrefix(s2, s3));
        assertFalse(IPV4SubnetTrie.isPrefix(s1, s3));
        assertFalse(IPV4SubnetTrie.isPrefix(s2, s3));
        assertTrue(IPV4SubnetTrie.isPrefix(s5, s4));
        assertFalse(IPV4SubnetTrie.isPrefix(s5, s3));
        
    }
    
    @Test
    public void testIPv4TrieBasic() {
        IPV4SubnetTrie<String> ipTrie = new IPV4SubnetTrie<String>();
        addToTrie(ipTrie, getSub("192.168.0.0", 16));
        addToTrie(ipTrie, getSub("192.168.2.0", 24));
        addToTrie(ipTrie, getSub("192.168.3.0", 24));
        addToTrie(ipTrie, getSub("192.168.3.252", 30));
        addToTrie(ipTrie, getSub("4.4.4.0", 24));
        addToTrie(ipTrie, getSub("10.0.1.1", 32));
        
        doTrieGetTest(ipTrie, new TrieTest("192.168.0.0", 16, "192.168.0.0/16"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.2.0", 24, "192.168.2.0/24"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.3.0", 24, "192.168.3.0/24"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.3.252", 30, "192.168.3.252/30"));
        doTrieGetTest(ipTrie, new TrieTest("4.4.4.0", 24, "4.4.4.0/24"));
        doTrieGetTest(ipTrie, new TrieTest("10.0.1.1", 32, "10.0.1.1/32"));
        doTrieGetTest(ipTrie, new TrieTest("4.4.4.0", 25, null));
        doTrieGetTest(ipTrie, new TrieTest("10.0.1.1", 31, null));
        doTrieGetTest(ipTrie, new TrieTest("10.0.1.2", 32, null));

        ipTrie = new IPV4SubnetTrie<String>();
        addToTrie(ipTrie, getSub("192.168.3.252", 30));
        addToTrie(ipTrie, getSub("192.168.0.0", 16));
        doTrieGetTest(ipTrie, new TrieTest("192.168.0.0", 16, "192.168.0.0/16"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.3.252", 30, "192.168.3.252/30"));

        ipTrie = new IPV4SubnetTrie<String>();
        addToTrie(ipTrie, getSub("192.168.3.252", 30));
        addToTrie(ipTrie, getSub("192.168.3.0", 24));
        addToTrie(ipTrie, getSub("192.168.0.0", 16));
        addToTrie(ipTrie, getSub("192.168.2.0", 24));
        addToTrie(ipTrie, getSub("10.0.1.1", 32));
        addToTrie(ipTrie, getSub("4.4.4.0", 24));

        doTrieGetTest(ipTrie, new TrieTest("192.168.0.0", 16, "192.168.0.0/16"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.2.0", 24, "192.168.2.0/24"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.3.0", 24, "192.168.3.0/24"));
        doTrieGetTest(ipTrie, new TrieTest("192.168.3.252", 30, "192.168.3.252/30"));
        doTrieGetTest(ipTrie, new TrieTest("4.4.4.0", 24, "4.4.4.0/24"));
        doTrieGetTest(ipTrie, new TrieTest("10.0.1.1", 32, "10.0.1.1/32"));
        doTrieGetTest(ipTrie, new TrieTest("4.4.4.0", 25, null));
        doTrieGetTest(ipTrie, new TrieTest("10.0.1.1", 31, null));
        doTrieGetTest(ipTrie, new TrieTest("10.0.1.2", 32, null));
    }

    @Test
    public void testIPv4TriePrefix() {
        IPV4SubnetTrie<String> ipTrie = new IPV4SubnetTrie<String>();
        addToTrie(ipTrie, getSub("192.168.0.0", 16));
        addToTrie(ipTrie, getSub("192.168.2.0", 24));
        addToTrie(ipTrie, getSub("192.168.3.0", 24));
        addToTrie(ipTrie, getSub("192.168.3.252", 30));
        addToTrie(ipTrie, getSub("4.4.4.0", 24));
        addToTrie(ipTrie, getSub("10.0.1.1", 32));

        doTriePrefixTest(ipTrie, new TrieTest("192.168.1.1", "192.168.0.0/16"));
        doTriePrefixTest(ipTrie, new TrieTest("192.168.2.1", "192.168.2.0/24"));
        doTriePrefixTest(ipTrie, new TrieTest("192.168.3.1", "192.168.3.0/24"));
        doTriePrefixTest(ipTrie, new TrieTest("192.168.3.252", "192.168.3.0/24"));
        doTriePrefixTest(ipTrie, new TrieTest("192.168.3.252", "192.168.3.252/30"));
        doTriePrefixTest(ipTrie, new TrieTest("4.4.4.255", "4.4.4.0/24"));
        doTriePrefixTest(ipTrie, new TrieTest("10.0.1.1", "10.0.1.1/32"));
        
    }
}
