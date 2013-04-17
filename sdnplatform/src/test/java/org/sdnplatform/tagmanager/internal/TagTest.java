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

package org.sdnplatform.tagmanager.internal;
import org.junit.Test;
import org.sdnplatform.tagmanager.Tag;
import org.sdnplatform.tagmanager.TagInvalidNameException;
import org.sdnplatform.tagmanager.TagInvalidNamespaceException;
import org.sdnplatform.tagmanager.TagInvalidValueException;
import org.sdnplatform.test.PlatformTestCase;



public class TagTest extends PlatformTestCase {
    public static String ns = "com.namespace";
    public static String name = "name";
    public static String value = "value";
    
    @Test
    public void testTagCreation() {
        Tag tag = null;
        String tagStr = null;
        try {
            tagStr = ns + "." + name;
            tag = new Tag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag == null);
        
        try {
            tagStr = ns + "." + name + "=" + value;
            tag = new Tag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidNameException);
            return;
        }
        assertTrue(tag == null);
        
        try {
            tag = new Tag(null, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidNamespaceException);
            return;
        }
        assertTrue(tag == null);
        
        try {
            tagStr = ns + "." + name + " = " + value;
            tag = new Tag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag != null);
        assertTrue(tag.getNamespace().equals(ns));
        assertTrue(tag.getName().equals(name));
        assertTrue(tag.getValue().equals(value));
        
        try {
            tagStr = ns + "." + name + "=" + value;
            tag = new Tag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag != null);
        assertTrue(tag.getNamespace().equals(ns));
        assertTrue(tag.getName().equals(name));
        assertTrue(tag.getValue().equals(value));
        
        try {
            tagStr = ns + " . " + name + " = " + value;
            tag = new Tag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag != null);
        assertTrue(tag.getNamespace().equals(ns));
        assertTrue(tag.getName().equals(name));
        assertTrue(tag.getValue().equals(value));
    }
}
