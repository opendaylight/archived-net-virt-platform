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
import org.openflow.protocol.factory.OFVendorActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OFNiciraVendorActionFactory implements OFVendorActionFactory {
    protected static Logger logger =
            LoggerFactory.getLogger(OFNiciraVendorActionFactory.class);

    static class OFActionNiciraVendorDemux extends OFActionNiciraVendor {
        OFActionNiciraVendorDemux() {
            super((short) 0);
        }
    }

    @Override
    public OFActionVendor readFrom(ChannelBuffer data) {
        data.markReaderIndex();
        OFActionNiciraVendorDemux demux = new OFActionNiciraVendorDemux();
        demux.readFrom(data);
        data.resetReaderIndex();

        switch(demux.getSubtype()) {
            case OFActionNiciraTtlDecrement.TTL_DECREMENT_SUBTYPE:
                OFActionNiciraTtlDecrement ttlAction = new OFActionNiciraTtlDecrement();
                ttlAction.readFrom(data);
                return ttlAction;
            default:
                logger.error("Unknown Nicira vendor action subtype: "+demux.getSubtype());
                return null;
        }
    }

}
