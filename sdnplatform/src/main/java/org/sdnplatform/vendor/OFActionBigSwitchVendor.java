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
import org.openflow.protocol.action.OFActionVendor;

public abstract class OFActionBigSwitchVendor extends OFActionVendor {
    public static int MINIMUM_LENGTH = 12;
    public static int BSN_VENDOR_ID = OFBigSwitchVendorData.BSN_VENDOR_ID;

    protected int subtype;

    protected OFActionBigSwitchVendor(int subtype) {
        super();
        super.setLength((short)MINIMUM_LENGTH);
        super.setVendor(BSN_VENDOR_ID);
        this.subtype = subtype;
    }

    public int getSubtype() {
        return this.subtype;
    }

    public void setSubtype(int subtype) {
        this.subtype = subtype;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.subtype = data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.subtype);
    }

    @Override
    public int hashCode() {
        final int prime = 379;
        int result = super.hashCode();
        result = prime * result + vendor;
        result = prime * result + subtype;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof OFActionBigSwitchVendor)) {
            return false;
        }
        OFActionBigSwitchVendor other = (OFActionBigSwitchVendor) obj;
        if (subtype != other.subtype) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + "; subtype=" + subtype;
    }
}
