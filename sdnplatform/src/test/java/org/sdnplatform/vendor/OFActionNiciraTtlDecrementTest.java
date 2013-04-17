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
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionVendor;
import org.sdnplatform.vendor.OFActionNiciraTtlDecrement;
import org.sdnplatform.vendor.OFActionNiciraVendor;

import static org.junit.Assert.*;

public class OFActionNiciraTtlDecrementTest {
    protected static byte[] expectedWireFormat = { 
                (byte) 0xff, (byte) 0xff,          // action vendor
                0x00, 0x10,                        // length 
                0x00, 0x00, 0x23, 0x20,            // nicira
                0x00, 0x12,                        // subtype 18 == 0x12
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // pad 
    };
    
    @Test
    public void testAction() {
        ChannelBuffer buf = ChannelBuffers.buffer(32);
        
        OFActionNiciraTtlDecrement act = new OFActionNiciraTtlDecrement();
        
        assertEquals(true, act instanceof OFActionNiciraVendor);
        assertEquals(true, act instanceof OFActionVendor);
        assertEquals(true, act instanceof OFAction);
        
        act.writeTo(buf);
        
        ChannelBuffer buf2 = buf.copy();
        
        assertEquals(16, buf.readableBytes());
        byte fromBuffer[] = new byte[16]; 
        buf.readBytes(fromBuffer);
        assertArrayEquals(expectedWireFormat, fromBuffer);
        
        // Test parsing. TODO: we don't really have the proper parsing
        // infrastructure....
        OFActionNiciraVendor act2 = new OFActionNiciraTtlDecrement();
        act2.readFrom(buf2);
        assertEquals(act, act2);
        assertNotSame(act, act2);
        
        assertEquals(OFActionType.VENDOR, act2.getType());
        assertEquals(16, act2.getLength());
        assertEquals(OFActionNiciraVendor.NICIRA_VENDOR_ID, act2.getVendor());
        assertEquals((short)18, act2.getSubtype());
    }

}
