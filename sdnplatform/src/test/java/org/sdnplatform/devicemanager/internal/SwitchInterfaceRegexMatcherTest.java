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

package org.sdnplatform.devicemanager.internal;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPhysicalPort;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.SwitchInterfaceRegexMatcher;

public class SwitchInterfaceRegexMatcherTest {
    MockControllerProvider controllerProvider;
    SwitchInterfaceRegexMatcher matcher;
    
    ConcurrentHashMap<Long, IOFSwitch> switches;
    IOFSwitch sw1;
    IOFSwitch sw2;
    ArrayList<OFPhysicalPort> sw1ports;
    ArrayList<OFPhysicalPort> sw2ports;
    
    SwitchPort s1p1;
    SwitchPort s1p2;
    SwitchPort s1p3;
    SwitchPort s2p1;
    SwitchPort s2p2;
    
    @Before
    public void setUp() {
        controllerProvider = new MockControllerProvider();
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        controllerProvider.setSwitches(switches);
        matcher = new SwitchInterfaceRegexMatcher(controllerProvider);
        
        sw1 = createMock(IOFSwitch.class);
        sw1ports = new ArrayList<OFPhysicalPort>();
        OFPhysicalPort pp = new OFPhysicalPort();
        pp.setName("eth1");
        pp.setPortNumber((short)1);
        sw1ports.add(pp);
        pp = new OFPhysicalPort();
        pp.setName("eth2");
        pp.setPortNumber((short)2);
        sw1ports.add(pp);
        pp = new OFPhysicalPort();
        pp.setName("port1");
        pp.setPortNumber((short)3);
        sw1ports.add(pp);
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getEnabledPorts()).andReturn(sw1ports).anyTimes();
        switches.put(1L, sw1);
        
        sw2 = createMock(IOFSwitch.class);
        sw2ports = new ArrayList<OFPhysicalPort>();
        pp = new OFPhysicalPort();
        pp.setName("eTh1");
        pp.setPortNumber((short)11);
        sw2ports.add(pp);
        pp = new OFPhysicalPort();
        pp.setName("EtH2");
        pp.setPortNumber((short)12);
        sw2ports.add(pp);
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.getEnabledPorts()).andReturn(sw2ports).anyTimes();
        switches.put(2L, sw2);
        
        s1p1 = new SwitchPort(1L, 1);
        s1p2 = new SwitchPort(1L, 2);
        s1p3 = new SwitchPort(1L, 3);
        s2p1 = new SwitchPort(2L, 11);
        s2p2 = new SwitchPort(2L, 12);
        
        replay(sw1, sw2);
    }
    
    protected void verifyExpectedSwitchPorts(SwitchPort[] exptected,
                                             Collection<SwitchPort> actual) {
        Set<SwitchPort> expectedSet = 
                new HashSet<SwitchPort>(Arrays.asList(exptected));
        Set<SwitchPort> actualSet = new HashSet<SwitchPort>();
        if (actual != null)
            actualSet.addAll(actual);
        assertEquals(expectedSet, actualSet);
        
    }
    
    @After
    public void tearDown() {
        verify(sw1, sw2);
    }
    
    @Test
    public void testCornerCases() {
        // make sure these are handled internally and don't generate 
        // exception. 
        
        // switch doesn't exist. 
        matcher.addOrUpdate("foo", 44L, "pattern");
        matcher.switchPortChanged(44L);
        
        // invald regex -- should be ignored
        matcher.addOrUpdate("bar", 1L, "patt[");
        matcher.addOrUpdate("bar", 1L, "pat{]");
        matcher.addOrUpdate("bar", 1L, "pat]df");
        matcher.addOrUpdate("bar", 1L, "pat}df");
        
        // remove non-existent, update existent
        matcher.addOrUpdate("key1", 1L, "eth.*");
        matcher.remove("doesnotexist");
        matcher.addOrUpdate("key1", 2L, "Eth.*");
        
        
    }
    
    /* Test add, remove, and update */ 
    @Test
    public void testAddRemoveUpdate() {
        Collection<SwitchPort> ifaces;
        
        matcher.addOrUpdate("key1", 1L, "eth1");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1 },
                                  ifaces);
        
        matcher.remove("key1");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] {},
                                  ifaces);
        
        // add again
        matcher.addOrUpdate("key1", 1L, "eth1");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1 },
                                  ifaces);
        
        // update
        matcher.addOrUpdate("key1", 1L, "eth.*");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        
        // update
        matcher.addOrUpdate("key1", 1L, "eth1");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1 },
                                  ifaces);
        
        // different key
        matcher.addOrUpdate("key2", 2L, "eth1");
        ifaces = matcher.getInterfacesByKey("key2");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1 },
                                  ifaces);
        
    }
    
    
    /* Do some general test to see the regex matching is sane */
    @Test
    public void testRegexMatching() {
        Collection<SwitchPort> ifaces;
        
        matcher.addOrUpdate("key1", 1L, "eth1");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1 },
                                  ifaces);
        matcher.remove("key1");
        
        // basic matching.
        matcher.addOrUpdate("key1", 1L, "eth.*");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        matcher.remove("key1");
        
        // different matches
        matcher.addOrUpdate("key1", 1L, "eth[0-9]");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        matcher.remove("key1");
        
        // another flavor of matching
        matcher.addOrUpdate("key1", 1L, "eth[0-9]+");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        matcher.remove("key1");
        
        // check that regex matching is anchored at start of string
        matcher.addOrUpdate("key1", 1L, "th[0-9]+");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] {},
                                  ifaces);
        matcher.remove("key1");
        
        // check that regex matching is anchored at end
        matcher.addOrUpdate("key1", 1L, "eth");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] {},
                                  ifaces);
        matcher.remove("key1");
        
        // check that regex matching is case insensitive
        matcher.addOrUpdate("key1", 1L, "eTh.*");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        matcher.remove("key1");
        
        // check that regex matching is case insensitive 
        matcher.addOrUpdate("key1", 2L, "eth.*");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1, s2p2 },
                                  ifaces);
        matcher.remove("key1");
    }
    
    
    /* Test switch wildcards */
    @Test
    public void testSwitchWildcards() { 
        Collection<SwitchPort> ifaces; 
        
        matcher.addOrUpdate("key1", 1L, "eth.*");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        
        matcher.addOrUpdate("key1", null, "eth.*");
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2, s2p1, s2p2 },
                                  ifaces);
        
        // note new key
        matcher.addOrUpdate("key2", null, ".*1");
        ifaces = matcher.getInterfacesByKey("key2");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p3, s2p1 },
                                  ifaces);
        // just to cross-check: key1 should be unaffected 
        ifaces = matcher.getInterfacesByKey("key1");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2, s2p1, s2p2 },
                                  ifaces);
    }
    
    
    /* Test adding. removing switches */
    @Test
    public void testSwitchChanges() {
        Collection<SwitchPort> ifaces; 
        
        // remove sw2 for the time being
        switches.remove(2L);
        
        // populate our regexes 
        matcher.addOrUpdate("allEth", null, "eth.*");
        matcher.addOrUpdate("sw1Eth", 1L, "eth[0-9]");
        matcher.addOrUpdate("sw2Eth", 2L, "eth.*");
        
        // verify
        ifaces = matcher.getInterfacesByKey("allEth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw1Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw2Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] {},
                                  ifaces);
        
        // add switch 2
        switches.put(2L, sw2);
        matcher.addedSwitch(sw2);
        ifaces = matcher.getInterfacesByKey("allEth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2, s2p1, s2p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw1Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw2Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1, s2p2 },
                                  ifaces);
        
        // remove switch 1
        switches.remove(1L);
        matcher.removedSwitch(sw1);
        ifaces = matcher.getInterfacesByKey("allEth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1, s2p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw1Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] {},
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw2Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1, s2p2 },
                                  ifaces);
        
        // add switch 1 back
        switches.put(1L, sw1);
        matcher.addedSwitch(sw1);
        ifaces = matcher.getInterfacesByKey("allEth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2, s2p1, s2p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw1Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p1, s1p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw2Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1, s2p2 },
                                  ifaces);
        
        // modify ports on sw1. Since we just fiddle around in the list of
        // ports we don't need to reset the mock
        sw1ports.remove(0); // remove port 1
        OFPhysicalPort p = new OFPhysicalPort();
        p.setPortNumber((short)4);
        p.setName("eth4");
        sw1ports.add(p);
        SwitchPort s1p4 = new SwitchPort(1L, 4);
        matcher.switchPortChanged(1L);
        ifaces = matcher.getInterfacesByKey("allEth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p2, s1p4, s2p1, s2p2 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw1Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s1p2, s1p4 },
                                  ifaces);
        ifaces = matcher.getInterfacesByKey("sw2Eth");
        verifyExpectedSwitchPorts(new SwitchPort[] { s2p1, s2p2 },
                                  ifaces);
    }
}
