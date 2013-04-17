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

package org.sdnplatform.netvirt.virtualrouting.internal;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;


import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.netvirt.virtualrouting.IICMPListener;
import org.sdnplatform.netvirt.virtualrouting.IICMPListener.ICMPCommand;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.RoutingDecision;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Manages ICMP requests.
 * @author wilmo119
 *
 */

@LogMessageCategory("Network Virtualization")
public class IcmpManager implements IOFMessageListener {
    protected static Logger logger = LoggerFactory.getLogger(IcmpManager.class);

    protected Set<IICMPListener> icmpListeners;

    public IcmpManager() {
        icmpListeners = new CopyOnWriteArraySet<IICMPListener>();
    }

    public void addIcmpListener(IICMPListener il) {
        icmpListeners.add(il);
        if (logger.isDebugEnabled()) {
            logger.debug("Added icmp listener: {}", il.getName());
        }
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           ListenerContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
            default:
                return Command.CONTINUE;
        }
    }

    private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                           ListenerContext cntx) {
        Ethernet eth =
                IControllerService.bcStore.get(cntx,
                        IControllerService.CONTEXT_PI_PAYLOAD);
        if (eth.getEtherType() != Ethernet.TYPE_IPv4)
            return Command.CONTINUE;

        IPv4 ipv4 = (IPv4) eth.getPayload();
        if (!(ipv4.getPayload() instanceof ICMP))
            return Command.CONTINUE;

        ICMP icmp = (ICMP) ipv4.getPayload();
        if (logger.isTraceEnabled()) {
            long srcMac = Ethernet.toLong(eth.getSourceMACAddress());
            long dstMac = Ethernet.toLong(eth.getDestinationMACAddress());
            int srcIp = ipv4.getSourceAddress();
            int dstIp = ipv4.getDestinationAddress();
            logger.trace("Got ICMP: {}::{} -> {}::{}",
                         new Object[] { HexString.toHexString(srcMac),
                                        IPv4.fromIPv4Address(srcIp),
                                        HexString.toHexString(dstMac),
                                        IPv4.fromIPv4Address(dstIp) });
            logger.trace("ICMP type: {} code: {}", icmp.getIcmpType(),
                                                   icmp.getIcmpCode());
        }

        for (IICMPListener il : icmpListeners) {
            ICMPCommand ret = null;

            if (icmp.getIcmpType() == ICMP.ECHO_REQUEST) {
                ret = il.ICMPRequestHandler(sw, pi, cntx);
            } else if (icmp.getIcmpType() == ICMP.ECHO_REPLY) {
                ret = il.ICMPReplyHandler(sw, pi, cntx);
            }

            if (ret == null || ret == ICMPCommand.CONTINUE) {
                continue;
            } else if (ret == ICMPCommand.STOP) {
                IDevice srcDevice = IDeviceService.fcStore.get(cntx,
                        IDeviceService.CONTEXT_SRC_DEVICE);
                RoutingAction ra = RoutingAction.NONE;
                RoutingDecision vrd = new RoutingDecision(sw.getId(),
                                                          pi.getInPort(),
                                                          srcDevice, ra);
                vrd.addToContext(cntx);
                return Command.STOP;
            }
        }
        return Command.CONTINUE;
    }

    @Override
    public String getName() {
        return "icmpmanager";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }
}
