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

import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple "cache" to track timeouts. When an object is added to the cache
 * we record the current time. We will not re-add an object already in the
 * cache. We can check if an element has been in the cache for longer than
 * a given timeout.
 *
 * This implementation is thread-safe but does not guarantee ordering. E.g.,
 * if two inserts happen at the same time one of them will succeed but we do
 * not guarantee which one.
 *
 * @author gregor
 *
 */
public class TimeoutCache<V> {
    private final ConcurrentHashMap<V, Long> cache;
    private final long timeout; // store in ns

    public TimeoutCache(long timeoutMilliseconds) {
        super();
        cache = new ConcurrentHashMap<V, Long>();
        this.timeout = timeoutMilliseconds * 1000*1000;
    }

    /**
     * Add val to the cache at the current time if it's not already in
     * the cache.
     * @param val the value to add
     * @return true if the entry already existed (and thus no new value was
     * added)
     */
    public boolean putIfAbsent(V val) {
        Long rv = cache.putIfAbsent(val, System.nanoTime());
        return (rv != null);
    }

    /**
     * Remove val from the cache.
     * @param val
     * @return true if the entry existed in the cache prior to removal
     */
    public boolean remove(V val) {
        Long rv = cache.remove(val);
        return (rv != null);
    }

    /**
     * Check if the val is in the cache and if so whether its timeout is
     * expired (i.e., it has been in the cache for more than timeout time)
     * @param val
     * @return ture iff the value is in the cache and its timeout has expired
     */
    public boolean isTimeoutExpired(V val) {
        Long lastTime = cache.get(val);
        if (lastTime == null)
            return false;
        Long curTime = System.nanoTime();
        if (curTime - lastTime > timeout)
            return true;
        return false;
    }
}
