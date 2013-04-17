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

package org.sdnplatform.vendor;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;
import org.openflow.protocol.action.OFActionType;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.vendor.OFActionTunnelDstIP;

import static org.junit.Assert.*;

public class OFActionTunnelDstIPTest extends PlatformTestCase{
    protected static byte[] expectedWireFormat1 = { 
            (byte) 0xff, (byte) 0xff,       // ActionVendor
            0x00, 0x10,                     // 16 bytes
            0x00, 0x5c, 0x16, (byte)0xc7,   // VendorId BSN
            0x00, 0x00, 0x00, 0x02,         // subtype 2 (32 bit)
            0x11, 0x21, 0x31, 0x41          // IP 17.33.49.65
    };

    @Test
    public void testAction() {
        OFActionTunnelDstIP tunnAct1 = new OFActionTunnelDstIP();
        assertEquals(0, tunnAct1.dstIPAddr);

        OFActionTunnelDstIP tunnAct2 = new OFActionTunnelDstIP(1);
        
        assertEquals(false, tunnAct1.equals(tunnAct2));
        tunnAct1.setTunnelDstIP(1);
        assertEquals(tunnAct1, tunnAct2);
        
        testAll(tunnAct1);
        testAll(tunnAct2);
    }
    
    private void testAll(OFActionTunnelDstIP tip) {
        assertEquals(OFActionType.VENDOR, tip.getType());
        assertEquals(2, tip.getSubtype());
        assertEquals(16, tip.getLength());
        assertEquals(0x005c16c7, tip.getVendor());

        tip.setTunnelDstIP(24);
        assertEquals(24, tip.getTunnelDstIP());
        
        // Test wire format
        int ip = IPv4.toIPv4Address("17.33.49.65");
        tip.setTunnelDstIP(ip);
        ChannelBuffer buf = ChannelBuffers.buffer(32);
        tip.writeTo(buf);
        ChannelBuffer buf2 = buf.copy();
        assertEquals(16, buf.readableBytes());
        byte fromBuffer[] = new byte[16]; 
        buf.readBytes(fromBuffer);
        assertArrayEquals(expectedWireFormat1, fromBuffer);
        
        OFActionTunnelDstIP act2 = new OFActionTunnelDstIP();
        act2.readFrom(buf2);
        assertEquals(tip, act2);
        
        
    }
    
    
}
