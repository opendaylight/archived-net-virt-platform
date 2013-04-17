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

/**
 * Class for JSON RPC add-port reply message  
 * used by Jackson to decode JSON to POJO
 * 
 * @author Saurav Das
 */
public class JSONAddPortReplyMsg {
    private int id;
    private Object error;
    private ArrayList<Object> result;
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public Object getError() {
        return error;
    }
    public void setError(Object error) {
        this.error = error;
    }
    public  ArrayList<Object> getResult() {
        return result;
    }
    public void setResult(ArrayList<Object> result) {
        this.result = result;
    }

}
