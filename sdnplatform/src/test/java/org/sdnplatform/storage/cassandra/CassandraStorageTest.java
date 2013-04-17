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

package org.sdnplatform.storage.cassandra;

import java.util.HashMap;
import java.util.Map;

import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.storage.cassandra.CassandraStorageSource;
import org.sdnplatform.storage.cassandra.Connection;
import org.sdnplatform.storage.cassandra.ConnectionPool;
import org.sdnplatform.storage.tests.StorageTest;


public class CassandraStorageTest extends StorageTest {

    private static final String HOST = null;
    private static final int PORT = 0;
    private static final String KEYSPACE = "SDNPlatformStorageUnitTest";
    private static final String USER = null;
    private static final String PASSWORD = null;
    
    static boolean keyspaceInitialized = false;
    
    private void initKeyspace() {
        
        if (keyspaceInitialized)
            return;
        
        keyspaceInitialized = true;
        
        Connection connection = null;
        
        try {
            connection = new Connection(HOST, PORT);
            connection.login(USER,PASSWORD);
            try {
                Map<String,String> strategyOptions = new HashMap<String,String>();
                strategyOptions.put("replication_factor", "1");
                connection.createKeyspace(KEYSPACE, "org.apache.cassandra.locator.SimpleStrategy", strategyOptions);
            }
            catch (StorageException exc) {
                // Keyspace may already exist, which isn't an error, so we just ignore.
                // We may still need to create the column family though, which is why we
                // have this inner try/catch handler
            }
            connection.setKeyspace(KEYSPACE);
            try {
                connection.dropColumnFamily(PERSON_TABLE_NAME);
            }
            catch (StorageException exc) {
                // Column family may not exist yet, but that's OK, so we just ignore it.
            }
        }
        catch (StorageException exc) {
            // The keyspace and column families may already exist,
            // so we just ignore the error.
        }
        finally {
            if (connection != null)
                connection.close();
        }
    }
    
    private void resetTables() {
        Connection connection = null;
        try {
            connection = new Connection(HOST, PORT);
            connection.setKeyspace(KEYSPACE);
            connection.login(USER, PASSWORD);
            connection.truncate(PERSON_TABLE_NAME);
        }
        finally {
            if (connection != null)
                connection.close();
        }
    }
    
    
    public void setUp() throws Exception {
        initKeyspace();
        ModuleContext fmc = new ModuleContext();
        CassandraStorageSource cassandraStorageSource = new CassandraStorageSource();
        RestApiServer restApi = new RestApiServer();
        fmc.addService(IStorageSourceService.class, cassandraStorageSource);
        fmc.addService(IRestApiService.class, restApi);
        restApi.init(fmc);
        restApi.startUp(fmc);
        cassandraStorageSource.init(fmc);
        cassandraStorageSource.startUp(fmc);
        cassandraStorageSource.setConnectionPool(new ConnectionPool(HOST, PORT, KEYSPACE, USER, PASSWORD));
        storageSource = cassandraStorageSource;
        super.setUp();
    }

    public void tearDown() throws Exception {
        resetTables();
    }
}
