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

package org.sdnplatform.ovsdb;

/**
 * Interface that allows listening for OVS connection events
 */
public interface IOVSDBListener {

    /**
     * Fired when an OVS switch is connected to the controller
     * @param ovsdb the new IOVSDB object
     */
    public void addedSwitch(IOVSDB ovsdb);

    /**
     * Fired when an OVS switch is removed from the controller
     * @param ovsdb the new IOVSDB object
     */
    public void removedSwitch(IOVSDB ovsdb);

    /**
     * Fired when a potential KVM based OVS switch connects
     */
    public void addedNonVTASwitch();
}
