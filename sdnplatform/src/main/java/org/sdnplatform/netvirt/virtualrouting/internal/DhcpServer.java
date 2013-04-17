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

/**
 * This class stores the metadata for DHCP servers.
 * Mainly this class keeps track on whether the server represented by it
 * is still alive. To do so we keep track of the time of the oldest outstanding
 * request. Once we receive a reply we clear the time for the request. We
 * don't track individual requests since we really only care for liveness.
 * 
 * TODO: this implementation is fully synchronized. If this becomes a problem 
 * with lock contention we'll need to find better concurrency mechanismn 
 * 
 * TODO: we currently only track time. We could have just as easily have
 * used a TimeoutCache in DhcpManager instead of having a map of DhcpServers.
 * However, we'll eventually want to associate more state with a server
 *
 */
public class DhcpServer {
    private Long oldestPendingRequestTime;
    private long timeout;
    
    public DhcpServer(long timeout) {
        this.oldestPendingRequestTime = null;
        this.timeout = timeout * 1000 * 1000;
    }
    
    public synchronized void hadRequest() {
        if (oldestPendingRequestTime == null)
            oldestPendingRequestTime = System.nanoTime();
    }
    
    public synchronized void hadResponse() {
        oldestPendingRequestTime = null;
    }
    
    public synchronized boolean isAlive() {
        long curTime = System.nanoTime();
        if (oldestPendingRequestTime == null)
            return true;
        if (curTime - oldestPendingRequestTime > timeout)
            return false;
        return true;
    }
}
