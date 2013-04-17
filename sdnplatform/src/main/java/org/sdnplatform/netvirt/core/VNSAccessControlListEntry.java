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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.util.MutableInteger;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.netvirt.core.VNSAccessControlList.VNSAclMatchResult;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.TCP;
import org.sdnplatform.packet.UDP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Represents a NetVirt ACL entry, which is part of a parent AccessControlList
 *   sequence No
 *   type (mac, ip, <0-255>, icmp, udp, tcp)
 *   permit/deny 
 *   
 * @author shudong.zhou@bigswitch.com
 */
@LogMessageCategory("Network Virtualization")
public class VNSAccessControlListEntry implements Comparable<VNSAccessControlListEntry> {
    // Constants for ACL actions
    protected static Map<String, VNSAclMatchResult> actionMap = new HashMap<String, VNSAclMatchResult>();
    static {
        actionMap.put("permit", VNSAclMatchResult.ACL_PERMIT);
        actionMap.put("deny", VNSAclMatchResult.ACL_DENY);       
    }
    
    // Constants for ACL types and IPPROTO mapping
    protected static final int IPPROTO_INVALID = -1;
    public static final int IPPROTO_ALL = 256;
    public static final int ICMPTYPE_ALL = 256;
    public static final int ETHERTYPE_ALL = 65536;
    public static final int VLAN_ALL = 4096;
    
    protected static Map<String, Integer> aclTypeMap = new HashMap<String, Integer>();
    static {
        aclTypeMap.put("ip", IPPROTO_ALL);
        aclTypeMap.put("ipproto", IPPROTO_INVALID);
        aclTypeMap.put("icmp", new Integer(IPv4.PROTOCOL_ICMP));
        aclTypeMap.put("udp", new Integer(IPv4.PROTOCOL_UDP));
        aclTypeMap.put("tcp", new Integer(IPv4.PROTOCOL_TCP));
        aclTypeMap.put("mac", IPPROTO_INVALID);
    }
    
    protected static Logger logger = LoggerFactory.getLogger(VNSAccessControlListEntry.class);
    
    protected VNSAccessControlList parentACL;
    protected int seqNo;
    protected String aclType;  // must be one of NetVirtAclEntryType
    protected String action;
    protected VNSAclMatchResult aclResult;
    
    protected int ipproto;
    protected int srcIp, srcIpMask, dstIp, dstIpMask;
    protected int srcTpPort, dstTpPort;
    protected String srcTpPortOp, dstTpPortOp;
    protected short icmpType;
    protected byte[] srcMac, dstMac;
    protected int etherType;
    protected short vlan;
    
    /**
     * Static method for transport level port comparison
     */
    public static boolean tpPortEq(int port1, int port2) {
        return port1 == port2;
    }
    
    public static boolean tpPortNeq(int port1, int port2) {
        return port1 != port2;
    }
    
    /**
     * @param parentACL the parent of the ACLEntry
     */
    public VNSAccessControlListEntry(int seqNo, VNSAccessControlList parentACL) {
        super();
        this.seqNo = seqNo;
        this.parentACL = parentACL;
    }
    
    public VNSAccessControlList getParentACL() {
        return parentACL;
    }
    public void setParentACL(VNSAccessControlList parentACL) {
        if (parentACL == null)
            throw new NullPointerException("parentACL cannot be null");
        this.parentACL = parentACL;
    }
    public int getSeqNo() {
        return this.seqNo;
    }
    public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
    }
    public String getAction() {
        return this.action;
    }
    public void setAction(String action) throws Exception {
        if (actionMap.containsKey(action)) {
            this.action = action;
            this.aclResult = actionMap.get(action);
            return;
        }
        throw new Exception("Invalid acl action" + action);
    }
    public String getType() {
        return aclType;
    }
    public void setType(String type) throws Exception {
        aclType = type;
        if (aclTypeMap.containsKey(type)) {
            aclType = type;
            ipproto = aclTypeMap.get(type);
        } else {
            ipproto = Integer.parseInt(type);
            if (ipproto <= IPPROTO_INVALID || ipproto >= IPPROTO_ALL) {
                throw new Exception("Invalid IPPROTO " + type);
            }
            aclType = "ipproto";
        }
    }
    public int getSrcIp() {
        return srcIp;
    }
    public void setSrcIp(String srcIp) {
        if (srcIp == null)
            return;
        this.srcIp = IPv4.toIPv4Address(srcIp);
    }
    public int getSrcIpMask() {
        return srcIpMask;
    }
    public void setSrcIpMask(String srcIpMask) {
        if (srcIpMask == null) {
            this.srcIpMask = -1;
            return;
        }
        this.srcIpMask = IPv4.toIPv4Address(srcIpMask);
    }
    public int getDstIp() {
        return dstIp;
    }
    public void setDstIp(String dstIp) {
        if (dstIp == null)
            return;
        this.dstIp = IPv4.toIPv4Address(dstIp);
    }
    public int getDstIpMask() {
        return dstIpMask;
    }
    public void setDstIpMask(String dstIpMask) {
        if (dstIpMask == null) {
            this.dstIpMask = -1;
            return;
        }
        this.dstIpMask = IPv4.toIPv4Address(dstIpMask);
    }
    public int getSrcTpPort() {
        return this.srcTpPort;
    }
    public void setSrcTpPort(int srcTpPort) throws Exception {
        if (srcTpPort < 0 || srcTpPort > 65535) {
            throw new Exception("Invalid source transport port number " + srcTpPort);
        }
        this.srcTpPort = srcTpPort;
    }
    public int getDstTpPort() {
        return this.dstTpPort;
    }
    public void setDstTpPort(int dstTpPort) throws Exception {
        if (dstTpPort < 0 || dstTpPort > 65535) {
            throw new Exception("Invalid destination transport port number " + srcTpPort);
        }
        this.dstTpPort = dstTpPort;
    }
    public String getSrcTpPortOp() {
        return this.srcTpPortOp;
    }
    public void setSrcTpPortOp(String op) throws Exception {
        if (op == null) {
            this.srcTpPortOp = "any";
            return;
        }
        if ("eq".equals(op) || "neq".equals(op) || "any".equals(op)) {
            this.srcTpPortOp = op;
        } else {
            throw new Exception("Invalid source transport port op " + op);
        }
    }
    public String getDstTpPortOp() {
        return this.dstTpPortOp;
    }
    public void setDstTpPortOp(String op) throws Exception {
        if (op == null) {
            this.srcTpPortOp = "any";
            return;
        }
        if ("eq".equals(op) || "neq".equals(op) || "any".equals(op)) {
            this.dstTpPortOp = op;
        } else {
            throw new Exception("Invalid destination transport op " + op);
        }
    }
    public void setIcmpType(int icmpType) throws Exception {
        if (icmpType < 0 || icmpType > ICMPTYPE_ALL) {
            throw new Exception("Invalid ICMP type " + icmpType);
        }
        this.icmpType = (short) icmpType;
    }
    public byte[] getSrcMac() {
        return this.srcMac;
    }
    public void setSrcMac(String srcMac) {
        if (srcMac == null || srcMac.isEmpty())
            this.srcMac = null;
        else
            this.srcMac = HexString.fromHexString(srcMac);
    }
    public byte[] getDstMac() {
        return this.dstMac;
    }
    public void setDstMac(String dstMac) {
        if (dstMac == null || dstMac.isEmpty())
            this.dstMac = null;
        else
            this.dstMac = HexString.fromHexString(dstMac);
    }
    public void setEtherType(int etherType) throws Exception {
        if (etherType < 0 || etherType > ETHERTYPE_ALL) {
            throw new Exception("Invalid ether type " + etherType);
        }
        this.etherType = etherType;
    }
    public int getVlan() {
        return this.vlan;
    }
    public void setVlan(int vlan) throws Exception {
        if (vlan < 0 || vlan > VLAN_ALL) {
            throw new Exception("Invalid vlan value " + vlan);
        }
        this.vlan = (short) vlan;
    }
    
    /**
     * Returns IPv4 if src/dst/proto matches, else return null
     */
    private IPv4 getMatchedIPv4(Ethernet eth, MutableInteger wildcards) {
        wildcards.setValue(wildcards.intValue() & ~OFMatch.OFPFW_DL_TYPE);
        if (eth.getPayload() instanceof IPv4) {
            IPv4 ipv4 = (IPv4) eth.getPayload();
            
            // First, check for protocol match
            if (IPPROTO_ALL != ipproto) {
                wildcards.setValue(wildcards.intValue() & ~OFMatch.OFPFW_NW_PROTO);
                if (ipv4.getProtocol() != ipproto)
                    return null;
            }
            
            // Check for src/dst address match
            int srcAddr = ipv4.getSourceAddress();
            int dstAddr = ipv4.getDestinationAddress();
            Boolean match = ((srcAddr & ~srcIpMask) == (srcIp & ~srcIpMask) &&
                    (dstAddr & ~dstIpMask) == (dstIp & ~dstIpMask));

            // Figure out the widest possible address wildcard mask
            int srcWildcardBits = 32;
            int dstWildcardBits = 32;
            if (!match) {
                if ((srcAddr & ~srcIpMask) != (srcIp & ~srcIpMask)) {
                    srcWildcardBits = getWildcardBits(srcAddr, srcIp, srcIpMask, false);
                } else {
                    dstWildcardBits = getWildcardBits(dstAddr, dstIp, dstIpMask, false);
                }
            } else {           
                srcWildcardBits = getWildcardBits(srcAddr, srcIp, srcIpMask, true);
                dstWildcardBits = getWildcardBits(dstAddr, dstIp, dstIpMask, true);
            }
            adjustIpWildcards(srcWildcardBits, dstWildcardBits, wildcards);
            return match ? ipv4 : null;
        }
        
        return null;
    }
    
    private int getWildcardBits(int addr, int ip, int ipMask, boolean match) {
        int maskBits = 0;
        int tmp = 1;
        // count the least significant contiguous bits
        while ((ipMask & tmp) != 0) {
            maskBits++;
            tmp <<= 1;
        }
        if (match) {
            // can only match as wide as the mask
            return maskBits;
        }
        
        // keep increasing mask until we get a match
        do {
            ipMask |= (1 << maskBits);
            maskBits++;
        } while ((addr & ~ipMask) != (ip & ~ipMask));
        
        return maskBits - 1;
    }
    
    private void adjustIpWildcards(int srcWildcardBits, int dstWildcardBits, MutableInteger wildcards) {
        if (srcWildcardBits < 32) {
            int oldBits = (wildcards.intValue() & OFMatch.OFPFW_NW_SRC_MASK) >> OFMatch.OFPFW_NW_SRC_SHIFT;
            if (srcWildcardBits < oldBits) {
                wildcards.setValue((wildcards.intValue() & ~OFMatch.OFPFW_NW_SRC_MASK) |
                                   (srcWildcardBits << OFMatch.OFPFW_NW_SRC_SHIFT));
            }
        }
        if (dstWildcardBits < 32) {
            int oldBits = (wildcards.intValue() & OFMatch.OFPFW_NW_DST_MASK) >> OFMatch.OFPFW_NW_DST_SHIFT;
            if (dstWildcardBits < oldBits) {
                wildcards.setValue((wildcards.intValue() & ~(OFMatch.OFPFW_NW_DST_MASK)) |
                                   (dstWildcardBits << OFMatch.OFPFW_NW_DST_SHIFT));
            }
        }
    }
    
    /**
     * Match on IP addresses only
     */
    public boolean match_ip (Ethernet eth, IDevice srcDev, IDevice dstDev,
                             MutableInteger wildcards) {
        return getMatchedIPv4(eth, wildcards) != null;
    }
    
    /**
     * Match on ip addresses and protocol number
     */
    public boolean match_ipproto (Ethernet eth, IDevice srcDev, IDevice dstDev,
                                  MutableInteger wildcards) {
        return getMatchedIPv4(eth, wildcards) != null;
    }
    
    /**
     * Compares tcp/udp ports in packet against acl specs
     */
    private boolean matchTpPort(String op, int port1, int port2,
                                MutableInteger wildcards,
                                int tpFlag) {
        if ("eq".equals(op)) {
            wildcards.setValue(wildcards.intValue() & ~tpFlag);
            return port1 == port2;
        } else if ("neq".equals(op)) {
            wildcards.setValue(wildcards.intValue() & ~tpFlag);
            return port1 != port2;
        } else { // any
            return true;
        }
    }
    
    /**
     * Match on udp ports and ip addresses
     */
    public boolean match_udp (Ethernet eth, IDevice srcDev, IDevice dstDev,
                              MutableInteger wildcards) {
        IPv4 ipv4 = getMatchedIPv4(eth, wildcards);
        if (ipv4 != null) {
            assert(ipv4.getPayload() instanceof UDP);
            UDP udp = (UDP) ipv4.getPayload();
            return matchTpPort(srcTpPortOp, srcTpPort, udp.getSourcePort(),
                               wildcards, OFMatch.OFPFW_TP_SRC) &&
                   matchTpPort(dstTpPortOp, dstTpPort, udp.getDestinationPort(),
                               wildcards, OFMatch.OFPFW_TP_DST);
        }
        
        return false;
    }
    
    
    /**
     * Match on tcp ports and ip addresses
     */
    public boolean match_tcp (Ethernet eth, IDevice srcDev, IDevice dstDev,
                              MutableInteger wildcards) {
        IPv4 ipv4 = getMatchedIPv4(eth, wildcards);
        if (ipv4 != null) {
            assert(ipv4.getPayload() instanceof TCP);
            TCP tcp = (TCP) ipv4.getPayload();
            return matchTpPort(srcTpPortOp, srcTpPort, tcp.getSourcePort(),
                               wildcards, OFMatch.OFPFW_TP_SRC) &&
                   matchTpPort(dstTpPortOp, dstTpPort, tcp.getDestinationPort(),
                               wildcards, OFMatch.OFPFW_TP_DST);
        }
        return false;
    }
    
    /**
     * Match on ip addresses and ICMP type
     */
    public boolean match_icmp (Ethernet eth, IDevice srcDev, IDevice dstDev,
                               MutableInteger wildcards) {
        IPv4 ipv4 = getMatchedIPv4(eth, wildcards);
        if (ipv4 != null) {
            // protocol already matched
            assert(ipv4.getPayload() instanceof ICMP);
            ICMP icmp = (ICMP) ipv4.getPayload();
            if (ICMPTYPE_ALL == icmpType)
                return true;
            // Openflow spec uses TP_SRC to specify icmp type
            wildcards.setValue(wildcards.intValue() & ~OFMatch.OFPFW_TP_SRC);
            return icmp.getIcmpType() == icmpType;
        }
        
        return false;
    }

    /*
     * Match configured Vlan tag in the ACL if any, with all the vlan tags
     * associated with the device.
     *
     * XXX Filtering vlan tags through ACLs would impact address-space
     * separation, as we solely vlan tags at the moment to achieve this
     * separation.
     */
    private boolean matchVlan (IDevice dev) {
        if (dev != null) {

            /*
             * Iterate through all associated vlan tags for this device and
             * find if there is a match.
             */
            for (Short devVlan : dev.getVlanId()) {
                if (devVlan != null && devVlan.equals(vlan)) return true;
            }
        }

        /*
         * No vlan match was found in this acl entry.
         */
        return false;
    }
    
    /**
     * Match on Ethernet addresses and type
     */
    public boolean match_mac (Ethernet eth, IDevice srcDev, IDevice dstDev,
                              MutableInteger wildcards) {

        /*
         * Unicast    : (srcDev == null) => DENY
         *              (dstDev == null) => PERMIT, so that the device learning
         *                                          process can be triggered
         *
         * Non-Unicast: (srcDev == null) => DENY
         *              (dstDev == null) => Match against the packet
         *
         */

        /*
         * Check if source mac address has been configured in this acl entry.
         */
        if (srcMac != null) {

            /*
             * If there is no source device, we should essentially drop the
             * packet.
             */
            if (srcDev == null) return false ;

            /*
             * Compare the source device's MAC address with the source mac
             * address configured in this ACL entry.
             *
             * byte[].equals() doesn't compare content, a Java feature. Use
             * Arrays.equals() instead.
             */
             if (!Arrays.equals(Ethernet.toByteArray(srcDev.getMACAddress()),
                                srcMac)) {
                return false ;
            }
        }

        /*
         * Check if the destination mac address has been configured in this
         * acl entry.
         */
        if (dstMac != null) {
            byte[] tmpMac;

            /*
             * If we do know about the destination device, compare its MAC
             * address with what has been configured in this ACL entry.
             */
            if (dstDev != null) {
                tmpMac = Ethernet.toByteArray(dstDev.getMACAddress());
            } else {

                /*
                 * For Unicast packets, if there is no destination device
                 * present, we [force] return a match so that the destination
                 * entity device can be properly learnt asap.
                 */
                if (!eth.isMulticast() && !eth.isBroadcast()) return true;

                /*
                 * Use packet's destination mac address instead of the device's.
                 * XXX Need to be re-addressed for multicast rewrite in future.
                 */
                tmpMac = eth.getDestinationMACAddress();
            }

            /*
             * We now have the proper MAC address to use. Compare it against
             * what has been configured in this ACL entry.
             */
            if (!Arrays.equals(tmpMac, dstMac)) return false;
        }

        /*
         * Check if vlan tag has been configured in this ACL entry.
         */
        if (VLAN_ALL != vlan) {

            /*
             * Update the wildcards bit mask, with the fact that VLAN tag field
             * is not a wild card, by resetting the OFPFW_DL_VLAN bit.
             */
            wildcards.setValue(wildcards.intValue() & ~OFMatch.OFPFW_DL_VLAN);

            /*
             * Match configured vlan tag with all the vlan tags associated with
             * the source and destination device.
             */
            if (!matchVlan(srcDev) && !matchVlan(dstDev)) {

                /*
                 * XXX Should we go ahead and match against whats in the pkt ?
                 */
                if (eth.getVlanID() != vlan) {
                    return false;
                }
            }
        }

        /*
         * Check if etherType has been configured in this ACL entry.
         */
        if (ETHERTYPE_ALL != etherType) {

            /*
             * Note down that etherType match is no longer a wild card.
             */
            wildcards.setValue(wildcards.intValue() & ~OFMatch.OFPFW_DL_TYPE);

            /*
             * If the packet's etherType does not match with whats been 
             * configured, then this acl entry does not match with this packet.
             */
            if (eth.getEtherType() != etherType) return false;
        }

        /*
         * All configured mac related attributes if any match correctly
         * with the received packet or its associated devices.
         */
        return true;
    }
    
    /**
     * Match this ACL entry against Ethernet packet eth.
     */
    @LogMessageDoc(level="ERROR",
                   message="Failed to invoke ACL match",
                   explanation="Failed to match flow against ACL",
                   recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public VNSAclMatchResult matchAcl (Ethernet eth,
                                       IDevice srcDev, IDevice dstDev,
                                       MutableInteger wildcards) {
        try {
            Method matchm = this.getClass()
                                .getMethod("match_" + aclType,
                                           new Class[] {eth.getClass(),
                                                        IDevice.class,
                                                        IDevice.class,
                                                        MutableInteger.class});
            if ((Boolean) matchm.invoke(this, eth, srcDev, dstDev, wildcards)) {
                return aclResult;
            }
        } catch (Exception e) {
            logger.error("Failed to invoke ACL match", e);
        }

        return VNSAclMatchResult.ACL_NO_MATCH;
    }
    
    @Override
    public boolean equals(Object arg0) {
        if (arg0 instanceof VNSAccessControlListEntry) {
            return (this.seqNo == ((VNSAccessControlListEntry)arg0).seqNo &&
                    this.parentACL.equals(((VNSAccessControlListEntry)arg0).parentACL));
        } 
        return super.equals(arg0);
    }

    @Override
    public int hashCode() {
        return (this.seqNo + "|" + this.parentACL.getName()).hashCode();
    }

    @Override
    public int compareTo(VNSAccessControlListEntry o) {
        // higher priority is sorted first
        if (parentACL.equals(o.parentACL)) {
            return (new Integer(o.seqNo)).compareTo(seqNo);
        } else {
            return (parentACL.compareTo(o.parentACL));
        }
    }

    private String toString_IpMask(int ip, int mask) {
        if (mask == -1)
            return " any";
        return  " " + IPv4.fromIPv4Address(ip) +
                " " + IPv4.fromIPv4Address(mask);
    }
    private String toString_tpPort(String op, int port) {
        if ("any".equals(op))
            return " any";
        return " " + op + " " + port;
    }
    public String toString_ip() {
        return aclType +
               toString_IpMask(srcIp, srcIpMask) +
               toString_IpMask(dstIp, dstIpMask);
    }
    public String toString_ipproto() {
        return Integer.toString(ipproto) +
        toString_IpMask(srcIp, srcIpMask) +
        toString_IpMask(dstIp, dstIpMask);
    }
    private String toString_tcp_udp() {
        return aclType +
               toString_IpMask(srcIp, srcIpMask) +
               toString_tpPort(srcTpPortOp, srcTpPort) +
               toString_IpMask(dstIp, dstIpMask) +
               toString_tpPort(dstTpPortOp, dstTpPort);
    }
    public String toString_udp() {
        return toString_tcp_udp();
    }
    public String toString_tcp() {
        return toString_tcp_udp();
    }
    public String toString_icmp() {
        return aclType +
               toString_IpMask(srcIp, srcIpMask) +
               toString_IpMask(dstIp, dstIpMask) +
               " " + Integer.toString(icmpType);
    }
    public String toString_mac() {
        return aclType +
               " " + (srcMac == null ? "any" : HexString.toHexString(srcMac)) +
               " " + (dstMac == null ? "any" : HexString.toHexString(dstMac)) +
               " " + etherType +
               " " + vlan;
    }
    
    @Override
    public String toString(){
        StringBuffer sb = new StringBuffer();
        sb.append("ACL: " + parentACL.getName());
        sb.append(" seqNo: " + seqNo);
        sb.append(" " + action + " ");
        
        try {
            Method matchm = this.getClass().getMethod("toString_" + aclType);
            sb.append((String)matchm.invoke(this));
        } catch (Exception e) {
            sb.append(e.toString());
        }
        
        return sb.toString();
    }
}
