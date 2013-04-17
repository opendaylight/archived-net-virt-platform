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

package org.sdnplatform.core.internal;

import org.kohsuke.args4j.Option;

/**
 * Expresses the port settings of OpenFlow controller.
 */
public class CmdLineSettings {
    public static final String DEFAULT_CONFIG_FILE = "config/sdnplatform.properties";

    @Option(name="-cf", aliases="--configFile", metaVar="FILE", usage="SDN Platform configuration file")
    private String configFile = DEFAULT_CONFIG_FILE;
    
    public String getModuleFile() {
    	return configFile;
    }
}
