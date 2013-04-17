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

package org.sdnplatform.core.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The service registry for an IControllerProvider.
 * @author alexreimers
 */
public class ModuleContext implements IModuleContext {
	protected Map<Class<? extends IPlatformService>, IPlatformService> serviceMap;
	protected Map<Class<? extends IModule>, Map<String, String>> configParams;
	protected Collection<IModule> moduleSet;
	
	/**
	 * Creates the ModuleContext for use with this IControllerProvider.
	 * This will be used as a module registry for all IModule(s).
	 */
	public ModuleContext() {
		serviceMap = 
		        new HashMap<Class<? extends IPlatformService>,
		                              IPlatformService>();
		configParams =
		        new HashMap<Class<? extends IModule>,
		                        Map<String, String>>();
	}
	
	/**
	 * Adds a IModule for this Context.
	 * @param clazz the service class
	 * @param service The IPlatformService to add to the registry
	 * @throws ModuleException 
	 */
	public void addService(Class<? extends IPlatformService> clazz, 
	                       IPlatformService service) {
	    if (serviceMap.containsKey(clazz)) {
	        String msg = String.format("Can't add implementation %s for service"
	                + " %s, already provided by %s",
	                clazz.getName(),
	                service.getClass().getName(),
	                serviceMap.get(clazz).getClass().getName());
	        throw new ModuleException(msg);
	    }
		serviceMap.put(clazz, service);
	}
	
	@SuppressWarnings("unchecked")
    @Override
	public <T extends IPlatformService> T getServiceImpl(Class<T> service) {
	    IPlatformService s = serviceMap.get(service);
		return (T)s;
	}
	
	@Override
	public Collection<Class<? extends IPlatformService>> getAllServices() {
	    return serviceMap.keySet();
	}
	
	@Override
	public Collection<IModule> getAllModules() {
	    return moduleSet;
	}
	
	public void setModuleSet(Collection<IModule> modSet) {
	    this.moduleSet = modSet;
	}
	
	/**
	 * Gets the configuration parameter map for a module
	 * @param module The module to get the configuration map for, usually yourself
	 * @return A map containing all the configuration parameters for the module, may be empty
	 */
	@Override
	public Map<String, String> getConfigParams(IModule module) {
	    Map<String, String> retMap = configParams.get(module.getClass());
	    if (retMap == null) {
	        // Return an empty map if none exists so the module does not
	        // need to null check the map
	        retMap = new HashMap<String, String>();
	        configParams.put(module.getClass(), retMap);
	    }

	    return retMap;
	}
	
	/**
	 * Adds a configuration parameter for a module
	 * @param mod The fully qualified module name to add the parameter to
	 * @param key The configuration parameter key
	 * @param value The configuration parameter value
	 */
	public void addConfigParam(IModule mod, String key, String value) {
	    Map<String, String> moduleParams = configParams.get(mod.getClass());
	    if (moduleParams == null) {
	        moduleParams = new HashMap<String, String>();
	        configParams.put(mod.getClass(), moduleParams);
	    }
	    moduleParams.put(key, value);
	}
 }
