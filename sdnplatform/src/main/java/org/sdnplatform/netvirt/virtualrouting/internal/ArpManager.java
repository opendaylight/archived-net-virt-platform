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

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceListener;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.ARPMode;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.IARPListener;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.virtualrouting.IARPListener.ARPCommand;
import org.sdnplatform.packet.ARP;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPacket;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.RoutingDecision;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.topology.ITopologyService;
import org.sdnplatform.topology.NodePortTuple;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Manage ARP requests on the network to reduce broadcast traffic by
 * converting broadcast ARP requests into unicast ARP requests.
 * @author readams
 *
 */
@LogMessageCategory("Network Virtualization")
public class ArpManager implements IOFMessageListener {
    protected static  Logger logger = LoggerFactory.getLogger(ArpManager.class);

    public static final short ARP_FLOWMOD_HARD_TIMEOUT = 5;

    protected IControllerService controllerProvider;
    protected IDeviceService deviceManager;
    protected ITopologyService topology;
    protected IVirtualRoutingService virtualRouting;
    protected ITunnelManagerService tunnelManager;

    protected BasicFactory factory;
    protected Set<IARPListener> arpListeners;

    protected int ARP_CACHE_DEFAULT_TIMEOUT_MS = 2000;
    protected int INTER_NetVirt_BROADCAST_SUPPRESSION_TIMEOUT_MS = 30000;

    protected Map<Long, Long> unicastARPRequestTime;

    protected DeviceListenerImpl deviceListener;

    public ArpManager() {
        arpListeners = new CopyOnWriteArraySet<IARPListener>();
        unicastARPRequestTime = new ConcurrentHashMap<Long, Long>();
        deviceListener = new DeviceListenerImpl();
    }

    public void startUp() {
        factory = controllerProvider.getOFMessageFactory();
        virtualRouting.addPacketListener(this);
        deviceManager.addListener(this.deviceListener);
    }

    public void addArpListener(IARPListener al) {
        arpListeners.add(al);
        if (logger.isTraceEnabled()) {
            String listeners = "ARP listeners: ";
            for (IARPListener a : arpListeners)
                listeners += a.getName() + ", ";
            logger.trace(listeners);
        }
    }

    public void setControllerProvider(
                             IControllerService controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    public void setDeviceManager(IDeviceService deviceManager) {
        this.deviceManager = deviceManager;
    }

    public void setVirtualRouting(IVirtualRoutingService virtualRouting) {
        this.virtualRouting = virtualRouting;
    }

    public void setTopology(ITopologyService topology) {
        this.topology = topology;
    }

    public void setTunnelManager(ITunnelManagerService tunnelManager) {
        this.tunnelManager = tunnelManager;
    }

    // *******************
    // Internal Methods - ARP packet processing related
    // *******************

    private RoutingDecision setupDecision(IOFSwitch sw,
                                                 IDevice src,
                                                 IDevice dest,
                                                 OFPacketIn pi,
                                                 Ethernet eth,
                                                 RoutingAction action) {
        RoutingDecision vrd =
                new RoutingDecision(sw.getId(), pi.getInPort(), src, action);
        // For ARPs,
        // 1. We wildcard the L4 ports and IP ToS as they have no meaning
        //    in ARP packets.
        // 2. While the IP proto field in the match can be used to match on
        //    ARP opcode, we wildcard it as hardware may or may not support it
        // 3. While IP src and dst addresses can also be specified in the match,
        //    we wildcard it as hardware may or may not support it for ARPs
        // 4. We also wildcard the dl_vlan_pcp field as we are matching on
        //    untagged packets
        vrd.setWildcards(OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_TP_SRC |
                         OFMatch.OFPFW_NW_TOS | OFMatch.OFPFW_NW_PROTO |
                         OFMatch.OFPFW_NW_SRC_ALL | OFMatch.OFPFW_NW_DST_ALL |
                         OFMatch.OFPFW_DL_VLAN_PCP);

        /* NetVirt-254 ARP flows need to have a hard timeout. */
        vrd.setHardTimeout(ARP_FLOWMOD_HARD_TIMEOUT);

        if (dest != null)
            vrd.addDestinationDevice(dest);

        return vrd;
    }

    private boolean isDeviceKnownToCluster(IDevice d, long switchId) {
        SwitchPort[] aps = d.getAttachmentPoints();
        if (aps == null || aps.length == 0) return false;
        long swclid = topology.getL2DomainId(switchId);
        for (SwitchPort ap : aps) {
            long apclid = topology.getL2DomainId(ap.getSwitchDPID());
            if (apclid == swclid)
                return true;
        }
        return false;
    }

    /**
     * Gets the ARPMode configuration based on the source device and the NetVirt it
     * is in. If the device is in multiple NetVirt it will return the 'loosest'
     * configuration mode.
     * @param cntx The ListenerContext associated with this processing chain
     * @return The ARPMode config setting for the device
     */
    private ARPMode getARPMode(ListenerContext cntx) {
        // TODO - See if we can come up with a config mode
        // that takes both the source and destination into account
        ARPMode config = ARPMode.DROP_IF_UNKNOWN;
        List<VNSInterface> srcIfaces =
                INetVirtManagerService.bcStore.get(cntx,
                                       INetVirtManagerService.CONTEXT_SRC_IFACES);
        if (srcIfaces != null) {
            for (VNSInterface iface : srcIfaces) {
                ARPMode bc = iface.getParentVNS().getArpManagerMode();
                if (bc.compareTo(config) > 0) {
                    config = bc;
                }
            }
        }
        return config;
    }

    /**
     * Get the device corresponding to sender hardware and protocol address
     * fields in the ARP packet.
     */
    private IDevice getSenderDevice(ARP arp, short vlan,
                                    long swdpid, int port) {
        if (arp == null) return null;

        byte[] senderAddr = arp.getSenderHardwareAddress();
        long senderMAC = Ethernet.toLong(senderAddr);

        byte[] senderIPAddr = arp.getSenderProtocolAddress();
        int senderIP = IPv4.toIPv4Address(senderIPAddr);

        IDevice device = deviceManager.findDevice(senderMAC,
                                                   vlan, senderIP,
                                                    swdpid, port);
        return device;
    }

    private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                           ListenerContext cntx) {
        Ethernet eth =
                IControllerService.bcStore.get(cntx,
                        IControllerService.CONTEXT_PI_PAYLOAD);
        if (!(eth.getPayload() instanceof ARP)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Received a packet from switch {} that was not " +
                             "an ARP, PacketIn={}",
                             HexString.toHexString(sw.getId()), pi);
            }
            return Command.CONTINUE;
        }

        ARP arp = (ARP) eth.getPayload();
        // If this is an ARP packet for something other than IP; handle like
        // any other layer 2 packet
        if (arp.getProtocolType() != ARP.PROTO_TYPE_IP) {
            if (logger.isDebugEnabled()) {
                logger.debug("Received an ARP packet from switch {} that " +
                         "was not an IP ARP, PacketIn={}",
                         HexString.toHexString(sw.getId()), pi);
            }
            return Command.CONTINUE;
        }

        // The sender device is obtained from the ARP data
        IDevice senderDevice = getSenderDevice(arp, eth.getVlanID(),
                                                sw.getId(),
                                                pi.getInPort());
        if (senderDevice == null) return Command.CONTINUE;

        IDevice src =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
        if (src == null) return Command.CONTINUE;
        IDevice dst =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);

        if (logger.isTraceEnabled()) {
            logger.trace("Received an ARP packet, opcode {}, from switch " +
                     "{}/{} with sender-ip {} target-ip {}",
                     new Object[] {arp.getOpCode(),
                                   HexString.toHexString(sw.getId()),
                                   pi.getInPort(),
                                   IPv4.fromIPv4Address(IPv4.toIPv4Address(
                                             arp.getSenderProtocolAddress())),
                                   IPv4.fromIPv4Address(IPv4.toIPv4Address(
                                             arp.getTargetProtocolAddress()))});
        }

        ARPMode config = getARPMode(cntx);

        // Call all the ARP handlers
        for (IARPListener al : arpListeners) {
            ARPCommand ret = null;
            if (arp.getOpCode() == ARP.OP_REQUEST) {
                ret = al.ARPRequestHandler(sw, pi, cntx, config);
            } else if (arp.getOpCode() == ARP.OP_REPLY) {
                ret = al.ARPReplyHandler(sw, pi, cntx, config);
            } else if (arp.getOpCode() == ARP.OP_RARP_REQUEST) {
                ret = al.RARPRequestHandler(sw, pi, cntx, config);
            } else if (arp.getOpCode() == ARP.OP_RARP_REPLY) {
                ret = al.RARPReplyHandler(sw, pi, cntx, config);
            }

            if (ret == null || ret == ARPCommand.CONTINUE) {
                continue;
            } else if (ret == ARPCommand.SKIP) {
                // Don't process the rest of the modules.
                break;
            } else if (ret == ARPCommand.STOP) {
                // Stop processing this packet.
                RoutingAction ra = RoutingAction.NONE;
                RoutingDecision vrd = setupDecision(sw, src, dst, pi, eth, ra);
                vrd.addToContext(cntx);
                return Command.STOP;
            }
        }

        int dstip =  IPv4.toIPv4Address(arp.getTargetProtocolAddress());
        int srcip = IPv4.toIPv4Address(arp.getSenderProtocolAddress());

        // If sender IP is the same as the target IP,
        // it is a gratuitous ARP.
        // Let it proceed down the packet processing chain.
        if (srcip == dstip) {
            return Command.CONTINUE;
        }

        // Remove the unicastARPRequestTime entry from the map
        // when an ARP reply is received.
        if (arp.getOpCode() == ARP.OP_REPLY) {
            // We need to use the sender device here instead of source
            // device (as the source MAC address in the VRRP ARP response
            // will be different from the sender hardware address).
            unicastARPRequestTime.remove(senderDevice.getDeviceKey());
        }

        RoutingAction action = RoutingAction.FORWARD;
        if (ARPMode.FLOOD_IF_UNKNOWN.equals(config) ||
                ARPMode.ALWAYS_FLOOD.equals(config)) {
            action = RoutingAction.FORWARD_OR_FLOOD;
        }

        boolean isBroadcast = eth.isBroadcast();
        if (isBroadcast && !ARPMode.ALWAYS_FLOOD.equals(config)) {
            Iterator<? extends IDevice> dstiter =
                    deviceManager.queryClassDevices(src.getEntityClass(), null, null, dstip,
                                                    null, null);
            boolean found = false;
            while (dstiter.hasNext()) {
                dst = dstiter.next();
                Integer[] ipv4addrs = dst.getIPv4Addresses();
                for (Integer ipv4addr : ipv4addrs) {
                    if (ipv4addr.equals(dstip)) {
                        found = true;
                        break;
                    }
                }
                if (found)
                    break;
            }
            if (!found) dst = null;

            if (dst == null && ARPMode.DROP_IF_UNKNOWN.equals(config)) {
                /**
                 *  Drop the packetIn since the destination is unknown.
                 *  Do Not install DROP flowmod since dst mac in flowmod is FF,
                 *  which would prevent the host to ARP for known hosts.
                 *
                 *  FIXME: Not installing DROP flowmod on the switch exposes
                 *  the controller to ARP DOS attack. This should be
                 *  handled together with other security weakness in the
                 *  controller.
                 */
                action = RoutingAction.NONE;
            } else if (dst != null && arp.getOpCode() == ARP.OP_REQUEST) {
                long currTime = System.currentTimeMillis();
                Date dstLastSeen = dst.getLastSeen();
                Date threshold =
                        new Date(currTime -
                                 INTER_NetVirt_BROADCAST_SUPPRESSION_TIMEOUT_MS);
                /**
                 * If the dst device is last seen within last
                 * ARP_CACHE_DEFAULT_TIMEOUT_MS and src and dest
                 * devices are not in the same NetVirt, drop the request
                 */
                if (virtualRouting.connected(src, srcip, dst, dstip) == false &&
                        dstLastSeen != null &&
                        dstLastSeen.after(threshold)) {
                    // None will tell virtual routing to drop.
                    action = RoutingAction.NONE;
                } else if (isDeviceKnownToCluster(dst, sw.getId())) {
                    /**
                     * If dst device has not been heard from, or doesn't have AP in the
                     * cluster where the PI is received, flood the ARP request
                     * as a regular broadcast
                     */
                    Long lastUArpTime =
                            unicastARPRequestTime.get(dst.getDeviceKey());

                    if (lastUArpTime != null && lastUArpTime > 0 &&
                        (currTime - lastUArpTime > ARP_CACHE_DEFAULT_TIMEOUT_MS) &&
                        !ARPMode.DROP_IF_UNKNOWN.equals(config)) {
                        // if lastUArpTime > 0, then it is valid.
                        // It has been more than TIMEOUT (ms) since the last
                        // unicast ARP was sent, and we have not seen any
                        // response, so go back to flooding this ARP request.
                        action = RoutingAction.FORWARD_OR_FLOOD;
                    } else {
                        if (isValidIncomingUnicastPort(src, dst,
                                                       sw.getId(),
                                                       pi.getInPort())) {
                            if (lastUArpTime == null)
                                unicastARPRequestTime.put(dst.getDeviceKey(),
                                                          currTime);
                            OFPacketIn unicastARPRequest =
                                    createUnicastARPPacketIn(pi, eth, arp, dst);
                            if (logger.isTraceEnabled()) {
                                logger.trace("Converting ARP to unicast and" +
                                             "re-injecting {}", arp);
                            }
                            controllerProvider.injectOfMessage(sw,
                                                             unicastARPRequest);
                            // None will tell virtual routing to drop.
                            action = RoutingAction.NONE;
                        } else {
                            action = RoutingAction.NONE;
                            if (logger.isTraceEnabled()) {
                                logger.trace("Drop ARP packet as the packet " +
                                        "can be converted to unicast, however " +
                                        "packet-in switchport is invalid for " +
                                        "unicast transmission. " +
                                        "AP {}/{} for src device {}",
                                        new Object[] {
                                        HexString.toHexString(sw.getId()),
                                        pi.getInPort(),
                                        src.getMACAddressString()});
                            }
                        }
                    }
                }
            }
        }

        // Don't program a broadcast packet with a specific dst device
        if (isBroadcast && (action == RoutingAction.FORWARD ||
                action == RoutingAction.FORWARD_OR_FLOOD))
            dst = null;

        RoutingDecision vrd = setupDecision(sw, src, dst, pi, eth, action);

        if (logger.isTraceEnabled()) {
            logger.trace("Handling ARP with action {}: {}",
                    vrd.getRoutingAction(), arp);
        }
        vrd.addToContext(cntx);
        return Command.STOP;
    }

    private boolean isValidIncomingUnicastPort(IDevice srcDevice,
                                            IDevice dstDevice,
                                            long inSwitch,
                                            short inPort) {
        int i;

        // First, identify if the src-dst traffic is allowed to use tunnels
        // or not.
        boolean isTunnelTraffic = tunnelManager.isTunnelEndpoint(srcDevice)
                || tunnelManager.isTunnelEndpoint(dstDevice);
        boolean tunnelEnabled = !isTunnelTraffic;

        // If the packet-in switch port is not an attachment point port,
        // then, allow unicast conversion to take place.
        if (!topology.isAttachmentPointPort(inSwitch, inPort, tunnelEnabled))
            return true;

        // Get the source and destination attachment points on the same
        // L2 domain as the inSwitch.
        SwitchPort[] srcAPs = srcDevice.getAttachmentPoints();
        SwitchPort[] dstAPs = dstDevice.getAttachmentPoints();
        SwitchPort srcSwitchPort = null;
        SwitchPort dstSwitchPort = null;

        for (i=0; i<srcAPs.length; ++i) {
            if (topology.inSameL2Domain(inSwitch, srcAPs[i].getSwitchDPID(),
                                        tunnelEnabled)) {
                srcSwitchPort = srcAPs[i];
                break;
            }
        }
        // Attachment point for source not found in the same L2 domain
        if (srcSwitchPort == null) return false;

        for (i=0; i<dstAPs.length; ++i) {
            if (topology.inSameL2Domain(inSwitch, dstAPs[i].getSwitchDPID(),
                                        tunnelEnabled)) {
                dstSwitchPort = dstAPs[i];
                break;
            }
        }
        // Attachment point for destination not found in the same L2 domain
        if (dstSwitchPort == null) return false;

        // Check if the inSwitch, inPort are consistent with the source
        // attachment point port.  If inconsistent, return false.
        if (!topology.isConsistent(srcSwitchPort.getSwitchDPID(),
                                  (short)srcSwitchPort.getPort(),
                                  inSwitch, inPort,
                                  tunnelEnabled))
            return false;

        // Since the switch port is consistent, use this packet-in switch
        // packet-in port to verify if it the right switchport for unicast
        // traffic or not.

        // Get the incoming switch port for the unicast traffic from
        // srcAP to dstAP.
        NodePortTuple npt = topology.getIncomingSwitchPort(inSwitch,
                                                           inPort,
                                                           dstSwitchPort.getSwitchDPID(),
                                                           (short)dstSwitchPort.getPort(),
                                                           tunnelEnabled);

        // Verify if npt is the same as the inSwitch and inPort.
        if (npt == null) return false;

        return (npt.getNodeId() == inSwitch && npt.getPortId() == inPort);
    }

    /**
     * Converts an ARP into unicast Ethernet frame. We expect
     * that the destination device has only a single MAC address.
     * @param pi The original OFPacketIn.
     * @param eth The Ethernet frame containing the ARP request.
     * @param arp The ARP packet in the ethernet frame.
     * @param dst The IDevice destination device
     */
    private OFPacketIn createUnicastARPPacketIn(OFPacketIn pi,
                                                Ethernet eth, ARP arp,
                                                IDevice dst) {
        byte[] dstMac = Ethernet.toByteArray(dst.getMACAddress());
        IPacket arpRequest = new Ethernet()
        .setSourceMACAddress(eth.getSourceMACAddress())
        .setDestinationMACAddress(dstMac)
        .setEtherType(Ethernet.TYPE_ARP)
        .setVlanID(eth.getVlanID())
        .setPriorityCode(eth.getPriorityCode())
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REQUEST)
                .setSenderHardwareAddress(arp.getSenderHardwareAddress())
                .setSenderProtocolAddress(arp.getSenderProtocolAddress())
                .setTargetHardwareAddress(arp.getTargetHardwareAddress())
                .setTargetProtocolAddress(arp.getTargetProtocolAddress()));
        byte[] arpRequestSerialized = arpRequest.serialize();

        OFPacketIn newpi =
                (OFPacketIn) controllerProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_IN);
        newpi.setInPort(pi.getInPort());
        newpi.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        newpi.setReason(OFPacketInReason.NO_MATCH);
        newpi.setPacketData(arpRequestSerialized);
        newpi.setTotalLength((short) arpRequestSerialized.length);
        newpi.setLength((short)(OFPacketIn.MINIMUM_LENGTH +
                arpRequestSerialized.length));

        return newpi;
    }

    // *******************
    // IOFMessageListener
    // *******************

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

    @Override
    public String getName() {
        return "arpmanager";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    // *******************
    // IDeviceListener
    // *******************

    class DeviceListenerImpl implements IDeviceListener {
        @Override
        public void deviceAdded(IDevice device) {
            unicastARPRequestTime.remove(device.getDeviceKey());
        }

        @Override
        public void deviceRemoved(IDevice device) {
            unicastARPRequestTime.remove(device.getDeviceKey());
        }

        @Override
        public void deviceMoved(IDevice device) {
            unicastARPRequestTime.remove(device.getDeviceKey());
        }

        @Override
        public void deviceIPV4AddrChanged(IDevice device) {
            unicastARPRequestTime.remove(device.getDeviceKey());
        }

        @Override
        public void deviceVlanChanged(IDevice device) {
        }

        @Override
        public String getName() {
            return ArpManager.this.getName();
        }

        @Override
        public boolean isCallbackOrderingPrereq(String type, String name) {
            return false;
        }

        @Override
        public boolean isCallbackOrderingPostreq(String type, String name) {
            return false;
        }
    }
}

