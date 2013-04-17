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


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantReadWriteLock;



import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.tagmanager.ITagListener;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.tagmanager.Tag;
import org.sdnplatform.tagmanager.TagDoesNotExistException;
import org.sdnplatform.tagmanager.TagInvalidHostMacException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@LogMessageCategory("Device Management")
public class BetterDeviceManagerImpl extends DeviceManagerImpl
    implements IModule, ITagManagerService, IStorageSourceListener,
               IHAListener {
    protected static Logger logger =
            LoggerFactory.getLogger(BetterDeviceManagerImpl.class);

    // Additional dependencies
    protected IEntityClassifierService ecs;

    /********
     * TagNamespace
     */
    protected static final String DEFAULT_NAMESPACE = "default";

    /********
     * Tag table fields
     */
    public static final String TAGMAPPING_TABLE_NAME = "controller_tagmapping";
    public static final String TAGMAPPING_ID_COLUMN_NAME = "id";

    public static final String TAGMAPPING_TAG_COLUMN_NAME = "tag_id";
    public static final String TAGMAPPING_MAC_COLUMN_NAME = "mac";
    public static final String TAGMAPPING_VLAN_COLUMN_NAME = "vlan";
    public static final String TAGMAPPING_SWITCH_COLUMN_NAME = "dpid";
    public static final String TAGMAPPING_INTERFACE_COLUMN_NAME = "ifname";



    public static final String TAG_TABLE_NAME = "controller_tag";
    public static final String TAG_ID_COLUMN_NAME = "id";
    public static final String TAG_NAMESPACE_COLUMN_NAME = "namespace";
    public static final String TAG_NAME_COLUMN_NAME = "name";
    public static final String TAG_VALUE_COLUMN_NAME = "value";
    public static final String TAG_PERSIST_COLUMN_NAME = "persist";


    /*
     * spoofing protection tables and columns
     */
    protected static final String COMPOUND_KEY_SEPARATOR_REGEX = "\\|";

    protected static final String HOSTCONFIG_TABLE_NAME =
            "controller_hostconfig";
    protected static final String HOSTCONFIG_ID_COLUMN_NAME = "id";
    protected static final String HOSTCONFIG_ADDRESSPACE_COLUMN_NAME =
            "address_space";
    protected static final String HOSTCONFIG_VLAN_COLUMN_NAME = "vlan";
    protected static final String HOSTCONFIG_MAC_COLUMN_NAME = "mac";


    protected static final String HOSTSECURITYIPADDRESS_TABLE_NAME =
            "controller_hostsecurityipaddress";
    protected static final String HOSTSECURITYIPADDRESS_ID_COLUMN_NAME =
            "id";
    protected static final String HOSTSECURITYIPADDRESS_HOSTCONFIG_COLUMN_NAME =
            "hostconfig_id";
    protected static final String HOSTSECURITYIPADDRESS_IP_COLUMN_NAME =
            "ip";

    protected static final String HOSTSECURITYATTACHMENTPOINT_TABLE_NAME =
            "controller_hostsecurityattachmentpoint";
    protected static final String HOSTSECURITYATTACHMENTPOINT_ID_COLUMN_NAME =
            "id";
    protected static final String HOSTSECURITYATTACHMENTPOINT_HOSTCONFIG_COLUMN_NAME =
            "hostconfig_id";
    protected static final String HOSTSECURITYATTACHMENTPOINT_DPID_COLUMN_NAME =
            "dpid";
    protected static final String HOSTSECURITYATTACHMENTPOINT_IF_NAME_REGEX_COLUMN_NAME =
            "if_name_regex";


    public static class DeviceId {
        String addressSpace;
        Short vlan;
        Long mac;
        public DeviceId(String addressSpace, Short vlan, Long mac) {
            super();
            this.addressSpace = addressSpace;
            this.vlan = vlan;
            this.mac = mac;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result =
                    prime
                            * result
                            + ((addressSpace == null)
                                    ? 0
                                    : addressSpace.hashCode());
            result = prime * result + ((mac == null) ? 0 : mac.hashCode());
            result = prime * result + ((vlan == null) ? 0 : vlan.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            DeviceId other = (DeviceId) obj;
            if (addressSpace == null) {
                if (other.addressSpace != null) return false;
            } else if (!addressSpace.equals(other.addressSpace))
                                                                return false;
            if (mac == null) {
                if (other.mac != null) return false;
            } else if (!mac.equals(other.mac)) return false;
            if (vlan == null) {
                if (other.vlan != null) return false;
            } else if (!vlan.equals(other.vlan)) return false;
            return true;
        }
        @Override
        public String toString() {
            return "DeviceId [addressSpace=" + addressSpace + ", vlan="
                    + vlan + ", mac=" + mac + "]";
        }
    }

    public static class ScopedIp {
        public String addressSpace;
        public Integer ip;
        public ScopedIp(String addressSpace, Integer ip) {
            super();
            this.addressSpace = addressSpace;
            this.ip = ip;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result =
                    prime
                            * result
                            + ((addressSpace == null)
                                    ? 0
                                    : addressSpace.hashCode());
            result = prime * result + ((ip == null) ? 0 : ip.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            ScopedIp other = (ScopedIp) obj;
            if (addressSpace == null) {
                if (other.addressSpace != null) return false;
            } else if (!addressSpace.equals(other.addressSpace))
                                                                return false;
            if (ip == null) {
                if (other.ip != null) return false;
            } else if (!ip.equals(other.ip)) return false;
            return true;
        }
        @Override
        public String toString() {
            return "ScopedIp [addressSpace=" + addressSpace + ", ip=" + ip
                    + "]";
        }


    }

    /**
     * This is the switch dpid, iface name tuple
     */
    public class SwitchInterface {
        Long dpid;
        String ifaceName;

        /**
         * @param dpid2
         * @param ifaceName2
         */
        public SwitchInterface(Long dpid2, String ifaceName2) {
            this.dpid = dpid2;
            this.ifaceName = ifaceName2;
        }

        public SwitchInterface(SwitchInterface other) {
            this.dpid = other.dpid;
            this.ifaceName = other.ifaceName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((dpid == null) ? 0 : dpid.hashCode());
            result = prime * result
                            + ((ifaceName == null) ? 0 : ifaceName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            SwitchInterface other = (SwitchInterface) obj;
            if (dpid == null) {
                if (other.dpid != null) return false;
            } else if (!dpid.equals(other.dpid)) return false;
            if (ifaceName == null) {
                if (other.ifaceName != null) return false;
            } else if (!ifaceName.equals(other.ifaceName)) return false;
            return true;
        }

        public Short getPortNumber() {
            IOFSwitch sw = controllerProvider.getSwitches().get(dpid);
            if (sw == null)
                return null;
            OFPhysicalPort p = sw.getPort(ifaceName);
            if (p == null)
                return null;
            return p.getPortNumber();
        }
    }

    /********
     * Tag States and lock
     */
    protected ReentrantReadWriteLock m_lock;
    private Map<String, ConcurrentHashMap<String, Set<Tag>>> m_tags;
    private Map<String, Set<EntityConfig>> tagToEntities;
    private Map<EntityConfig, Set<Tag>> entityToTags;

    /********
     * Tag notification
     */
    private Set<ITagListener> m_listeners;

    /*
     * Spoofing protection
     */

    /**
     * We synchronize on this object when we change our internal anti-spoofing
     * structures. We don't need to synchronize on reads since we use
     * ConcurrentHashMaps. Of course, we could also update our data structures
     * lock-free but updating is not on the critical path so the coding and
     * complexity overhead isn't worth it.
     */
    protected Object antiSpoofingConfigWriteLock;

    protected SwitchInterfaceRegexMatcher interfaceRegexMatcher;

    /**
     * Anti-spoofing configuration: per IP-address (scoped to an address-space):
     * the set of devices/hosts that are allowed to use this IP
     */
    protected ConcurrentHashMap<ScopedIp,Collection<DeviceId>>
            hostSecurityIpMap;
    /**
     * NOTE: CURRENTLY UNUSED SINCE WE FUNNEL EVERYTHING THROUGH REGEX
     * MATCHING.
     * Anti-spoofing configuration: per address-space:
     * the mapping from MAC address to switch port.
     * First key: address space name
     * Second key: Mac address
     * Data: collection of switch-port names.
     */
    protected ConcurrentHashMap<String,
            ConcurrentHashMap<Long,
            Collection<SwitchInterface>>> hostSecurityInterfaceMap;

    /**
     * Anti-spoofing configuration: the mapping from host (deviceId)
     * to the key (tag?) that interfaceRegexMatcher will use when querying for
     * switch interfaces matching this host
     * Key: deviceId
     * Data: collection of keys to query
     */
    protected ConcurrentHashMap<DeviceId, Collection<String>>
            hostSecurityInterfaceRegexMap;

    @Override
    protected Device learnDeviceByEntity(Entity entity) {
        return super.learnDeviceByEntity(entity);
    }

    //***************
    // ITagManagerService
    //***************

    @Override
    public Set<String> getNamespaces() {
        return new HashSet<String>(m_tags.keySet());
    }

    /**
     * Create a new tag.
     * @param tag
     */
    @Override
    public Tag createTag(String ns, String name, String value) {
        return new Tag(ns, name, value, true);
    }

    /**
     * Create a new tag, with persist.
     * @param tag
     */
    @Override
    public Tag createTag(String ns, String name, String value,
                         boolean persist) {
        return new Tag(ns, name, value, persist);
    }

    /**
     * Add a new tag. The tag is saved in DB.
     * @param tag
     */
    @Override
    public void addTag(Tag tag) {
        if (tag == null) return;

        String ns = DEFAULT_NAMESPACE;
        if (!tag.getNamespace().equals("")) {
            ns = tag.getNamespace();
        }

        Map<String, Object> rowValues = new HashMap<String, Object>();

        rowValues.put(TAG_ID_COLUMN_NAME, getTagKey(ns, tag.getName(),
                                                    tag.getValue()));
        rowValues.put(TAG_NAMESPACE_COLUMN_NAME, ns);
        rowValues.put(TAG_NAME_COLUMN_NAME, tag.getName());
        rowValues.put(TAG_VALUE_COLUMN_NAME, tag.getValue());
        rowValues.put(TAG_PERSIST_COLUMN_NAME, tag.getPersist());
        storageSource.insertRow(TAG_TABLE_NAME, rowValues);
    }

    /**
     * Delete a tag. The tag is removed from DB.
     * @param tag
     */
    @Override
    public void deleteTag(Tag tag)
        throws TagDoesNotExistException {
        if (tag == null) return;

        String ns = DEFAULT_NAMESPACE;
        if (!tag.getNamespace().equals("")) {
            ns = tag.getNamespace();
        }

        String tagDBKey = getTagKey(ns, tag.getName(), tag.getValue());
        if (m_tags.get(ns) == null ||
            !m_tags.get(ns).containsKey(tag.getName())) {
            throw new TagDoesNotExistException(tag.toString());
        }
        storageSource.deleteRow(TAG_TABLE_NAME, tagDBKey);
    }

    /**
     * Map a tag to a host. The mapping is saved in DB.
     * @param tag
     * @param hostmac
     */
    @Override
    public void mapTagToHost(Tag tag, String hostmac, Short vlan, String dpid,
                             String interfaceName)
        throws TagDoesNotExistException, TagInvalidHostMacException {
        if (tag == null) throw new TagDoesNotExistException("null tag");
        if (hostmac != null && !Ethernet.isMACAddress(hostmac)) {
            throw new TagInvalidHostMacException(hostmac + " is an invalid mac address");
        }

        String ns = DEFAULT_NAMESPACE;
        if (!tag.getNamespace().equals("")) {
            ns = tag.getNamespace();
        }

        if (m_tags.get(ns) == null ||
            !m_tags.get(ns).containsKey(tag.getName())) {
            throw new TagDoesNotExistException(tag.toString());
        }
        if (hostmac == null && vlan == null && dpid == null &&
                interfaceName == null)
            return;

        String blankStr = new String("");
        String vlanStr = blankStr;

        if (vlan != null) {
            vlanStr = new String(vlan.toString());
        }

        if (dpid == null && interfaceName != null)
            return;
        if (hostmac == null)
            hostmac = blankStr;
        if (dpid == null)
            dpid = blankStr;
        if (interfaceName == null)
            interfaceName = blankStr;

        Map<String, Object> rowValues = new HashMap<String, Object>();
        String tagid = getTagKey(ns, tag.getName(), tag.getValue());
        String id = tagid + Tag.KEY_SEPARATOR + hostmac + Tag.KEY_SEPARATOR +
                vlanStr + Tag.KEY_SEPARATOR + dpid +
                Tag.KEY_SEPARATOR + interfaceName;
        rowValues.put(TAGMAPPING_ID_COLUMN_NAME, id);

        rowValues.put(TAGMAPPING_TAG_COLUMN_NAME, tagid);
        rowValues.put(TAGMAPPING_MAC_COLUMN_NAME, hostmac);
        rowValues.put(TAGMAPPING_VLAN_COLUMN_NAME, vlanStr);
        rowValues.put(TAGMAPPING_SWITCH_COLUMN_NAME, dpid);
        rowValues.put(TAGMAPPING_INTERFACE_COLUMN_NAME, interfaceName);
        storageSource.insertRowAsync(TAGMAPPING_TABLE_NAME, rowValues);
    }

    /**
     * UnMap a tag from a host. The mapping is removed from DB.
     * @param tag
     * @param hostmac
     */
    @Override
    public void unmapTagToHost(Tag tag, String hostmac, Short vlan, String dpid,
                               String interfaceName)
        throws TagDoesNotExistException, TagInvalidHostMacException {
        if (tag == null) throw new TagDoesNotExistException("null tag");
        if (hostmac != null && !Ethernet.isMACAddress(hostmac)) {
            throw new TagInvalidHostMacException(hostmac +
                                                 " is an invalid mac address");
        }
        if (hostmac == null && vlan == null && dpid == null &&
                interfaceName == null)
            return;
        String blankStr = new String("");
        String vlanStr = blankStr;
        if (vlan != null)
            vlanStr = new String(vlan.toString());

        if (hostmac == null)
            hostmac = blankStr;

        if (dpid == null && interfaceName != null)
            return;

        if (dpid == null)
            dpid = blankStr;
        if (interfaceName == null)
            interfaceName = blankStr;

        String ns = DEFAULT_NAMESPACE;
        if (!tag.getNamespace().equals("")) {
            ns = tag.getNamespace();
        }

        if (m_tags.get(ns) == null ||
            !m_tags.get(ns).containsKey(tag.getName())) {
            throw new TagDoesNotExistException(tag.toString());
        }

        String id = getTagKey(ns, tag.getName(), tag.getValue()) +
                Tag.KEY_SEPARATOR + hostmac + Tag.KEY_SEPARATOR +  vlanStr +
                Tag.KEY_SEPARATOR + dpid + Tag.KEY_SEPARATOR + interfaceName;
        storageSource.deleteRowAsync(TAGMAPPING_TABLE_NAME, id);
    }

    @Override
    public Set<Tag> getTags(String ns, String name) {
        if (ns != null && ns.equals("")) {
            ns = DEFAULT_NAMESPACE;
        }

        Map<String, Set<Tag>> nsTags = m_tags.get(ns);
        if (nsTags != null) {
            return nsTags.get(name);
        }
        return null;
    }

    @Override
    public Set<Tag> getTagsByNamespace(String ns) {
        if (ns == null) return null;

        Set<Tag> tags = new HashSet<Tag>();

        if (ns.equals("")) {
            ns = DEFAULT_NAMESPACE;
        }
        ConcurrentHashMap<String, Set<Tag>> nsTags = m_tags.get(ns);
        if (nsTags != null) {
            Iterator<Map.Entry<String, Set<Tag>>> it = nsTags.entrySet().iterator();
            while (it.hasNext()) {
                tags.addAll(it.next().getValue());
            }
            return tags;
        } else {
            return null;
        }
    }

    @Override
    public void addListener(ITagListener listener) {
        m_listeners.add(listener);
    }

    @Override
    public void removeListener(ITagListener listener) {
        m_listeners.remove(listener);
    }

    @Override
    public Set<Tag> getTagsByDevice(IDevice device) {
        logger.debug("Getting tags for device-" + device);
        Set <Tag> tagsForEntities = new HashSet<Tag>();
        Set <Entity> allPartialEntities = new HashSet <Entity>();
        allPartialEntities.add(new Entity(device.getMACAddress(), null,
                                          null, null, null, null));
        for (Short vlan : device.getVlanId()) {
            allPartialEntities.add(new Entity(0, vlan, null, null,
                                              null, null));
            allPartialEntities.add(new Entity(device.getMACAddress(), vlan,
                                              null, null, null, null));
            for (SwitchPort switchPort : device.getAttachmentPoints(true)) {
                allPartialEntities.add(new Entity(device.getMACAddress(), vlan,
                                              null, switchPort.getSwitchDPID(),
                                              null, null));
                allPartialEntities.add(new Entity(device.getMACAddress(), vlan,
                                              null, switchPort.getSwitchDPID(),
                                              switchPort.getPort(), null));
                allPartialEntities.add(new Entity(0, vlan, null,
                                                  switchPort.getSwitchDPID(),
                                                  null, null));
                allPartialEntities.add(new Entity(0, vlan, null,
                                                  switchPort.getSwitchDPID(),
                                                  switchPort.getPort(), null));
            }
        }
        for (SwitchPort switchPort : device.getAttachmentPoints(true)) {
            allPartialEntities.add(new Entity(0, null, null,
                                      switchPort.getSwitchDPID(), null, null));
            allPartialEntities.add(new Entity(0, null, null,
                                   switchPort.getSwitchDPID(),
                                   switchPort.getPort(), null));
            allPartialEntities.add(new Entity(device.getMACAddress(), null,
                                              null, switchPort.getSwitchDPID(),
                                              null, null));
            allPartialEntities.add(new Entity(device.getMACAddress(), null,
                                              null, switchPort.getSwitchDPID(),
                                              switchPort.getPort(), null));

        }
        for (Entity thisEntity : allPartialEntities) {
            tagsForEntities.addAll(this.getTagsByEntityConfig(
                                   EntityConfig.convertEntityToEntityConfig(
                                            controllerProvider, thisEntity)));
        }
        return tagsForEntities;
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#
     * getTagsByHost(java.lang.String, java.lang.Short, java.lang.String,
     * java.lang.String)
     */
    @Override
    public Set<Tag> getTagsByHost(String hostmac, Short vlan, String dpid,
                                  String interfaceName) {
        if (hostmac == null && vlan == null && dpid == null && interfaceName == null)
            return new HashSet<Tag>();
        String vlanStr = null;
        if (vlan != null)
            vlanStr = vlan.toString();
        return this.getTagsByEntityConfig(
              new EntityConfig(hostmac, vlanStr, dpid, interfaceName));
    }

    /* (non-Javadoc)
     * @see org.sdnplatform.tagmanager.ITagManagerService#
     * getDevicesByTag(org.sdnplatform.tagmanager.Tag)
     */
    @Override
    public Set <IDevice> getDevicesByTag(Tag tag) {
        String tagDBKey = tag.getDBKey();
        Set<EntityConfig> entities = this.tagToEntities.get(tagDBKey);
        if (entities == null)
            return null;
        Set <IDevice> retDevices= new HashSet <IDevice>();
        for (EntityConfig entity : entities) {
            Short vlan = (entity.vlan == null ? null : new Short(entity.vlan));
            Long dpid = (entity.dpid == null ? null : HexString.toLong(entity.dpid));
            Iterator<? extends IDevice> devicesReMapped =
                    this.queryDevices(HexString.toLong(entity.mac),
                                      vlan,
                                      null,
                                      dpid,
                                      extractSwitchPortNumber(entity.dpid,
                                              entity.interfaceName));
            while (devicesReMapped.hasNext()) {
                retDevices.add(devicesReMapped.next());
            }
        }
        return retDevices;
    }

    public void removeAllListeners() {
        m_listeners.clear();
    }

    @Override
    public Tag getTagFromDBId(String id) {
        if (id == null) return null;

        String[] fields = id.split("\\"+ Tag.KEY_SEPARATOR);
        // Tag key is expected to be in namespace|id format.
        if (fields == null || fields.length != 3) {
            return null;
        }
        return new Tag(fields[0], fields[1], fields[2]);
    }

    //****************************
    // Internal Methods = Tag related
    //*****************************

    private Integer extractSwitchPortNumber(String dpid, String ifaceName) {
        if (dpid != null && ifaceName != null) {
            IOFSwitch sw = controllerProvider.getSwitches().get(
                    HexString.toLong(dpid));
            if (sw == null) {
                logger.info("Switch {} not in switch map", dpid);
                return null;
            }
            OFPhysicalPort p = sw.getPort(ifaceName);
            if (p == null) {
                logger.info("On Switch {} Port {} does not exist yet",
                             dpid, ifaceName);
                return null;
            }
            return (int)p.getPortNumber();
        }
        return null;
    }

    /**
     * finds the change set of devices that got remapped and then notify
     * @param thisEntity
     */
    private void notifyAllDevicesReMapped(EntityConfig thisEntity) {
        /* query for all devices that match this entity and then
         * notify the tag listeners of this change
         */
        Integer port = null;
        if (thisEntity.dpid != null && thisEntity.interfaceName != null) {
            port = extractSwitchPortNumber(thisEntity.dpid,
                                           thisEntity.interfaceName);
        }

        Long longDpid = null;
        if (thisEntity.dpid != null)
            longDpid = new Long(HexString.toLong(thisEntity.dpid));
        Long longMac = null;
        if (thisEntity.mac != null)
            longMac = new Long(HexString.toLong(thisEntity.mac));
        Short vlan = null;
        if (thisEntity.vlan != null)
            vlan = new Short(thisEntity.vlan);
        Iterator<Device> devicesReMapped =
                this.getDeviceIteratorForQuery(longMac, vlan, null, longDpid,
                                               port);
        ArrayList <Device> devicesToNotify = new ArrayList <Device>();
        while (devicesReMapped.hasNext()) {
            Device device = devicesReMapped.next();
            if (reclassifyDevice(device) == false) {
                devicesToNotify.add(device);
            }
        }
        if (devicesToNotify.isEmpty())
            return;
        Iterator<? extends IDevice> devicesReMappedToNotify =
                devicesToNotify.iterator();
        TagManagerNotification notification =
                new TagManagerNotification(devicesReMappedToNotify);
        notification.setAction(
                           TagManagerNotification.Action.TAGDEVICES_REMAPPED);
        this.notifyListeners(notification);
    }

    private void addEntityTagConfig(EntityConfig thisEntity, Tag thisTag) {
        Set <Tag> thisEntityTags = entityToTags.get(thisEntity);
        if (thisEntityTags == null) {
            thisEntityTags = new HashSet <Tag>();
            entityToTags.put(thisEntity, thisEntityTags);
        }
        if (thisEntityTags.add(thisTag) == true) {
            /* notify listeners of this change
             *
             */
            notifyAllDevicesReMapped(thisEntity);
        }

        Set <EntityConfig> thisTagEntities = tagToEntities.get(thisTag.getDBKey());
        if (thisTagEntities == null) {
            thisTagEntities = new HashSet<EntityConfig>();
            tagToEntities.put(thisTag.getDBKey(), thisTagEntities);
        }
        thisTagEntities.add(thisEntity);
    }

    private void removeEntityTagConfig(EntityConfig thisEntity, Tag thisTag) {
        Set <Tag> thisEntityTags = entityToTags.get(thisEntity);
        if (thisEntityTags != null) {
            if (thisEntityTags.contains(thisTag)) {
                if (thisEntityTags.remove(thisTag) == true) {
                    /* notify listeners of this change
                     *
                     */
                    notifyAllDevicesReMapped(thisEntity);
                }
            }
            if (thisEntityTags.isEmpty()) {
                entityToTags.remove(thisEntity);
            }
        }

        Set <EntityConfig> thisTagEntities =
                tagToEntities.get(thisTag.getDBKey());
        if (thisTagEntities != null) {
            thisTagEntities.remove(thisEntity);
            if (thisTagEntities.isEmpty()) {
                tagToEntities.remove(thisTag.getDBKey());
            }
        }
    }

    protected Set<Tag> getTagsByEntityConfig(EntityConfig thisEntity) {
        if (thisEntity == null)
            return new HashSet<Tag>();
        if (logger.isTraceEnabled()) {
            logger.trace("get Tags for entity - " + thisEntity.toString());
            for (EntityConfig entity: this.entityToTags.keySet()) {
                logger.trace("Found a entityConfig key in entityToTags - " +
                              entity.toString());
            }
        }
        Set<Tag> tags = this.entityToTags.get(thisEntity);

        if (tags == null) {
            return new HashSet<Tag>();
        }
        if (logger.isTraceEnabled()) {
            for (Tag tag : tags) {
                logger.debug("getTagsByEntityConfig: Tag value is - " + tag.getDBKey());
            }
        }
        return tags;
    }

    protected Set<EntityConfig> getEntityConfigsByTag(Tag tag) {
        if (tag == null)
            return null;
        Set<EntityConfig> entities = this.tagToEntities.get(tag.getDBKey());
        if (entities == null) {
            return new HashSet<EntityConfig>();
        }
        return entities;
    }

    @LogMessageDoc(level="ERROR",
            message="Exception caught handling tagManager notification",
            explanation="A transient error occurred while notifying tog changes",
            recommendation=LogMessageDoc.TRANSIENT_CONDITION)
    @SuppressWarnings("incomplete-switch")
    private void notifyListeners(TagManagerNotification notification) {
        if (m_listeners != null) {
            for (ITagListener listener : m_listeners) {
                try {
                    switch (notification.getAction()) {
                        case ADD_TAG:
                            listener.tagAdded(notification.getTag());
                            break;
                        case DELETE_TAG:
                            listener.tagDeleted(notification.getTag());
                            break;
                        case TAGDEVICES_REMAPPED:
                            logger.debug("Notifying listeners that the tags of"
                                         + " devices for remapped");
                            listener.tagDevicesReMapped(
                                                    notification.getDevices());
                            break;
                    }
                }
                catch (Exception e) {
                    logger.error("Exception caught handling tagManager notification", e);
                }
            }
        }
    }

    /**
     * Add a new tag without updating storage.
     * Return true if no tag is associated with the id;
     *        false if a tag is associated with the id and is updated.
     * @param tag
     */
    private boolean addTagInternal(Tag tag) {
        boolean retCode = true;
        String ns = tag.getNamespace();
        if (ns.equals("")) {
            ns = DEFAULT_NAMESPACE;
        }
        ConcurrentHashMap<String, Set<Tag>> nsTags =
                m_tags.get(ns);
        if (nsTags == null) {
            nsTags = new ConcurrentHashMap<String, Set<Tag>>();
            m_tags.put(ns, nsTags);
        }
        Set<Tag> tags = nsTags.get(tag.getName());
        if (tags == null) {
            tags = new CopyOnWriteArraySet<Tag>();
            Set<Tag> oldtags = nsTags.putIfAbsent(tag.getName(), tags);
            if (oldtags != null) tags = oldtags;
        }
        retCode = tags.add(tag);

        return retCode;
    }

    /**
     * Delete a new tag without updating storage
     * @param tag
     */
    private boolean deleteTagInternal(Tag tag) {
        boolean retCode = true;

        String ns = tag.getNamespace();
        if (ns.equals("")) {
            ns = DEFAULT_NAMESPACE;
        }


        ConcurrentHashMap<String, Set<Tag>> nsTags =
                m_tags.get(ns);
        if (nsTags != null) {
            Set<Tag> tags = nsTags.get(tag.getName());
            if (tags == null) {
                retCode = false;
            } else {
                retCode = tags.remove(tag);
                if (tags.size() == 0) nsTags.remove(tag.getName());
                if (nsTags.size() == 0) m_tags.remove(ns);
            }
        } else {
            retCode = false;
        }
        // Remove tag mapping when the tag is removed
        if (retCode) {
            Set<EntityConfig> entities =
                    this.tagToEntities.remove(tag.getDBKey());
            if (entities != null) {
                for (EntityConfig thisEntity : entities) {
                    this.removeEntityTagConfig(thisEntity, tag);
                }
            }
        }

        return retCode;
    }

    /**
     * @param newTag
     * @param mac
     * @param vlan
     * @param dpid
     * @param interfaceName
     */
    private void addTagHostMappingInternal(Tag tag, String mac,
                                           String vlan, String dpid,
                                           String interfaceName) {
        addTagInternal(tag);
        EntityConfig entity = new EntityConfig(mac, vlan, dpid,
                                               interfaceName);
        this.addEntityTagConfig(entity, tag);

    }

    private void deleteTagHostMappingInternal(Tag tag, String mac, String vlan,
                                          String dpid, String interfaceName) {
        EntityConfig entity = new EntityConfig(mac, vlan, dpid, interfaceName);
        this.removeEntityTagConfig(entity, tag);
        return;
    }

    //*********************
    //   Storage Listener
    //*********************

    public IStorageSourceService getStorageSource() {
        return this.storageSource;
    }

    public void setStorageSource(IStorageSourceService s) {
        storageSource = s;
    }

    /**
     * Called when a new row is inserted into a table.
     *
     * @param tableName The table where the rows were inserted
     * @param rowKeys The keys of the rows that were inserted
     */
    @LogMessageDoc(level="WARN",
            message="BigDeviceManager ignore rowModified event for table {table}",
            explanation="Ignored modify event for unknown table",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        if (tableName == null || rowKeys == null) {
            return;
        }

        if (tableName.equals(TAG_TABLE_NAME)) {
            tagRowsInserted(rowKeys);
        } else if (tableName.equals(TAGMAPPING_TABLE_NAME)) {
            tagMappingRowsInserted(rowKeys);
        } else if (tableName.equals(HOSTSECURITYIPADDRESS_TABLE_NAME)) {
            hostSecurityIpAddressModified(rowKeys, false);
        } else if (tableName.equals(HOSTSECURITYATTACHMENTPOINT_TABLE_NAME)) {
            hostSecurityAttachmentPointModified(rowKeys, false);
        } else {
            logger.warn("BigDeviceManager ignore rowModified event for table {}",
                        tableName);
        }
    }

    /**
     * Called when a new row is deleted from a table.
     *
     * @param tableName The table where the rows were deleted
     * @param rowKeys The keys of the rows that were deleted
     */
    @LogMessageDoc(level="WARN",
            message="BigDeviceManager ignore rowDeleted event for table {table}",
            explanation="Ignored delete event for unknown table",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (tableName == null || rowKeys == null) {
            return;
        }

        if (tableName.equals(TAG_TABLE_NAME)) {
            tagRowsDeleted(rowKeys);
        } else if (tableName.equals(TAGMAPPING_TABLE_NAME)) {
            tagMappingRowsDeleted(rowKeys);
        } else if (tableName.equals(HOSTSECURITYIPADDRESS_TABLE_NAME)) {
            hostSecurityIpAddressModified(rowKeys, true);
        } else if (tableName.equals(HOSTSECURITYATTACHMENTPOINT_TABLE_NAME)) {
            hostSecurityAttachmentPointModified(rowKeys, true);
        } else {
            logger.warn("BigDeviceManager ignore rowDeleted event for table {}",
                        tableName);
        }
    }

    //*********************
    //   Internal Methods - Storage/Tag related
    //*********************

    private void tagRowsInserted(Set<Object> rowKeys) {
        try {
            m_lock.writeLock().lock();

            for (Object row: rowKeys) {
                String key = "";
                if (!(row instanceof String)) {
                    continue;
                } else {
                    key = (String)row;
                }

                IResultSet resultSet = storageSource.getRow(TAG_TABLE_NAME, key);
                if (resultSet.next()) {
                    String ns = null;
                    String tagName = null;
                    String tagValue = null;

                    ns = resultSet.getString(TAG_NAMESPACE_COLUMN_NAME);
                    tagName = resultSet.getString(TAG_NAME_COLUMN_NAME);
                    tagValue = resultSet.getString(TAG_VALUE_COLUMN_NAME);

                    Tag newTag = new Tag(ns, tagName, tagValue);
                    TagManagerNotification.Action action =
                            TagManagerNotification.Action.ADD_TAG;
                    if (addTagInternal(newTag)) {
                        TagManagerNotification notification =
                                new TagManagerNotification(newTag, null, action);
                        notifyListeners(notification);
                    }
                }
            }
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    @LogMessageDoc(level="ERROR",
            message="Configuration error, entity spec has a interface " +
                    "specified but no dpid, rejecting this config {tag} {mac}" +
                    " {vlan} {OFportname}",
            explanation="Tag match configuration has a OF-Portname specified " +
                        "but switch dpid not specifed",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    private void tagMappingRowsInserted(Set<Object> rowKeys) {
        try {
            m_lock.writeLock().lock();

            for (Object row: rowKeys) {
                String key = "";
                if (!(row instanceof String)) {
                    continue;
                } else {
                    key = (String)row;
                }

                IResultSet resultSet = storageSource.getRow(TAGMAPPING_TABLE_NAME,
                                                            key);
                if (resultSet.next()) {
                    String tagid = resultSet.getString(TAGMAPPING_TAG_COLUMN_NAME);
                    String mac = resultSet.getString(TAGMAPPING_MAC_COLUMN_NAME);
                    String vlanStr =
                            resultSet.getString(TAGMAPPING_VLAN_COLUMN_NAME);

                    String dpid =
                            resultSet.getString(TAGMAPPING_SWITCH_COLUMN_NAME);
                    String interfaceName =
                            resultSet.getString(TAGMAPPING_INTERFACE_COLUMN_NAME);
                    Tag newTag = getTagFromDBId(tagid);
                    if (dpid == null && interfaceName != null) {
                        logger.error("Configuration error, entity spec has a " +
                                "interface specified but no dpid, rejecting " +
                                "this config - " + tagid + " " + mac + " " +
                                vlanStr + " " + interfaceName);
                        return;
                    }
                    this.addTagHostMappingInternal(newTag, mac, vlanStr, dpid,
                                                   interfaceName);
                }
            }
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    private void tagRowsDeleted (Set<Object> rowKeys) {
        try {
            m_lock.writeLock().lock();

            for (Object row: rowKeys) {
                String key = null;
                if (row instanceof String) {
                    key = (String)row;

                    Tag tag = getTagFromDBId(key);
                    if (!deleteTagInternal(tag)) {
                        logger.info("rowsDeleted, tag {} doesn't exist", tag);
                        return;
                    }

                    TagManagerNotification notification =
                            new TagManagerNotification(tag, null,
                                                       TagManagerNotification.Action.DELETE_TAG);
                    notifyListeners(notification);
                }
            }
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    private void tagMappingRowsDeleted (Set<Object> rowKeys) {
        try {
            m_lock.writeLock().lock();

            for (Object row: rowKeys) {
                String key = "";
                if (!(row instanceof String)) {
                    continue;
                } else {
                    key = (String)row;
                }
                String[] fields = key.split("\\"+ Tag.KEY_SEPARATOR);


                Tag newTag = null;
                String mac = null;
                String vlanStr = null;
                String dpid = null;
                String interfaceName = null;

                if (fields == null || fields.length < 3) {
                    return;
                }
                newTag = new Tag(fields[0], fields[1], fields[2]);
                if (fields.length < 3)
                    return;
                if (!fields[3].isEmpty())
                    mac = fields[3];
                if (fields.length > 4) {
                    if (!fields[4].isEmpty())
                        vlanStr = fields[4];
                    if (fields.length > 5) {
                        if (!fields[5].isEmpty())
                            dpid = fields[5];
                        if (fields.length > 6)
                            if (!fields[6].isEmpty())
                                interfaceName = fields[6];
                    }
                }

                if (dpid == null && interfaceName != null) {
                    logger.error("Configuration error, entity spec has a " +
                            "interface specified but no dpid, rejecting " +
                            "this config - " + newTag + " " + mac + " " +
                            vlanStr + " " + interfaceName);
                    return;
                }
                this.deleteTagHostMappingInternal(newTag, mac, vlanStr, dpid,
                                                  interfaceName);

            }
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    private String getTagKey(String ns, String name, String value) {
        return  ns + Tag.KEY_SEPARATOR + name +
                Tag.KEY_SEPARATOR + value;
    }

    protected Tag getTagFromMappingId(String id) {
        if (id == null) return null;

        String[] fields = id.split("\\"+ Tag.KEY_SEPARATOR);
        // Tag key is expected to be in namespace|id format.
        if (fields == null || fields.length != 4) {
            return null;
        }
        return new Tag(fields[0], fields[1], fields[2]);
    }

    /**
     * Clears the in-memory tags
     */
    protected void clearTagsFromMemory() {
        m_lock.writeLock().lock();
        try {
            m_tags.clear();
            this.tagToEntities.clear();
            this.entityToTags.clear();
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    /**
     * Read the Tag information from storage
     */
    protected void loadTagsFromStorage() throws StorageException {
        m_lock.writeLock().lock();
        try {
            // Flush device mappings
            m_tags.clear();
            this.tagToEntities.clear();
            this.entityToTags.clear();

            IResultSet resultSet = storageSource.executeQuery(TAG_TABLE_NAME,
                    new String[]{TAG_ID_COLUMN_NAME, TAG_NAMESPACE_COLUMN_NAME,
                    TAG_NAME_COLUMN_NAME, TAG_VALUE_COLUMN_NAME},
                    null, null);

            while (resultSet.next()) {
                String ns = resultSet.getString(TAG_NAMESPACE_COLUMN_NAME);
                String tagName = resultSet.getString(TAG_NAME_COLUMN_NAME);
                String tagValue = resultSet.getString(TAG_VALUE_COLUMN_NAME);

                Tag tag = new Tag(ns, tagName, tagValue);
                addTagInternal(tag);
                logger.trace("Configured {} ", tag);
            }

            resultSet = storageSource.executeQuery(TAGMAPPING_TABLE_NAME, null,
                                                   null, null);

            while (resultSet.next()) {
                String tagid = resultSet.getString(TAGMAPPING_TAG_COLUMN_NAME);
                String mac = resultSet.getString(TAGMAPPING_MAC_COLUMN_NAME);
                String vlanStr = resultSet.getString(
                                      TAGMAPPING_VLAN_COLUMN_NAME);

                String dpid =
                        resultSet.getString(TAGMAPPING_SWITCH_COLUMN_NAME);
                String interfaceName =
                        resultSet.getString(TAGMAPPING_INTERFACE_COLUMN_NAME);
                Tag tag = getTagFromDBId(tagid);
                if ((dpid == null || dpid.isEmpty()) &&
                        (interfaceName != null && !interfaceName.isEmpty())) {
                    logger.error("Configuration error, entity spec has a " +
                                 "interface specified but no dpid, rejecting " +
                                 "this config - " + tagid + " " + mac + " " +
                                 vlanStr + " " + interfaceName);
                    return;
                }
                this.addTagHostMappingInternal(tag, mac, vlanStr, dpid,
                                               interfaceName);
                logger.trace("Configured mapping ", tag, " -  " + mac +
                             vlanStr + dpid + interfaceName);
            }


        } finally {
            m_lock.writeLock().unlock();
        }
    }

    //******************************************
    //   Internal Methods - Security/Spoofing Related
    //******************************************

    /**
     * Handle a config change from storage: HostSecurityIpAddress config
     * has changed.
     *
     * @param compoundKeys A set of compound row keys from the database. We
     *        will split the compound key in its components instead of
     *        querying the table and all its references
     * @param isDeleted Inidicates whether the entry should be added or
     *        deleted from our internal structures.
     */
    @LogMessageDoc(level="ERROR",
            message="RowKey from HostSecurityIpAddress table is not a String",
            explanation="Error in a Host security IP-Address configuration, " +
                        "compound key is not a string",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    protected void hostSecurityIpAddressModified(Set<Object> compoundKeys,
                                                 boolean isDeleted) {
        // We don't really need to synchronized here,
        // hostSecurityIpAddressModified(String,boolean) will also
        // synchronize but this prevents potentially thousands of lock/unlock
        // operations
        synchronized(antiSpoofingConfigWriteLock) {
            for(Object key: compoundKeys) {
                if (!(key instanceof String)) {
                    logger.error("RowKey from HostSecurityIpAddress table "
                            + "is not a String");
                    continue;
                }
                hostSecurityIpAddressModified((String)key, isDeleted);
            }
        }
    }

    /**
     * Handle a config change from storage: HostSecurityIpAddress config
     * has changed.
     *
     * @param compoundKey A single compound row key from the database. We
     *        will split the compound key in its components instead of
     *        querying the table and all its references
     * @param isDeleted Inidicates whether the entry should be added or
     *        deleted from our internal structures.
     */
    @LogMessageDocs({
                    @LogMessageDoc(level="ERROR",
                    message="Cannot specify a VLAN for " +
                            "HostSecurityIpAddress if the address space is " +
                            "default. {Compound-key}",
                    explanation="Unsupported Vlan in a host security Ip-Address " +
                            "configuration for default address space",
                    recommendation=LogMessageDoc.GENERIC_ACTION),
                    @LogMessageDoc(level="ERROR",
                    message="Invalid Vlan in compound key from " +
                            "HostSecurityIpAddress table",
                    explanation="Invalid Vlan in a host security Ip-Address " +
                                "configuration",
                    recommendation=LogMessageDoc.GENERIC_ACTION),
                    @LogMessageDoc(level="ERROR",
                    message="Invalid MAC address in compound key from " +
                            "HostSecurityIpAddress table",
                    explanation="Invalid Vlan in a host security Ip-Address " +
                                "configuration",
                    recommendation=LogMessageDoc.GENERIC_ACTION),
                    @LogMessageDoc(level="ERROR",
                    message="Invalid IP address in compound key from " +
                            "HostSecurityIpAddress table",
                    explanation="Invalid Vlan in a host security Ip-Address " +
                                "configuration",
                    recommendation=LogMessageDoc.GENERIC_ACTION),
                    @LogMessageDoc(level="ERROR",
                    message="Invalid compound primary key in " +
                             "HostSecurityIpAddress table",
                    explanation="Invalid compound primary key in a host " +
                                "security Ip-Address configuration",
                    recommendation=LogMessageDoc.GENERIC_ACTION),
                    })
    protected void hostSecurityIpAddressModified(String compoundKey,
                                                 boolean isDeleted) {

        String[] fields = compoundKey.split(COMPOUND_KEY_SEPARATOR_REGEX);
        if (fields.length != 4) {
            logger.error("Invalid compound primary key {} in " +
                         "HostSecurityIpAddress table", compoundKey);
            return;
        }
        String addrSpace = fields[0];
        String vlanString = fields[1];
        String macString = fields[2];
        String ipString = fields[3];

        Short vlan;
        if (vlanString.equals("")) {
            vlan = null;
        } else if (! addrSpace.equals("default")) {
            // not default address space but vlan specified: invalid
            logger.error("Cannot specify a VLAN for " +
                    "HostSecurityIpAddress if the address space is " +
                    "not default. Compound-key: {}", compoundKey);
            return;

        } else {
            try {
                vlan = Short.parseShort(vlanString);
            } catch (NumberFormatException e) {
                logger.error("Invalid Vlan {} in compound key {} from " +
                             "HostSecurityIpAddress table", vlanString, compoundKey);
                return;
            }
            if (vlan < 1 || vlan > 4095) {
                logger.error("Invalid VLAN {} in compound key {} from " +
                             "HostSecurityIpAddress table", vlanString, compoundKey);
                return;
            }
        }

        Long mac;
        try {
            mac = HexString.toLong(macString);
        } catch (NumberFormatException e) {
            logger.error("Invalid MAC address {} in compound key {} from " +
                         "HostSecurityIpAddress table", macString, compoundKey);
            return;
        }
        if (mac >= (1L<<48)) {
            logger.error("Invalid MAC address {} in compound key {} from " +
                         "HostSecurityIpAddress table", macString, compoundKey);
            return;
        }

        Integer ip;
        try {
            ip = IPv4.toIPv4Address(ipString);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid IP address {} in compound key {} from " +
                         "HostSecurityIpAddress table", ipString, compoundKey);
            return;
        }

        if (logger.isDebugEnabled()) {
            String verb = (isDeleted) ? "Removing" : "Adding";
            logger.debug("HostSecurityIp: {} entry addressSpace={}, mac={}, ip={}",
                         new Object[] { verb, addrSpace, macString, ipString });
        }

        synchronized(antiSpoofingConfigWriteLock) {
            if (isDeleted)
                removeAntiSpoofingIp2Mac(addrSpace, vlan, mac, ip);
            else
                addAntiSpoofingIp2Mac(addrSpace, vlan, mac, ip);
        }
    }

    /**
     * Add an IP to MAC anti-spoofing entry. Lock MAC to IP
     *
     * NOTE: The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param ip
     */
    protected void addAntiSpoofingIp2Mac(String addrSpace,
                                         Short vlan,
                                         Long mac,
                                         Integer ip) {
        ScopedIp scopedIp = new ScopedIp(addrSpace, ip);
        DeviceId host = new DeviceId(addrSpace, vlan, mac);

        Collection<DeviceId> hosts = hostSecurityIpMap.get(scopedIp);

        if (hosts == null) {
            hosts = Collections.newSetFromMap(
                           new ConcurrentHashMap<DeviceId, Boolean>());
        }
        hosts.add(host);
        hostSecurityIpMap.putIfAbsent(scopedIp, hosts);
    }

    /**
     * Remove an IP to MAC anti-spoofing entry.
     *
     * NOTE: The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param ip
     */
    protected void removeAntiSpoofingIp2Mac(String addrSpace,
                                            Short vlan,
                                            Long mac,
                                            Integer ip) {
        ScopedIp scopedIp = new ScopedIp(addrSpace, ip);
        Collection<DeviceId> hosts = hostSecurityIpMap.get(scopedIp);
        if (hosts == null) {
            return;
        }
        DeviceId host = new DeviceId(addrSpace, vlan, mac);
        hosts.remove(host);
        if (hosts.isEmpty())
            hostSecurityIpMap.remove(scopedIp);
    }

    @LogMessageDoc(level="ERROR",
            message="RowKey from HostSecurityAttachmentPoint table is not a " +
                    "String",
            explanation="Error in a Host security attachment-point " +
                        "configuration, compound key is not a string",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    /**
     * Handle a config change from storage: HostSecurityAttachmentPoint config
     * has changed.
     *
     * @param compoundKeys A set of compound row keys from the database. We
     *        will split the compound key in its components instead of
     *        querying the table and all its references
     * @param isDeleted Indicates whether the entry should be added or
     *        deleted from our internal structures.
     */
    protected void hostSecurityAttachmentPointModified(Set<Object> compoundKeys,
                                                       boolean isDeleted) {
        // We don't really need to synchronized here,
        // hostSecurityAttachmentPointModified(String,boolean) will also
        // synchronize but this prevents potentially thousands of lock/unlock
        // operations
        synchronized(antiSpoofingConfigWriteLock) {
            for(Object key: compoundKeys) {
                if (!(key instanceof String)) {
                    logger.error("RowKey from HostSecurityAttachmentPoint table "
                                 + "is not a String");
                    continue;
                }
                hostSecurityAttachmentPointModified((String)key, isDeleted);
            }
        }
    }

    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Cannot specify a VLAN for " +
                        "HostSecurityAttachmentPoint if the address space is " +
                        "default. {Compound-key}",
                explanation="Unsupported Vlan in a host security Ip-Address " +
                        "configuration for default address space",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="ERROR",
                message="Invalid MAC in compound key from " +
                        "HostSecurityAttachmentPoint table",
                explanation="Invalid MAC in a host security Ip-Address "
                                + "configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION),
         @LogMessageDoc(level="ERROR",
                message="Invalid Vlan in compound key from " +
                        "HostSecurityAttachmentPoint table",
                explanation="Invalid Vlan in a host security Ip-Address "
                                + "configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="ERROR",
                message="Invalid DPID in compound key from " +
                        "HostSecurityAttachmentPoint table",
                explanation="Invalid DPID in a host security Ip-Address "
                                + "configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="ERROR",
                message="Invalid compound primary key in " +
                         "HostSecurityAttachmentPoint table",
                explanation="Invalid compound primary key in a host " +
                            "security attachment point configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    /**
     * Handle a config change from storage: HostSecurityAttachmentPoint config
     * has changed.
     *
     * @param compoundKey A single compound row key from the database. We
     *        will split the compound key in its components instead of
     *        querying the table and all its references
     * @param isDeleted Inidicates whether the entry should be added or
     *        deleted from our internal structures.
     */
    protected void hostSecurityAttachmentPointModified(String compoundKey,
                                                       boolean isDeleted) {
        String[] fields = compoundKey.split(COMPOUND_KEY_SEPARATOR_REGEX);
        if (fields.length != 5) {
            logger.error("Invalid compound primary key {} in " +
                         "HostSecurityAttachmentPoint table", compoundKey);
            return;
        }
        String addrSpace = fields[0];
        String vlanString = fields[1];
        String macString = fields[2];
        String dpidString = fields[3];
        String ifaceNameRegex = fields[4];

        Short vlan;
        if (vlanString.equals("")) {
            vlan = null;
        } else if (! addrSpace.equals("default")) {
            // not default address space but vlan specified: invalid
            logger.error("Cannot specify a VLAN for " +
                    "HostSecurityAttachmentPoint if the address space is " +
                    "not default. Compound-key: {}", compoundKey);
            return;

        } else {
            try {
                vlan = Short.parseShort(vlanString);
            } catch (NumberFormatException e) {
                logger.error("Invalid Vlan {} in compound key {} from " +
                             "HostSecurityAttachmentPoint table",
                             vlanString, compoundKey);
                return;
            }
            if (vlan < 1 || vlan > 4095) {
                logger.error("Invalid VLAN {} in compound key {} from " +
                             "HostSecurityAttachmentPoint table",
                             vlanString, compoundKey);
                return;
            }
        }
        Long mac;
        try {
            mac = HexString.toLong(macString);
        } catch (NumberFormatException e) {
            logger.error("Invalid MAC address {} in compound key {} from " +
                         "HostSecurityAttachmentPoint table",
                         macString, compoundKey);
            return;
        }
        if (mac >= (1L<<48)) {
            logger.error("Invalid MAC address {} in compound key {} from " +
                         "HostSecurityAttachmentPoint table",
                         macString, compoundKey);
            return;
        }
        Long dpid;
        try {
            if (dpidString.equals("\010")) // octal code for backspace, used by sdncon for NULL
                dpid = null;
            else
                dpid = HexString.toLong(dpidString);
        } catch (NumberFormatException e) {
            logger.error("Invalid DPID {} in compound key {} from " +
                         "HostSecurityAttachmentPoint table",
                         dpidString, compoundKey);
            return;
        }

        if (logger.isDebugEnabled()) {
            String verb = (isDeleted) ? "Removing" : "Adding";
            logger.debug("HostSecurityAttachmentPoint: {} entry addressSpace={}, mac={}, " +
                         "switch={}, ifaceRegex={}",
                         new Object[] { verb, addrSpace, macString,
                                        dpidString, ifaceNameRegex });
        }
        synchronized(antiSpoofingConfigWriteLock) {
            if (isDeleted)
                removeHostSecurityInterfaceEntry(addrSpace, vlan, mac,
                                                 dpid, ifaceNameRegex);
            else
                addHostSecurityInterfaceEntry(addrSpace, vlan, mac,
                                              dpid, ifaceNameRegex);
        }
    }

    /**
     * Add an host to SwitchInterface anti-spoofing entry.
     *
     * The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param dpid
     * @param ifaceName
     */
    protected void addHostSecurityInterfaceEntry(String addrSpace,
                                                 Short vlan,
                                                 Long mac,
                                                 Long dpid,
                                                 String ifaceName) {
        DeviceId host = new DeviceId(addrSpace, vlan, mac);
        Collection<String> keys = hostSecurityInterfaceRegexMap.get(host);
        if (keys == null) {
            keys = Collections.newSetFromMap(
                            new ConcurrentHashMap<String, Boolean>());
        }
        String key = addrSpace + "|" + vlan + "|" + mac + "|"
                    + dpid + "|" + ifaceName;
        keys.add(key);
        hostSecurityInterfaceRegexMap.put(host, keys);
        interfaceRegexMatcher.addOrUpdate(key, dpid, ifaceName);
        /* CURRENTLY UNUSED:
         * use for exact matches
        SwitchInterface switchIface = new SwitchInterface(dpid, ifaceName);
        ConcurrentHashMap<Long, Collection<SwitchInterface>> mac2switchIface =
                hostSecurityInterfaceMap.get(addrSpace);
        if (mac2switchIface == null) {
            mac2switchIface = new ConcurrentHashMap<Long,
                                                    Collection<SwitchInterface>>();
        }
        Collection<SwitchInterface> ifaces = mac2switchIface.get(mac);
        if (ifaces == null) {
            ifaces = Collections.newSetFromMap(
                            new ConcurrentHashMap<SwitchInterface, Boolean>());
        }
        ifaces.add(switchIface);
        mac2switchIface.putIfAbsent(mac, ifaces);
        hostSecurityInterfaceMap.putIfAbsent(addrSpace, mac2switchIface);
        */
    }

    /**
     * Remove an host to SwitchInterface anti-spoofing entry.
     *
     * The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param dpid
     * @param ifaceName
     */
    protected void removeHostSecurityInterfaceEntry(String addrSpace,
                                                    Short vlan,
                                                    Long mac,
                                                    Long dpid,
                                                    String ifaceName) {
        DeviceId host = new DeviceId(addrSpace, vlan, mac);
        Collection<String> keys = hostSecurityInterfaceRegexMap.get(host);
        if (keys == null) {
            return;
        }
        String key = addrSpace + "|" + vlan + "|" + mac + "|"
                    + dpid + "|" + ifaceName;
        keys.remove(key);
        if (keys.isEmpty())
            hostSecurityInterfaceRegexMap.remove(host);
        interfaceRegexMatcher.remove(key);

        /* CURRENTLY UNUSED:
         * use for exact matches
        SwitchInterface switchIface = new SwitchInterface(dpid, ifaceName);
        ConcurrentHashMap<Long, Collection<SwitchInterface>> mac2switchIface =
                hostSecurityInterfaceMap.get(addrSpace);
        if (mac2switchIface == null) {
            return;
        }
        Collection<SwitchInterface> ifaces = mac2switchIface.get(mac);
        if (ifaces == null) {
            return;
        }
        ifaces.remove(switchIface);
        if (ifaces.isEmpty())
            mac2switchIface.remove(mac);
        if (mac2switchIface.isEmpty())
            hostSecurityInterfaceMap.remove(addrSpace);
        */
    }

    /**
     * Read anti-spoofing configuration from storage
     */
    protected void readAntiSpoofingConfigFromStorage() throws StorageException {
        synchronized(antiSpoofingConfigWriteLock) {
            hostSecurityIpMap.clear();
            hostSecurityInterfaceMap.clear();

            IResultSet resultSet;

            // Read HostSecurityIpAddress
            resultSet = storageSource
                    .executeQuery(HOSTSECURITYIPADDRESS_TABLE_NAME,
                                  null, null, null);
            while (resultSet.next()) {
                hostSecurityIpAddressModified(
                        resultSet.getString(HOSTSECURITYIPADDRESS_ID_COLUMN_NAME),
                        false);
            }

            // Read HostSecurityAttachmentPoint
            resultSet = storageSource
                    .executeQuery(HOSTSECURITYATTACHMENTPOINT_TABLE_NAME,
                                  null, null, null);
            while (resultSet.next()) {
                hostSecurityAttachmentPointModified(
                        resultSet.getString(HOSTSECURITYATTACHMENTPOINT_ID_COLUMN_NAME),
                        false);
            }
        }
    }

    /*
     * allows for exact matching of switchInterfaces. CURRENTLY UNUSED SINCE
     * WE USE ONLY THE REGEX MATCHER FOR THE TIME BEING.
     */
    protected boolean checkHostSecurityInterfaceExact(String addrSpace,
                                                     Entity entity) {
        Long mac = entity.getMacAddress();
        Map<Long, Collection<SwitchInterface>> mac2SwitchPort =
                hostSecurityInterfaceMap.get(addrSpace);
        if (mac2SwitchPort == null) {
            // No config for this address space: allow
            return true;
        }
        Collection<SwitchInterface> switchInterfaces = mac2SwitchPort.get(mac);
        if (switchInterfaces == null || switchInterfaces.isEmpty()) {
            // no config for this Mac: allow
            return true;
        }
        for(SwitchInterface swi: switchInterfaces) {
            if (swi.getPortNumber() != null &&
                    topology.isConsistent(entity.getSwitchDPID(),
                                      (short)entity.getSwitchPort().intValue(),
                                      swi.dpid,
                                      swi.getPortNumber())) {
                // the port in the entity is consistent with one of the allowed
                // ports: allow the entity. We note that a port going into
                // a BD is consistent with every other port going to the same
                // BD.
                return true;
            }
        }
        return false;
    }

    @LogMessageDoc(level="WARN",
            message="Drop packet with srcMac equals to {virtualMac} " +
                    "for {services}",
            explanation="Dropped packet with Source MAC equal to virtual mac of"
                        + " service",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    @Override
    protected boolean isEntityAllowed(Entity entity, IEntityClass entityClass) {
        String addressSpaceName = entityClass.getName();
        Integer ip = entity.getIpv4Address();
        Long mac = entity.getMacAddress();
        Short vlan = null;
        if (entityClass.getKeyFields().contains(DeviceField.VLAN))
            vlan = entity.getVlan();
        DeviceId host = new DeviceId(addressSpaceName, vlan, mac);

        // First, check for IP spoofing
        if (entity.getIpv4Address() != null) {
            ScopedIp scopedIp = new ScopedIp(addressSpaceName, ip);
            Collection<DeviceId> hosts = hostSecurityIpMap.get(scopedIp);
            if (hosts != null && (!hosts.contains(host)))
                return false;
        }

        // check attachment point

        if (!isValidAttachmentPoint(entity.getSwitchDPID(),
                                    entity.getSwitchPort())) {
            // not an AP port: allow
            return true;
        }

        Collection<String> keys = hostSecurityInterfaceRegexMap.get(host);
        if (keys == null) {
            // no config for this host: allow
            return true;
        }
        for (String key: keys) {
            Collection<SwitchPort> ifaces =
                    interfaceRegexMatcher.getInterfacesByKey(key);
            if (ifaces == null) {
                continue;
            }
            for (SwitchPort swp: ifaces) {
                if (topology.isAttachmentPointPort(swp.getSwitchDPID(),
                                                   (short)swp.getPort()) &&
                        topology.isConsistent(entity.getSwitchDPID(),
                                      (short)entity.getSwitchPort().intValue(),
                                      swp.getSwitchDPID(),
                                      (short)swp.getPort())) {
                    // the port in the entity is consistent with one of the allowed
                    // ports: allow the entity. We note that a port going into
                    // a BD is consistent with every other port going to the same
                    // BD.
                    return true;
                }
            }
        }
        return false;
    }

    //***************
    // IModule
    //***************

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(ITagManagerService.class);
        l.addAll(super.getModuleServices());
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
    getServiceImpls() {
        Map<Class<? extends IPlatformService>,
            IPlatformService> m =
                super.getServiceImpls();
        m.put(ITagManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
    getModuleDependencies() {
    	return null;
    }

    @Override
    public void init(ModuleContext context) {
        super.init(context);
        ecs = context.getServiceImpl(IEntityClassifierService.class);

        m_listeners = new CopyOnWriteArraySet<ITagListener>();
        m_lock = new ReentrantReadWriteLock();
        m_tags = new ConcurrentHashMap<String, ConcurrentHashMap<String,
                Set<Tag>>>();

        tagToEntities = new ConcurrentHashMap<String, Set<EntityConfig>>();
        entityToTags = new ConcurrentHashMap<EntityConfig, Set<Tag>>();

        antiSpoofingConfigWriteLock = new Object();
        interfaceRegexMatcher =
                new SwitchInterfaceRegexMatcher(controllerProvider);
        hostSecurityIpMap =
                new ConcurrentHashMap<ScopedIp, Collection<DeviceId>>();
        hostSecurityInterfaceMap = new ConcurrentHashMap<String,
                ConcurrentHashMap<Long,Collection<SwitchInterface>>>();
        hostSecurityInterfaceRegexMap =
                new ConcurrentHashMap<DeviceId, Collection<String>>();
    }

    @Override
    public void startUp(ModuleContext context) {
        // Our 'constructor'
        super.startUp(context);

        storageSource.createTable(BetterDeviceManagerImpl.TAG_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(BetterDeviceManagerImpl.TAG_TABLE_NAME,
                BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME);
        storageSource.createTable(BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME,
                                  null);
        storageSource.setTablePrimaryKeyName(
                                    BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME,
                                BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME);

        loadTagsFromStorage();
        storageSource.addListener(TAG_TABLE_NAME, this);
        storageSource.addListener(TAGMAPPING_TABLE_NAME, this);

        // Anti-spoofing storage
        storageSource.createTable(HOSTSECURITYATTACHMENTPOINT_TABLE_NAME, null);
        storageSource
                .setTablePrimaryKeyName(HOSTSECURITYATTACHMENTPOINT_TABLE_NAME,
                                        HOSTCONFIG_ID_COLUMN_NAME);
        storageSource.createTable(HOSTSECURITYIPADDRESS_TABLE_NAME, null);
        storageSource
                .setTablePrimaryKeyName(HOSTSECURITYIPADDRESS_TABLE_NAME,
                                        HOSTCONFIG_ID_COLUMN_NAME);

        readAntiSpoofingConfigFromStorage();
        storageSource.addListener(HOSTSECURITYIPADDRESS_TABLE_NAME, this);
        storageSource.addListener(HOSTSECURITYATTACHMENTPOINT_TABLE_NAME, this);

        controllerProvider.addOFSwitchListener(interfaceRegexMatcher);
    }

    //***************
    // IHAListener
    //***************

    @LogMessageDoc(level="WARN",
            message="Unknown controller role: {role}",
            explanation="Controller's role seems to a unknown role",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        super.roleChanged(oldRole, newRole);
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    logger.debug("Re-reading tags from storage due " +
                            "to HA change from SLAVE->MASTER");
                    loadTagsFromStorage();
                }
                break;
            case SLAVE:
                logger.debug("Clearing tags due to " +
                        "HA change to SLAVE");
                clearTagsFromMemory();
                break;
            default:
                logger.warn("Unknown controller role: {}", newRole);
                break;
        }
    }

    @Override
    public void controllerNodeIPsChanged(
            Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {
        super.controllerNodeIPsChanged(curControllerNodeIPs,
                                       addedControllerNodeIPs,
                                       removedControllerNodeIPs);
        // ignore
    }
}
