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

import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;

public class OFBigSwitchVendorExtensions {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized)
            return;
    
        OFBasicVendorId bsnVendorId = 
                new OFBasicVendorId(OFBigSwitchVendorData.BSN_VENDOR_ID, 4);
        OFVendorId.registerVendorId(bsnVendorId);

        // register data types used for big tap
        OFBasicVendorDataType setEntryVendorData =
                new OFBasicVendorDataType(
                         OFNetmaskSetVendorData.BSN_SET_IP_MASK_ENTRY,
                         OFNetmaskSetVendorData.getInstantiable());
        bsnVendorId.registerVendorDataType(setEntryVendorData);

        OFBasicVendorDataType getEntryVendorDataRequest =
                new OFBasicVendorDataType(
                         OFNetmaskGetVendorDataRequest.BSN_GET_IP_MASK_ENTRY_REQUEST,
                         OFNetmaskGetVendorDataRequest.getInstantiable());
        bsnVendorId.registerVendorDataType(getEntryVendorDataRequest);

        OFBasicVendorDataType getEntryVendorDataReply =
                new OFBasicVendorDataType(
                         OFNetmaskGetVendorDataReply.BSN_GET_IP_MASK_ENTRY_REPLY,
                         OFNetmaskGetVendorDataReply.getInstantiable());
        bsnVendorId.registerVendorDataType(getEntryVendorDataReply);

        // register data types used for tunneling
        OFBasicVendorDataType getIntfIPVendorDataRequest = 
                new OFBasicVendorDataType(
                          OFInterfaceIPRequestVendorData.BSN_GET_INTERFACE_IP_REQUEST,
                          OFInterfaceIPRequestVendorData.getInstantiable());
        bsnVendorId.registerVendorDataType(getIntfIPVendorDataRequest);

        OFBasicVendorDataType getIntfIPVendorDataReply = 
                new OFBasicVendorDataType(
                          OFInterfaceIPReplyVendorData.BSN_GET_INTERFACE_IP_REPLY,
                          OFInterfaceIPReplyVendorData.getInstantiable());
        bsnVendorId.registerVendorDataType(getIntfIPVendorDataReply);

        
    }
}
