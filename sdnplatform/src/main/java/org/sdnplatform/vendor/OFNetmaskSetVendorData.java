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

public class OFNetmaskSetVendorData extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFNetmaskSetVendorData();
        }
    };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFNetmaskSetVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * Opcode/dataType to set an entry in the switch netmask table
     */
    public static final int BSN_SET_IP_MASK_ENTRY = 0;
    
    /**
     * Construct a get network mask vendor data
     */
    public OFNetmaskSetVendorData() {
        super(BSN_SET_IP_MASK_ENTRY);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFNetmaskSetVendorData(byte tableIndex, int netMask) {
        super(BSN_SET_IP_MASK_ENTRY, tableIndex, netMask);
    }
}
