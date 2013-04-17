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

package org.sdnplatform.netvirt.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.ser.ToStringSerializer;
import org.sdnplatform.devicegroup.DeviceGroupBase;
import org.sdnplatform.devicemanager.IDevice;



/**
 * Representation of a Network Virtualization.  This will contain configuration
 * and state specific to a single NetVirt.
 * 
 * NOTE! This object can not store any state.
 * 
 * @author readams
 */
public class VNS extends DeviceGroupBase {
    // ARP & DHCP Managers will have to access these enums
    @JsonSerialize(using=ToStringSerializer.class)
    public enum DHCPMode {
        FLOOD_IF_UNKNOWN("flood-if-unknown"),   // discover DHCP server with broadcast
        STATIC("static"),
        L3_RELAY("relay"),
        ALWAYS_FLOOD("always-flood");
        
        private String value;
        DHCPMode(String v) {
            value = v;
        }
        
        @Override
        public String toString() {
            return value;
        }

        public static DHCPMode fromString(String str) {
            for (DHCPMode m : DHCPMode.values()) {
                if (m.value.equals(str)) {
                    return m;
                }
            }
            return DHCPMode.FLOOD_IF_UNKNOWN;
        }
    }
    
    @JsonSerialize(using=ToStringSerializer.class)
    public enum ARPMode {
        DROP_IF_UNKNOWN("drop-if-unknown"),    // drop if we don't know about the host
        FLOOD_IF_UNKNOWN("flood-if-unknown"),   // broadcast if we don't know about the host
        ALWAYS_FLOOD("always-flood");       // always broadcast
        
        private String value;
        ARPMode(String v) {
            value = v;
        }
        
        @Override
        public String toString() {
            return value;
        }

        public static ARPMode fromString(String str) {
            for (ARPMode m : ARPMode.values()) {
                if (m.value.equals(str)) {
                    return m;
                }
            }
            return ARPMode.FLOOD_IF_UNKNOWN;
        }
    }
    
    @JsonSerialize(using=ToStringSerializer.class)
    public enum BroadcastMode {
        DROP("drop"),
        FORWARD_TO_KNOWN("forward-to-known"),   // send to known hosts in NetVirt
        ALWAYS_FLOOD("always-flood");
        
        private String value;
        BroadcastMode(String v) {
            value = v;
        }
        
        @Override
        public String toString() {
            return value;
        }

        public static BroadcastMode fromString(String str) {
            for (BroadcastMode m : BroadcastMode.values()) {
                if (m.value.equals(str)) {
                    return m;
                }
            }
            return BroadcastMode.FORWARD_TO_KNOWN;
        }
    }
    
    // DHCP Manager related configuration
    protected DHCPMode dhcpMode = DHCPMode.FLOOD_IF_UNKNOWN;
    protected int dhcpIp; // If 0, we don't know the IP
    
    // ARP Manager related configuration
    protected ARPMode arpMode = ARPMode.FLOOD_IF_UNKNOWN;
    
    // Broadcast related configuration
    protected BroadcastMode broadcastMode = BroadcastMode.FORWARD_TO_KNOWN;

    // Address-Space this NetVirt belongs to.
    protected String addressSpaceName;
    
    // Devices known to this NetVirt, used for BroadcastMode.FORWARD_TO_KNOWN
    protected Set<Long> knownDevices;
    
    /* Used to garbage collect stale config data */
    protected boolean marked;
    
    /* Origin/creator name */
    private String origin;
    
        
    public VNS(String name) {
        super(name);
        dhcpIp = 0;
        knownDevices = 
                Collections.newSetFromMap(new ConcurrentHashMap<Long,Boolean>());
    }
    
    @JsonIgnore
    public boolean isMarked() {
        return marked;
    }
    
    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public void setDhcpManagerMode(DHCPMode mode) {
        this.dhcpMode = mode;
    }
    
    public DHCPMode getDhcpManagerMode() {
        return dhcpMode;
    }
    
    public void setAddressSpaceName(String addressSpaceName) {
        if (addressSpaceName == null || addressSpaceName.isEmpty()) {
            this.addressSpaceName = "default";
        } else {
            this.addressSpaceName = addressSpaceName;
        }
    }
    
    public String getAddressSpaceName() {
        if (addressSpaceName == null || addressSpaceName.isEmpty()) {
            this.addressSpaceName = "default";
        }
        return addressSpaceName;
    }
    
    public void setDhcpIp(int ip) {
        this.dhcpIp = ip;
    }
    
    public int getDhcpIp() {
        return dhcpIp;
    }
    
    public void setArpManagerMode(ARPMode mode) {
        this.arpMode = mode;
    }
    
    public ARPMode getArpManagerMode() {
        return this.arpMode;
    }
    
    public void setBroadcastMode(BroadcastMode mode) {
        this.broadcastMode = mode;
    }
    
    public BroadcastMode getBroadcastMode() {
        return this.broadcastMode;
    }

    @JsonIgnore
    public Set<Long> getKnownDevices() {
        return knownDevices;
    }
    
    public void addDevice(IDevice device) {
        knownDevices.add(device.getDeviceKey());
    }
    
    public void removeDevice(long deviceKey) {
        if (knownDevices.contains(deviceKey))
            knownDevices.remove(deviceKey);
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    public String getOrigin() {
        return this.origin;
    }
    
    public void removeAllDevices() {
        knownDevices.clear();
    }
    
    @Override
    public String toString() {
        return (name + 
                " [Active " + active + "]" + 
                " [Priority " + priority + "]");  
    }
}


