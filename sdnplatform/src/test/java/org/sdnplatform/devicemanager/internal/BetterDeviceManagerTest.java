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

import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.IEntityClass;
import org.sdnplatform.devicemanager.IEntityClassifierService;
import org.sdnplatform.devicemanager.internal.BetterDeviceManagerImpl;
import org.sdnplatform.devicemanager.internal.DefaultEntityClassifier;
import org.sdnplatform.devicemanager.internal.Entity;
import org.sdnplatform.devicemanager.internal.EntityConfig;
import org.sdnplatform.flowcache.FlowReconcileManager;
import org.sdnplatform.flowcache.IFlowReconcileService;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.restserver.IRestApiService;
import org.sdnplatform.restserver.RestApiServer;
import org.sdnplatform.storage.IResultSet;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.OperatorPredicate;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.tagmanager.ITagListener;
import org.sdnplatform.tagmanager.Tag;
import org.sdnplatform.tagmanager.TagDoesNotExistException;
import org.sdnplatform.tagmanager.TagManagerException;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.threadpool.IThreadPoolService;
import org.sdnplatform.topology.ITopologyService;





@SuppressWarnings("unchecked")
public class BetterDeviceManagerTest extends PlatformTestCase {
    private BetterDeviceManagerImpl betterDeviceManager;
    ITopologyService topology;

    MemoryStorageSource storageSource;
    FlowReconcileManager flowReconcileMgr;

    private static class TagManagerTest {
        ArrayList<Map<String, Object>> m_tags = 
            new ArrayList<Map<String, Object>>();
        ArrayList<Map<String, Object>> m_tagEntityMappings = 
            new ArrayList<Map<String, Object>>();
        
        public void addTag(Map<String, Object>... tags) {
            for (Map<String, Object> tag : tags) {
                m_tags.add(tag);
            }
        }
        
        public void addEntityTagMappings(Map<String, Object>... tagMappings) {
            for (Map<String, Object> mapping : tagMappings) {
                m_tagEntityMappings.add(mapping);
            }
        }
        
        public void writeToStorageTags(IStorageSourceService storageSource) {
            for (Map<String, Object> row : m_tags) {
                storageSource.insertRow(BetterDeviceManagerImpl.TAG_TABLE_NAME,
                                        row);
            }
        }
        public void writeToStorageEntityMappings(
                                       IStorageSourceService storageSource) {
            for (Map<String, Object> row : m_tagEntityMappings) {
                storageSource.insertRow(
                                    BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME,
                                    row);
            }
        }
    }
    
    // in our service insertion OUI
    protected static byte[] vMAC = Ethernet.toMACAddress("5C:16:C7:01:DE:AD");
    protected static int vIP = IPv4.toIPv4Address("192.168.1.3");
    protected static byte[] snMAC = Ethernet.toMACAddress("00:44:44:22:11:00");
    protected static int snIP = IPv4.toIPv4Address("192.168.1.11");

    private static final Map<String, Object> tag1;
    static {
        tag1 = new HashMap<String, Object>();
        tag1.put(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                 "sdnplatform.org|tag1Name|tag1Value");
        tag1.put(BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                 "sdnplatform.org");
        tag1.put(BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME, "tag1Name");
        tag1.put(BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME, "tag1Value");
    }
    
    private static final Map<String, Object> tag1Mapping;
    static {
        tag1Mapping = new HashMap<String, Object>();
        tag1Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME, 
           "sdnplatform.org|tag1Name|tag1Value|00:00:00:00:00:01|10|" +
           "00:00:00:00:00:00:00:01|eth1");
        tag1Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME, 
                        "sdnplatform.org|tag1Name|tag1Value");
        tag1Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME, 
                        "00:00:00:00:00:01");
        tag1Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME, 
                        "10");
        tag1Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME, 
                "00:00:00:00:00:00:00:01");
        tag1Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME, 
                "eth1");
    }
    
    private static final Map<String, Object> tag2;
    static {
        tag2 = new HashMap<String, Object>();
        tag2.put(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                 "sdnplatform.org|tag2Name|tag2Value");
        tag2.put(BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                 "sdnplatform.org");
        tag2.put(BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME, "tag2Name");
        tag2.put(BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME, "tag2Value");
    }
    
    private static final Map<String, Object> tag2Mapping;
    static {
        tag2Mapping = new HashMap<String, Object>();
        tag2Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME, 
           "sdnplatform.org|tag2Name|tag2Value|00:00:00:00:00:02|null|null|null");
        tag2Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                        "sdnplatform.org|tag2Name|tag2Value");
        tag2Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME,
                        "00:00:00:00:00:02");
        tag2Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME, null);
        tag2Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                        null);
        tag2Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME,
                        null);
    }
    
    
    private static final Map<String, Object> tag3;
    static {
        tag3 = new HashMap<String, Object>();
        tag3.put(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                 "sdnplatform.org|tag3Name|tag3Value");
        tag3.put(BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                 "sdnplatform.org");
        tag3.put(BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME, "tag3Name");
        tag3.put(BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME, "tag3Value");
    }
    
    private static final Map<String, Object> tag3Mapping;
    static {
        tag3Mapping = new HashMap<String, Object>();
        tag3Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME,
                        "sdnplatform.org|tag3Name|tag3Value|null|15|null|null");
        tag3Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                        "sdnplatform.org|tag3Name|tag3Value");
        tag3Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME, null);
        tag3Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME, "15");
        tag3Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                        null);
        tag3Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME,
                        null);
    }
    
    private static final Map<String, Object> tag4;
    static {
        tag4 = new HashMap<String, Object>();
        tag4.put(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                 "sdnplatform.org|tag4Name|tag4Value");
        tag4.put(BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                 "sdnplatform.org");
        tag4.put(BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME, "tag4Name");
        tag4.put(BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME, "tag4Value");
    }
    
    private static final Map<String, Object> tag4Mapping;
    static {
        tag4Mapping = new HashMap<String, Object>();
        tag4Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME,
     "sdnplatform.org|tag4Name|tag4Value|null|null|00:00:00:00:00:00:00:02|null");
        tag4Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                        "sdnplatform.org|tag4Name|tag4Value");
        tag4Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME, null);
        tag4Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME, null);
        tag4Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                        "00:00:00:00:00:00:00:02");
        tag4Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME,
                        null);
    }
    
    private static final Map<String, Object> tag5;
    static {
        tag5 = new HashMap<String, Object>();
        tag5.put(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                 "sdnplatform.org|tag5Name|tag5Value");
        tag5.put(BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                 "sdnplatform.org");
        tag5.put(BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME, "tag5Name");
        tag5.put(BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME, "tag5Value");
    }
    
    private static final Map<String, Object> tag5Mapping;
    static {
        tag5Mapping = new HashMap<String, Object>();
        tag5Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME,
     "sdnplatform.org|tag5Name|tag5Value|null|null|00:00:00:00:00:00:00:03|eth2");
        tag5Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                        "sdnplatform.org|tag5Name|tag5Value");
        tag5Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME, null);
        tag5Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME, null);
        tag5Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                        "00:00:00:00:00:00:00:03");
        tag5Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME,
                        "eth2");
        
    }
    
    private static final Map<String, Object> tag6;
    static {
        tag6 = new HashMap<String, Object>();
        tag6.put(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                 "sdnplatform.org|tag6Name|tag6Value");
        tag6.put(BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                 "sdnplatform.org");
        tag6.put(BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME, "tag6Name");
        tag6.put(BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME, "tag6Value");
    }
    
    private static final Map<String, Object> tag6Mapping;
    static {
        tag6Mapping = new HashMap<String, Object>();
        tag6Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME,
       "sdnplatform.org|tag6Name|tag6Value|null|20|00:00:00:00:00:00:00:04|eth3");
        tag6Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                        "sdnplatform.org|tag6Name|tag6Value");
        tag6Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME, null);
        tag6Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME, "20");
        tag6Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                        "00:00:00:00:00:00:00:04");
        tag6Mapping.put(BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME,
                        "eth3");
        
    }
    

    private static final TagManagerTest oneTagTest;
    static {
        oneTagTest = new TagManagerTest();
        oneTagTest.addTag(tag1);
        oneTagTest.addEntityTagMappings(tag1Mapping);
    }
    
    private static final TagManagerTest twoTagTest;
    static {
        twoTagTest = new TagManagerTest();
        twoTagTest.addTag(tag1, tag2);
        twoTagTest.addEntityTagMappings(tag1Mapping, tag2Mapping);
    }
    
    private static final TagManagerTest threeTagTest;
    static {
        threeTagTest = new TagManagerTest();
        threeTagTest.addTag(tag1, tag2, tag3);
        threeTagTest.addEntityTagMappings(tag1Mapping,
                                          tag2Mapping,
                                          tag3Mapping);
    }
    
    private static final TagManagerTest vlanTagTest;
    static {
        vlanTagTest = new TagManagerTest();
        vlanTagTest.addTag(tag4);
        vlanTagTest.addEntityTagMappings(tag4Mapping);
    }
    
    private static final TagManagerTest switchTagTest;
    static {
        switchTagTest = new TagManagerTest();
        switchTagTest.addTag(tag5);
        switchTagTest.addEntityTagMappings(tag5Mapping);
    }
    
    private static final TagManagerTest switchInterfaceTagTest;
    static {
        switchInterfaceTagTest = new TagManagerTest();
        switchInterfaceTagTest.addTag(tag6);
        switchInterfaceTagTest.addEntityTagMappings(tag6Mapping);
    }
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        ModuleContext fmc = new ModuleContext();
        RestApiServer restApi = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        topology = createMock(ITopologyService.class);
        fmc.addService(IThreadPoolService.class, tp);
        mockControllerProvider = getMockControllerProvider();
        flowReconcileMgr = new FlowReconcileManager();
        storageSource = new MemoryStorageSource();

        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IControllerService.class,
                       mockControllerProvider);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(ITopologyService.class, topology);
        DefaultEntityClassifier entityClassifier =
                new DefaultEntityClassifier();
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        tp.init(fmc);
        entityClassifier.init(fmc);
        restApi.init(fmc);
        storageSource.init(fmc);
        flowReconcileMgr.init(fmc);
        storageSource.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        tp.startUp(fmc);
        
        betterDeviceManager = new BetterDeviceManagerImpl();
        betterDeviceManager.init(fmc);
        betterDeviceManager.startUp(fmc);
        clearTagTable();
    }
    
    private void clearTagTable() {
        IStorageSourceService storageSource = getStorageSource();
        
        // Clear tag table
        IResultSet  rSet = storageSource.executeQuery(
                             BetterDeviceManagerImpl.TAG_TABLE_NAME,
                new String[]{BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME},
                null, null);
        while (rSet.next()) {
            String id = rSet.getString(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME);
            storageSource.deleteRow(BetterDeviceManagerImpl.TAG_TABLE_NAME, id);
        }

        // Clear tag_Host_Mapping table
        rSet = storageSource.executeQuery(
                             BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME,
                new String[]{BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME},
                null, null);
        while (rSet.next()) {
            String id = rSet.getString(
                               BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME);
            storageSource.deleteRow(BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME, 
                                    id);
        }

        BetterDeviceManagerImpl tagManager = getTagManager();
        tagManager.loadTagsFromStorage();
    }

    private void setupTest(TagManagerTest test) {
        clearTagTable();
        IStorageSourceService storageSource = getStorageSource();
        test.writeToStorageTags(storageSource);
        test.writeToStorageEntityMappings(storageSource);

        BetterDeviceManagerImpl tagManager = getTagManager();
        tagManager.loadTagsFromStorage();
    }
     
    private void checkTag(Set<Tag> checkTags, Tag referenceTag) {
        assertTrue(checkTags.contains(referenceTag));
    }
    
    private void checkTag(Tag checkTag, Tag referenceTag) {
        assertTrue(checkTag.equals(referenceTag));
    }
    
    private String getTagDBId(Tag tag) {
        return tag.getNamespace() + Tag.KEY_SEPARATOR + 
            tag.getName() + Tag.KEY_SEPARATOR +
            tag.getValue();
    }
    
    private void checkTagInStorage(Tag tag) {
        IStorageSourceService storageSource = getStorageSource();
        String id = getTagDBId(tag);
        IResultSet rset = 
            storageSource.executeQuery(BetterDeviceManagerImpl.TAG_TABLE_NAME,
                new String[]{BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME},
                new OperatorPredicate(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME, 
                                      OperatorPredicate.Operator.EQ, 
                                      id), null);
        int count = 0;
        while (rset.next()) {
            count += 1;
            assertEquals(id, rset.getString(
                            BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME));
            assertEquals(tag.getNamespace(), rset.getString(
                            BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME));
            assertEquals(tag.getName(), rset.getString(
                            BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME));
            assertEquals(tag.getValue(), rset.getString(
                            BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME));
        }
        assertEquals(1, count);
    }
    
    private void checkNoTagInStorage(Tag tag) {
        IStorageSourceService storageSource = getStorageSource();
        String id = getTagDBId(tag);
        IResultSet rset = 
            storageSource.executeQuery(BetterDeviceManagerImpl.TAG_TABLE_NAME,
                new String[]{BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME},
                new OperatorPredicate(BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME, 
                                      OperatorPredicate.Operator.EQ, 
                                      id), null);

        while (rset.next()) {
            assertFalse((id.equals(rset.getString(
                        BetterDeviceManagerImpl.TAG_ID_COLUMN_NAME))) &&
                (tag.getNamespace().equals(rset.getString(
                        BetterDeviceManagerImpl.TAG_NAMESPACE_COLUMN_NAME))) &&
                (tag.getName().equals(rset.getString(
                        BetterDeviceManagerImpl.TAG_NAME_COLUMN_NAME))) &&
                (tag.getValue().equals(rset.getString(
                        BetterDeviceManagerImpl.TAG_VALUE_COLUMN_NAME))));
        }
    }

    private void checkTagMappingInStorage(Tag tag, String host, Integer vlan, 
                                          String dpid, String interfaceName) {
        IStorageSourceService storageSource = getStorageSource();
        String id = getTagDBId(tag) + Tag.KEY_SEPARATOR + host + 
                Tag.KEY_SEPARATOR + vlan.toString() + Tag.KEY_SEPARATOR + dpid +
                Tag.KEY_SEPARATOR + interfaceName;
        IResultSet rset = 
            storageSource.executeQuery(
                            BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME,
                new String[]{BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                            BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME},
                new OperatorPredicate(
                            BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME, 
                            OperatorPredicate.Operator.EQ, 
                            id), null);
        int count = 0;
        while (rset.next()) {
            count += 1;
            assertEquals(getTagDBId(tag), rset.getString(
                             BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME));
            assertEquals(host, rset.getString(
                              BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME));
        }
        assertEquals(1, count);
    }
    
    private void checkNoTagMappingInStorage(Tag tag, String host, Integer vlan, 
                                            String dpid, String interfaceName) {
        IStorageSourceService storageSource = getStorageSource();
        String vlanStr = null;
        if (vlan != null)
            vlanStr = new String(vlan.toString());
        String id = getTagDBId(tag) + Tag.KEY_SEPARATOR + host + 
                Tag.KEY_SEPARATOR + vlanStr + Tag.KEY_SEPARATOR + dpid +
                Tag.KEY_SEPARATOR + interfaceName;
        IResultSet rset = 
            storageSource.executeQuery(
                        BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME,
                new String[]{BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME,
                        BetterDeviceManagerImpl.TAGMAPPING_TAG_COLUMN_NAME,
                        BetterDeviceManagerImpl.TAGMAPPING_MAC_COLUMN_NAME,
                        BetterDeviceManagerImpl.TAGMAPPING_VLAN_COLUMN_NAME,
                        BetterDeviceManagerImpl.TAGMAPPING_SWITCH_COLUMN_NAME,
                        BetterDeviceManagerImpl.TAGMAPPING_INTERFACE_COLUMN_NAME},
            new OperatorPredicate(
                        BetterDeviceManagerImpl.TAGMAPPING_ID_COLUMN_NAME, 
                        OperatorPredicate.Operator.EQ, 
                        id), null);

        assertFalse(rset.next());
    }
    
    @Test
    public void testInitialLoad() {
        setupTest(threeTagTest);
        BetterDeviceManagerImpl tagManager = getTagManager();
        String host1 = "00:00:00:00:00:01";
        String dpid = "00:00:00:00:00:00:00:01";
        String interfaceName = "eth1";
        Short vlan = new Short((short)10);
        EntityConfig entity = new EntityConfig(host1, vlan.toString(), dpid, 
                                               interfaceName);
        
        Set<Tag> tags = tagManager.getTagsByNamespace("sdnplatform.org");
        assertEquals(3, tags.size());
        Set<Tag> myTag1s = tagManager.getTags("sdnplatform.org", "tag1Name");
        Tag referenceTag1 = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        checkTag(myTag1s, referenceTag1);
        Set<Tag> myTag2s = tagManager.getTags("sdnplatform.org", "tag2Name");
        Tag referenceTag2 = new Tag("sdnplatform.org", "tag2Name", "tag2Value");
        checkTag(myTag2s, referenceTag2);


        tags = tagManager.getTagsByEntityConfig(entity);
        assertEquals(1, tags.size());
        checkTag(tags, referenceTag1);
        //Tag referenceTag3 = new Tag("sdnplatform.org", "tag3Name", "tag3Value");
        checkTag(tags, referenceTag1);

    }
    
    @Test
    public void testAddTag() {
        BetterDeviceManagerImpl tagManager = getTagManager();
        clearTagTable();
        String host1 = "00:00:00:00:00:01";
        String dpid = "00:00:00:00:00:00:00:01";
        String interfaceName = "eth1";
        Short vlan = new Short((short)10);
        EntityConfig entity1 = new EntityConfig(host1, vlan.toString(), dpid, 
                                                interfaceName);
        String host2 = "00:00:00:00:00:02";
        EntityConfig entity2 = new EntityConfig(host2, null, null, null);

        Tag newTag1 = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        tagManager.addTag(newTag1);

        Set<Tag> tags = tagManager.getTags("sdnplatform.org", "tag1Name");
        assertEquals(1, tags.size());
        Tag myTag = tags.iterator().next();
        checkTag(myTag, newTag1);
        checkTagInStorage(newTag1);


        tags = tagManager.getTagsByEntityConfig(entity1);
        assertTrue(tags.isEmpty());

        checkNoTagMappingInStorage(newTag1, host1, new Integer(vlan), dpid, 
                                   interfaceName);
        checkNoTagMappingInStorage(newTag1, host2, null, null, null);

        try {
            tagManager.mapTagToHost(newTag1, host1, new Short((short)10), 
                                    "00:00:00:00:00:00:00:01", "eth1");
        } catch (TagManagerException e){
            e.printStackTrace();
        }


        tags = tagManager.getTagsByEntityConfig(entity1);
        assertEquals(1, tags.size());
        checkTag(tags, newTag1);

        checkTagMappingInStorage(newTag1, host1, 10, "00:00:00:00:00:00:00:01", 
                                 "eth1");
        //checkNoTagMappingInStorage(newTag1, host2);

        try {
            tagManager.mapTagToHost(newTag1, host2, null, null, null);
        } catch (TagManagerException e){}


        tags = tagManager.getTagsByEntityConfig(entity2);
        assertEquals(1, tags.size());
        checkTag(tags, newTag1);

        //checkTagMappingInStorage(newTag1, host2);


        //tags = tagManager.getTagsByEntity(entity2);
        //assertEquals(1, tags.size());
        checkTag(tags, newTag1);

    }
    
    @Test
    public void testAddTagToDBDirect() {
        clearTagTable();
        BetterDeviceManagerImpl tagManager = getTagManager();
        
        TagManagerTest oneTagTest = new TagManagerTest();
        oneTagTest.addTag(tag1);
        oneTagTest.addEntityTagMappings(tag1Mapping);
        oneTagTest.writeToStorageTags(getStorageSource());
        oneTagTest.writeToStorageEntityMappings(getStorageSource());
        
        Set<Tag> tags = tagManager.getTagsByNamespace("sdnplatform.org");
        assertEquals(1, tags.size());
        tags = tagManager.getTags("sdnplatform.org", "tag1Name");
        assertEquals(1, tags.size());
        Tag myTag = tags.iterator().next();
        Tag referenceTag = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        checkTag(myTag, referenceTag);
        
        tags = tagManager.getTagsByEntityConfig(
                              new EntityConfig("00:00:00:00:00:01", "10", 
                                         "00:00:00:00:00:00:00:01", "eth1"));
        assertEquals(1, tags.size());
        checkTag(tags, referenceTag);
        
    }
    
    @Test
    public void testAddVlanToDBDirect() {
        clearTagTable();
        BetterDeviceManagerImpl tagManager = getTagManager();
        
        TagManagerTest oneTagTest = new TagManagerTest();
        oneTagTest.addTag(tag3);
        oneTagTest.addEntityTagMappings(tag3Mapping);
        oneTagTest.writeToStorageTags(getStorageSource());
        oneTagTest.writeToStorageEntityMappings(getStorageSource());
        
        Set<Tag> tags = tagManager.getTagsByNamespace("sdnplatform.org");
        assertEquals(1, tags.size());
        tags = tagManager.getTags("sdnplatform.org", "tag3Name");
        assertEquals(1, tags.size());
        Tag myTag = tags.iterator().next();
        Tag referenceTag = new Tag("sdnplatform.org", "tag3Name", "tag3Value");
        checkTag(myTag, referenceTag);
        
        tags = tagManager.getTagsByEntityConfig(new EntityConfig(null, 
                                                     "15", null, null));
        assertEquals(1, tags.size());
        checkTag(tags, referenceTag);
    }
    
    @Test
    public void testAddSwitchToDBDirect() {
        clearTagTable();
        BetterDeviceManagerImpl tagManager = getTagManager();
        

        
        TagManagerTest oneTagTest = new TagManagerTest();
        oneTagTest.addTag(tag4);
        oneTagTest.addEntityTagMappings(tag4Mapping);
        oneTagTest.writeToStorageTags(getStorageSource());
        oneTagTest.writeToStorageEntityMappings(getStorageSource());
        
        Set<Tag> tags = tagManager.getTagsByNamespace("sdnplatform.org");
        assertEquals(1, tags.size());
        tags = tagManager.getTags("sdnplatform.org", "tag4Name");
        assertEquals(1, tags.size());
        Tag myTag = tags.iterator().next();
        Tag referenceTag = new Tag("sdnplatform.org", "tag4Name", "tag4Value");
        checkTag(myTag, referenceTag);
        
        tags = tagManager.getTagsByEntityConfig(new EntityConfig(null, null, 
                                                     "00:00:00:00:00:00:00:02", 
                                                     null));
        assertEquals(1, tags.size());
        checkTag(tags, referenceTag);
    }
  
    
    @Test
    public void testAddSwitchInterfaceToDBDirect() {
        clearTagTable();
        BetterDeviceManagerImpl tagManager = getTagManager();
        
        TagManagerTest oneTagTest = new TagManagerTest();
        OFFeaturesReply reply = new OFFeaturesReply();
        List<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
        reply.setPorts(ports);
        OFPhysicalPort port1 = new OFPhysicalPort();
        port1.setName("eth1");
        port1.setPortNumber((short) 1);
        ports.add(port1);
        OFPhysicalPort port2 = new OFPhysicalPort();
        port2.setName("eth2");
        port2.setPortNumber((short) 2);
        ports.add(port2);
        OFPhysicalPort port3 = new OFPhysicalPort();
        port2.setName("eth3");
        port2.setPortNumber((short) 3);
        ports.add(port3);
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        for (OFPhysicalPort p: ports) {
            expect(mockSwitch.getPort(p.getName())).andReturn(p).anyTimes();
            expect(mockSwitch.getPort(p.getPortNumber()))
            .andReturn(p).anyTimes();
        }
        expect(mockSwitch.getId()).andReturn(4L).anyTimes();
        //expect(mockSwitch.getFeaturesReply()).andReturn(reply).anyTimes();
        
        HashMap<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, mockSwitch);
        mockControllerProvider.setSwitches(switches);
        replay(mockSwitch);
        oneTagTest.addTag(tag6);
        oneTagTest.addEntityTagMappings(tag6Mapping);
        oneTagTest.writeToStorageTags(getStorageSource());
        oneTagTest.writeToStorageEntityMappings(getStorageSource());
        
        Set<Tag> tags = tagManager.getTagsByNamespace("sdnplatform.org");
        assertEquals(1, tags.size());
        tags = tagManager.getTags("sdnplatform.org", "tag6Name");
        assertEquals(1, tags.size());
        Tag myTag = tags.iterator().next();
        Tag referenceTag = new Tag("sdnplatform.org", "tag6Name", "tag6Value");
        checkTag(myTag, referenceTag);
        
        tags = 
            tagManager.getTagsByEntityConfig(new EntityConfig(null, 
                                             "20", 
                                             "00:00:00:00:00:00:00:04","eth3"));
        assertEquals(1, tags.size());
        checkTag(tags, referenceTag);
    }
    
    
    @Test
    public void testRemoveTag1() {
        setupTest(twoTagTest);
        
        String host1 = "00:00:00:00:00:01";
        String dpid = "00:00:00:00:00:00:00:01";
        String interfaceName = "eth1";
        Short vlan = new Short((short)10);
        EntityConfig entity1 = new EntityConfig(host1, vlan.toString(), dpid, 
                                                interfaceName);
        String host2 = "00:00:00:00:00:02";
        new EntityConfig(host2, null, null, null);

               
        BetterDeviceManagerImpl tagManager = getTagManager();
        Tag myTag = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        try {
            tagManager.unmapTagToHost(myTag, host1, vlan, dpid, interfaceName);
        } catch (TagManagerException e) {
            fail("Failed to unmap tag " + myTag + " and host " + host1);
        }
        checkNoTagMappingInStorage(myTag, host1, new Integer(vlan), dpid, 
                                   interfaceName);
        
        
        Set<Tag> tags = tagManager.getTagsByEntityConfig(entity1);
        assertTrue(tags.isEmpty());
        
        
        try {
            tagManager.deleteTag(myTag);
        } catch (TagDoesNotExistException e) {
            fail("Failed to delete tag " + myTag);
        }
        Set<Tag> tags1 = tagManager.getTags("sdnplatform.org", "tag1Name");
        assertNull(tags1);
        checkNoTagInStorage(myTag);
    }
    
    @Test
    public void testRemoveTag2() {
        setupTest(threeTagTest);
        BetterDeviceManagerImpl tagManager = getTagManager();
        String host1 = "00:00:00:00:00:01";
        String dpid = "00:00:00:00:00:00:00:01";
        String interfaceName = "eth1";
        Short vlan = new Short((short)10);
        EntityConfig entity1 = new EntityConfig(host1, vlan.toString(), dpid, 
                                                interfaceName);
        
        Tag myTag1 = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        new Tag("sdnplatform.org", "tag3Name", "tag3Value");
        try {
            tagManager.unmapTagToHost(myTag1, host1, vlan, dpid, interfaceName);
        } catch (TagManagerException e) {
            fail("Failed to unmap tag " + myTag1 + " and host " + host1);
        }
        checkNoTagMappingInStorage(myTag1, host1, new Integer(vlan), dpid, 
                                   interfaceName);
        //checkTagMappingInStorage(myTag3, host1);

        Set<Tag> tags = tagManager.getTagsByEntityConfig(entity1);
        assertEquals(0, tags.size());
        
    }
    
    @Test
    public void testRemoveTagFromDBDirect() {
        setupTest(twoTagTest);
        BetterDeviceManagerImpl tagManager = getTagManager();
        Tag myTag = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        String host1 = "00:00:00:00:00:01";
        String dpid = "00:00:00:00:00:00:00:01";
        String interfaceName = "eth1";
        Short vlan = new Short((short)10);
        EntityConfig entity1 = new EntityConfig(host1, vlan.toString(), dpid, 
                                                interfaceName);
        
        String tagId = "sdnplatform.org|tag1Name|tag1Value";
        String tagMappingId = tagId + Tag.KEY_SEPARATOR + host1 + 
                Tag.KEY_SEPARATOR + vlan.toString() + Tag.KEY_SEPARATOR + dpid 
                + Tag.KEY_SEPARATOR + interfaceName;
        
        checkTagInStorage(myTag);
        checkTagMappingInStorage(myTag, host1, new Integer(vlan), dpid, 
                                 interfaceName);
        
        getStorageSource().deleteRow(BetterDeviceManagerImpl.TAG_TABLE_NAME, tagId);
        
        Set<Tag> tags = tagManager.getTags("sdnplatform.org", "tag1Name");
        assertNull(tags);
        checkNoTagInStorage(myTag);
        
        getStorageSource().deleteRow(BetterDeviceManagerImpl.TAGMAPPING_TABLE_NAME, 
                                     tagMappingId);

        tags = tagManager.getTagsByEntityConfig(entity1);
        assertTrue(tags.isEmpty());

        checkNoTagMappingInStorage(myTag, host1, new Integer(vlan), dpid, 
                                   interfaceName);
    }
    
    @Test
    public void testAddTagListener() {
        setupTest(oneTagTest);
        BetterDeviceManagerImpl tagManager = getTagManager();
        String host1 = "00:00:00:00:00:01";
        
        // Set up the listeners and record the expected notification
        ITagListener listener1 = createNiceMock(ITagListener.class);
        ITagListener listener2 = createNiceMock(ITagListener.class);
        
        Tag myTag = new Tag("sdnplatform.org", "tag2Name", "tag2Value");
        listener1.tagAdded(myTag);
        listener2.tagAdded(myTag);
        //listener1.tagHostMappingAdded(myTag, host1);
        //listener2.tagHostMappingAdded(myTag, host1);
        /*
        listener1.tagDevicesReMapped(
                             tagManager.queryDevices(HexString.toLong(host1), 
                                                     null, null, null, null));
        listener2.tagDevicesReMapped(
                             tagManager.queryDevices(HexString.toLong(host1), 
                                                     null, null, null, null));
        */
        
        replay(listener1, listener2);

        // Now try it for real
        tagManager.addListener(listener1);
        tagManager.addListener(listener2);

        // Update a new tag
        tagManager.addTag(myTag);
        try {
            tagManager.mapTagToHost(myTag, host1, null, null, null);
        } catch (TagManagerException e) {}
        
        verify(listener1); 
        verify(listener2);
        tagManager.removeAllListeners();
    }
    
    @Test
    public void testRemoveTagListener() {
        setupTest(twoTagTest);
        BetterDeviceManagerImpl tagManager = getTagManager();
        String host1 = "00:00:00:00:00:02";
        
        // Set up the listeners and record the expected notification
        ITagListener listener1 = createNiceMock(ITagListener.class);
        //ITagListener listener2 = createNiceMock(ITagListener.class);
        
        Tag myTag = new Tag("sdnplatform.org", "tag1Name", "tag1Value");
        listener1.tagDeleted(myTag);
        //listener2.tagDeleted(myTag);
        /*
        Iterator<? extends IDevice> devices = 
                tagManager.queryDevices(new Long(HexString.toLong(host1)), null,
                                        null, null, null);
        
        listener1.tagDevicesReMapped(devices);
        */
        
        
        replay(listener1);

        // Now try it for real
        tagManager.addListener(listener1);
        //tagManager.addListener(listener2);

        // Delete a tag
        try {
            tagManager.unmapTagToHost(myTag, host1, null, null, null);
            tagManager.deleteTag(myTag);
        } catch (TagManagerException e) {
            fail("deleteTag failed with exception " + e);
        }
        
        verify(listener1); 
        //verify(listener2);
    }
    
    /* A shim class to represent an HostSecurityAttachmentPoint row with helpers
     * to add/remove a row from storage. 
     */
    protected static class HostSecurityAttachmentPointRow {
        public HostSecurityAttachmentPointRow() {
            super();
        }
        public HostSecurityAttachmentPointRow(String addressSpace, Short vlan,
                                              Long mac, Long dpid, 
                                              String iface) {
            super();
            this.addressSpace = addressSpace;
            this.vlan = vlan;
            this.mac = mac;
            this.dpid = dpid;
            this.iface = iface;
        }
        public String addressSpace;
        public Long mac;
        public Short vlan;
        public Long dpid;
        public String iface;
        public void writeToStorage(IStorageSourceService storageSource) {
            ConcurrentHashMap<String, Object> row = 
                    new ConcurrentHashMap<String, Object>();
            String vlanString = (vlan==null) ? "" : vlan.toString();
            String hostConfig = addressSpace + "|" + vlanString + "|" +
                    HexString.toHexString(mac, 6);
            String dpidString = 
                    (dpid != null) ? HexString.toHexString(dpid, 8) : "\010";
            String id = hostConfig + "|" + dpidString + "|" + iface;
            row.put(BetterDeviceManagerImpl
                    .HOSTSECURITYATTACHMENTPOINT_ID_COLUMN_NAME, 
                   id);
            row.put(BetterDeviceManagerImpl
                    .HOSTSECURITYATTACHMENTPOINT_HOSTCONFIG_COLUMN_NAME, 
                    hostConfig);
            storageSource.insertRow(
                    BetterDeviceManagerImpl.HOSTSECURITYATTACHMENTPOINT_TABLE_NAME,
                    row);
        }
        
        public void removeFromStorage(IStorageSourceService storageSource) {
            String vlanString = (vlan==null) ? "" : vlan.toString();
            String hostConfig = addressSpace + "|" + vlanString + "|" +
                    HexString.toHexString(mac, 6);
            String dpidString = 
                    (dpid != null) ? HexString.toHexString(dpid, 8) : "\010";
            String id = hostConfig + "|" + dpidString + "|" + iface;
            storageSource.deleteRow(
                    BetterDeviceManagerImpl.HOSTSECURITYATTACHMENTPOINT_TABLE_NAME,
                    id);
        }
    }

    /* A shim class to represent an HostSecurityIpAddressRow with helpers
     * to add/remove a row from storage. 
     */
    protected static class HostSecurityIpAddressRow {
        public HostSecurityIpAddressRow() {
            super();
        }
        public HostSecurityIpAddressRow(String addressSpace, Short vlan, 
                                        Long mac, Integer ip) {
            super();
            this.addressSpace = addressSpace;
            this.vlan = vlan;
            this.mac = mac;
            this.ip = ip;
        }
        public String addressSpace;
        public Short vlan;
        public Long mac;
        public Integer ip;
        public void writeToStorage(IStorageSourceService storageSource) {
            ConcurrentHashMap<String, Object> row = 
                    new ConcurrentHashMap<String, Object>();
            String vlanString = (vlan==null) ? "" : vlan.toString();
            String hostConfig = addressSpace + "|" + vlanString + "|" +
                    HexString.toHexString(mac, 6);
            String ipString = IPv4.fromIPv4Address(ip);
            String id = hostConfig + "|" + ipString;
            row.put(BetterDeviceManagerImpl.HOSTSECURITYIPADDRESS_ID_COLUMN_NAME, 
                   id);
            row.put(BetterDeviceManagerImpl
                    .HOSTSECURITYIPADDRESS_HOSTCONFIG_COLUMN_NAME, 
                    hostConfig);
            row.put(BetterDeviceManagerImpl.HOSTSECURITYIPADDRESS_IP_COLUMN_NAME, 
                    ipString);
            storageSource.insertRow(
                    BetterDeviceManagerImpl.HOSTSECURITYIPADDRESS_TABLE_NAME,
                    row);
        }
        
        public void removeFromStorage(IStorageSourceService storageSource) {
            String vlanString = (vlan==null) ? "" : vlan.toString();
            String hostConfig = addressSpace + "|" + vlanString + "|" +
                    HexString.toHexString(mac, 6);
            String ipString = IPv4.fromIPv4Address(ip);
            String id = hostConfig + "|" + ipString;
            storageSource.deleteRow(
                    BetterDeviceManagerImpl.HOSTSECURITYIPADDRESS_TABLE_NAME, id);
        }
    }

    /* an IAnswer for responding to topology.isConsistent() calls. We return
     * true if both switch/ports are equal and false otherwise
     */
    protected static class IsConsistentAnswer implements IAnswer<Boolean> {
        @Override
        public Boolean answer() throws Throwable {
            Object[] args = getCurrentArguments();
            if (args[0].equals(args[2]) && args[1].equals(args[3]))
                return true;
            else 
                return false;
        }
    }

    /**
     * Verify of the given entities are allowed or not. Calls isEntityAllowed
     * for each entity that's passed in and verifies that it matches the 
     * expected value in the expected boolean array. 
     * "expecgted" is an array of expected values. We expect that 
     * isEntityAllowed(entities[i]) == expected[i] (in the given address space)
     * 
     * @param expected 
     * @param entities
     * @param entityClassName
     */
    public void verifyEntityAllowed(boolean[] expected, Entity[] entities,
                                    String entityClassName) {
        assertEquals("test setup error", expected.length, entities.length);
        for(int i = 0; i < expected.length; i++) {
            assertEquals("testing entity idx " + i,
                         expected[i],
                         isEntityAllowed(entities[i], entityClassName));
        }
    }

    public void setupTopology(IAnswer<Boolean> answer, 
                              boolean switch1IsAttachmentPoint) {
        reset(topology);
        topology.isConsistent(anyLong(), anyShort(), anyLong(), anyShort());
        expectLastCall().andAnswer(answer).anyTimes();
        topology.isAttachmentPointPort(eq(1L), anyShort());
        expectLastCall().andReturn(switch1IsAttachmentPoint).anyTimes();
        topology.isAttachmentPointPort(anyLong(), anyShort());
        expectLastCall().andReturn(true).anyTimes();
        replay(topology);
    }

    /**
     * A wrapper around BetterDeviceManager.isEntityAllowed (the method under 
     * test). This method just mocks an IEnityClass and its key fields according
     * to the current controller semantics (default AS has VLAN+MAC as key 
     * field, others have just MAC as key fields).
     * 
     * @param entity
     * @param entityClassName
     * @return
     */
    public boolean isEntityAllowed(Entity entity, String entityClassName) {
        IEntityClass ec = createMock(IEntityClass.class);
        expect(ec.getName()).andReturn(entityClassName).anyTimes();
        EnumSet<IDeviceService.DeviceField> keyFields;
        if (entityClassName.equals("default")) {
            keyFields = EnumSet.of(IDeviceService.DeviceField.VLAN,
                                   IDeviceService.DeviceField.MAC);
        } else { 
            keyFields = EnumSet.of(IDeviceService.DeviceField.MAC);
        }
        expect(ec.getKeyFields()).andReturn(keyFields).anyTimes();
        replay(ec);
        boolean rv = betterDeviceManager.isEntityAllowed(entity, ec);
        verify(ec);
        return rv;
    }
    
    
    /*
     * Test anti-spoofing protecting using only IP based rules. The test
     * successively adds (and potentially) removes IP based anti-spoofing
     * rules. 
     * 
     * We test against a set of three entities. 
     */
    @Test 
    public void testIsEntityAllowedIpOnly() {
        expect(topology.isAttachmentPointPort(anyLong(), anyShort()))
                .andReturn(true).anyTimes();
        
        replay(topology);        
        
        Entity e1 = new Entity(1L, null, 1, 1L, 1, null);
        Entity e2 = new Entity(1L, null, null, 1L, 1, null);
        Entity e3 = new Entity(2L, null, 1, 1L, 1, null);
        Entity[] entities = new Entity[] { e1, e2, e3 };
        // no rules. all entities allowed in all address-spaces. 
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "foobar");
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "AS1");
      
        // progressively populate ip to mac anti-spoofing rules for AS1.
        // we check address-space foobar to ensure it's unaffected by the rules.
        
        // lock IP2 to Mac 3 in AS1. Doesn't affect our entities 
        HostSecurityIpAddressRow r3 = 
                new HostSecurityIpAddressRow("AS1", null, 3L, 2);
        r3.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "foobar");
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "AS1");
        
        // lock IP1 to Mac 1 in AS1. Entity e3 violates this rule and 
        // should thus be disallowed. 
        HostSecurityIpAddressRow r4 = 
                new HostSecurityIpAddressRow("AS1", null, 1L, 1);
        r4.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "foobar");
        verifyEntityAllowed(new boolean[] { true, true, false}, 
                            entities, "AS1");
        r4.removeFromStorage(storageSource);
        
        // Lock Ip1 to Mac 3 in AS1. Not both e1 and e3 violate this rule. 
        HostSecurityIpAddressRow r5 = 
                new HostSecurityIpAddressRow("AS1", null, 3L, 1);
        r5.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "foobar");
        verifyEntityAllowed(new boolean[] { false, true, false}, 
                            entities, "AS1");
        
        // previous rule still in force. Now we also allow IP1 on Mac 1. 
        // e1 becomes valid again 
        HostSecurityIpAddressRow r6 = 
                new HostSecurityIpAddressRow("AS1", null, 1L, 1);
        r6.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "foobar");
        verifyEntityAllowed(new boolean[] { true, true, false}, 
                            entities, "AS1");
        
        // previous rules still in force. Now we also allow IP1 on Mac 2. 
        // e3 is valid again (i.e., all entities are now allowed)
        HostSecurityIpAddressRow r7 = 
                new HostSecurityIpAddressRow("AS1", null, 2L, 1);
        r7.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "foobar");
        verifyEntityAllowed(new boolean[] { true, true, true}, 
                            entities, "AS1");
    }
    
    
    
    @Test
    public void testIsEntityAllowedHost2SwitchPort() {
        IAnswer<Boolean> isConsistentAnswer = new IsConsistentAnswer();
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        ArrayList<OFPhysicalPort> sw1ports = new ArrayList<OFPhysicalPort>();
        ArrayList<OFPhysicalPort> sw2ports = new ArrayList<OFPhysicalPort>();
        for(int i=1; i<=2; i++) {
            OFPhysicalPort p = new OFPhysicalPort();
            p.setName("eth" + i);
            p.setPortNumber((short)i);
            expect(sw1.getPort(p.getName())).andReturn(p).anyTimes();
            sw1ports.add(p);
            
            p = new OFPhysicalPort();
            p.setName("port" + i);
            p.setPortNumber((short)(i));
            expect(sw2.getPort(p.getName())).andReturn(p).anyTimes();
            sw2ports.add(p);
        }
        // catch-all for ports we haven't specified
        expect(sw1.getPort(anyObject(String.class))).andStubReturn(null);
        expect(sw2.getPort(anyObject(String.class))).andStubReturn(null);
        expect(sw1.getEnabledPorts()).andReturn(sw1ports).anyTimes();
        expect(sw2.getEnabledPorts()).andReturn(sw2ports).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw2.getId()).andReturn(2L).anyTimes();
        ConcurrentHashMap<Long, IOFSwitch> switches = 
                new ConcurrentHashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        mockControllerProvider.setSwitches(switches);
        
        
        replay(sw1, sw2);
        
        // host1 switch1 port1
        Entity h1s1p1 = new Entity(1L, null, null, 1L, 1, null);
        // host1 switch1 port2
        Entity h1s1p2 = new Entity(1L, null, null, 1L, 2, null);
        // host1 switch2 port1
        Entity h1s2p1 = new Entity(1L, null, null, 2L, 1, null);
        // host2 switch2 port2
        Entity h2s2p2 = new Entity(2L, null, null, 2L, 2, null);
        Entity[] entities = new Entity[] { h1s1p1, h1s1p2, h1s2p1, h2s2p2 };
        
        // Test 0
        // no config. All should be allowed 
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "foobar");
        
        // Test 1
        // In address-space "foobar" allow MAC 1 on the following switch ports:
        //     sw1,p1(eth1), sw2,p2(port2)
        HostSecurityAttachmentPointRow r = 
                new HostSecurityAttachmentPointRow("foobar", null, 1L, 1L, "eth1");
        r.writeToStorage(storageSource);
        r = new HostSecurityAttachmentPointRow("foobar", null, 1L, 2L, "port1");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        verify(topology);
        
        // Test 2a
        // foobar is same as Test1
        // In address-space "AS1" allow MAC 2 on a non-existing switch 
        r = new HostSecurityAttachmentPointRow("AS1", null, 2L, 10L, "eth1");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, true, false},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        verify(topology);
        r.removeFromStorage(storageSource);
        
        // Test 2b
        // foobar is same as Test1
        // same as 2a but allow MAC 2 on existing switch 1 but on a 
        // non-existing port (we use a port name what would be valid on sw 2
        // though)
        // We just reuse and modify switchPorts !
        r = new HostSecurityAttachmentPointRow("AS1", null, 2L, 1L, "port1");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, true, false},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        verify(topology);
        
        // Test 2c
        // foobar is same as Test1
        // same as 2b but we finally allow MAC 2 on sw2/p2
        HostSecurityAttachmentPointRow r2;
        r2 = new HostSecurityAttachmentPointRow("AS1", null, 2L, 2L, "port2");
        r2.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        verify(topology);
        r.removeFromStorage(storageSource);
        r2.removeFromStorage(storageSource);
        
        
        // Test 4a
        // foobar is same as in Test1
        // AS1: Only allow MAC1 on sw1/eth1
        r = new HostSecurityAttachmentPointRow("AS1", null, 1L, 1L, "eth1");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, false, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        verify(topology);
        r.removeFromStorage(storageSource);
        
        // Test 4b
        // same as 2a but also allow MAC 1 on sw2/port1
        r = new HostSecurityAttachmentPointRow("AS1", null, 1L, 1L, "eth1");
        r.writeToStorage(storageSource);
        r2 = new HostSecurityAttachmentPointRow("AS1", null, 1L, 2L, "port1");
        r2.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        verify(topology);
        r.removeFromStorage(storageSource);
        r2.removeFromStorage(storageSource);
        
        // Test 5
        // foobar is same as in Test1
        // AS1: Only allow MAC1 on sw1/eth1. However, we declare all ports
        // on switch 1 as non-attachment point ports
        r = new HostSecurityAttachmentPointRow("AS1", null, 1L, 1L, "eth1");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, false);
        verifyEntityAllowed(new boolean[] {true, true, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "foobar");
        r.removeFromStorage(storageSource);
        verify(topology);
        
        
        // Test 6: regex on switch1: match all ports 
        r = new HostSecurityAttachmentPointRow("AS1", null, 1L, 1L, "eth.*");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        r.removeFromStorage(storageSource);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        // r is already removed
        verify(topology);
        
        // Test 7: regex all switches
        r = new HostSecurityAttachmentPointRow("AS1", null, 1L, null, ".*1");
        r.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        r.removeFromStorage(storageSource);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, true, true},
                            entities, "foobar");
        // r is already removed
        verify(topology);
        
        
        verify(sw1, sw2);
    }
    
    /*
     * Test spoofing protection in default address space with different VLANs
     */
    @Test
    public void testIsEntityAllowedVlan() {
        IAnswer<Boolean> isConsistentAnswer = new IsConsistentAnswer();
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        ArrayList<OFPhysicalPort> sw1ports = new ArrayList<OFPhysicalPort>();
        ArrayList<OFPhysicalPort> sw2ports = new ArrayList<OFPhysicalPort>();
        for(int i=1; i<=2; i++) {
            OFPhysicalPort p = new OFPhysicalPort();
            p.setName("eth" + i);
            p.setPortNumber((short)i);
            expect(sw1.getPort(p.getName())).andReturn(p).anyTimes();
            sw1ports.add(p);
            
            p = new OFPhysicalPort();
            p.setName("eth" + i);
            p.setPortNumber((short)(i));
            expect(sw2.getPort(p.getName())).andReturn(p).anyTimes();
            sw2ports.add(p);
        }
        // catch-all for ports we haven't specified
        expect(sw1.getPort(anyObject(String.class))).andStubReturn(null);
        expect(sw2.getPort(anyObject(String.class))).andStubReturn(null);
        expect(sw1.getEnabledPorts()).andReturn(sw1ports).anyTimes();
        expect(sw2.getEnabledPorts()).andReturn(sw2ports).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw2.getId()).andReturn(2L).anyTimes();
        ConcurrentHashMap<Long, IOFSwitch> switches = 
                new ConcurrentHashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        mockControllerProvider.setSwitches(switches);
        
        replay(sw1, sw2);
        
        Entity h1 = new Entity(1L, null, 1, 1L, 1, null);
        Entity h1vlan2 = new Entity(1L, (short)2, 1, 1L, 1, null);
        Entity h1vlan2sw2 = new Entity(1L, (short)2, 1, 2L, 1, null);
        Entity h2vlan2 = new Entity(2L, (short)2, 1, 1L, 1, null);
        Entity[] entities = new Entity[] { h1, h1vlan2, h1vlan2sw2, h2vlan2 };
        
        // Test 0
        // no config. All should be allowed 
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "default");
        verify(topology);
        
        
        // Test 1
        // Lock MAC 1 to nonexisting switch/port in AS1 and default. VLAN
        // should be ignored in AS1
        HostSecurityAttachmentPointRow r1 = 
                new HostSecurityAttachmentPointRow("AS1", null, 1L, 
                                                   0xffL, "eth1");
        HostSecurityAttachmentPointRow r2 = 
                new HostSecurityAttachmentPointRow("default", null, 1L,
                                                   0xffL, "eth1");
        r1.writeToStorage(storageSource);
        r2.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {false, false, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {false, true, true, true},
                            entities, "default");
        verify(topology);
        
        // Test 2: now lock MAC 1 to sw1-port1
        HostSecurityAttachmentPointRow r3 = 
                new HostSecurityAttachmentPointRow("AS1", (short)2, 1L, 
                                                   0x1L, "eth1");
        HostSecurityAttachmentPointRow r4 =
                new HostSecurityAttachmentPointRow("default", (short)2, 1L,
                                                   0x1L, "eth1");
        r3.writeToStorage(storageSource);
        r4.writeToStorage(storageSource);
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] {false, false, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {false, true, false, true},
                            entities, "default");
        verify(topology);
        
        // Clear storage
        r1.removeFromStorage(storageSource);
        r2.removeFromStorage(storageSource);
        r3.removeFromStorage(storageSource);
        r4.removeFromStorage(storageSource);
        
        // Test 3: Lock IP1 to MAC 2
        HostSecurityIpAddressRow ipRow1 = 
                new HostSecurityIpAddressRow("AS1", null, 2L, 1);
        HostSecurityIpAddressRow ipRow2 = 
                new HostSecurityIpAddressRow("default", null, 2L, 1);
        ipRow1.writeToStorage(storageSource);
        ipRow2.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] {false, false, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {false, false, false, false},
                            entities, "default");
        verify(topology);
            
        // Test 4: same as Test3 plus add: Lock IP1 to MAC 2, VLAN 2
        // Not setting for AS1 since using non-default AS + vlan is illegal
        HostSecurityIpAddressRow ipRow3 = 
                new HostSecurityIpAddressRow("default", (short)2, 2L, 1);
        ipRow3.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] {false, false, false, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {false, false, false, true},
                            entities, "default");
        verify(topology);
            
        // Test 5: Same as Test 4: add: Lock IP1 to MAC 1
        HostSecurityIpAddressRow ipRow4 = 
                new HostSecurityIpAddressRow("AS1", null, 1L, 1);
        HostSecurityIpAddressRow ipRow5 = 
                new HostSecurityIpAddressRow("default", null, 1L, 1);
        ipRow4.writeToStorage(storageSource);
        ipRow5.writeToStorage(storageSource);
        verifyEntityAllowed(new boolean[] {true, true, true, true},
                            entities, "AS1");
        verifyEntityAllowed(new boolean[] {true, false, false, true},
                            entities, "default");
        verify(topology);
            
        verify(sw1, sw2);
    }
    
    
    /**
     * Add the specified HostSecurityAttachmentPointRow to the storage source
     * and then verify how many rules betterDeviceManager has read into its 
     * matching structure (hostSecurityInterfaceRegexMap). This method is used
     * to check if out-of-bounds values are ignored when reading from storage. 
     * Will also remove the row after we are done. 
     * @param r row to write to storage
     * @param expectedEntries number of expected entries in matching structure
     * after row is added. 
     */
    public void doTestHostSecurityIpAddressRow(HostSecurityIpAddressRow r,
                                               int expectedEntries) {
        r.writeToStorage(storageSource);
        assertEquals(expectedEntries, betterDeviceManager.hostSecurityIpMap.size());
        r.removeFromStorage(storageSource);
        
    }
    
    /* Valid range check when reading HostSecurityIpAddress config
     * from storage. Valid vlans are 1--4095. Macs must be at most 6 bytes.
     */
    @Test
    public void testHostSecurityIpAddressVlanMacRange() {
        HostSecurityIpAddressRow ipRow; 
        
        // vlan 0: invalid. ignored.
        ipRow = new HostSecurityIpAddressRow("default", (short)0, 1L, 1);
        doTestHostSecurityIpAddressRow(ipRow, 0);
        
        // vlan 1: ok
        ipRow = new HostSecurityIpAddressRow("default", (short)1, 1L, 1);
        doTestHostSecurityIpAddressRow(ipRow, 1);
        
        // vlan 4095: ok
        ipRow = new HostSecurityIpAddressRow("default", (short)4095, 1L, 1);
        doTestHostSecurityIpAddressRow(ipRow, 1);
        
        // vlan 4096: invalid. ignored.
        ipRow = new HostSecurityIpAddressRow("default", (short)4096, 1L, 1);
        doTestHostSecurityIpAddressRow(ipRow, 0);
        
        // MAC is all "1". largest valid mac.
        ipRow = new HostSecurityIpAddressRow("default", (short)1,
                                             0xffffffffffffL, 1);
        doTestHostSecurityIpAddressRow(ipRow, 1);
        
        // MAC is more then 48 bit. Invalid and ignored. 
        ipRow = new HostSecurityIpAddressRow("default", (short)1,
                                             0x1000000000000L, 1);
        doTestHostSecurityIpAddressRow(ipRow, 0);
    }
    
    
    /**
     * Add the specified HostSecurityAttachmentPointRow to the storage source
     * and then verify how many rules betterDeviceManager has read into its 
     * matching structure (hostSecurityInterfaceRegexMap). This method is used
     * to check if out-of-bounds values are ignored when reading from storage. 
     * Will also remove the row after we are done. 
     * @param r row to write to storage
     * @param expectedEntries number of expected entries in matching structure
     * after row is added. 
     */
    public void doTestHostSecurityAttachmentPointRow(
            HostSecurityAttachmentPointRow r, int expectedEntries) {
        r.writeToStorage(storageSource);
        assertEquals(expectedEntries, 
                     betterDeviceManager.hostSecurityInterfaceRegexMap.size());
        r.removeFromStorage(storageSource);
        
    }
    
    /* Valid range check when reading HostSecurityAttachmentPoint config
     * from storage. Valid vlans are 1--4095. Macs must be at most 6 bytes.
     */
    @Test
    public void testHostSecurityAttachmentPointVlanMacRange() {
        HostSecurityAttachmentPointRow apRow; 
        
        // vlan 0: invalid. ignored
        apRow = new HostSecurityAttachmentPointRow("default", (short)0, 1L, 
                                                   1L, "eth1");
        doTestHostSecurityAttachmentPointRow(apRow, 0);
        
        // vlan 1: ok
        apRow = new HostSecurityAttachmentPointRow("default", (short)1, 1L, 
                                                   1L, "eth1");
        doTestHostSecurityAttachmentPointRow(apRow, 1);
        
        // vlan 4095: ok
        apRow = new HostSecurityAttachmentPointRow("default", (short)4095, 1L,
                                                   1L, "eth1");
        doTestHostSecurityAttachmentPointRow(apRow, 1);
        
        // vlan 4096: invalid. ignored
        apRow = new HostSecurityAttachmentPointRow("default", (short)4096, 1L,
                                                   1L, "eth1");
        doTestHostSecurityAttachmentPointRow(apRow, 0);
        
        // MAC is all "1". Still allowed. 
        apRow = new HostSecurityAttachmentPointRow("default", (short)1,
                                             0xffffffffffffL, 
                                             1L, "eth1");
        doTestHostSecurityAttachmentPointRow(apRow, 1);
        
        // MAC has more then 48 bits. Ignored. 
        apRow = new HostSecurityAttachmentPointRow("default", (short)1,
                                             0x1000000000000L, 
                                             1L, "eth1");
        doTestHostSecurityAttachmentPointRow(apRow, 0);
    }
        
    
    protected BetterDeviceManagerImpl getTagManager() {
        return betterDeviceManager;
    }
    
    protected IStorageSourceService getStorageSource() {
        return betterDeviceManager.getStorageSource();
    }
}
