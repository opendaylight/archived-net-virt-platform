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

package org.sdnplatform.devicemanager.internal;

import java.util.Set;

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.tagmanager.ITagListener;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.tagmanager.Tag;
import org.sdnplatform.tagmanager.TagDoesNotExistException;
import org.sdnplatform.tagmanager.TagInvalidHostMacException;



/**
 * @author sandeephebbani
 *
 */
public class MockTagManager implements ITagManagerService {

    private Set<IDevice> devices;
    private Set<Tag> tags;

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getNamespaces()
     */
    @Override
    public Set<String> getNamespaces() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#createTag(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public Tag createTag(String ns, String name, String value) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#createTag(java.lang.String, java.lang.String, java.lang.String, boolean)
     */
    @Override
    public Tag createTag(String ns, String name, String value,
                         boolean persist) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#addTag(org.sdnplatform.tagmanager.Tag)
     */
    @Override
    public void addTag(Tag tag) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#deleteTag(org.sdnplatform.tagmanager.Tag)
     */
    @Override
    public void deleteTag(Tag tag) throws TagDoesNotExistException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#mapTagToHost(org.sdnplatform.tagmanager.Tag, java.lang.String, java.lang.Short, java.lang.String, java.lang.String)
     */
    @Override
    public
            void
            mapTagToHost(Tag tag, String hostmac, Short vlan, String dpid,
                         String interfaceName) throws TagDoesNotExistException,
                    TagInvalidHostMacException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#unmapTagToHost(org.sdnplatform.tagmanager.Tag, java.lang.String, java.lang.Short, java.lang.String, java.lang.String)
     */
    @Override
    public
            void
            unmapTagToHost(Tag tag, String hostmac, Short vlan, String dpid,
                           String interfaceName) throws TagDoesNotExistException,
                    TagInvalidHostMacException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getTagsByHost(java.lang.String, java.lang.Short, java.lang.String, java.lang.String)
     */
    @Override
    public Set<Tag> getTagsByHost(String hostmac, Short vlan, String dpid,
                                  String interfaceName) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getTagFromDBId(java.lang.String)
     */
    @Override
    public Tag getTagFromDBId(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getTags(java.lang.String, java.lang.String)
     */
    @Override
    public Set<Tag> getTags(String ns, String name) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getTagsByNamespace(java.lang.String)
     */
    @Override
    public Set<Tag> getTagsByNamespace(String namespace) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#addListener(org.sdnplatform.tagmanager.ITagListener)
     */
    @Override
    public void addListener(ITagListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#removeListener(org.sdnplatform.tagmanager.ITagListener)
     */
    @Override
    public void removeListener(ITagListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getTagsByDevice(org.sdnplatform.devicemanager.IDevice)
     */
    @Override
    public Set<Tag> getTagsByDevice(IDevice device) {
        return tags;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#getDevicesByTag(org.sdnplatform.tagmanager.Tag)
     */
    @Override
    public Set<IDevice> getDevicesByTag(Tag tag) {
        return devices;
    }

    /**
     * @param devices
     */
    public void setDevices(Set<IDevice> devices) {
        this.devices = devices;
        
    }

    /**
     * @param noTags
     */
    public void setTags(Set<Tag> tags) {
        this.tags = tags;
        
    }

}
