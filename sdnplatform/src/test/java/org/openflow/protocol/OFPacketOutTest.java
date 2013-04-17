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

package org.openflow.protocol;

import org.junit.Test;

public class OFPacketOutTest {

    @Test(expected = IllegalArgumentException.class)
    public void testBothBufferIdAndPayloadSet() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(12);
        packetOut.setPacketData(new byte[] { 1, 2, 3 });
    }

    @Test
    public void testOnlyBufferIdSet() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(12);
        packetOut.setPacketData(null);
        packetOut.setPacketData(new byte[] {});
        packetOut.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeitherBufferIdNorPayloadSet() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        packetOut.setPacketData(null);
        packetOut.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeitherBufferIdNorPayloadSet2() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        packetOut.setPacketData(new byte[] {});
        packetOut.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeitherBufferIdNorPayloadSet3() {
        OFPacketOut packetOut = new OFPacketOut();
        packetOut.validate();
    }

}
