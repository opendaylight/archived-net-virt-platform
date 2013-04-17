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

package org.sdnplatform.netvirt.manager.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.sdnplatform.addressspace.BetterEntityClass;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IInfoProvider;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.util.ListenerDispatcher;
import org.sdnplatform.core.util.SingletonTask;
import org.sdnplatform.devicegroup.DeviceGroupMatcher;
import org.sdnplatform.devicegroup.IDeviceGroupMatcher;
import org.sdnplatform.devicegroup.MembershipRule;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceListener;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.flowcache.FCQueryObj;
import org.sdnplatform.flowcache.FlowCacheQueryResp;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.flowcache.IFlowQueryHandler;
import org.sdnplatform.flowcache.IFlowReconcileListener;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.flowcache.OFMatchReconcile;
import org.sdnplatform.flowcache.PendingSwRespKey;
import org.sdnplatform.flowcache.PendingSwitchResp;
import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;
import org.sdnplatform.forwarding.IRewriteService;
import org.sdnplatform.netvirt.core.VNS;
import org.sdnplatform.netvirt.core.VNSInterface;
import org.sdnplatform.netvirt.core.VNS.ARPMode;
import org.sdnplatform.netvirt.core.VNS.BroadcastMode;
import org.sdnplatform.netvirt.core.VNS.DHCPMode;
import org.sdnplatform.netvirt.manager.IVNSInterfaceClassifier;
import org.sdnplatform.netvirt.manager.INetVirtListener;
import org.sdnplatform.netvirt.manager.INetVirtManagerService;
import org.sdnplatform.netvirt.virtualrouting.IVirtualRoutingService;
import org.sdnplatform.netvirt.web.NetVirtWebRoutable;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.tagmanager.ITagListener;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.tagmanager.Tag;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The NetVirt manager component is responsible for maintaining a mapping
 * between devices on the network and the NetVirts and NetVirt interfaces to
 * which they belong. NetVirt manager does not make any forwarding decision:
 * its responsibility is limited to NetVirt ID updates.
 * @author readams
 */
@LogMessageCategory("Network Virtualization")
public class NetVirtManagerImpl implements IModule, IOFMessageListener,
                                       INetVirtManagerService,
                                       IStorageSourceListener, ITagListener,
                                       IFlowReconcileListener,
                                       IFlowQueryHandler, IOFSwitchListener,
                                       IInfoProvider, IHAListener {
    protected static Logger logger = LoggerFactory.getLogger(NetVirtManagerImpl.class);

    // *******************************
    // Constants for accessing storage
    // *******************************

    // Table names
    public static final String VNS_TABLE_NAME = "controller_vns";
    public static final String VNS_INTERFACE_RULE_TABLE_NAME = "controller_vnsinterfacerule";
    public static final String SWITCH_INTERFACE_CONFIG_TABLE_NAME = "controller_switchinterfaceconfig";

    // Column names
    public static final String ID_COLUMN_NAME = "id";
    public static final String ACTIVE_COLUMN_NAME = "active";
    public static final String PRIORITY_COLUMN_NAME = "priority";
    public static final String ORIGIN_COLUMN_NAME = "origin";
    public static final String ADDRESS_SPACE_COLUMN_NAME = "vns_address_space_id";
    public static final String DHCP_CONFIG_MODE_COLUMN_NAME = "dhcp_mode";
    public static final String DHCP_IP_COLUMN_NAME = "dhcp_ip";
    public static final String ARP_CONFIG_MODE_COLUMN_NAME = "arp_mode";
    public static final String BROADCAST_CONFIG_MODE_COLUMN_NAME = "broadcast";
    public static final String VNS_COLUMN_NAME = "vns_id";
    public static final String DESCRIPTION_COLUMN_NAME = "description";
    public static final String MULTIPLE_ALLOWED_COLUMN_NAME = "allow_multiple";
    public static final String VLAN_TAG_ON_EGRESS_COLUMN_NAME = "vlan_tag_on_egress";
    public static final String MAC_COLUMN_NAME = "mac";
    public static final String IP_SUBNET_COLUMN_NAME = "ip_subnet";
    public static final String SWITCH_COLUMN_NAME = "switch";
    public static final String PORTS_COLUMN_NAME = "ports";
    public static final String VLANS_COLUMN_NAME = "vlans";
    public static final String TAGS_COLUMN_NAME = "tags";
    public static final String SWITCH_BROADCAST_IFACE_COLUMN_NAME = "broadcast";
    public static final String SWITCH_IFACE_NAME = "if_name";
    public static final String SWITCH_DPID = "switch_id";

    // Time constants
    protected static final int INTERFACE_AGE_TIME = 1000 * 60 * 60; // 1 hour
    protected static final int INTERFACE_CLEANUP_INTERVAL = 12; // hours
    protected static final int LAST_SEEN_UPDATE_INTERVAL = 1000 * 60 * 5; // 5 minutes
    protected static final int UPDATE_TASK_BATCH_DELAY_MS = 750;
    
    // ********************
    // Service Dependencies
    // ********************

    protected IControllerService controllerProvider;
    protected IDeviceService deviceManager;
    protected IStorageSourceService storageSource;
    protected ITagManagerService tagManager;
    protected IVirtualRoutingService virtualRouting;
    protected IRestApiService restApi;
    protected IFlowCacheService betterFlowCacheMgr;
    protected IFlowReconcileService flowReconcileMgr;
    protected IThreadPoolService threadPool;
    protected IRewriteService rewriteService;

    protected DeviceListenerImpl deviceListener;

    // *****************
    // NetVirt configuration
    // *****************
    

    /**
     * This is the switch dpid, iface name tuple of all configured
     * broadcast interfaces
     */
    public class SwitchInterface {
        String dpid;
        String ifaceName;

        public String getDpid() {
            return dpid;
        }
        public String getIfaceName() {
            return ifaceName;
        }
    }

    /**
     * This class represents the configuration of NetVirt manager that is
     * created / updated / read from storage (NetVirt table and NetVirt interface
     * table). We use "double buffering" to increase performance. We have one
     * instance of ConfigState that contains the currently running config
     * and one that we use to build the configuration while reading from
     * storage.
     * @author gregor
     *
     */
    public static class ConfigState {
        public ConfigState() {
            interfaceRuleMap = new HashMap<String, MembershipRule<VNS>>();
            vnsMap = new ConcurrentHashMap<String, VNS>();
            switchInterfaceRuleMap = new ConcurrentHashMap<Long, List<MembershipRule<VNS>>>();
            interfaceMap = new ConcurrentHashMap<String, VNSInterface>();
            deviceGroupMatchers = new HashMap<String, IDeviceGroupMatcher<VNS>>();
            deviceInterfaceMap = new ConcurrentHashMap<Long, List<VNSInterface>>();
        }

        public void clear() {
            interfaceRuleMap.clear();
            vnsMap.clear();
            switchInterfaceRuleMap.clear();
            interfaceMap.clear();
            deviceGroupMatchers.clear();
            deviceInterfaceMap.clear();
        }

        /**
         * This is the cache for mapping a device to its list of interfaces
         */
        protected Map<Long, List<VNSInterface>> deviceInterfaceMap;

        /**
         * This is the set of VNS that exist in the system, mapping
         * ID to VNS object
         */
        public ConcurrentHashMap<String, VNS> vnsMap;

        /**
         * This is the set of VNS interface rules that exist in the
         * system, mapping ID to VNS interface rule object
         */
        public Map<String,MembershipRule<VNS>> interfaceRuleMap;

        /**
         * This is the set of VNS interface rules that exist in the
         * system, mapping a switch DPID to a VNS interface rule object
         */
        public Map<Long, List<MembershipRule<VNS>>> switchInterfaceRuleMap;

        /**
         * This is the set of VNS interfaces that exist in the
         * system, mapping ID to VNS interface object
         */
        public Map<String,VNSInterface> interfaceMap;

        /**
         * Matches devices to interfaces using the interface rules
         */
        public HashMap<String, IDeviceGroupMatcher<VNS>> deviceGroupMatchers;
    }

    /**
     * The currently active config
     */
    protected ConfigState curConfigState;
    /**
     * The config currently being read / created from storage
     */
    protected ConfigState newConfigState;

    /**
     * This is the set of switch port tuples to which all broadcast
     * messages will be sent.
     */
    protected List<SwitchInterface> confBroadcastIfaces;

    /**
     * This is the set of switch port tuples to which all broadcast
     * messages will be sent.
     */
    protected List<SwitchPort> broadcastSwitchPorts;


    protected static final String CLASSIFIER = "Classifier";
    private ListenerDispatcher<String,IVNSInterfaceClassifier> vnsInterfaceClassifiers;

    private IDeviceGroupMatcher<VNS> getDeviceGroupMatcher(IDevice device) {
        if (device.getEntityClass() == null) return null;

        String name = device.getEntityClass().getName();
        return curConfigState.deviceGroupMatchers.get(name);
    }

    /** The number of times flow query resp handler method was called. */
    protected int flowQueryRespHandlerCallCount;

    /** The last fc query resp. */
    protected FlowCacheQueryResp lastFCQueryResp;

    /**
     * Data structure for pending switch query responses
     */
    protected ConcurrentHashMap<PendingSwRespKey,
                                PendingSwitchResp>pendSwRespMap;

    public FlowCacheQueryResp getLastFCQueryResp() {
        return lastFCQueryResp;
    }

    public int getFlowQueryRespHandlerCallCount() {
        return flowQueryRespHandlerCallCount;
    }

    public List<SwitchInterface> getConfBroadcastIfaces() {
        return confBroadcastIfaces;
    }

    // *****************
    // NetVirt manager state
    // *****************

    /**
     * Lock for reading from storage into newConfigState
     */
    protected Object newConfigLock;

    /**
     *  Lock on NetVirt configuration state.
     *  readLock needs to be held while accessing curConfigState
     *  writeLock needs to be held while switching curConfigState and
     *      newConfigState
     *
     */
    protected ReentrantReadWriteLock configLock;

    /**
     * Asynchronous task for responding to NetVirt configuration changes
     * notifications.
     */
    SingletonTask configUpdateTask;

    // List of INetVirtListeners
    protected List<INetVirtListener> netVirtListeners;


    /* A config property. If this is set to true, we will enable special 
     * treatment for VNS with a non-empty, non-null origin field. If such a
     * VNS is added/modified/deleted we will /only/ reconcile flows from 
     * this NetVirt and not from all other NetVirt with the same or lower priority. 
     * The assumption / contract is that such NetVirt have non-overlapping
     * membership rules
     */
    private boolean nonNullOriginSimpleReconcile;

    public NetVirtManagerImpl() {
    }
    
    /* package private. Only to be used by test code */
    void setNonNullOriginSimpleReconcile(boolean nonNullOriginSimpleReconcile) {
        this.nonNullOriginSimpleReconcile = nonNullOriginSimpleReconcile;
    }

    // ******************
    // IPlatformService
    // ******************

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(INetVirtManagerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m =
        new HashMap<Class<? extends IPlatformService>,
                    IPlatformService>();
        // We are the class that implements the service
        m.put(INetVirtManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IControllerService.class);
        l.add(IDeviceService.class);
        l.add(IStorageSourceService.class);
        l.add(ITagManagerService.class);
        l.add(IRestApiService.class);
        l.add(IVirtualRoutingService.class);
        l.add(IFlowCacheService.class);
        l.add(IFlowReconcileService.class);
        l.add(IThreadPoolService.class);
        l.add(IRewriteService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        Map<String, String> configOptions = context.getConfigParams(this);

        String option = configOptions.get("nonnulloriginsimplereconcile");
        if (option != null && option.equalsIgnoreCase("false")) {
            nonNullOriginSimpleReconcile = false;
            logger.info("Disabled special treatmeant of non-null origin for " +
                        "flow reconcile");
        } else {
            nonNullOriginSimpleReconcile = true;
            logger.info("Enabled special treatmeant of non-null origin for " +
                        "flow reconcile");
        }

        controllerProvider =
            context.getServiceImpl(IControllerService.class);
        deviceManager =
            context.getServiceImpl(IDeviceService.class);
        storageSource =
            context.getServiceImpl(IStorageSourceService.class);
        tagManager =
            context.getServiceImpl(ITagManagerService.class);
        restApi =
            context.getServiceImpl(IRestApiService.class);
        virtualRouting =
            context.getServiceImpl(IVirtualRoutingService.class);
        betterFlowCacheMgr =
                context.getServiceImpl(IFlowCacheService.class);
        flowReconcileMgr =
                context.getServiceImpl(IFlowReconcileService.class);
        threadPool =
                context.getServiceImpl(IThreadPoolService.class);
        rewriteService = context.getServiceImpl(IRewriteService.class);

        // Init internal data structures
        flowQueryRespHandlerCallCount = 0;

        configLock = new ReentrantReadWriteLock();
        newConfigLock = new Object();

        curConfigState = new ConfigState();
        broadcastSwitchPorts = new ArrayList<SwitchPort>();
        confBroadcastIfaces = new ArrayList<SwitchInterface>();
        configLock = new ReentrantReadWriteLock();
        vnsInterfaceClassifiers = new ListenerDispatcher<String, IVNSInterfaceClassifier>();
        netVirtListeners = new ArrayList<INetVirtListener>();

        deviceListener = new DeviceListenerImpl();
    }

    @Override
    public void startUp(ModuleContext context) {
        restApi.addRestletRoutable(new NetVirtWebRoutable());

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        configUpdateTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                readVNSConfigFromStorage();
            }
        });

        storageSource.createTable(VNS_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VNS_TABLE_NAME, ID_COLUMN_NAME);
        storageSource.createTable(VNS_INTERFACE_RULE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(VNS_INTERFACE_RULE_TABLE_NAME, ID_COLUMN_NAME);
        storageSource.createTable(SWITCH_INTERFACE_CONFIG_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(SWITCH_INTERFACE_CONFIG_TABLE_NAME, ID_COLUMN_NAME);

        storageSource.addListener(VNS_TABLE_NAME, this);
        storageSource.addListener(VNS_INTERFACE_RULE_TABLE_NAME, this);
        storageSource.addListener(SWITCH_INTERFACE_CONFIG_TABLE_NAME, this);

        controllerProvider.addOFMessageListener(OFType.PACKET_IN, this);
        controllerProvider.addOFMessageListener(OFType.STATS_REPLY, this);
        controllerProvider.addOFSwitchListener(this);
        controllerProvider.addHAListener(this);
        controllerProvider.addInfoProvider("summary", this);

        flowReconcileMgr.addFlowReconcileListener(this);

        tagManager.addListener(this);
        deviceManager.addListener(this.deviceListener);

        readVNSConfigFromStorage();
        readSwitchInterfaceConfig();
    }

    @Override
    public void addNetVirtListener(INetVirtListener listener) {
        if (listener != null)
            netVirtListeners.add(listener);
    }

    @Override
    public void removeNetVirtListener(INetVirtListener listener) {
        if (listener != null)
            netVirtListeners.remove(listener);
    }

    protected List<INetVirtListener> getNetVirtListener() {
        return Collections.unmodifiableList(netVirtListeners);
    }

    @Override
    public void addVNSInterfaceClassifier(IVNSInterfaceClassifier classifier) {
        vnsInterfaceClassifiers.addListener(CLASSIFIER, classifier);
    }

    // ******************
    // IOFMessageListener
    // ******************

    @Override
    public IOFMessageListener.Command receive(IOFSwitch sw, OFMessage msg, ListenerContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }

    @Override
    public String getName() {
        return "netVirtmanager";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return name.equals("devicemanager");
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    // ***********
    // INetVirtManager
    // ***********

    @Override
    public Iterator<VNS> getAllVNS() {
        configLock.readLock().lock();
        try {
            return curConfigState.vnsMap.values().iterator();
        } finally {
            configLock.readLock().unlock();
        }
    }

    @Override
    public VNS getVNS(String name) {
        configLock.readLock().lock();
        try {
            return curConfigState.vnsMap.get(name);
        } finally {
            configLock.readLock().unlock();
        }
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Failed to assign a VNS to device {device} {exception}",
                explanation="Could not assign a VNS to the device.",
                recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    public List<VNSInterface> getInterfaces(IDevice d) {
        List<VNSInterface> ifaces = null;
        boolean cachemiss = false;

        configLock.readLock().lock();
        try {
            // Always check with registered VNSInterfaceClassifiers and union the results.
            ifaces = classifyUnknownDevice(d);
            if (logger.isTraceEnabled()) {
                if (ifaces != null) {
                    for (VNSInterface iface : ifaces) {
                        logger.trace("Registered classifier results: {} matches interface {}",
                                     d.getMACAddressString(), iface.toString());
                    }
                } else {
                    logger.trace("Registered classifier results: {} matches null interface",
                                 d.getMACAddressString());
                }
            }

            // Check cached interfaces for the device
            List<VNSInterface> tmpIfaces = curConfigState
                    .deviceInterfaceMap.get(d.getDeviceKey());
            cachemiss = (tmpIfaces == null);
            if (cachemiss) {
                try {
                    tmpIfaces = matchDevice(d);
                } catch (Exception e) {
                    logger.error("Failed to assign a VNS to device {}: {}", d, e);
                }
            }

            // Merge the two sets of vnsInterfaces together.
            if (ifaces != null) {
                if (tmpIfaces != null && tmpIfaces.size() > 0) {
                    ifaces.addAll(tmpIfaces);
                }
            } else {
                ifaces = tmpIfaces;
            }

            if (ifaces == null || ifaces.size() == 0) {
                // If there's no assignment, assign to the default interface on
                // the default VNS
                VNSInterface defIface =
                        getIfaceFromName(
                                new String[] {"default", d.getMACAddressString()},
                                null, d, null);
                tmpIfaces = new ArrayList<VNSInterface>(1);
                tmpIfaces.add(defIface);
                ifaces = tmpIfaces;
            }

            if (cachemiss && tmpIfaces != null) {
                // Cache result
                curConfigState.deviceInterfaceMap.put(d.getDeviceKey(), tmpIfaces);
                for (VNSInterface b : tmpIfaces) {
                    b.getParentVNS().addDevice(d);
                }

                if (logger.isDebugEnabled()) {
                    StringBuffer sb = new StringBuffer();
                    sb.append("VNS interface(s) assigned: ");
                    sb.append(d.getEntityClass().getName());
                    sb.append("::");
                    sb.append(d.getMACAddressString() + " [");
                    for (VNSInterface b : tmpIfaces) {
                        sb.append(b.getParentVNS().getName() + ":");
                        sb.append(b.getName() + ",");
                    }
                    sb.append("]");
                    logger.debug(sb.toString());
                }
            }

            // Update timestamps on ifaces
            Date currentTime = new Date();
            for (VNSInterface i : ifaces) {
                i.setLastSeen(currentTime);
            }
        } finally {
            configLock.readLock().unlock();
        }

        if (logger.isTraceEnabled()) {
            logger.trace("All getInterface results for {} :", d.getMACAddressString());
            for (VNSInterface iface : ifaces) {
                logger.trace("{} matches interface {}", d.getMACAddressString(), iface.toString());
            }
        }
        return ifaces;
    }

    protected List<VNSInterface> classifyUnknownDevice(IDevice d) {
        if (d == null || d.getEntityClass() == null) return null;

        List<IVNSInterfaceClassifier> listeners = vnsInterfaceClassifiers.getOrderedListeners();
        List<VNSInterface> vnsInterfaces = null;

        if (listeners != null) {
            vnsInterfaces = new ArrayList<VNSInterface>();
            for (IVNSInterfaceClassifier listener : listeners) {
                List<VNSInterface> interfaces = listener.classifyDevice(d);
                if (interfaces != null && interfaces.size() > 0) {
                    vnsInterfaces.addAll(interfaces);
                }
            }
        }

        if (vnsInterfaces == null || vnsInterfaces.size() == 0) {
            return null;
        } else {
            return vnsInterfaces;
        }
    }

    protected List<VNSInterface> classifyUnknownDevice(String addressSpace,
                                             Long deviceMac,
                                             Short deviceVlan,
                                             Integer deviceIpv4,
                                             SwitchPort switchPort) {
        List<IVNSInterfaceClassifier> listeners = vnsInterfaceClassifiers.getOrderedListeners();
        List<VNSInterface> vnsInterfaces = null;

        if (listeners != null) {
            vnsInterfaces = new ArrayList<VNSInterface>();
            for (IVNSInterfaceClassifier listener : listeners) {
                List<VNSInterface> interfaces =
                        listener.classifyDevice(addressSpace,
                                                deviceMac,
                                                deviceVlan,
                                                deviceIpv4,
                                                switchPort);
                if (interfaces != null && interfaces.size() > 0) {
                    vnsInterfaces.addAll(interfaces);
                }
            }
        }

        if (vnsInterfaces == null || vnsInterfaces.size() == 0) {
            return null;
        } else {
            return vnsInterfaces;
        }
    }

    @Override
    public VNSInterface getInterface(String name) {
        configLock.readLock().lock();
        try {
            return curConfigState.interfaceMap.get(name);
        } finally {
            configLock.readLock().unlock();
        }
    }

    @Override
    public Iterator<VNSInterface> getAllInterfaces() {
        configLock.readLock().lock();
        try {
            return curConfigState.interfaceMap.values().iterator();
        } finally {
            configLock.readLock().unlock();
        }
    }

    @Override
    public List<SwitchPort> getBroadcastSwitchPorts() {
        return broadcastSwitchPorts;
    }

    // *******************
    // IDeviceListener
    // *******************

    class DeviceListenerImpl implements IDeviceListener {
        @Override
        public void deviceAdded(IDevice device) {
            // Don't care
            return;
        }

        @Override
        public void deviceRemoved(IDevice device) {
            clearCachedDeviceState(device.getDeviceKey());
        }

        @Override
        public void deviceMoved(IDevice device) {
            // Remove cached device state if we have attachment point rules

            IDeviceGroupMatcher<VNS> deviceGroupMatcher =
                getDeviceGroupMatcher(device);

            if (deviceGroupMatcher == null) return;

            if (deviceGroupMatcher.hasSwitchPortRules() ||
                    deviceGroupMatcher.hasTagRules()) {
                clearCachedDeviceState(device.getDeviceKey());
            }
        }

        @Override
        public void deviceIPV4AddrChanged(IDevice device) {
            // Remove cached device state if we have ip address rules
            IDeviceGroupMatcher<VNS> deviceGroupMatcher =
                getDeviceGroupMatcher(device);

            if (deviceGroupMatcher == null) return;

            if (deviceGroupMatcher.hasIpSubnetRules()) {
                clearCachedDeviceState(device.getDeviceKey());
            }
        }

        @Override
        public void deviceVlanChanged(IDevice device) {
            clearCachedDeviceState(device.getDeviceKey());
        }

        @Override
        public String getName() {
            return NetVirtManagerImpl.this.getName();
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

    // ***************
    // IFlowReconcileListener
    // ***************

    @Override
    public Command reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList) {
        for (OFMatchReconcile ofm : ofmRcList) {
            IFlowCacheService.fcStore.put(ofm.cntx,
                IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, "netVirt");
            if (logger.isTraceEnabled()) {
                logger.trace("Reconciling flow: match={}",
                             ofm.ofmWithSwDpid.getOfMatch());
            }
            annotateDeviceVNSInterfaces (ofm.cntx, ofm.ofmWithSwDpid.getOfMatch());
        }
        return Command.CONTINUE;
    }

    // *****************
    // IFlowQueryHandler
    // *****************

    @Override
    public void flowQueryRespHandler(FlowCacheQueryResp flowResp) {
        flowQueryRespHandlerCallCount++;
        lastFCQueryResp = flowResp;
        if (logger.isTraceEnabled()) {
            logger.trace("Executing flowQueryRespHandler {} flowCnt={}",
                                flowResp.toString(),
                                lastFCQueryResp.qrFlowCacheObjList.size());
        }

        flowReconcileMgr.flowQueryGenericHandler(flowResp);
    }

    // **********************
    // IStorageSourceListener
    // **********************

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(SWITCH_INTERFACE_CONFIG_TABLE_NAME))
            readSwitchInterfaceConfig();
        else
            queueConfigUpdate();
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (tableName.equals(SWITCH_INTERFACE_CONFIG_TABLE_NAME))
            readSwitchInterfaceConfig();
        else
            queueConfigUpdate();
    }

    // **********************
    // ITagListener
    // **********************

    @Override
    public void tagAdded(Tag tag) {
        // Noop
    }

    @Override
    public void tagDeleted(Tag tag) {
        // Noop
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagListener#
     * tagDevicesReMapped(java.util.Iterator)
     */
    @Override
    public void tagDevicesReMapped(Iterator<? extends IDevice> devices) {
        if (logger.isTraceEnabled())
            logger.trace("Devices got remapped to tags");

        while (devices.hasNext()) {
            IDevice d = devices.next();

            IDeviceGroupMatcher<VNS> deviceGroupMatcher =
                getDeviceGroupMatcher(d);
            if (deviceGroupMatcher == null) continue;

            if (!deviceGroupMatcher.hasTagRules()) {
                continue;
            }
            if (logger.isTraceEnabled()) {
                logger.trace("This device got remapped - {}", d.getMACAddressString());
            }
            clearCachedDeviceState(d.getDeviceKey());
            flowReconcileMgr.updateFlowForDestinationDevice(d,
                             this,
                             FCQueryEvType.DEVICE_PROPERTY_CHANGED);
            flowReconcileMgr.updateFlowForSourceDevice(d,
                             this,
                             FCQueryEvType.DEVICE_PROPERTY_CHANGED);
        }
    }

    // *************
    // IInfoProvider
    // *************

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();
        configLock.readLock().lock();
        try {
            info.put("# VNSes", curConfigState.vnsMap.size());
            info.put("# VNS Interfaces", curConfigState.interfaceMap.size());
        } finally {
            configLock.readLock().unlock();
        }


        return info;
    }

    // ***************
    // Private methods
    // ***************

    /**
     * Queue a task to update the configuration state
     */
    protected void queueConfigUpdate() {
        configUpdateTask.reschedule(UPDATE_TASK_BATCH_DELAY_MS,
                                    TimeUnit.MILLISECONDS);
    }

    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="VNS {vns}: Invalid DHCP server IP {ip}, " +
                        "reverting to {mode}",
                explanation="An invalid IP DHCP server IP is present in the " +
                        "configuration for this VNS",
                recommendation="Correct the invalid DHCP server IP"),
       @LogMessageDoc(level="INFO",
                message="VNS {vns}: DHCP Manager mode {mode}, server {ip}",
                explanation="This displays the DHCP Manager mode " +
                    "configuration for this VNS")
    })
    private void setDhcpConfig(VNS b, String mode, String ip) {
        int dhcpIp = 0;
        try {
            if (ip != null)
                dhcpIp = IPv4.toIPv4Address(ip);
        } catch (IllegalArgumentException e) {
            mode = "flood-if-unknown";
            dhcpIp = 0;
            ip = null;
            logger.error("VNS {}: Invalid DHCP server IP {}, reverting to {}",
                         ip, mode);
        }

        b.setDhcpIp(dhcpIp);
        b.setDhcpManagerMode(DHCPMode.fromString(mode));
        logger.info("VNS {}: DHCP Manager mode {}, server {}",
                    new Object[] {b.getName(),
                                  b.getDhcpManagerMode(),
                                  ip == null ? "not specified" : ip});
    }

    @LogMessageDoc(level="INFO",
            message="VNS {vns}: ARP Manager setting is: {mode}",
            explanation="This displays the ARP Manager mode " +
                "configuration for this VNS")
    private void setArpConfig(VNS b, String setting) {
        b.setArpManagerMode(ARPMode.fromString(setting));
        logger.info("VNS {}: ARP Manager setting is: {}",
                    b.getName(), b.getArpManagerMode());
    }

    @LogMessageDoc(level="INFO",
            message="VNS {vns}: Broadcast setting is: {mode}",
            explanation="This displays the general broadcast packet " +
                    "mode for this VNS.")
    private void setBroadcastConfig(VNS b, String setting) {
        b.setBroadcastMode(BroadcastMode.fromString(setting));
        logger.info("VNS {}: Broadcast setting is: {}",
                    b.getName(), b.getBroadcastMode());
    }

    /**
     * Read the broadcast interfaces from switch config.
     * @throws StorageException
     */
    protected void readSwitchInterfaceConfig() throws StorageException {
        IResultSet swIfaceConfigResultSet;
        // try multiple times as work-around for BSC-2928;
        //    see Jira BSC-2928 comment 09/Dec/12 1:01 PM
        int trial = 0;
        while (true) {
            trial++;
            try {
                swIfaceConfigResultSet =
                        storageSource.executeQuery(SWITCH_INTERFACE_CONFIG_TABLE_NAME,
                                new String[]{SWITCH_DPID, SWITCH_IFACE_NAME,
                                SWITCH_BROADCAST_IFACE_COLUMN_NAME},
                                null, null);
                break;
            } catch (StorageException e) {
                if (trial > 15) {
                    throw e;
                }
                logger.warn("retry " + trial
                            + ": readSwitchInterfaceConfig encountered StorageExcption "
                            + e.getMessage());
                try {
                    Thread.sleep((trial < 6) ? 1000 : 2000);
                } catch (InterruptedException e2) {
                    logger.warn("sleep interrupted");
                }
            }
        }
        List<SwitchInterface> lsi = new ArrayList<SwitchInterface>();

        while (swIfaceConfigResultSet.next()) {

            boolean broadcast =
                    swIfaceConfigResultSet.getBoolean(SWITCH_BROADCAST_IFACE_COLUMN_NAME);

            if (broadcast) {
                SwitchInterface si = new SwitchInterface();
                si.dpid = swIfaceConfigResultSet.getString(SWITCH_DPID);
                si.ifaceName = swIfaceConfigResultSet.getString(SWITCH_IFACE_NAME);
                lsi.add(si);
            }
        }

        confBroadcastIfaces = lsi;
        updateBroadcastSwitchPorts();
    }

    /**
     *  Updates the broadcast switch ports whenever a switch joins, leaves, or the
     *  config for broadcast interfaces is changed.
     */
    protected void updateBroadcastSwitchPorts() {
        List<SwitchPort> lspt = new ArrayList<SwitchPort>();

        for(SwitchInterface si : confBroadcastIfaces) {
            Long dpid = HexString.toLong(si.dpid);
            IOFSwitch sw = controllerProvider.getSwitches().get(dpid);
            if (sw != null) {
                OFPhysicalPort p = sw.getPort(si.getIfaceName());
                if (p!=null && sw.portEnabled(p))
                    lspt.add(new SwitchPort(dpid, p.getPortNumber()));
            }
        }

        broadcastSwitchPorts = lspt;
    }

    private void triggerFlowReconciliation (String vnsName) {

        /* NetVirt Manager triggers the flow reconciliation by submitting
         * flow query to the flow cache. The flows are actually reconciled
         * in virtual routing.
         */
        FCQueryObj fcQueryObj = new FCQueryObj(this,
                                                   vnsName,
                                                   null,   // null vlan
                                                   null,   // null srcDevice
                                                   null,   // null destDevice
                                                   getName(),
                                                   FCQueryEvType.APP_CONFIG_CHANGED,
                                                   null);
        betterFlowCacheMgr.submitFlowCacheQuery(fcQueryObj);
    }

    /*
     * netVirtProcessAddressSpaceConfig
     *
     * Process address-space configuration in netVirt.
     */
    private void netVirtProcessAddressSpaceConfig (VNS oldVNS, VNS vns,
                     IResultSet netVirtResultSet, Set<VNS> vnsModifiedSet) {
        String oldAddressSpace;
        String newAddressSpace;

        if (oldVNS == null) {
            oldAddressSpace = "default";
        } else {
            oldAddressSpace = oldVNS.getAddressSpaceName();
            if (oldAddressSpace == null || oldAddressSpace.isEmpty()) {
                oldAddressSpace = "default";
            }
        }

        newAddressSpace = netVirtResultSet.getString(ADDRESS_SPACE_COLUMN_NAME);

        if (newAddressSpace == null || newAddressSpace.isEmpty()) {
            newAddressSpace = "default";
        }

        vns.setAddressSpaceName(newAddressSpace);

        if (!oldAddressSpace.equals(newAddressSpace)) {
            // we need to add the old and new VNS to the change set
            // so we can correctly determine which address-spaces are affected
            vnsModifiedSet.add(vns);
            if (oldVNS != null)
                vnsModifiedSet.add(oldVNS);
        }

        if (!newConfigState.deviceGroupMatchers.containsKey(newAddressSpace)) {
            newConfigState.deviceGroupMatchers.put(newAddressSpace,
                new DeviceGroupMatcher<VNS>(tagManager, controllerProvider));
        }

        return;
    }

    /**
     * Read the VNS configuration information from storage, merging with the
     * existing configuration.
     */
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Error loading VNS {vns} from storage, entry ignored. ",
                explanation="Could not read the entry for this NetVirt from " +
                        "the system database",
                recommendation="This can happen transiently under certain " +
                        "circumstances without causing problems.  If the " +
                        "problem persists, it may indicate" +
                        " corruption in the system database."),
        @LogMessageDoc(level="ERROR",
                message="Error loading VNS {vns} rule {rule} from " +
                        "storage, entry ignored.",
                explanation="Could not read the entry for this VNS rule from " +
                        "the system database",
                recommendation="This can happen transiently under certain " +
                        "circumstances without causing problems.  If the " +
                        "problem persists, it may indicate" +
                        " corruption in the system database.")
    })
    protected void readVNSConfigFromStorage() throws StorageException {
        IResultSet vnsResultSet;
        // try multiple times as work-around for BSC-2928
        int trial = 0;
        while (true) {
            trial++;
            try {
                vnsResultSet = storageSource.executeQuery(VNS_TABLE_NAME,
                    null, null, null);
                break;
            } catch (StorageException e) {
                if (trial > 15) {
                    throw e;
                }
                logger.warn("retry " + trial
                            + ": readNetVirtConfigFromStorage encountered StorageExcption "
                            + e.getMessage());
                try {
                    Thread.sleep((trial < 6) ? 1000 : 2000);
                } catch (InterruptedException e2) {
                    logger.warn("sleep interrupted");
                }
            }
        }
        IResultSet vnsIRuleResultSet =
            storageSource.executeQuery(VNS_INTERFACE_RULE_TABLE_NAME,
                new String[]{ID_COLUMN_NAME, VNS_COLUMN_NAME,
                             DESCRIPTION_COLUMN_NAME,
                             VLAN_TAG_ON_EGRESS_COLUMN_NAME,
                             MULTIPLE_ALLOWED_COLUMN_NAME, ACTIVE_COLUMN_NAME,
                             PRIORITY_COLUMN_NAME, MAC_COLUMN_NAME,
                             IP_SUBNET_COLUMN_NAME, SWITCH_COLUMN_NAME,
                             PORTS_COLUMN_NAME, VLANS_COLUMN_NAME,
                             TAGS_COLUMN_NAME},
                null, null);

        // We will maintain a set of NetVirtes that were deleted and a set new NetVirtes
        // that were created so that we can reconcile flows in a subset of NetVirtes
        // We add NetVirt to this set if they have been modified in a way
        // that would affect flow reconciliation. We will often add the
        // old and new NetVirt to this set so we can identify the all
        // relevant address-spaces and the highest priority NetVirt that
        // changed. 
        // For this reason this must be a list and not a set
        // because old and new NetVirt will be considered equal
        Set<VNS> deletedSet = 
                Collections.newSetFromMap(new IdentityHashMap<VNS, Boolean>());
        Set<VNS> modifiedSet  = 
                Collections.newSetFromMap(new IdentityHashMap<VNS, Boolean>());

        synchronized(newConfigLock) {
            newConfigState = new ConfigState();

            while (vnsResultSet.next()) {
                String id = vnsResultSet.getString(ID_COLUMN_NAME);

                VNS oldvns = curConfigState.vnsMap.get(id);
                VNS vns = new VNS(id);
                if (oldvns == null) {
                    // New VNS was created
                    modifiedSet.add(vns);
                }

                try {
                    // FIXME: We don't track changes to the ORIGIN column
                    vns.setOrigin(vnsResultSet.getString(ORIGIN_COLUMN_NAME));
                    
                    vns.setActive(vnsResultSet.getBoolean(ACTIVE_COLUMN_NAME));
                    if (oldvns != null && oldvns.isActive() != vns.isActive()) {
                        if (oldvns.isActive())
                            modifiedSet.add(oldvns);
                        else
                            modifiedSet.add(vns);
                    }
                    vns.setPriority(vnsResultSet.getInt(PRIORITY_COLUMN_NAME));
                    if (oldvns != null && oldvns.getPriority() != vns.getPriority()) {
                        // need to flag both VNS' as modified since we need
                        // later identify the highest priority one to
                        // reconcile
                        modifiedSet.add(oldvns);
                        modifiedSet.add(vns);
                    }
                    vns.setDescription(
                        vnsResultSet.getString(DESCRIPTION_COLUMN_NAME));
                    netVirtProcessAddressSpaceConfig(oldvns, vns, vnsResultSet,
                                                 modifiedSet);

                    setArpConfig(vns, vnsResultSet.getString(ARP_CONFIG_MODE_COLUMN_NAME));
                    setDhcpConfig(vns, vnsResultSet.getString(DHCP_CONFIG_MODE_COLUMN_NAME),
                                       vnsResultSet.getString(DHCP_IP_COLUMN_NAME));
                    setBroadcastConfig(vns, vnsResultSet.getString(BROADCAST_CONFIG_MODE_COLUMN_NAME));
                } catch (Exception e) {
                    logger.warn("Error loading VNS " + id + " from storage, entry ignored. " + e);
                    continue;
                }

                vns.setMarked(true);
                newConfigState.vnsMap.put(vns.getName(), vns);
                if (logger.isTraceEnabled()) {
                    logger.trace("VNS {}: Configuration complete ",
                                 vns.getName());
                }
            }

            // clear out state related to VNS that no longer exist
            for (VNS oldvns: curConfigState.vnsMap.values()) {
                if (! newConfigState.vnsMap.containsKey(oldvns.getName()))
                    deletedSet.add(oldvns);
            }

            // Read interface rules from result set
            while (vnsIRuleResultSet.next()) {
                String id = vnsIRuleResultSet.getString(ID_COLUMN_NAME);
                String vnsid = vnsIRuleResultSet.getString(VNS_COLUMN_NAME);

                VNS vns = newConfigState.vnsMap.get(vnsid);
                MembershipRule<VNS> oldIRule =
                        curConfigState.interfaceRuleMap.get(id);
                MembershipRule<VNS> irule;

                if (vns != null) {
                    if (oldIRule == null) {
                        modifiedSet.add(vns);
                    }

                    irule = new MembershipRule<VNS>(id, vns);

                    try {
                        /* For each of the possible VNS Interface rule match
                         * options, we compare the match field with the old
                         * value, and if the new value is differnet then we
                         * add the netVirt to netVirtWithRuleChangeSet
                         */
                        irule.setDescription(vnsIRuleResultSet.getString(DESCRIPTION_COLUMN_NAME));
                        irule.setActive(vnsIRuleResultSet.getBoolean(ACTIVE_COLUMN_NAME));
                        irule.setMultipleAllowed(vnsIRuleResultSet.getBoolean(MULTIPLE_ALLOWED_COLUMN_NAME));
                        irule.setVlanTagOnEgress(vnsIRuleResultSet.getBoolean(VLAN_TAG_ON_EGRESS_COLUMN_NAME));
                        irule.setPriority(vnsIRuleResultSet.getInt(PRIORITY_COLUMN_NAME));
                        irule.setMac(vnsIRuleResultSet.getString(MAC_COLUMN_NAME));
                        irule.setIpSubnet(vnsIRuleResultSet.getString(IP_SUBNET_COLUMN_NAME));
                        irule.setSwitchId(vnsIRuleResultSet.getString(SWITCH_COLUMN_NAME));
                        irule.setPorts(vnsIRuleResultSet.getString(PORTS_COLUMN_NAME));
                        irule.setVlans(vnsIRuleResultSet.getString(VLANS_COLUMN_NAME));
                        irule.setTags(vnsIRuleResultSet.getString(TAGS_COLUMN_NAME));

                        if (oldIRule != null
                                && !oldIRule.matchingFieldsEquals(irule)) {
                            // rules have changed
                            modifiedSet.add(oldIRule.getParentDeviceGroup());
                            modifiedSet.add(irule.getParentDeviceGroup());

                        }
                    } catch (Exception e) {
                        logger.warn("Error loading VNS " + vnsid + " rule " + id + " from storage, entry ignored. " + e);
                        continue;
                    }

                    irule.setMarked(true);

                    newConfigState.interfaceRuleMap.put(irule.getName(),
                                                        irule);

                    createVNSInterfaces(irule);

                    // Setup lookup data structures
                    String addrSpaceName = vns.getAddressSpaceName();
                    IDeviceGroupMatcher<VNS> deviceGroupMatcher =
                            newConfigState.deviceGroupMatchers.get(addrSpaceName);

                    if (deviceGroupMatcher != null) {
                        // deviceGroupMatcher should never be null
                        deviceGroupMatcher.addRuleIfActive(irule);
                    }

                    if (logger.isTraceEnabled()) {
                        logger.trace("Configured VNS Interface Rule {} ",
                                     irule);
                    }
                }
            }

            // Flag NetVirt with deleted rules
            for (MembershipRule<VNS> oldIRule:
                    curConfigState.interfaceRuleMap.values()) {
                if (! newConfigState.interfaceRuleMap
                        .containsKey(oldIRule.getName())) {
                    modifiedSet.add(oldIRule.getParentDeviceGroup());
                }
            }

            // Get VNS that must be sent to flow reconciliation
            Set<String> vnsFlowQuerySet =
                    getVNSFlowReconciliation(modifiedSet, deletedSet);

            // Swap config states
            configLock.writeLock().lock();
            try {
                curConfigState = newConfigState;
                newConfigState = null;
            } finally {
                configLock.writeLock().unlock();
            }

            for (String netVirtName : vnsFlowQuerySet) {
                triggerFlowReconciliation(netVirtName);
            }
            if (!vnsFlowQuerySet.isEmpty()) {
                // FIXME: temporary work-around to be able to reconcile
                // flow from VirtualRoutingService
                triggerFlowReconciliation(
                        IVirtualRoutingService.VRS_FLOWCACHE_NAME);

                // Notify all netVirtListeners on changed netVirtes
                for (INetVirtListener listener : netVirtListeners) {
                    listener.netVirtChanged(vnsFlowQuerySet);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Notify listener {} of changed VNSes {}",
                                 listener.getClass().getName(), vnsFlowQuerySet);
                    }
                }
            }
        } // end synchronized
    }

    protected Set<String>
            getVNSFlowReconciliation (Set<VNS> vnsModifiedSet,
                                         Set<VNS> vnsDeletedSet) {

        /* Reconcile flows for the VNStes that were deleted and check if any
         * flow would be moved to a new vns that was created with higher
         * priority.
         * <p>
         *
         * DELETED VNS
         * ===========
         * For the flows in VNSes that were deleted either those flows need to
         * be deleted or they need to be moved to some other NetVirt if the devices
         * were member of multiple vns. If the flow need to be moved to some
         * other NetVirt then the flow-mods in the switches need not be touched buy
         * the flow-cache should be updated so that these flows are moved to
         * other application instance name. Here we need to submit flow query
         * for all the VNSes that were deleted.
         * <p>
         * ADDED VNS
         * =========
         * For the new vns that were created we need to check if any flow
         * from a lower priority NetVirt need to be migrated to a newly created
         * vns. To accomplish this we need to submit flow query for all the
         * existing vns that are in lower priority than the highest-priority
         * vns that was created.
         *
         * We will use the logic stated above to create a set of vns for which
         * flow queries need to be submitted for reconciliation.
         * 
         * QUICK HACK
         * ==========
         * see nonNullOriginSimpleReconcile's comment 
         * 
         */
        HashSet<String> vnsFlowQuerySet = new HashSet<String>();
        
        if (vnsDeletedSet.isEmpty() && vnsModifiedSet.isEmpty())
            return Collections.emptySet();

        /*
         * TODO: we can do this a lot more efficently:
         *   a) only query for NetVirt' that are in the affected address-spaces
         *      (instead of quering all NetVirt' with a priority lower than max)
         *   b) rely on the NetVirt ordering (which includes NetVirt name to break a
         *      tie) instead of using just the priority.
         */

        /* Find the NetVirt with highest priority in netVirtModifiedSet */
        int maxPriority = -Integer.MIN_VALUE;
        for (VNS netVirtIdx : vnsModifiedSet) {
            vnsFlowQuerySet.add(netVirtIdx.getName());
            if (nonNullOriginSimpleReconcile && 
                    netVirtIdx.getOrigin() != null &&
                    !netVirtIdx.getOrigin().isEmpty() ) {
                continue;
            }
            if (netVirtIdx.getPriority() > maxPriority) {
                maxPriority = netVirtIdx.getPriority();
            }
        }

        /* Get all NetVirtes with priority less than or equal to
         * highestPriorityNetVirtAddedAndRuleChanged
         */
        for (String vnsNameIdx : newConfigState.vnsMap.keySet() ) {
            VNS vnsCur = newConfigState.vnsMap.get(vnsNameIdx);
            if (vnsCur.getPriority() <= maxPriority) {
                vnsFlowQuerySet.add(vnsNameIdx);
            }
        }

        /* Now add all the NetVirtes that were deleted as we need to reconcile
         * flows in those NetVirtes
         */
        for (VNS vnsIdx : vnsDeletedSet) {
            vnsFlowQuerySet.add(vnsIdx.getName());
        }

        /* Now submit flow query to get the flows in each of these NetVirtes */
        if (logger.isTraceEnabled()) {
            logger.trace("Set of NetVirtes to query for flow reconciliation: {}",
                vnsFlowQuerySet);
        }

        // We want to always add default NetVirt to reconcile set given that it
        // will match everything that was otherwise unmatched
        vnsFlowQuerySet.add("default|default");
        return vnsFlowQuerySet;
    }

    /**
     * Pre-create NetVirt interface based on the rule
     */
    private void createVNSInterfaces(MembershipRule<VNS> vnsIRule) {
        String ifname = vnsIRule.getFixedInterfaceName();
        VNS vns = vnsIRule.getParentDeviceGroup();
        VNSInterface iface =
                newConfigState.interfaceMap.get(vns.getName() + "|" + ifname);
        if (iface == null) {
            iface = new VNSInterface(ifname, vns, vnsIRule, null);
            newConfigState.interfaceMap.put(vns.getName() + "|" + ifname, iface);
        }

        /**
         * Update the switchInterfaceRuleMap to include vnsirule if
         * it has any switch-specific sub-interface to be created.
         */
        if (vnsIRule.getSwitchId() == null) return;
        long switchDPID = HexString.toLong(vnsIRule.getSwitchId());

        List<MembershipRule<VNS>> memList;
        memList = newConfigState.switchInterfaceRuleMap.get(switchDPID);
        if (memList == null) {
            memList = new ArrayList<MembershipRule<VNS>>();
            newConfigState.switchInterfaceRuleMap.put(switchDPID, memList);
        }
        memList.add(vnsIRule);

        /**
         *  If the switch with switchDPID is already connected, then
         *  create the sub-interface rule immediately.
         */
        IOFSwitch sw = controllerProvider.getSwitches().get(switchDPID);
        if (sw == null) return;
        createVNSSubInterfaces(newConfigState, sw, vnsIRule);
    }


    /**
     * When a switch connects to the controller, this method will create
     * NetVirt sub-interfaces for any switch/switch-port specific configurations.
     * This method is called from addedSwitch() and therefore must operate
     * on curConfigState
     * @param switchDPID
     */
    private void createVNSSubInterfacesForSwitch(long switchDPID) {
        /*
         * We can perform this operation while only holding the read lock
         * since we won't be swapping configState pointers.
         * TODO: verify that this is indeed the case
         */
        configLock.readLock().lock();
        try {
            List<MembershipRule<VNS>> memList;
            memList = curConfigState.switchInterfaceRuleMap.get(switchDPID);
            if (memList == null) return;

            IOFSwitch sw = controllerProvider.getSwitches().get(switchDPID);
            if (sw == null) return;

            for(MembershipRule<VNS> irule: memList)
                createVNSSubInterfaces(curConfigState, sw, irule);
        } finally {
            configLock.readLock().unlock();
        }
     }

    /**
     * Pre-create NetVirt sub-interfaces based on rule (if applicable)
     * Only switch-port rules are relevant in the current implementation
     */
    private void createVNSSubInterfaces(ConfigState configState,
                                        IOFSwitch sw,
                                        MembershipRule<VNS> irule) {
        String ifname = irule.getFixedInterfaceName();
        VNS vns = irule.getParentDeviceGroup();
        VNSInterface iface =
                configState.interfaceMap.get(vns.getName() + "|" + ifname);
        if (iface == null) {
            logger.debug("Fixed interface not created for {}", irule);
            return;
        }

        List<String> subifnames = irule.getFixedSubInterfaceNames(sw);
        if (subifnames == null)
            return;

        for (String name : subifnames) {
            VNSInterface subiface =
                    configState.interfaceMap.get(vns.getName() + "|" + name);
            if (subiface == null) {
                subiface = new VNSInterface(name, vns, irule, iface);
                configState.interfaceMap.put(vns.getName() + "|" +  name,
                                             subiface);
            }
        }
    }

    /**
     * Annotate the message context for a packet in the NetVirt interfaces associated
     * with the source and destination addresses for the flow.
     *
     * @param sw the switch for the packet-in
     * @param pi the packet-in message
     * @param cntx
     * @return @ref
     */
    private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                           ListenerContext cntx) {
        IFlowCacheService.fcStore.put(cntx, IFlowCacheService.FLOWCACHE_APP_INSTANCE_NAME, "netVirt");
        return annotateDeviceVNSInterfaces (cntx, null);
    }

    /**
     * Annotate NetVirt interfaces for src and dst devices
     * @param cntx
     * @return
     */
    @LogMessageDoc(level="WARN",
            message="Source device {device}'s entity class is not " +
                    " a BetterEntityClass",
            explanation="This message indicates a misconfiguration of the " +
                    "packet processing pipeline.",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private Command annotateDeviceVNSInterfaces (ListenerContext cntx, OFMatch match) {
        IDevice src =
            IDeviceService.fcStore.
                get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
        IDevice dst =
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);

        if (logger.isTraceEnabled()) {
            logger.trace("srcDevice: {}, dstDevice: {}", src, dst);
        }

        // Retrieve interfaces for source and destination
        List<VNSInterface> srcIfaces = null;
        IEntityClass srcEc = null;
        if (src != null) {
            srcEc = src.getEntityClass();
            if (srcEc instanceof BetterEntityClass) {
                BetterEntityClass addrSpace = (BetterEntityClass)srcEc;
                if (addrSpace.getVlan() != null) {
                    rewriteService.setTransportVlan(addrSpace.getVlan(), cntx);
                }
            } else {
                logger.warn("Source device {}'s entity class is not " +
                            " a BetterEntityClass", src);
            }
            srcIfaces = getInterfaces(src);
            if (srcIfaces != null && srcIfaces.size() > 0) {
                if (logger.isTraceEnabled()) {
                    logger.trace("srcIface: {}", srcIfaces.get(0));
                }
                bcStore.put(cntx, INetVirtManagerService.CONTEXT_SRC_IFACES, srcIfaces);
            }
        }

        List<VNSInterface> dstIfaces = null;
        if (dst != null) {
            dstIfaces = getInterfaces(dst);
        } else {
            // shortcut out of having to do work before calling registered classifiers
            // if no classifiers have registered
            if (srcEc != null &&
                    vnsInterfaceClassifiers.getOrderedListeners() != null) {
                dstIfaces = tryClassifyingUnknownDevice(cntx, match, srcEc);
            }
        }
        if (dstIfaces != null && dstIfaces.size() > 0) {
            if (logger.isTraceEnabled()) {
                logger.trace("dstIface: {}", dstIfaces.get(0));
            }
            bcStore.put(cntx, INetVirtManagerService.CONTEXT_DST_IFACES, dstIfaces);
        }
        return Command.CONTINUE;
    }

    /**
     * Even without a device, this method will try to get the relevant information
     * from the packet, in order to classify the unknown device's NetVirtInterfaces
     * using registered INetVirtIntefaceClassifiers (like SINetVirtInterfaceClassifier)
     */
    private List<VNSInterface> tryClassifyingUnknownDevice(ListenerContext cntx,
                                                           OFMatch match,
                                                           IEntityClass srcEc) {
        Integer ipAddress = null;
        Short vlan = null;
        long dstMac = 0;

        Ethernet eth =
                IControllerService.bcStore.
                get(cntx,IControllerService.CONTEXT_PI_PAYLOAD);

        if (eth != null) {
            // If packetIn is an unicast IPV4 packet, get destination ipaddress.
            if (eth.getPayload() instanceof IPv4 &&
                    !eth.isBroadcast() &&
                    !eth.isMulticast()) {
                IPv4 ipv4 = (IPv4) eth.getPayload();
                ipAddress = ipv4.getDestinationAddress();
            }
            vlan = eth.getVlanID();
            dstMac = eth.getDestinationMAC().toLong();
        } else if (match != null) {
            ipAddress = match.getNetworkDestination();
            vlan = match.getDataLayerVirtualLan();
            dstMac = Ethernet.toLong(match.getDataLayerDestination());
        }
        /**
         * There are two options on when to query registered NetVirtInterfaceClassifiers:
         * 1. If the device doesn't match any NetVirtInterface,
         * Check the registered NetVirtInterfaceClassifiers for device
         * classification. This is what is required for ServiceInsertion feature and,
         * hence, the implementation.
         * 2. Always check with registered NetVirtInterfaceClassifiers and union the results.
         *
         * Option 2 is more flexible, but no use case yet.
         */
        return classifyUnknownDevice(srcEc.getName(),
                                     dstMac,
                                     vlan,
                                     ipAddress,
                                     null);
    }

    /**
     * Match a device against the interface rules and return the list of interfaces.
     * Must be called with a config read lock held.
     *
     * @param d
     * @return
     */
    protected List<VNSInterface> matchDevice(IDevice d) throws Exception {
        IDeviceGroupMatcher<VNS> deviceGroupMatcher =
            getDeviceGroupMatcher(d);

        if (deviceGroupMatcher == null) return null;
        List<MembershipRule<VNS>> matches = deviceGroupMatcher.matchDevice(d);
        if (matches == null)
            return null;
        ArrayList<VNSInterface> deviceInterfaces =
                new ArrayList<VNSInterface>();
        for (MembershipRule<VNS> netVirtIRule: matches) {
            deviceInterfaces.add(getIfaceFromMatchingRule(netVirtIRule, d));
        }
        return deviceInterfaces;
    }

    /**
     * createDefaultNetVirt
     * Create default NetVirt under the entity's address-space.
     * for tenant default
     */
    protected VNS createDefaultNetVirt (IEntityClass entityClass) {

        /*
         * Use 'default' by default.
         */
        String netVirtName          = "default|default";
        String addressSpaceName = "default";

        /*
         * If this device is in non-default address-space, name the netVirt
         * accordingly.
         */
        if (entityClass != null && !entityClass.getName().isEmpty() &&
                !entityClass.getName().equals("default")) {
            addressSpaceName = entityClass.getName();
            netVirtName = "default|" + addressSpaceName + "-default";
        }

        /*
         * Create a new NetVirt and set its address-space.
         */
        VNS netVirt = new VNS(netVirtName);
        netVirt.setActive(true);
        netVirt.setAddressSpaceName(addressSpaceName);

        VNS oldNetVirt = curConfigState.vnsMap.putIfAbsent(netVirt.getName(), netVirt);

        if (oldNetVirt != null)
            return oldNetVirt;
        else
            return netVirt;
    }

    /**
     * Get an interface from an interface name, creating it if needed
     * @param iname the components of the interface name
     * @param netVirt the netVirt for the interface (may be null, in which case
     * use the default NetVirt)
     * @param rule the NetVirt rule for the interface (may be null)
     * @return
     */
    @Override
    public VNSInterface getIfaceFromName(String[] iname,
                                            VNS netVirt, IDevice device,
                                            MembershipRule<VNS> rule) {
        String[] names = new String[iname.length];
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < iname.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(iname[i]);
            names[i] = sb.toString();
        }

        // use default NetVirt, creating if needed, if NetVirt is null
        if (netVirt == null) {
            IEntityClass entityClass = null;

            if (device != null) {
                entityClass = device.getEntityClass();
            }
            netVirt = createDefaultNetVirt(entityClass);
        }

        VNSInterface iface =
                curConfigState.interfaceMap.get(netVirt.getName() + "|" + names[names.length-1]);
        if (iface == null) {
            // Allocate new interface
            boolean created = false;
            for (String name : names) {
                VNSInterface last = iface;
                iface = curConfigState.interfaceMap.get(netVirt.getName() + "|" + name);
                if (iface == null) {
                    iface = new VNSInterface(name, netVirt, rule, last);
                    curConfigState.interfaceMap.put(netVirt.getName() + "|" +
                            name, iface);
                    if (created) {  // catching missed pre-creation
                        logger.debug(
                                "Parent interface not pre-created {}",
                                last);
                    }
                    created = true;
                }
            }
        }

        return iface;
    }

    /**
     * Get the NetVirt interface for a matching rule and its device
     * @param rule the rule
     * @param d the device
     * @return the interface
     */
    protected VNSInterface getIfaceFromMatchingRule(MembershipRule<VNS> rule,
                                                    IDevice d) {
        String[] iname =
                rule.getInterfaceNameForDevice(d, controllerProvider);
        return getIfaceFromName(iname, rule.getParentDeviceGroup(), d, rule);
    }

    @Override
    public void clearCachedDeviceState(long deviceKey) {
        if (logger.isDebugEnabled()) {
            logger.debug("Clearing cached NetVirt interface mapping for {}",
                         deviceKey);
        }
        configLock.writeLock().lock();
        try {
            List<VNSInterface> ifaces =
                    curConfigState.deviceInterfaceMap.get(deviceKey);
            if (ifaces == null)
                return;
            for (VNSInterface iface : ifaces) {
                iface.getParentVNS().removeDevice(deviceKey);
                curConfigState.interfaceMap.remove(iface.getParentVNS().getName() + "|" +
                                    iface.getName());
            }
            curConfigState.deviceInterfaceMap.remove(deviceKey);
        } finally {
            configLock.writeLock().unlock();
        }
    }

    // ***************
    // IOFSwitchListener
    // ***************

    @Override
    public void addedSwitch(IOFSwitch sw) {
        configLock.writeLock().lock();
        try {
            createVNSSubInterfacesForSwitch(sw.getId());
        } finally {
            configLock.writeLock().unlock();
        }
        updateBroadcastSwitchPorts();
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        updateBroadcastSwitchPorts();
    }

    @Override
    public void switchPortChanged(Long switchId) {
        // no-op
    }

    @Override
    public void controllerNodeIPsChanged(
            Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {
        // ignore
    }

    // ***************
    // IHARoleListener
    // ***************

    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    reloadConfigState();
                }
                break;
            case SLAVE:
                logger.debug("Clearing config state due to HA " +
                               "role change to SLAVE");
                clearConfigState();
                clearCachedState();
                break;
           default:
               break;
        }
    }

    /**
     * Clears all the internal config state.
     */
    protected void clearConfigState() {
        configLock.writeLock().lock();
        try {
            curConfigState.clear();
            confBroadcastIfaces.clear();
            broadcastSwitchPorts.clear();
        } finally {
            configLock.writeLock().unlock();
        }
    }

    protected void clearCachedState() {
        flowQueryRespHandlerCallCount = 0;
        lastFCQueryResp = null;
    }

    /**
     * Reloads the internal config state.
     */
    protected void reloadConfigState() {
        readVNSConfigFromStorage();
        readSwitchInterfaceConfig();
    }

}
