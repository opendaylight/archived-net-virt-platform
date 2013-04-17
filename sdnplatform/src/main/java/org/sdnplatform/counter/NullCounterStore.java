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

package org.sdnplatform.counter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMessage;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.counter.CounterStore.NetworkLayer;
import org.sdnplatform.counter.CounterValue.CounterType;
import org.sdnplatform.packet.Ethernet;


/**
 * An ICounsterStoreService implementation that does nothing.
 * This is used mainly for performance testing or if you don't
 * want to use the counterstore.
 * @author alexreimers
 *
 */
public class NullCounterStore implements IModule,
        ICounterStoreService {

    private ICounter emptyCounter;
    private List<String> emptyList;
    private Map<String, ICounter> emptyMap;
    
    @Override
    public void updatePacketInCounters(IOFSwitch sw, OFMessage m, Ethernet eth) {
        // no-op
    }

    @Override
    public void updatePacketInCountersLocal(IOFSwitch sw, OFMessage m, Ethernet eth) {
        // no-op
    }

    @Override
    public void updatePktOutFMCounterStore(IOFSwitch sw, OFMessage ofMsg) {
        // no-op
    }

    @Override
    public void updatePktOutFMCounterStoreLocal(IOFSwitch sw, OFMessage ofMsg) {
        // no-op
    }

    @Override
    public void updateFlush() {
        // no-op
    }

    @Override
    public List<String>
            getAllCategories(String counterName, NetworkLayer layer) {
        return emptyList;
    }

    @Override
    public ICounter createCounter(String key, CounterType type) {
        return emptyCounter;
    }

    @Override
    public ICounter getCounter(String key) {
        return emptyCounter;
    }

    @Override
    public Map<String, ICounter> getAll() {
        return emptyMap;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> services =
                new ArrayList<Class<? extends IPlatformService>>(1);
        services.add(ICounterStoreService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
            IPlatformService> m = 
                new HashMap<Class<? extends IPlatformService>,
                        IPlatformService>();
        m.put(ICounterStoreService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleDependencies() {
        // None, return null
        return null;
    }

    @Override
    public void init(ModuleContext context)
                             throws ModuleException {
        emptyCounter = new SimpleCounter(new Date(), CounterType.LONG);
        emptyList = new ArrayList<String>();
        emptyMap = new HashMap<String, ICounter>();
    }

    @Override
    public void startUp(ModuleContext context) {
        // no-op
    }
}
