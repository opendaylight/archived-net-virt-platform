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

package org.sdnplatform.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.sdnplatform.core.internal.Controller;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.counter.ICounterStoreService;
import org.sdnplatform.flowcache.IFlowCacheService;
import org.sdnplatform.perfmon.IPktInProcessingTimeService;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.threadpool.IThreadPoolService;


public class ControllerProvider implements IModule {
    Controller controller;
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> services =
                new ArrayList<Class<? extends IPlatformService>>(1);
        services.add(IControllerService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IPlatformService>,
               IPlatformService> getServiceImpls() {
        controller = new Controller();
        
        Map<Class<? extends IPlatformService>,
            IPlatformService> m = 
                new HashMap<Class<? extends IPlatformService>,
                            IPlatformService>();
        m.put(IControllerService.class, controller);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> dependencies =
            new ArrayList<Class<? extends IPlatformService>>(4);
        dependencies.add(IStorageSourceService.class);
        dependencies.add(IPktInProcessingTimeService.class);
        dependencies.add(IRestApiService.class);
        dependencies.add(ICounterStoreService.class);
        dependencies.add(IFlowCacheService.class);
        dependencies.add(IThreadPoolService.class);
        return dependencies;
    }

    @Override
    public void init(ModuleContext context) throws ModuleException {
       controller.setStorageSourceService(
           context.getServiceImpl(IStorageSourceService.class));
       controller.setPktInProcessingService(
           context.getServiceImpl(IPktInProcessingTimeService.class));
       controller.setCounterStore(
           context.getServiceImpl(ICounterStoreService.class));
       controller.setFlowCacheMgr(
           context.getServiceImpl(IFlowCacheService.class));
       controller.setRestApiService(
           context.getServiceImpl(IRestApiService.class));
       controller.setThreadPoolService(
           context.getServiceImpl(IThreadPoolService.class));
       controller.init(context.getConfigParams(this));
    }

    @Override
    public void startUp(ModuleContext context) {
        controller.startupComponents();
    }
}
