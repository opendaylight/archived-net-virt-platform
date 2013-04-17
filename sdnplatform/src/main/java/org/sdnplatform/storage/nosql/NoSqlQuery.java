/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *    Originally created by David Erickson, Stanford University 
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the
 *    License. You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an "AS
 *    IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *    express or implied. See the License for the specific language
 *    governing permissions and limitations under the License. 
 */

package org.sdnplatform.storage.nosql;

import java.util.HashMap;
import java.util.Map;

import org.sdnplatform.storage.IPredicate;
import org.sdnplatform.storage.IQuery;
import org.sdnplatform.storage.RowOrdering;


public class NoSqlQuery implements IQuery {

    private String tableName;
    private String[] columnNameList;
    private IPredicate predicate;
    private RowOrdering rowOrdering;
    private Map<String,Comparable<?>> parameterMap;
    
    NoSqlQuery(String className, String[] columnNameList, IPredicate predicate, RowOrdering rowOrdering) {
        this.tableName = className;
        this.columnNameList = columnNameList;
        this.predicate = predicate;
        this.rowOrdering = rowOrdering;
    }
    
    @Override
    public void setParameter(String name, Object value) {
        if (parameterMap == null)
            parameterMap = new HashMap<String,Comparable<?>>();
        parameterMap.put(name, (Comparable<?>)value);
    }

    @Override
    public String getTableName() {
        return tableName;
    }
    
    String[] getColumnNameList() {
        return columnNameList;
    }
    
    IPredicate getPredicate() {
        return predicate;
    }
    
    RowOrdering getRowOrdering() {
        return rowOrdering;
    }
    
    Comparable<?> getParameter(String name) {
        Comparable<?> value = null;
        if (parameterMap != null) {
            value = parameterMap.get(name);
        }
        return value;
    }
    
    Map<String,Comparable<?>> getParameterMap() {
        return parameterMap;
    }
}
