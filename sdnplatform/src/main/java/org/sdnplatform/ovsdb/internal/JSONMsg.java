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

package org.sdnplatform.ovsdb.internal;

import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all JSON RPC messages sent by Controller
 * @author Saurav Das
 *
 */
public class JSONMsg {
    protected static Logger log = LoggerFactory.getLogger(JSONMsg.class);
    
    protected int length = 0;
    String jsonstr = " ";
    
    public int getLengthU() {
        return length;
    }

    public void writeTo(ChannelBuffer buf) {
        buf.writeBytes(jsonstr.getBytes());
    }
    
}
