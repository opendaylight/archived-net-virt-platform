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

package org.sdnplatform.tunnelmanager;

import static org.easymock.EasyMock.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPhysicalPort;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitch.OFPortType;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.ovsdb.IOVSDBManagerService;
import org.sdnplatform.storage.IStorageSourceService;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.tunnelmanager.TunnelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




@SuppressWarnings("unchecked")
public class TunnelManagerTest extends PlatformTestCase {
    protected static Logger logger = LoggerFactory.
                                        getLogger(TunnelManagerTest.class);
    private TunnelManager tm;
    private static final String SWITCH_CONFIG_TABLE_NAME =
        "controller_switchconfig";
    private static final String SWITCH_DPID = "dpid";
    private static final String TUNNEL_ENABLED_OR_NOT = "tunnel_termination";

    public TunnelManager getTunnelManager() {
        return tm;
    }
    private MemoryStorageSource stosrc;

    public static class TMTest {
        ArrayList<Map<String, Object>> swTunnInfoList =
            new ArrayList<Map<String, Object>>();

        public void addSwTunnInfo(Map<String, Object>... stis) {
            for (Map<String, Object> sti : stis) {
                swTunnInfoList.add(sti);
            }
        }

        public void writeToStorage(IStorageSourceService stosrc) {
            for(Map<String, Object> row : swTunnInfoList) {
                stosrc.insertRow(SWITCH_CONFIG_TABLE_NAME, row);
            }
        }

        public void removeFromStorage(IStorageSourceService stosrc, int i) {
            stosrc.deleteRow(SWITCH_CONFIG_TABLE_NAME, i);
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockThreadPoolService tp = new MockThreadPoolService();
        tp.init(null);
        TunnelManager.TUNNEL_TASK_DELAY = 0;
        tm = new TunnelManager();
        tm.setControllerProvider(getMockControllerProvider());
        tm.threadPool = tp;
        stosrc = new MemoryStorageSource();
        tm.setStorageSource(stosrc);
        Set<String> cols = new HashSet<String>();
        cols.add(SWITCH_DPID);
        cols.add(TUNNEL_ENABLED_OR_NOT);
        stosrc.createTable(SWITCH_CONFIG_TABLE_NAME, cols);

        /*
        stosrc = createNiceMock(IStorageSource.class);
        tm.setStorageSource(stosrc);
        stosrc.addListener(SWITCH_CONFIG_TABLE_NAME, tm);
        expectLastCall().atLeastOnce();
        */
        tm.startUp(null);
    }

    protected void setupIOFSwitchTunnelMode(IOFSwitch sw) {
        // These set of tests do not check for the tunnel-IP retrieval process
        // Nevertheless we let TunnelManager know what kind of process
        // the switch supports.
        expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_BSN_SET_TUNNEL_DST_ACTION, true))
                .andReturn(true).anyTimes();
        expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP, true))
                .andReturn(false).anyTimes();
    }

    protected void setupIOFSwitchWrite(IOFSwitch sw)  throws IOException {
        expect(sw.getNextTransactionId()).andReturn(42).anyTimes();
        sw.write(anyObject(List.class), anyObject(ListenerContext.class));
        expectLastCall().atLeastOnce();
    }

    protected void setupIOFSwitchPortType(IOFSwitch sw) {
        expect(sw.getPortType((short)4)).andReturn(OFPortType.TUNNEL_LOOPBACK).times(2);
        expect(sw.getPortType((short)3)).andReturn(OFPortType.TUNNEL).times(1);
        expect(sw.getPortType((short)1)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw.getPortType((short)2)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw.getPortType((short)0xfffe)).andReturn(OFPortType.NORMAL).anyTimes();
    }

    protected void setupAddRemoveIOFSwitchMocks(IOFSwitch sw1, IOFSwitch sw2,
                       IOFSwitch sw3, IOVSDBManagerService ovsdb,
                       IStorageSourceService stosrc) throws IOException {
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getEnabledPorts()).andReturn(getPorts(true)).times(1);
        setupIOFSwitchPortType(sw1);
        setupIOFSwitchTunnelMode(sw1);
        setupIOFSwitchWrite(sw1);

        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.getEnabledPorts()).andReturn(getPorts(true)).times(1);
        setupIOFSwitchPortType(sw2);
        setupIOFSwitchTunnelMode(sw2);
        setupIOFSwitchWrite(sw2);

        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.getEnabledPorts()).andReturn(getPorts(true)).times(1);
        setupIOFSwitchPortType(sw3);
        setupIOFSwitchTunnelMode(sw3);
        setupIOFSwitchWrite(sw3);

        ConcurrentHashMap<Long, IOFSwitch> switchmap =
                new ConcurrentHashMap<Long, IOFSwitch>();
        switchmap.put(1L, sw1);
        switchmap.put(2L, sw2);
        switchmap.put(3L, sw3);
        getMockControllerProvider().setSwitches(switchmap);
    }

    protected OFPhysicalPort getPort(String name) {
        for(OFPhysicalPort p : getPorts(true)) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    protected Collection<OFPhysicalPort> getPorts(boolean hasTunnel) {
        List<OFPhysicalPort> switchIntfs = new ArrayList<OFPhysicalPort>();

        OFPhysicalPort p;
        int numports = (hasTunnel) ? 5 : 3;
        for (short i=0; i<numports; i++) {
            p = new OFPhysicalPort();
            p.setConfig(0);
            p.setState(0);
            p.setCurrentFeatures(0xc0);
            p.setAdvertisedFeatures(0);
            p.setSupportedFeatures(0);
            p.setPeerFeatures(0);
            switch (i) {
                case 0: // eth1 uplink port
                    p.setPortNumber((short) 1);
                    byte[] hardwareAddress1 = new byte[]{
                                                  (byte)0xcc,0,0,0,0,(byte)1};
                    p.setHardwareAddress(hardwareAddress1);
                    p.setName("eth1");
                    break;
                case 1: // ovs-br0 LOCAL port
                    p.setPortNumber((short) 0xfffe);
                    byte[] hardwareAddress2 = new byte[]{
                                                  (byte)0xcc,0,0,0,0,(byte)2};
                    p.setHardwareAddress(hardwareAddress2);
                    p.setName("ovs-br0");
                    break;
                case 2: // a tap port for a connected VM
                    p.setPortNumber((short) 2);
                    byte[] hardwareAddress3 = new byte[]{
                                                  (byte)0xcc,0,0,0,0,(byte)3};
                    p.setHardwareAddress(hardwareAddress3);
                    p.setName("tap0");
                    break;
                case 3: // a GRE tunnel port
                    p.setPortNumber((short) 3);
                    byte[] hardwareAddress4 = new byte[]{
                                                         (byte)0xcc,0,0,0,0,(byte)4};
                    p.setHardwareAddress(hardwareAddress4);
                    p.setName("tun-bsn");
                    break;
                case 4: // a tunnel-endpoint port
                    p.setPortNumber((short) 4);
                    byte[] hardwareAddress5 = new byte[]{
                                                         (byte)0xcc,0,0,0,0,(byte)5};
                    p.setHardwareAddress(hardwareAddress5);
                    p.setName("tun-loopback");
                    break;
            }
            switchIntfs.add(p);
        }
        return switchIntfs;
    }

    //************************************************
    private static final TMTest emptyTest;
    static {
        emptyTest = new TMTest();
    }

    @Test
    public void testAddRemoveSwitch() throws IOException {
        logger.info("*** Starting test 1: testAddRemoveSwitch ***");

        emptyTest.writeToStorage(stosrc);
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        setupAddRemoveIOFSwitchMocks(sw1, sw2, sw3, ovsdb, stosrc);
        replay(sw1, sw2, sw3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));

        tm.removedSwitch(sw2);
    }

    //************************************************
    //enable a switch for tunneling
    private static final Map<String, Object> t1;
    static {
        t1 = new HashMap<String, Object>();
        t1.put(SWITCH_DPID, "00:00:00:00:00:00:00:01");
        t1.put(TUNNEL_ENABLED_OR_NOT, "enabled");
    }

    private static final TMTest basicTest1;
    static {
        basicTest1 = new TMTest();
        basicTest1.addSwTunnInfo(t1);
    }

    @Test
    public void testEnableSwitch() throws IOException {
        logger.info("*** Starting test 2: testEnableSwitch ***");
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        setupAddRemoveIOFSwitchMocks(sw1, sw2, sw3, ovsdb, stosrc);
        replay(sw1, sw2, sw3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);
        basicTest1.writeToStorage(stosrc);
        verify(sw1, sw2, sw3, ovsdb);

        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));


        //reset(sw1, sw2, sw3, ovsdb);
        //setupAddRemoveIOFSwitchMocks(sw1, sw2, sw3, ovsdb, stosrc);
        //replay(sw1, sw2, sw3, ovsdb);
        tm.removedSwitch(sw2);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
    }

    //************************************************
    // disable a switch for tunneling
    private static final Map<String, Object> t2;
    static {
        t2 = new HashMap<String, Object>();
        t2.put(SWITCH_DPID, "00:00:00:00:00:00:00:03");
        t2.put(TUNNEL_ENABLED_OR_NOT, "disabled");
    }

    private static final TMTest basicTest2;
    static {
        basicTest2 = new TMTest();
        basicTest2.addSwTunnInfo(t1, t2);
    }

    @Test
    public void testDisableSwitch() throws IOException {
        logger.info("*** Starting test 3: testDisableSwitch ***");
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        setupAddRemoveIOFSwitchMocks(sw1, sw2, sw3, ovsdb, stosrc);
        replay(sw1, sw2, sw3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);
        basicTest2.writeToStorage(stosrc);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(false, tm.outstandingTunnelIP.contains(3L));

        tm.removedSwitch(sw2);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(false, tm.outstandingTunnelIP.contains(3L));
        assertEquals(false, tm.tunnelCapableSwitches.containsKey(sw2.getId()));
    }

    //************************************************
    // testing transitions of configured state

    private static final Map<String, Object> kt1, kt2;
    static {
        kt1 = new HashMap<String, Object>();
        kt1.put(SWITCH_DPID, "00:00:00:00:00:00:00:01");
        kt1.put(TUNNEL_ENABLED_OR_NOT, "enabled");
        kt2 = new HashMap<String, Object>();
        kt2.put(SWITCH_DPID, "00:00:00:00:00:00:00:01");
        kt2.put(TUNNEL_ENABLED_OR_NOT, "disabled");
    }

    private static final TMTest kvmTest1;
    static {
        kvmTest1 = new TMTest();
        kvmTest1.addSwTunnInfo(kt1);
    }

    private static final TMTest kvmTest2;
    static {
        kvmTest2 = new TMTest();
        kvmTest2.addSwTunnInfo(kt2);
    }

    private static final TMTest kvmTest3;
    static {
        kvmTest3 = new TMTest();
        kvmTest3.addSwTunnInfo(kt2, kt1);
    }

    private static final TMTest kvmTest4;
    static {
        kvmTest4 = new TMTest();
        kvmTest4.addSwTunnInfo(kt1, kt2);
    }

    @Test
    public void testConfigTransitions() throws IOException {
        logger.info("*** Starting test 4: testConfigTransitions ***");
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        setupAddRemoveIOFSwitchMocks(sw1, sw2, sw3, ovsdb, stosrc);

        replay(sw1, sw2, sw3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);

        kvmTest1.writeToStorage(stosrc);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        assertEquals(3, tm.tunnelCapableSwitches.size());

        kvmTest1.removeFromStorage(stosrc, 1); //equivalent to "no tunnel termination"
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        verify(sw1, sw2, sw3, ovsdb);

        kvmTest2.writeToStorage(stosrc); // disabling switch
        assertEquals(false, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.tunnelCapableSwitches.containsKey(sw2.getId()));

        kvmTest2.removeFromStorage(stosrc, 2); //equivalent to "no tunnel termination"
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        verify(sw1, sw2, sw3, ovsdb);

        //disabled to enabled
        kvmTest3.writeToStorage(stosrc);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        assertEquals(3, tm.tunnelCapableSwitches.size());

        //enabled to disabled
        kvmTest4.writeToStorage(stosrc);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(false, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        assertEquals(3, tm.tunnelCapableSwitches.size());

    }

    //************************************************
    // testing transitions of configured state

    @Test
    public void testTunnelPortChangedOnSwitch() throws IOException {
        logger.info("*** Starting test 5: testTunnelPortChangedOnSwitch ***");

        emptyTest.writeToStorage(stosrc);
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        setupAddRemoveIOFSwitchMocks(sw1, sw2, sw3, ovsdb, stosrc);
        replay(sw1, sw2, sw3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);
        verify(sw1, sw2, sw3, ovsdb);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));

        //removing tunnel port
        reset(sw2);
        expect(sw2.getEnabledPorts()).andReturn(getPorts(false)).once();
        expect(sw2.getId()).andReturn(2L).once();
        // missing port type TUNNEL
        expect(sw2.getPortType((short)1)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw2.getPortType((short)2)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw2.getPortType((short)0xfffe)).andReturn(OFPortType.NORMAL).anyTimes();
        replay(sw2);
        tm.switchPortChanged(2L);
        verify(sw2) ;
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(false, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));
        assertEquals(2, tm.tunnelCapableSwitches.size());

        // re-enabling tunnel port while pretending that tunnel IPs for switches
        // 1 and 3 have already been learned
        reset(sw2);
        tm.outstandingTunnelIP.remove(1L);
        tm.outstandingTunnelIP.remove(3L);
        expect(sw2.getEnabledPorts()).andReturn(getPorts(true)).times(2);
        expect(sw2.getId()).andReturn(2L).anyTimes();
        // two calls to portType -- once in switchPortChanged
        // and the other in addedSwitch
        expect(sw2.getPortType((short)3)).andReturn(OFPortType.TUNNEL).times(2);
        expect(sw2.getPortType((short)4)).andReturn(OFPortType.TUNNEL_LOOPBACK).anyTimes();
        expect(sw2.getPortType((short)1)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw2.getPortType((short)2)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw2.getPortType((short)0xfffe)).andReturn(OFPortType.NORMAL).anyTimes();
        setupIOFSwitchTunnelMode(sw2);
        setupIOFSwitchWrite(sw2);
        replay(sw2);
        tm.switchPortChanged(2L);
        verify( sw2);
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(3, tm.tunnelCapableSwitches.size());
    }

}
