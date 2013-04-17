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

import org.junit.Before;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.sdnplatform.devicegroup.IDeviceGroup;


public abstract class AbstractDeviceGroupContractTEST {

    protected IDeviceGroup dg;
    
    @Before
    public void setUpDeviceGroup() {
        dg = allocateDeviceGroup();
    }
    
    // returns a new concrete instance
    protected abstract IDeviceGroup allocateDeviceGroup();
    
    @Test
    public final void testName() {
        assertEquals(false, dg.getName()==null);
        try {
            dg.setName(null);
            fail("setName(null) should have thrown a NullPointerException");
        }
        catch (NullPointerException e) {
            // expected
        }
        dg.setName("foo");
        assertEquals("foo", dg.getName());
        dg.setName("bar");
        assertEquals("bar", dg.getName());
    }
    
    @Test
    public final void testPriority() {
        int[] priorities = new int[] { Integer.MIN_VALUE,
                            Integer.MAX_VALUE,
                            -1,
                            0, 
                            1,
                            424
                            -454};
        for (int p : priorities) {
            dg.setPriority(p);
            assertEquals(p, dg.getPriority());
        }
    }
    
    @Test 
    public final void testActive() {
        dg.setActive(false);
        assertEquals(false, dg.isActive());
        dg.setActive(true);
        assertEquals(true, dg.isActive());
    }
    
    public void doTransitiveTest(IDeviceGroup dg1,
                                 IDeviceGroup dg2,
                                 IDeviceGroup dg3) {
        if (dg1.compareTo(dg2) == dg2.compareTo(dg3)) {
            assertEquals(dg1.compareTo(dg2),
                         dg1.compareTo(dg3));
        }
        if (dg1.compareTo(dg2)==0) {
            assertEquals(dg1.compareTo(dg3),
                         dg2.compareTo(dg3));
        }
    }
    
    @Test
    public final void testCompareToAndEquals() {
        int[] priorities = new int[] { Integer.MIN_VALUE,
                            Integer.MAX_VALUE,
                            -1,
                            0, 
                            1,
                            424
                            -454};
        String [] names = new String[] { "foo",
                                         "bar",
                                         "theseArentTheDroidYoureLookingFor"};
                                         
        try {
            IDeviceGroup other = createMock(IDeviceGroup.class);
            dg.compareTo(other);
            fail("compareTo() should have thrown IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        IDeviceGroup dg2 = allocateDeviceGroup();
        IDeviceGroup dg3 = allocateDeviceGroup();


        // TODO: Comparable doesn't require -1, 1 to be returned. Maybe
        // we should relax these tests and just check <0 >0
        // whoa. that's what I call nested loops
        for (int p1 : priorities) {
            for (int p2 : priorities) {
                for (String name1: names ) {
                    for (String name2: names) {
                        dg.setName(name1);
                        dg.setPriority(p1);
                        dg2.setName(name2);
                        dg2.setPriority(p2);
                        // Test symmetry
                        assertEquals(dg.compareTo(dg2), -dg2.compareTo(dg));
                        assertEquals(dg.equals(dg2), dg2.equals(dg));
                        if (p1 == p2) {
                            assertEquals(name1.compareTo(name2), 
                                         dg.compareTo(dg2));
                        } else if ( p1 < p2 ) {
                            assertEquals(1, dg.compareTo(dg2));
                        } else {
                            assertEquals(-1, dg.compareTo(dg2));
                        }
                        if (p1 == p2 && name1.equals(name2)) {
                            assertEquals(0, dg.compareTo(dg2));
                            assertEquals(true, dg.equals(dg2));
                        }
                        assertEquals(name1.equals(name2), dg.equals(dg2));
                        
                        // and not for some transitive tests
                        for(int p3: priorities) {
                            for (String name3: names) {
                                dg3.setName(name3);
                                dg3.setPriority(p3);
                                doTransitiveTest(dg, dg2, dg3);
                            }
                        }
                    }
                }
            }
        }
    }
}
