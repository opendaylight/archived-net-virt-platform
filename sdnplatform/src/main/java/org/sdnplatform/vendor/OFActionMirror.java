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


public class OFActionMirror extends OFActionBigSwitchVendor {
    public final static int MINIMUM_LENGTH = 12;
    public final static int BSN_ACTION_MIRROR = 1;

    protected int destPort;
    protected int vlanTag;
    protected byte copyStage;
    protected byte pad0;
    protected byte pad1;
    protected byte pad2;

    public OFActionMirror(short portNumber) {
        super(BSN_ACTION_MIRROR);
        super.setLength((short) (OFActionBigSwitchVendor.MINIMUM_LENGTH + OFActionMirror.MINIMUM_LENGTH));
        this.destPort = portNumber;
        this.vlanTag = 0;
        this.copyStage = 0;
    }

    public int getDestPort() {
        return destPort;
    }

    public void setDestPort(int destPort) {
        this.destPort = destPort;
    }

    public int getVlanTag() {
        return vlanTag;
    }

    public void setVlanTag(int vlanTag) {
        this.vlanTag = vlanTag;
    }

    public byte getCopyStage() {
        return copyStage;
    }

    public void setCopyStage(byte copyStage) {
        this.copyStage = copyStage;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + copyStage;
        result = prime * result + destPort;
        result = prime * result + pad0;
        result = prime * result + pad1;
        result = prime * result + pad2;
        result = prime * result + vlanTag;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFActionMirror other = (OFActionMirror) obj;
        if (copyStage != other.copyStage) return false;
        if (destPort != other.destPort) return false;
        if (pad0 != other.pad0) return false;
        if (pad1 != other.pad1) return false;
        if (pad2 != other.pad2) return false;
        if (vlanTag != other.vlanTag) return false;
        return true;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.destPort = data.readInt();
        this.vlanTag = data.readInt();
        this.copyStage = data.readByte();
        this.pad0 = data.readByte();
        this.pad1 = data.readByte();
        this.pad2 = data.readByte();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.destPort);
        data.writeInt(this.vlanTag);
        data.writeByte(this.copyStage);
        data.writeByte(this.pad0);
        data.writeByte(this.pad1);
        data.writeByte(this.pad2);
    }
}
