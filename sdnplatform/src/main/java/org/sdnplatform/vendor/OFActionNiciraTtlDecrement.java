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

public class OFActionNiciraTtlDecrement extends OFActionNiciraVendor {
    public static int MINIMUM_LENGTH_TTL_DECREMENT = 16;
    public static final short TTL_DECREMENT_SUBTYPE = 18;
    
    
    public OFActionNiciraTtlDecrement() {
        super(TTL_DECREMENT_SUBTYPE);
        super.setLength((short)MINIMUM_LENGTH_TTL_DECREMENT);
    }
    
    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        data.skipBytes(6);  // pad
    }
    
    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeZero(6);
    }

    
}
