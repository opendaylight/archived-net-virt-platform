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

package org.sdnplatform.packetstreamer;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;


import org.sdnplatform.core.annotations.LogMessageCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Generated code
import org.sdnplatform.packetstreamer.thrift.*;

/**
 * The PacketStreamer Server that brokers the packet streaming service.
 */
@LogMessageCategory("OpenFlow Message Tracing")
public class PacketStreamerServer {
    protected static Logger log = LoggerFactory.getLogger(PacketStreamerServer.class);
    protected static int port = 9090;
    protected static PacketStreamerHandler handler;
    protected static PacketStreamer.Processor<PacketStreamerHandler> processor;

    
    /** 
     * Main function entry point;
     * @param args
     */
    public static void main(String [] args) {
        try {
            port = Integer.parseInt(System.getProperty("org.sdnplatform.packetstreamer.port", "9090"));
            
            handler = new PacketStreamerHandler();
            processor = new PacketStreamer.Processor<PacketStreamerHandler>(handler);

            Runnable simple = new Runnable() {
                public void run() {
                    hshaServer(processor);
                }
            };

            new Thread(simple).start();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    
    /** 
     * The function to create a thrift Half-Sync and Half-Async Server.
     * @param processor
     */
    public static void hshaServer(PacketStreamer.Processor<PacketStreamerHandler> processor) {
        try {
            TNonblockingServerTransport serverTransport = new TNonblockingServerSocket(port);
            THsHaServer.Args args = new THsHaServer.Args(serverTransport);
            args.processor(processor);
            args.transportFactory(new TFramedTransport.Factory());
            args.protocolFactory(new TBinaryProtocol.Factory(true, true));
            TServer server = new THsHaServer(args);
    
            log.info("Starting the packetstreamer hsha server on port {} ...", port);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
