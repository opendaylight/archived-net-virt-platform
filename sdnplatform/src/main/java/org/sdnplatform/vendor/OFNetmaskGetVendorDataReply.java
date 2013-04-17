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

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;


/**
 * Subclass of OFVendorData
 * 
 * @author munish_mehta
 */
public class OFNetmaskGetVendorDataReply extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFNetmaskGetVendorDataReply();
        }
    };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFNetmaskGetVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * Opcode/dataType to represent REPLY of GET_MASK request
     */
    public static final int BSN_GET_IP_MASK_ENTRY_REPLY = 2;

    /**
     * Construct a get network mask vendor data
     */
    public OFNetmaskGetVendorDataReply() {
        super(BSN_GET_IP_MASK_ENTRY_REPLY);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFNetmaskGetVendorDataReply(byte tableIndex, int netMask) {
        super(BSN_GET_IP_MASK_ENTRY_REPLY, tableIndex, netMask);
    }
}
