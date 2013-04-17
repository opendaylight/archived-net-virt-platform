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

import java.util.ArrayList;
import java.util.HashMap;


import org.openflow.util.HexString;
import org.sdnplatform.routing.Link;

public class BroadcastTreeMultipath {
    protected HashMap<Long, ArrayList<Link>> links;
    protected HashMap<Long, Integer> costs;

    public BroadcastTreeMultipath() {
        links = new HashMap<Long, ArrayList<Link>>();
        costs = new HashMap<Long, Integer>();
    }

    public BroadcastTreeMultipath(HashMap<Long, ArrayList<Link>> links, HashMap<Long, Integer> costs) {
        this.links = links;
        this.costs = costs;
    }

    // deprecate?
    // legacy method - change to getTreeLinks or now return first element
    public Link getTreeLink(long node) {
        return links.get(node).get(0);
    }

    public int getCost(long node) {
        if (costs.get(node) == null)
            return -1;
        return (costs.get(node));
    }

    public HashMap<Long, ArrayList<Link>> getLinks() {
        return links;
    }

    public ArrayList<Link> getLinks(long node) {
        return links.get(node);
    }
    
    // deprecate?
    // mynode is src of link in dst rooted tree convention
    public void addTreeLink(long myNode, Link link) {
        links.get(myNode).add(link);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (long n : links.keySet()) {
            sb.append("[" + HexString.toHexString(n) + ": cost=" + costs.get(n)
                    + ", " + links.get(n) + "]");
        }
        return sb.toString();
    }

    public HashMap<Long, Integer> getCosts() {
        return costs;
    }
}
