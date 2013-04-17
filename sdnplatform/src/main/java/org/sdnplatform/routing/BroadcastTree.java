/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *    Originally created by David Erickson, Stanford University 
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the
 *    License. You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an "AS
 *    IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language
 *    governing permissions and limitations under the License. 
 */

package org.sdnplatform.routing;
import java.util.HashMap;


import org.openflow.util.HexString;
import org.sdnplatform.routing.Link;

public class BroadcastTree {
    protected HashMap<Long, Link> links;
    protected HashMap<Long, Integer> costs;

    public BroadcastTree() {
        links = new HashMap<Long, Link>();
        costs = new HashMap<Long, Integer>();
    }

    public BroadcastTree(HashMap<Long, Link> links, HashMap<Long, Integer> costs) {
        this.links = links;
        this.costs = costs;
    }

    public Link getTreeLink(long node) {
        return links.get(node);
    }

    public int getCost(long node) {
        if (costs.get(node) == null) return -1;
        return (costs.get(node));
    }

    public HashMap<Long, Link> getLinks() {
        return links;
    }

    public void addTreeLink(long myNode, Link link) {
        links.put(myNode, link);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for(long n: links.keySet()) {
            sb.append("[" + HexString.toHexString(n) + ": cost=" + costs.get(n) + ", " + links.get(n) + "]");
        }
        return sb.toString();
    }

    public HashMap<Long, Integer> getCosts() {
        return costs;
    }
}
