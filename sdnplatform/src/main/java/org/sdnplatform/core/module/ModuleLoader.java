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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;


import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds all SDN Platform modules in the class path and loads/starts them.
 * @author alexreimers
 *
 */
public class ModuleLoader {
    protected static Logger logger = 
            LoggerFactory.getLogger(ModuleLoader.class);

    protected static Map<Class<? extends IPlatformService>,
                  Collection<IModule>> serviceMap;
    protected static Map<IModule,
                  Collection<Class<? extends 
                                   IPlatformService>>> moduleServiceMap;
    protected static Map<String, IModule> moduleNameMap;
    protected static Object lock = new Object();
    
    protected ModuleContext moduleContext;
    
    public static final String COMPILED_CONF_FILE = 
            "sdnplatformdefault.properties";
    public static final String SDNPLATFORM_MODULES_KEY =
            "sdnplatform.modules";
    
    public ModuleLoader() {
        moduleContext = new ModuleContext();
    }
    
    /**
     * Finds all IModule(s) in the classpath. It creates 3 Maps.
     * serviceMap -> Maps a service to a module
     * moduleServiceMap -> Maps a module to all the services it provides
     * moduleNameMap -> Maps the string name to the module
     * @throws ModuleException If two modules are specified in the configuration
     * that provide the same service.
     */
    protected static void findAllModules(Collection<String> mList) throws ModuleException {
        synchronized (lock) {
            if (serviceMap != null) return;
            serviceMap = 
                    new HashMap<Class<? extends IPlatformService>,
                                Collection<IModule>>();
            moduleServiceMap = 
                    new HashMap<IModule,
                                Collection<Class<? extends 
                                           IPlatformService>>>();
            moduleNameMap = new HashMap<String, IModule>();
            
            // Get all the current modules in the classpath
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            ServiceLoader<IModule> moduleLoader
                = ServiceLoader.load(IModule.class, cl);
            // Iterate for each module, iterate through and add it's services
            Iterator<IModule> moduleIter = moduleLoader.iterator();
            while (moduleIter.hasNext()) {
                IModule m = null;
                try {
                    m = moduleIter.next();
                } catch (ServiceConfigurationError sce) {
                    logger.debug("Could not find module");
                    //moduleIter.remove();
                    continue;
                }
            //}
            //for (IModule m : moduleLoader) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Found module " + m.getClass().getName());
                }

                // Set up moduleNameMap
                moduleNameMap.put(m.getClass().getCanonicalName(), m);

                // Set up serviceMap
                Collection<Class<? extends IPlatformService>> servs =
                        m.getModuleServices();
                if (servs != null) {
                    moduleServiceMap.put(m, servs);
                    for (Class<? extends IPlatformService> s : servs) {
                        Collection<IModule> mods = 
                                serviceMap.get(s);
                        if (mods == null) {
                            mods = new ArrayList<IModule>();
                            serviceMap.put(s, mods);
                        }
                        mods.add(m);
                        // Make sure they haven't specified duplicate modules in the config
                        int dupInConf = 0;
                        for (IModule cMod : mods) {
                            if (mList.contains(cMod.getClass().getCanonicalName()))
                                dupInConf += 1;
                        }
                        
                        if (dupInConf > 1) {
                            String duplicateMods = "";
                            for (IModule mod : mods) {
                                duplicateMods += mod.getClass().getCanonicalName() + ", ";
                            }
                            throw new ModuleException("ERROR! The configuraiton" +
                                    " file specifies more than one module that provides the service " +
                                    s.getCanonicalName() +". Please specify only ONE of the " +
                                    "following modules in the config file: " + duplicateMods);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Loads the modules from a specified configuration file.
     * @param fName The configuration file path
     * @return An IModuleContext with all the modules to be started
     * @throws ModuleException
     */
    @LogMessageDocs({
        @LogMessageDoc(level="INFO",
                message="Loading modules from file {file name}",
                explanation="The controller is initializing its module " +
                        "configuration from the specified properties file"),
        @LogMessageDoc(level="INFO",
                message="Loading default modules",
                explanation="The controller is initializing its module " +
                        "configuration to the default configuration"),
        @LogMessageDoc(level="ERROR",
                message="Could not load module configuration file",
                explanation="The controller failed to read the " +
                        "module configuration file",
                recommendation="Verify that the module configuration is " +
                        "present. " + LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Could not load default modules",
                explanation="The controller failed to read the default " +
                        "module configuration",
                recommendation=LogMessageDoc.CHECK_CONTROLLER)
    })
    public IModuleContext loadModulesFromConfig(String fName) 
            throws ModuleException {
        Properties prop = new Properties();
        
        File f = new File(fName);
        if (f.isFile()) {
            logger.info("Loading modules from file {}", fName);
            try {
                prop.load(new FileInputStream(fName));
            } catch (Exception e) {
                logger.error("Could not load module configuration file", e);
                System.exit(1);
            }
        } else {
            logger.info("Loading default modules");
            InputStream is = this.getClass().getClassLoader().
                                    getResourceAsStream(COMPILED_CONF_FILE);
            try {
                prop.load(is);
            } catch (IOException e) {
                logger.error("Could not load default modules", e);
                System.exit(1);
            }
        }
        
        String moduleList = prop.getProperty(SDNPLATFORM_MODULES_KEY)
                                .replaceAll("\\s", "");
        Collection<String> configMods = new ArrayList<String>();
        configMods.addAll(Arrays.asList(moduleList.split(",")));
        return loadModulesFromList(configMods, prop);
    }
    
    /**
     * Loads modules (and their dependencies) specified in the list
     * @param mList The array of fully qualified module names
     * @param ignoreList The list of SDN Platform services NOT to 
     * load modules for. Used for unit testing.
     * @return The ModuleContext containing all the loaded modules
     * @throws ModuleException
     */
    protected IModuleContext loadModulesFromList(Collection<String> configMods, Properties prop, 
            Collection<IPlatformService> ignoreList) throws ModuleException {
        logger.debug("Starting module loader");
        if (logger.isDebugEnabled() && ignoreList != null)
            logger.debug("Not loading module services " + ignoreList.toString());

        findAllModules(configMods);
        
        Collection<IModule> moduleSet = new ArrayList<IModule>();
        Map<Class<? extends IPlatformService>, IModule> moduleMap =
                new HashMap<Class<? extends IPlatformService>,
                            IModule>();

        Queue<String> moduleQ = new LinkedList<String>();
        // Add the explicitly configured modules to the q
        moduleQ.addAll(configMods);
        Set<String> modsVisited = new HashSet<String>();
        
        while (!moduleQ.isEmpty()) {
            String moduleName = moduleQ.remove();
            if (modsVisited.contains(moduleName))
                continue;
            modsVisited.add(moduleName);
            IModule module = moduleNameMap.get(moduleName);
            if (module == null) {
                throw new ModuleException("Module " + 
                        moduleName + " not found");
            }
            // If the module provies a service that is in the
            // services ignorelist don't load it.
            if ((ignoreList != null) && (module.getModuleServices() != null)) {
                for (IPlatformService ifs : ignoreList) {
                    for (Class<?> intsIgnore : ifs.getClass().getInterfaces()) {
                        //System.out.println(intsIgnore.getName());
                        // Check that the interface extends IPlatformService
                        //if (intsIgnore.isAssignableFrom(IPlatformService.class)) {
                        //System.out.println(module.getClass().getName());
                        if (intsIgnore.isAssignableFrom(module.getClass())) {
                            // We now ignore loading this module.
                            logger.debug("Not loading module " + 
                                         module.getClass().getCanonicalName() +
                                         " because interface " +
                                         intsIgnore.getCanonicalName() +
                                         " is in the ignore list.");
                            
                            continue;
                        }
                        //}
                    }
                }
            }
            
            // Add the module to be loaded
            addModule(moduleMap, moduleSet, module);
            // Add it's dep's to the queue
            Collection<Class<? extends IPlatformService>> deps = 
                    module.getModuleDependencies();
            if (deps != null) {
                for (Class<? extends IPlatformService> c : deps) {
                    IModule m = moduleMap.get(c);
                    if (m == null) {
                        Collection<IModule> mods = serviceMap.get(c);
                        // Make sure only one module is loaded
                        if ((mods == null) || (mods.size() == 0)) {
                            throw new ModuleException("ERROR! Could not " +
                                    "find an IModule that provides service " +
                                    c.toString());
                        } else if (mods.size() == 1) {
                            IModule mod = mods.iterator().next();
                            if (!modsVisited.contains(mod.getClass().getCanonicalName()))
                                moduleQ.add(mod.getClass().getCanonicalName());
                        } else {
                            boolean found = false;
                            for (IModule moduleDep : mods) {
                                if (configMods.contains(moduleDep.getClass().getCanonicalName())) {
                                    // Module will be loaded, we can continue
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                String duplicateMods = "";
                                for (IModule mod : mods) {
                                    duplicateMods += mod.getClass().getCanonicalName() + ", ";
                                }
                                throw new ModuleException("ERROR! Found more " + 
                                    "than one (" + mods.size() + ") IModules that provides " +
                                    "service " + c.toString() + 
                                    ". Please specify one of the following modules in the config: " + 
                                    duplicateMods);
                            }
                        }
                    }
                }
            }
        }
        
        moduleContext.setModuleSet(moduleSet);
        parseConfigParameters(prop);
        initModules(moduleSet);
        startupModules(moduleSet);
        
        return moduleContext;
    }
    
    /**
     * Loads modules (and their dependencies) specified in the list.
     * @param configMods The collection of fully qualified module names to load.
     * @param prop The list of properties that are configuration options.
     * @return The ModuleContext containing all the loaded modules.
     * @throws ModuleException
     */
    public IModuleContext loadModulesFromList(Collection<String> configMods, Properties prop) 
            throws ModuleException {
        return loadModulesFromList(configMods, prop, null);
    }
    
    /**
     * Add a module to the set of modules to load and register its services
     * @param moduleMap the module map
     * @param moduleSet the module set
     * @param module the module to add
     */
    protected void addModule(Map<Class<? extends IPlatformService>, 
                                           IModule> moduleMap,
                            Collection<IModule> moduleSet,
                            IModule module) {
        if (!moduleSet.contains(module)) {
            Collection<Class<? extends IPlatformService>> servs =
                    moduleServiceMap.get(module);
            if (servs != null) {
                for (Class<? extends IPlatformService> c : servs)
                    moduleMap.put(c, module);
            }
            moduleSet.add(module);
        }
    }

    /**
     * Allocate  service implementations and then init all the modules
     * @param moduleSet The set of modules to call their init function on
     */
    protected void initModules(Collection<IModule> moduleSet) {
        for (IModule module : moduleSet) {            
            // Get the module's service instance(s)
            Map<Class<? extends IPlatformService>, 
                IPlatformService> simpls = module.getServiceImpls();

            // add its services to the context
            if (simpls != null) {
                for (Entry<Class<? extends IPlatformService>, 
                        IPlatformService> s : simpls.entrySet()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Setting " + s.getValue() + 
                                     "  as provider for " + 
                                     s.getKey().getCanonicalName());
                    }
                    moduleContext.addService(s.getKey(), s.getValue());
                }
            }
        }
        
        for (IModule module : moduleSet) {
            // init the module
            if (logger.isDebugEnabled()) {
                logger.debug("Initializing " + 
                             module.getClass().getCanonicalName());
            }
            module.init(moduleContext);
        }
    }
    
    /**
     * Call each loaded module's startup method
     * @param moduleSet the module set to start up
     */
    protected void startupModules(Collection<IModule> moduleSet) {
        for (IModule m : moduleSet) {
            if (logger.isDebugEnabled()) {
                logger.debug("Starting " + m.getClass().getCanonicalName());
            }
            m.startUp(moduleContext);
        }
    }
    
    /**
     * Parses configuration parameters for each module
     * @param prop The properties file to use
     */
    @LogMessageDoc(level="WARN",
                   message="Module {module} not found or loaded. " +
                           "Not adding configuration option {key} = {value}",
                   explanation="Ignoring a configuration parameter for a " +
                        "module that is not loaded.")
    protected void parseConfigParameters(Properties prop) {
        if (prop == null) return;
        
        Enumeration<?> e = prop.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            // Ignore module list key
            if (key.equals(SDNPLATFORM_MODULES_KEY)) {
                continue;
            }
            
            String configValue = null;
            int lastPeriod = key.lastIndexOf(".");
            String moduleName = key.substring(0, lastPeriod);
            String configKey = key.substring(lastPeriod + 1);
            // Check to see if it's overridden on the command line
            String systemKey = System.getProperty(key);
            if (systemKey != null) {
                configValue = systemKey;
            } else {
                configValue = prop.getProperty(key);
            }
            
            IModule mod = moduleNameMap.get(moduleName);
            if (mod == null) {
                logger.warn("Module {} not found or loaded. " +
                            "Not adding configuration option {} = {}", 
                            new Object[]{moduleName, configKey, configValue});
            } else {
                moduleContext.addConfigParam(mod, configKey, configValue);
            }
        }
    }
}
