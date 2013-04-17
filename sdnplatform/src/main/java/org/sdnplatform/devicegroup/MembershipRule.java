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

package org.sdnplatform.devicegroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.packet.Ethernet;


/**
 * Represents a DeviceGroup membership rule (e.g., NetVirt interface rule) that 
 * defines membership of a define in a device group.
 * @author readams
 */
public class MembershipRule<T extends IDeviceGroup> 
        implements Comparable<MembershipRule<T>> {
    protected T parentDeviceGroup;
    
    protected String name;
    protected static final String UNTAGGED_VLAN_NAME = "untagged";
    /*
     * Non-unique name of this rule.
     */
    protected String ruleName;
    protected String friendlyName;
    protected String description;
    protected int priority;

    protected boolean multipleAllowed;
    protected boolean vlanTagOnEgress;
    protected boolean active;
    
    protected String mac;
    protected String ipSubnet;
    protected String switchId;
    protected String ports;
    protected List<String> portList; // exploded unmodifiable list of ports
    protected String vlans;
    protected List<Integer> vlanList; // exploded unmodifiable list of vlans
    protected String tags;
    protected List<String> tagList; // exploded unmodifiable list of tags
    
    /* Used to garbage collect stale config data */
    protected boolean marked;
    
    /**
     * @param name This membership rule's unique name. 
     * @param parentDeviceGroup the parent of the interface rule
     */
    public MembershipRule(String name, T parentDeviceGroup) {
        super();
        this.setName(name);
        this.setParentDeviceGroup(parentDeviceGroup);
    }
    
    @JsonIgnore
    public T getParentDeviceGroup() {
        return parentDeviceGroup;
    }
    public void setParentDeviceGroup(T parentDeviceGroup) {
        if (parentDeviceGroup == null)
            throw new NullPointerException("parentDeviceGroup cannot be null");
        this.parentDeviceGroup = parentDeviceGroup;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        if (name == null)
            throw new NullPointerException("name cannot be null");
        this.name = name;
        this.friendlyName = name.replaceAll("[^|]*\\|", "");        
    }
    public String getRuleName() {
        return ruleName;
    }
    public void setRuleName(String ruleName) {
        if (ruleName == null)
            throw new NullPointerException("ruleName cannot be null");
        this.ruleName = ruleName;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isMultipleAllowed() {
        return multipleAllowed;
    }

    public void setMultipleAllowed(boolean multipleAllowed) {
        this.multipleAllowed = multipleAllowed;
    }

    public boolean getVlanTagOnEgress() {
        return vlanTagOnEgress;
    }

    public void setVlanTagOnEgress(boolean vlanTagOnEgress) {
        this.vlanTagOnEgress = vlanTagOnEgress;
    }

    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMac() {
        return mac;
    }

    static final protected Pattern macPattern =
        Pattern.compile("([A-Fa-f\\d]{2}:?){5}[A-Fa-f\\d]{2}");
    
    public void setMac(String mac) throws IllegalArgumentException {
        if (mac != null) {
            Matcher m = macPattern.matcher(mac);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid mac: " + mac);
            }
            this.mac = mac.toLowerCase();
        }
        else {
            this.mac = null;
        }
    }

    public String getIpSubnet() {
        return ipSubnet;
    }

    static final protected Pattern ipSubnetPattern =
        Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}/\\d{1,2}?");
    
    public void setIpSubnet(String ipSubnet) throws IllegalArgumentException {
        if (ipSubnet != null) {
            Matcher m = ipSubnetPattern.matcher(ipSubnet);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid ip subnet: " + ipSubnet);
            }
        }
        this.ipSubnet = ipSubnet;
    }

    public String getSwitchId() {
        return switchId;
    }

    static final protected Pattern switchIdPattern =
        Pattern.compile("([A-Fa-f\\d]{2}:?){7}[A-Fa-f\\d]{2}");
    
    public void setSwitchId(String switchId) throws IllegalArgumentException {
        if (switchId != null) {
            Matcher m = switchIdPattern.matcher(switchId);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid switchId: " + switchId);
            }
            this.switchId = switchId.toLowerCase();
        }
        else {
            this.switchId = null;
        }
    }

    /**
     * Returns the ports to match in original form. I.e., a string specifying
     * port ranges and enumerations
     * @return
     */
    public String getPorts() {
        return ports;
    }

    static final protected Pattern portRangePattern = 
        Pattern.compile("([A-Za-z0-9-\\.:/]*?)(\\d+)-(\\d+)");

    /**
     * Explode the port rule into a list of real port names
     * @return a (possibly-null) unmodifiable list of port names
     */    
    @JsonIgnore
    public List<String> getPortList() {
        return this.portList;
    }
    
    /**
     * Set the ports to match on for this rule. Requires that a switchId is 
     * set as well. Used the port *name* for matching. Supports ranges and 
     * enumerations: 
     *    GigabitEthernet1-6,Foobar2,Foobar3
     * 
     * @param ports
     * @throws IllegalArgumentException of the string is not a valid 
     * port list
     */
    public void setPorts(String ports) throws IllegalArgumentException {
        if (ports == null) {
            this.portList = null;
            this.ports = null;
            return;
        }
                
        String[] portranges = ports.split(",");
        // list might end up being larger than portranges.size
        List<String> portl = new ArrayList<String>(portranges.length);
        for (String p : portranges) {
            Matcher m = portRangePattern.matcher(p);
            
            if (m.matches()) {
                try {
                    int start = Integer.parseInt(m.group(2));
                    int end = Integer.parseInt(m.group(3));
                    String prefix = m.group(1);
                    for (int i = start; i <= end && i>=0; i++) {
                        portl.add(prefix + i);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                           "Could not process port range pattern " + ports);
                }
            } else {
                portl.add(p);
            }
        }        
        this.ports = ports;
        if (portl.size() == 0) 
            this.portList = null;
        else
            this.portList = Collections.unmodifiableList(portl);
    }

    /**
     * Returns the vlans to match as the original string
     * @return
     */
    public String getVlans() {
        return vlans;
    }
    
    static final protected Pattern vlanRangePattern = 
        Pattern.compile("(\\d+)-(\\d+)");
    /**
     * Explode the vlans rule into a list of vlan IDs 
     * @return a (possibly-null) unmodifiable list of vlans
     */
    @JsonIgnore
    public List<Integer> getVlanList() {
        return this.vlanList;
    }
    
    /**
     * Set the vlans to match on for this rule. 
     * Supports ranges and enumerations: 
     *     1-5,10,23-42,4242 
     * 
     * @param vlans
     * @throws IllegalArgumentException of the string is not a valid 
     * vlan list
     * TODO: should we fail if a specified vlan is out of bounds?  
     *  Right now we silently ignore it!
     */
    public void setVlans(String vlans) throws IllegalArgumentException {
        if (vlans == null) {
            this.vlans = null;
            this.vlanList = null;
            return;
        }
        String[] vlanranges = vlans.split(",");
        List<Integer> vlanl = new ArrayList<Integer>(vlanranges.length);
        for (String p : vlanranges) {
            Matcher m = vlanRangePattern.matcher(p);
            
            if (m.matches()) {
                try {
                    int start = Integer.parseInt(m.group(1));
                    int end = Integer.parseInt(m.group(2));
                    if (start < 0) start = 0;
                    if (end > 4095) end = 4095;
                    for (int i = start; i <= end; i++) {
                        vlanl.add(i);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Could not process port "
                            + "range pattern " + ports);
                }
            } else if (p.contentEquals(UNTAGGED_VLAN_NAME)) {
                int vlanInt = Ethernet.VLAN_UNTAGGED;
                vlanInt = vlanInt & 0xffff;
                vlanl.add(new Integer(vlanInt));
            } else {
                try {
                    int vlan = Integer.parseInt(p);
                    if (vlan >= 0 && vlan <= 4095)
                        vlanl.add(vlan);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid vlan id " + p);
                }
            }
        }
        this.vlans = vlans;
        if (vlanl.size() == 0) 
            this.vlanList = null;
        else
            this.vlanList = Collections.unmodifiableList(vlanl);
    }

    /**
     * Return tags to match for this rule as specified by setTags 
     * @return
     */
    public String getTags() {
        return tags;
    }
    
    static final protected Pattern tagsPattern = 
        Pattern.compile("([\\.a-zA-Z0-9_-]*)=([a-zA-Z0-9_-]+)");
    /**
     * Explode the tags rule into a list of tags 
     * @return a (possibly-null) unmodifiable list of tags
     */
    public List<String> getTagList() {
        return tagList;
    }
    
    /**
     * Set the tags to match on for this rule. You can specify a list 
     * of key=value pairs.
     * 
     * @param tags
     * @throws IllegalArgumentException of the string is not a valid 
     * list of tags
     */
    public void setTags(String tags) throws IllegalArgumentException {
        if (tags == null) {
            this.tagList = null;
            this.tags = null;
            return;
        }
        String[] tagMembers = tags.split(",");
        List<String> tagGroup = new ArrayList<String>(tagMembers.length);
        for (String p : tagMembers) {
            p = p.trim();
            Matcher m = tagsPattern.matcher(p);
    
            if (m.matches()) {
                String fullname = m.group(1);
                String ns = "default";
                String name = "";
                int separatorIndex = fullname.lastIndexOf('.');
                if (fullname.length() == 0)
                    throw new IllegalArgumentException("Invalid tag: " + p); 
                if (separatorIndex == (fullname.length() - 1)) {
                    throw new IllegalArgumentException("Invalid tag: " + p); 
                } else if (separatorIndex == -1) {
                    name = fullname;
                } else {
                    ns = fullname.substring(0, separatorIndex);
                    name = fullname.substring(separatorIndex+1);
                }
                tagGroup.add(ns + "|" + name + "|" + m.group(2));
            } else {
                throw new IllegalArgumentException("Invalid tag: " + p);
            }   
        }   
        this.tags = tags;
        // tagGroup will never be empty
        this.tagList = Collections.unmodifiableList(tagGroup);
    }
    
    /**
     * Used to clean up unused rules on config change.
     * @return
     */
    public boolean isMarked() {
        return marked;
    }

    /**
     * Used to clean up unused rules on config change. All rules are set
     * to marked==false when the config changes. Then rules are updated. 
     * Any rule that is left unmarked after reading of rules is complete
     * needs to be removed.
     * @return
     */
    public void setMarked(boolean marked) {
        this.marked = marked;
    }
    
    public static boolean isInteger(String s) {
        int radix = 10;
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') continue;
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }
    
    /**
     * Return a fixed interface name associated with this rule
     * TODO: should this move the NetVirtManagerImpl or NetVirtInterface?
     */
    @JsonIgnore
    public String getFixedInterfaceName() {
        boolean viface = true;
        
        if (switchId != null && mac == null && ipSubnet == null) {
            viface = false;
        }
        
        if (isInteger(friendlyName)) {
            if (viface)
                return "VEth" + friendlyName;
            else
                return "Eth" + friendlyName;
        } else {
            return friendlyName;
        }
    }

    /**
     * Return a list of sub-interface names that can be derived from
     * the rule itself, independent of devices. This is called
     * once upon interface creation and when switches
     * connect.
     * TODO: should this move the NetVirtManagerImpl or NetVirtInterface?
     */
    public List<String> getFixedSubInterfaceNames(IOFSwitch sw) {
        String mainIfaceName = "";
        boolean viface = true;
        List<String> ifNames;
        
        if (switchId != null && mac == null && ipSubnet == null) {
            viface = false;
        }
            
        // Nothing to do if called on switch connect and this is
        // not a switch based rule.
        if (viface || !sw.getStringId().equals(switchId))
            return null;
        
        mainIfaceName = getFixedInterfaceName();

        /*
         * There is a switch-based rule and the switch exists,
         * return per-port sub-interface names.
         */
        Collection<OFPhysicalPort> ports = sw.getEnabledPorts();
        List<String> ruleports = getPortList();
        ifNames = new ArrayList<String>(1);
        for (OFPhysicalPort ofp : ports) {
            if (ruleports == null || ruleports.contains(ofp.getName())) {
                ifNames.add(mainIfaceName + "/" + ofp.getName());
            }
        }
        
        return ifNames;
    }

    /**
     * Return the appropriate full interface name for specified device 
     * assuming the device matches this interface rule.  The name is
     * returned in an array of name elements. For example:
     * {"Eth5", "34"} or {"MyMacInterface", "00:00:00:00:00:01"}
     * @param d the device to look up
     * @param controllerProvider a sdnplatform provider 
     * @return the interface name specified in an array of components
     * TODO: should this move the NetVirtManagerImpl or NetVirtInterface?
     */
    public String[] getInterfaceNameForDevice(IDevice d,
                                              IControllerService 
                                              controllerProvider) {
        String prefix = "";
        boolean viface = true;
        if (switchId != null && mac == null && ipSubnet == null) {
            viface = false;
        }

        // Check if the interface name is an integer
        if (isInteger(friendlyName)) {
            if (viface)
                prefix = "VEth";
            else 
                prefix = "Eth";
        }
        
        String subiname = null;
        if (viface) {
            subiname = d.getMACAddressString();
        } else {
            /*
             * If this is "physical" interface we need to find the matching 
             * switch attachment point, and return the "nice" name for the 
             * port as the subinterface
             */
            SwitchPort[] aps = d.getAttachmentPoints();
            Map<Long, IOFSwitch> switches = controllerProvider.getSwitches();
            for (SwitchPort ap : aps) {
                long switchIdLong = ap.getSwitchDPID();
                long ruleswitch = HexString.toLong(switchId);
                if (switchIdLong == ruleswitch) {
                    IOFSwitch sw = switches.get(switchIdLong);
                    if (sw == null) continue;
                    OFPhysicalPort p = sw.getPort((short)ap.getPort());
                    if (p!=null && sw.portEnabled(p)) {
                        subiname = p.getName();
                    }
                }
                if (subiname != null)
                    break;
            }
            if (subiname == null) {
                subiname = "[disconnected]";
            }
        } 
        return new String[] {prefix + friendlyName, subiname};
    }
    
    

    @Override
    public boolean equals(Object arg0) {
        if (arg0 instanceof MembershipRule) {
            MembershipRule<?> other = (MembershipRule<?>)arg0;
            return (this.name.equals(other.name) &&
                    this.parentDeviceGroup.equals(other.parentDeviceGroup));
        } 
        return false;
    }

    @Override
    public int hashCode() {
        return (this.name + "|" + this.parentDeviceGroup.getName()).hashCode();
    }

    /**
     * Checks if this rule and other rule have the same matching fields, 
     * priority, etc. I.e., whether they would be exactly the same from a
     * matching point of view.
     */ 
    public boolean matchingFieldsEquals(MembershipRule<T> other) {
        if (this == other) return true;
        if (other == null) return false;
        if (active != other.active) return false;
        if (ipSubnet == null) {
            if (other.ipSubnet != null) return false;
        } else if (!ipSubnet.equals(other.ipSubnet)) return false;
        if (mac == null) {
            if (other.mac != null) return false;
        } else if (!mac.equals(other.mac)) return false;
        if (multipleAllowed != other.multipleAllowed) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        if (ports == null) {
            if (other.ports != null) return false;
        } else if (!ports.equals(other.ports)) return false;
        if (switchId == null) {
            if (other.switchId != null) return false;
        } else if (!switchId.equals(other.switchId)) return false;
        if (tags == null) {
            if (other.tags != null) return false;
        } else if (!tags.equals(other.tags)) return false;
        if (vlanTagOnEgress != other.vlanTagOnEgress) return false;
        if (vlans == null) {
            if (other.vlans != null) return false;
        } else if (!vlans.equals(other.vlans)) return false;
        return true;
    }

    @Override
    public int compareTo(MembershipRule<T> o) {
        // higher priority is sorted first
        if (parentDeviceGroup.equals(o.parentDeviceGroup)) {
            if (priority != o.priority) 
                return (new Integer(o.priority)).compareTo(priority);
            return (name.compareTo(o.name));
        } else {
            return (parentDeviceGroup.compareTo(o.parentDeviceGroup));
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(friendlyName);
        sb.append(" [Parent: " + parentDeviceGroup.getName() + "]");
        sb.append(" [Active: " + active + "]");
        sb.append(" [Priority: " + priority + "]");
        if (mac != null)
            sb.append(" [MAC: " + mac + "]");
        if (vlans != null)
            sb.append(" [VLAN: " + vlans + "]");
        if (ipSubnet != null)
            sb.append(" [IP Subnet: " + ipSubnet + "]");
        if (switchId != null)
            sb.append(" [Switch: " + switchId + "]");
        if (ports != null)
            sb.append(" [Ports: " + ports + "]");

        return sb.toString();
    }
    

}
