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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.ovsdb.IOVSDB;
import org.sdnplatform.ovsdb.IOVSDBListener;
import org.sdnplatform.ovsdb.IOVSDBManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation for {@link IOVSDBManagerService}
 */
public class OVSDBManagerImpl 
    implements IModule, IOVSDBManagerService,
               IHAListener {
    protected static Logger logger = 
        LoggerFactory.getLogger(OVSDBManagerImpl.class);

    /**
     * OVSDB objects that are connected to the controller on the OpenFlow 
     * channel. Indexed by dpid.
     */
    public static Map<Long, IOVSDB> ovsSwitchMap = 
        new ConcurrentHashMap<Long, IOVSDB>();
    /**
     * OVSDB objects that are not connected to the controller on the OpenFlow
     * channel but are reachable on the JSON RPC channel. Indexed by management
     * IP address.
     */
    public static Map<String, IOVSDB> ovsSwitchMapNC = 
        new ConcurrentHashMap<String, IOVSDB>();

    protected IControllerService controllerProvider;
    ClientBootstrap bootstrap;
    OVSDBClientPipelineFactory ovsdbcFact;

    public Collection<IOVSDBListener> listeners;

    public void shutDown() {
        
    }
    
    //*****************************
    // Getters/Setters
    //*****************************

    public void setControllerProvider(
            IControllerService controllerProvider) {
        this.controllerProvider = controllerProvider;
    }
    
    //**************
    // IOVSDBManager
    //**************

    @Override
    public IOVSDB getOVSDB(long dpid) {
        return ovsSwitchMap.get(dpid);
    }

    @Override
    public Collection<IOVSDB> getOVSDB() {
        return ovsSwitchMap.values();
    }

    @Override
    public void addOVSDBListener(IOVSDBListener l) {
        if (listeners == null)
            listeners = new ArrayList<IOVSDBListener>();
        listeners.add(l);
    }
    
    @Override
    public IOVSDB removeOVSDB(long dpid) {
        IOVSDB o = ovsSwitchMap.remove(dpid);
        if (logger.isDebugEnabled() && o != null) {
            logger.debug("removing OVSDB obj from ovs map for dpid {}", dpid);
        }
        return o;
    }    
    
    @Override
    public IOVSDB addOVSDB(long dpid) {
        if (ovsSwitchMap.containsKey(dpid)) {
            logger.info("OVS Switch {} already connected", dpid);
            return ovsSwitchMap.get(dpid);
        } else {
            //create new ovsdb instance for OVSDBManager
            Object statusObject = new Object();
            if (controllerProvider.getSwitches() != null &&
                controllerProvider.getSwitches().get(dpid) != null) {
                String mgmtIPAddr = controllerProvider.getSwitches().get(dpid)
                                    .getInetAddress().toString();
                mgmtIPAddr = mgmtIPAddr.substring(1, mgmtIPAddr.indexOf(':'));
                if (ovsSwitchMapNC.containsKey(mgmtIPAddr)) {
                    ovsSwitchMapNC.remove(mgmtIPAddr);
                    logger.debug("removed ovsdb object in NC map " +
                            "for ovs @ {}", mgmtIPAddr);
                }
                logger.debug("adding new OVSDB object to ovs map for " +
                        "dpid {} at mgmt. ip {}", dpid, mgmtIPAddr);
                OVSDBImpl tsw = new OVSDBImpl(dpid, mgmtIPAddr,
                        ovsdbcFact, bootstrap, statusObject);
                tsw.getTunnelIPAddress(); //populate tunnel-IP address
                ovsSwitchMap.put(dpid, tsw);
                return tsw;
            }
            return null;
        }
    }    
    
    @Override
    public boolean addPort(long dpid, String portname) {
        IOVSDB ovs = ovsSwitchMap.get(dpid);
        if (ovs != null) {
            ovs.addPort(portname, null, null, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean delPort(long dpid, String portname) {
        IOVSDB ovs = ovsSwitchMap.get(dpid);
        if (ovs != null) {
            ovs.delPort(portname);
            return true;
        }
        return false;
    }
    
    @Override
    public ArrayList<String> getControllerIPAddresses(long dpid) {
        if (ovsSwitchMap.containsKey(dpid))
            return ovsSwitchMap.get(dpid).getControllerIPs();
        //check in map for switches not connected on OF channel
        for (IOVSDB o : ovsSwitchMapNC.values()) {
            if (o.getDpid() == dpid) {
                return o.getControllerIPs();
            }
        }
        return null;
    }
    
    @Override
    public void setControllerIPAddresses(long dpid, ArrayList<String> cntrIP) {
        if (cntrIP == null || cntrIP.size() == 0) {
            logger.error("must specify at least one controller-ip to set" +
                    "at dpid {}", dpid);
            return;
        }
        if (ovsSwitchMap.containsKey(dpid)) {
            ovsSwitchMap.get(dpid).setControllerIPs(cntrIP);
            return;
        } else {
            for (IOVSDB o : ovsSwitchMapNC.values()) {
                if (o.getDpid() == dpid) {
                    o.setControllerIPs(cntrIP);
                    return;
                }
            }
        }
        logger.error("Switch dpid {} not set - could not find ovsdb object" +
                " when trying to set controller-IPs", dpid);
    }
    
    private IOVSDB findOrCreateBridge(String mgmtIPAddr) {
        for (IOVSDB o : ovsSwitchMap.values()) {
            if (o.getMgmtIPAddr().equals(mgmtIPAddr)) {
                return o;
            }
        }
        if (ovsSwitchMapNC.containsKey(mgmtIPAddr)) {
            return ovsSwitchMapNC.get(mgmtIPAddr);
        }
        
        // otherwise create a new OVSDB object with dummy dpid
        Object statusObject = new Object();
        OVSDBImpl dsw = new OVSDBImpl(-1, mgmtIPAddr, ovsdbcFact, bootstrap,
                                      statusObject);
        ovsSwitchMapNC.put(mgmtIPAddr, dsw);
        return dsw;
    }
    
    @Override
    public String getBridgeDpid(String mgmtIPAddr) {
        IOVSDB dsw = findOrCreateBridge(mgmtIPAddr);
        return dsw.getBridgeDpid();
    }
    
    @Override
    public void setBridgeDpid(String mgmtIPAddr, String dpidstr) {
        // We may have stale objects with same dpid in NC map, remove conflicts
        Long dpid = Long.parseLong(dpidstr, 16);
        for (IOVSDB o : ovsSwitchMapNC.values()) {
            if (o.getDpid() == dpid && !o.getMgmtIPAddr().equals(mgmtIPAddr)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Removing stale object with IP {} and dpid {}",
                                mgmtIPAddr, dpidstr);
                }
                ovsSwitchMapNC.remove(o.getMgmtIPAddr());
             }
        }
        IOVSDB dsw = findOrCreateBridge(mgmtIPAddr);
        dsw.setBridgeDpid(dpidstr);
    }

    //*****************
    // Internal helpers
    //*****************
    
    protected void setupNettyClient() {
        // Configure the client.
        bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        // TODO: intelligently decide between SSL and plain
        ovsdbcFact = new OVSDBClientPipelineFactory();
        bootstrap.setPipelineFactory(ovsdbcFact);
        bootstrap.setOption("reuseAddr", true);
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setOption("child.tcpNoDelay", true);
    }
    
    // IModule
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IOVSDBManagerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> 
                getServiceImpls() {
        Map<Class<? extends IPlatformService>,
            IPlatformService> m = 
                new HashMap<Class<? extends IPlatformService>,
                    IPlatformService>();
        m.put(IOVSDBManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> 
                getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IControllerService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        controllerProvider =
                context.getServiceImpl(IControllerService.class);
    }

    @Override
    public void startUp(ModuleContext context) {
        
        controllerProvider.addHAListener(this);
        setupNettyClient();
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Unknown controller role: {role}",
                explanation="The OVSDB manager received a notification "
                           + "that the controller changed to a new role that"
                           + "is currently not supported",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                // no-op for now
                break;
            case SLAVE:
                logger.debug("Clearing OVS Switch Maps due to " +
                        "HA change to SLAVE");
                ovsSwitchMap.clear();
                ovsSwitchMapNC.clear();
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
