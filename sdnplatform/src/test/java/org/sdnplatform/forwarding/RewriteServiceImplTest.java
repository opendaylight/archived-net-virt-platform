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

package org.sdnplatform.forwarding;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;


import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.forwarding.RewriteServiceImpl;
import org.sdnplatform.packet.Ethernet;


public class RewriteServiceImplTest {
    protected ListenerContext cntx;
    protected RewriteServiceImpl rw;
    protected IAddressSpaceManagerService addressSpaceManager;
    
    protected static Long[] macsToTest = new Long[] {
                Long.MIN_VALUE, Long.MAX_VALUE, -35342513L, 
                (long)Integer.MIN_VALUE, (long)Integer.MAX_VALUE,
                0L, 1L, 2L, 3L, -1L, -2L, -3L, 4549495L, 42L, 129329392L
            };
    @Before
    public void setUp() throws Exception {
        ModuleContext fmc = new ModuleContext();
        cntx = new ListenerContext();
        rw = new RewriteServiceImpl();
        addressSpaceManager = createNiceMock(IAddressSpaceManagerService.class);
        
        fmc.addService(IAddressSpaceManagerService.class, addressSpaceManager);
        rw.init(fmc);
        rw.startUp(fmc);
        replay(addressSpaceManager);
        
    }
    
    @Test
    public void testGetRuleFromContext() {
        RewriteServiceImpl.RewriteRule rule;
        RewriteServiceImpl.RewriteRule rule2;
        assertNull(cntx.getStorage().get(RewriteServiceImpl.REWRITE_RULE));
        rule = rw.getRuleFromContext(cntx);
        assertSame(rule, cntx.getStorage().get(RewriteServiceImpl.REWRITE_RULE));
        cntx.getStorage().clear();
        rule2 = rw.getRuleFromContext(cntx);
        assertNotSame(rule, rule2);
        
        ListenerContext cntx1 = new ListenerContext();
        ListenerContext cntx2 = new ListenerContext();
        rule = rw.getRuleFromContext(cntx1);
        rule2 = rw.getRuleFromContext(cntx2);
        assertNotSame(rule, rule2);
        
        rw.setIngressDstMac(1L, 11L, cntx1);
        rw.setTransportVlan((short)42, cntx2);
        assertEquals(Long.valueOf(1L), rule.origDstMac);
        assertEquals(Long.valueOf(11L), rule.finalDstMac);
        assertEquals(null, rule.transportVlan);
        assertEquals(null, rule.origSrcMac);
        assertEquals(null, rule.finalSrcMac);
        assertEquals(null, rule.ttlDecrement);
        
        assertEquals(null, rule2.origDstMac);
        assertEquals(null, rule2.finalDstMac);
        assertEquals(Short.valueOf((short)42) , rule2.transportVlan);
        assertEquals(null, rule2.origSrcMac);
        assertEquals(null, rule2.finalSrcMac);
        assertEquals(null, rule2.ttlDecrement);
    }
    
    @Test
    public void testGetRuleFromContextIfExists() {
        RewriteServiceImpl.RewriteRule rule;
        RewriteServiceImpl.RewriteRule rule2;
        assertNull(cntx.getStorage().get(RewriteServiceImpl.REWRITE_RULE));
        rule = rw.getRuleFromContextIfExists(cntx);
        assertNull(cntx.getStorage().get(RewriteServiceImpl.REWRITE_RULE));
        assertNull(rule);
        
        rule2 = rw.getRuleFromContext(cntx);
        assertSame(rule2, cntx.getStorage().get(RewriteServiceImpl.REWRITE_RULE));
        rule = rw.getRuleFromContextIfExists(cntx);
        assertSame(rule, rule2);
        
        
        ListenerContext cntx1 = new ListenerContext();
        ListenerContext cntx2 = new ListenerContext();
        rule = rw.getRuleFromContextIfExists(cntx1);
        rule2 = rw.getRuleFromContext(cntx2);
        assertNull(rule);
        assertNotNull(rule2);
        
        rw.setIngressDstMac(1L, 11L, cntx1);
        rw.setTransportVlan((short)42, cntx2);
        rule = rw.getRuleFromContextIfExists(cntx1);
        assertNotNull(rule);
        assertEquals(Long.valueOf(1L), rule.origDstMac);
        assertEquals(Long.valueOf(11L), rule.finalDstMac);
        assertEquals(null, rule.transportVlan);
        assertEquals(null, rule.origSrcMac);
        assertEquals(null, rule.finalSrcMac);
        assertEquals(null, rule.ttlDecrement);
        
        assertEquals(null, rule2.origDstMac);
        assertEquals(null, rule2.finalDstMac);
        assertEquals(Short.valueOf((short)42) , rule2.transportVlan);
        assertEquals(null, rule2.origSrcMac);
        assertEquals(null, rule2.finalSrcMac);
        assertEquals(null, rule2.ttlDecrement);
    }
    
    
    @Test
    public void testHasRewriteRules() {
        assertEquals(false, rw.hasRewriteRules(cntx));
        
        // only vlan
        rw.setTransportVlan((short)1, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearTransportVlan(cntx);
        assertEquals(false, rw.hasRewriteRules(cntx));
        
        // only dstMac
        rw.setIngressDstMac(1L, 11L, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearIngressDstMac(cntx);
        assertEquals(false, rw.hasRewriteRules(cntx));
        
        // only srcMac 
        rw.setEgressSrcMac(21L, 31L, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearEgressSrcMac(cntx);
        assertEquals(false, rw.hasRewriteRules(cntx));
        
        // only TTL decrement
        rw.setTtlDecrement(1, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearTtlDecrement(cntx);
        assertEquals(false, rw.hasRewriteRules(cntx));
        
        
        // all of them
        // first we set all of them 
        rw.setTransportVlan((short)1, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.setIngressDstMac(1L, 11L, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.setEgressSrcMac(21L, 31L, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.setTtlDecrement(1, cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        // no we remove all of them 
        rw.clearTransportVlan(cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearIngressDstMac(cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearEgressSrcMac(cntx);
        assertEquals(true, rw.hasRewriteRules(cntx));
        rw.clearTtlDecrement(cntx);
        assertEquals(false, rw.hasRewriteRules(cntx));
    }
    
    @Test
    public void testDstMacRulesExceptions() {
        try {
            rw.setIngressDstMac(1L, 11L, null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.setIngressDstMac(1L, null, cntx);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.setIngressDstMac(null, 1L, cntx);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.getOrigIngressDstMac(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.getFinalIngressDstMac(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.clearIngressDstMac(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
    }
    
    @Test
    public void testVlanRulesExceptions() {
        try {
            rw.setTransportVlan((short)1, null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.setTransportVlan(null, cntx);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        
        // check with invalid vlans
        Short[] invalidVlans = new Short [] { -1, 0, 4096, 
                                              Ethernet.VLAN_UNTAGGED
                                            };
        for (Short vlan: invalidVlans) {
            try {
                rw.setTransportVlan(vlan, cntx);
                fail("Expected exception was not thrown");
            } catch (IllegalArgumentException e) {
                // exptected
            }
        }
        
        // check with valid vlans. This should succeed
        Short[] validVlans = new Short [] { 1, 2, 3,
                                            256, 512, 1024, 2048, 4094, 4095,
                                            42, 424, 23 //, Ethernet.VLAN_UNTAGGED
                                          };
        for (Short vlan: validVlans) {
            // Set the MAC
            rw.setTransportVlan(vlan, cntx);
            assertEquals(vlan, rw.getTransportVlan(cntx));
        }
        
        try {
            rw.getTransportVlan(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.clearTransportVlan(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
    }
    
    @Test
    public void testSrcMacRulesExceptions() {
        try {
            rw.setEgressSrcMac(1L, 11L, null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.setEgressSrcMac(1L, null, cntx);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.setEgressSrcMac(null, 1L, cntx);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.getOrigEgressSrcMac(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.getFinalEgressSrcMac(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.clearEgressSrcMac(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
    }
    
    
    @Test
    public void testTtlDecrementRuleExceptions() {
        try {
            rw.setTtlDecrement(1, null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.getTtlDecrement(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        try {
            rw.clearTtlDecrement(null);
            fail("Expected exception was not thrown");
        } catch (NullPointerException e) {
            // exptected
        }
        // invalid number of hops
        for (int n: new int[] { -1, 0, 255, 256}) {
            try {
                rw.setTtlDecrement(n, cntx);
                fail("Expected exception was not thrown for n=" + n);
            } catch (IllegalArgumentException e) {
                // exptected
            }
        }
        // valid number of hops
        for (int n: new int[] { 1, 2, 3, 42, 253, 254 }) {
            rw.setTtlDecrement(n, cntx);
            assertEquals(Integer.valueOf(n), rw.getTtlDecrement(cntx));
        }
    }
    
    
    
    @Test
    public void testDstMacRules() {
        assertNull(rw.getOrigIngressDstMac(cntx));
        assertNull(rw.getFinalIngressDstMac(cntx));
        rw.clearIngressDstMac(cntx);
        assertNull(rw.getOrigIngressDstMac(cntx));
        assertNull(rw.getFinalIngressDstMac(cntx));
        rw.clearIngressDstMac(cntx);
        
        for (Long origMac: macsToTest) {
            for (Long newMac: macsToTest) {
                // Set the MAC
                rw.setIngressDstMac(origMac, newMac, cntx);
                if (origMac.equals(newMac)) {
                    assertNull(rw.getOrigIngressDstMac(cntx));
                    assertNull(rw.getFinalIngressDstMac(cntx));
                    continue;
                }
                assertEquals(origMac, rw.getOrigIngressDstMac(cntx));
                assertEquals(newMac, rw.getFinalIngressDstMac(cntx));
                
                // Make sure there's no effect to the other fields
                assertEquals(null, rw.getTransportVlan(cntx));
                rw.setTransportVlan((short)1, cntx);
                assertEquals(Short.valueOf((short)1), rw.getTransportVlan(cntx));
                assertEquals(origMac, rw.getOrigIngressDstMac(cntx));
                assertEquals(newMac, rw.getFinalIngressDstMac(cntx));
                rw.clearTransportVlan(cntx);
                assertEquals(origMac, rw.getOrigIngressDstMac(cntx));
                assertEquals(newMac, rw.getFinalIngressDstMac(cntx));
                
                assertNull(rw.getOrigEgressSrcMac(cntx));
                assertNull(rw.getFinalEgressSrcMac(cntx));
                rw.setEgressSrcMac(1L, 11L, cntx);
                assertEquals(Long.valueOf(1L), rw.getOrigEgressSrcMac(cntx));
                assertEquals(Long.valueOf(11L), rw.getFinalEgressSrcMac(cntx));
                rw.clearEgressSrcMac(cntx);
                assertNull(rw.getOrigEgressSrcMac(cntx));
                assertNull(rw.getFinalEgressSrcMac(cntx));
                
                // try to reset and clear the MAC
                rw.clearIngressDstMac(cntx);
                assertNull(rw.getOrigIngressDstMac(cntx));
                assertNull(rw.getFinalIngressDstMac(cntx));
            }
        }
        assertNull(rw.getOrigIngressDstMac(cntx));
        assertNull(rw.getFinalIngressDstMac(cntx));
        assertNull(rw.getTransportVlan(cntx));
    }
    
    
    @Test
    public void testSrcMacRules() {
        assertNull(rw.getOrigEgressSrcMac(cntx));
        assertNull(rw.getFinalEgressSrcMac(cntx));
        rw.clearEgressSrcMac(cntx);
        assertNull(rw.getOrigEgressSrcMac(cntx));
        assertNull(rw.getFinalEgressSrcMac(cntx));
        rw.clearEgressSrcMac(cntx);
        
        for (Long origMac: macsToTest) {
            for (Long newMac: macsToTest) {
                // Set the MAC
                rw.setEgressSrcMac(origMac, newMac, cntx);
                if (origMac.equals(newMac)) {
                    assertNull(rw.getOrigEgressSrcMac(cntx));
                    assertNull(rw.getFinalEgressSrcMac(cntx));
                    continue;
                }
                assertEquals(origMac, rw.getOrigEgressSrcMac(cntx));
                assertEquals(newMac, rw.getFinalEgressSrcMac(cntx));
                
                // Make sure there's no effect to the other fields
                assertEquals(null, rw.getTransportVlan(cntx));
                rw.setTransportVlan((short)1, cntx);
                assertEquals(Short.valueOf((short)1), rw.getTransportVlan(cntx));
                assertEquals(origMac, rw.getOrigEgressSrcMac(cntx));
                assertEquals(newMac, rw.getFinalEgressSrcMac(cntx));
                
                assertNull(rw.getOrigIngressDstMac(cntx));
                assertNull(rw.getFinalIngressDstMac(cntx));
                rw.setIngressDstMac(1L, 11L, cntx);
                assertEquals(Long.valueOf(1L), rw.getOrigIngressDstMac(cntx));
                assertEquals(Long.valueOf(11L), rw.getFinalIngressDstMac(cntx));
                rw.clearIngressDstMac(cntx);
                assertNull(rw.getOrigIngressDstMac(cntx));
                assertNull(rw.getFinalIngressDstMac(cntx));
                
                rw.clearTransportVlan(cntx);
                assertEquals(origMac, rw.getOrigEgressSrcMac(cntx));
                assertEquals(newMac, rw.getFinalEgressSrcMac(cntx));
                
                // try to reset and clear the MAC
                rw.clearEgressSrcMac(cntx);
                assertNull(rw.getOrigEgressSrcMac(cntx));
                assertNull(rw.getFinalEgressSrcMac(cntx));
            }
        }
        assertNull(rw.getOrigEgressSrcMac(cntx));
        assertNull(rw.getFinalEgressSrcMac(cntx));
        assertNull(rw.getTransportVlan(cntx));
    }
    
    
    @Test
    public void testVlanRules() {
        assertNull(rw.getTransportVlan(cntx));
        rw.clearTransportVlan(cntx);
        assertNull(rw.getTransportVlan(cntx));
        
        Short[] validVlans = new Short [] { 1, 2, 3,
                                            256, 512, 1024, 2048, 4094, 4095,
                                            42, 424, 23 //, Ethernet.VLAN_UNTAGGED
                                          };
        for (Short vlan: validVlans) {
            // Set the MAC
            rw.setTransportVlan(vlan, cntx);
            assertEquals(vlan, rw.getTransportVlan(cntx));
            
            // Make sure there's no effect to the other fields
            assertNull(rw.getOrigIngressDstMac(cntx));
            assertNull(rw.getFinalIngressDstMac(cntx));
            rw.setIngressDstMac(1L, 11L, cntx);
            assertEquals(Long.valueOf(1L), rw.getOrigIngressDstMac(cntx));
            assertEquals(Long.valueOf(11L), rw.getFinalIngressDstMac(cntx));
            assertEquals(vlan, rw.getTransportVlan(cntx));
            rw.clearIngressDstMac(cntx);
            assertEquals(vlan, rw.getTransportVlan(cntx));
            
            // try to reset and clear the VLAN
            rw.setTransportVlan(vlan, cntx);
            assertEquals(vlan, rw.getTransportVlan(cntx));
            rw.setTransportVlan((short)100, cntx);
            assertEquals(Short.valueOf((short)100), rw.getTransportVlan(cntx));
            rw.clearTransportVlan(cntx);
            assertNull(rw.getTransportVlan(cntx));
        }
        assertNull(rw.getOrigIngressDstMac(cntx));
        assertNull(rw.getFinalIngressDstMac(cntx));
        assertNull(rw.getTransportVlan(cntx));
    }
    
    
    @Test
    public void testTtlDecrementRules() {
        assertNull(rw.getTtlDecrement(cntx));
        rw.clearTtlDecrement(cntx);
        assertNull(rw.getTtlDecrement(cntx));
        
        int[] validNumHops = new int[] { 1, 2, 3, 4, 42, 254 };
        
        for (int n: validNumHops) {
            rw.setTtlDecrement(n, cntx);
            // Make sure there's no effect to the other fields
            assertNull(rw.getOrigIngressDstMac(cntx));
            assertNull(rw.getFinalIngressDstMac(cntx));
            rw.setIngressDstMac(1L, 11L, cntx);
            assertEquals(Long.valueOf(1L), rw.getOrigIngressDstMac(cntx));
            assertEquals(Long.valueOf(11L), rw.getFinalIngressDstMac(cntx));
            assertEquals(Integer.valueOf(n), rw.getTtlDecrement(cntx));
            rw.clearIngressDstMac(cntx);
            assertEquals(Integer.valueOf(n), rw.getTtlDecrement(cntx));
            
            // try to reset and clear the Ttl
            rw.setTtlDecrement(n, cntx);
            assertEquals(Integer.valueOf(n), rw.getTtlDecrement(cntx));
            rw.setTtlDecrement(100, cntx);
            assertEquals(Integer.valueOf(100), rw.getTtlDecrement(cntx));
            rw.clearTtlDecrement(cntx);
            assertNull(rw.getTtlDecrement(cntx));
        }
    }
    
    
    @Test
    public void testSwitchPortVlanMode() {
        SwitchPort swp = new SwitchPort(1L, 1);
        Short[] vlans = new Short [] { 1, 2, 3, null,
                                       256, 512, 1024, 2048, 4094, 4095,
                                       42, 424, 23, Ethernet.VLAN_UNTAGGED
                                          };
        resetToDefault(addressSpaceManager);
        for (Short vlan: vlans) {
            String addressSpaceName = "AS" + vlan;
            expect(addressSpaceManager.getSwitchPortVlanMode(swp, addressSpaceName,
                                                             vlan, true))
                    .andReturn(vlan).once();
        }
        replay(addressSpaceManager);
        for (Short vlan: vlans) {
            String addressSpaceName = "AS" + vlan;
            assertEquals(vlan, rw.getSwitchPortVlanMode(swp, addressSpaceName,
                                                        vlan, true));
        }
        verify(addressSpaceManager);
    }
}
