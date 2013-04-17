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

import java.util.ArrayList;
import java.util.List;


import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.util.MutableInteger;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.netvirt.virtualrouting.internal.VirtualRouting;
import org.sdnplatform.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Represents an access control list. It is associated with a NetVirt and contain one or more entries.
 * @author shudong.zhou@bigswitch.com
 */
public class VNSAccessControlList implements Comparable<VNSAccessControlList> {
    public static enum VNSAclMatchResult {
        ACL_NO_MATCH,
        ACL_PERMIT,
        ACL_DENY
    };
    
    protected static Logger logger = LoggerFactory.getLogger(VNSAccessControlList.class);
    
    protected String name;
    protected String description;
    protected int priority;
    
    protected List<VNSAccessControlListEntry> entries;
    
    /* Used to garbage collect stale config data */
    protected boolean marked;
    
    /**
     * @param parentNetVirt the parent of the interface rule
     */
    public VNSAccessControlList(String name) {
        super();
        this.setName(name);
        this.priority = 32768;  // default as specified in PRD
        entries = new ArrayList<VNSAccessControlListEntry>();
    }
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        if (name == null)
            throw new NullPointerException("name cannot be null");
        this.name = name;  
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
    
    /**
     * Add acl entry to list in the order they are applied
     */
     public void addAclEntry(VNSAccessControlListEntry entry) {
        int index;
        entry.setParentACL(this);
        for (index = 0; index < entries.size(); index++) {
            if (entry.compareTo(entries.get(index)) < 0)
                continue;
            // no dups at the same sequence number
            if (entry.compareTo(entries.get(index)) == 0)
                entries.remove(index);
            break;
        }
        entries.add(index, entry);
    }
    
     /**
      * Apply acl to a packet and get the matching action
      * If this is for an explain packet then populate the context with the acl
      * entry that was hit
      */
     public VNSAclMatchResult applyAcl (Ethernet eth, MutableInteger wildcards,
                                        ListenerContext cntx,
                                        String direction) {
       
         /* 
          * Extract source device and destination device information from the
          * listener context.
          */
         IDevice srcDev = IDeviceService.fcStore.get(
                              cntx, IDeviceService.CONTEXT_SRC_DEVICE);
         IDevice dstDev = IDeviceService.fcStore.get(
                              cntx, IDeviceService.CONTEXT_DST_DEVICE);

         // The entries list is sorted with sequence number
         for (VNSAccessControlListEntry entry : entries) {
             VNSAclMatchResult result = entry.matchAcl(eth, srcDev, dstDev,
                                                       wildcards);
             if (result != VNSAclMatchResult.ACL_NO_MATCH) {
                 if (logger.isTraceEnabled())
                     logger.trace("{} matched ACL entry {}", eth, entry);
                 
                 // Note down the acl entry hit for explain packet
                 if ((cntx != null) && (direction != null)) {
                     if (NetVirtExplainPacket.isExplainPktCntx(cntx)) {
                         String aclDirection = NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_ENTRY;
                         if (direction.equals(VirtualRouting.ACL_DIRECTION_INPUT)) {
                             aclDirection = NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_ENTRY;
                         }
                         // split() below converts "ACL: default|acl-test2-out seqNo: 10 permit ip any any" to
                         // "10 permit ip any any", for example
                         // TODO - this sucks, it depends upon specific string format.
                         NetVirtExplainPacket.explainPacketSetContext(cntx, aclDirection, entry.toString().split(":", 3)[2]);
                     }
                 }
                 return result;
             }
         }
         // Implicit deny if no ACL entry matches
         if ((cntx != null) && (direction != null)) {
             String aclDirection = NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_ENTRY;
             if (direction.equals(VirtualRouting.ACL_DIRECTION_INPUT)) {
                 aclDirection = NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_ENTRY;
             }
             NetVirtExplainPacket.explainPacketSetContext(cntx, aclDirection, "Implicit deny");
         }
         return VNSAclMatchResult.ACL_DENY;
     }
     
    @Override
    public boolean equals(Object arg0) {
        if (arg0 instanceof VNSAccessControlList) {
            return this.name.equals(((VNSAccessControlList)arg0).name);
        } 
        return super.equals(arg0);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public int compareTo(VNSAccessControlList o) {
        // higher priority is sorted first
        if (priority != o.priority) 
            return (new Integer(o.priority)).compareTo(priority);
        return (name.compareTo(o.name));
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(name);
        sb.append(" [Priority: " + priority + "]");
        sb.append(" [Number of entries: " + entries.size() + "]");

        return sb.toString();
    }
    
}
