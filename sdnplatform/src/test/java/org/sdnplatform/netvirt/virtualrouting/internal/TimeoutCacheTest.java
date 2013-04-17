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

package org.sdnplatform.netvirt.virtualrouting.internal;

import static org.junit.Assert.*;
import org.junit.Test;
import org.sdnplatform.netvirt.virtualrouting.internal.TimeoutCache;


public class TimeoutCacheTest {
    @Test
    public void testTimeoutCache() throws InterruptedException {
        TimeoutCache<Integer> tc = new TimeoutCache<Integer>(30);
        
        assertEquals(false, tc.remove(42));
        assertEquals(false, tc.putIfAbsent(42));
        assertEquals(true, tc.remove(42));
        assertEquals(false, tc.remove(42));
        
        assertEquals(false, tc.isTimeoutExpired(1));
        assertEquals(false, tc.putIfAbsent(1));
        
        assertEquals(false, tc.isTimeoutExpired(1));
      
        Thread.sleep(20);
        // Timeout not expired. add element again
        assertEquals(true, tc.putIfAbsent(1));
        assertEquals(false, tc.isTimeoutExpired(1));
        
        Thread.sleep(15);
        // Time expired after first add but not second. Timeout for element
        // should be expired
        assertEquals(true, tc.isTimeoutExpired(1));
        assertEquals(true, tc.remove(1));
        assertEquals(false, tc.isTimeoutExpired(1));
    }

}
