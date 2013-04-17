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

package org.sdnplatform.tagmanager;

public class TagDoesNotExistException extends TagManagerException {

    static final long serialVersionUID = 5629989010156158790L;
    
    static private String makeExceptionMessage(String s) {
        String message = "TagDoesNotExist Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagDoesNotExistException() {
        super(makeExceptionMessage(null));
    }
    
    public TagDoesNotExistException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagDoesNotExistException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
