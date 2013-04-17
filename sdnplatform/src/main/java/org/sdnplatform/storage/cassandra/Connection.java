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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;


import org.apache.cassandra.thrift.AuthenticationException;
import org.apache.cassandra.thrift.AuthenticationRequest;
import org.apache.cassandra.thrift.AuthorizationException;
import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnDef;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ColumnPath;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.IndexClause;
import org.apache.cassandra.thrift.IndexExpression;
import org.apache.cassandra.thrift.IndexOperator;
import org.apache.cassandra.thrift.IndexType;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.KeyRange;
import org.apache.cassandra.thrift.KeySlice;
import org.apache.cassandra.thrift.KsDef;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.SliceRange;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.sdnplatform.storage.StorageException;

public class Connection {
    
    String host;
    int port;
    private TTransport transport;
    private Cassandra.Client client;
    private String keyspace;
    private String user;
    private String password;
    private boolean inUse;
    private Map<ByteBuffer,Map<String,List<Mutation>>> pendingMutations;
    private Set<Object> pendingDeletions;
    private String pendingColumnFamily;
    private static long lastTimestamp;
    // SimpleDateFormat is not thread-safe so we need to keep a separate
    // instance per thread.
    private static ThreadLocal<DateFormat> dateFormat = new ThreadLocal<DateFormat>();
    
    private static String NULL_VALUE_STRING = "\b";
    
    public Connection(String host, int port) {

        // Use defaults for host and port if they're not specified
        if (host == null)
            host = "localhost";
        if (port == 0)
            port = 9160;
        
        this.host = host;
        this.port = port;
        
        open();
        
        inUse = false;
    }
    
    private Cassandra.Client getClient() {
        if (client == null)
            open();
        return client;
    }
    
    private SlicePredicate getSlicePredicate(String[] columnNameList) {
        SlicePredicate slicePredicate = new SlicePredicate();
        try {
            if (columnNameList != null) {
                List<ByteBuffer> columnNameByteBufferList = new ArrayList<ByteBuffer>();
                for (String columnName: columnNameList) {
                    byte[] columnNameBytes = columnName.getBytes("UTF-8");
                    columnNameByteBufferList.add(ByteBuffer.wrap(columnNameBytes));
                }
                slicePredicate.setColumn_names(columnNameByteBufferList);
            } else {
                SliceRange sliceRange = new SliceRange();
                sliceRange.setStart(new byte[0]);
                sliceRange.setFinish(new byte[0]);
                // FIXME: The default column count is 100. We should tune the value.
                sliceRange.setCount(100000);
                
                slicePredicate.setSlice_range(sliceRange);
            }
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Character encoding exception with key range", exc);
        }
        return slicePredicate;
    }
    
    private List<Map<String,Object>> convertKeySliceList(List<KeySlice> keySliceList, String primaryKeyName) {
        List<Map<String,Object>> rowList = new ArrayList<Map<String,Object>>();
        try {
            for (KeySlice keySlice: keySliceList) {
                List<ColumnOrSuperColumn> columnList = keySlice.getColumns();
                if (!columnList.isEmpty()) {
                    byte[] keyBytes = keySlice.getKey();
                    String key = new String(keyBytes, "UTF-8");
                    Map<String,Object> columnMap = new HashMap<String,Object>();
                    columnMap.put(primaryKeyName, key);
                    for (ColumnOrSuperColumn columnOrSuperColumn: columnList) {
                        Column column = columnOrSuperColumn.getColumn();
                        byte[] columnNameBytes = column.getName();
                        String columnName = new String(columnNameBytes, "UTF-8");
                        byte[] valueBytes = column.getValue();
                        String value = new String(valueBytes, "UTF-8");
                        if (value.equals(NULL_VALUE_STRING))
                            value = null;
                        columnMap.put(columnName, value);
                    }
                    rowList.add(columnMap);
                }
            }
            return rowList;
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Character encoding exception with key range", exc);
        }
    }
    
    protected List<Map<String,Object>> getRowsByPrimaryKey(String tableName, String primaryKeyName,
            String keyStart, String keyEnd, String[] columnNameList, ConsistencyLevel consistencyLevel) {
        try {
            // Get the column parent
            ColumnParent columnParent = new ColumnParent();
            columnParent.setColumn_family(tableName);

            SlicePredicate slicePredicate = getSlicePredicate(columnNameList);

            // Get the key range.
            // FIXME: These lower/upper bounds values make sense for typical ASCII
            // strings (i.e. space and tilde are at the start and end of the printable
            // ASCII characters), but wouldn't make sense for non-ASCII data. In theory,
            // it seems like you should be able to set keyStartBytes to [0] and
            // keyEndBytes to [255,255,255,255,255,...], but that didn't work when I
            // tried it (at least with an older version of Cassandra, haven't tried it
            // with the 0.7 beta versions). Should look into this some more to determine
            // what's the best solution.
            if (keyStart == null)
                keyStart = "";
            if (keyEnd == null)
                keyEnd = "";
            byte[] keyStartBytes = keyStart.getBytes("UTF-8");
            byte[] keyEndBytes = keyEnd.getBytes("UTF-8");
            KeyRange keyRange = new KeyRange();
            keyRange.setStart_key(keyStartBytes);
            keyRange.setEnd_key(keyEndBytes);
            // FIXME: Shouldn't hard-code count here. Experiment with making this
            // bigger. Can we make it really big or do we need to worry about chunked
            // Cassandra reads to handle large result sets?
            keyRange.setCount(1000000);

            // Get the data
            List<KeySlice> keySliceList = getClient().get_range_slices(columnParent, slicePredicate, keyRange, consistencyLevel);

            List<Map<String,Object>> rowList = convertKeySliceList(keySliceList, primaryKeyName);

            return rowList;
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Character encoding exception with key range", exc);
        }
        catch (TimedOutException exc) {
            throw new StorageException("Cassandra request timed out", exc);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (UnavailableException exc) {
            throw new StorageException("Cassandra unavailable", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }

    protected List<Map<String,Object>> getRowsByIndexedColumn(String tableName, String primaryKeyName,
            String indexedColumnName, Comparable<?> indexedColumnValue,
            String[] columnNameList, ConsistencyLevel consistencyLevel) {
        try {
            // Get the column parent
            ColumnParent columnParent = new ColumnParent();
            columnParent.setColumn_family(tableName);

            SlicePredicate slicePredicate = getSlicePredicate(columnNameList);

            // Get the index expression.
            IndexExpression indexExpression = new IndexExpression();
            byte[] indexedColumnNameBytes = indexedColumnName.getBytes("UTF-8");
            indexExpression.setColumn_name(indexedColumnNameBytes);
            indexExpression.setOp(IndexOperator.EQ);
            String indexedColumnValueString;
            if (indexedColumnValue == null)
                indexedColumnValueString = NULL_VALUE_STRING;
            else
                indexedColumnValueString = indexedColumnValue.toString();
            byte[] columnValueBytes = indexedColumnValueString.getBytes("UTF-8");
            indexExpression.setValue(columnValueBytes);
            List<IndexExpression> indexExpressionList = new ArrayList<IndexExpression>();
            indexExpressionList.add(indexExpression);
            IndexClause indexClause = new IndexClause();
            // FIXME: Shouldn't hard-code count here. Should do chunked reads
            // instead of getting all at once.
            indexClause.setCount(1000000);
            byte[] startKeyBytes = " ".getBytes("UTF-8");
            indexClause.setStart_key(startKeyBytes);
            indexClause.setExpressions(indexExpressionList);

            // Get the data
            List<KeySlice> keySliceList = getClient().get_indexed_slices(columnParent, indexClause, slicePredicate, consistencyLevel);

            List<Map<String,Object>> rowList = convertKeySliceList(keySliceList, primaryKeyName);

            return rowList;
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Character encoding exception with column name/value", exc);
        }
        catch (TimedOutException exc) {
            throw new StorageException("Cassandra request timed out", exc);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (UnavailableException exc) {
            throw new StorageException("Cassandra unavailable", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }

    public void commit(ConsistencyLevel consistencyLevel) {
        try {
            try {
                if (pendingMutations != null) {
                    getClient().batch_mutate(pendingMutations, consistencyLevel);
                }

                if (pendingDeletions != null) {
                    long timestamp = getNextTimestamp();
                    for (Object key: pendingDeletions) {
                        String keyString = key.toString();
                        byte[] keyBytes = keyString.getBytes("UTF-8");
                        ByteBuffer keyByteBuffer = ByteBuffer.wrap(keyBytes);
                        ColumnPath columnPath = new ColumnPath();
                        columnPath.setColumn_family(pendingColumnFamily);
                        client.remove(keyByteBuffer, columnPath, timestamp, consistencyLevel);
                    }
                }
            }
            catch (UnsupportedEncodingException exc) {
                throw new StorageException("Unsupported character encoding in row key", exc);
            }
            catch (TimedOutException exc) {
                throw new StorageException("Cassandra request timed out", exc);
            }
            catch (InvalidRequestException exc) {
                throw new StorageException("Invalid Cassandra request", exc);
            }
            catch (UnavailableException exc) {
                throw new StorageException("Cassandra unavailable", exc);
            }
            catch (TException exc) {
                throw new StorageException("Thrift error connecting to Cassandra", exc);
            }
        }
        finally {
            rollback();
        }
    }
    
    public void rollback() {
        pendingMutations = null;
        pendingDeletions = null;
        pendingColumnFamily = null;
    }
    
    public void updateColumn(String columnFamily, Object rowKey, String columnName, Object value) {
        Map<String,Object> columnUpdateMap = new HashMap<String,Object>();
        columnUpdateMap.put(columnName, value);
        updateRow(columnFamily, rowKey, columnUpdateMap);
    }
    
    public void updateRow(String columnFamily, Object rowKey, Map<String,Object> columnUpdateMap) {
        Map<Object,Map<String,Object>> rowUpdateMap = new HashMap<Object,Map<String,Object>>();
        rowUpdateMap.put(rowKey, columnUpdateMap);
        updateRows(columnFamily, rowUpdateMap);
    }

    public void updateRow(String columnFamily, Map<String,Object> columnUpdateMap, String primaryKeyName) {
        List<Map<String,Object>> rowUpdateList = new ArrayList<Map<String,Object>>();
        rowUpdateList.add(columnUpdateMap);
        updateRows(columnFamily, primaryKeyName, rowUpdateList);
    }

    private List<Mutation> getRowMutationList(String columnFamily, Object rowKey) {
        
        if (pendingMutations == null)
            pendingMutations = new HashMap<ByteBuffer,Map<String,List<Mutation>>>();
        
        ByteBuffer rowKeyBytes;
        try {
            rowKeyBytes = ByteBuffer.wrap(rowKey.toString().getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Unsupported character encoding for row ID", exc);
        }
        
        Map<String,List<Mutation>> rowIdMap = pendingMutations.get(rowKeyBytes);
        if (rowIdMap == null) {
            rowIdMap = new HashMap<String,List<Mutation>>();
            pendingMutations.put(rowKeyBytes, rowIdMap);
        }
        
        List<Mutation> rowMutationList = rowIdMap.get(columnFamily);
        if (rowMutationList == null) {
            rowMutationList = new ArrayList<Mutation>();
            rowIdMap.put(columnFamily, rowMutationList);
        }
        
        return rowMutationList;
    }
    
    byte[] convertValueToBytes(Object value) throws StorageException {
        try {
            String s;
            if (value == null) {
                s = NULL_VALUE_STRING;
            } else if (value instanceof Date) {
                // FIXME: This is a hack to do the date conversion here. Currently
                // the date conversion is split between the cassandra bundle and the
                // nosql bundle. This should be refactored so that the logic for
                // conversion to the format to store the data in the database is only
                // in one place.
                DateFormat df = dateFormat.get();
                if (df == null) {
                    df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    df.setTimeZone(TimeZone.getTimeZone("GMT"));
                    dateFormat.set(df);
                }
                s = df.format(value);
            } else {
                s = value.toString();
            }
            byte[] bytes = s.getBytes("UTF-8");
            return bytes;
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Unsupported character encoding for column value", exc);
        }
    }

    private long getNextTimestamp() {
        // Java only lets us get the time in milliseconds, not microseconds, grrr
        long timestamp = System.currentTimeMillis() * 1000;
        if (timestamp <= lastTimestamp)
            timestamp = lastTimestamp + 1;
        lastTimestamp = timestamp;
        return timestamp;
    }
    
    private Mutation getMutation(String columnName, Object value, long timestamp) {
        byte[] columnNameBytes;
        try {
            columnNameBytes = columnName.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException exc) {
            throw new StorageException("Unsupported character encoding for column name", exc);
        }
        byte[] valueBytes = convertValueToBytes(value);
        Column column = new Column();
        column.setName(columnNameBytes);
        column.setValue(valueBytes);
        column.setTimestamp(timestamp);
        ColumnOrSuperColumn columnOrSuperColumn = new ColumnOrSuperColumn();
        columnOrSuperColumn.setColumn(column);
        Mutation mutation = new Mutation();
        mutation.setColumn_or_supercolumn(columnOrSuperColumn);
        return mutation;
    }
    
    public void updateRows(String columnFamily, Map<Object,Map<String,Object>> rowUpdateMap) {
        long timestamp = getNextTimestamp();
        for (Map.Entry<Object,Map<String,Object>> rowEntry: rowUpdateMap.entrySet()) {
            Object rowKey = rowEntry.getKey();
            List<Mutation> rowMutationList = getRowMutationList(columnFamily, rowKey);
            for (Map.Entry<String,Object> columnEntry: rowEntry.getValue().entrySet()) {
                Mutation mutation = getMutation(columnEntry.getKey(), columnEntry.getValue(), timestamp);
                rowMutationList.add(mutation);
            }
        }
    }
    
    public void updateRows(String columnFamily, Set<Object> rowKeys, Map<String,Object> columnUpdateMap) {
        Map<Object,Map<String,Object>> rowUpdateMap = new HashMap<Object,Map<String,Object>>();
        for (Object rowKey: rowKeys) {
            rowUpdateMap.put(rowKey, columnUpdateMap);
        }
        updateRows(columnFamily, rowUpdateMap);
    }
    
    public String generateRowId() {
        return UUID.randomUUID().toString();
    }
    
    public void updateRows(String columnFamily, String primaryKeyName, List<Map<String,Object>> rowUpdateList) {
        long timestamp = getNextTimestamp();
        for (Map<String,Object> rowUpdateMap: rowUpdateList) {
            String rowId = (String) rowUpdateMap.get(primaryKeyName);
            if (rowId == null)
                rowId = generateRowId();
            List<Mutation> rowMutationList = getRowMutationList(columnFamily, rowId);
            for (Map.Entry<String,Object> entry: rowUpdateMap.entrySet()) {
                String columnName = entry.getKey();
                // FIXME: For now we include the primary key data as column data too.
                // This is not completely efficient, because it means we're storing that
                // data twice in Cassandra, but if you don't do that, then you can't set
                // up secondary indexes on the primary key column in order to do range
                // queries on that data (not supported currently in 0.7.0, but is targeted
                // for the 0.7.1 release). Also there are (arguably pathological) cases
                // where if you don't store the data as column data too then the row could
                // be incorrectly interpreted as a deleted (tombstoned) row. So to make
                // things simpler (at least for now) we just always included the key as
                // column data too.
                //if (!columnName.equals(primaryKeyName)) {
                    Mutation mutation = getMutation(columnName, entry.getValue(), timestamp);
                    rowMutationList.add(mutation);
                //}
            }
        }
    }
    
    public void deleteRows(String columnFamily, Set<Object> rowKeys) {
        for (Object rowKey : rowKeys) {
            if (pendingDeletions == null)
                pendingDeletions = new HashSet<Object>();
            pendingDeletions.add(rowKey);
            pendingColumnFamily = columnFamily;
        }
    }
    
    public void truncate(String columnFamily) {
        try {
            client.truncate(columnFamily);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (UnavailableException exc) {
            throw new StorageException("Cassandra unavailable", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }
    
    public void setKeyspace(String keyspace) {
        try {
            client.set_keyspace(keyspace);
            this.keyspace = keyspace;
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }
    
    public void login(String user, String password) {
        
        if (user == null)
            return;
        
        this.user = user;
        this.password = password;
        
        try {
            // FIXME: Not sure if this is correct? The only example I could find
            // for login was the Perl example program and this seemed to be what it
            // was doing. This also seemed like what the SimpleAuthenticator was
            // expecting in the auth credentials, but I'm not sure if this is
            // intended to apply to other authenticators as well. Need to do some
            // more research.
            Map<String,String> credentials = new HashMap<String,String>();
            credentials.put("username", user);
            credentials.put("password", password);
            client.login(new AuthenticationRequest(credentials));
        }
        catch (TException exc) {
            throw new StorageException("Thrift exception", exc);
        }
        catch (AuthenticationException exc) {
            throw new StorageException("Authentication failed", exc);
        }
        catch (AuthorizationException exc) {
            throw new StorageException("Authorization failed", exc);
        }
    }
    
    public void createKeyspace(String keyspaceName, String strategyClass, Map<String,String> strategyOptions) {
        KsDef keyspaceDef = new KsDef();
        keyspaceDef.setName(keyspaceName);
        keyspaceDef.setStrategy_class(strategyClass);
        if (strategyOptions != null)
            keyspaceDef.setStrategy_options(strategyOptions);
        List<CfDef> cfDefList = new ArrayList<CfDef>();
        keyspaceDef.setCf_defs(cfDefList);
        try {
            client.system_add_keyspace(keyspaceDef);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (SchemaDisagreementException exc) {
            throw new StorageException("Cassandra schema disagreement error", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }
    
    public void dropKeyspace(String keyspaceName) {
        try {
            client.system_drop_keyspace(keyspaceName);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (SchemaDisagreementException exc) {
            throw new StorageException("Cassandra schema disagreement error", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }
    
    public void dropColumnFamily(String columnFamilyName) {
        if (keyspace == null)
            throw new StorageException("Null keyspace name in dropColumnFamily");
        try {
            client.system_drop_column_family(columnFamilyName);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (SchemaDisagreementException exc) {
            throw new StorageException("Cassandra schema disagreement error", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }
    
    public void createColumnFamily(String columnFamilyName, Set<String> indexedColumns) {
        try {
            if (keyspace == null)
                throw new StorageException("Null keyspace name in createColumnFamily");
            CfDef columnFamilyDef = new CfDef();
            columnFamilyDef.setName(columnFamilyName);
            columnFamilyDef.setKeyspace(keyspace);
            columnFamilyDef.setComparator_type("UTF8Type");
            if (indexedColumns != null) {
                List<ColumnDef> metadataList = new ArrayList<ColumnDef>();
                for (String indexedColumn: indexedColumns) {
                    ColumnDef columnDef = new ColumnDef();
                    try {
                        byte[] columnNameBytes = indexedColumn.getBytes("UTF-8");
                        columnDef.setName(columnNameBytes);
                    }
                    catch (UnsupportedEncodingException exc) {
                        throw new StorageException("Unsupported character encoding for indexed column name", exc);
                    }
                    // FIXME: Shouldn't hard-code these.
                    columnDef.setIndex_type(IndexType.KEYS);
                    columnDef.setValidation_class("BytesType");
                    metadataList.add(columnDef);
                }
                columnFamilyDef.setColumn_metadata(metadataList);
            }
            client.system_add_column_family(columnFamilyDef);
        }
        catch (InvalidRequestException exc) {
            throw new StorageException("Invalid Cassandra request", exc);
        }
        catch (SchemaDisagreementException exc) {
            throw new StorageException("Cassandra schema disagreement error", exc);
        }
        catch (TException exc) {
            throw new StorageException("Thrift error connecting to Cassandra", exc);
        }
    }
    
    public void open() {
        try {
            // FIXME: Is this the optimal code for thrift 0.5? This code seems to change
            // with every new Cassandra release and they never update the sample code.
            // Probably need to get the source package and look at the unit tests to verify.
            TSocket socket = new TSocket(this.host, this.port);
            transport = new TFramedTransport(socket);
            TProtocol protocol = new TBinaryProtocol(transport);
            client = new Cassandra.Client(protocol);
            transport.open();
        }
        catch (TTransportException exc) {
            close();
            throw new StorageException("Error opening Cassandra connection", exc);
        }
    }
    
    public void close() {
        if (transport != null)
            transport.close();
        client = null;
        transport = null;
    }
    
    public void reconnect() {
        close();
        open();
        setKeyspace(keyspace);
        login(this.user, this.password);
    }
    
    public boolean getInUse() {
        return inUse;
    }
    
    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }
}
