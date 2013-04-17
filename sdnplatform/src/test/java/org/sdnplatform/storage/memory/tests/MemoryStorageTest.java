/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University 
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

package org.sdnplatform.storage.memory.tests;

import org.junit.Before;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.storage.tests.StorageTest;

public class MemoryStorageTest extends StorageTest {

    @Before
    public void setUp() throws Exception {
        storageSource = new MemoryStorageSource();
        restApi = new RestApiServer();
        ModuleContext fmc = new ModuleContext();
        fmc.addService(IRestApiService.class, restApi);
        restApi.init(fmc);
        storageSource.init(fmc);
        restApi.startUp(fmc);
        storageSource.startUp(fmc);
        super.setUp();
    }
}
