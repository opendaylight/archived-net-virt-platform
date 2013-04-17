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

package org.sdnplatform.core.web;

import java.util.Map;

import org.restlet.resource.Get;
import org.sdnplatform.core.module.ModuleLoaderResource;


public class LoadedModuleLoaderResource extends ModuleLoaderResource {
	/**
	 * Retrieves information about all modules available
	 * to SDN Platform.
	 * @return Information about all modules available.
	 */
    @Get("json")
    public Map<String, Object> retrieve() {
    	return retrieveInternal(true);
    }
}
