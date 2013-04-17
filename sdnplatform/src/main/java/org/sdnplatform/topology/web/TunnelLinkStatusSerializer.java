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

package org.sdnplatform.topology.web;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.sdnplatform.topology.TunnelEvent.TunnelLinkStatus;


public class TunnelLinkStatusSerializer extends JsonSerializer<TunnelLinkStatus>{

    @Override
    public void serialize(TunnelLinkStatus status, JsonGenerator jGen,
            SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        String resultString;
        if (status == TunnelLinkStatus.UP)
            resultString = "up";
        else if (status == TunnelLinkStatus.DOWN)
            resultString = "down";
        else if (status == TunnelLinkStatus.NOT_ENABLED)
            resultString = "disabled";
        else if (status == TunnelLinkStatus.NOT_ACTIVE)
            resultString = "not active";
        else resultString = "unknown";

        jGen.writeString(resultString);
    }
}
