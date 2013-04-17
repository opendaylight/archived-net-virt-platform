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

import java.util.Iterator;

import org.sdnplatform.devicemanager.IDevice;


public interface ITagListener {

    /**
     * Called when a new tag is added.
     * 
     * @param tag The tag that was added
     */
    public void tagAdded(Tag tag);
    
    /**
     * Called when a tag is removed.
     * 
     * @param tag The tag that was removed
     */
    public void tagDeleted(Tag tag);
    
    /**
     * Called when devices get re-mapped
     * @param devices
     */
    public void tagDevicesReMapped(Iterator<? extends IDevice> devices);
}
