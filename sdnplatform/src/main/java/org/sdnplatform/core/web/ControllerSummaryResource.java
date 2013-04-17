/*
 * Copyright (c) 2012,2013 Big Switch Networks, Inc.
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
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.IControllerService;


/**
 * Get summary counters registered by all modules
 * @author shudongz
 */
public class ControllerSummaryResource extends ServerResource {
    @Get("json")
    public Map<String, Object> retrieve() {
        IControllerService controllerProvider = 
            (IControllerService)getContext().getAttributes().
                get(IControllerService.class.getCanonicalName());
        return controllerProvider.getControllerInfo("summary");
    }

}
