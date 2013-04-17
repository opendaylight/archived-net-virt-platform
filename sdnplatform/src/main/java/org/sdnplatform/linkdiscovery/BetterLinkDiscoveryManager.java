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

package org.sdnplatform.linkdiscovery;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.sdnplatform.core.IInfoProvider;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.LDUpdate;
import org.sdnplatform.linkdiscovery.ILinkDiscovery.UpdateOperation;
import org.sdnplatform.linkdiscovery.internal.LinkDiscoveryManager;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.LLDPTLV;
import org.sdnplatform.routing.Link;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.tunnelmanager.ITunnelManagerService;
import org.sdnplatform.vendor.OFActionMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BetterLinkDiscoveryManager extends LinkDiscoveryManager
implements IOFMessageListener, IOFSwitchListener,
IStorageSourceListener, ILinkDiscoveryService,
IModule, IInfoProvider {
    protected static Logger log =
            LoggerFactory.getLogger(BetterLinkDiscoveryManager.class);

    ITunnelManagerService tunnelManager;

    @Override
    public void startUp(ModuleContext context) {
        super.startUp(context);
    }

    @Override
    public void init (ModuleContext context) {

        try {
            // set the default value to true.
            AUTOPORTFAST_DEFAULT = true;
            super.init(context);
            tunnelManager = context.getServiceImpl(ITunnelManagerService.class);
        } catch (Exception e) {
            log.warn("{}", e);
        }
    }

    /**
     * This method should ideally be in OFSwitchImpl that should be
     * overridden when sub-classing.  This way, every switch can
     * implement their own versions of identifying a tunnel port
     * if they are indeed different
     */
    @Override
    public boolean isTunnelPort(long sw, short port) {
        if (tunnelManager == null) return false;
        Short tunnelPort = tunnelManager.getTunnelPortNumber(sw);
        if (tunnelPort == null || tunnelPort.shortValue() != port)
            return false;
        return true;
    }

    @Override
    public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info) {

        if (lt == null)
            return ILinkDiscovery.LinkType.INVALID_LINK;

        IOFSwitch srcSw = controllerProvider.getSwitches().get(lt.getSrc());
        IOFSwitch dstSw = controllerProvider.getSwitches().get(lt.getDst());

        if (srcSw == null || dstSw == null)
            return ILinkDiscovery.LinkType.INVALID_LINK;

        if (isTunnelPort(lt.getSrc(), lt.getSrcPort()) ||
                isTunnelPort(lt.getDst(), lt.getDstPort())) {
            return ILinkDiscovery.LinkType.TUNNEL;
        }

        if (info == null) return ILinkDiscovery.LinkType.INVALID_LINK;

        if (info.getUnicastValidTime() != null) {
            return ILinkDiscovery.LinkType.DIRECT_LINK;
        } else if (info.getMulticastValidTime()  != null) {
            return ILinkDiscovery.LinkType.MULTIHOP_LINK;
        }
        return ILinkDiscovery.LinkType.INVALID_LINK;
    }

    @Override
    protected void setControllerTLV() {
        //Setting the controllerTLVValue based on current nano time,
        //controller's IP address, and the network interface object hash
        //the corresponding IP address.

        final int prime = 7867;
        InetAddress localIPAddress = null;
        NetworkInterface localInterface = null;

        byte[] controllerTLVValue = new byte[] {0, 0, 0, 0, 0, 0, 0, 0};  // 8 byte value.
        ByteBuffer bb = ByteBuffer.allocate(10);

        try{
            localIPAddress = java.net.InetAddress.getLocalHost();
            localInterface = NetworkInterface.getByInetAddress(localIPAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        long result = System.nanoTime();
        if (localIPAddress != null)
            result = result * prime + IPv4.toIPv4Address(localIPAddress.getHostAddress());
        if (localInterface != null)
            result = result * prime + localInterface.hashCode();
        // Set the first 4 bits to 0x7, so that SDN platform is higher than sdnplatform.
        result = result | (0x7000000000000000L);

        log.trace("Controller TLV: {}", result);

        bb.putLong(result);
        bb.rewind();
        bb.get(controllerTLVValue, 0, 8);

        this.controllerTLV = new LLDPTLV().setType((byte) 0x0c).setLength((short) 8).setValue(controllerTLVValue);
    }

    /**
     * This method overrides the one in LinkDiscovery to special-case
     * tunnel links.
     */
    @Override
    protected boolean isLinkAllowed(long src, short srcPort,
                                    long dst, short dstPort) {
        // tunnel manager doesn't exist.
        if (tunnelManager == null) return true;

        Short srcTunnelPort = tunnelManager.getTunnelPortNumber(src);
        Short dstTunnelPort = tunnelManager.getTunnelPortNumber(dst);

        boolean srcTunnelFlag = (srcTunnelPort != null &&
                srcTunnelPort.shortValue() == srcPort);
        boolean dstTunnelFlag = (dstTunnelPort != null &&
                dstTunnelPort.shortValue() == dstPort);

        if (srcTunnelFlag && dstTunnelFlag) {
            // Tunnel links are not allowed to be handled. However updates are
            // being sent for tunnel liveness detection
            updates.add(new LDUpdate(src, srcPort, dst, dstPort,
                                     ILinkDiscovery.LinkType.TUNNEL,
                                     UpdateOperation.LINK_UPDATED));
            return false;
        } else if (srcTunnelFlag || dstTunnelFlag) {
            // one of them is tunnel, so there's something wrong
            // in the network.
            log.warn("Detecting link between a tunnel and a non-tunnel port. {}",
                     new Link(src, srcPort, dst, dstPort));
            return false;
        }

        return true;
    }

    /**
     * This empty method is to ensure link information is not written to
     * the database on the SDN platform -- for scalability; while this
     * feature is available on the open-source side.  These methods are
     * currently in place for scalability. When we work on multi-master,
     * we need to sync the links through the database, then we will remove
     * and/or re-factor as necessary.
     * @param lt
     * @param linkInfo
     */
    @Override
    protected void writeLinkToStorage(Link lt, LinkInfo linkInfo) {
    }

    /**
     * Since we are not writing links to storage, we don't need to remove
     * links from storage.
     */
    @Override
    protected void removeLinkFromStorage(Link lt) {
    }


    @Override
    protected List<OFAction> getDiscoveryActions (IOFSwitch sw, OFPhysicalPort port){
        List<OFAction> actions = new ArrayList<OFAction>();

        // if overlay bind mode and port mirror is enabled, set the action to mirror
        if ((sw.getActions() == 0) && (port.getConfig() == 0x80000000)) {
            actions.add(new OFActionMirror(port.getPortNumber()));
        } else {
            actions.add(new OFActionOutput(port.getPortNumber(), (short) 0));
        }
        return actions;
    }

    /**
     * Check if outgoing discovery messages are enabled or not.
     * @param sw
     * @param port
     * @param isStandard
     * @param isReverse
     * @return
     */
    @Override
    protected boolean isOutgoingDiscoveryAllowed(long sw, short port,
                                                 boolean isStandard,
                                                 boolean isReverse) {

        if (isLinkDiscoverySuppressed(sw, port)) {
            /* Dont send LLDPs out of this port as suppressLLDP is set */
            return false;
        }

        IOFSwitch iofSwitch = controllerProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            return false;
        }

        if (port == OFPort.OFPP_LOCAL.getValue()) return false;

        OFPhysicalPort ofpPort = iofSwitch.getPort(port);
        if (ofpPort == null) {
            if (log.isTraceEnabled()) {
                log.trace("Null physical port. sw={}, port={}",
                          HexString.toHexString(sw), port);
            }
            return false;
        }

        // if it is a tunnel port, then do not send LLDPs.
        if (tunnelManager != null &&
                tunnelManager.getTunnelPortNumber(sw) != null &&
                tunnelManager.getTunnelPortNumber(sw).shortValue() == port)
            return false;

        // For fast ports, do not send forward LLDPs or BDDPs.
        if (!isReverse && isAutoPortFastFeature() && iofSwitch.isFastPort(port))
            return false;

        // if overlay bind mode and port mirror is not enabled, do not send LLDPs or BDDPs.
        if ((iofSwitch.getActions() == 0) && (ofpPort.getConfig() != 0x80000000)) {
            return false;
        }

        return true;
    }
}
