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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cassandra.thrift.ConsistencyLevel;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.storage.nosql.NoSqlStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class CassandraStorageSource extends NoSqlStorageSource {
    private ConnectionPool connectionPool;
    protected static Logger log = LoggerFactory.getLogger(CassandraStorageSource.class);
    
    public CassandraStorageSource() {
        super();
    }
    
    protected List<Map<String,Object>> getAllRows(String tableName, String[] columnNameList) {
        Connection connection = null;
        try {
            connection = connectionPool.acquireConnection();
            String primaryKeyName = getTablePrimaryKeyName(tableName);
            List<Map<String,Object>> rowList = connection.getRowsByPrimaryKey(tableName,
                    primaryKeyName, null, null, columnNameList, ConsistencyLevel.ONE);
            return rowList;
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }

    protected Map<String,Object> getRow(String tableName, String[] columnNameList, Object rowKey) {
        Connection connection = null;
        try {  
            connection = connectionPool.acquireConnection();
            String primaryKeyName = getTablePrimaryKeyName(tableName);
            String rowKeyString = rowKey.toString();
            List<Map<String,Object>> rowList = connection.getRowsByPrimaryKey(tableName,
                    primaryKeyName, rowKeyString, rowKeyString, columnNameList, ConsistencyLevel.ONE);
            //if (rowList.size() != 1)
            //    throw new StorageException("Row not found: table = \"" + tableName + "\"; key = \"" + rowKeyString + "\"");
            if (rowList.size() == 0)
                return null;
            return rowList.get(0);
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }

    protected List<Map<String,Object>> executeEqualityQuery(String tableName,
            String[] columnNameList, String columnName, Comparable<?> columnValue) {
        Connection connection = null;
        try {
            connection = connectionPool.acquireConnection();
            String primaryKeyName = getTablePrimaryKeyName(tableName);
            List<Map<String,Object>> rowList = connection.getRowsByIndexedColumn(tableName,
                    primaryKeyName, columnName, columnValue, columnNameList, ConsistencyLevel.ONE);
            return rowList;
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }
    
    protected List<Map<String,Object>> executeRangeQuery(String tableName,
            String[] columnNameList, String predicateColumnName,
            Comparable<?> startValue, boolean startInclusive, Comparable<?> endValue, boolean endInclusive) {
        // FIXME: Not implemented yet. Not actually supported in Cassandra yet, though.
        // Currently indexed range queries are targeted for the 0.7.1 release of Cassandra.
        return null;
    }
    
    protected void insertRows(String tableName, List<Map<String,Object>> insertRowList) {
        Connection connection = null;
        try {
            connection = connectionPool.acquireConnection();
            String primaryKeyName = getTablePrimaryKeyName(tableName);
            connection.updateRows(tableName, primaryKeyName, insertRowList);
            connection.commit(ConsistencyLevel.ONE);
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }
    
    protected void updateRows(String tableName, Set<Object> rowKeys, Map<String,Object> updateColumnMap) {
        Connection connection = null;
        try {
            connection = connectionPool.acquireConnection();
            connection.updateRows(tableName, rowKeys, updateColumnMap);
            connection.commit(ConsistencyLevel.ONE);
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }
    
    protected void updateRowsImpl(String tableName, List<Map<String,Object>> updateRowList) {
        insertRows(tableName, updateRowList);
    }
    
    protected void deleteRowsImpl(String tableName, Set<Object> rowKeyList) {
        Connection connection = null;
        try {
            connection = connectionPool.acquireConnection();
            connection.deleteRows(tableName, rowKeyList);
            connection.commit(ConsistencyLevel.ONE);
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }
    
    /** Create a new table if one does not already exist with the given name.
     * 
     * @param tableName The name of the table to create.
     */
    public void createTable(String tableName, Set<String> indexedColumnNames) {
        super.createTable(tableName, indexedColumnNames);
        Connection connection = null;
        try {
            connection = connectionPool.acquireConnection();
            connection.createColumnFamily(tableName, indexedColumnNames);
        }
        catch (StorageException exc) {
            // FIXME: For right now, just ignore it if this fails. The common
            // case where this happens is when the table already exists (i.e. it's
            // already been created by a previous run of sdnplatform or else by running
            // syncdb in Django. Ideally we should differentiate among the different
            // types of exceptions and only ignore exceptions related to the
            // table / column family already existing. To do that we'd probably need
            // to have more subclasses of StorageException.
        }
        finally {
            if (connection != null)
                connectionPool.releaseConnection(connection);
        }
    }
    
    public void setConnectionPool(ConnectionPool pool) {
        connectionPool = pool;
    }

    // IModule methods

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        // Add to Abstract's dependencies
        return null;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        super.init(context);
        this.connectionPool = 
                new ConnectionPool("localhost", 9160, "sdncon", null, null);
    }
}
