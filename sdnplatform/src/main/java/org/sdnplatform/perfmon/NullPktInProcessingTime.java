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

package org.sdnplatform.perfmon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMessage;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;


/**
 * An IPktInProcessingTimeService implementation that does nothing.
 * This is used mainly for performance testing or if you don't
 * want to use the IPktInProcessingTimeService features.
 * @author alexreimers
 *
 */
public class NullPktInProcessingTime 
    implements IModule, IPktInProcessingTimeService {
    
    private CumulativeTimeBucket ctb;
    private boolean inited = false;
    
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IPktInProcessingTimeService.class);
        return l;
    }
    
    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m = 
            new HashMap<Class<? extends IPlatformService>,
                        IPlatformService>();
        // We are the class that implements the service
        m.put(IPktInProcessingTimeService.class, this);
        return m;
    }
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        // We don't have any dependencies
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

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void bootstrap(List<IOFMessageListener> listeners) {
        if (!inited)
            ctb = new CumulativeTimeBucket(listeners);
    }

    @Override
    public void recordStartTimeComp(IOFMessageListener listener) {

    }

    @Override
    public void recordEndTimeComp(IOFMessageListener listener) {

    }

    @Override
    public void recordStartTimePktIn() {

    }

    @Override
    public void recordEndTimePktIn(IOFSwitch sw, OFMessage m,
                                   ListenerContext cntx) {
        
    }

    @Override
    public void setEnabled(boolean enabled) {
    
    }

    @Override
    public CumulativeTimeBucket getCtb() {
        return ctb;
    }
}
