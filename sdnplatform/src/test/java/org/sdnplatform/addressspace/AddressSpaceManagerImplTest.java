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

package org.sdnplatform.addressspace;

import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.util.HexString;
import org.sdnplatform.addressspace.AddressSpaceManagerImpl;
import org.sdnplatform.addressspace.BetterEntityClass;
import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicegroup.MembershipRule;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClassListener;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.devicemanager.IDeviceService.DeviceField;
import org.sdnplatform.devicemanager.internal.Entity;
import org.sdnplatform.devicemanager.test.MockDeviceManager;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@SuppressWarnings("unchecked")
public class AddressSpaceManagerImplTest extends PlatformTestCase
implements IEntityClassListener {
    
    private MockDeviceManager mockDeviceManager;
    private ModuleContext fmc;
    private AddressSpaceManagerImpl addressSpaceManager;
    private IFlowReconcileService mockFlowReconciler;
    private MemoryStorageSource storageSource; 
    
    // entities with MAC 1
    protected static Entity entityNoVlan_1;
    static { entityNoVlan_1 = new Entity(1L, null, 1, 1L, 1, new Date()); }
    protected static Entity entityVlan42_1;
    static { entityVlan42_1 = new Entity(1L, (short)42, 1, 1L, 1, new Date()); }
    protected static Entity entityVlan1_1;
    static { entityVlan1_1 = new Entity(1L, (short)1, 1, 1L, 1, new Date()); }
    protected static Entity entityVlan23_1;
    static { entityVlan23_1 = new Entity(1L, (short)23, 1, 1L, 1, new Date()); }
    
    // entities with MAC 2
    protected static Entity entityNoVlan_2;
    static { entityNoVlan_2 = new Entity(2L, null, 2, 1L, 1, new Date()); }
    protected static Entity entityVlan42_2;
    static { entityVlan42_2 = new Entity(2L, (short)42, 2, 1L, 1, new Date()); }
    protected static Entity entityVlan1_2;
    static { entityVlan1_2 = new Entity(2L, (short)1, 2, 1L, 1, new Date()); }
    protected static Entity entityVlan23_2;
    static { entityVlan23_2 = new Entity(2L, (short)23, 2, 1L, 1, new Date()); }
    
    // entities for address space SwitchId10
    protected static Entity entitySw10_noVlan;
    protected static Entity entitySw10_Vlan100;
    protected static Entity entityVlan1001;
    static {  
        entitySw10_noVlan = new Entity(10L, null, 10, 10L, 1, new Date()); 
        entitySw10_Vlan100 = new Entity(10L, (short)100, 10, 10L, 1, new Date()); 
        // entity on core switch, has vlan but different switch
        entityVlan1001 = new Entity(10L, (short)1001, 10, 42L, 1, new Date()); 
    }
    
    // entities for address space MAC 255
    protected static Entity entityMac255_noVlan;
    protected static Entity entityMac255_Vlan100;
    protected static Entity entityVlan1002;
    static {  
        entityMac255_noVlan = new Entity(255L, null, 255, 11L, 1, new Date()); 
        entityMac255_Vlan100 = new Entity(255L, (short)100, 255, 11L, 1, new Date()); 
        // entity on core switch, has vlan but different MAC (different device)
        entityVlan1002 = new Entity(42L, (short)1002, 10, 42L, 1, new Date()); 
    }

    /* 
     * 
     */
    protected AddressSpaceConfigTest doDummyConfig(boolean runTest) {
        AddressSpaceConfigTest confTest = new AddressSpaceConfigTest();
        for (Short vlan: new Short[] { 1,2,3,42 }) {
            AddressSpaceDefinition asp = new AddressSpaceDefinition("ASP" + vlan);
            asp.active = true;
            asp.egress_vlan = vlan;
            AddressSpaceRule asrule = new AddressSpaceRule("ASP" + vlan, 
                                                           "taggedVlan");
            asrule.active = true;
            asrule.vlans = vlan.toString();
            
            expectedChangedAddressSpaces.add("ASP" + vlan);
            confTest.addAddressSpace(storageSource, asp);
            confTest.addAddressSpaceRule(storageSource, asrule);
        }
        
        AddressSpaceDefinition asp = new AddressSpaceDefinition("switchId10");
        asp.active = true;
        asp.egress_vlan = 1001;
        AddressSpaceRule asrule = new AddressSpaceRule("switchId10", "foobar");
        asrule.active = true;
        asrule.setSwitchId(10L);
        confTest.addAddressSpace(storageSource, asp);
        confTest.addAddressSpaceRule(storageSource, asrule);
        expectedChangedAddressSpaces.add("switchId10");
        
        expectedChangedAddressSpaces.add("default");
        if (runTest)
            configTestsRun(confTest);
        return confTest;
    }
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        
        mockDeviceManager = new MockDeviceManager();
        addressSpaceManager = new AddressSpaceManagerImpl();
        RestApiServer ras = new RestApiServer();
        // We currently completely ignore flow reconciliation. 
        mockFlowReconciler = createMock(IFlowReconcileService.class);
        MockThreadPoolService tp = new MockThreadPoolService();
        storageSource = new MemoryStorageSource();
        ITopologyService topology = createMock(ITopologyService.class);
        
        
        fmc = new ModuleContext();
        fmc.addService(IControllerService.class, mockControllerProvider);
        fmc.addService(IDeviceService.class, mockDeviceManager);
        fmc.addService(IAddressSpaceManagerService.class, addressSpaceManager);
        fmc.addService(IEntityClassifierService.class, addressSpaceManager);
        fmc.addService(IRestApiService.class, ras);
        fmc.addService(IFlowReconcileService.class, mockFlowReconciler);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(ITopologyService.class, topology);
        
        topology.addListener(mockDeviceManager);
        expectLastCall().times(1);
        replay(topology);

        // Init 
        storageSource.init(fmc);
        mockDeviceManager.init(fmc);
        addressSpaceManager.init(fmc);
        ras.init(fmc);
        mockControllerProvider.init(fmc);
        // don't init mockFlowReconciler
        tp.init(fmc);

        
        // Startup
        storageSource.startUp(fmc);
        mockDeviceManager.startUp(fmc);
        addressSpaceManager.startUp(fmc);
        ras.startUp(fmc);
        mockControllerProvider.startUp(fmc);
        // don't startUp mockFlowReconciler
        tp.init(fmc);
        
        addressSpaceManager.addListener(this);
        expectedChangedAddressSpaces = new HashSet<String>();
    }
    
    @After
    public void tearDown() {
        AddressSpaceManagerImpl.logger = 
                LoggerFactory.getLogger(AddressSpaceManagerImpl.class);
    }
    
    
    private static class AddressSpaceDefinition {
        public AddressSpaceDefinition(String name) {
            this.name = name;
        }
        String name;
        boolean active;
        int priority;
        String description;
        int egress_vlan;
        
        public Map<String,Object> toMap() {
            HashMap<String,Object> map = new HashMap<String, Object>();
            map.put(AddressSpaceManagerImpl.NAME_COLUMN_NAME, name);
            map.put(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME, active);
            map.put(AddressSpaceManagerImpl.PRIORITY_COLUMN_NAME,
                          priority);
            map.put(AddressSpaceManagerImpl.SEPARATOR_COLUMN_NAME,
                          egress_vlan);
            map.put(AddressSpaceManagerImpl.DESCRIPTION_COLUMN_NAME,
                          description);
            return map;
        }
    }
    
    private static class AddressSpaceRule {
        public AddressSpaceRule(String asName, String name) {
            this.asName = asName; 
            this.name = name;
        }
        public void setSwitchId(Long dpid) {
            switchId = HexString.toHexString(dpid, 8);
        }
        String asName;
        String name;
        String description;
        boolean active;
        int priority;
        String switchId;
        String ports;
        String vlans;
        String tags;
        public Map<String,Object> toMap() {
            HashMap<String,Object> map = new HashMap<String, Object>();
            map.put(AddressSpaceManagerImpl.NAME_COLUMN_NAME,
                            asName + "|" + name);
            map.put(AddressSpaceManagerImpl.RULE_COLUMN_NAME, name);
            map.put(AddressSpaceManagerImpl.ADDRESS_SPACE_COLUMN_NAME, asName);
            map.put(AddressSpaceManagerImpl.DESCRIPTION_COLUMN_NAME, description);
            map.put(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME, active);
            map.put(AddressSpaceManagerImpl.PRIORITY_COLUMN_NAME, priority);
            map.put(AddressSpaceManagerImpl.SWITCH_COLUMN_NAME, switchId);
            map.put(AddressSpaceManagerImpl.PORTS_COLUMN_NAME, ports);
            map.put(AddressSpaceManagerImpl.VLANS_COLUMN_NAME, vlans);
            map.put(AddressSpaceManagerImpl.TAGS_COLUMN_NAME, tags);
            return map;

        }
    }

    /*
     * AddressSpaceConfigTest
     *
     * Subclass to maintain a list of address-space and address-space
     * identifier-rules configuration.
     *
     * Use this to add address-space configurations, delete them, etc.
     * Maintains data structures to record what's been configured.
     *
     * Provides method to verify what's been configured with what is expected
     * to be configured, by comparing the data between local and controller
     * objects.
     */
    private static class AddressSpaceConfigTest {
        public HashMap<String, Map<String, Object>> addressSpaceList;
        public HashMap<String, Map<String, Object>> addressSpaceRuleList;
        public HashMap<Integer,String> vlanToAS;

        public AddressSpaceConfigTest() {
            addressSpaceList = new HashMap<String, Map<String, Object>>();
            addressSpaceRuleList = new HashMap<String, Map<String, Object>>();
            vlanToAS = new HashMap<Integer, String>();
        }

        public HashMap<String, Map<String, Object>> getAddressSpaceList () {
            return addressSpaceList;
        }
        
        /*
         * Add a new address space definition to this config test. 
         */
        public void addAddressSpace(IStorageSourceService storageSource,
                                    AddressSpaceDefinition... asDefs) {
            for (AddressSpaceDefinition asDef: asDefs)
                addAddressSpace(storageSource, asDef.toMap());
        }

        /*
         * Add a new address-space
         */
		public void addAddressSpace(IStorageSourceService storageSource,
                                    Map<String, Object>... addressSpaces) {
            for (Map<String, Object> addressSpace : addressSpaces) {
                String asName = (String)
                        addressSpace.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME);
                assertNotNull(asName);
                addressSpaceList.put(asName, addressSpace);
                Integer vlan = (Integer)
                        addressSpace.get(AddressSpaceManagerImpl.SEPARATOR_COLUMN_NAME);
                if (vlan != null)
                    vlanToAS.put(vlan, asName);

                storageSource.insertRow(
                    AddressSpaceManagerImpl.ADDRESS_SPACE_TABLE_NAME,
                    addressSpace);
            }
        }
        

        /*
         * Add a new address space definition to this config test. 
         */
        public void addAddressSpaceRule(IStorageSourceService storageSource,
                                    AddressSpaceRule... asRules) {
            for (AddressSpaceRule asRule: asRules)
                addAddressSpaceRule(storageSource, asRule.toMap());
        }
        /*
         * Add address-space/identifier-rule
         */
        public void addAddressSpaceRule(IStorageSourceService storageSource,
                                        Map<String, Object>... rules) {
            for (Map<String, Object> rule : rules) {
                assertNotNull(rule.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME));
                addressSpaceRuleList.put(
                (String) rule.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME), rule);
                storageSource.insertRow(
                    AddressSpaceManagerImpl.ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME,
                    rule);
            }
        }

        /*
         * Delete address-space identifier rules
         */
        @SuppressWarnings("unused")
        public void deleteAddressSpaceRule(IStorageSourceService storageSource,
                                           String deleteAsRule) {
            addressSpaceRuleList.remove(deleteAsRule);
            storageSource.deleteRow(
                    AddressSpaceManagerImpl.ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME,
                    deleteAsRule);
        }

        public void deleteAddressSpaceRuleFromAs(
                        IStorageSourceService storageSource,
                        String deleteAs) {

            ArrayList<String> tmpAddressSpaceList = new ArrayList<String>();

            for (String ruleName : addressSpaceRuleList.keySet()) {
                    if (!ruleName.matches("^" + deleteAs + "\\|.*")) continue;
                    storageSource.deleteRow(
                        AddressSpaceManagerImpl.ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME, ruleName);
                    tmpAddressSpaceList.add(ruleName);
            }

            for (String ruleName : tmpAddressSpaceList) {
                addressSpaceRuleList.remove(ruleName);
            }
        }

        public void deleteAddressSpace(IStorageSourceService storageSource,
                                       String deleteAs) {
            if (!addressSpaceList.containsKey(deleteAs)) return;
            deleteAddressSpaceRuleFromAs(storageSource, deleteAs);
            storageSource.deleteRow(
                    AddressSpaceManagerImpl.ADDRESS_SPACE_TABLE_NAME,
                    deleteAs);
            addressSpaceList.remove(deleteAs);
        }

        public void deleteAll(IStorageSourceService storageSource) {
            for (String row : addressSpaceList.keySet()) {
                storageSource.deleteRow(
                    AddressSpaceManagerImpl.ADDRESS_SPACE_TABLE_NAME, row);
            }
            addressSpaceList.clear();
            for (String row : addressSpaceRuleList.keySet()) {
                storageSource.deleteRow(AddressSpaceManagerImpl.ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME, row);
            }
            addressSpaceRuleList.clear();
        }
        
        @SuppressWarnings("unused")
        public void writeToStorage(IStorageSourceService storageSource) {
            for (Map<String, Object> row : addressSpaceList.values()) {
                storageSource.insertRow(
                    AddressSpaceManagerImpl.ADDRESS_SPACE_TABLE_NAME, row);
            }
            for (Map<String, Object> row : addressSpaceRuleList.values()) {
                storageSource.insertRow(AddressSpaceManagerImpl.ADDRESS_SPACE_IDENTIFIER_RULE_TABLE_NAME, row);
            }
        }

        public void verifyAddressSpaceConfig (AddressSpaceManagerImpl amgr) {
            // Make sure the number of configured AS matched
            int expectedNumAs;
            // account for default address space (+1)
            expectedNumAs = addressSpaceList.size() + 1;
            for (Map<String, Object> as : addressSpaceList.values()) {
                if (as.get(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME)==(Boolean)false)
                {   expectedNumAs--;
                    continue;
                }
                if (as.get(AddressSpaceManagerImpl.SEPARATOR_COLUMN_NAME)==null)
                {   expectedNumAs--; 
                    continue;
                }
                BetterEntityClass addressSpace =
                    amgr.getAddressSpaceMap().get(
                        as.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME));

                // address-space-name
                assertEquals(addressSpace.getName(), 
                    as.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME));
                assertEquals(addressSpace.getDescription(),
                    as.get(AddressSpaceManagerImpl.DESCRIPTION_COLUMN_NAME));
                assertEquals(addressSpace.isActive(),
                    as.get(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME));
                assertEquals(addressSpace.getPriority(),
                    as.get(AddressSpaceManagerImpl.PRIORITY_COLUMN_NAME));
            }
            
            assertEquals(expectedNumAs, amgr.addressSpaceMap.size());
            
            // Make sure the entityClass array is correctly indexed
            assertEquals(4096, amgr.entityClasses.size());
            int numAsInArray = 0;
            for (int vlan = 0; vlan < amgr.entityClasses.size(); vlan++) {
                BetterEntityClass as = amgr.entityClasses.get(vlan);
                if (as != null) {
                    numAsInArray++;
                    if (vlan==0) {
                        assertNull(as.getVlan());
                    } else {
                        assertEquals(Short.valueOf((short)vlan), as.getVlan());
                    }
                }
            }
            // default address space will not be in array, so adjust count
            // for comparison
            assertEquals(expectedNumAs, numAsInArray+1);
            

            for (Map<String, Object> rule : addressSpaceRuleList.values()) {
                BetterEntityClass addressSpace =
                        amgr.getAddressSpaceMap().get(
                            rule.get(AddressSpaceManagerImpl.ADDRESS_SPACE_COLUMN_NAME));
                if (addressSpace==null)
                    continue;
                MembershipRule<BetterEntityClass> id_rule =
                    amgr.getIdentifierRuleMap().get(
                        rule.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME));

                BetterEntityClass parentAddressSpace =
                    id_rule.getParentDeviceGroup();
                assertEquals(parentAddressSpace.getName() + "|" +
                             id_rule.getRuleName(), id_rule.getName());

                // address-space-name | identifier-rule-name
                assertEquals(id_rule.getName(),
                    rule.get(AddressSpaceManagerImpl.NAME_COLUMN_NAME));
                assertEquals(id_rule.getParentDeviceGroup().getName(),
                    rule.get(
                        AddressSpaceManagerImpl.ADDRESS_SPACE_COLUMN_NAME));
                assertEquals(id_rule.getDescription(),
                    rule.get(AddressSpaceManagerImpl.DESCRIPTION_COLUMN_NAME));
                assertEquals(id_rule.getRuleName(),
                    rule.get(AddressSpaceManagerImpl.RULE_COLUMN_NAME));
                assertEquals(id_rule.isActive(),
                    rule.get(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME));
                assertEquals(id_rule.getPriority(),
                    rule.get(AddressSpaceManagerImpl.PRIORITY_COLUMN_NAME));
                // assertEquals(id_rule.getMac(),
                 //    rule.get(AddressSpaceManagerImpl.MAC_COLUMN_NAME));
                assertEquals(id_rule.getSwitchId(),
                    rule.get(AddressSpaceManagerImpl.SWITCH_COLUMN_NAME));
                assertEquals(id_rule.getPorts(),
                    rule.get(AddressSpaceManagerImpl.PORTS_COLUMN_NAME));
                assertEquals(id_rule.getVlans(),
                    rule.get(AddressSpaceManagerImpl.VLANS_COLUMN_NAME));
                assertEquals(id_rule.getTags(),
                    rule.get(AddressSpaceManagerImpl.TAGS_COLUMN_NAME));
            }
        }
    }

    private void configTestsRun (AddressSpaceConfigTest test) {
        // FIXME: this is very ugly. Every write to storage triggered a 
        // notification. However, due to the 750ms delay, we just call
        // the readAddressSpaceConfigFromStorage() method before the
        // timeout expires and we will terminate the test before it expires
        addressSpaceManager.readAddressSpaceConfigFromStorage();
        test.verifyAddressSpaceConfig(addressSpaceManager);
        assertEquals(true, expectedChangedAddressSpaces.isEmpty());
    }

    private void configTestsCleanup (AddressSpaceConfigTest configTest) {

        /*
         * Cleanup at the end.
         */
        for (String as : configTest.getAddressSpaceList().keySet()) {
            expectedChangedAddressSpaces.add(as);
        }
        expectedChangedAddressSpaces.add("default");
        configTest.deleteAll(storageSource);
        configTestsRun(configTest);
    }
    
    protected IAddressSpaceManagerService addressSpaceManagerService;
    private   HashSet<String>             expectedChangedAddressSpaces;
    private   Map<String, Object>         config_as;
    private   Map<String, Object>         config_rule;

    // *********************
    // IAddressSpaceListener
    // *********************
    @Override
    public void entityClassChanged (Set<String> entityClassNames)
    {
        assertEquals(expectedChangedAddressSpaces, entityClassNames);
        expectedChangedAddressSpaces.removeAll(entityClassNames);
    }

    /*
     * Configuration utily routine to configure an address-space along with
     * an identifier rule.
     * TODO: remove and replace with AddressSpaceDefinition and 
     * AddressSpaceRule classes. These should make the tests way more
     * readable. 
     */
    private void testAddressSpaceConfigCommon (
            AddressSpaceConfigTest configTest,
            String  as_name,
            boolean as_active,
            int     as_priority,
            String  as_description,
            int  as_egress_vlan,

            String  as_rule_name,
            String  as_rule_description,
            boolean as_rule_active,
            int     as_rule_priority,
            String  as_rule_mac,
            String  as_rule_switch,
            String  as_rule_ports,
            String  as_rule_vlans,
            String  as_rule_tags
        ) {

        config_as   = new HashMap<String, Object>();
        config_rule = new HashMap<String, Object>();

        config_as.put(AddressSpaceManagerImpl.NAME_COLUMN_NAME, as_name);
        config_as.put(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME, as_active);
        config_as.put(AddressSpaceManagerImpl.PRIORITY_COLUMN_NAME,
                      as_priority);
        if (as_egress_vlan!=0)
            config_as.put(AddressSpaceManagerImpl.SEPARATOR_COLUMN_NAME,
                      as_egress_vlan);
        config_as.put(AddressSpaceManagerImpl.DESCRIPTION_COLUMN_NAME,
                      as_description);

        config_rule.put(AddressSpaceManagerImpl.NAME_COLUMN_NAME,
                        as_name + "|" + as_rule_name);
        config_rule.put(AddressSpaceManagerImpl.RULE_COLUMN_NAME, as_rule_name);
        config_rule.put(AddressSpaceManagerImpl.ADDRESS_SPACE_COLUMN_NAME,
                        as_name);
        config_rule.put(AddressSpaceManagerImpl.DESCRIPTION_COLUMN_NAME,
                        as_rule_description);
        config_rule.put(AddressSpaceManagerImpl.ACTIVE_COLUMN_NAME,
                        as_rule_active);
        config_rule.put(AddressSpaceManagerImpl.PRIORITY_COLUMN_NAME,
                        as_rule_priority);
        config_rule.put(AddressSpaceManagerImpl.MAC_COLUMN_NAME, as_rule_mac);
        config_rule.put(AddressSpaceManagerImpl.SWITCH_COLUMN_NAME,
                        as_rule_switch);
        config_rule.put(AddressSpaceManagerImpl.PORTS_COLUMN_NAME,
                        as_rule_ports);
        config_rule.put(AddressSpaceManagerImpl.VLANS_COLUMN_NAME,
                        as_rule_vlans);
        config_rule.put(AddressSpaceManagerImpl.TAGS_COLUMN_NAME, as_rule_tags);

        configTest.addAddressSpace(storageSource, config_as);
        configTest.addAddressSpaceRule(storageSource, config_rule);
    }

    /*
     * Wrapper routine to configure address-spaces with most defaults but some.
     */
    private void testAddressSpaceConfigCommonBrief (
            AddressSpaceConfigTest configTest,
            String                 as_name,
            int                    as_priority,
            String                 as_rule_name,
            int                    as_rule_priority,
            String                 as_vlans) {
        testAddressSpaceConfigCommon(configTest,
            as_name, true, as_priority, "test", Integer.parseInt(as_vlans),
            as_rule_name, "test rule", true, as_rule_priority,
            null, "00:00:00:00:00:00:00:01", "A54-58,B1,C0-5",
            as_vlans, "org.sdnplatform.tag1=value1");
    }

    @Test 
    public void testKeyFields() {
        EnumSet<DeviceField> keyFields = addressSpaceManager.getKeyFields();
        assertEquals(4, keyFields.size());
        assertEquals(true, keyFields.contains(DeviceField.MAC));
        assertEquals(true, keyFields.contains(DeviceField.SWITCH));
        assertEquals(true, keyFields.contains(DeviceField.PORT));
        assertEquals(true, keyFields.contains(DeviceField.VLAN));
        // we should not be able to modify the keyFields.
        keyFields.add(DeviceField.IPV4);
        assertEquals(5, keyFields.size());
        keyFields = addressSpaceManager.getKeyFields();
        assertEquals(4, keyFields.size());
    }

    // Test classify entities for external ports
    @Test
    public void testClassifyEntityEdge() {
        ITopologyService topology = createMock(ITopologyService.class);
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).
                andReturn(true).anyTimes();
        AddressSpaceConfigTest confTest = doDummyConfig(true);
        
        addressSpaceManager.topology = topology;
        replay(topology);
        
        BetterEntityClass bec;
        BetterEntityClass bec2;
        // no vlan
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityNoVlan_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityNoVlan_1);
        assertEquals(bec, bec2);
        assertNull(bec.vlan);
        assertEquals("default" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityNoVlan_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityNoVlan_2);
        assertEquals(bec, bec2);
        assertNull(bec.vlan);
        assertEquals("default" , bec.getName());
        
        // vlan 42
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan42_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan42_1);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)42), bec.vlan);
        assertEquals("ASP42" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan42_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan42_2);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)42), bec.vlan);
        assertEquals("ASP42" , bec.getName());
        
        // vlan 1
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan1_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan1_2);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)1), bec.vlan);
        assertEquals("ASP1" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan1_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan1_2);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)1), bec.vlan);
        assertEquals("ASP1" , bec.getName());
        
        // vlan 23 -- no AddrSpace for it ==> default
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan23_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan23_1);
        assertEquals(bec, bec2);
        assertEquals(addressSpaceManager.defaultEntityClass, bec);
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan23_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan23_2);
        assertEquals(bec, bec2);
        assertEquals(addressSpaceManager.defaultEntityClass, bec);
        
        // switchID 10
        bec = (BetterEntityClass)addressSpaceManager
                .classifyEntity(entitySw10_noVlan);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entitySw10_noVlan);
        assertEquals(bec, bec2);
        assertEquals("switchId10", bec.getName());
        assertEquals(Short.valueOf((short)1001), bec.vlan);
        bec = (BetterEntityClass)addressSpaceManager
                .classifyEntity(entitySw10_Vlan100);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entitySw10_Vlan100);
        assertEquals(bec, bec2);
        assertEquals("switchId10", bec.getName());
        assertEquals(Short.valueOf((short)1001), bec.vlan);
        // edge port, no vlan based rule ==> no rule matches. Packet
        // is tagged with vlan associated with an AS ==> don't allow
        bec = (BetterEntityClass)addressSpaceManager
                .classifyEntity(entityVlan1001);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan1001);
        assertEquals(bec, bec2);
        assertEquals(null, bec);
        configTestsCleanup(confTest);
    }

    // Test classify entities for internal ports
    @Test
    public void testClassifyEntityInternal() {
        AddressSpaceConfigTest confTest = doDummyConfig(true);
        ITopologyService topology = createMock(ITopologyService.class);
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).
                andReturn(false).anyTimes();
        
        addressSpaceManager.topology = topology;
        replay(topology);
        
        BetterEntityClass bec;
        BetterEntityClass bec2;
        // no vlan
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityNoVlan_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityNoVlan_1);
        assertEquals(bec, bec2);
        assertNull(bec.vlan);
        assertEquals("default" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityNoVlan_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityNoVlan_2);
        assertEquals(bec, bec2);
        assertNull(bec.vlan);
        assertEquals("default" , bec.getName());
        
        // vlan 42
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan42_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan42_1);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)42), bec.vlan);
        assertEquals("ASP42" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan42_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan42_2);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)42), bec.vlan);
        assertEquals("ASP42" , bec.getName());
        
        // vlan 1
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan1_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan1_2);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)1), bec.vlan);
        assertEquals("ASP1" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan1_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan1_2);
        assertEquals(bec, bec2);
        assertEquals(Short.valueOf((short)1), bec.vlan);
        assertEquals("ASP1" , bec.getName());
        
        // vlan 23 ==> no address space configures for it ==> default
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan23_1);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan23_1);
        assertEquals(bec, bec2);
        assertEquals(addressSpaceManager.defaultEntityClass, bec);
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan23_2);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan23_2);
        assertEquals(bec, bec2);
        assertEquals(addressSpaceManager.defaultEntityClass, bec);
        
        // switchID 10
        bec = (BetterEntityClass)addressSpaceManager
                .classifyEntity(entitySw10_noVlan);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entitySw10_noVlan);
        // no vlan ==> default entity class
        assertEquals(bec, bec2);
        assertEquals("default", bec.getName());
        assertEquals(null, bec.vlan);
        bec = (BetterEntityClass)addressSpaceManager
                .classifyEntity(entitySw10_Vlan100);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entitySw10_Vlan100);
        // vlan doesn't exists ==> default address space
        assertEquals(bec, bec2);
        assertEquals(addressSpaceManager.defaultEntityClass, bec);
        // tagged with correct vlan
        bec = (BetterEntityClass)addressSpaceManager
                .classifyEntity(entityVlan1001);
        bec2 = (BetterEntityClass)addressSpaceManager
                .reclassifyEntity(null, entityVlan1001);
        assertEquals(bec, bec2);
        assertEquals("switchId10", bec.getName());
        assertEquals(Short.valueOf((short)1001), bec.vlan);
        
        configTestsCleanup(confTest);
    }

    @Test
    public void testExceptionHandling() {
        Logger logger = createMock(Logger.class);
        Capture<String> logMsgCapture = new Capture<String>();
        AddressSpaceManagerImpl.logger = logger;
        logger.error(capture(logMsgCapture), anyObject(Throwable.class));
        expectLastCall().once();
        replay(logger);
        addressSpaceManager.deviceGroupMatcher = null;
        addressSpaceManager.doMatchEntity(null);
        verify(logger);
        String msg = logMsgCapture.getValue();
        assertEquals(true, msg.contains("Failed to assign an address space"));
    }

    /* some simple test to see if matching is active
     */
    protected void doAssertActive() {
        ITopologyService topology = createMock(ITopologyService.class);
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).
                andReturn(true).anyTimes();
        
        addressSpaceManager.topology = topology;
        
        assertEquals(true, 
                     addressSpaceManager.deviceGroupMatcher.hasVlanRules());
        assertEquals(true, addressSpaceManager.addressSpaceGlobalActiveState);
        assertEquals(false, addressSpaceManager.entityClasses.isEmpty());
        
        
        replay(topology);
        BetterEntityClass bec;
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityNoVlan_1);
        assertEquals("default" , bec.getName());
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan42_1);
        assertEquals("ASP42" , bec.getName());
        verify(topology);
        
    }

    /* some simple test to see if matching is inactive.
     * will always classify as NULL
     */
    protected void doAssertInactive() {
        addressSpaceManager.topology = null;
        
        assertEquals(false, 
                     addressSpaceManager.deviceGroupMatcher.hasVlanRules());
        assertEquals(false, addressSpaceManager.addressSpaceGlobalActiveState);
        assertEquals(true, addressSpaceManager.entityClasses.isEmpty());
        
        BetterEntityClass bec;
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityNoVlan_1);
        assertEquals(null, bec);
        bec = (BetterEntityClass)addressSpaceManager.classifyEntity(entityVlan42_1);
        assertEquals(null, bec);
    }

    @Test
    public void testClearConfigState() {
        doDummyConfig(true);
        doAssertActive();
        addressSpaceManager.clearConfigState();
        doAssertInactive();
    }

    @Test
    public void testRoleChange() {
        AddressSpaceConfigTest confTest = doDummyConfig(true);
        Logger logger = createNiceMock(Logger.class);
        AddressSpaceManagerImpl.logger = logger;
        logger.error(anyObject(String.class), anyObject(String.class));
        expectLastCall().atLeastOnce();
        // I'd really like to expect just any debug() call, but that's 
        // not possible :-(
        logger.debug(anyObject(String.class));
        expectLastCall().atLeastOnce();
        
        replay(logger);
        doAssertActive();
        addressSpaceManager.roleChanged(Role.MASTER, Role.SLAVE);
        doAssertInactive();
        addressSpaceManager.roleChanged(Role.SLAVE, Role.SLAVE);
        doAssertInactive();
        addressSpaceManager.roleChanged(Role.SLAVE, Role.EQUAL);  // no-op
        doAssertInactive();
        
        for (String asName: confTest.addressSpaceList.keySet())
            expectedChangedAddressSpaces.add(asName);
        expectedChangedAddressSpaces.add("default");
        addressSpaceManager.roleChanged(Role.SLAVE, Role.MASTER);
        confTest = doDummyConfig(true);
        doAssertActive();
        addressSpaceManager.roleChanged(Role.MASTER, Role.MASTER);
        doAssertActive();
        addressSpaceManager.roleChanged(Role.MASTER, Role.EQUAL); // no-op
        doAssertActive();
        
        addressSpaceManager.roleChanged(Role.SLAVE, Role.SLAVE);
        doAssertInactive();
        // addressSpaceManager trusts the oldRole we give it and will
        // only re-read config if it's a SLAVE->MASTER transition
        addressSpaceManager.roleChanged(Role.MASTER, Role.MASTER);
        doAssertInactive();
        
        verify(logger);
    }

    @Test
    public void testAddressSpaceConfig_1 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 1000, "rule1",
                                          10, "101");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 1000, "rule1",
                                         10, "102");
        expectedChangedAddressSpaces.add("as3");
        testAddressSpaceConfigCommonBrief(configTest, "as4", 1000, "rule1",
                                         10, "103");
        expectedChangedAddressSpaces.add("as4");
        
        expectedChangedAddressSpaces.add("default");

        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_2 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 1000, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 1000, "rule1",
                                         10, "300");
        expectedChangedAddressSpaces.add("as3");
        
        expectedChangedAddressSpaces.add("default");
        // We don't expect any notifications for changed address spaces
        // since we only added some 

        configTestsRun(configTest);

        /*
         * Add more address-spaces at lower priority than any of those that
         * already exist and make sure that only those that are newly added
         * get notified.
         */
        testAddressSpaceConfigCommonBrief(configTest, "as4", 500, "rule1",
                                         10, "400");
        expectedChangedAddressSpaces.add("as4");
        expectedChangedAddressSpaces.add("default");
        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_3 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 1000, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 500, "rule1",
                                         10, "300");
        expectedChangedAddressSpaces.add("as3");

        expectedChangedAddressSpaces.add("default");

        configTestsRun(configTest);

        /*
         * Add more address-spaces at lower priority than any of those that
         * already exist and make sure that only those that are newly added
         * get notified.
         */
        configTest.deleteAddressSpace(storageSource, "as3");
        expectedChangedAddressSpaces.add("as3");

        testAddressSpaceConfigCommonBrief(configTest, "as4", 500, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");

        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_4 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        expectedChangedAddressSpaces.add("default");

        configTestsRun(configTest);

        /*
         * Update a couple of address-spaces wrt contents such as vlans.
         */
        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule2",
                                          20, "211");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("default");

        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_5 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the priority of the address-space(s) rules and verify.
         */
        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule2",
                                          50, "200");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as3");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_6 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the priority of the address-space(s) and verify.
         */
        testAddressSpaceConfigCommonBrief(configTest, "as2", 1500, "rule1",
                                          10, "202");
        expectedChangedAddressSpaces.add("as1");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as3");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_7 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as21", 500, "rule1",
                                          10, "210");
        expectedChangedAddressSpaces.add("as21");


        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        testAddressSpaceConfigCommonBrief(configTest, "as4", 100, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the priority of the address-space(s) and verify.
         */
        testAddressSpaceConfigCommonBrief(configTest, "as2", 200, "rule1",
                                          10, "202");
        expectedChangedAddressSpaces.add("as21");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }

    @Test
    public void testAddressSpaceConfig_8 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as21", 500, "rule1",
                                          10, "210");
        expectedChangedAddressSpaces.add("as21");


        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        testAddressSpaceConfigCommonBrief(configTest, "as4", 100, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the active state of an address-space
         */
        testAddressSpaceConfigCommon(configTest,
            "as2", false, 500, "test", 200, 
            "rule1", "test rule", true, 10,
            "00:00:00:00:00:01", "00:00:00:00:00:00:00:01", "A54-58,B1,C0-5",
            "200", "org.sdnplatform.tag1=value1");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as21");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the active state of an address-space
         */
        testAddressSpaceConfigCommon(configTest,
            "as2", true, 500, "test", 200,
            "rule1", "test rule", true, 10,
            "00:00:00:00:00:01", "00:00:00:00:00:00:00:01", "A54-58,B1,C0-5",
            "200", "org.sdnplatform.tag1=value1");

        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as21");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }
    

    @Test
    public void testAddressSpaceConfig_9 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as21", 500, "rule1",
                                          10, "210");
        expectedChangedAddressSpaces.add("as21");


        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        testAddressSpaceConfigCommonBrief(configTest, "as4", 100, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the active state of an address-space rule.
         */
        testAddressSpaceConfigCommon(configTest,
            "as2", true, 500, "test", 200,
            "rule1", "test rule", false, 10,
            "00:00:00:00:00:01", "00:00:00:00:00:00:00:01", "A54-58,B1,C0-5",
            "200", "org.sdnplatform.tag1=value1");

        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as21");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the active state of an address-space rule.
         */
        testAddressSpaceConfigCommon(configTest,
            "as2", true, 500, "test", 200,
            "rule1", "test rule", true, 10,
            "00:00:00:00:00:01", "00:00:00:00:00:00:00:01", "A54-58,B1,C0-5",
            "200", "org.sdnplatform.tag1=value1");

        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as21");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }
    
    @Test
    public void testAddressSpaceConfig_10 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();

        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");

        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");

        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");

        testAddressSpaceConfigCommonBrief(configTest, "as4", 200, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");

        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);

        /*
         * Change the priority of the address-space(s) rules and verify.
         */
        configTest.deleteAddressSpace(storageSource, "as1");
        testAddressSpaceConfigCommonBrief(configTest, "as3", 400, "rule2",
                                          50, "300");
        expectedChangedAddressSpaces.add("as1");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");
        expectedChangedAddressSpaces.add("default");

        configTestsRun(configTest);

        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }
    
    @Test
    public void testAddressSpaceConfig_11 () {
        AddressSpaceConfigTest configTest = new AddressSpaceConfigTest();
        /*special value '0' passed in as vlan_egress_tag unconfigured*/
        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "0");
        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");
        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");
        testAddressSpaceConfigCommonBrief(configTest, "as4", 200, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");
        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);
        /*
         * remove the address-space(s) vlan_egress_tag and verify.
         */
        
        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "0");
        expectedChangedAddressSpaces.add("as2");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");
        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);
        
        /*
         * Change the active state of an address-space to False
         */
        testAddressSpaceConfigCommon(configTest,
            "as3", false, 300, "test", 200, 
            "rule1", "test rule", true, 10,
            "00:00:00:00:00:01", "00:00:00:00:00:00:00:01", "A54-58,B1,C0-5",
            "200", "org.sdnplatform.tag1=value1");
        expectedChangedAddressSpaces.add("as3");
        expectedChangedAddressSpaces.add("as4");
        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);
        
        /*add all address space as normal*/
        testAddressSpaceConfigCommonBrief(configTest, "as1", 1000, "rule1",
                                          10, "100");
        expectedChangedAddressSpaces.add("as1");
        testAddressSpaceConfigCommonBrief(configTest, "as2", 500, "rule1",
                                          10, "200");
        expectedChangedAddressSpaces.add("as2");
        testAddressSpaceConfigCommonBrief(configTest, "as3", 300, "rule1",
                                          10, "300");
        expectedChangedAddressSpaces.add("as3");
        testAddressSpaceConfigCommonBrief(configTest, "as4", 200, "rule1",
                                          10, "400");
        expectedChangedAddressSpaces.add("as4");
        expectedChangedAddressSpaces.add("default");
        
        configTestsRun(configTest);
        /*
         * Cleanup at the end.
         */
        configTestsCleanup(configTest);
    }
    
    /* Test getSwitchPortVlanMode for external (attachment point) ports only */
    @Test
    public void testGetSwitchPortVlanMode() {
        ITopologyService topology = createMock(ITopologyService.class);
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).
                andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort(), anyBoolean())).
                andReturn(true).anyTimes();
        addressSpaceManager.topology = topology;
        replay(topology);
        
        // from doDummyConfig: SwitchId 10 will be vlan 1001 UNTAGGED
        AddressSpaceConfigTest confTest = doDummyConfig(false);
        
        // AddressSpace 1002: Switch 11: allowed tagged 
        AddressSpaceDefinition asDef = new AddressSpaceDefinition("AS1002");
        asDef.active = true;
        asDef.egress_vlan = 1002;
        AddressSpaceRule asrule = new AddressSpaceRule("AS1002",
                                                       "ruleSw11Vlan1002");
        asrule.active = true;
        asrule.vlans = "1002";
        asrule.setSwitchId(11L);
        expectedChangedAddressSpaces.add("AS1002");
        confTest.addAddressSpace(storageSource, asDef);
        confTest.addAddressSpaceRule(storageSource, asrule);
        
        
        
        // AddressSpace 1003: allow tagged everywhere
        // TODO: we need to give it high priority to make it match 
        // for switch 11
        asDef = new AddressSpaceDefinition("AS1003");
        asDef.active = true;
        asDef.egress_vlan = 1003;
        asDef.priority = 100;
        asrule = new AddressSpaceRule("AS1003", "vlan1003");
        asrule.active = true;
        asrule.vlans = "1003";
        expectedChangedAddressSpaces.add("AS1003");
        confTest.addAddressSpace(storageSource, asDef);
        confTest.addAddressSpaceRule(storageSource, asrule);
        
        configTestsRun(confTest);
        
        
        SwitchPort swp10x1 = new SwitchPort(10L, 1);
        SwitchPort swp11x1 = new SwitchPort(11L, 1);
        SwitchPort swp12x1 = new SwitchPort(12L, 1);
        Short untagged = Ethernet.VLAN_UNTAGGED;
        
        // the current VLAN should be ignored for non-default address-spaces 
        // We test with several different ones. Some used by AS, some not.
        Short[] currentVlansNonDefault =
                new Short[] { untagged, 1, 2, 3, 23, 100, 1001, 
                              1002, 1003 };
        
       
        for (Short curVlan: currentVlansNonDefault) {
            // the current VLAN should be ignored for non-default address-spaces 
            String asName = "switchId10";
            Short vlan = 1001;
            String msg = "currentVlan=" + curVlan;
            assertEquals(msg, untagged, 
                         addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                                asName, curVlan, true));
            assertEquals(msg, null, 
                         addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                                                                   asName,
                                                                   curVlan, 
                                                                   true));
            assertEquals(msg, null, 
                         addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                                asName, curVlan, true));
            
            asName = "AS1002";
            vlan = 1002;
            assertEquals(msg, null, 
                         addressSpaceManager.getSwitchPortVlanMode(swp10x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                                asName, curVlan, true));
            assertEquals(msg, null, 
                         addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                                asName, curVlan, true));
            
            asName = "AS1003";
            vlan = 1003;
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp10x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                                asName, curVlan, true));
                    
        }
        
        // Default address space, vlan that doesn't belong to any other AS
        Short vlan = 23;
        assertEquals(null, // the rule for switchId10 matches all vlans on sw10
                     addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                            "default", vlan, true));
        assertEquals(vlan, 
                     addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                            "default", vlan, true));
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                            "default", vlan, true));
        vlan = untagged;
        assertEquals(null,
                     addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                            "default", vlan, true));
        assertEquals(vlan, 
                     addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                            "default", vlan, true));
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                            "default", vlan, true));
        
        vlan = 1001; // vlan in use by other AS. never allow in default
        assertEquals(null,
                     addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                            "default", vlan, true));
        assertEquals(null, 
                     addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                            "default", vlan, true));
        assertEquals(null,
                     addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                            "default", vlan, true));
        
        
        configTestsCleanup(confTest);
    }
    
    
    /* Test getSwitchPortVlanMode for internal ports only */
    @Test
    public void testGetSwitchPortVlanModeInternalPorts() {
        ITopologyService topology = createMock(ITopologyService.class);
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).
                andReturn(false).anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort(), anyBoolean())).
                andReturn(false).anyTimes();
        addressSpaceManager.topology = topology;
        replay(topology);
        
        // from doDummyConfig: SwitchId 10 will be vlan 1001 UNTAGGED
        AddressSpaceConfigTest confTest = doDummyConfig(false);
        
        // AddressSpace 1002: Switch 11: allowed tagged 
        AddressSpaceDefinition asDef = new AddressSpaceDefinition("AS1002");
        asDef.active = true;
        asDef.egress_vlan = 1002;
        AddressSpaceRule asrule = new AddressSpaceRule("AS1002",
                                                       "ruleSw11Vlan1002");
        asrule.active = true;
        asrule.vlans = "1002";
        asrule.setSwitchId(11L);
        expectedChangedAddressSpaces.add("AS1002");
        confTest.addAddressSpace(storageSource, asDef);
        confTest.addAddressSpaceRule(storageSource, asrule);
        
        
        
        // AddressSpace 1003: 
        // TODO: we need to give it high priority to make it match 
        // for switch 11
        asDef = new AddressSpaceDefinition("AS1003");
        asDef.active = true;
        asDef.egress_vlan = 1003;
        asDef.priority = 100;
        asrule = new AddressSpaceRule("AS1003", "vlan1003");
        asrule.active = true;
        asrule.vlans = "1003";
        expectedChangedAddressSpaces.add("AS1003");
        confTest.addAddressSpace(storageSource, asDef);
        confTest.addAddressSpaceRule(storageSource, asrule);
        
        configTestsRun(confTest);
        
        
        SwitchPort swp10x1 = new SwitchPort(10L, 1);
        SwitchPort swp11x1 = new SwitchPort(11L, 1);
        SwitchPort swp12x1 = new SwitchPort(12L, 1);
        Short untagged = Ethernet.VLAN_UNTAGGED;
        
        // the current VLAN should be ignored for non-default address-spaces 
        // We test with several different ones. Some used by AS, some not.
        Short[] currentVlansNonDefault =
                new Short[] { untagged, 1, 2, 3, 23, 100, 1001, 
                              1002, 1003 };
        
       
        for (Short curVlan: currentVlansNonDefault) {
            // the current VLAN should be ignored for non-default address-spaces 
            String asName = "switchId10";
            Short vlan = 1001;
            String msg = "currentVlan=" + curVlan;
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                                                                   asName,
                                                                   curVlan, 
                                                                   true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                                asName, curVlan, true));
            
            asName = "AS1002";
            vlan = 1002;
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp10x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                                asName, curVlan, true));
            
            asName = "AS1003";
            vlan = 1003;
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp10x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                                asName, curVlan, true));
            assertEquals(msg, vlan, 
                         addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                                asName, curVlan, true));
                    
        }
        
        // Default address space, vlan that doesn't belong to any other AS
        Short vlan = 23;
        assertEquals(vlan, 
                     addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                            "default", vlan, true));
        assertEquals(vlan, 
                     addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                            "default", vlan, true));
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                            "default", vlan, true));
        vlan = untagged;
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                            "default", vlan, true));
        assertEquals(vlan, 
                     addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                            "default", vlan, true));
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                            "default", vlan, true));
        
        vlan = 1001; // vlan is used by other AS. This is a case that can 
        // only happen as a race condition. In normal operation these packets
        // would have been dropped by the ingress switch. We still allow the 
        // packet though. Such packet in transit while config change strikes 
        // are ok. 
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp10x1, 
                            "default", vlan, true));
        assertEquals(vlan, 
                     addressSpaceManager.getSwitchPortVlanMode(swp11x1,
                            "default", vlan, true));
        assertEquals(vlan,
                     addressSpaceManager.getSwitchPortVlanMode(swp12x1,
                            "default", vlan, true));
        
        
        configTestsCleanup(confTest);
    }
    
    
    @Test
    public void testSanity() {
        assertTrue(true);
    }
}
