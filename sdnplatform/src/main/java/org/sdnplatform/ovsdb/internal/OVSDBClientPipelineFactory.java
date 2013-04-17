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


import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.ssl.SslHandler;
import org.sdnplatform.ovsdb.IOVSDB;



/**
 * Netty client pipeline factory for JSON RPC messaging 
 * 
 * @author Gregor
 *
 */
public class OVSDBClientPipelineFactory implements ChannelPipelineFactory {
    private IOVSDB currtsw;
    private Object statusObject;
    private boolean useSSL;
    
    public void setCurSwitch(IOVSDB tsw) {
        currtsw = tsw; 
    }
    
    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }
    
    @Override
    public ChannelPipeline getPipeline() throws Exception {
        JSONDecoder jsonRpcDecoder = new JSONDecoder();
        JSONEncoder jsonRpcEncoder = new JSONEncoder();
           
        ChannelPipeline pipeline = Channels.pipeline();
        
        if (useSSL) {
            // Add SSL handler first to encrypt and decrypt everything.
            SSLEngine engine =
                BSNSslContextFactory.getClientContext().createSSLEngine();
            engine.setUseClientMode(true);
            // OVSDB supports *only* TLSv1
            engine.setEnabledProtocols(new String[] { "TLSv1" } );
            pipeline.addLast("ssl", new SslHandler(engine));
        }
        pipeline.addLast("jsondecoder", jsonRpcDecoder);
        pipeline.addLast("jsonencoder", jsonRpcEncoder);
        pipeline.addLast("jsonhandler", 
                new JSONMsgHandler(currtsw, statusObject));
        return pipeline;
    }

    public void setStatusObject(Object statusObject) {
        this.statusObject = statusObject;
    }

}
