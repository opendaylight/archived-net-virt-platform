/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.sndplatform.adaptors.controllerservice;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;

import org.jboss.netty.channel.Channel;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;
import org.sdnplatform.core.IControllerService.Role;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.internal.Controller;
import org.sdnplatform.threadpool.IThreadPoolService;

public class OFSwitchAdaptor implements IOFSwitch {

    private long id = 0;

    public long getId() {
        return this.id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public void setId(Long id) {
        this.id = id.longValue();
    }

    public boolean attributeEquals(String arg0, Object arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void cancelAllStatisticsReplies() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void cancelFeaturesReply(int arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void cancelStatisticsReply(int arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");

    }

    public void clearAllFlowMods() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");

    }

    public void deletePort(short arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");

    }

    public void deletePort(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");

    }

    public void deliverOFFeaturesReply(OFMessage arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");

    }

    public void deliverStatisticsReply(OFMessage arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void disconnectOutputStream() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void flush() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public int getActions() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Object getAttribute(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<Object, Object> getAttributes() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public int getBuffers() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public int getCapabilities() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Date getConnectedSince() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Collection<Short> getEnabledPortNumbers() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Collection<OFPhysicalPort> getEnabledPorts() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Role getHARole() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public SocketAddress getInetAddress() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Lock getListenerReadLock() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Lock getListenerWriteLock() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public int getNextTransactionId() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public OFPhysicalPort getPort(short arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public OFPhysicalPort getPort(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Map<Short, Long> getPortBroadcastHits() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public OFPortType getPortType(short arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Collection<OFPhysicalPort> getPorts() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Future<List<OFStatistics>> getStatistics(OFStatisticsRequest arg0)
            throws IOException {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public String getStringId() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public byte getTables() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public List<Short> getUplinkPorts() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean hasAttribute(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean isConnected() {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean isFastPort(short arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean portEnabled(short arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean portEnabled(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean portEnabled(OFPhysicalPort arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Future<OFFeaturesReply> querySwitchFeaturesReply()
            throws IOException {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Object removeAttribute(String arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void sendStatsQuery(OFStatisticsRequest arg0, int arg1,
            IOFMessageListener arg2) throws IOException {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setAttribute(String arg0, Object arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setChannel(Channel arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setConnected(boolean arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setControllerProvider(Controller arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setFeaturesReply(OFFeaturesReply arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setHARole(Role arg0, boolean arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setPort(OFPhysicalPort arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setSwitchProperties(OFDescriptionStatistics arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void setThreadPoolService(IThreadPoolService arg0) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean updateBroadcastCache(Long arg0, Short arg1) {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void write(OFMessage arg0, ListenerContext arg1) throws IOException {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public void write(List<OFMessage> arg0, ListenerContext arg1)
            throws IOException {
        // TODO Finish Implementing this method
        throw new UnsupportedOperationException("Not implemented yet.");
    }

}
