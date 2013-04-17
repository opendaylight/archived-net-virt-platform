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

package org.sdnplatform.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.openflow.protocol.OFMatchWithSwDpid;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.SwitchPort;


public class FlowCache implements IModule, IFlowCacheService {

    @Override
    public void submitFlowCacheQuery(FCQueryObj query) {}

    @Override
    public void deleteFlowCacheBySwitch(long switchDpid) {}

    @Override
    public void updateFlush() {}
    
    @Override
    public boolean addFlow(String appInstName, OFMatchWithSwDpid ofm, 
                           Long cookie, long srcSwDpid, 
                           short inPort, short priority, byte action) {
        return true;
    }

    @Override
    public boolean addFlow(ListenerContext cntx, OFMatchWithSwDpid ofm, 
                           Long cookie, SwitchPort swPort, 
                           short priority, byte action) {
        return true;
    }

    @Override
    public boolean moveFlowToDifferentApplInstName(OFMatchReconcile ofMRc) {
        return true;
    }

    @Override
    public void deleteAllFlowsAtASourceSwitch(IOFSwitch sw) {}
    
    @Override
    public void querySwitchFlowTable(long swDpid) {}
    
    // IModule

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
       l.add(IFlowCacheService.class);
       return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> 
                                                            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m = 
            new HashMap<Class<? extends IPlatformService>,
                IPlatformService>();
        m.put(IFlowCacheService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> 
                                                    getModuleDependencies() {
        return null;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {}

    @Override
    public void startUp(ModuleContext context) {}
}
