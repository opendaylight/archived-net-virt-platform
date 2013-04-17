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

/**
 *
 */
package org.sdnplatform.addressspace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.util.SingletonTask;
import org.sdnplatform.devicegroup.DeviceGroupMatcher;
import org.sdnplatform.devicegroup.MembershipRule;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IEntityClassListener;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;
import org.sdnplatform.devicemanager.internal.Entity;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceListener;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.StorageException;
import org.sdnplatform.tagmanager.ITagManagerService;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * @author gregor
 *
 * TODO: unify vlan semantics: -1 vs. NULL vs. 0 vs. untagged vs. not-set
 * (this is a sdnplatform wide task)
 *
 * TODO: find out the right interaction with Sandeep
 */
public class AddressSpaceManagerImpl implements
                 IAddressSpaceManagerService,
                 IHAListener,
                 IModule,
                 IStorageSourceListener,
                 IEntityClassifierService {


    // Table names
    public static final String ADDRESS_SPACE_TABLE_NAME =
                                  "controller_addressspace";
    public static final String ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME =
                                  "controller_addressspaceidentifierrule";

    // Column names
    public static final String NAME_COLUMN_NAME          = "name";
    public static final String ACTIVE_COLUMN_NAME        = "active";
    public static final String PRIORITY_COLUMN_NAME      = "priority";
    public static final String SEPARATOR_COLUMN_NAME = "vlan_tag_on_egress";

    public static final String ID_COLUMN_NAME = "name";
    public static final String ADDRESS_SPACE_COLUMN_NAME =
                                      "address_space_id";
    public static final String DESCRIPTION_COLUMN_NAME   = "description";
    public static final String RULE_COLUMN_NAME   = "rule";
    public static final String MAC_COLUMN_NAME           = "mac";
    public static final String SWITCH_COLUMN_NAME        = "switch";
    public static final String PORTS_COLUMN_NAME         = "ports";
    public static final String VLANS_COLUMN_NAME         = "vlans";
    public static final String TAGS_COLUMN_NAME          = "tag";

    protected static final int UPDATE_TASK_BATCH_DELAY_MS = 750;

    protected static Logger logger =
                         LoggerFactory.getLogger(AddressSpaceManagerImpl.class);

    /**
     * This is the set of address spaces that exist in the system, mapping
     * ID to BetterEntityClass object
     */
    protected Map<String, BetterEntityClass> addressSpaceMap;

    /**
     * This is the set of Address Space identifier rules that exist in the
     * system, mapping name to address space identifier rule object
     */
    protected Map<String,MembershipRule<BetterEntityClass>> identifierRuleMap;

    /**
     * Address Space manager event listeners
     */
    protected Set<IEntityClassListener> entityClassListeners;

    /**
     * Asynchronous task for responding to address space configuration changes
     * notifications.
     */
    SingletonTask configUpdateTask;

    protected IStorageSourceService storageSource;
    protected IRestApiService restApi;
    protected IThreadPoolService threadPool;
    protected IControllerService controllerProvider;
    protected ITagManagerService tagManager;
    protected ITopologyService topology;

    protected ReentrantReadWriteLock configLock;
    /**
     * This list maps vlans to addressSpaces. We use it for fast address space
     * lookups on internal links
     */
    protected ArrayList<BetterEntityClass> entityClasses;
    protected DeviceGroupMatcher<BetterEntityClass> deviceGroupMatcher;
    protected static final EnumSet<DeviceField> keyFields;
    protected BetterEntityClass defaultEntityClass;
    protected boolean addressSpaceGlobalActiveState;
    protected boolean enableNetworkService;
    static {
        keyFields = EnumSet.of(DeviceField.SWITCH,
                               DeviceField.PORT,
                               DeviceField.VLAN,
                               DeviceField.MAC);
    }

    protected Map<String, BetterEntityClass> getAddressSpaceMap () {
        return addressSpaceMap;
    }

    protected Map<String,MembershipRule<BetterEntityClass>>
                  getIdentifierRuleMap () {
        return identifierRuleMap;
    }

    public String getName () {
        return "addressSpaceManager";
    }

    // **********************
    // IStorageSourceListener
    // **********************

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        queueConfigUpdate();
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        queueConfigUpdate();
    }

    /**
     * Queue a task to update the configuration state
     */
    protected void queueConfigUpdate() {
        configUpdateTask.reschedule(UPDATE_TASK_BATCH_DELAY_MS,
                                    TimeUnit.MILLISECONDS);
    }

    // ********************
    // Service Dependencies
    // ********************

    /**
     * A shim IDevice implementations that just wraps a single entity
     * We pass instances of of this class to
     * {@link DeviceGroupMatcher#matchDevice(IDevice)}
     *
     * TODO: alternatively, we could add a DeviceGroupMatcher.matchEntity()
     */
    protected class FakeDevice implements IDevice {
        protected Entity entity;
        String macAddressString;
        public FakeDevice(Entity entity) {
            this.entity = entity;
        }
        @Override
        public Long getDeviceKey() {
            return null;
        }
        @Override
        public long getMACAddress() {
            return entity.getMacAddress();
        }
        @Override
        public String getMACAddressString() {
            if (macAddressString == null) {
                macAddressString =
                    HexString.toHexString(entity.getMacAddress(), 6);
            }
            return macAddressString;
        }
        @Override
        public Short[] getVlanId() {
            if (entity.getVlan() != null) {
                return new Short[]{ entity.getVlan() };
            } else {
                return new Short[] { Short.valueOf((short)-1) };
            }
        }
        @Override
        public Integer[] getIPv4Addresses() {
            // no support for IP based rules
            throw new UnsupportedOperationException();
        }
        @Override
        public SwitchPort[] getAttachmentPoints() {
            if (entity.getSwitchDPID() != null &&
                entity.getSwitchPort() != null) {
                return new SwitchPort[] {
                          new SwitchPort(entity.getSwitchDPID().longValue(),
                                         entity.getSwitchPort().shortValue())
                        };
            }
            return new SwitchPort[0];
        }
        @Override
        public SwitchPort[] getAttachmentPoints(boolean includeError) {
            return getAttachmentPoints();
        }
        @Override
        public Date getLastSeen() {
            throw new UnsupportedOperationException();
        }
        @Override
        public IEntityClass getEntityClass() {
            throw new UnsupportedOperationException();
        }
        @Override
        public Short[] getSwitchPortVlanIds(SwitchPort swp) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Match entity against address space membership rules and return
     * the appropriate BetterEntityClass. This class matches entities at the
     * edge of an OF domain.
     * Call only while holding configLock!
     * @param entity the entity to match
     * @return entityClass or null
     */
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Failed to assign an address space {device} "
                        + "{exception}",
                explanation="Could not assign an address-space to the device.",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })
    protected BetterEntityClass doMatchEntity(Entity entity) {
        // An attachment point port. Match entity against
        // address space membership rules
        FakeDevice d = new FakeDevice(entity);
        List<MembershipRule<BetterEntityClass>> rules;
        try {
            rules = deviceGroupMatcher.matchDevice(d);
            if (rules == null) {
                if (entity.getVlan() == null) {
                    // no rules matched && untagged:
                    // default entity class
                    return defaultEntityClass;
                } else if (entityClasses.get(entity.getVlan()) == null) {
                    // no rules && tagged && no address space associated
                    // with this vlan: default entity class
                    return defaultEntityClass;
                } else {
                    // no rules && tagged && there is an address space
                    // associated with the vlan: don't allow
                    return null;
                }
            } else {
                // we matched rules. We don't allow "allow multiple"
                // so we just look at the first rule
                return rules.get(0).getParentDeviceGroup();
            }
        } catch (Exception e) {
            logger.error("Failed to assign an address space " + d, e);
            return null;
        }
    }

    /**
     * Drop all state. Used for going to SLAVE mode
     */
    protected void clearConfigState() {
        configLock.writeLock().lock();
        try {
            deviceGroupMatcher.clear();
            addressSpaceMap.clear();
            identifierRuleMap.clear();
            entityClasses.clear();
            addressSpaceGlobalActiveState = false;
        } finally {
            configLock.writeLock().unlock();
        }
    }

    private boolean hasAddressSpaceOptionChanged (String oldS, String newS,
                                            boolean ignoreCase) {
        if ((oldS == null) && (newS == null)) {
            return false;
        }

        if ((oldS == null) && (newS != null)) {
            return true;
        }

        if ((oldS != null) && (newS == null)) {
            return true;
        }
        if ((ignoreCase) && (!oldS.equalsIgnoreCase(newS))) {
            return true;
        }
        if ((!ignoreCase) && (!oldS.equals(newS))) {
            return true;
        }
        return false;
    }

    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Unsupported mac address based match ignored",
                explanation="An address-space configuration in the data-base "
                           +"uses unsupported MAC based matches",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="WARN",
                message="Vlan tag value {vlan}" +
                        " does not match vlan-tag-on-egress value {vlan}. " +
                        " Entry ignored.",
                explanation="Address-space misconfiguration. An address-space" +
                        "uses a different vlan for vlan-tag-on-egress than" +
                        "it uses to match devices.",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="WARN",
                message="Error loading address space {address-space}" +
                        " rule {rule-name} from storage, entry ignored." +
                        " {exception}",
                explanation="Could not read the mentioned address-spaces" +
                        "identifier-rule from storage.",
                        recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    private void readAddressSpaceIdentifierRuleConfig (
                    IResultSet              iruleResultSet,
                    HashSet<BetterEntityClass> addressSpaceWithRuleChangesSet)
    {
        String addressSpaceName = iruleResultSet.getString(
                                        ADDRESS_SPACE_COLUMN_NAME);

        if (addressSpaceName == null)
            return;
        BetterEntityClass addressSpace = addressSpaceMap.get(addressSpaceName);

        if (addressSpace == null || addressSpace == defaultEntityClass)
            return;

        String identifierName = iruleResultSet.getString(NAME_COLUMN_NAME);
        MembershipRule<BetterEntityClass> idRule =
            identifierRuleMap.get(identifierName);

        if (idRule == null) {
            idRule = new MembershipRule<BetterEntityClass>(identifierName,
                                                         addressSpace);
            addressSpaceWithRuleChangesSet.add(addressSpace);
            logger.debug("New address space interface rule added: {}", idRule);
        }

        try {

            /* For each of the possible address-space identifier  rule
             * match options, we compare the match field with the old
             * value, and if the new value is differnet, then we
             * add the addressSpace to addressSpaceWithRuleChangesSet
             */
            idRule.setDescription(
                iruleResultSet.getString(DESCRIPTION_COLUMN_NAME));
            idRule.setRuleName(
                iruleResultSet.getString(RULE_COLUMN_NAME));

            boolean oldState = idRule.isActive();
            idRule.setActive(iruleResultSet.getBoolean(ACTIVE_COLUMN_NAME));

            if (oldState != idRule.isActive()) {

                /*
                 * Active status of rule has changed
                 */
                addressSpaceWithRuleChangesSet.add(
                    idRule.getParentDeviceGroup());
            }

            int oldPriority = idRule.getPriority();
            idRule.setPriority(iruleResultSet.getInt(PRIORITY_COLUMN_NAME));
            if (oldPriority != idRule.getPriority()) {

                /*
                 * Priority of rule has changed
                 */
                addressSpaceWithRuleChangesSet.add(
                    idRule.getParentDeviceGroup());
            }

            String oldSwitchId = idRule.getSwitchId();
            idRule.setSwitchId(iruleResultSet.getString(SWITCH_COLUMN_NAME));
            if (hasAddressSpaceOptionChanged(oldSwitchId, idRule.getSwitchId(),
                                       true)) {
                /*
                 * Switch ID match rule has changed
                 */
                addressSpaceWithRuleChangesSet.add(
                    idRule.getParentDeviceGroup());
            }

            String oldPorts = idRule.getPorts();
            idRule.setPorts(iruleResultSet.getString(PORTS_COLUMN_NAME));
            if (hasAddressSpaceOptionChanged(oldPorts,
                                             idRule.getPorts(), false)) {

                /*
                 * Ports match rule has changed
                 */
                addressSpaceWithRuleChangesSet.add(
                    idRule.getParentDeviceGroup());
            }

            String oldTags = idRule.getTags();
            idRule.setTags(iruleResultSet.getString(TAGS_COLUMN_NAME));
            if (hasAddressSpaceOptionChanged(oldTags,
                                             idRule.getTags(), false)) {

                /*
                 * Tags match rule has changed
                 */
                addressSpaceWithRuleChangesSet.add(
                    idRule.getParentDeviceGroup());
            }

            String oldMac = idRule.getMac();
            String newMac = iruleResultSet.getString(MAC_COLUMN_NAME);

            if (newMac != null) {
                logger.warn("Unsupported mac address based match ignored");
            } else {
                idRule.setMac(newMac);
                if (hasAddressSpaceOptionChanged(oldMac, newMac, true)) {

                    /*
                    * MAC match of rule has changed
                    */
                    addressSpaceWithRuleChangesSet.add(
                        idRule.getParentDeviceGroup());
                }
            }

            String oldVlans = idRule.getVlans();
            String newVlans = iruleResultSet.getString(VLANS_COLUMN_NAME);

            if (newVlans != null &&
                !(new Short(newVlans)).equals(
                    idRule.getParentDeviceGroup().getVlan())) {
                logger.warn("Vlan tag value " + newVlans +
                            " does not match vlan-tag-on-egress value " +
                            idRule.getParentDeviceGroup().getVlan() +
                            ". Entry ignored");
            } else {

                idRule.setVlans(newVlans);
                if (hasAddressSpaceOptionChanged(oldVlans, newVlans, false)) {

                    /*
                     * VLAN match rule has changed
                    */
                    addressSpaceWithRuleChangesSet.add(
                        idRule.getParentDeviceGroup());
                }
            }
        } catch (Exception e) {
            logger.warn("Error loading address space " + addressSpaceName +
                        " rule " + identifierName +
                        " from storage, entry ignored. " + e);
            return;
        }

        idRule.setMarked(true);

        identifierRuleMap.put(idRule.getName(), idRule);

        /*
         * XXX NetVirt pre-creates interfaces and sub-interfaces upon configuration
         * read. Check if we need to do some thing similar, when we read
         * address space configuration as well.
         */
        // createNetVirtInterfaces(idRule);

        logger.debug("Configured address space identifier Rule " + idRule);

        return;
    }

    private int processAddressSpaceVlanTagOnEgressConfig (
                    BetterEntityClass addressSpace,
                    IResultSet     addressSpaceResultSet,
                    int            maxPriInModifiedSet) {
        Short oldVlan = addressSpace.getVlan();
        Short newVlan = null;

        if (addressSpaceResultSet.containsColumn(SEPARATOR_COLUMN_NAME)) {
            newVlan = addressSpaceResultSet.getShort(SEPARATOR_COLUMN_NAME);
        }
        addressSpace.setVlan(newVlan);

        if (oldVlan == null && newVlan == null) {
            return maxPriInModifiedSet;
        }

        if (oldVlan != null && newVlan != null) {
            if (oldVlan.equals(newVlan)) {
                return maxPriInModifiedSet;
            }
        }

        if (maxPriInModifiedSet < addressSpace.getPriority()) {
            maxPriInModifiedSet = addressSpace.getPriority();
        }

        if (newVlan != null) {
            if (entityClasses.get(newVlan) != null) {
                // Another address-space is already using this vlan.
                throw new StorageException("Vlan " + newVlan.toString() +
                            " is already in use by another address-space " +
                            entityClasses.get(newVlan).getName());
            }
        }

        return maxPriInModifiedSet;
    }

    @LogMessageDocs({
        @LogMessageDoc(level="WARN",
                message="Configuration of default address space {default-name}"
                        + " is not allowed",
                explanation="Address-space \"default\" is configured in the " +
                        "database but configuration of it is not allowed",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="WARN",
                message="Error loading address space {address-space}" +
                        " from storage, entry ignored {exception}",
                explanation="Could not read the mentioned address-spaces" +
                        "configuration from storage.",
                        recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    private int readAddressSpaceDefinitionConfig (
                     IResultSet addressSpaceResultSet,
                     int        maxPriInModifiedSet) {

        String addressSpaceName =
                    addressSpaceResultSet.getString(NAME_COLUMN_NAME);
        if (addressSpaceName.equals(DEFAULT_ADDRESS_SPACE_NAME)) {
            // Do not allow configuration of the default address space
            logger.warn("Configuration of default address space {} "
                    + "is not allowed",
                     DEFAULT_ADDRESS_SPACE_NAME);
            return maxPriInModifiedSet;
        }

       // check if address space is active
        if (!addressSpaceResultSet.getBoolean(ACTIVE_COLUMN_NAME)) {
            if (logger.isDebugEnabled()) {
                  logger.debug("Skipping inactive address-space {}",
                               addressSpaceName);
            }
            return maxPriInModifiedSet;
        }
       // skip if address space does not have a transport /egress vlan
        if (!addressSpaceResultSet.containsColumn(SEPARATOR_COLUMN_NAME)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping address-space {} with vlan-tag-on-egress",
                             addressSpaceName);
            }
            return maxPriInModifiedSet;
        }
        BetterEntityClass addressSpace = addressSpaceMap.get(addressSpaceName);

        if (addressSpace == null) {
            /* New BetterEntityClass was created */
            addressSpace = new BetterEntityClass(addressSpaceName, null);

            if (maxPriInModifiedSet < addressSpace.getPriority()) {
                maxPriInModifiedSet = addressSpace.getPriority();
            }
        }

        try {
            int oldPriority = addressSpace.getPriority();

            addressSpace.setPriority(
                addressSpaceResultSet.getInt(PRIORITY_COLUMN_NAME));

            if (oldPriority != addressSpace.getPriority()) {

                if (maxPriInModifiedSet < oldPriority) {
                    maxPriInModifiedSet = oldPriority;
                }

                if (maxPriInModifiedSet < addressSpace.getPriority()) {
                    maxPriInModifiedSet = addressSpace.getPriority();
                }
            }

            boolean oldState = addressSpace.isActive();
            addressSpace.setActive(
                addressSpaceResultSet.getBoolean(ACTIVE_COLUMN_NAME));

            if (oldState != addressSpace.isActive()) {

                /*
                 * Active status of address-space has changed
                 */
                if (maxPriInModifiedSet < addressSpace.getPriority()) {
                    maxPriInModifiedSet = addressSpace.getPriority();
                }
            }
            maxPriInModifiedSet =
                processAddressSpaceVlanTagOnEgressConfig(addressSpace,
                                                   addressSpaceResultSet,
                                                   maxPriInModifiedSet);

            addressSpace.setDescription(
                addressSpaceResultSet.getString(DESCRIPTION_COLUMN_NAME));
        } catch (Exception e) {
            logger.warn("Error loading address space " +
                        addressSpaceName +
                        " from storage, entry ignored. " + e);
            return maxPriInModifiedSet;
        }

        // Store address space in class wide structures
        addressSpace.setMarked(true);
        addressSpaceMap.put(addressSpace.getName(), addressSpace);
        if (addressSpace.getVlan() == null) {
            entityClasses.set(0, addressSpace);
        } else {
            entityClasses.set(addressSpace.getVlan(), addressSpace);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("address space {} ({}): Reading AS definition complete ",
                         addressSpace.getName(), addressSpace.getVlan());
        }
        return maxPriInModifiedSet;
    }

    private void findAndUpdateModifiedAddressSpaces (
        HashSet<BetterEntityClass> addressSpaceDeletesSet,
        HashSet<BetterEntityClass> addressSpaceWithRuleChangesSet,
        int                     maxPriInModifiedSet) {

        /*
         * FIXME: these comments are copied mostly verbatim from NetVirtManagerImpl
         * and don't make a lot of sense for address space manager....
         *
         * Certain clients such as deviceManager need to be informed about
         * all the address spaces that are affected. Eventually, all entities
         * in all the affected address-spaces must be reclassified as the
         * new configuration can render existing entities to be mapped to
         * different entityClasses.
         *
         * DELETED address spaces
         * =============
         * For the flows in address spaces that were deleted either those flows need to
         * be deleted or they need to be moved to some other address space if the devices
         * were member of multiple address spaces. If the flow need to be moved to some
         * other address space then the flow-mods in the switches need not be touched buy
         * the flow-cache should be updated so that these flows are moved to
         * other application instance name. Here we need to submit flow query
         * for all the address spaces that were deleted.
         * <p>
         * ADDED address spaces
         * ===========
         * For the new address spaces that were created we need to check if any flow
         * from a lower priority address space need to be migrated to a newly created
         * address space. To accomplish this we need to submit flow query for all the
         * existing address spaces that are in lower priority than the highest-priority
         * addressSpace that was created.
         *
         * We will use the logic stated above to create a set of address spaces for which
         * flow queries need to be submitted for reconciliation.
         */
        HashSet<String> addressSpacesChanged = new HashSet<String>();

        /*
         * Find the address-space with highest priority in id rule changed
         */
        int maxPriInChangedSet = 0;
        for (BetterEntityClass addressSpace :
                 addressSpaceWithRuleChangesSet) {
            if (addressSpace.getPriority() > maxPriInChangedSet) {
                maxPriInChangedSet = addressSpace.getPriority();
            }
        }
        /* Get higher priority of the two */
        int maxPriInAddedAndChanged = Math.max(maxPriInModifiedSet,
                                               maxPriInChangedSet);

        /*
         * Now add all the address spaces that were deleted as we need to
         * reconcile flows in those address spaces
         */
        for (BetterEntityClass addressSpace : addressSpaceDeletesSet) {
            addressSpacesChanged.add(addressSpace.getName());
            if (addressSpace.getPriority() > maxPriInAddedAndChanged) {
                maxPriInAddedAndChanged = addressSpace.getPriority();
            }
        }

        /*
         * Get all address spaces with priority less than or equal to
         * maxPriInAddedAndChanged
         */
        for (String addSpaceName : addressSpaceMap.keySet() ) {
            BetterEntityClass addressSpace = addressSpaceMap.get(addSpaceName);
            if (addressSpace.getPriority() <= maxPriInAddedAndChanged) {
                addressSpacesChanged.add(addSpaceName);
            }
        }

        /*
         * Notify interested clients, address-spaces that need
         * re-classification.
         */
        notifyAddressSpaceConfigurationChanged(addressSpacesChanged);

        return;
    }

    /*
     * IEntityClassifierService
     */
    @Override
    public void addListener (IEntityClassListener listener) {
        entityClassListeners.add(listener);
    }

    private void notifyAddressSpaceConfigurationChanged (
                     HashSet<String> entityClassNames) {

        /*
         * If the list is empty, then don't bother.
         */
        if (entityClassNames.isEmpty()) return;

        /*
         * Now submit flow query to get the flows in each of these address
         * spaces.
         */
        if (logger.isTraceEnabled()) {
            logger.trace("Set of address spaces to query for flow " +
                         "reconciliation: {}", entityClassNames);
        }
        for (IEntityClassListener listener : entityClassListeners) {
            listener.entityClassChanged(entityClassNames);
        }
    }

    /**
     * Read the AddressSpace configuration information from storage, merging
     * with the existing configuration.
     */
    protected void readAddressSpaceConfigFromStorage() throws StorageException {
        IResultSet addressSpaceResultSet = storageSource.executeQuery(
                                                  ADDRESS_SPACE_TABLE_NAME,
                                                  null, null, null);
        IResultSet iruleResultSet = storageSource.executeQuery(
            ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME,
            new String[] {
                NAME_COLUMN_NAME,
                ADDRESS_SPACE_COLUMN_NAME,
                SEPARATOR_COLUMN_NAME,
                DESCRIPTION_COLUMN_NAME,
                RULE_COLUMN_NAME,
                ACTIVE_COLUMN_NAME,
                PRIORITY_COLUMN_NAME,
                MAC_COLUMN_NAME,
                SWITCH_COLUMN_NAME,
                PORTS_COLUMN_NAME,
                VLANS_COLUMN_NAME,
                TAGS_COLUMN_NAME
            },
            null, null);

        /*
         * We will maintain a set of address spaces that were deleted and a set
         * new address spaces that were created so that we can reconcile flows
         * in a subset of address spaces
         */
        HashSet<BetterEntityClass> addressSpaceDeletesSet =
                                    new HashSet<BetterEntityClass>();
        HashSet<BetterEntityClass> addressSpaceWithRuleChangesSet =
                                    new HashSet<BetterEntityClass>();
        int maxPriInModifiedSet = 0;

        configLock.writeLock().lock();
        try {
            addressSpaceMap.put(DEFAULT_ADDRESS_SPACE_NAME, defaultEntityClass);
            // Clear out the vlan to address space array
            entityClasses.clear();
            for (int i = 0; i < 4096; i++) {
                entityClasses.add(null);
            }

            addressSpaceGlobalActiveState = true;


            // Flush rule-matching data structures
            deviceGroupMatcher.clear();


            // Read in all the BetterEntityClass
            for (BetterEntityClass addressSpace : addressSpaceMap.values()) {
                if (addressSpace == defaultEntityClass)
                    continue;
                addressSpace.setMarked(false);
            }

            while (addressSpaceResultSet.next()) {
                maxPriInModifiedSet = readAddressSpaceDefinitionConfig(
                                          addressSpaceResultSet,
                                          maxPriInModifiedSet);
            }

            // clear out state related to address-spaces that no longer exist
            Iterator<Entry<String, BetterEntityClass>> addressSpaceMapIter =
                                          addressSpaceMap.entrySet().iterator();
            while (addressSpaceMapIter.hasNext()) {
                BetterEntityClass addressSpace =
                                   addressSpaceMapIter.next().getValue();
                if (addressSpace == defaultEntityClass)
                    continue; // don't delete default address space
                if (addressSpace.isMarked() == false) {
                    /* This address space was deleted */
                    addressSpaceDeletesSet.add(addressSpace);
                    addressSpaceMapIter.remove();
                }
            }

            // Read in all the address-space identifier rules
            for (MembershipRule<BetterEntityClass> idRule :
                                                identifierRuleMap.values()) {
                idRule.setMarked(false);
            }

            while (iruleResultSet.next()) {
                readAddressSpaceIdentifierRuleConfig(iruleResultSet,
                    addressSpaceWithRuleChangesSet);
            }

            /*
             * clear out old rules state no longer exist and set up lookup data
             * structures
             */
            Iterator<Entry<String, MembershipRule<BetterEntityClass>>>
                interfaceRuleMapIter = identifierRuleMap.entrySet().iterator();
            while (interfaceRuleMapIter.hasNext()) {
                MembershipRule<BetterEntityClass> idRule =
                    interfaceRuleMapIter.next().getValue();
                if (idRule.isMarked() == false) {
                    interfaceRuleMapIter.remove();
                    addressSpaceWithRuleChangesSet.add(
                        idRule.getParentDeviceGroup());
                    continue;
                }
                deviceGroupMatcher.addRuleIfActive(idRule);
            }

        } finally {
            configLock.writeLock().unlock();
        }

        findAndUpdateModifiedAddressSpaces(
            addressSpaceDeletesSet, addressSpaceWithRuleChangesSet,
            maxPriInModifiedSet);

        return;
    }

    /**
     * @see org.sdnplatform.devicemanager.IEntityClassifierService#classifyEntity(org.sdnplatform.devicemanager.internal.Entity)
     */
    @Override
    public IEntityClass classifyEntity(Entity entity) {
        Long switchDpid = entity.getSwitchDPID();
        Integer port = entity.getSwitchPort();
        BetterEntityClass entityClass = null;
        short vlan = (entity.getVlan()!=null) ?
                entity.getVlan().shortValue() : 0;
        configLock.readLock().lock();
        try {
            if (!addressSpaceGlobalActiveState) {
                return null;
            }

            /*
             * If this is an internal port, we simply use the entities VLAN tag
             * to identify the address space.
             */
            if (switchDpid!= null && port!=null &&
                    !topology.isAttachmentPointPort(switchDpid,
                                                   port.shortValue())) {
                entityClass = entityClasses.get(vlan);
                if (entityClass == null)
                    entityClass = defaultEntityClass;
                if (logger.isTraceEnabled()) {
                    logger.trace("Internal port. Entity={} entityClass={}",
                                 entity, entityClass);
                }
            }
            else {
                entityClass = doMatchEntity(entity);
                if (logger.isTraceEnabled()) {
                    logger.trace("External port. Entity={} entityClass={}",
                                 entity, entityClass);
                }
            }
        } finally {
            configLock.readLock().unlock();
        }
        return entityClass;
    }

    /**
     * @see org.sdnplatform.devicemanager.IEntityClassifierService#getKeyFields()
     */
    @Override
    public final EnumSet<DeviceField> getKeyFields() {

        /*
         * Make sure nobody can accidentally change our keyFiels by returning
         * a clone since there's no ImmutableEnumSet :-(
         */
        return keyFields.clone();
    }

    /**
     * @see org.sdnplatform.devicemanager.IEntityClassifierService#reclassifyEntity(org.sdnplatform.devicemanager.IDevice, org.sdnplatform.devicemanager.internal.Entity)
     */
    @Override
    public IEntityClass reclassifyEntity(IDevice curDevice, Entity entity) {
        return classifyEntity(entity);
    }


    @Override
    public Short getSwitchPortVlanMode(SwitchPort swp, String addressSpaceName,
                                       Short currentVlan,
                                       boolean tunnelEnabled) {
        // TODO:
        // This is a temporary work around to support networkservice with addressspace.
        // Once duplicate ip is properly support, this should be removed.
        if (enableNetworkService) {
            return Short.valueOf(Ethernet.VLAN_UNTAGGED);
        }

        // TODO: we really should cache these lookups. But then we need
        // to listen and react to Tag changes. Sigh.
        if (addressSpaceName == null)
            throw new NullPointerException("address-space cannot be null");
        if (swp == null)
            throw new NullPointerException("swp cannot be null");
        if (currentVlan == null)
            throw new NullPointerException("currentVlan cannot be null");

        configLock.readLock().lock();
        try {
            BetterEntityClass sourceAS = addressSpaceMap.get(addressSpaceName);
            if (sourceAS == null)
                return null;
            if (!topology.isAttachmentPointPort(swp.getSwitchDPID(),
                                                (short)swp.getPort(),
                                                tunnelEnabled)) {
                // internal link. packet is always allowed. we use the VLAN
                // of the source address-space or the current vlan if it's the
                // default address-space.
                if (sourceAS == defaultEntityClass)
                    return currentVlan;
                else
                    return sourceAS.getVlan();
            }

            /* need to special case default address space. Only check with
             * the VLAN the packet currently has.
             */
            if (sourceAS == defaultEntityClass) {
                Entity e;
                if (currentVlan.equals(Ethernet.VLAN_UNTAGGED)) {
                    e = new Entity(0L, null, null,
                                   swp.getSwitchDPID(), swp.getPort(), null);
                } else {
                    e = new Entity(0L, currentVlan, null,
                                   swp.getSwitchDPID(), swp.getPort(), null);
                }
                BetterEntityClass bec = doMatchEntity(e);
                if (sourceAS.equals(bec))
                    return currentVlan;
                else return null;
            }


            // Check if the VLAN is native
            // Query for the switch-port without the vlan.
            Entity e = new Entity(0L, null, null,
                                  swp.getSwitchDPID(), swp.getPort(), null);
            BetterEntityClass bec = doMatchEntity(e);
            if (sourceAS.equals(bec)) {
                return Short.valueOf(Ethernet.VLAN_UNTAGGED);
            }

            // address-space is not native. does it have a vlan?
            if (sourceAS.getVlan() == null)
                return null;

            // address-space is not native. It has a vlan. check if it's allowed
            // tagged
            e = new Entity(0L, sourceAS.getVlan(), null,
                           swp.getSwitchDPID(), swp.getPort(), null);
            bec = doMatchEntity(e);
            if (bec != null  && sourceAS.equals(bec)) {
                return sourceAS.getVlan();
            }
        }
        finally {
            configLock.readLock().unlock();
        }
        return null;
    }


    @Override
    public IEntityClass getEntityClassByName(String addressSpaceName) {
        configLock.readLock().lock();
        try {
            return addressSpaceMap.get(addressSpaceName);
        } finally {
            configLock.readLock().unlock();
        }
    }


    /**
     * @see org.sdnplatform.devicemanager.IEntityClassifierService#deviceUpdate(org.sdnplatform.devicemanager.IDevice, java.util.Collection)
     */
    @Override
    public void deviceUpdate(IDevice oldDevice,
                             Collection<? extends IDevice> newDevices) {
        // TODO
        throw(new UnsupportedOperationException("Not implemented"));
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IAddressSpaceManagerService.class);
        l.add(IEntityClassifierService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService> getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m =
        new HashMap<Class<? extends IPlatformService>,
                    IPlatformService>();
        // We are the class that implements the service
        m.put(IAddressSpaceManagerService.class, this);
        m.put(IEntityClassifierService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l =
                new ArrayList<Class<? extends IPlatformService>>();
        // TODO!!
        l.add(IControllerService.class);
        //l.add(IDeviceService.class);
        // TODO: Those will come later
        l.add(IStorageSourceService.class);
        l.add(ITagManagerService.class);
        l.add(ITopologyService.class);
        //l.add(IRestApiService.class);
        //l.add(IFlowReconcileService.class);
        //l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {

        controllerProvider =
                context.getServiceImpl(IControllerService.class);
        tagManager = context.getServiceImpl(ITagManagerService.class);
        topology = context.getServiceImpl(ITopologyService.class);
        storageSource = context.getServiceImpl(IStorageSourceService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        entityClassListeners = new HashSet<IEntityClassListener>();
        addressSpaceMap = new ConcurrentHashMap<String, BetterEntityClass>();
        identifierRuleMap = new HashMap<String,
                                        MembershipRule<BetterEntityClass>>();

        defaultEntityClass = new BetterEntityClass();
        defaultEntityClass.setActive(true);
        defaultEntityClass.setPriority(Integer.MIN_VALUE);

        configLock = new ReentrantReadWriteLock();

        entityClasses = new ArrayList<BetterEntityClass>(4096);

        // we reference tagManager here but the constructor won't call any
        // of its methods so we are ok.
        deviceGroupMatcher = new DeviceGroupMatcher<BetterEntityClass>(tagManager,
                                 controllerProvider);

        String nsProp = System.getProperty("org.sdnplatform.addressspace.EnableNetworkService");
        if (nsProp != null) {
            if (Integer.parseInt(nsProp) != 0) {
                enableNetworkService = true;
            }
        }
    }

    @Override
    public void startUp(ModuleContext context) {

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        configUpdateTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                readAddressSpaceConfigFromStorage();
            }
        });

        storageSource.createTable(ADDRESS_SPACE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(ADDRESS_SPACE_TABLE_NAME,
                                             NAME_COLUMN_NAME);
        storageSource.createTable(ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME,
                                  null);
        storageSource.setTablePrimaryKeyName(
            ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME, NAME_COLUMN_NAME);

        storageSource.addListener(ADDRESS_SPACE_TABLE_NAME, this);
        storageSource.addListener(ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME,
                                  this);

        readAddressSpaceConfigFromStorage();

        controllerProvider.addHAListener(this);
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="New role {role} is currently not supported",
                explanation="The address-space logic received a notification "
                           + "that the controller changed to a new role that"
                           + "is currently not supported",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })

    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    readAddressSpaceConfigFromStorage();
                }
                break;
            case SLAVE:
                logger.debug("Clearing config state due to HA " +
                               "role change to SLAVE");
                clearConfigState();
                break;
            default:
                logger.error("New role {} is currently not supported", newRole);
                break;
        }
    }

    @Override
    public void
            controllerNodeIPsChanged(
                Map<String, String> curControllerNodeIPs,
                Map<String, String> addedControllerNodeIPs,
                Map<String, String> removedControllerNodeIPs) {
        // ignore
    }
}
