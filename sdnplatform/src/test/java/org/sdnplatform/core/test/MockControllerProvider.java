/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University 
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

package org.sdnplatform.core.test;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;


import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IInfoProvider;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchDriver;
import org.sdnplatform.core.IOFSwitchFilter;
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.RoleInfo;
import org.sdnplatform.core.IListener.Command;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.util.ListenerDispatcher;
import org.sdnplatform.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class MockControllerProvider implements IModule, IControllerService {
    protected static Logger log = LoggerFactory.getLogger(MockControllerProvider.class);
    protected ConcurrentMap<OFType, ListenerDispatcher<OFType,IOFMessageListener>> listeners;
    protected List<IOFSwitchListener> switchListeners;
    protected List<IHAListener> haListeners;
    protected Map<Long, IOFSwitch> switches;
    protected BasicFactory factory;

    /**
     * 
     */
    public MockControllerProvider() {
        listeners = new ConcurrentHashMap<OFType, ListenerDispatcher<OFType, 
                                   IOFMessageListener>>();
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        switchListeners = new CopyOnWriteArrayList<IOFSwitchListener>();
        haListeners = new CopyOnWriteArrayList<IHAListener>();
        factory = new BasicFactory();
    }

    @Override
    public synchronized void addOFMessageListener(OFType type, 
                                                  IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd = 
                listeners.get(type);
        if (ldd == null) {
            ldd = new ListenerDispatcher<OFType, IOFMessageListener>();
            listeners.put(type, ldd);
        }
        ldd.addListener(type, listener);
    }

    @Override
    public synchronized void removeOFMessageListener(OFType type,
                                                     IOFMessageListener listener) {
        ListenerDispatcher<OFType, IOFMessageListener> ldd = 
                listeners.get(type);
        if (ldd != null) {
            ldd.removeListener(listener);
        }
    }

    /**
     * @return the listeners
     */
    @Override
    public Map<OFType, List<IOFMessageListener>> getListeners() {
        Map<OFType, List<IOFMessageListener>> lers = 
                new HashMap<OFType, List<IOFMessageListener>>();
            for(Entry<OFType, ListenerDispatcher<OFType, IOFMessageListener>> e : 
                listeners.entrySet()) {
                lers.put(e.getKey(), e.getValue().getOrderedListeners());
            }
            return Collections.unmodifiableMap(lers);
    }

    public void clearListeners() {
        this.listeners.clear();
    }
    
    @Override
    public Map<Long, IOFSwitch> getSwitches() {
        return this.switches;
    }

    public void setSwitches(Map<Long, IOFSwitch> switches) {
        this.switches = switches;
    }

    @Override
    public void addOFSwitchListener(IOFSwitchListener listener) {
        switchListeners.add(listener);
    }

    @Override
    public void removeOFSwitchListener(IOFSwitchListener listener) {
        switchListeners.remove(listener);
    }

    public void dispatchMessage(IOFSwitch sw, OFMessage msg) {
        dispatchMessage(sw, msg, new ListenerContext());
    }
    
    public void dispatchMessage(IOFSwitch sw, OFMessage msg, ListenerContext bc) {
        List<IOFMessageListener> theListeners = listeners.get(msg.getType()).getOrderedListeners();
        if (theListeners != null) {
            Command result = Command.CONTINUE;
            Iterator<IOFMessageListener> it = theListeners.iterator();
            if (OFType.PACKET_IN.equals(msg.getType())) {
                OFPacketIn pi = (OFPacketIn)msg;
                Ethernet eth = new Ethernet();
                eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
                IControllerService.bcStore.put(bc, 
                        IControllerService.CONTEXT_PI_PAYLOAD, 
                        eth);
            }
            while (it.hasNext() && !Command.STOP.equals(result)) {
                result = it.next().receive(sw, msg, bc);
            }
        }
    }
    
    @Override
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m, ListenerContext bc) {
        List<IOFMessageListener> msgListeners = null;
        if (listeners.containsKey(m.getType())) {
            msgListeners = listeners.get(m.getType()).getOrderedListeners();
        }
            
        if (msgListeners != null) {                
            for (IOFMessageListener listener : msgListeners) {
                if (listener instanceof IOFSwitchFilter) {
                    if (!((IOFSwitchFilter)listener).isInterested(sw)) {
                        continue;
                    }
                }
                if (Command.STOP.equals(listener.receive(sw, m, bc))) {
                    break;
                }
            }
        }
    }
    
    public void handleOutgoingMessages(IOFSwitch sw, List<OFMessage> msglist, ListenerContext bc) {
        for (OFMessage m:msglist) {
            handleOutgoingMessage(sw, m, bc);
        }
    }

    /**
     * @return the switchListeners
     */
    public List<IOFSwitchListener> getSwitchListeners() {
        return switchListeners;
    }
    
    @Override
    public void terminate() {
    }

    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg) {
        dispatchMessage(sw, msg);
        return true;
    }
    
    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg, 
                                   ListenerContext bContext) {        
        dispatchMessage(sw, msg, bContext);     
        return true;
    }
    
    @Override
    public BasicFactory getOFMessageFactory() {
        return factory;
    }

    @Override
    public void run() {
        logListeners();
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> services =
                new ArrayList<Class<? extends IPlatformService>>(1);
        services.add(IControllerService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
            IPlatformService> m = 
                new HashMap<Class<? extends IPlatformService>,
                        IPlatformService>();
        m.put(IControllerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleDependencies() {
        return null;
    }
    
    @Override
    public void init(ModuleContext context)
                                                 throws ModuleException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void startUp(ModuleContext context) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void addInfoProvider(String type, IInfoProvider provider) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removeInfoProvider(String type, IInfoProvider provider) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Map<String, Object> getControllerInfo(String type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addHAListener(IHAListener listener) {
        haListeners.add(listener);
    }

    @Override
    public void removeHAListener(IHAListener listener) {
        haListeners.remove(listener);
    }
    
    @Override
    public Role getRole() {
        return null;
    }
    
    @Override
    public void setRole(Role role, String roleChangeDescription) {
        
    }
    
    /**
     * Dispatches a new role change notification
     * @param oldRole
     * @param newRole
     */
    public void dispatchRoleChanged(Role oldRole, Role newRole) {
        for (IHAListener rl : haListeners) {
            rl.roleChanged(oldRole, newRole);
        }
    }


    @Override
    public Map<String, String> getControllerNodeIPs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getSystemStartTime() {
        // TODO Auto-generated method stub
        return 0;
    }

    private void logListeners() {
        for (Map.Entry<OFType,
                       ListenerDispatcher<OFType, 
                                          IOFMessageListener>> entry
             : listeners.entrySet()) {
            
            OFType type = entry.getKey();
            ListenerDispatcher<OFType, IOFMessageListener> ldd = 
                    entry.getValue();
            
            StringBuffer sb = new StringBuffer();
            sb.append("OFListeners for ");
            sb.append(type);
            sb.append(": ");
            for (IOFMessageListener l : ldd.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            log.debug(sb.toString());            
        }
    }

    @Override
    public void setAlwaysClearFlowsOnSwAdd(boolean value) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void addOFSwitchDriver(String desc, IOFSwitchDriver driver) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public RoleInfo getRoleInfo() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Long> getMemory() {
        Map<String, Long> m = new HashMap<String, Long>();
        Runtime runtime = Runtime.getRuntime();
        m.put("total", runtime.totalMemory());
        m.put("free", runtime.freeMemory());
        return m;
    }

    @Override
    public Long getUptime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        return rb.getUptime();
    }
}
