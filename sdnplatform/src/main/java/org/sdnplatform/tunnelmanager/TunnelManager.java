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

package org.sdnplatform.tunnelmanager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFVendor;
import org.openflow.util.HexString;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.IOFSwitch.OFPortType;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.util.SingletonTask;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.ovsdb.IOVSDB;
import org.sdnplatform.ovsdb.IOVSDBManagerService;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.vendor.OFBigSwitchVendorData;
import org.sdnplatform.vendor.OFBigSwitchVendorExtensions;
import org.sdnplatform.vendor.OFInterfaceIPReplyVendorData;
import org.sdnplatform.vendor.OFInterfaceIPRequestVendorData;
import org.sdnplatform.vendor.OFInterfaceVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/**
 * TunnelManager collects and maintains information regarding the tunneling
 * capability of connected switches (consult ITunnelManagerService for more
 * information on the API tunnelManager offers). As of the Fat Tire release,
 * TunnelManager no longer creates or deletes tunnel ports in connected switches.
 *
 * A switch is defined as
 *   'tunneling-capable': If it has been configured with a OFPortType.TUNNEL port
 *                        and a OFPortType.TUNNEL_LOOPBACK port using out of band
 *                        techniques (eg. using ovs-vsctl or our install script).
 *   'tunneling-enabled': By default a tunneling-capable switch is tunneling-enabled
 *                        It is however possible to enable or disable the tunneling
 *                        function on a tunnel-capable switch using the CLI or REST-API
 *   'tunneling-active':  A tunneling-enabled switch with a tunnel-endpoint IP
 *                        address on the TUNNEL_LOOPBACK port is deemed active for tunneling
 *
 *  A tunnel port is defined as
 *    a port configured on a switch with name 'tun-bsn'. This port is necessarily
 *    an OpenFlow port - i.e. a port under OpenFlow control. Note that there is
 *    a single such port on a switch; it is created or removed by means other than
 *    the controller; and no IP address is configured on this port. Packets sent
 *    out of this port will get encapsulated with (currently) GRE encap and then
 *    reappear through the TUNNEL_LOOPBACK port.
 *
 *  A tunnel-endpoint is defined as
 *    a host-OS interface on which the tunnel-IP address for the switch is
 *    configured. In the past, this 'could' be the 'ovs-br0' port in an OVS (in which case
 *    it is OpenFlow visible); or it could be some other interface (eg. eth3)
 *    which is not OpenFlow controllable. In our current FatTire solution, the
 *    tunnel-endpoint is the OFPortType.TUNNEL_LOOPBACK port (named tun-loopback).
 *
 * @author Saurav Das
 */

public class TunnelManager
    implements IModule, IStorageSourceListener,
            ITunnelManagerService, IHAListener, IOFSwitchListener, IOFMessageListener
{
    private static final String SWITCH_CONFIG_TABLE_NAME =
        "controller_switchconfig";
    private static final String SWITCH_DPID = "dpid";
    private static final String TUNNEL_ENABLED_OR_NOT = "tunnel_termination";
    protected static Logger logger = LoggerFactory.
                                        getLogger(TunnelManager.class);

    protected IStorageSourceService storageSource;
    protected IControllerService controllerProvider;
    protected IRestApiService restApi;
    protected IOVSDBManagerService ovsDBManager;
    protected IThreadPoolService threadPool;

    protected static int TUNNEL_TASK_DELAY = 1000; //ms
    // timer for retrying fetch of tunnel-IP-address
    private static int TUNNEL_IP_RETRY = 1000; // ms
    private static int TUNNEL_IP_RETRY_MAX = 32000; // ms

    /**
     * Asynchronous task for tunnel creation/deletion for all switches.
     */
    SingletonTask tunnelHandlingTask;

    /**
     * protected class to hold info on tunnel port
     */
    protected class TunnelPortInfo {
        long dpid;
        short portNum;  // port number of the OFPortType.TUNNEL port
        short loopbackPortNum; // port number of the OFPortType.TUNNEL_LOOPBACK port
        int ipv4Addr;   // ipv4 addr of the tunnel-endpoint
        int ipv4AddrMask;
        byte[] macAddr; // mac addr of the tunnel-endpoint
        String enabled; // could be '', 'disabled', or 'yes'
        boolean active; // true when IP addr is learnt and tunneling is not disabled

        public TunnelPortInfo() {
            macAddr = new byte[] {0,0,0,0,0,0};
            portNum = -1;
            loopbackPortNum = -1;
            enabled = "";
        }
    }

    /**
     * Set of all switches on which a OFPortType.TUNNEL port exists.
     */
    ConcurrentHashMap<Long, TunnelPortInfo> tunnelCapableSwitches =
            new ConcurrentHashMap<Long, TunnelPortInfo>();

    /**
     * Convenience cache to quickly return switch dpid for a given tunnel-endpoint
     * IP address. Note that even if tunneling is not ACTIVE on the switch, this
     * cache will preserve the ip-to-dpid mapping.
     */
    ConcurrentHashMap<Integer, Long> tunnIPToDpid = new ConcurrentHashMap<Integer, Long>();

    /**
     * Convenience cache to quickly return switch dpid for a given tunnel-endpoint
     * MAC address. Note that even if tunneling is not ACTIVE on the switch, this
     * cache will preserve the mac-to-dpid mapping.
     */
    ConcurrentHashMap<Long, Long> tunnMACToDpid = new ConcurrentHashMap<Long, Long>();

    /**
     * tunnelIPSubnets keeps track of all subnets reported by tunnel-capable switches
     *
     */
    ConcurrentHashMap<Integer, Set<Integer>> tunnelIPSubnets =
            new ConcurrentHashMap<Integer, Set<Integer>>();

    /**
     * Map of switch-dpids and their tunnel-configuration in storage
     */
    HashMap<String, String> confTunnelSwitches;
    HashMap<String, String> lastConfTunnelSwitches;

    /**
     * Set of switches which are tunnel capable (i.e. tunnel port exists)
     * but local-tunnel-IP has not been found
     */
    protected Set<Long> outstandingTunnelIP = Collections.newSetFromMap(
                                       new ConcurrentHashMap<Long,Boolean>());

    /**
     * Requests for interface tunnel-IP sent to switches
     */
    protected Map<Long, Set<Integer>>  sentIPRequests = new ConcurrentHashMap<
            Long, Set<Integer>>();

    /**
     * Collection of registered listeners for tunnel ports added/removed
     */
    public Collection<ITunnelManagerListener> listeners;



    //******************
    // IOFSwitchListener
    //******************

    /**
     * Checks if added switch has a configured tunnel ports (named 'tun-bsn'
     * and 'tun-loopback'
     * - if so, the switch is tunnel capable and registered in 'tunnelCapableSwitches'
     * - if the tunnel port exists, the switch is enabled for tunneling by default
     * - the operator can override the default in the CLI; and so we check for
     *   tunnel state configuration from storage
     *   - if no state is configured, or the state is 'enabled'
     *     tunnelManger tries to get the local tunnel-IP configured on the tunnel-endpoint
     *   - if tunnel-state is 'disabled', no attempt is made to get the tunnel-IP
     *     however, tunnelCpableSwitches will still register the switch as
     *     tunnel capable.
     */
    @Override
    public void addedSwitch(IOFSwitch sw) {
        long dpid = sw.getId();
        short tunPortNum = -1;
        short tunLoopbackPortNum  = -1;
        int numTunnelPorts = 0;
        for (OFPhysicalPort p : sw.getEnabledPorts()) {
            //logger.info("port name: {}", p.getName());
            if (sw.getPortType(p.getPortNumber()) == OFPortType.TUNNEL) {
                numTunnelPorts++;
                tunPortNum = p.getPortNumber();
            } else if (sw.getPortType(p.getPortNumber()) == OFPortType.TUNNEL_LOOPBACK) {
                numTunnelPorts++;
                tunLoopbackPortNum = p.getPortNumber();
            }
        }
        if (numTunnelPorts == 2) {
            // verify non-existence of switch in existing set of t.c.switches
            if (tunnelCapableSwitches.containsKey(dpid)) {
                logger.warn("Tunnel capable switch {} re-discovered",
                            HexString.toHexString(dpid));
            } else {
                TunnelPortInfo tp = new TunnelPortInfo();
                tp.dpid = dpid;
                tp.portNum = tunPortNum;
                tp.loopbackPortNum = tunLoopbackPortNum;
                tunnelCapableSwitches.put(dpid, tp);
                outstandingTunnelIP.add(dpid);
                if (logger.isDebugEnabled()) {
                    logger.debug("Adding tunnel-capable switch {}. Querying tunnel-IP..",
                                 HexString.toHexString(dpid));
                }
                //delay checking storage for multiple switches connecting nearly simultaneously
                //if (logger.isDebugEnabled()) showAllTunnels();
                tunnelHandlingTask.reschedule(TUNNEL_TASK_DELAY, TimeUnit.MILLISECONDS);
            }
       }
    }


    /**
     * Removes the switch from all maps/lists if it was tunnel-capable
     */
    @Override
    public void removedSwitch(IOFSwitch sw) {
        long dpid = sw.getId();
        if (tunnelCapableSwitches.containsKey(dpid)) {
            TunnelPortInfo deltpi = tunnelCapableSwitches.remove(dpid);
            tunnIPToDpid.remove(deltpi.ipv4Addr);
            tunnMACToDpid.remove(Ethernet.toLong(deltpi.macAddr));
            outstandingTunnelIP.remove(dpid);
            // may have had some ovsdbmanager dealings if an OVSDB had been created
            ovsDBManager.removeOVSDB(dpid);
            // may have pending IP requests
            sentIPRequests.remove(dpid);
            if (logger.isDebugEnabled()) {
                logger.debug("Removing tunnel-capable switch {}" +
                        " from all tunnelManager data-stores. Informing listeners..",
                        HexString.toHexString(dpid));
            }
            informListeners(false, dpid, deltpi.portNum);
        }
    }

    /** An OFPortType.TUNNEL port may be added or deleted some time after a switch
     *  connects to the controller. Or the port may go up/down.
     */
    @Override
    public void switchPortChanged(Long switchId) {
        int foundTunnelPorts = 0;
        Map<Long, IOFSwitch> swmap = controllerProvider.getSwitches();
        IOFSwitch sw = null;
        if (swmap != null) sw = swmap.get(switchId);
        if (sw == null) {
            // Nothing can be done here - switch has gone away!
            // An additional removedSwitch should have also been called and that
            // will take care of the necessary steps to update TunnelManager state and
            // inform listeners
            return;
        }
        for (OFPhysicalPort p : sw.getEnabledPorts()) {
            if (sw.getPortType(p.getPortNumber()) == OFPortType.TUNNEL ||
                sw.getPortType(p.getPortNumber()) == OFPortType.TUNNEL_LOOPBACK) {
                foundTunnelPorts++;
            } else if (p.getName().equals("ovs-br0")) {
                //FIXME : may not care about ovs-br0 anymore
                if (p.getState() == OFPortState.OFPPS_LINK_DOWN.getValue()) {
                    logger.warn("Port State changed on ovs-br0 port");
                }
            }
        }

        // if either tunnel port got deleted then remove tunneling capability
        if (foundTunnelPorts != 2) {
            if (tunnelCapableSwitches.containsKey(switchId)) {
                logger.info("Port Changed: Switch {} is no longer tunnel-capable",
                            HexString.toHexString(switchId));
                removedSwitch(sw);
            } else {
                // noop - switch was already tunnel in-capable
                // ignoring port changed notification: probably some other port
            }
        } else {
            if (!tunnelCapableSwitches.containsKey(switchId)) {
                logger.info("Port Changed: Switch {} is now tunnel-capable",
                            HexString.toHexString(switchId));
                addedSwitch(sw);
            } else {
                // noop - switch was already tunnel capable
                // ignoring port changed notification: probably some other port
            }
        }
    }

    @Override
    public String getName() {
        return "tunnelmanager";
    }

    //*********************
    //   Storage Listener
    //*********************

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(SWITCH_CONFIG_TABLE_NAME)) {
            tunnelHandlingTask.reschedule(TUNNEL_TASK_DELAY,
                    TimeUnit.MILLISECONDS);
        }

    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(SWITCH_CONFIG_TABLE_NAME)) {
            lastConfTunnelSwitches = confTunnelSwitches;
            tunnelHandlingTask.reschedule(TUNNEL_TASK_DELAY,
                    TimeUnit.MILLISECONDS);
        }
    }

    public void setStorageSource(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }

    //*********************
    //   OFMessage Listener
    //*********************

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public org.sdnplatform.core.IListener.Command
            receive(IOFSwitch sw, OFMessage msg, ListenerContext cntx) {
        switch (msg.getType()) {
            case VENDOR:
                handleVendorMessage((OFVendor)msg, sw.getId(), msg.getXid());
                break;
            case ERROR:
                handleErrorMessage((OFError)msg, sw.getId(), msg.getXid());
                break;
            default:
                break;
        }
        return Command.CONTINUE;
    }

    //***********************************
    //  Internal Methods - Config Related
    //***********************************

    protected void readSwitchConfig() throws StorageException {
        if (logger.isDebugEnabled()) logger.debug("reading switch config");
        IResultSet swConfigResultSet = storageSource.executeQuery(
                SWITCH_CONFIG_TABLE_NAME, new String[] {SWITCH_DPID,
                        TUNNEL_ENABLED_OR_NOT}, null, null);
        HashMap<String, String> lct = new HashMap<String, String>();
        while (swConfigResultSet.next()) {
            String dpid = swConfigResultSet.getString(SWITCH_DPID);
            String tunnel = swConfigResultSet.getString(TUNNEL_ENABLED_OR_NOT);
            lct.put(dpid, tunnel);
            //logger.info("TUNNELS: dpid {} enornot {}", dpid, tunnel);
        }
        confTunnelSwitches = lct;

        // determine which rows were deleted if any
        ArrayList<String> deletedEnabled=null, deletedDisabled=null;
        if (lastConfTunnelSwitches != null &&
            confTunnelSwitches.size() < lastConfTunnelSwitches.size()) {
            for (Iterator<String> iter =
                lastConfTunnelSwitches.keySet().iterator(); iter.hasNext();) {
                String id = iter.next();
                if (confTunnelSwitches.get(id) == null) {
                    // found deleted row, determine if enabled or disabled
                    // before deletion
                    if (lastConfTunnelSwitches.get(id).equals("enabled")) {
                        if (deletedEnabled == null)
                            deletedEnabled = new ArrayList<String>();
                        deletedEnabled.add(id);
                    } else if (lastConfTunnelSwitches.get(id).equals("disabled")){
                        if (deletedDisabled == null)
                            deletedDisabled = new ArrayList<String>();
                        deletedDisabled.add(id);
                    }
                }
            }
        }
        reconcileTunnelSwitches(deletedEnabled, deletedDisabled);
    }

    private void reconcileTunnelSwitches(ArrayList<String> deletedEnabled,
            ArrayList<String> deletedDisabled) {
        // need to reconcile configuration with existing set of tunnelSwitches

        // Logic:
        // If a tunnel capable switch is not in configuration learned from storage,
        // it should have default behavior - tunneling will be enabled.
        if (deletedEnabled != null) {
            for (int i=0; i<deletedEnabled.size(); i++) {
                //check if 'enabled' was removed on a switch capable of tunneling
                long deldpid = HexString.toLong(deletedEnabled.get(i));
                if (!tunnelCapableSwitches.containsKey(deldpid)) {
                    logger.error("Tunneling configuration went from enabled->default" +
                            "on a switch {} without tunneling capability",
                            HexString.toHexString(deldpid));
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Tunneling configuration went from enabled->" +
                                "default on switch {}",
                                HexString.toHexString(deldpid));
                    }
                    processSwitchEnabled(deldpid);
                }
            }
        }

        if (deletedDisabled != null) {
            for (int i=0; i<deletedDisabled.size(); i++) {
                //check if 'disabled' was removed on a switch capable of tunneling
                long deldpid = HexString.toLong(deletedDisabled.get(i));
                if (tunnelCapableSwitches.containsKey(deldpid)) {
                    // defaults to tunnels being enabled
                    if (logger.isDebugEnabled()) {
                        logger.debug("Tunneling configuration went from disabled->" +
                                "default on switch {}. Enabling..",
                                HexString.toHexString(deldpid));
                    }
                    processSwitchEnabled(deldpid);
                } else {
                    logger.error("Tunneling configuration went from disabled->default" +
                            "on a switch {} without tunneling capability",
                            HexString.toHexString(deldpid));
                }
            }
        }

        // More Logic:
        // If an OVS is in configuration learned from storage, the switch may
        // be in enabled/disabled/default for tunneling. In such cases,
        // we need to update TopologyManager of such changes
        if (confTunnelSwitches == null) return;
        for (Iterator<Entry<String, String>> iter =
            confTunnelSwitches.entrySet().iterator(); iter.hasNext();) {
            Entry<String, String> e = iter.next();
            long confdpid = HexString.toLong(e.getKey());
            if (tunnelCapableSwitches.containsKey(confdpid)) {
                if (e.getValue().equals("disabled")) {
                    processSwitchDisabled(confdpid);
                } else {
                    // enabled or default
                    processSwitchEnabled(confdpid);
                }
            } else {
                logger.warn("Tunneling configuration {} on switch {} " +
                        "which is not currently capable of tunneling",
                        e.getValue(), HexString.toHexString(confdpid));
            }
        }

    }

    private void processSwitchDisabled(long dpid) {
        if (tunnelCapableSwitches.containsKey(dpid)) {
          tunnelCapableSwitches.get(dpid).active = false;
          tunnelCapableSwitches.get(dpid).enabled = "disabled";
          outstandingTunnelIP.remove(dpid);
          if (logger.isDebugEnabled()) {
              logger.debug("Disabling switch {} for tunneling",
                           HexString.toHexString(dpid));
          }
          informListeners(false, dpid, tunnelCapableSwitches.get(dpid).portNum);
        } else {
            logger.warn("attempt to disable tunneling on a switch {}" +
                    " not currently capable of tunneling - no OFPortType.TUNNEL port",
                    HexString.toHexString(dpid));
        }
    }

    private void processSwitchEnabled(long dpid) {
        if (tunnelCapableSwitches.containsKey(dpid)) {
            TunnelPortInfo tp = tunnelCapableSwitches.get(dpid);
            if (tp.active) {
                // already active - ensure correct enabled state
                tp.enabled = "yes";
            } else if (!tp.active && tp.ipv4Addr != 0) {
                tp.active = true;
                tp.enabled = "yes";
                tunnIPToDpid.putIfAbsent(tp.ipv4Addr, dpid);
                tunnMACToDpid.putIfAbsent(Ethernet.toLong(tp.macAddr), dpid);
                informListeners(true, dpid, tp.portNum);
                if (logger.isDebugEnabled()) {
                    logger.debug("Switch {} ACTIVE for tunneling",
                                 HexString.toHexString(dpid));
                }
            } else {
                tp.enabled = "yes";
                // need to poll for tunnelIP
                if (logger.isDebugEnabled()) logger.debug("tunnel enabled " +
                        "on tunnel capable switch {}. Polling for tunnelIP...",
                        HexString.toHexString(dpid));
                outstandingTunnelIP.add(dpid);
            }
        } else {
            logger.error("attempt to enable tunneling on a switch {}" +
                    "not currently capable of tunneling - no OFPortType.TUNNEL port",
                    HexString.toHexString(dpid));
        }
    }

    //**************************************
    //  Internal Methods - Tunnel IP Related
    //**************************************

    protected void getTunnelIPs() {
        Iterator<Long> iter = outstandingTunnelIP.iterator();
        while (iter.hasNext()) {
            long dpid = iter.next();
            getTunnelIP(dpid, false);
        }
    }

    protected void getTunnelIP(long dpid, boolean refresh) {
        IOFSwitch sw = controllerProvider.getSwitches().get(dpid);
        if (sw.attributeEquals(IBetterOFSwitch.SUPPORTS_BSN_SET_TUNNEL_DST_ACTION,
                               true)) {
            getTunnelIPFromOF(sw);
        } else if (sw.attributeEquals(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP,
                                      true)) {
            logger.warn("Using OVSDB to get tunnel-endpoint-IP, but standard OVS" +
                    " may not support the ACTION that sets dst-tunnel-IP on-the-fly.");
            // FIXME: in standard OVS the assumption is that the tunnel endpoint
            // is ovs-br0 (with port number OFPP_LOCAL), because JSON-RPC with OVSDB
            // does not currently tell us which port is the tunnel-endpoint port
            byte[] tmac = sw.getPort("ovs-br0").getHardwareAddress();
            tunnMACToDpid.putIfAbsent(Ethernet.toLong(tmac), dpid);
            tunnelCapableSwitches.get(dpid).macAddr = tmac;
            // standard OVS - use OVSDB to get tunnelIP
            getTunnelIPFromOVSDB(sw, refresh);
        } else {
            logger.error("Unknown tunneling switch {}", HexString.toHexString(dpid));
        }
    }

    protected void getTunnelIPFromOVSDB(IOFSwitch sw, boolean refresh) {
        long dpid = sw.getId();
        if (logger.isDebugEnabled()) {
            logger.debug("getting tunnel-IP from OVSDB for sw: {} ",
                    HexString.toHexString(dpid));
        }
        IOVSDB tsw =  ovsDBManager.getOVSDB(dpid);
        if (tsw  == null) {
            tsw = ovsDBManager.addOVSDB(sw.getId());
        }
        String ipAddr = tsw.getTunnelIPAddress(refresh);
        if (ipAddr != null) {
            int ip = IPv4.toIPv4Address(ipAddr);
            // FIXME: in standard OVS the assumption is that the tunnel endpoint
            // is ovs-br0 (with port number OFPP_LOCAL), because JSON-RPC with OVSDB
            // does not currently tell us which port is the tunnel-endpoint port
            // It also does not tell us the IP addr mask.
            registerIPandMacAddr(dpid, ip,
                                 sw.getPort("ovs-br0").getHardwareAddress(), 0);
        }
    }

    protected void registerIPandMacAddr(long dpid, int ipAddr, byte[] macAddr,
                                        int ipAddrMask) {
        // while retrieving the tunnel IP, the switch may have been disabled
        // for tunneling, in which case the outstandingTunnelIP set would
        // no longer contain the switch-dpid, and 'remove' would return false
        boolean stillEnabled = outstandingTunnelIP.remove(dpid);
        TunnelPortInfo tp = tunnelCapableSwitches.get(dpid);
        if (tp != null) {
            tp.ipv4Addr = ipAddr;
            tp.macAddr = macAddr;
            tp.ipv4AddrMask = ipAddrMask;
            registerTunnelIPSubnet(ipAddr, ipAddrMask);
            tunnIPToDpid.putIfAbsent(ipAddr, dpid);
            tunnMACToDpid.putIfAbsent(Ethernet.toLong(macAddr), dpid);
            if (stillEnabled) {
                logger.info("Tunneling ACTIVE on sw {} with ip {} - Informing listeners..",
                            HexString.toHexString(dpid),
                            IPv4.fromIPv4Address(ipAddr));
                tp.active = true;
                informListeners(true, dpid, tp.portNum);
            }
        }
    }

    protected void registerTunnelIPSubnet(int ipAddr, int ipAddrMask) {
        if (!tunnelIPSubnets.containsKey(ipAddrMask)) {
            Set<Integer> si = Collections.newSetFromMap(
                                  new ConcurrentHashMap<Integer,Boolean>());
            tunnelIPSubnets.putIfAbsent(ipAddrMask, si);
        }
        int subnet = ipAddr & ipAddrMask;
        tunnelIPSubnets.get(ipAddrMask).add(subnet);
    }

    protected void getTunnelIPFromOF(IOFSwitch sw) {
        long dpid = sw.getId();
        int xid = 0;
        if (logger.isDebugEnabled()) {
            logger.debug("getting tunnel-IP using OF Extn. for sw: {} ",
                    HexString.toHexString(dpid));
        }
        try {
            xid = sendIPRequestMsg(sw);
        } catch (IOException e) {
            logger.warn("Failed to send interface IP request message " +
                    "to switch {}: {}.", sw, e);
            return;
        }
        Set<Integer> pendingSet = sentIPRequests.get(sw.getId());
        if (pendingSet == null) {
            pendingSet = new HashSet<Integer>();
            pendingSet.add(xid);
            sentIPRequests.put(dpid, pendingSet);
        } else {
            pendingSet.add(xid);
        }

    }


    protected int sendIPRequestMsg(IOFSwitch sw) throws IOException {
        int xid = sw.getNextTransactionId();
        OFVendor ipRequest = (OFVendor) controllerProvider.getOFMessageFactory()
                .getMessage((OFType.VENDOR));
        ipRequest.setXid(xid);
        ipRequest.setVendor(OFBigSwitchVendorData.BSN_VENDOR_ID);
        OFInterfaceIPRequestVendorData ipReqData = new
                OFInterfaceIPRequestVendorData();
        ipRequest.setVendorData(ipReqData);
        ipRequest.setLength((short) (OFVendor.MINIMUM_LENGTH + ipReqData.getLength()));

        // Send it to the switch
        List<OFMessage> msgList =
                Collections.singletonList((OFMessage)ipRequest);
        sw.write(msgList, new ListenerContext());

        return xid;
    }

    private void handleVendorMessage(OFVendor vmsg, long dpid, int xid) {
        int vendor = vmsg.getVendor();
        if (vendor == OFBigSwitchVendorData.BSN_VENDOR_ID) {
            OFBigSwitchVendorData bsnVendorData = (OFBigSwitchVendorData)
                    vmsg.getVendorData();
            int vdataType = bsnVendorData.getDataType();
            switch (vdataType) {
                case OFInterfaceIPReplyVendorData.BSN_GET_INTERFACE_IP_REPLY:
                    OFInterfaceIPReplyVendorData ipReplyData =
                        (OFInterfaceIPReplyVendorData) bsnVendorData;
                    processIPReplyData(ipReplyData, dpid, xid);
                    break;
                case OFInterfaceIPRequestVendorData.BSN_GET_INTERFACE_IP_REQUEST:
                    break;
                default:
                    if (logger.isDebugEnabled()) {
                        logger.debug("Recieved Vendor message subtype {}. " +
                                "Ignoring...", vdataType);
                    }
                    break;
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Recieved Vendor message from unknown vendor {}. " +
                        "Ignoring...", vendor);
            }
        }

    }

    protected void handleErrorMessage(OFError err, long dpid, int xid) {
        short errorType = err.getErrorType();
        short errorCode = err.getErrorCode();
        if (errorType == OFError.OFErrorType.OFPET_BAD_REQUEST.getValue() &&
                errorCode == (short)OFError.OFBadRequestCode.OFPBRC_EPERM.ordinal()) {
            Set<Integer> xids = sentIPRequests.get(dpid);

            if (xids != null && xids.contains(xid)) {
                logger.warn("Switch {} not configured with tunnel-endpoint " +
                        "information (missing /etc/bsn_tunnel_interface file)",
                        HexString.toHexString(dpid));
                xids.remove(xid);
                return;
            }
        } else {
            // ignoring all other error messages
        }
    }

    protected void processIPReplyData(OFInterfaceIPReplyVendorData ipReplyData,
                                      long dpid, int xid) {
        List<OFInterfaceVendorData> intfIPs = ipReplyData.getInterfaces();
        Set<Integer> xids = sentIPRequests.get(dpid);

        if (xids == null || !xids.contains(xid)) {
            logger.warn("Unsolicited OFInterfaceIPReply from switch {}",
                        HexString.toHexString(dpid));
            return;
        }
        xids.remove(xid);
        switch (intfIPs.size()) {
            case 0: // tunnel-endpoint does not have IP address. Keep polling..
                logger.warn("No IP addresses returned from sw: {}",
                            HexString.toHexString(dpid));
                break;
            case 1: // exactly one tunnel-endpoint. Process ..
                OFInterfaceVendorData tunnEnd = intfIPs.get(0);
                String receivedName = tunnEnd.getName();
                if (!receivedName.equals("tun-loopback")) {
                    logger.warn("Received tunnel endpoint IP address has " +
                            "unexpected name {} in sw {}", receivedName,
                            HexString.toHexString(dpid));
                } else {
                    registerIPandMacAddr(dpid, tunnEnd.getIpv4Addr(),
                                         tunnEnd.getHardwareAddress(),
                                         tunnEnd.getIpv4AddrMask());
                }
                break;
            default:
                logger.warn("More than one interface in InterfaceIPReplyMsg " +
                        "from sw {}. Ignoring..", HexString.toHexString(dpid));
                /*
                for (OFInterfaceVendorData intf : intfIPs) {
                    logger.info("{} {} {}", new Object[]{intf.getName(),
                                IPv4.fromIPv4Address(intf.getIpv4Addr()),
                                intf.getHardwareAddress()});
                }
                */
                break;
        }
    }

    //**************************************
    //  Internal Methods - Other
    //**************************************

    protected void informListeners(boolean added, long dpid, short portnum) {
        if (listeners == null) return;
        for (ITunnelManagerListener tml : listeners) {
            if (added)
                tml.tunnelPortActive(dpid, portnum);
            else
                tml.tunnelPortInactive(dpid, portnum);
        }
    }

    /*
    protected void showAllTunnels() { // FIXME
        ArrayList<SwitchTunnelInfo> stil = getAllTunnels();
        for (int i=0; i<stil.size(); i++) {
            SwitchTunnelInfo sti = stil.get(i);
            logger.debug("Switch dpid <managementIP>: {} <{}>", sti.hexDpid,
                    sti.mgmtIpAddr);
            logger.debug("Switch local-tunnel-IP addr: {}",
                    sti.tunnelIPAddr);
            logger.debug("Switch tunnel ports (vta<remoteIP>):");
            if (sti.tunnelPorts != null) {
                for (String p : sti.tunnelPorts) {
                    logger.debug("\t {}", p);
                }
            }
        }
    }
    */

    /**
     * exponentially increasing timer - used when trying to get a tunnel-ip
     * address from a tunnel-capable switch. Starts at 1sec and doubles at each call to
     * a max value of TUNNEL_IP_RETRY_MAX.
     */
    private void incrRetryTimer() {
        if (TUNNEL_IP_RETRY * 2 > TUNNEL_IP_RETRY_MAX) {
            // noop
        } else {
            TUNNEL_IP_RETRY *= 2;
        }
    }


    /**
     * @return the current value of the retry timer
     */
    private int getRetryTimer() {
        return TUNNEL_IP_RETRY;
    }

    /**
     * @return reset the current value of the retry timer
     */
    private void resetRetryTimer() {
        TUNNEL_IP_RETRY = 1000;
    }

    /**
     * used  by tests
     * @param controllerProvider
     */
    public void setControllerProvider(IControllerService
                                      controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    /**
     * used by tests
     * @param ovsDBManager
     */
    public void setOVSDBManager(IOVSDBManagerService ovsDBManager) {
        this.ovsDBManager = ovsDBManager;
    }

    //***************
    // ITunnelManager
    //***************

    /**
     * getTunnelsOnSwitch is used to get current information on tunnels
     * for a particular switch, via CLI/REST-API.
     *
     * @param swDpid dpid of switch for which tunnel information is requested
     * @return SwitchTunnelInfo object containing tunnel information for swDpid
     *         or null if the switch is not connected to the controller.
     */
    @Override
    public SwitchTunnelInfo getTunnelsOnSwitch(long swDpid) {
        TunnelPortInfo tpi = tunnelCapableSwitches.get(swDpid);
        if (tpi != null) {
            SwitchTunnelInfo swinfo = new SwitchTunnelInfo();
            swinfo.dpid = tpi.dpid;
            swinfo.hexDpid = HexString.toHexString(tpi.dpid);
            swinfo.tunnelIPAddr = IPv4.fromIPv4Address(tpi.ipv4Addr);
            if (tpi.loopbackPortNum != -1) {
                IOFSwitch sw = controllerProvider.getSwitches().get(tpi.dpid);
                for (OFPhysicalPort p : sw.getEnabledPorts()) {
                    if (sw.getPortType(p.getPortNumber()) == OFPortType.TUNNEL_LOOPBACK)
                            swinfo.tunnelEndPointIntfName = p.getName();
                }
            }
            swinfo.tunnelCapable = true;

            if (tpi.enabled.equals("") || tpi.enabled.equals("yes"))
                // "" implies that tunnels were enabled by default on this tunnel
                // capable switch. "yes" implies that there were a series of
                // configurations done on the CLI/REST_API that resulted in
                // tunnels being enabled on this switch. For now the two cases
                // are treated the same
                swinfo.tunnelEnabled = true;
            else if (tpi.enabled.equals("disabled"))
                // "disabled" implies that tunneling was explicitly disabled
                // on the switch via the CLI/REST_API
                swinfo.tunnelEnabled = false;

            swinfo.tunnelActive = tpi.active;

            if (swinfo.tunnelActive)
                swinfo.tunnelState = "active";
            else if (swinfo.tunnelEnabled)
                swinfo.tunnelState = "enabled";
            else
                swinfo.tunnelState = "disabled";

            return swinfo;
        } else {
            // the switch may be connected to the controller but may not be
            // tunnel-capable (so tunnelManager does not keep state for this switch)
            // - in this case we should let the caller know that it is incapable
            Map<Long, IOFSwitch> swmap = controllerProvider.getSwitches();
            IOFSwitch sw = null;
            if (swmap != null) sw = swmap.get(swDpid);
            if (sw != null) {
                SwitchTunnelInfo swinfo = new SwitchTunnelInfo();
                swinfo.dpid = swDpid;
                swinfo.hexDpid = HexString.toHexString(swDpid);
                swinfo.tunnelCapable = false;
                swinfo.tunnelEnabled = false;
                swinfo.tunnelActive = false;
                swinfo.tunnelState = "disabled";
                swinfo.tunnelIPAddr = "";
                swinfo.tunnelEndPointIntfName = "";
                return swinfo;
            } else {
                return null;
            }
        }
    }

    /**
     * getAllTunnels returns information on all switches that are connected to the
     * controller, irrespective of whether they are tunnel capable or not.
     * @return ArrayList of SwitchTunnelInfo objects. The ArrayList may be
     *         empty if there are no switches connected to the controller.
     */
    @Override
    public ArrayList<SwitchTunnelInfo> getAllTunnels() {
        ArrayList<SwitchTunnelInfo> swinfolist = new ArrayList<SwitchTunnelInfo>();
        Map<Long, IOFSwitch> swmap = controllerProvider.getSwitches();
        if (swmap == null) return swinfolist;

        for (Object id : swmap.keySet().toArray()) {
            SwitchTunnelInfo sti = getTunnelsOnSwitch(((Long)id).longValue());
            if (sti != null) swinfolist.add(sti);
        }
        return swinfolist;
    }

    /**
     * SwitchTunnelInfo: A public class used to return tunnel information for
     * a switch (in getAllTunnels and getTunnelsOnSwitch methods). It includes
     * the switch dpid; a hexstring version of the dpid; the local-tunnel IP
     * addresses, the interface name for the tunnel-endpoint port, and tunnel
     * state (capable, enabled/disabled or active).
     */
    public class SwitchTunnelInfo {
        public long dpid;
        public String hexDpid;
        public String tunnelIPAddr; // FIXME  - show mask as well
        public String tunnelEndPointIntfName;
        public boolean tunnelCapable;
        public boolean tunnelEnabled;
        public boolean tunnelActive;
        public String tunnelState; // enabled, disabled or active
        public long getDpid() {
            return dpid;
        }
        public String getHexDpid() {
            return hexDpid;
        }
        public String getTunnelIPAddr() {
            return tunnelIPAddr;
        }
        public String getTunnelEndPointIntfName() {
            return tunnelEndPointIntfName;
        }
        public boolean isTunnelCapable() {
            return tunnelCapable;
        }
        public boolean isTunnelEnabled() {
            return tunnelEnabled;
        }
        public boolean isTunnelActive() {
            return tunnelActive;
        }
        public String getTunnelState() {
            return tunnelState;
        }
    }

    @Override
    public boolean isTunnelEndpoint(IDevice dev) {
        if (tunnMACToDpid.get(dev.getMACAddress()) != null)
            return true;
        for (int ipAddr : dev.getIPv4Addresses()) {
            if (tunnIPToDpid.get(ipAddr) != null)
                return true;
        }
        return false;
    }

    @Override
    public boolean isTunnelSubnet(int ipAddress) {
        Iterator<Integer> iter = tunnelIPSubnets.keySet().iterator();
        while (iter.hasNext()) {
            int mask = iter.next();
            int test = ipAddress & mask;
            if (tunnelIPSubnets.get(mask).contains(test)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Integer getTunnelIPAddr(long dpid) {
        TunnelPortInfo tpi = tunnelCapableSwitches.get(dpid);
        if (tpi == null) return null;

        if (tpi.active && tpi.ipv4Addr != 0) {
            return tpi.ipv4Addr;
        } else {
            return null;
        }
    }

    @Override
    public boolean isTunnelActiveByIP(int ipAddr) {
        Long dpid = tunnIPToDpid.get(ipAddr);
        if (dpid == null) return false; // not a tunnel-endpoint
        return isTunnelActiveByDpid(dpid);
    }

    @Override
    public boolean isTunnelActiveByDpid(long dpid) {
        TunnelPortInfo tpi = tunnelCapableSwitches.get(dpid);
        if (tpi != null && tpi.active) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Short getTunnelPortNumber(long dpid) {
        TunnelPortInfo tpi = tunnelCapableSwitches.get(dpid);
        if (tpi != null && tpi.portNum != 0)
            return tpi.portNum;
        else
            return null;
    }

    @Override
    public void updateTunnelIP(long dpid1, long dpid2) {
        getTunnelIP(dpid1, true);
        getTunnelIP(dpid2, true);
    }

    @Override
    public Long getSwitchDpid(int tunnelIPAddress) {
        return tunnIPToDpid.get(tunnelIPAddress);
    }

    @Override
    public boolean isTunnelLoopbackPort(long switchDPID, short port) {
        TunnelPortInfo tpi = tunnelCapableSwitches.get(switchDPID);
        if (tpi != null) {
            if (port == tpi.loopbackPortNum) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Short getTunnelLoopbackPort(long switchDPID) {
        TunnelPortInfo tpi = tunnelCapableSwitches.get(switchDPID);
        if (tpi != null && tpi.loopbackPortNum != -1) {
            return tpi.loopbackPortNum;
        }
        return null;
    }

    @Override
    public void addListener(ITunnelManagerListener l) {
        if (listeners == null)
            listeners = new ArrayList<ITunnelManagerListener>();
        listeners.add(l);
    }

    @Override
    public void removeListener(ITunnelManagerListener l) {
        if (listeners == null) return;
        listeners.remove(l);
    }

    //***************
    // IModule
    //***************

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
            new ArrayList<Class<? extends IPlatformService>>();
        l.add(ITunnelManagerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>,
                IPlatformService> getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m =
            new HashMap<Class<? extends IPlatformService>,
                    IPlatformService>();
        m.put(ITunnelManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
                                            getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IStorageSourceService.class);
        l.add(IControllerService.class);
        l.add(IOVSDBManagerService.class);
        l.add(IRestApiService.class);
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        storageSource =
                context.getServiceImpl(IStorageSourceService.class);
        controllerProvider =
                context.getServiceImpl(IControllerService.class);
        ovsDBManager =
                context.getServiceImpl(IOVSDBManagerService.class);
        restApi =
                context.getServiceImpl(IRestApiService.class);
        threadPool =
                context.getServiceImpl(IThreadPoolService.class);
    }

    @Override
    public void startUp(ModuleContext context) {
        controllerProvider.addOFSwitchListener(this);
        controllerProvider.addOFMessageListener(OFType.VENDOR, this);
        controllerProvider.addOFMessageListener(OFType.ERROR, this);
        controllerProvider.addHAListener(this);
        storageSource.addListener(SWITCH_CONFIG_TABLE_NAME, this);
        OFBigSwitchVendorExtensions.initialize();

        ScheduledExecutorService ses = threadPool.
                                            getScheduledExecutor();
        tunnelHandlingTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                readSwitchConfig();
                if (outstandingTunnelIP.size() > 0) {
                    getTunnelIPs();
                    incrRetryTimer();
                    logger.warn("trying tunnel-IP request again in {} secs",
                            getRetryTimer()/1000);
                    tunnelHandlingTask.reschedule(getRetryTimer(),
                            TimeUnit.MILLISECONDS);
                } else {
                    resetRetryTimer();
                }
            }
        });
    }

    //***************
    // IHAListener
    //***************

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Unknown controller role: {role}",
                explanation="tunnelManager received a notification "
                           + "that the controller changed to a new role that"
                           + "is currently not supported",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    logger.debug("Re-reading tunnels from storage due " +
                            "to HA change from SLAVE->MASTER");
                    tunnelHandlingTask.reschedule(TUNNEL_TASK_DELAY,
                            TimeUnit.MILLISECONDS);
                }
                break;
            case SLAVE:
                logger.debug("Clearing cached tunnel state due to " +
                        "HA change to SLAVE");
                if (confTunnelSwitches != null) confTunnelSwitches.clear();
                if (lastConfTunnelSwitches != null) lastConfTunnelSwitches.clear();
                tunnelCapableSwitches.clear();
                tunnIPToDpid.clear();
                tunnMACToDpid.clear();
                outstandingTunnelIP.clear();
                //ovsDBManager.
                sentIPRequests.clear();
                break;
            default:
                logger.warn("Unknow controller role: {}", newRole);
                break;
        }
    }

    @Override
    public void controllerNodeIPsChanged(
            Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {
        // ignore
    }

}
