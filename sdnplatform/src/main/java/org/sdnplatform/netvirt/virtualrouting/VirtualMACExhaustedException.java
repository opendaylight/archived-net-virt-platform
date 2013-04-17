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

package org.sdnplatform.netvirt.virtualrouting;

public class VirtualMACExhaustedException extends Exception {

    static final long serialVersionUID = 289534771027891748L;
    
    static private String makeExceptionMessage(String s) {
        String message = "Service Insertion Virtual Mav Exhausted Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public VirtualMACExhaustedException() {
        super(makeExceptionMessage(null));
    }
    
    public VirtualMACExhaustedException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public VirtualMACExhaustedException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
