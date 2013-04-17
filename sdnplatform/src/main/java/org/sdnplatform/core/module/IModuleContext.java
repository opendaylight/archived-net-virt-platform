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
import java.util.Map;

	
public interface IModuleContext {	
    /**
     * Retrieves a casted version of a module from the registry.
     * @param name The IPlatformService object type
     * @return The IPlatformService
     * @throws ModuleException If the module was not found 
     * or a ClassCastException was encountered.
     */
    public <T extends IPlatformService> T getServiceImpl(Class<T> service);
    
    /**
     * Returns all loaded services
     * @return A collection of service classes that have been loaded
     */
    public Collection<Class<? extends IPlatformService>> getAllServices();
    
    /**
     * Returns all loaded modules
     * @return All SDN Platform modules that are going to be loaded
     */
    public Collection<IModule> getAllModules();
    
    /**
     * Gets module specific configuration parameters.
     * @param module The module to get the configuration parameters for
     * @return A key, value map of the configuration options
     */
    public Map<String, String> getConfigParams(IModule module);
}
