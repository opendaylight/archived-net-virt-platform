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

package org.sdnplatform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchDriver;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.restserver.IRestApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BetterDriverManager implements IModule, IOFSwitchDriver {
    protected static Logger log =
            LoggerFactory.getLogger(BetterDriverManager.class.getName());
    
    // **************
    // Module members
    // **************
    
    protected IControllerService controllerProvider;
    protected IRestApiService restApi;
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IControllerService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        controllerProvider =
                context.getServiceImpl(IControllerService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(ModuleContext context) {
        // For BigBench
        controllerProvider.addOFSwitchDriver("big switch networks", this);        
        // For BigTest OVS and VTA
        controllerProvider.addOFSwitchDriver("Big Switch Networks", this);        
        // Default driver, should match all
        controllerProvider.addOFSwitchDriver("", this);
    }

    @Override
    public IOFSwitch getOFSwitchImpl(String regis_desc,
                                     OFDescriptionStatistics description) {
        String model = description.getDatapathDescription();
 
        if (regis_desc.startsWith("big switch networks")) {
            // BigTest Mininet, avoid OVS optimization
            if (model.startsWith("bigtest")) {
                return new BetterOFSwitchImpl();
            }
        }
        if (regis_desc.equals("Big Switch Networks")) {
            if (description.getHardwareDescription().startsWith("Open vSwitch")) {
                return new BetterOFSwitchBSNOVS();
            } else if (description.getHardwareDescription().startsWith("Xenon")) {
                return new BetterOFSwitchXenon();
            }
        }
        if (regis_desc.equals("")) {
            String vendor = description.getManufacturerDescription();
            String make = description.getHardwareDescription();
            // Production OVS, optimize topology probing
            if (vendor.startsWith("Nicira") &&
                    make.startsWith("Open vSwitch")) {
                return new BetterOFSwitchOVS();
            }
            return new BetterOFSwitchImpl();
        }
        log.warn("Unknown model registered: " + regis_desc);
        return null;
    }

}
