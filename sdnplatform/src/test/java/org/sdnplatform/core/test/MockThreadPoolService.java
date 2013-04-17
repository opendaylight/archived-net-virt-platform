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

package org.sdnplatform.core.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.threadpool.IThreadPoolService;


public class MockThreadPoolService implements IModule, IThreadPoolService {
    
    protected ScheduledExecutorService mockExecutor = new MockScheduledExecutor();

    /**
     * Return a mock executor that will simply execute each task 
     * synchronously once.
     */
    @Override
    public ScheduledExecutorService getScheduledExecutor() {
        return mockExecutor;
    }

    // IModule
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
            IPlatformService> m = 
                new HashMap<Class<? extends IPlatformService>,
                    IPlatformService>();
        m.put(IThreadPoolService.class, this);
        // We are the class that implements the service
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleDependencies() {
        // No dependencies
        return null;
    }

    @Override
    public void init(ModuleContext context)
                                 throws ModuleException {
    }

    @Override
    public void startUp(ModuleContext context) {
        // no-op
    }

}
