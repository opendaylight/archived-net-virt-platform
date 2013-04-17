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
import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

public class OFInterfaceIPRequestVendorData extends OFBigSwitchVendorData {
 
    protected static Instantiable<OFVendorData> instantiable =
            new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new OFInterfaceIPRequestVendorData();
                }
    };
    
    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFInterfaceIPRequestVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * Opcode/dataType to request IP addresses of all interfaces
     */
    public static final int BSN_GET_INTERFACE_IP_REQUEST = 9;

    /**
     * Construct an interface IP request vendor data 
     */
    public OFInterfaceIPRequestVendorData() {
        super(BSN_GET_INTERFACE_IP_REQUEST);   
    }
    
    /**
     * @return the total length of the interface IP request message
     *         the length is already accounted for in the super class 
     */
    @Override
    public int getLength() {
        return super.getLength();
    }
    
    /**
     * Read from the ChannelBuffer
     * @param data the channel buffer from which we're deserializing
     * @param length the length to the end of the enclosing message
     */
    @Override
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
    }
    
    /**
     * Write to the ChannelBuffer
     * @param data the channel buffer to which we're serializing
     */
    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
    }
    
}
