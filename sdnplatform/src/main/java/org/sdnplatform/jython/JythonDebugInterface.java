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

package org.sdnplatform.jython;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JythonDebugInterface implements IModule {
    protected static Logger log = LoggerFactory.getLogger(JythonDebugInterface.class);
    protected JythonServer debug_server;
    protected static int JYTHON_PORT = 6655;
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        // We don't export services
        return null;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        // We don't export services
        return null;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleDependencies() {
        // We don't have any dependencies
        return null;
    }

    @Override
    public void init(ModuleContext context)
             throws ModuleException {
        // no-op
    }

    @Override
    public void startUp(ModuleContext context) {
        Map<String, Object> locals = new HashMap<String, Object>();     
        // add all existing module references to the debug server
        for (Class<? extends IPlatformService> s : context.getAllServices()) {
            // Put only the last part of the name
            String[] bits = s.getCanonicalName().split("\\.");
            String name = bits[bits.length-1];
            locals.put(name, context.getServiceImpl(s));
        }
        
        // read our config options
        Map<String, String> configOptions = context.getConfigParams(this);
        int port = JYTHON_PORT;
        String portNum = configOptions.get("port");
        if (portNum != null) {
            port = Integer.parseInt(portNum);
        }
        
        JythonServer debug_server = new JythonServer(port, locals);
        debug_server.start();
    }
}
