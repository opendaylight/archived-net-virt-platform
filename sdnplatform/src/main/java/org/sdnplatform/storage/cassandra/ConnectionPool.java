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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sdnplatform.storage.StorageException;


public class ConnectionPool {
    
    private String host;
    private int port;
    private String keyspace;
    private String user;
    private String password;
    private List<Connection> connectionList;
    
    public ConnectionPool(String host, int port, String keyspace, String user, String password) {
        this.host = host;
        this.port = port;
        this.keyspace = keyspace;
        this.user = user;
        this.password = password;
        this.connectionList = new ArrayList<Connection>();
    }
    
    public synchronized Connection acquireConnection() {
        // See if there's an existing connection that's available
        for (Connection connection: connectionList) {
            if (!connection.getInUse()) {
                connection.setInUse(true);
                return connection;
            }
        }
        
        // No existing connection, so create a new one
        Connection connection = new Connection(host, port);
        try {
            connection.setKeyspace(keyspace);
        }
        catch (StorageException exc) {
            // FIXME: Not sure if we should support creating the keyspace here.
            // Right now the replication factor and strategy are hard-coded, which is bad.
            // Currently for real applications you'd want to have some other process
            // (e.g. Django) create the keyspace and column families with the appropriate
            // settings and indexed columns.
            Map<String,String> strategyOptions = new HashMap<String,String>();
            strategyOptions.put("replication_factor", "1");
            connection.createKeyspace(keyspace, "org.apache.cassandra.locator.SimpleStrategy", strategyOptions);
            connection.setKeyspace(keyspace);
        }
        if (user != null)
            connection.login(user, password);
        connectionList.add(connection);
        
        connection.setInUse(true);
        
        return connection;
    }
    
    public synchronized void releaseConnection(Connection connection) {
        connection.setInUse(false);
    }
}
