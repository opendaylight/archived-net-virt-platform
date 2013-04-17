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

package org.openflow.protocol.action;

import org.jboss.netty.buffer.ChannelBuffer;


public class MockVendorAction extends OFActionVendor {
    public static final int VENDOR_ID = 0xdeadbeef;

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private byte[] mockData;

    public byte[] getMockData() {
        return mockData;
    }

    public void setMockData(byte[] mockData) {
        this.mockData = mockData;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);

        int dataLength = getLength() - MINIMUM_LENGTH;
        if(dataLength > 0) {
            mockData = new byte[dataLength];
            data.readBytes(mockData);
        } else {
            mockData = EMPTY_BYTE_ARRAY;
        }

    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeBytes(mockData);
    }


}
