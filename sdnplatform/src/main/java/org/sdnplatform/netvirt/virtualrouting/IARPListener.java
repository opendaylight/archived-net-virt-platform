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

package org.sdnplatform.netvirt.virtualrouting;

import org.openflow.protocol.OFPacketIn;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.netvirt.core.VNS.ARPMode;



/**
 * An interface for modules that are interested in receiving ARP packets.
 * @author alexreimers
 */
public interface IARPListener {
    public enum ARPCommand {
        CONTINUE,  // Continue processing
        STOP,  // Stop processing
        SKIP // Skip other listeners and continue processing
    }
    
    /**
     * Called for each listener when an ARP request is received.
     * @param sw The switch the ARP was received on.
     * @param pi The PacketIn which contains the Ethernet frame and ARP request.
     * @param cntx A SDN Platform message context object you can use to pass 
     * information between listeners
     * @param configMode The NetVirt for the host sending the ARP request, can be null.
     * @return Whether to continue processing.
     */
    public ARPCommand ARPRequestHandler(IOFSwitch sw, OFPacketIn pi, 
                                        ListenerContext cntx, ARPMode configMode);
    
    /**
     * Called for each listener when an ARP reply is received.
     * @param sw The switch the ARP was received on.
     * @param pi The PacketIn which contains the Ethernet frame and ARP reply.
     * @param cntx A SDN Platform message context object you can use to pass 
     * information between listeners
     * @param configMode The NetVirt for the host sending the ARP reply, can be null.
     * @return Whether to continue processing.
     */
    public ARPCommand ARPReplyHandler(IOFSwitch sw, OFPacketIn arp, 
                                      ListenerContext cntx, ARPMode configMode);
    
    /**
     * Called for each listener when an RARP request is received.
     * @param sw The switch the ARP was received on.
     * @param pi The PacketIn which contains the Ethernet frame and RARP request.
     * @param cntx A SDN Platform message context object you can use to pass 
     * information between listeners
     * @param configMode The NetVirt for the host sending the RARP reply, can be null.
     * @return Whether to continue processing.
     */
    public ARPCommand RARPRequestHandler(IOFSwitch sw, OFPacketIn pi, 
                                         ListenerContext cntx, ARPMode configMode);
    
    /**
     * Called for each listener when an RARP reply is received.
     * @param sw The switch the ARP was received on.
     * @param pi The PacketIn which contains the Ethernet frame and RARP reply.
     * @param cntx A SDN Platform message context object you can use to pass 
     * information between listeners
     * @param configMode The NetVirt for the host sending the RARP reply, can be null.
     * @return Whether to continue processing.
     */
    public ARPCommand RARPReplyHandler(IOFSwitch sw, OFPacketIn pi, 
                                         ListenerContext cntx, ARPMode configMode);
    
    /**
     * Get the name of the module listening for ARP packets.
     * @return The name.
     */
    public String getName();
}
