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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.ModuleLoader;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IModuleContext;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.test.MockControllerProvider;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.counter.NullCounterStore;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.perfmon.NullPktInProcessingTime;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.topology.TopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TestModuleLoader extends ModuleLoader {
	protected static Logger log = LoggerFactory.getLogger(TestModuleLoader.class);
	
	// List of default modules to use unless specified otherwise
	public static final Class<? extends IModule> DEFAULT_STORAGE_SOURCE =
			MemoryStorageSource.class;
	public static final Class<? extends IModule> DEFAULT_SDNPLATFORM_PRPOVIDER =
			MockControllerProvider.class;
	public static final Class<? extends IModule> DEFAULT_TOPOLOGY_PROVIDER =
			TopologyManager.class;
	public static final Class<? extends IModule> DEFAULT_DEVICE_SERVICE =
			MockDeviceManager.class;
	public static final Class<? extends IModule> DEFAULT_COUNTER_STORE =
			NullCounterStore.class;
	public static final Class<? extends IModule> DEFAULT_THREADPOOL =
			MockThreadPoolService.class;
	public static final Class<? extends IModule> DEFAULT_ENTITY_CLASSIFIER =
			DefaultEntityClassifier.class;
	public static final Class<? extends IModule> DEFAULT_PERFMON =
			NullPktInProcessingTime.class;
	
	protected static final Collection<Class<? extends IModule>> DEFAULT_MODULE_LIST;
	
	static {
		DEFAULT_MODULE_LIST = new ArrayList<Class<? extends IModule>>();
		DEFAULT_MODULE_LIST.add(DEFAULT_DEVICE_SERVICE);
		DEFAULT_MODULE_LIST.add(DEFAULT_SDNPLATFORM_PRPOVIDER);
		DEFAULT_MODULE_LIST.add(DEFAULT_STORAGE_SOURCE);
		DEFAULT_MODULE_LIST.add(DEFAULT_TOPOLOGY_PROVIDER);
		DEFAULT_MODULE_LIST.add(DEFAULT_COUNTER_STORE);
		DEFAULT_MODULE_LIST.add(DEFAULT_THREADPOOL);
		DEFAULT_MODULE_LIST.add(DEFAULT_ENTITY_CLASSIFIER);
		DEFAULT_MODULE_LIST.add(DEFAULT_PERFMON);
	}
	
	protected IModuleContext fmc;
	
	/**
	 * Adds default modules to the list of modules to load. This is done
	 * in order to avoid the module loader throwing errors about duplicate
	 * modules and neither one is specified by the user.
	 * @param userModules The list of user specified modules to add to.
	 */
	protected void addDefaultModules(Collection<Class<? extends IModule>> userModules) {
		Collection<Class<? extends IModule>> defaultModules =
				new ArrayList<Class<? extends IModule>>(DEFAULT_MODULE_LIST.size());
		defaultModules.addAll(DEFAULT_MODULE_LIST);
		
		Iterator<Class<? extends IModule>> modIter = userModules.iterator();
		while (modIter.hasNext()) {
			Class<? extends IModule> userMod = modIter.next();
			Iterator<Class<? extends IModule>> dmIter = defaultModules.iterator();
			while (dmIter.hasNext()) {
				Class<? extends IModule> dmMod = dmIter.next();
				Collection<Class<? extends IPlatformService>> userModServs;
				Collection<Class<? extends IPlatformService>> dmModServs;
				try {
					dmModServs = dmMod.newInstance().getModuleServices();
					userModServs = userMod.newInstance().getModuleServices();
				} catch (InstantiationException e) {
					log.error(e.getMessage());
					break;
				} catch (IllegalAccessException e) {
					log.error(e.getMessage());
					break;
				}
				
				// If either of these are null continue as they have no services
				if (dmModServs == null || userModServs == null) continue;
				
				// If the user supplied modules has a service
				// that is in the default module list we remove
				// the default module from the list.
				boolean shouldBreak = false;
				Iterator<Class<? extends IPlatformService>> userModServsIter 
					= userModServs.iterator();
				while (userModServsIter.hasNext()) {
					Class<? extends IPlatformService> userModServIntf = userModServsIter.next();
					Iterator<Class<? extends IPlatformService>> dmModsServsIter 
						= dmModServs.iterator();
					while (dmModsServsIter.hasNext()) {
						Class<? extends IPlatformService> dmModServIntf 
							= dmModsServsIter.next();
						
						if (dmModServIntf.getCanonicalName().equals(
								userModServIntf.getCanonicalName())) {
							logger.debug("Removing default module {} because it was " +
									"overriden by an explicitly specified module",
									dmModServIntf.getCanonicalName());
							dmIter.remove();
							shouldBreak = true;
							break;
						}
					}
					if (shouldBreak) break;
				}
				if (shouldBreak) break;
			}
		}
		
		// Append the remaining default modules to the user specified ones.
		// This avoids the module loader throwing duplicate module errors.
		userModules.addAll(defaultModules);
		log.debug("Using module set " + userModules.toString());
	}
	
	/**
	 * Sets up all modules and their dependencies.
	 * @param modules The list of modules that the user wants to load.
	 * @param mockedServices The list of services that will be mocked. Any
	 * module that provides this service will not be loaded.
	 */
	public void setupModules(Collection<Class<? extends IModule>> modules,
			Collection<IPlatformService> mockedServices) {
		Collection<String> modulesAsString = new ArrayList<String>();
		for (Class<? extends IModule> m : modules) {
			modulesAsString.add(m.getCanonicalName());
		}
		
		try {
			fmc = loadModulesFromList(modulesAsString, null, mockedServices);
		} catch (ModuleException e) {
			log.error(e.getMessage());
		}
	}
	
	/**
	 * Gets the inited/started instance of a module from the context.
	 * @param ifl The name if the module to get, i.e. "LearningSwitch.class".
	 * @return The inited/started instance of the module.
	 */
	public IModule getModuleByName(Class<? extends IModule> ifl) {
		Collection<IModule> modules = fmc.getAllModules();
		for (IModule m : modules) {
			if (ifl.getCanonicalName().equals(m.getClass().getCanonicalName())) {
				return m;
			}
		}
		return null;
	}
	
	/**
	 * Gets an inited/started instance of a service from the context.
	 * @param ifs The name of the service to get, i.e. "ITopologyService.class".
	 * @return The inited/started instance of the service from teh context.
	 */
	public IPlatformService getModuleByService(Class<? extends IPlatformService> ifs) {
		Collection<IModule> modules = fmc.getAllModules();
		for (IModule m : modules) {
			Collection<Class<? extends IPlatformService>> mServs = m.getModuleServices();
			if (mServs == null) continue;
			for (Class<? extends IPlatformService> mServClass : mServs) {
				if (mServClass.getCanonicalName().equals(ifs.getCanonicalName())) {
					assert(m instanceof IPlatformService);
					return (IPlatformService)m;
				}
			}
		}
		return null;
	}
}
