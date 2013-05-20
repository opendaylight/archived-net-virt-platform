/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sndplatform.adaptors.controllerservice;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.NodeConnector.NodeConnectorIDType;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IHAListener;
import org.sdnplatform.core.IInfoProvider;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchDriver;
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.RoleInfo;
import org.sdnplatform.packet.Ethernet;

public class ControllerServiceAdaptor implements IControllerService, IListenDataPacket {

    private Set<IOFMessageListener> packetInListeners = new CopyOnWriteArraySet<IOFMessageListener>();

    public PacketResult receiveDataPacket(RawPacket inPkt) {
        PacketResult result = PacketResult.IGNORED;

        // Let's only do this for OF nodeTypes... IOFMessageListener only understand OF
        if(packetInListeners.size() > 0 &&
            inPkt.getIncomingNodeConnector().getNode().getType() == Node.NodeIDType.OPENFLOW) {
            /*
             * Looked in org.sdnplatform.devicemanager.internal.DeviceManagerImpl.processPacketInMessage()
             * and it appears to only need the OFPacketIn.data and OFPacketIn.inport
             * additionally it seems to only need IOFSwitch.getID() and IOFSwitch.getStringID()
             */
            // Create a new OFPacketIn
            OFPacketIn in = new OFPacketIn();
            // Set its data
            in.setPacketData(inPkt.getPacketData());
            // Set its inport
            in.setInPort(toOFPort(inPkt.getIncomingNodeConnector()));

            // Create a new Ethernet and deserialize the packet data into it
            Ethernet eth = new Ethernet();
            eth.deserialize(in.getPacketData(), 0,
            in.getPacketData().length);
            // Add the Ethernet to the ListenerContext
            ListenerContext ctx = new ListenerContext();
            ctx.getStorage().put(IControllerService.CONTEXT_PI_PAYLOAD, eth);

            // Create a new OFSwitchAdaptor and set its id

            OFSwitchAdaptor sw = new OFSwitchAdaptor();
            sw.setId((Long) inPkt.getIncomingNodeConnector().getNode().getID());

            // Send OFPacketIn to all IOFMessageListeners
            for(IOFMessageListener packetInListener: this.packetInListeners) {
                packetInListener.receive(sw, in, ctx);
            }
            result = PacketResult.KEEP_PROCESSING;
        }

        return result;
    }

    private static short toOFPort(NodeConnector salPort) {
        if (salPort.getType().equals(NodeConnectorIDType.SWSTACK)) {
            return OFPort.OFPP_LOCAL.getValue();
        } else if (salPort.getType().equals(NodeConnectorIDType.HWPATH)) {
            return OFPort.OFPP_NORMAL.getValue();
        } else if (salPort.getType().equals(NodeConnectorIDType.CONTROLLER)) {
            return OFPort.OFPP_CONTROLLER.getValue();
        }
        return (Short) salPort.getID();
    }

    public void addOFMessageListener(OFType type, IOFMessageListener listener) {
        // TODO Finish Implementing this method
        if(type == OFType.PACKET_IN) {
            packetInListeners.add(listener);
        } else {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
    }

    public void removeOFMessageListener(OFType type, IOFMessageListener listener) {
        // TODO Finish Implementing this method
        if(type == OFType.PACKET_IN) {
            packetInListeners.remove(listener);
        } else {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
    }

    public void addHAListener(IHAListener arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void addInfoProvider(String arg0, IInfoProvider arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }



    public void addOFSwitchDriver(String arg0, IOFSwitchDriver arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void addOFSwitchListener(IOFSwitchListener arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<String, Object> getControllerInfo(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<String, String> getControllerNodeIPs() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<OFType, List<IOFMessageListener>> getListeners() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<String, Long> getMemory() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public BasicFactory getOFMessageFactory() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Role getRole() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public RoleInfo getRoleInfo() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<Long, IOFSwitch> getSwitches() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public long getSystemStartTime() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Long getUptime() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void handleOutgoingMessage(IOFSwitch arg0, OFMessage arg1,
            ListenerContext arg2) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean injectOfMessage(IOFSwitch arg0, OFMessage arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean injectOfMessage(IOFSwitch arg0, OFMessage arg1,
            ListenerContext arg2) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void removeHAListener(IHAListener arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void removeInfoProvider(String arg0, IInfoProvider arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void removeOFSwitchListener(IOFSwitchListener arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void run() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setAlwaysClearFlowsOnSwAdd(boolean arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setRole(Role arg0, String arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void terminate() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

}
