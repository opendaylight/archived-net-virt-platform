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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceListener;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.DHCPMode;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.packet.DHCP;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.routing.RoutingDecision;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@LogMessageCategory("Network Virtualization")
public class DhcpManager implements IOFMessageListener {

    protected static Logger logger = LoggerFactory.getLogger(DhcpManager.class);

    /* Maps deviceKeys to the dhcpServer instance (which keeps track of
     * pending requests, etc.). We will maintain this map for all DHCP servers.
     */
    private ConcurrentMap<Long, DhcpServer> dhcpServers;

    /* Maps NetVirt (name) to the deviceId of its DhcpServer. Only used for
     * snooped servers. We currently allow only a single server per NetVirt.
     * FIXME: we don't remove stale entries from this map
     */
    private ConcurrentMap<String, Long> netVirtDhcpServers;

    private TimeoutCache<Long> deviceIdsPending;

    /* This timeout is used to stop converting to unicast in Flood-if-unknown
     * mode in one of two cases:
     *   * We converted to unicast for a client but the client did not receive
     *     a reply in the last TIMEOUT ms.
     *   * We converted a request to a particular server to unicast but the
     *     server has not send /any/ replies since.
     * The two cases are subtly different and thus we handle them independently.
     * (E.g., if the DHCP server changes its config and is not responsible
     * for a particular NetVirt anymore. The server is till alive but it might
     * not answer to (some) clients.
     */
    protected long UNICAST_CONV_TIMEOUT = 2000; // in ms

    protected IControllerService controllerProvider;
    protected IDeviceService deviceManager;
    protected IVirtualRoutingService virtualRouting;
    protected INetVirtManagerService netVirtManager;

    protected DeviceListenerImpl deviceListener;

    public void init() {
        dhcpServers = new ConcurrentHashMap<Long, DhcpServer>();
        netVirtDhcpServers = new ConcurrentHashMap<String, Long>();
        deviceIdsPending = new TimeoutCache<Long>(UNICAST_CONV_TIMEOUT);
        deviceListener = new DeviceListenerImpl();
    }

    // *******************
    // Getters and Setters
    // *******************

    @Override
    public String getName() {
        return "dhcpmanager";
    }

    public IDeviceListener getDeviceListener() {
        return deviceListener;
    }

    public void setControllerProvider(IControllerService controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    public void setDeviceManager(IDeviceService deviceManager) {
        this.deviceManager = deviceManager;
    }

    // *******************
    // Internal Methods
    // *******************

    private OFPacketIn convertBCastToUcast(Ethernet eth,
                                           OFPacketIn pi,
                                           IDevice dst) {
        byte[] dstMac = Ethernet.toByteArray(dst.getMACAddress());
        eth.setDestinationMACAddress(dstMac);
        byte[] serializedPacket = eth.serialize();

        OFPacketIn fakePi =
                ((OFPacketIn) controllerProvider.getOFMessageFactory()
                    .getMessage(OFType.PACKET_IN))
                    .setInPort(pi.getInPort())
                    .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                    .setReason(OFPacketInReason.NO_MATCH);
        fakePi.setPacketData(serializedPacket);
        fakePi.setTotalLength((short) serializedPacket.length);
        fakePi.setLength((short)(OFPacketIn.MINIMUM_LENGTH +
                                 serializedPacket.length));

        return fakePi;
    }

    /**
     * Add FORWARD_OR_FLOOD decision, return STOP to indicate that a decision
     * has been made
     */
    private Command floodDhcpPacket(IOFSwitch sw, OFPacketIn pi, IDevice srcDev,
                                 ListenerContext cntx) {
        RoutingDecision vrd =
            new RoutingDecision(sw.getId(), pi.getInPort(), srcDev,
                                       RoutingAction.FORWARD_OR_FLOOD);
        // FIXME: what to do with wildcards since we didn't run ACLs.
        // Sigh. Not using wildcards as a quick and ugly fix
        vrd.setWildcards(0);
        vrd.addToContext(cntx);

        return Command.STOP;
    }

    /**
     * Add a NONE decision and return STOP, the packet is effectively being
     * ignored.
     */
    private Command ignoreDhcpPacket(IOFSwitch sw, OFPacketIn pi,
                                IDevice srcDev, ListenerContext cntx) {
        RoutingDecision vrd =
            new RoutingDecision(sw.getId(), pi.getInPort(), srcDev, RoutingAction.NONE);
        vrd.addToContext(cntx);

        return Command.STOP;
    }


    /**
     * Handle a broadcast DHCP request for FLOOD_IF_UNKNOWN mode
     * @param netVirt
     * @param srcDevice
     * @param eth
     * @param pi
     * @param sw
     * @param cntx
     * @return
     */
    private Command handleFloodIfUnkownRequest(VNS netVirt, IDevice srcDevice,
                                           Ethernet eth, OFPacketIn pi,
                                           IOFSwitch sw,
                                           ListenerContext cntx) {

        // Get the snooped server for this NetVirt. If none exists we flood the
        // packet.
        Long serverDeviceId = netVirtDhcpServers.get(netVirt.getName());
        if (serverDeviceId == null) {
            return floodDhcpPacket(sw, pi, srcDevice, cntx);
        }
        DhcpServer server = dhcpServers.get(serverDeviceId);
        IDevice serverDevice = deviceManager.getDevice(serverDeviceId);
        if (server == null || serverDevice == null) {
            return floodDhcpPacket(sw, pi, srcDevice, cntx);
        }

        if (deviceIdsPending.isTimeoutExpired(srcDevice.getDeviceKey())) {
            // Check that this device doesn't have a long outstanding request
            // that hasn't been answered yet. If we have outstanding requests
            // we need to flood.
            if (logger.isDebugEnabled()) {
                logger.debug("Not converting to unicast. Unanswered DHCP " +
                        "request from {} pending for more than {} ms",
                                 srcDevice, UNICAST_CONV_TIMEOUT);
            }
            return floodDhcpPacket(sw, pi, srcDevice, cntx);
        }
        // Check liveness
        if (!server.isAlive()) {
            // If server has been unresponsive to previous request, we
            // need to flood.
            if (logger.isDebugEnabled()) {
                logger.debug("Not converting to unicast. DHCP server {}" +
                        "has been unresponsive", serverDevice);
            }
            return floodDhcpPacket(sw, pi, srcDevice, cntx);
        }

        OFPacketIn fakePi = convertBCastToUcast(eth, pi, serverDevice);
        controllerProvider.injectOfMessage(sw, fakePi);
        deviceIdsPending.putIfAbsent(srcDevice.getDeviceKey());
        server.hadRequest();
        return ignoreDhcpPacket(sw, pi, srcDevice, cntx);
    }

    /**
     * Handle a broadcast DHCP request for STATIC mode
     * @param netVirt
     * @param srcDevice
     * @param eth
     * @param pi
     * @param sw
     * @param cntx
     * @return
     */
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="DHCP server {server or relay IP} configured "
                       + "{NetVirt name} is unknown to DeviceManager. "
                       + "Dropping request",
                explanation="The named NetVirt uses DHCP-mode static but the " +
                        "configured server or relay ould not be found. " +
                        "The DHCP request is ignored",
                recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    private Command handleStaticRequest(VNS netVirt, IDevice srcDevice,
                                        Ethernet eth, OFPacketIn pi,
                                        IOFSwitch sw,
                                        ListenerContext cntx) {
        // Query device manager for the configure IP address of the
        // DHCP server or relay
        IDevice dhcpServerDevice = null;
        Iterator<? extends IDevice> dstiter =
                deviceManager.queryClassDevices(srcDevice.getEntityClass(),
                                                null, null,
                                                netVirt.getDhcpIp(),
                                                null, null);
        if (dstiter.hasNext()) {
            dhcpServerDevice = dstiter.next();
        }
        if (dhcpServerDevice != null) {
            OFPacketIn fakePi =
                    convertBCastToUcast(eth, pi, dhcpServerDevice);
            controllerProvider.injectOfMessage(sw, fakePi);
        } else {
            // We could not locate a Device for the configured
            // DHCP server. We need to drop the request.
            // TODO: should rate-limit this log message
            logger.warn("DHCP server {} configured for NetVirt {} " +
                    "is unknown to DeviceManager. Dropping request",
                    IPv4.fromIPv4Address(netVirt.getDhcpIp()),
                    netVirt.getName());

        }
        return ignoreDhcpPacket(sw, pi, srcDevice, cntx);
    }

    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="No source device found for MAC {mac address}",
                explanation="Could not find a source device for the " +
                        "source of a DHCP packet.",
                recommendation=LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="WARN",
                message="Possible rogue DHCP server {ip address} " +
                        "detected, stopping flow",
                explanation="",
                recommendation=LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Unknown DHCP Mode {} for NetVirt {}",
                explanation="The configures DHCP mode is unknown",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
      @LogMessageDoc(level="INFO",
                message="NetVirt {name} snooped DHCP {ip address}",
                explanation="A new DHCP server was discovered for the " +
                        "given NetVirt."),
      @LogMessageDoc(level="WARN",
                message="DHCP server {ip address} is not known " +
                        "to the device manager",
                explanation="A DHCP reply was seen that doesn't correspond " +
                        "to any known device.",
                recommendation=LogMessageDoc.TRANSIENT_CONDITION),
      @LogMessageDoc(level="ERROR",
                message="DHCP reply to unknown destination device, " +
                        "MAC = {mac address}",
                explanation="A DHCP reply was sent to an address that doesn't " +
                        "correspond to any known device.",
                recommendation=LogMessageDoc.TRANSIENT_CONDITION)
    })
    private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                           ListenerContext cntx) {
        Ethernet eth =
            IControllerService.bcStore.
            get(cntx, IControllerService.CONTEXT_PI_PAYLOAD);

        if (eth.getEtherType() != Ethernet.TYPE_IPv4)
            return Command.CONTINUE;

        IPv4 ipv4 = (IPv4) eth.getPayload();
        if (ipv4.getProtocol() != IPv4.PROTOCOL_UDP)
            return Command.CONTINUE;

        UDP udp = (UDP) ipv4.getPayload();
        // Make sure it's a DHCP request/reply
        if (!(udp.getPayload() instanceof DHCP))
            return Command.CONTINUE;

        // Get source device
        IDevice srcDevice =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
        if (srcDevice == null) {
            logger.error("No source device found for MAC {}",
                         HexString.toHexString(eth.getSourceMACAddress()));
            return ignoreDhcpPacket(sw, pi, null, cntx);
        }

        // Get destination device. This can be null.
        IDevice dstDevice =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);

        if (logger.isTraceEnabled()) {
             logger.trace("DHCP Packet found from {}", srcDevice);
        }

        List<VNSInterface> srcIfaces =
            INetVirtManagerService.bcStore.
                get(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES);


        DHCP dhcp = (DHCP) udp.getPayload();
        byte opcode = dhcp.getOpCode();

        // Find the NetVirt and its mode
        VNS netVirt = null;
        DHCPMode mode = null;
        if (srcIfaces != null && srcIfaces.size() > 0) {
            // ifaces are ordered by priority. In case of unicast packet
            // VR has already run chooseNetVirt() and replaced the list
            // of all matching interfaces with the one that actually
            // matches.
            netVirt = srcIfaces.get(0).getParentVNS();
            mode = netVirt.getDhcpManagerMode();
        } else {
            logger.error("No NetVirt interface found for device {}",
                         srcDevice);
            return ignoreDhcpPacket(sw, pi, srcDevice, cntx);
        }

        /*
         * If it's a unicast DHCP request we just Command.CONTINUE.
         * This could either be a host requesting a DHCP lease it had when it
         * joined this network previously, or it could been re-injected into our
         * processing chain and we just need to forward it at this stage.
         * We use flood or forward to ensure that the packet gets there.
         */
        if (opcode == DHCP.OPCODE_REQUEST) {

            if (eth.isBroadcast()) {
                switch(mode) {
                    case ALWAYS_FLOOD:
                        return floodDhcpPacket(sw, pi, srcDevice, cntx);
                    case FLOOD_IF_UNKNOWN:
                        return handleFloodIfUnkownRequest(netVirt, srcDevice, eth,
                                                          pi, sw, cntx);
                    case STATIC:
                        return handleStaticRequest(netVirt, srcDevice, eth,
                                                   pi, sw, cntx);
                    default:
                        logger.error("Unknown DHCP Mode {} for NetVirt {}",
                                      mode, netVirt.getName());
                        return ignoreDhcpPacket(sw, pi, srcDevice, cntx);
                }
            } else {
                // This is a unicast request.
                if (dstDevice == null) {
                    // unknown dest device. Flood if appropriate otherwise
                    // drop the packet.
                    // FIXME: we really want the full Virtual Routing logic
                    // to decide here if we can reach the destination
                    // (see processUnicastPacket). But we can't just let
                    // the packet pass through for processUnicastPacket to
                    // handle it because in case the dst is unreachable
                    // we need to flood and not discover the dest via
                    // an ARP.
                    switch(mode) {
                        case ALWAYS_FLOOD:
                        case FLOOD_IF_UNKNOWN:
                            return floodDhcpPacket(sw, pi, srcDevice, cntx);
                        case STATIC:
                        default:
                            return ignoreDhcpPacket(sw, pi, srcDevice, cntx);
                    }
                }
                return Command.CONTINUE;
                // if destination is known, we let the normal VR code
                // handle it.
                // FIXME: This implies that we will allow ARP requests to
                //        incorrect servers in STATIC mode. We still prevent
                //        spoofing because we verify the reply.
            }
        } else if (opcode == DHCP.OPCODE_REPLY) {
            RoutingDecision vrd = null;

            // track server (regardless of DHCPMode and flag that we've
            // received a response.
            // TODO: could limit this to FLOOD_IF_UNKOWN only
            Long serverDeviceId = srcDevice.getDeviceKey();
            DhcpServer server = dhcpServers.get(serverDeviceId);
            if (server == null) {
                server = new DhcpServer(UNICAST_CONV_TIMEOUT);
                DhcpServer oldServer =
                        dhcpServers.putIfAbsent(srcDevice.getDeviceKey(), server);
                if (oldServer != null)
                    server = oldServer;
            }
            server.hadResponse();
            if (dstDevice != null)
                deviceIdsPending.remove(dstDevice.getDeviceKey());

            switch(mode) {
                case ALWAYS_FLOOD:
                    if (eth.isBroadcast()) {
                        return floodDhcpPacket(sw, pi, srcDevice, cntx);
                    } else {
                        if (dstDevice == null)
                            return floodDhcpPacket(sw, pi, srcDevice, cntx);
                    }
                    // Unicast and device is known. Let normal VR handle it
                    return Command.CONTINUE;
                case FLOOD_IF_UNKNOWN:
                    Long oldServerId = netVirtDhcpServers.put(netVirt.getName(),
                                                          serverDeviceId);
                    if (logger.isDebugEnabled()) {
                        if (oldServerId == null) {
                            logger.debug("NetVirt {} snooped DHCP {}",
                                         netVirt.getName(),
                                         srcDevice.getMACAddress());
                        } else if (! oldServerId.equals(serverDeviceId)) {
                            logger.debug("NetVirt {} snooped DHCP changed to {}",
                                         netVirt.getName(),
                                         srcDevice.getMACAddress());
                        }
                    }
                    if (eth.isBroadcast()) {
                        // FIXME: get client HW-addr from DHCP and unicast to
                        // it ?? Can we do this in all cases? Why didn't the
                        // server send unicast if it knows the client's HW
                        // address?
                        return floodDhcpPacket(sw, pi, srcDevice, cntx);
                    } else {
                        if (dstDevice == null) {
                            return floodDhcpPacket(sw, pi, srcDevice, cntx);
                        }
                    }
                    return Command.CONTINUE;
                case STATIC:
                    boolean serverIpFound = false;
                    for(Integer ip: srcDevice.getIPv4Addresses()) {
                        if (ip != null && ip.equals(netVirt.getDhcpIp())) {
                            serverIpFound = true;
                            break;
                        }
                    }
                    if (! serverIpFound) {
                        logger.warn("Possible rogue DHCP server {} detected, stopping flow",
                                    IPv4.fromIPv4Address(ipv4.getSourceAddress()));
                        vrd = new RoutingDecision(sw.getId(), pi.getInPort(),
                                                  srcDevice,
                                                  RoutingAction.DROP);
                        vrd.addDestinationDevice(dstDevice);
                        vrd.addToContext(cntx);
                        return Command.STOP;
                    }
                    if (eth.isBroadcast()) {
                        // FIXME: get client HW-addr from DHCP and unicast to
                        // it ?? Can we do this in all cases? Why didn't the
                        // server send unicast if it knows the client's HW
                        // address?
                        return floodDhcpPacket(sw, pi, srcDevice, cntx);
                    } else {
                        if (dstDevice == null) {
                            return ignoreDhcpPacket(sw, pi, srcDevice, cntx);
                        }
                    }
                    return Command.CONTINUE;
                default:
                    break;

            }
        }
        // we should never get here.
        return Command.CONTINUE;
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
            dhcpServers.remove(device.getDeviceKey());
            deviceIdsPending.remove(device.getDeviceKey());
        }

        @Override
        public void deviceRemoved(IDevice device) {
            dhcpServers.remove(device.getDeviceKey());
            deviceIdsPending.remove(device.getDeviceKey());
        }

        @Override
        public void deviceMoved(IDevice device) {
            // no-op
        }

        @Override
        public void deviceIPV4AddrChanged(IDevice device) {
            // no-op
        }

        @Override
        public void deviceVlanChanged(IDevice device) {
            // no-op
        }

        @Override
        public String getName() {
            return DhcpManager.this.getName();
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
