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

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.sdnplatform.core.internal.CmdLineSettings;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.ModuleLoader;
import org.sdnplatform.core.module.IModuleContext;
import org.sdnplatform.restserver.IRestApiService;


/**
 * Host for the SDN Platform main method
 * @author alexreimers
 */
public class Main {

    /**
     * Main method to load configuration and modules
     * @param args
     * @throws ModuleException 
     */
    public static void main(String[] args) throws ModuleException {
        // Setup logger
        System.setProperty("org.restlet.engine.loggerFacadeClass", 
                "org.restlet.ext.slf4j.Slf4jLoggerFacade");
        
        CmdLineSettings settings = new CmdLineSettings();
        CmdLineParser parser = new CmdLineParser(settings);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.out);
            System.exit(1);
        }
        
        // Load modules
        ModuleLoader fml = new ModuleLoader();
        IModuleContext moduleContext = fml.loadModulesFromConfig(settings.getModuleFile());
        // Run REST server
        IRestApiService restApi = moduleContext.getServiceImpl(IRestApiService.class);
        restApi.run();
        // Run the main sdnplatform module
        IControllerService controller =
                moduleContext.getServiceImpl(IControllerService.class);
        // This call blocks, it has to be the last line in the main
        controller.run();
    }
}
