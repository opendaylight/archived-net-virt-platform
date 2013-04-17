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

package org.sdnplatform.ovsdb;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.sdnplatform.ovsdb.internal.JSONDecoder;
import org.sdnplatform.ovsdb.internal.JSONEncoder;


public class OVSDBServerPipelineFactory implements ChannelPipelineFactory {
    OVSDBImplTest ot;
    
    public OVSDBServerPipelineFactory(OVSDBImplTest ot) {
        this.ot = ot;
    }
    
    @Override
    public ChannelPipeline getPipeline() throws Exception {

        JSONDecoder jsonRpcDecoder = new JSONDecoder();
        JSONEncoder jsonRpcEncoder = new JSONEncoder();
        HashedWheelTimer timer = new HashedWheelTimer();
        IdleStateHandler idleHandler = new IdleStateHandler(timer, 0, 0, 2);

        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("jsondecoder", jsonRpcDecoder);
        pipeline.addLast("jsonencoder", jsonRpcEncoder);
        pipeline.addLast("idle", idleHandler);
        pipeline.addLast("jsonServerHandler", new OVSDBServerHandler(ot));
        return pipeline;
    }


}
