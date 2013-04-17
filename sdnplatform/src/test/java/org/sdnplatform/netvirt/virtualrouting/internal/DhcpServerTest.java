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
import org.sdnplatform.netvirt.virtualrouting.internal.DhcpServer;

public class DhcpServerTest {
    
    @Test
    public void testDchpServer() throws InterruptedException {
        DhcpServer server = new DhcpServer(30);
        
        assertEquals(true, server.isAlive());
        server.hadRequest();
        assertEquals(true, server.isAlive());
        Thread.sleep(20);
        // Still within time. Mark another request, server is still alive
        server.hadRequest();
        assertEquals(true, server.isAlive());
        
        Thread.sleep(15); 
        // Timeout for first request has expired (but not for second request)
        // server should not be alive anymore
        assertEquals(false, server.isAlive());
        server.hadResponse();
        assertEquals(true, server.isAlive());
        
        Thread.sleep(35);
        // no new requests after response. Server is still alive
        assertEquals(true, server.isAlive());
        
        server.hadRequest();
        assertEquals(true, server.isAlive());
    }

}
