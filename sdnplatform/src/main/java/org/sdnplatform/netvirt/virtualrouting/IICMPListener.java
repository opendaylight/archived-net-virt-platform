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

/**
 * An interface for modules that are interested in receiving ICMP packets.
 * @author wilmo119
 *
 */

public interface IICMPListener {
    public enum ICMPCommand {
        CONTINUE, // Continue processing
        STOP, // Stop processing
    }

    /**
     * Called for each listener when an ICMP request is received.
     * @param sw The switch the ICMP request was received on.
     * @param pi The PacketIn containing the ICMP request.
     * @param cntx A SDN Platform message context object for passing information
     * between listeners.
     * @return Whether to continue processing.
     */
    public ICMPCommand ICMPRequestHandler(IOFSwitch sw, OFPacketIn pi,
                                          ListenerContext cntx);

    /**
     * Called for each listener when an ICMP reply is received.
     * @param sw The switch the ICMP reply was received on.
     * @param pi The PacketIn containing the ICMP rely.
     * @param cntx A SDN Platform message context object for passing information
     * between listeners.
     * @return Whether to continue processing.
     */
    public ICMPCommand ICMPReplyHandler(IOFSwitch sw, OFPacketIn pi,
                                        ListenerContext cntx);

    /**
     * Get the name of the module listening for ICMP packets.
     * @return The name.
     */
    public String getName();
}