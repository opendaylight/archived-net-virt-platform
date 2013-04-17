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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.util.HexString;
import org.sdnplatform.IBetterOFSwitch;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitch.OFPortType;
import org.sdnplatform.core.test.MockThreadPoolService;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.ovsdb.IOVSDB;
import org.sdnplatform.ovsdb.IOVSDBManagerService;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.storage.memory.MemoryStorageSource;
import org.sdnplatform.test.PlatformTestCase;
import org.sdnplatform.tunnelmanager.TunnelManager;
import org.sdnplatform.tunnelmanager.TunnelManagerTest.TMTest;
import org.sdnplatform.util.MACAddress;
import org.sdnplatform.vendor.OFBigSwitchVendorData;
import org.sdnplatform.vendor.OFInterfaceIPReplyVendorData;
import org.sdnplatform.vendor.OFInterfaceVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class TunnelManagerPollTest extends PlatformTestCase{
    protected static Logger logger = LoggerFactory.
            getLogger(TunnelManagerPollTest.class);

    private TunnelManager tm;
    private static final String SWITCH_CONFIG_TABLE_NAME =
        "controller_switchconfig";
    private static final String SWITCH_DPID = "dpid";
    private static final String TUNNEL_ENABLED_OR_NOT = "tunnel_termination";

    public TunnelManager getTunnelManager() {
        return tm;
    }
    private MemoryStorageSource stosrc;



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

    protected OFFeaturesReply getFeaturesReply(long dpid) {
        OFFeaturesReply fr = new OFFeaturesReply();
        fr.setDatapathId(dpid);
        fr.setPorts((List<OFPhysicalPort>)getPorts(true));
        return fr;
    }

    protected void setupIOFSwitchPortType(IOFSwitch sw) {
        expect(sw.getPortType((short)4)).andReturn(OFPortType.TUNNEL_LOOPBACK).times(2);
        expect(sw.getPortType((short)3)).andReturn(OFPortType.TUNNEL).times(1);
        expect(sw.getPortType((short)1)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw.getPortType((short)2)).andReturn(OFPortType.NORMAL).anyTimes();
        expect(sw.getPortType((short)0xfffe)).andReturn(OFPortType.NORMAL).anyTimes();
    }

    protected void setupOneStandardOVSSwitchMock(IOFSwitch sw, long dpid) {
        expect(sw.getId()).andReturn(dpid).anyTimes();
        String dpidString = HexString.toHexString(dpid);
        expect(sw.getStringId()).andReturn(dpidString).anyTimes();
        expect(sw.getEnabledPorts()).andReturn(getPorts(true)).anyTimes();
        expect(sw.getPort(anyObject(String.class)))
                .andAnswer(new GetPortAnswer()).anyTimes();
        expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_BSN_SET_TUNNEL_DST_ACTION, true))
                .andReturn(false).anyTimes();
        expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP, true))
                .andReturn(true).anyTimes();
        setupIOFSwitchPortType(sw);
    }

    protected void setupStandardOVSSwitchMocks(IOFSwitch sw1, IOFSwitch sw2,
                                               IOFSwitch sw3) {
        setupOneStandardOVSSwitchMock(sw1, 1L);
        setupOneStandardOVSSwitchMock(sw2, 2L);
        setupOneStandardOVSSwitchMock(sw3, 3L);

        ConcurrentHashMap<Long, IOFSwitch> switchmap =
                new ConcurrentHashMap<Long, IOFSwitch>();
        switchmap.put(1L, sw1);
        switchmap.put(2L, sw2);
        switchmap.put(3L, sw3);
        getMockControllerProvider().setSwitches(switchmap);
    }

    @SuppressWarnings("unchecked")
    protected void setupOneBSNOVSSwitchMock(IOFSwitch sw, long dpid)
            throws IOException {
        expect(sw.getId()).andReturn(dpid).anyTimes();
        String dpidString = HexString.toHexString(dpid);
        expect(sw.getStringId()).andReturn(dpidString).anyTimes();
        expect(sw.getEnabledPorts()).andReturn(getPorts(true)).anyTimes();
        expect(sw.getPort(anyObject(String.class)))
                .andAnswer(new GetPortAnswer()).anyTimes();
        expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_BSN_SET_TUNNEL_DST_ACTION, true))
                .andReturn(true).anyTimes();
        expect(sw.attributeEquals(IBetterOFSwitch.SUPPORTS_OVSDB_TUNNEL_SETUP, true))
                .andReturn(false).anyTimes();
        expect(sw.getNextTransactionId()).andReturn(42).anyTimes();
        sw.write(anyObject(List.class), anyObject(ListenerContext.class));
        expectLastCall().atLeastOnce();
        setupIOFSwitchPortType(sw);
    }

    protected void setupBSNOVSSwitchMocks(IOFSwitch sw1, IOFSwitch sw2,
                                          IOFSwitch sw3) throws IOException {
        setupOneBSNOVSSwitchMock(sw1, 1L);
        setupOneBSNOVSSwitchMock(sw2, 2L);
        setupOneBSNOVSSwitchMock(sw3, 3L);

        ConcurrentHashMap<Long, IOFSwitch> switchmap =
                new ConcurrentHashMap<Long, IOFSwitch>();
        switchmap.put(1L, sw1);
        switchmap.put(2L, sw2);
        switchmap.put(3L, sw3);
        getMockControllerProvider().setSwitches(switchmap);
    }


    protected void setupIOFSwitchMocks(IOFSwitch sw1, IOFSwitch sw2,
                       IOFSwitch sw3) {

        sw1.setFeaturesReply(getFeaturesReply(1L));
        sw2.setFeaturesReply(getFeaturesReply(2L));
        sw3.setFeaturesReply(getFeaturesReply(3L));
        ConcurrentHashMap<Long, IOFSwitch> switchmap =
                new ConcurrentHashMap<Long, IOFSwitch>();
        switchmap.put(1L, sw1);
        switchmap.put(2L, sw2);
        switchmap.put(3L, sw3);
        getMockControllerProvider().setSwitches(switchmap);

    }

    protected void setupIOVSDBMocks(IOVSDB db1, IOVSDB db2, IOVSDB db3,
                               IOVSDBManagerService ovsdb) {
        ArrayList<IOVSDB> ovsdbs = new ArrayList<IOVSDB>();
        ovsdbs.add(db1);
        ovsdbs.add(db2);
        ovsdbs.add(db3);
    }

    public class GetPortAnswer implements IAnswer<OFPhysicalPort> {
        @Override
        public OFPhysicalPort answer() throws Throwable {
            Object[] args = getCurrentArguments();
            String portName = (String)args[0];
            for(OFPhysicalPort p : getPorts(true)) {
                if (p.getName().equals(portName)) return p;
            }
            return null;
        }
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
    public void testPollStandardOVSSwitch() {
        logger.info("*** Starting test 1: testPollStandardOVSSwitch ***");

        emptyTest.writeToStorage(stosrc);
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        IOVSDB db1 = createMock(IOVSDB.class);
        IOVSDB db2 = createMock(IOVSDB.class);
        IOVSDB db3 = createMock(IOVSDB.class);

        //setupIOFSwitchMocks(sw1, sw2, sw3);
        setupStandardOVSSwitchMocks(sw1, sw2, sw3);
        setupIOVSDBMocks(db1, db2, db3, ovsdb);

        // first time OVS setup and no IP address found
        expect(ovsdb.getOVSDB(1L)).andReturn(null).atLeastOnce();
        expect(ovsdb.getOVSDB(2L)).andReturn(null).atLeastOnce();
        expect(ovsdb.getOVSDB(3L)).andReturn(null).atLeastOnce();

        expect(ovsdb.addOVSDB(1L)).andReturn(db1).atLeastOnce();
        expect(ovsdb.addOVSDB(2L)).andReturn(db2).atLeastOnce();
        expect(ovsdb.addOVSDB(3L)).andReturn(db3).atLeastOnce();

        expect(db1.getTunnelIPAddress(false)).andReturn(null).anyTimes();
        expect(db2.getTunnelIPAddress(false)).andReturn(null).anyTimes();
        expect(db3.getTunnelIPAddress(false)).andReturn(null).anyTimes();

        replay(sw1, sw2, sw3);
        replay(db1, db2, db3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);
        verify(db1, db2, db3, ovsdb);

        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));

        assertEquals(false, tm.tunnelCapableSwitches.get(1L).active);
        assertEquals(false, tm.tunnelCapableSwitches.get(2L).active);
        assertEquals(false, tm.tunnelCapableSwitches.get(3L).active);

        assertEquals(0, tm.tunnelCapableSwitches.get(1L).ipv4Addr);
        assertEquals(0, tm.tunnelCapableSwitches.get(2L).ipv4Addr);
        assertEquals(0, tm.tunnelCapableSwitches.get(3L).ipv4Addr);

        assertNull(tm.tunnIPToDpid.get(16843009));
        assertNull(tm.tunnIPToDpid.get(33686018));
        assertNull(tm.tunnIPToDpid.get(50529027));

        // invalid mac and ip addresses
        IDevice noTunnelEndp1 = createMock(IDevice.class);
        expect(noTunnelEndp1.getMACAddress()).andReturn(100L).atLeastOnce();
        expect(noTunnelEndp1.getIPv4Addresses()).andReturn(
                new Integer[] {100, 200}).atLeastOnce();

        // valid tunnel-endpoint mac for a standard OVS switch
        IDevice tunnelEndp2 = createMock(IDevice.class);
        expect(tunnelEndp2.getMACAddress()).andReturn(
                Ethernet.toLong(new byte[] {(byte) 0xcc,0,0,0,0,2})).atLeastOnce();

        replay(noTunnelEndp1, tunnelEndp2);
        assertEquals(false, tm.isTunnelEndpoint(noTunnelEndp1));
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp2));

        assertEquals(null, tm.getTunnelIPAddr(1L));
        assertEquals(null, tm.getTunnelIPAddr(2L));
        assertEquals(null, tm.getTunnelIPAddr(3L));

        assertEquals(false, tm.isTunnelActiveByDpid(3L));
        assertEquals(false, tm.isTunnelActiveByDpid(2L));
        assertEquals(false, tm.isTunnelActiveByDpid(1L));
        assertEquals(false, tm.isTunnelActiveByIP(1));
        assertEquals(false, tm.isTunnelActiveByIP(168430090));

        assertEquals(3, tm.getTunnelPortNumber(1L).shortValue());
        assertEquals(3, tm.getTunnelPortNumber(2L).shortValue());
        assertEquals(3, tm.getTunnelPortNumber(3L).shortValue());

        verify(noTunnelEndp1, tunnelEndp2);
        verify(sw1, sw2, sw3);
    }


    @Test
    public void testPollStandardOVSSwitch2() {
        logger.info("*** Starting test 2: testPollStandardOVSSwitch 2***");

        emptyTest.writeToStorage(stosrc);
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        IOVSDB db1 = createMock(IOVSDB.class);
        IOVSDB db2 = createMock(IOVSDB.class);
        IOVSDB db3 = createMock(IOVSDB.class);

        setupStandardOVSSwitchMocks(sw1, sw2, sw3);
        setupIOVSDBMocks(db1, db2, db3, ovsdb);

        // next time IP address found
        expect(ovsdb.getOVSDB(1L)).andReturn(db1).atLeastOnce();
        expect(ovsdb.getOVSDB(2L)).andReturn(db2).atLeastOnce();
        expect(ovsdb.getOVSDB(3L)).andReturn(db3).atLeastOnce();

        expect(db1.getTunnelIPAddress(false)).andReturn("1.1.1.1").anyTimes();
        expect(db2.getTunnelIPAddress(false)).andReturn("2.2.2.2").anyTimes();
        expect(db3.getTunnelIPAddress(false)).andReturn("3.3.3.3").anyTimes();

        replay(sw1, sw2, sw3);
        replay(db1, db2, db3, ovsdb);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);
        verify(db1, db2, db3, ovsdb);

        assertEquals(false, tm.outstandingTunnelIP.contains(1L));
        assertEquals(false, tm.outstandingTunnelIP.contains(2L));
        assertEquals(false, tm.outstandingTunnelIP.contains(3L));

        assertEquals(true, tm.tunnelCapableSwitches.get(1L).active);
        assertEquals(true, tm.tunnelCapableSwitches.get(2L).active);
        assertEquals(true, tm.tunnelCapableSwitches.get(3L).active);

        assertEquals(16843009, tm.tunnelCapableSwitches.get(1L).ipv4Addr);
        assertEquals(33686018, tm.tunnelCapableSwitches.get(2L).ipv4Addr);
        assertEquals(50529027, tm.tunnelCapableSwitches.get(3L).ipv4Addr);

        assertEquals(Long.valueOf(1L), tm.tunnIPToDpid.get(16843009));
        assertEquals(Long.valueOf(2L), tm.tunnIPToDpid.get(33686018));
        assertEquals(Long.valueOf(3L), tm.tunnIPToDpid.get(50529027));

        //valid mac
        IDevice tunnelEndp1 = createMock(IDevice.class);
        expect(tunnelEndp1.getMACAddress()).andReturn(
                Ethernet.toLong(new byte[] {(byte) 0xcc,0,0,0,0,2})).atLeastOnce();

        // valid IP
        IDevice tunnelEndp2 = createMock(IDevice.class);
        expect(tunnelEndp2.getMACAddress()).andReturn(100L).atLeastOnce();
        expect(tunnelEndp2.getIPv4Addresses()).andReturn(
                new Integer[] {100, 16843009}).atLeastOnce();


        replay(tunnelEndp1, tunnelEndp2);
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp1));
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp2));

        assertEquals(16843009, tm.getTunnelIPAddr(1L).intValue());
        assertEquals(33686018, tm.getTunnelIPAddr(2L).intValue());
        assertEquals(50529027, tm.getTunnelIPAddr(3L).intValue());

        assertEquals(true, tm.isTunnelActiveByDpid(3L));
        assertEquals(true, tm.isTunnelActiveByDpid(2L));
        assertEquals(true, tm.isTunnelActiveByDpid(1L));
        assertEquals(false, tm.isTunnelActiveByIP(1));
        assertEquals(true, tm.isTunnelActiveByIP(16843009));

        assertEquals(3, tm.getTunnelPortNumber(1L).shortValue());
        assertEquals(3, tm.getTunnelPortNumber(2L).shortValue());
        assertEquals(3, tm.getTunnelPortNumber(3L).shortValue());

        verify(tunnelEndp1, tunnelEndp2);
        verify(sw1, sw2, sw3);

    }


    @Test
    public void testPollBSNOVSSwitch() throws IOException {
        logger.info("*** Starting test 3: testPollBSNOVSSwitch ***");

        emptyTest.writeToStorage(stosrc);
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        IOFSwitch sw3 = createMock(IOFSwitch.class);

        setupBSNOVSSwitchMocks(sw1, sw2, sw3);

        IOVSDBManagerService ovsdb = createNiceMock(IOVSDBManagerService.class);
        tm.setOVSDBManager(ovsdb);

        /*
        OFVendor ov = new OFVendor();
        ov.setLength((short)16);
        ov.setXid(1);
        ov.setVendor(OFBigSwitchVendorData.BSN_VENDOR_ID);
        OFInterfaceIPRequestVendorData vd = new OFInterfaceIPRequestVendorData();
        ov.setVendorData(vd);
        List<OFMessage> msgList = new ArrayList<OFMessage>(1);
        msgList.add(ov);
        Object[] m = msgList.toArray();
        expect(ch1.write(EasyMock.aryEq(m))).andReturn(null).atLeastOnce();
        */
        // expect the Interface IP request message

        replay(sw1, sw2, sw3);
        tm.addedSwitch(sw1);
        tm.addedSwitch(sw2);
        tm.addedSwitch(sw3);

        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(true, tm.outstandingTunnelIP.contains(3L));

        assertEquals(false, tm.tunnelCapableSwitches.get(1L).active);
        assertEquals(false, tm.tunnelCapableSwitches.get(2L).active);
        assertEquals(false, tm.tunnelCapableSwitches.get(3L).active);

        assertEquals(0, tm.tunnelCapableSwitches.get(1L).ipv4Addr);
        assertEquals(0, tm.tunnelCapableSwitches.get(2L).ipv4Addr);
        assertEquals(0, tm.tunnelCapableSwitches.get(3L).ipv4Addr);

        assertEquals(true, tm.sentIPRequests.containsKey(1L));
        assertEquals(true, tm.sentIPRequests.containsKey(2L));
        assertEquals(true, tm.sentIPRequests.containsKey(3L));

        //switch 1 receives empty list
        int xidsw1 = tm.sentIPRequests.get(1L).iterator().next();
        OFVendor ov1 = getReplyMsg(0, xidsw1, false, false);
        tm.receive(sw1, ov1, null);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(false, tm.tunnelCapableSwitches.get(1L).active);
        assertEquals(0, tm.tunnelCapableSwitches.get(1L).ipv4Addr);
        assertEquals(false, tm.sentIPRequests.get(1L).contains(xidsw1));

        //switch 2 receives more than one interface
        int xidsw2 = tm.sentIPRequests.get(2L).iterator().next();
        OFVendor ov2 = getReplyMsg(2, xidsw2, false, false);
        tm.receive(sw2, ov2, null);
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(false, tm.tunnelCapableSwitches.get(2L).active);
        assertEquals(0, tm.tunnelCapableSwitches.get(2L).ipv4Addr);
        assertEquals(false, tm.sentIPRequests.get(2L).contains(xidsw2));

        //switch 3 receives exactly one interface
        int xidsw3 = tm.sentIPRequests.get(3L).iterator().next();
        OFVendor ov3 = getReplyMsg(1, xidsw3, false, false);
        tm.receive(sw3, ov3, null);
        assertEquals(false, tm.outstandingTunnelIP.contains(3L));
        assertEquals(true, tm.tunnelCapableSwitches.get(3L).active);
        assertEquals(168430090, tm.tunnelCapableSwitches.get(3L).ipv4Addr);
        assertEquals(MACAddress.valueOf("0a:00:0a:00:0a:01"),
                     MACAddress.valueOf(tm.tunnelCapableSwitches.get(3L).macAddr));
        assertEquals(false, tm.sentIPRequests.get(3L).contains(xidsw3));
        assertEquals(3L, tm.tunnIPToDpid.get(168430090).longValue());

        // switch 1 receives unknown vendor
        if (tm.sentIPRequests.get(1L) != null)
            tm.sentIPRequests.get(1L).clear();
        tm.getTunnelIPFromOF(sw1);
        int xid2sw1 = tm.sentIPRequests.get(1L).iterator().next();
        OFVendor ov1b = getReplyMsg(1, xid2sw1, true, false);
        tm.receive(sw1, ov1b, null);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(false, tm.tunnelCapableSwitches.get(1L).active);
        assertEquals(0, tm.tunnelCapableSwitches.get(1L).ipv4Addr);
        assertEquals(true, tm.sentIPRequests.get(1L).contains(xid2sw1));

        // switch 2 receives unknown message sub-type
        if (tm.sentIPRequests.get(2L) != null)
            tm.sentIPRequests.get(2L).clear();
        tm.getTunnelIPFromOF(sw2);
        int xid2sw2 = tm.sentIPRequests.get(2L).iterator().next();
        OFVendor ov2b = getReplyMsg(1, xid2sw2, false, true);
        tm.receive(sw2, ov2b, null);
        assertEquals(true, tm.outstandingTunnelIP.contains(2L));
        assertEquals(false, tm.tunnelCapableSwitches.get(2L).active);
        assertEquals(0, tm.tunnelCapableSwitches.get(2L).ipv4Addr);
        assertEquals(true, tm.sentIPRequests.get(2L).contains(xid2sw2));

        // switch 1 receives permissions error message
        if (tm.sentIPRequests.get(1L) != null)
            tm.sentIPRequests.get(1L).clear();
        tm.getTunnelIPFromOF(sw1);
        int xid3sw1 = tm.sentIPRequests.get(1L).iterator().next();
        OFError err1 = new OFError();
        err1.setXid(xid3sw1);
        err1.setErrorType(OFErrorType.OFPET_BAD_REQUEST.getValue());
        err1.setErrorCode(OFError.OFBadRequestCode.OFPBRC_EPERM);
        tm.receive(sw1, err1, null);
        assertEquals(true, tm.outstandingTunnelIP.contains(1L));
        assertEquals(false, tm.tunnelCapableSwitches.get(1L).active);
        assertEquals(0, tm.tunnelCapableSwitches.get(1L).ipv4Addr);
        assertEquals(false, tm.sentIPRequests.get(1L).contains(xid3sw1));

        //check API calls

        // invalid mac and ip addresses
        IDevice noTunnelEndp1 = createMock(IDevice.class);
        expect(noTunnelEndp1.getMACAddress()).andReturn(100L).atLeastOnce();
        expect(noTunnelEndp1.getIPv4Addresses()).andReturn(
                new Integer[] {100, 200}).atLeastOnce();

        // valid mac but for a standard OVS switch - does not apply to BSN-OVS
        IDevice noTunnelEndp2 = createMock(IDevice.class);
        expect(noTunnelEndp2.getMACAddress()).andReturn(
                Ethernet.toLong(new byte[] {(byte) 0xcc,0,0,0,0,2})).atLeastOnce();
        expect(noTunnelEndp2.getIPv4Addresses()).andReturn(
                new Integer[] {100, 200}).atLeastOnce();

        // valid mac
        IDevice tunnelEndp1 = createMock(IDevice.class);
        expect(tunnelEndp1.getMACAddress()).andReturn(
                Ethernet.toLong(new byte[] {(byte)0xa,0,(byte)0xa,0,(byte)0xa,(byte)0x1}))
                    .atLeastOnce();

        // valid IP
        IDevice tunnelEndp2 = createMock(IDevice.class);
        expect(tunnelEndp2.getMACAddress()).andReturn(100L).atLeastOnce();
        expect(tunnelEndp2.getIPv4Addresses()).andReturn(
                new Integer[] {100, 168430090}).atLeastOnce();

        replay(noTunnelEndp1, noTunnelEndp2, tunnelEndp1, tunnelEndp2);
        assertEquals(false, tm.isTunnelEndpoint(noTunnelEndp1));
        assertEquals(false, tm.isTunnelEndpoint(noTunnelEndp2));
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp1));
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp2));
        tm.tunnelCapableSwitches.get(3L).active = false;
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp1));
        assertEquals(true, tm.isTunnelEndpoint(tunnelEndp2));
        tm.tunnelCapableSwitches.get(3L).active = true;

        assertEquals(null, tm.getTunnelIPAddr(1L));
        assertEquals(null, tm.getTunnelIPAddr(2L));
        assertEquals(168430090, tm.getTunnelIPAddr(3L).intValue());
        tm.tunnelCapableSwitches.get(3L).active = false;
        assertEquals(null, tm.getTunnelIPAddr(3L));
        tm.tunnelCapableSwitches.get(3L).active = true;

        assertEquals(true, tm.isTunnelActiveByDpid(3L));
        assertEquals(false, tm.isTunnelActiveByDpid(2L));
        assertEquals(false, tm.isTunnelActiveByDpid(1L));
        assertEquals(false, tm.isTunnelActiveByIP(1));
        assertEquals(true, tm.isTunnelActiveByIP(168430090));

        assertEquals(3, tm.getTunnelPortNumber(1L).shortValue());
        assertEquals(3, tm.getTunnelPortNumber(2L).shortValue());
        assertEquals(3, tm.getTunnelPortNumber(3L).shortValue());

        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.10.128")));
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.10.0")));
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.10.1")));
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.10.255")));
        assertEquals(false, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.11.0")));
        assertEquals(false, tm.isTunnelSubnet(IPv4.toIPv4Address("0.0.0.0")));
        assertEquals(false, tm.isTunnelSubnet(IPv4.toIPv4Address("255.255.255.0")));

        assertEquals(1, tm.tunnelIPSubnets.size());
        int e = IPv4.toIPv4Address("255.255.255.0") & IPv4.toIPv4Address("10.10.10.13");
        tm.tunnelIPSubnets.get(IPv4.toIPv4Address("255.255.255.0")).add(e);
        // same subnet as 10.10.10.10  - should not add to mask->set
        assertEquals(1, tm.tunnelIPSubnets.size());
        assertEquals(1, tm.tunnelIPSubnets.get(IPv4.toIPv4Address("255.255.255.0")).size());

        int ex = IPv4.toIPv4Address("255.255.255.0") & IPv4.toIPv4Address("10.10.11.13");
        tm.tunnelIPSubnets.get(IPv4.toIPv4Address("255.255.255.0")).add(ex);
        assertEquals(1, tm.tunnelIPSubnets.size());
        assertEquals(2, tm.tunnelIPSubnets.get(IPv4.toIPv4Address("255.255.255.0")).size());
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.10.128")));
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.11.128")));
        assertEquals(false, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.12.128")));

        Set<Integer> s = Collections.newSetFromMap(
                             new ConcurrentHashMap<Integer,Boolean>());
        int tex = IPv4.toIPv4Address("255.255.0.0") & IPv4.toIPv4Address("10.10.11.13");
        s.add(tex);
        tm.tunnelIPSubnets.put(IPv4.toIPv4Address("255.255.0.0"), s);
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.10.128")));
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.11.128")));
        assertEquals(true, tm.isTunnelSubnet(IPv4.toIPv4Address("10.10.12.128")));


        verify(noTunnelEndp1, noTunnelEndp2, tunnelEndp1, tunnelEndp2);

        // api calls after switch removal

        // mac
        IDevice tunnelEndp5 = createMock(IDevice.class);
        expect(tunnelEndp5.getMACAddress()).andReturn(
                Ethernet.toLong(new byte[] {(byte)0xa,0,(byte)0xa,0,(byte)0xa,(byte)0x1}))
                    .atLeastOnce();
        expect(tunnelEndp5.getIPv4Addresses()).andReturn(
                new Integer[] {100, 168430090}).atLeastOnce();

        // IP
        IDevice tunnelEndp6 = createMock(IDevice.class);
        expect(tunnelEndp6.getMACAddress()).andReturn(100L).atLeastOnce();
        expect(tunnelEndp6.getIPv4Addresses()).andReturn(
                new Integer[] {100, 168430090}).atLeastOnce();

        replay(tunnelEndp5, tunnelEndp6);
        tm.removedSwitch(sw3);
        assertEquals(null, tm.getTunnelIPAddr(3L));
        assertEquals(false, tm.tunnelCapableSwitches.containsKey(sw3.getId()));
        assertEquals(false, tm.isTunnelEndpoint(tunnelEndp5));
        assertEquals(false, tm.isTunnelEndpoint(tunnelEndp6));
        assertEquals(null, tm.getTunnelPortNumber(3L));
        assertEquals(false, tm.isTunnelActiveByDpid(3L));
        assertEquals(false, tm.isTunnelActiveByIP(168430090));
        verify(tunnelEndp5, tunnelEndp6);

        verify(sw1, sw2, sw3);
    }

    private OFVendor getReplyMsg(int numIntfs, int xid, boolean badVendor,
                                 boolean badSubtype) {
        OFVendor ov = new OFVendor();
        ov.setLength((short)16);
        ov.setXid(xid);
        if (badVendor) {
            ov.setVendor(0);
        } else {
            ov.setVendor(OFBigSwitchVendorData.BSN_VENDOR_ID);
        }
        OFInterfaceIPReplyVendorData vd = new OFInterfaceIPReplyVendorData();
        if (badSubtype) {
            vd.setDataType(100);
        }
        List<OFInterfaceVendorData> intfs = new ArrayList<OFInterfaceVendorData>();

        OFInterfaceVendorData intf = new OFInterfaceVendorData();
        intf.setName("tun-loopback");
        intf.setHardwareAddress(new byte[] {10,0,10,0,10,1});
        intf.setIpv4Addr(168430090); // 10.10.10.10
        intf.setIpv4AddrMask(IPv4.toIPv4Address("255.255.255.0"));

        if (numIntfs == 1) {
            intfs.add(intf);
        } else if (numIntfs > 1) {
            intfs.add(intf);
            intfs.add(intf);
        }
        vd.setInterfaces(intfs);
        ov.setVendorData(vd);
        return ov;
    }

}
