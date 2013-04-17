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

package org.sdnplatform.devicegroup;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.devicegroup.DeviceGroupBase;
import org.sdnplatform.devicegroup.MembershipRule;

import static org.easymock.EasyMock.*;

public class MembershipRuleTest {
    protected MembershipRule<DeviceGroupBase> r;
    @Before
    public void setUp() {
        DeviceGroupBase dg = new DeviceGroupBase("foobar");
        r = new MembershipRule<DeviceGroupBase>("rule", dg);
    }
    
    @Test
    public void testBasics() {
        // fields that can't be null
        assertNotNull(r.getParentDeviceGroup());
        assertNotNull(r.getName());
        try {
            r.setParentDeviceGroup(null);
            fail("setPartentDeviceGroup() should have thrown a NullPointerException");
        } catch (NullPointerException e) {
            //expected
        }
        try {
            r.setName(null);
            fail("setName() should have thrown a NullPointerException");
        } catch (NullPointerException e) {
            //expected
        }
        
        // parent device group
        DeviceGroupBase dg = new DeviceGroupBase("test");
        r.setParentDeviceGroup(dg);
        assertEquals(dg, r.getParentDeviceGroup());
        
        // name 
        String name = "ab|cd";
        r.setName(name);
        assertEquals(name, r.getName());
        assertEquals("cd", r.friendlyName);
        name = "FooBarRule";
        r.setName(name);
        assertEquals(name, r.getName());
        
        // Description
        String desc = "Description";
        r.setDescription(null);
        assertEquals(null, r.getDescription());
        r.setDescription(desc);
        assertEquals(desc, r.getDescription());
        
        // isActive
        r.setActive(false);
        assertEquals(false, r.isActive());
        r.setActive(true);
        assertEquals(true, r.isActive());

        // isMarked
        r.setActive(false);
        assertEquals(false, r.isActive());
        r.setActive(true);
        assertEquals(true, r.isActive());

        // isMultipleAllowed
        r.setMultipleAllowed(false);
        assertEquals(false, r.isMultipleAllowed());
        r.setMultipleAllowed(true);
        assertEquals(true, r.isMultipleAllowed());

        // Priority
        int[] priorities = new int[] { Integer.MIN_VALUE,
                                       Integer.MAX_VALUE,
                                       -1,
                                       0, 
                                       1,
                                       424
                                       -454};
        for (int p : priorities) {
            r.setPriority(p);
            assertEquals(p, r.getPriority());
        }
    }
    
    @Test
    public void testMac() {
        r.setMac(null);
        assertEquals(null, r.getMac());
        String[] validMacs = new String[] {  "00:11:33:44:55:66",
                                             "ab:cd:ef:01:23:45",
                                             "ff:ff:ff:ff:ff:ff",
                                             "01:23:45:67:89:ab",
                                             "Ab:cD:eF:01:23:45",
                                             "AB:CD:EF:01:23:45",
                                             "001133445566", // FIXME
                                             "0011:3344:5566" // FIXME
                                             
                                           };
        for (String m: validMacs) {
            r.setMac(m);
            assertEquals(m.toLowerCase(), r.getMac());
        }
        // TODO: should we trim leading/trailing whitespace? 
        String[] invalidMacs = new String[] { 
                                             "-0:11:33:44:55:66",
                                             "00:11:33:44:55:6-",
                                             "00:11:33:44:55:x6",
                                             "00:11:x3:44:55:66",
                                             "00:11:33::44:55:66",
                                             ":00:11:33:44:55:66",
                                             "00:11:33:44:55:66:",
                                             "00:11:33:44:55",
                                             "00:11:33:44:55:6",
                                             "00:11:33:4:55:66",
                                             "0:11:33:44:55:66",
                                      //FIXME       "001133445566",
                                      //FIXME       "0011:3344:5566",
                                             "00-11-33-44-55-66",
                                             "ab:cd:ef:g1:23:45",
                                             "ff:ff:ff:Gf:ff:ff",
                                             "00:11:33::44:55",  
                                             "   ab:cd:ef:01:23:45  ",
                                             "   a b :c d:e f:01:2 3:45  ",
                                             "ab:cd:ef:01:23:45  ",
                                             "ab:cd:ef:01:23:45XX",
                                             "ab:cd:ef:01:23:4500",
                                             "    ab:cd:ef:01:23:45",
                                             "XXXXab:cd:ef:01:23:45",
                                             "0000ab:cd:ef:01:23:45",
                                           };
        String oldVal = r.getMac();
        for (String m: invalidMacs) {
            try {
                r.setMac(m);
                fail("setMac(" + m 
                     + ") should have thrown a IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                //expected
            }
            assertEquals(oldVal, r.getMac());
        }
    }
    
    @Test
    public void testIpSubnet() {
        r.setIpSubnet(null);
        assertEquals(null, r.getIpSubnet());
        
        String[] validIps = new String[] { 
                                           "1.2.3.4/1",
                                           "1.2.3.4/12",
                                           "1.2.3.4/32",
                                           "1.2.3.4/8",
                                           "1.2.3.4/16",
                                           "001.002.003.004/16",
                                           "001.2.3.4/16",
                                           "01.2.3.4/16",
                                           "1.2.003.4/16",
                                           "1.2.03.4/16",
                                           "123.2.42.4/16",
                                           "123.123.123.123/16",
                                           "127.127.127.127/16",
                                           "128.128.128.128/16",
                                           "255.255.255.255/24",
                                           "255.1.1.1/24",
                                           "1.1.42.255/24",
                                           "255.255.255.255/24",
                                           "12.12.12.12/16",
                                           // FIXME: these should fail
                                           "1.2.3.4/0",
                                           "1.2.3.4/33",
                                           "1.2.3.4/48",
                                           "256.1.1.1/24",
                                           "999.1.1.1/24",
                                           "1.256.1.1/24",
                                           "1.1.256.1/24",
                                           "1.1.256.256/24",
                                         };
        for (String ip: validIps) {
            r.setIpSubnet(ip);
            assertEquals(ip, r.getIpSubnet());
        }
        String oldVal = r.getIpSubnet();
        // TODO: should we trim leading/trailing whitespace? 
        String[] invalidIps = new String[] { 
                                           // FIXME: these should fail
                                           // "1.2.3.4/0",
                                           // "1.2.3.4/33",
                                           // "1.2.3.4/48",
                                           // "256.1.1.1/24",
                                           // "999.1.1.1/24",
                                           // "1.256.1.1/24",
                                           // "1.1.256.1/24",
                                           // "1.1.256.256/24",
                                           "1.2.3.4/-1",
                                           "-1.2.3.4/12",
                                           "1.-2.3.4/12",
                                           "1.2.3.4/444",
                                           "0a.Fe.3.4/12",
                                           "1.2.3.4/F",
                                           "42.42.43.44/1a",
                                           "1.xa.3.4/12",
                                           "1.0x4.3.4/12",
                                           "1.0x.3.4/32",
                                           "1.2.00Y.4/8",
                                           "1.2.3.4/16/5",
                                           "12 . 12 .12.12/16",
                                           "12.12.12.12 /16",
                                           "12.12.12.12/ 16",
                                           "   12.12.12.12/16   ",
                                           "12.12.12.12/16   ",
                                           "12.12.12.12/16XXX",
                                           "12.12.12.12/16000",
                                           "   12.12.12.12/16",
                                           "XXX12.12.12.12/16",
                                           "00012.12.12.12/16"
                                         };
        for (String ip: invalidIps) {
            try {
                r.setIpSubnet(ip);
                fail("setIpSubnet(" + ip 
                     + ") should have thrown a IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                //expected
            }
            assertEquals(oldVal, r.getIpSubnet());
        }
    }
    
    @Test
    public void testSwitchId() {
        r.setSwitchId(null);
        assertEquals(null, r.getSwitchId());
        String[] validSwitchIds = new String[] {  "00:11:33:44:55:66:77:88",
                                             "ab:cd:ef:01:23:45:77:88",
                                             "ff:ff:ff:ff:ff:ff:ff:ff",
                                             "01:23:45:67:89:ab:cd:ef",
                                             "Ab:cD:eF:01:23:45:67:89",
                                             "AB:CD:EF:01:23:45:67:80",
                                             "0011334455667788", // FIXME?
                                             "0011:3344:5566:7788" // FIXME?
                                             
                                           };
        for (String s: validSwitchIds) {
            r.setSwitchId(s);
            assertEquals(s.toLowerCase(), r.getSwitchId());
        }
        // TODO: should we trim leading/trailing whitespace? 
        String[] invalidSwitchIds = new String[] { 
                                             "-0:11:33:44:55:66:77:88",
                                             "00:11:33:44:55:6-:77:88",
                                             "00:11:33:44:55:x6:77:88",
                                             "00:11:x3:44:55:66:77:88",
                                             "00:11:33::44:55:66:77:88",
                                             ":00:11:33:44:55:66:77:88",
                                             "00:11:33:44:55:66:77:88:",
                                             "00:11:33:44:55:66:77",
                                             "00:11:33:44:55:66:77:8",
                                             "00:11:33:4:55:66:77:88",
                                             "0:11:33:44:55:66:77:88",
                                      //FIXME?       "0011334455667788",
                                      //FIXME?       "0011:3344:5566:7788",
                                             "00-11-33-44-55-66-77-88",
                                             "ab:cd:ef:g1:23:45:67:89",
                                             "ff:ff:ff:Gf:ff:ff:ff:ff",
                                             "00:11:33::44:55:77:88",  
                                             "   ab:cd:ef:01:23:45:67:89  ",
                                             "   a b :c d:e f:01:2 3:45:67:89  ",
                                             "ab:cd:ef:01:23:45:67:89  ",
                                             "ab:cd:ef:01:23:45:67:89XX",
                                             "ab:cd:ef:01:23:45:67:8900",
                                             "    ab:cd:ef:01:23:45:67:89",
                                             "XXXXab:cd:ef:01:23:45:67:89",
                                             "0000ab:cd:ef:01:23:45:67:89",
                                           };
        String oldVal = r.getSwitchId();
        for (String s: invalidSwitchIds) {
            try {
                r.setSwitchId(s);
                fail("setSwitchId(" + s 
                     + ") should have thrown a IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                //expected
            }
            assertEquals(oldVal, r.getSwitchId());
        }
    }
    
    /*
     * Call setPorts() with input and check if the the result from 
     * getPortList() matches expected
     */
    public void doSetPorts(String input, String[] expected) {
        String[] dummy = new String[0];
        String[] actual;
        r.setPorts(input);
        assertEquals(input, r.getPorts());
        if (expected == null) {
            assertEquals(null, r.getPortList());
        } else {
            actual = r.getPortList().toArray(dummy);
            // TODO: could sort actual and expected
            assertArrayEquals(expected, actual);
        }
    }

    /*
     * Calls set port with input and expect an IllegalArgumentException 
     */
    public void doSetPortsInvalid(String input) {
        String oldVal = r.getPorts();
        List<String> oldList = r.getPortList();
        try {
            r.setPorts(input);
            fail("setPorts(" + input
                 + ") should have thrown a IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            //expected
        }
        assertEquals(oldVal, r.getPorts());
        assertEquals(oldList, r.getPortList());
    }

    @Test
    public void testExplodePorts() {
        r.setPorts(null);
        String [] expected;
        assertEquals(null, r.getPorts());
        assertEquals(null, r.getPortList());
        doSetPorts("uplink", new String[]{"uplink"});
        doSetPorts("eth0", new String[]{"eth0"});
        doSetPorts("eth1-3", new String[]{"eth1", "eth2", "eth3"});
        doSetPorts("eth21-23", new String[]{"eth21", "eth22", "eth23"});
        doSetPorts("eth121-123", new String[]{"eth121", "eth122", "eth123"});
        doSetPorts("A1-1", new String[]{"A1"});
        doSetPorts("A1-2,eth1-3", new String[]{"A1","A2","eth1","eth2","eth3"});
        doSetPorts("A1-2,eth11-13", new String[]{"A1","A2","eth11","eth12","eth13"});
        doSetPorts("eth0,eth3", new String[] {"eth0","eth3"});
        doSetPorts("eth2eth", new String[] {"eth2eth"});
        doSetPorts("42", new String[] {"42"});
        expected = new String[14];
        for(int i=0; i<14; i++) 
            expected[i] = Integer.toString(i+42);
        doSetPorts("42-55", expected);
        doSetPorts("42.42", new String[] {"42.42"});
        doSetPorts("port,port5,port7,A1-10,pp", 
                   new String[] {"port","port5","port7","A1","A2","A3", 
                                 "A4","A5","A6","A7","A8","A9","A10","pp"
                                 });
        doSetPorts("ovs-br0,ovs-br1", new String[] {"ovs-br0","ovs-br1"});
        doSetPorts("ovs-br0,eth2.1-2", new String[] {"ovs-br0","eth2.1","eth2.2"});
        doSetPorts("ovs-br0,eth2:1-2", new String[] {"ovs-br0","eth2:1","eth2:2"});
        doSetPorts("Ethernet8-10,port4,P6-6,p4", 
                   new String[] { "Ethernet8", "Ethernet9", "Ethernet10", 
                                  "port4", "P6", "p4"});
        // Juniper-style ports
        doSetPorts("ge-1/1/25-26", new String[] {"ge-1/1/25", "ge-1/1/26"});
        doSetPorts("ge-1/1/25", new String[] {"ge-1/1/25"});

        // TODO: These work but it's not clear whether they should and/or 
        // whether they should work differently
       //   * port ranges with non alpha-num characters as the base name
        //  * empty strings as port names
        //  * negative ranges 
        //  * spaces in port names
        //  * names with '-' that's not a numerical range
        doSetPorts("ovs-br0,eth2+1-2", new String[] {"ovs-br0","eth2+1-2"});
        doSetPorts("ovs-br0,eth2%1-2", new String[] {"ovs-br0","eth2%1-2"});
        doSetPorts("eth6-3", null);
        doSetPorts("eth2-", new String[] {"eth2-"});
        doSetPorts("eth1--3", new String[] {"eth1--3"});
        doSetPorts("eth1-1", new String[]{"eth1"});
        doSetPorts("eth-2", new String[] {"eth-2"});
        doSetPorts("eth2.1.3.5", new String[] {"eth2.1.3.5"});
        doSetPorts("port1 port2", new String[] { "port1 port2"});
        doSetPorts("port1, port2", new String[] { "port1"," port2"});
        // these are artifacts of String.split(). Should they be
        // more consistent?
        doSetPorts("eth0,,eth3", new String[] {"eth0","","eth3"});
        doSetPorts("eth0,eth3,", new String[] {"eth0","eth3"});
        doSetPorts("eth0,eth3,,,,,", new String[] {"eth0","eth3"});
        doSetPorts(",eth0,eth3", new String[] {"","eth0","eth3"});
        doSetPorts(",,eth0,eth3,,", new String[] {"","","eth0","eth3"});
        doSetPorts("eth0,,,eth3", new String[] {"eth0","","","eth3"});
        doSetPorts(",", null);
        doSetPorts(",,", null);
        
        // boundrary checks
        String a = Long.valueOf((long)Integer.MAX_VALUE + 1).toString();
        String b = Long.valueOf((long)Integer.MAX_VALUE + 10).toString();
        String c = Long.valueOf((long)Integer.MAX_VALUE ).toString();
        String d = Long.valueOf((long)Integer.MAX_VALUE - 1).toString();
        doSetPorts("p" + a, new String[] {"p"+a}); // TODO
        doSetPorts("p" + d + "-" + c, new String[] { "p"+d, "p"+c});
        doSetPorts("p0-2", new String[]{"p0", "p1", "p2"});
        
        doSetPortsInvalid("p" + a + "-" + b);
        doSetPortsInvalid("p" + c + "-" + a);
        doSetPortsInvalid("p" + d + "-" + a);
        
    }
    
    /*
     * Call setVlans() with input and check if the the result from 
     * getVlanList() matches expected
     */
    public void doSetVlans(String input, Integer[] expected) {
        Integer[] dummy = new Integer[0];
        Integer[] actual;
        r.setVlans(input);
        assertEquals(input, r.getVlans());
        if (expected == null) {
            assertEquals(null, r.getVlanList());
        } else {
            actual = r.getVlanList().toArray(dummy);
            // TODO: could sort actual and expected
            assertArrayEquals(expected, actual);
        }
    }

    /*
     * Calls set port with input and expect an IllegalArgumentException 
     */
    public void doSetVlansInvalid(String input) {
        String oldVal = r.getVlans();
        List<Integer> oldList = r.getVlanList();
        try {
            r.setVlans(input);
            fail("setVlans(" + input
                 + ") should have thrown a IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            //expected
        }
        assertEquals(oldVal, r.getVlans());
        assertEquals(oldList, r.getVlanList());
    }


    @Test
    public void testExplodeVlans() {
        r.setVlans(null);
        assertEquals(null, r.getVlans());
        assertEquals(null, r.getVlanList());
        
        // TODO: 
        //  * shouldn't we throw IllegalArgumentException for vlan ids
        //    that are out-of-bounds
        //  * Shouldn't "-1" translate to "no vlan"
        //  * String.split oddities (see above)
        //  * Many work but should really fail with an exception
        //  * handling of leading and trailing spaces (differs from other fields)
        doSetVlans("5", new Integer[]{5});
        doSetVlans("0", new Integer[]{0});
        doSetVlans("4095", new Integer[]{4095});
        doSetVlans("4096", null);
        doSetVlans("-1", null);
        doSetVlans("-44", null); // TODO
        doSetVlans("32-40", new Integer[]{32,33,34,35,36,37,38,39,40});
        doSetVlans("1-5", new Integer[]{1,2,3,4,5});
        doSetVlans("1-5,,", new Integer[]{1,2,3,4,5}); // TODO: leading ,, fails
        doSetVlans("0-5", new Integer[]{0,1,2,3,4,5});
        doSetVlans("5-0", null); // TODO
        doSetVlans("4094-4095", new Integer[]{4094,4095});
        doSetVlans("4094-4097", new Integer[]{4094,4095});
        doSetVlans("1-5,4094-4097,32-40", 
                   new Integer[]{1,2,3,4,5,4094,4095,32,33,34,35,36,37,38,39,40});
        doSetVlans("1,2,3,4-5,4094,4095,32-40,4242,", 
                   new Integer[]{1,2,3,4,5,4094,4095,32,33,34,35,36,37,38,39,40});
        doSetVlans("42,23,100", new Integer[] {42, 23, 100});
        doSetVlans("042,023,0100", new Integer[] {42, 23, 100});
        doSetVlans("untagged", new Integer[] {0xffff});
        
        
        String a = Long.valueOf((long)Integer.MAX_VALUE + 1).toString();
        String b = Long.valueOf((long)Integer.MAX_VALUE + 10).toString();
        String c = Long.valueOf((long)Integer.MAX_VALUE ).toString();
        String d = Long.valueOf((long)Integer.MAX_VALUE - 1).toString();
        doSetVlans(c + "-" + d, null);
        doSetVlansInvalid(a);
        doSetVlansInvalid(a + "-" + b);
        doSetVlansInvalid("p" + c + "-" + a);
        doSetVlansInvalid("p" + d + "-" + a);
        doSetVlansInvalid("asdf");
        doSetVlansInvalid("1-5,asdf");
        doSetVlansInvalid(",,1-5"); // TODO: works for ports, trailing works
        doSetVlansInvalid("6,,1-5"); // TODO: works for ports, trailing works
        doSetVlansInvalid("44-asdf"); 
        doSetVlansInvalid("--44"); 
        doSetVlansInvalid("44--"); 
        doSetVlansInvalid("1-5,4094-x097,32-40");
        doSetVlansInvalid("  1-5");
        doSetVlansInvalid("1 -5");
        doSetVlansInvalid("1- 5");
        doSetVlansInvalid("1-5 ");
    }
 
    /*
     * Call setVlans() with input and check if the the result from 
     * getVlanList() matches expected
     */
    public void doSetTags(String input, String[] expected) {
        String[] dummy = new String[0];
        String[] actual;
        r.setTags(input);
        assertEquals(input, r.getTags());
        if (expected == null) {
            assertEquals(null, r.getTagList());
        } else {
            actual = r.getTagList().toArray(dummy);
            // TODO: could sort actual and expected
            assertArrayEquals(expected, actual);
        }
    }

    /*
     * Calls set port with input and expect an IllegalArgumentException 
     */
    public void doSetTagsInvalid(String input) {
        String oldVal = r.getTags();
        List<String> oldList = r.getTagList();
        try {
            r.setTags(input);
            fail("setTags(" + input
                 + ") should have thrown a IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            //expected
        }
        assertEquals(oldVal, r.getTags());
        assertEquals(oldList, r.getTagList());
    }

    
    @Test
    public void testExplodeTags() {
        r.setTags(null);
        assertEquals(null, r.getTags());
        assertEquals(null, r.getTagList());
        
        // TODO: 
        //  * we treat leading and trailing whitespace differently than for ports
        //    and vlans
        doSetTags("com.ns1.tag1=v1", new String[]{"com.ns1|tag1|v1"});
        doSetTags("com..ns1.tag1=v1", new String[]{"com..ns1|tag1|v1"}); // TODO
        doSetTags("com.ns1..tag1=v1", new String[]{"com.ns1.|tag1|v1"}); // TODO
        doSetTags("tag42=v42", new String[]{"default|tag42|v42"});
        doSetTags("42=42", new String[]{"default|42|42"});
        doSetTags("42.42=42", new String[]{"42|42|42"});
        doSetTags(".tag42=v42", new String[]{"|tag42|v42"});
        doSetTags("ns1.tag1=v1,tag42=v42", 
                  new String[]{"ns1|tag1|v1", "default|tag42|v42"});
        doSetTags("ns1.tag1=v1,tag42=v42,,,", 
                  new String[]{"ns1|tag1|v1", "default|tag42|v42"}); // TODO: trailing ,,
        doSetTags("ns1.tag1=v1,ns2.tag2=v2", 
                  new String[]{"ns1|tag1|v1", "ns2|tag2|v2"});
        doSetTags("ns1.tag1=v1,ns2.tag2=v2,com.ns3.tag3=v3", 
                  new String[]{"ns1|tag1|v1", "ns2|tag2|v2", "com.ns3|tag3|v3"});
        doSetTags(" com.ns1.tag1=v1", new String[]{"com.ns1|tag1|v1"});
        doSetTags("com.ns1.tag1=v1 ", new String[]{"com.ns1|tag1|v1"});
        doSetTags("a.b.c.d.e=f,ns1.tag1=v1", 
                  new String[]{"a.b.c.d|e|f", "ns1|tag1|v1"});
        
        doSetTagsInvalid("");
        doSetTagsInvalid("=");
        doSetTagsInvalid("com.+ns1.tag1=v1");
        doSetTagsInvalid("com.n%1.tag1=v1");
        doSetTagsInvalid("com.ns1.tag1=^^");
        doSetTagsInvalid("com.ns1.tag1=v1[]");
        doSetTagsInvalid("com.ns1.tag1[]=v1");
        doSetTagsInvalid("com.ns1.:tag1=v1");
        doSetTagsInvalid("com.ns1.=v1");
        doSetTagsInvalid("com.ns1.=v1");
        doSetTagsInvalid("ns1.tag1=v1,,tag42=v42");
        doSetTagsInvalid(",,ns1.tag1=v1,tag42=v42");
        doSetTagsInvalid("asdf,com.ns1.tag1=v1");
        doSetTagsInvalid("com.ns1.tag1=v1,asdf");
        // all kinds of spaces
        doSetTagsInvalid("com.ns1.tag1==v1");
        doSetTagsInvalid("com. ns1.tag1=v1");
        doSetTagsInvalid("com.ns1. tag1=v1");
        doSetTagsInvalid("com.ns1 .tag1=v1");
        doSetTagsInvalid("com.ns1.tag1 =v1");
        doSetTagsInvalid("com.ns1.tag1= v1");
        doSetTagsInvalid("com.ns1.tag1==1");
        doSetTagsInvalid("com.ns1.tag1==");
        doSetTagsInvalid("com.ns1.tag1=");
        doSetTagsInvalid("=v1");
    }
    
    @Test
    public void testGetFixedInterfaceNames() {
        r.setSwitchId(HexString.toHexString(1L));
        
        // TODO: if we have a tag rule and/or vlan rule, 
        // shouldn't we consider the
        // interface to be "virtual" as well
        
        r.setName("42");
        r.setMac(null);
        r.setIpSubnet(null);
        assertEquals("Eth42", r.getFixedInterfaceName());
        r.setMac(HexString.toHexString(1L, 6));
        assertEquals("VEth42", r.getFixedInterfaceName());
        r.setMac(null);
        r.setIpSubnet("1.2.3.4/8");
        assertEquals("VEth42", r.getFixedInterfaceName());
        
        r.setName("Foobar");
        assertEquals("Foobar", r.getFixedInterfaceName());
        r.setMac(null);
        r.setIpSubnet(null);
        assertEquals("Foobar", r.getFixedInterfaceName());
    }
    
    
    public List<OFPhysicalPort> getMockEnabledPorts(String[] names) {
        List<OFPhysicalPort> l = new ArrayList<OFPhysicalPort>();
        for (String n: names) {
            OFPhysicalPort p = new OFPhysicalPort();
            p.setName(n);
            l.add(p);
        }
        return l;
    }
    
    public String[] list2SortedArray(List<String> list) {
        System.out.println(list);
        String[] rv = list.toArray(new String[0]);
        System.out.println(Arrays.toString(rv));
        Arrays.sort(rv);
        System.out.println(Arrays.toString(rv));
        return rv;
    }
    
    
    @Test
    public void testGetFixedSubInterfaceNames() {
        IOFSwitch sw = createMock(IOFSwitch.class);
        
        // TODO: if we have a tag rule and/or vlan rule, 
        // shouldn't we consider the
        // interface to be "virtual" as well. I.e., there are no
        // sub interfaces !!
        
        
        for (String ruleName: new String[] { "42", "foo" }) {
            r.setMac(null);
            r.setIpSubnet(null);
            r.setTags(null);
            r.setPorts(null);
            r.setName(ruleName);
            r.setSwitchId(HexString.toHexString(1L));

            // No subifaces: MAC is set
            replay(sw);
            r.setMac(HexString.toHexString(1L, 6));
            r.setIpSubnet(null);
            assertEquals(null, r.getFixedSubInterfaceNames(sw));
            // No subifaces: IP is set
            r.setIpSubnet("1.2.3.4/8");
            r.setMac(null);
            assertEquals(null, r.getFixedSubInterfaceNames(sw));
            verify(sw);
            reset(sw);
            // No subifaces: different switch
            r.setIpSubnet(null);
            r.setMac(null);
            expect(sw.getStringId()).andReturn(HexString.toHexString(2L))
                    .anyTimes();
            replay(sw);
            assertEquals(null, r.getFixedSubInterfaceNames(sw));
            verify(sw);
            reset(sw);

            //------
            // Now let's get some sub-interface names
            String[] portNames = new String[] { "eth1", "eth2", "eth3" };
            String prefix = r.getFixedInterfaceName() + "/";
            List<OFPhysicalPort> ports = getMockEnabledPorts(portNames);

            // No port based rules. One sub-iface per port
            expect(sw.getStringId()).andReturn(HexString.toHexString(1L))
                    .anyTimes();
            expect(sw.getEnabledPorts()).andReturn(ports).once();
            replay(sw);
            assertArrayEquals("current main interface: " + prefix,
                              new String[] { prefix+"eth1", prefix+"eth2", prefix+"eth3" },
                              list2SortedArray(r.getFixedSubInterfaceNames(sw)));
            verify(sw);
            reset(sw);

            //--- one port in rule
            r.setPorts("eth2");
            expect(sw.getStringId()).andReturn(HexString.toHexString(1L))
                    .anyTimes();
            expect(sw.getEnabledPorts()).andReturn(ports).once();
            replay(sw);
            assertArrayEquals("current main interface: " + prefix,
                              new String[] { prefix+"eth2" }, 
                              list2SortedArray(r.getFixedSubInterfaceNames(sw)));
            verify(sw);
            reset(sw);

            //--- two ports in rule
            r.setPorts("eth1,eth2");
            expect(sw.getStringId()).andReturn(HexString.toHexString(1L))
                    .anyTimes();
            expect(sw.getEnabledPorts()).andReturn(ports).once();
            replay(sw);
            assertArrayEquals("current main interface: " + prefix,
                              new String[] { prefix+"eth1", prefix+"eth2" }, 
                              list2SortedArray(r.getFixedSubInterfaceNames(sw)));
            verify(sw);
            reset(sw);
        }
    }
    
    // TODO: Missing tests for:
    // * getInterfaceNameForDevice()
    // * hashCode()
    // * compareTo()
    // * equals()
    // * toString()
}
