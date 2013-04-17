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

package org.sdnplatform.topology;

import java.util.HashSet;
import java.util.Set;


/**
 * 
 * @author Srinivasan Ramasubramanian, Big Switch Networks
 *
 */
public class BroadcastDomain {
    private long id;
    private Set<NodePortTuple> ports;

    public BroadcastDomain() {
        id = 0;
        ports = new HashSet<NodePortTuple>();
    }

    @Override 
    public int hashCode() {
        return (int)(id ^ id>>>32);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        BroadcastDomain other = (BroadcastDomain) obj;

        return this.ports.equals(other.ports);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Set<NodePortTuple> getPorts() {
        return ports;
    }

    public void add(NodePortTuple npt) {
        ports.add(npt);
    }

    @Override
    public String toString() {
        return "BroadcastDomain [id=" + id + ", ports=" + ports + "]";
    }
}
