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

import java.util.Date;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.sdnplatform.devicegroup.MembershipRule;


/**
 * Represent an interface on a NetVirt.  An interface for a NetVirt is generated
 * by a set of interface rules defined for the NetVirt.
 * 
 * An interface contains the devices that are assigned to it by matching
 * against the interface rules.  This class however does not maintain
 * this mapping; see @ref INetVirtManager to get the interface for a NetVirt.
 *
 * @author readams
 */
public class VNSInterface {
    protected String name;
    protected VNS parentNetVirt;
    protected MembershipRule<VNS> parentRule;
    protected VNSInterface parentIFace;
    protected Date lastSeen;
    
    /* Used to garbage collect stale config data */
    protected boolean marked;

    /**
     * Allocate a new NetVirt interface object with the specified parameters
     * @param name the fully-qualified interface name
     * @param parentNetVirt the associated parent NetVirt
     * @param parentRule the associated rule that generated the interface
     * @param parentIface the parent interface if it exists, or null if this is a top-level interface
     */
    public VNSInterface(String name, 
                        VNS parentNetVirt,
                        MembershipRule<VNS> parentRule, 
                        VNSInterface parentIface) {
        super();
        setName(name);
        setParentNetVirt(parentNetVirt);
        setParentRule(parentRule);
        setParentNetVirtInterface(parentIface);
        lastSeen = new Date();
    }
    
    public VNSInterface() {
        super();
        lastSeen = new Date();
    }
    
    public void copy(VNSInterface netVirtIface) {
        this.lastSeen    = netVirtIface.lastSeen;
        this.name        = netVirtIface.name;
        this.parentNetVirt   = netVirtIface.parentNetVirt;
        this.parentRule  = netVirtIface.parentRule;
        this.parentIFace = netVirtIface.parentIFace;
    }
    
    public VNS getParentVNS() {
        return parentNetVirt;
    }
    
    public void setParentNetVirt(VNS parentNetVirt) {
        if (parentNetVirt == null)
            throw new NullPointerException("parentNetVirt cannot be null");
        
        this.parentNetVirt = parentNetVirt;
    }

    public MembershipRule<VNS> getParentRule() {
        return parentRule;
    }
    public void setParentRule(MembershipRule<VNS> parentRule) {
        this.parentRule = parentRule;
    }
    
    @JsonIgnore
    public VNSInterface getParentVNSInterface() {
        return parentIFace;
    }
    public void setParentNetVirtInterface(VNSInterface iface) {
        this.parentIFace = iface;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        if (name == null)
            throw new NullPointerException("name cannot be null");

        this.name = name;
    }
    @JsonIgnore
    public boolean isMarked() {
        return marked;
    }
    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public Date getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(Date date) {
        lastSeen = date;
        if (this.parentIFace != null)
            this.parentIFace.setLastSeen(date);
    }
    
    @Override
    public String toString() {
        return "NetVirtInterface: name:" + name + " in NetVirt " + parentNetVirt.getName();
    }
}

