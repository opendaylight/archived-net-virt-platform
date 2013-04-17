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

package org.sdnplatform;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sdnplatform.SimpleVersion;

public class SimpleVersionTest {
    @Test
    public void testParsing() {
        SimpleVersion v;
        
        v = new SimpleVersion();
        assertEquals(0, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getBuild());
        
        v.setVersion("1.2.3");
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getBuild());
        
        v.clear();
        assertEquals(0, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getBuild());
        
        v = new SimpleVersion("1.2.3");
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getBuild());
        
        v.setVersion("asdf11.22.33.44.55.66asdf");
        assertEquals(11, v.getMajor());
        assertEquals(22, v.getMinor());
        assertEquals(33, v.getBuild());
        
        v.setVersion("asdf1.2.asdf.11.22.33.asdf");
        assertEquals(11, v.getMajor());
        assertEquals(22, v.getMinor());
        assertEquals(33, v.getBuild());
        
        try {
            v.setVersion("1.2.");
            fail("Should have thrown Exception");
        } catch (IllegalArgumentException e) {
            assertEquals(0, v.getMajor());
            assertEquals(0, v.getMinor());
            assertEquals(0, v.getBuild());
        }
        
        try {
            v = new SimpleVersion("asdf");
            fail("Should have thrown Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        
        try {
            v.setVersion("12123456789012345678901233.9.9");
            fail("Should have thrown Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            v.setVersion("1.123456789012345678901233.2");
            fail("Should have thrown Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            v.setVersion("1.2.123456789012345678901233");
            fail("Should have thrown Exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
    
    protected void doOneCompare(String v1, String v2, int expected) {
        SimpleVersion sv1 = new SimpleVersion(v1);
        SimpleVersion sv2 = new SimpleVersion(v2);
        assertEquals(expected, sv1.compareTo(sv2));
        assertEquals(-expected, sv2.compareTo(sv1));
        if (sv1.compareTo(sv2) == 0)
            assertEquals(true, sv1.equals(sv2));
        if (sv1.equals(sv2))
            assertEquals(0, sv1.compareTo(sv2));
    }
    
    @Test
    public void testCompareTo() {
        doOneCompare("1.0.0", "1.0.0", 0);
        doOneCompare("1.0.1", "1.0.0", 1);
        doOneCompare("1.1.0", "1.0.0", 1);
        doOneCompare("2.0.0", "1.0.0", 1);
        
        doOneCompare("10.0.0", "2.0.0", 1);
        doOneCompare("2.0.0", "10.0.0", -1);
    }

}
