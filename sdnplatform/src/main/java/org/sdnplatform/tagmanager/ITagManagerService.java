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

import java.util.Set;

import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.IDevice;


public interface ITagManagerService extends IPlatformService {
    
    public Set<String> getNamespaces();
    
    /**
     * Create a new tag.
     * @param tag
     */
    public Tag createTag(String ns, String name, String value);
    
    public Tag createTag(String ns, String name, String value, boolean persist);
    
    /**
     * Add a new tag.
     * @param tag
     */
    public void addTag(Tag tag);

    /**
     * Delete a new tag.
     * @param tag
     */
    public void deleteTag(Tag tag)
        throws TagDoesNotExistException;
    
    /**
     * Map a tag to a host.
     * @param tag
     * @param vlan TODO
     * @param dpid TODO
     * @param interfaceName TODO
     */
    public void mapTagToHost(Tag tag, String hostmac, Short vlan, String dpid, 
                             String interfaceName)
        throws TagDoesNotExistException, 
                TagInvalidHostMacException;
    
    /**
     * Unmap a tag from a host.
     * @param tag
     * @param vlan TODO
     * @param dpid TODO
     * @param interfaceName TODO
     */
    public void unmapTagToHost(Tag tag, String hostmac, Short vlan, String dpid,
                               String interfaceName)
        throws TagDoesNotExistException,
                TagInvalidHostMacException;
    
    /**
     * getter for tag mappings to a host.
     * @param hostmac
     * @param vlan
     * @param dpid
     * @param interfaceName
     * @return
     */
    public Set<Tag> getTagsByHost(String hostmac, Short vlan, String dpid,
                               String interfaceName);
    
    /**
     * Return a tag by DB ID
     * @param id
     * @return
     */
    public Tag getTagFromDBId(String id);

    
    /**
     * return ITags with the given namespace and name.
     * @param namespace
     * @param name
     * @return
     */
    public Set<Tag> getTags(String ns, String name);
    
    
    /**
     * 
     * @param namespace
     * @return
     */
    public Set<Tag> getTagsByNamespace(String namespace);
    
    /** 
     * Add a listener for tag creation, deletion, host mapping and unmapping.
     * @param listener The listener instance to call
     */
    public void addListener(ITagListener listener);
    
    /** Remove a tag listener
     * @param listener The previously installed listener instance
     */
    public void removeListener(ITagListener listener);
    
    /**
     * return set of tags for a given device
     * @param device
     * @return
     */
    public Set<Tag> getTagsByDevice(IDevice device);
    
    /**
     * Return set of devices given a tag.
     * @param tag
     * @return
     */
    public Set<IDevice> getDevicesByTag(Tag tag);
         
}
