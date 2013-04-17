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

package org.sdnplatform.ovsdb.internal;

import java.util.ArrayList;

import org.sdnplatform.ovsdb.internal.JSONShowReplyMsg.ShowResult;


/**
 * Class for JSON RPC update notification message  
 * used by Jackson to decode JSON to POJO
 * 
 * @author Saurav Das
 */
public class JSONUpdateMsg {
    //update message has "id", "method" and "params" 
    private int id;
    private  String method;
    private ArrayList<ShowResult> params;
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public ArrayList<ShowResult> getParams() {
        return params;
    }
    public void setParams(ArrayList<ShowResult> params) {
        this.params = params;
    }
    
    
}
