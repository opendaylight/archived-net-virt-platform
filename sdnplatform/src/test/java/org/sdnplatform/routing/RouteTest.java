/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University 
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

package org.sdnplatform.routing;

import org.junit.Test;
import org.sdnplatform.routing.Route;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.topology.NodePortTuple;


/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class RouteTest extends PlatformTestCase {
    @Test
    public void testCloneable() throws Exception {
        Route r1 = new Route(1L, 2L);
        Route r2 = new Route(1L, 3L);

        assertNotSame(r1, r2);
        assertNotSame(r1.getId(), r2.getId());

        r1 = new Route(1L, 3L);
        r1.getPath().add(new NodePortTuple(1L, (short)1));
        r1.getPath().add(new NodePortTuple(2L, (short)1));
        r1.getPath().add(new NodePortTuple(2L, (short)2));
        r1.getPath().add(new NodePortTuple(3L, (short)1));

        r2.getPath().add(new NodePortTuple(1L, (short)1));
        r2.getPath().add(new NodePortTuple(2L, (short)1));
        r2.getPath().add(new NodePortTuple(2L, (short)2));
        r2.getPath().add(new NodePortTuple(3L, (short)1));

        assertEquals(r1, r2);

        NodePortTuple temp = r2.getPath().remove(0);
        assertNotSame(r1, r2);

        r2.getPath().add(0, temp);
        assertEquals(r1, r2);

        r2.getPath().remove(1);
        temp = new NodePortTuple(2L, (short)5);
        r2.getPath().add(1, temp);
        assertNotSame(r1, r2);
    }
}
