/*
 * Copyright (c) 2012,2013 Big Switch Networks, Inc.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *    Originally created by David Erickson, Stanford University 
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the
 *    License. You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an "AS
 *    IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language
 *    governing permissions and limitations under the License. 
 */

package org.sdnplatform.devicemanager.web;

import java.io.IOException;


import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.openflow.util.HexString;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.internal.Device;
import org.sdnplatform.packet.IPv4;

/**
 * Serialize a device object
 */
public class DeviceSerializer extends JsonSerializer<Device> {

    @Override
    public void serialize(Device device, JsonGenerator jGen,
                          SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("entityClass", device.getEntityClass().getName());
        
        jGen.writeArrayFieldStart("mac");
        jGen.writeString(HexString.toHexString(device.getMACAddress(), 6));
        jGen.writeEndArray();

        jGen.writeArrayFieldStart("ipv4");
        for (Integer ip : device.getIPv4Addresses())
            jGen.writeString(IPv4.fromIPv4Address(ip));
        jGen.writeEndArray();

        jGen.writeArrayFieldStart("vlan");
        for (Short vlan : device.getVlanId())
            if (vlan >= 0)
                jGen.writeNumber(vlan);
        jGen.writeEndArray();
        jGen.writeArrayFieldStart("attachmentPoint");
        for (SwitchPort ap : device.getAttachmentPoints(true)) {
            serializer.defaultSerializeValue(ap, jGen);
        }
        jGen.writeEndArray();

        jGen.writeNumberField("lastSeen", device.getLastSeen().getTime());
        
        String dhcpClientName = device.getDHCPClientName();
        if (dhcpClientName != null) {
            jGen.writeStringField("dhcpClientName", dhcpClientName);
        }

        jGen.writeEndObject();
    }

}
