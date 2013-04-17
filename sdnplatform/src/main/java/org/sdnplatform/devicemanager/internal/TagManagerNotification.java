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

import java.util.Iterator;

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.tagmanager.Tag;



public class TagManagerNotification {
    
    public enum Action { ADD_TAG, 
            DELETE_TAG, 
            ADD_TAGHOST_MAPPING,
            DELETE_TAGHOST_MAPPING,
            TAGDEVICES_REMAPPED};
    
    private Tag m_tag;
    private String m_hostmac;
    private Action m_action;
    private Iterator<? extends IDevice> devices;
    
    public TagManagerNotification() {
    }
    
    public TagManagerNotification(Tag tag, String hostmac, Action action) {
        this.m_tag = tag;
        this.m_hostmac = hostmac;
        this.m_action = action;
    }
    
    public TagManagerNotification(Iterator<? extends IDevice> devices) {
        this.devices = devices;
    }
    
    public Tag getTag() {
        return m_tag;
    }
    
    public String getHost() {
        return m_hostmac;
    }
    
    public Action getAction() {
        return m_action;
    }
    
    public Iterator<? extends IDevice> getDevices() {
        return devices;
    }
    
    public void setTag(Tag tag) {
        this.m_tag = tag;
    }
    
    public void setHost(String host) {
        this.m_hostmac = host;
    }
    
    public void setAction(Action action) {
        this.m_action = action;
    }
    
    public void setDevices(Iterator<? extends IDevice> devices) {
        this.devices = devices;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result
                        + ((devices == null) ? 0 : devices.hashCode());
        result =
                prime * result
                        + ((m_action == null) ? 0 : m_action.hashCode());
        result =
                prime * result
                        + ((m_hostmac == null) ? 0 : m_hostmac.hashCode());
        result = prime * result + ((m_tag == null) ? 0 : m_tag.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TagManagerNotification other = (TagManagerNotification) obj;
        if (devices == null) {
            if (other.devices != null) return false;
        } else if (!devices.equals(other.devices)) return false;
        if (m_action != other.m_action) return false;
        if (m_hostmac == null) {
            if (other.m_hostmac != null) return false;
        } else if (!m_hostmac.equals(other.m_hostmac)) return false;
        if (m_tag == null) {
            if (other.m_tag != null) return false;
        } else if (!m_tag.equals(other.m_tag)) return false;
        return true;
    }
}
