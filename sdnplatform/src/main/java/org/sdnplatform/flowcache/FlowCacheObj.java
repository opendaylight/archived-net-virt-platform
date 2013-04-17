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

package org.sdnplatform.flowcache;

import java.util.ArrayList;
import java.util.ListIterator;



import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.util.HexString;
import org.sdnplatform.flowcache.BetterFlowCache.FCOper;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.IPv4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class FlowCacheObj. This is a helper class to BetterFlowCache class that 
 * performs the lower level flow lookup, insertion, activation, deactivation
 * functions.
 */
public class FlowCacheObj {

    protected static Logger logger =LoggerFactory.getLogger(FlowCacheObj.class);

    /** Flow is active */
    public static final byte FCStateACTIVE   = 1;

    /**
     * Flow is removed but it is still kept in flow cache for performance
     * reason
     */
    public static final byte FCStateINACTIVE = 2;

    /** Flow entry is created but not used yet */
    private static final byte FCStateUNUSED  = 3;

    /** The flow entry match permits traffic */
    public static final byte FCActionPERMIT  = 1;

    /** The flow entry match drops traffic */
    public static final byte FCActionDENY    = 2;

    /** A flow is dampened if it was inserted in the flow cache less than
     * DAMPEN_TIMEOUT_NS ago. Flow mods for dampened flows are not pushed to
     * the switch flow table.
     * all time is memsured in millisecond now.
     */
    private static final long DAMPEN_TIMEOUT_NS = 1500000000; // 1.5 seconds

    //************************
    // Commonly used wildcards
    //************************

    /* Some commonly used wildcards used for faster processing, for example in
     * comparison operations
     * Wildcard is a 22-bit field as per open-flow spec.
     */

    /* The Match all wildcard.
     */
    final public  static int WILD_ALL = 0x3FFFFF;
    /* The following wildcard is used in core switches and on internal ports
     * This wildcards all fields except VLAN and Dest MAC address.
     */
    final private static int WILD_MATCH_VLAN_DLSADR = 0x3FFFA0;

    /* The wild card below is commonly used for edge ports
     * Input-port, vlan, src and dest macs are NOT wild-carded.
     */
    final public  static int WILD_MATCH_INP_VLAN_DLADRS = 0x3FFFF0;
    
    /* The wild card below is commonly used when acl is "permit ip any any"
     * Input-port, vlan, src and dest macs, new protocol are NOT wild-carded.
     */
    final public  static int WILD_MATCH_INP_VLAN_DLADRS_ET = 0x3FFFE0;

    /* Input-port, vlan, src and dest macs etype, proto, dstprt are NOT 
     * wild-carded.
     */
    final private static int WILD_MATCH_INP_VLAN_DLADRS_ET_PROTO_DP = 0x3FFF40;

    /* Input-port, vlan, src and dest macs etype, proto are NOT wild-carded.
     */
    final private static int WILD_MATCH_INP_VLAN_DLADRS_ET_PROTO = 0x3FFFC0;

    /* The one below is used at switches that need L3_MATCH
     * Input-port, vlan, src and dest macs, ether-type and L3 addresses are 
     * NOT wild-carded.
     */
    final private static int WILD_MATCH_INP_VLAN_DLADRS_ETYP_NWADRS = 0x3000E0;

    final private static int WILD_PCP_TOS_L4PSPRT       = 0x300060;
    final private static int WILD_PCP_TOS_L4PDPRT       = 0x3000A0;
    final private static int WILD_PCP_L3SADR_TOS_L4SPRT = 0x303F60;
    final private static int WILD_PCP_L3DADR_TOS_L4DPRT = 0x3FC0A0;
    final private static int WILD_PCP_L3PROT_TOS        = 0x300020;
    final private static int WILD_NONE                  = 0x0;

    //***************************
    // FlowCacheObj class members
    //***************************
    /**
     * Most of the flows are expected to have one fce and the fce member would
     * be used to store that flow entry. In some cases however there would be
     * multiple flow entries between a source-destination pair and in those
     * cases the fceList would be used to store the 2nd and subsequent flows.
     * If there is only one flow entry then fceList member would be set to null.
     * Reason for this approach is not to user 12 bytes of arraylist overhead
     * (24 bytes on a 64-bit machine) for every flow in the flow cache when 
     * there is only one flow mod for a flow.
     * 
     * One example where there could be multiple flow specs between a source 
     * and a destination is when there is an ACL that permits specific 
     * transport layer ports.
     */
    public FCEntry fce;
    public ArrayList<FCEntry> fceList;
    /* If the entry is active then it is the time stamp of the latest fce 
     * installation time in this FlowCacheObject 
     */
    public   long     installTimeNs;
    public long     cookie;

    /**
     * Create an instance of flow cache object.
     */
    protected FlowCacheObj() {
        fce = new FCEntry();
        fceList = null;
    }

    /**
     * This class is used to represent a flow entry as it is stored in the flow
     * cache. Src MAC, Dest MAC and VLAN are used as hash keys hence they are 
     * not stored in the FCEntry object.
     */
    public class FCEntry { /* 32-bytes (four 8-bytes) */
        protected long     srcSwitchDpid;
        protected short    srcL4Port;
        protected short    destL4Port;
        protected short    inputPort;
        protected short    etherType;
        protected int      srcIpAddr;
        protected int      destIpAddr;
        protected int      wildcards;
        protected short    ofPri;    /* openflow flow-mod priority */
        protected byte     protocol; /* Network (L3) protocol      */
        protected byte     state;    /* See FCState* above         */
        protected byte     action;   /* See FCAction* above        */
        protected byte     nwTos;    /* Network TOS field          */
        protected byte     pcp;      /* vlan priority code point   */
        /* The scan count is incremented every scan period. It is reset when
         * a response from the switch flow table query hits this entry. It is
         * also reset when the entry gets deactivated. If this entry is active
         * and the scan count is greater than a threshold then the entry is
         * deleted. This handles the case when the flow mod removal message
         * was not sent or got lost.
         */
        protected byte     scanCnt;  /* incremented every scan period */

        /**
         * Create an instance of flow-cache entry object
         * @param ofmWithSwDpid flow match structure
         * @param priority flow-mod priority
         * @param action PERMIT or DENY action
         */
        protected FCEntry(OFMatchWithSwDpid ofmWithSwDpid, short priority, byte action) {
            this.fcEntryInit(ofmWithSwDpid, priority, action);
        }

        protected FCEntry() {
            state    = FCStateUNUSED; /* free */
            scanCnt  = 0;
        }

        private void fcEntryInit(OFMatchWithSwDpid ofmWithSwDpid, short priority, byte action) {
            srcSwitchDpid = ofmWithSwDpid.getSwitchDataPathId();
            srcL4Port     = ofmWithSwDpid.getOfMatch().getTransportSource();
            destL4Port    = ofmWithSwDpid.getOfMatch().getTransportDestination();
            inputPort     = ofmWithSwDpid.getOfMatch().getInputPort();
            etherType     = ofmWithSwDpid.getOfMatch().getDataLayerType();
            srcIpAddr     = ofmWithSwDpid.getOfMatch().getNetworkSource();
            destIpAddr    = ofmWithSwDpid.getOfMatch().getNetworkDestination();
            wildcards     = ofmWithSwDpid.getOfMatch().getWildcards();
            this.ofPri    = priority;
            protocol      = ofmWithSwDpid.getOfMatch().getNetworkProtocol();
            this.state    = FCStateACTIVE;
            this.action   = action;
            nwTos         = ofmWithSwDpid.getOfMatch().getNetworkTypeOfService();
            pcp           = ofmWithSwDpid.getOfMatch().getDataLayerVirtualLanPriorityCodePoint();
            scanCnt       = 0;
        }

        /* Returns a OFMatch object populated from this fce and supplied
         * parameters
         */
        protected void toOFMatchWithSwDpid(OFMatchWithSwDpid ofmWithSwDpid,
                                        short vlan, long srcMac, long dstMac) {

            /* Populate the OFMatch object */
            ofmWithSwDpid.getOfMatch().setDataLayerSource(Ethernet.toByteArray(srcMac));
            ofmWithSwDpid.getOfMatch().setDataLayerDestination(Ethernet.toByteArray(dstMac));
            ofmWithSwDpid.getOfMatch().setDataLayerVirtualLan(vlan);
            ofmWithSwDpid.getOfMatch().setTransportSource(srcL4Port);
            ofmWithSwDpid.getOfMatch().setTransportDestination(destL4Port);
            ofmWithSwDpid.getOfMatch().setInputPort(inputPort);
            ofmWithSwDpid.getOfMatch().setDataLayerType(etherType);
            ofmWithSwDpid.getOfMatch().setNetworkSource(srcIpAddr);
            ofmWithSwDpid.getOfMatch().setNetworkDestination(destIpAddr);
            ofmWithSwDpid.getOfMatch().setWildcards(wildcards);
            ofmWithSwDpid.getOfMatch().setNetworkProtocol(protocol);
            ofmWithSwDpid.getOfMatch().setNetworkTypeOfService(nwTos);
            ofmWithSwDpid.getOfMatch().setDataLayerVirtualLanPriorityCodePoint(pcp);
            ofmWithSwDpid.setSwitchDataPathId(srcSwitchDpid);
        }

        /* Returns a OFFlowMod object populated from this fce and supplied
         * parameters
         */
        protected void toOFMatchReconcile(OFMatchReconcile ofmRc,
                                        short vlan, long srcMac, long dstMac) {

            /* Populate the OFMatch object */
            OFMatchWithSwDpid ofmWithSwDpid = ofmRc.ofmWithSwDpid;
            ofmWithSwDpid.getOfMatch().setDataLayerSource(Ethernet.toByteArray(srcMac));
            ofmWithSwDpid.getOfMatch().setDataLayerDestination(Ethernet.toByteArray(dstMac));
            ofmWithSwDpid.getOfMatch().setDataLayerVirtualLan(vlan);
            ofmWithSwDpid.getOfMatch().setTransportSource(srcL4Port);
            ofmWithSwDpid.getOfMatch().setTransportDestination(destL4Port);
            ofmWithSwDpid.getOfMatch().setInputPort(inputPort);
            ofmWithSwDpid.getOfMatch().setDataLayerType(etherType);
            ofmWithSwDpid.getOfMatch().setNetworkSource(srcIpAddr);
            ofmWithSwDpid.getOfMatch().setNetworkDestination(destIpAddr);
            ofmWithSwDpid.getOfMatch().setWildcards(wildcards);
            ofmWithSwDpid.getOfMatch().setNetworkProtocol(protocol);
            ofmWithSwDpid.getOfMatch().setNetworkTypeOfService(nwTos);
            ofmWithSwDpid.getOfMatch().setDataLayerVirtualLanPriorityCodePoint(pcp);
            ofmWithSwDpid.setSwitchDataPathId(srcSwitchDpid);
            ofmRc.rcAction = OFMatchReconcile.ReconcileAction.NO_CHANGE;
            ofmRc.action = action;
        }

        /**
         * Compares if a given object is equal to this FCEntry object 
         * @param obj object to compare with
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof FCEntry)) {
                return false;
            }

            FCEntry o = (FCEntry) obj;

            if (srcSwitchDpid != o.srcSwitchDpid) {
                return false;
            }
            
            /* First check the wildcards since that determines how rest of the
             * OFMatch fields are relevant for equals comparison.
             */
            if (wildcards != o.wildcards) {
                return false;
            }

            switch (wildcards) {
                case WILD_MATCH_INP_VLAN_DLADRS: /* Edge ports */
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (inputPort     == o.inputPort)     &&
                        (ofPri         == o.ofPri)) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ET: /* permit ip any any */
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (ofPri         == o.ofPri)) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ET_PROTO_DP:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (destL4Port    == o.destL4Port)    &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (ofPri         == o.ofPri)         &&
                        (protocol      == o.protocol)) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ET_PROTO:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (ofPri         == o.ofPri)         &&
                        (protocol      == o.protocol)) {
                        return true;
                    }
                    break;
                case WILD_MATCH_VLAN_DLSADR: /* Core or internal ports */
                    if (srcSwitchDpid == o.srcSwitchDpid) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ETYP_NWADRS:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri         == o.ofPri)) {
                        return true;
                    }
                    break;
                case WILD_PCP_TOS_L4PSPRT:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (destL4Port    == o.destL4Port)    &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri         == o.ofPri)      &&
                        (protocol      == o.protocol)) {
                        return true;
                    }
                    break;
                case WILD_PCP_TOS_L4PDPRT:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (srcL4Port     == o.srcL4Port)     &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri      == o.ofPri)      &&
                        (protocol      == o.protocol)) {
                        return true;
                    }
                    break;
                case WILD_PCP_L3SADR_TOS_L4SPRT:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (destL4Port    == o.destL4Port)    &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri         == o.ofPri)      &&
                        (protocol      == o.protocol)) {
                        return true;
                    }
                    break;
                case WILD_PCP_L3DADR_TOS_L4DPRT:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (srcL4Port     == o.srcL4Port)     &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (ofPri         == o.ofPri)      &&
                        (protocol      == o.protocol)) {
                        return true;
                    }
                    break;
                case WILD_PCP_L3PROT_TOS:
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (srcL4Port     == o.srcL4Port)     &&
                        (destL4Port    == o.destL4Port)    &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri         == o.ofPri)) {
                        return true;
                    }
                    break;
                case WILD_NONE: /* None of the match fields are wild-carded */
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (srcL4Port     == o.srcL4Port)     &&
                        (destL4Port    == o.destL4Port)    &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri         == o.ofPri)      &&
                        (protocol      == o.protocol)      &&
                        (nwTos         == o.nwTos)         &&
                        (pcp           == o.pcp)) {
                        return true;
                    }
                    break;
                default:
                    if (logger.isDebugEnabled()) {
                        logger.debug("Unexpected wildcard {} in {}", wildcards, o);
                    }
                    if ((srcSwitchDpid == o.srcSwitchDpid) &&
                        (srcL4Port     == o.srcL4Port)     &&
                        (destL4Port    == o.destL4Port)    &&
                        (inputPort     == o.inputPort)     &&
                        (etherType     == o.etherType)     &&
                        (srcIpAddr     == o.srcIpAddr)     &&
                        (destIpAddr    == o.destIpAddr)    &&
                        (ofPri         == o.ofPri)      &&
                        (protocol      == o.protocol)      &&
                        (nwTos         == o.nwTos)         &&
                        (pcp           == o.pcp)) {
                        return true;
                    }
                    break;
            }
            return false;
        }

        /* This comparison function works only for OF v1.0. */
        private boolean compareWithFce(OFMatchWithSwDpid ofmWithSwDpid, Short pri) {

            /* First check the wildcards since that determines how rest of the
             * OFMatch fields are relevant for equals comparison
             *
             * Later versions of OVS switch (e.g. the one bundled with 
             * big-switch controller doesn't return the exact same wildcard that
             * was sent when flow-mod was programmed.
             * Offical openflow vm does return the same wildcard though
             * Openflow spec. 1.0 requires that the wildcard, match and priority
             * fields be preserved across flow-mod program and removal messages
             * In order to work with OVS, making some adjustments to the 
             * wildcard in ofm received in flow-removal message. Correctness is
             * maintained.
             */
            int ofmWildcards = ofmWithSwDpid.getOfMatch().getWildcards();
            ofmWildcards |= 0x100000; /* Since VLAN is wildcarded */
            if ((ofmWildcards & OFMatch.OFPFW_NW_SRC_ALL) != 0) {
                /* Here 0x100000 is same as 0x1XXXXX */
                ofmWildcards |= OFMatch.OFPFW_NW_SRC_MASK;
            }
            if ((ofmWildcards & OFMatch.OFPFW_NW_DST_ALL) != 0) {
                /* Here 0x100000 is same as 0x1XXXXX */
                ofmWildcards |= OFMatch.OFPFW_NW_DST_MASK;
            }

            if (srcSwitchDpid != ofmWithSwDpid.getSwitchDataPathId()) {
                return false;
            }
            
            if (wildcards != ofmWildcards)  {
                return false;
            }

            switch (wildcards) {
                case WILD_MATCH_INP_VLAN_DLADRS: /* Edge ports */
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (ofPri         == pri)) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ET: /* permit ip any any */
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (ofPri         == pri)) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ET_PROTO_DP:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (destL4Port    == ofmWithSwDpid.getOfMatch().getTransportDestination()) &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (ofPri         == pri)                           &&
                        (protocol      == ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ET_PROTO:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (ofPri         == pri)                           &&
                        (protocol      == ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return true;
                    }
                    break;
                case WILD_MATCH_VLAN_DLSADR: // Core or internal ports
                    if (srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId()) {
                        return true;
                    }
                    break;
                case WILD_MATCH_INP_VLAN_DLADRS_ETYP_NWADRS:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (srcIpAddr     == ofmWithSwDpid.getOfMatch().getNetworkSource())        &&
                        (destIpAddr    == ofmWithSwDpid.getOfMatch().getNetworkDestination())   &&
                        (ofPri         == pri)) {
                        return true;
                    }
                    break;
                case WILD_PCP_TOS_L4PSPRT:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (destL4Port    == ofmWithSwDpid.getOfMatch().getTransportDestination()) &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (srcIpAddr     == ofmWithSwDpid.getOfMatch().getNetworkSource())        &&
                        (destIpAddr    == ofmWithSwDpid.getOfMatch().getNetworkDestination())   &&
                        (ofPri         == pri)                           &&
                        (protocol      == ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return true;
                    }
                    break;
                case WILD_PCP_TOS_L4PDPRT:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (srcL4Port     == ofmWithSwDpid.getOfMatch().getTransportSource())      &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (srcIpAddr     == ofmWithSwDpid.getOfMatch().getNetworkSource())        &&
                        (destIpAddr    == ofmWithSwDpid.getOfMatch().getNetworkDestination())   &&
                        (ofPri         == pri)                           &&
                        (protocol      == ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return true;
                    }
                    break;
                case WILD_PCP_L3SADR_TOS_L4SPRT:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (destL4Port    == ofmWithSwDpid.getOfMatch().getTransportDestination()) &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (destIpAddr    == ofmWithSwDpid.getOfMatch().getNetworkDestination())   &&
                        (ofPri         == pri)                           &&
                        (protocol      == ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return true;
                    }
                    break;
                case WILD_PCP_L3DADR_TOS_L4DPRT:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (srcL4Port     == ofmWithSwDpid.getOfMatch().getTransportSource())      &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (ofPri         == pri)                           &&
                        (protocol      == ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return true;
                    }
                    break;
                case WILD_PCP_L3PROT_TOS:
                    if ((srcSwitchDpid == ofmWithSwDpid.getSwitchDataPathId())     &&
                        (srcL4Port     == ofmWithSwDpid.getOfMatch().getTransportSource())      &&
                        (destL4Port    == ofmWithSwDpid.getOfMatch().getTransportDestination()) &&
                        (inputPort     == ofmWithSwDpid.getOfMatch().getInputPort())            &&
                        (etherType     == ofmWithSwDpid.getOfMatch().getDataLayerType())        &&
                        (srcIpAddr     == ofmWithSwDpid.getOfMatch().getNetworkSource())        &&
                        (destIpAddr    == ofmWithSwDpid.getOfMatch().getNetworkDestination())   &&
                        (ofPri         == pri)){
                        return true;
                    }
                    break;
                default:
                    if (logger.isTraceEnabled()) {
                        logger.trace("Unexpected wildcard {}  in Match {}",
                                                            wildcards, ofmWithSwDpid);
                    }
                    if (srcSwitchDpid != ofmWithSwDpid.getSwitchDataPathId()) {
                        return false;
                    }
                    if (((wildcards & OFMatch.OFPFW_IN_PORT) == 0) &&
                         (inputPort     != ofmWithSwDpid.getOfMatch().getInputPort())) {
                        return false;
                    }
                    if (((wildcards & OFMatch.OFPFW_TP_DST) == 0) &&
                        (destL4Port    != ofmWithSwDpid.getOfMatch().getTransportDestination())) {
                        return false;
                    }
                    if (((wildcards & OFMatch.OFPFW_TP_SRC) == 0) &&
                        (srcL4Port    != ofmWithSwDpid.getOfMatch().getTransportSource())) {
                        return false;
                    }
                    if (((wildcards & OFMatch.OFPFW_DL_TYPE) == 0) &&
                        (etherType    != ofmWithSwDpid.getOfMatch().getDataLayerType())) {
                        return false;
                    }
                    if (((wildcards & OFMatch.OFPFW_NW_PROTO) == 0) &&
                        (protocol    != ofmWithSwDpid.getOfMatch().getNetworkProtocol())) {
                        return false;
                    }
                    if (((wildcards & OFMatch.OFPFW_NW_TOS) == 0) &&
                        (nwTos    != ofmWithSwDpid.getOfMatch().getNetworkTypeOfService())) {
                        return false;
                    }
                    if (ofPri != pri) {
                        return false;
                    }
                    int nwAddrPrefixLen = Math.max(32 -
                        ((wildcards & OFMatch.OFPFW_NW_SRC_MASK) >>
                          OFMatch.OFPFW_NW_SRC_SHIFT), 0);
                    int mask = ~((1 << (32 - nwAddrPrefixLen)) - 1);
                    if ((srcIpAddr & mask) != (ofmWithSwDpid.getOfMatch().getNetworkSource() & mask)) {
                        return false;
                    }
                    nwAddrPrefixLen = Math.max(32 -
                            ((wildcards & OFMatch.OFPFW_NW_DST_MASK) >>
                              OFMatch.OFPFW_NW_DST_SHIFT), 0);
                    mask = ~((1 << (32 - nwAddrPrefixLen)) - 1);
                    if ((destIpAddr & mask) !=
                        (ofmWithSwDpid.getOfMatch().getNetworkDestination() & mask)) {
                        return false;
                    }
                    return true;
            }
            return false;
        }

        //****************************************
        // Getters for REST API, Custom Serializer
        //****************************************
        /**
         * Get the source switch's data-path id in the flow cache entry object
         * @return data path id of the source switch of the flow in its cluster
         */
        public long getSrcSwitchDpid() {
            return srcSwitchDpid;
        }
        /**
         * Get the source transport port in the flow cache entry object
         * @return source transport port
         */
        public short getSrcL4Port() {
            return srcL4Port;
        }
        /**
         * Get the destination transport port in the flow cache entry object
         * @return destination transport port
         */
        public short getDestL4Port() {
            return destL4Port;
        }
        /**
         * Get the source switch's input port in the flow cache entry object
         * @return input port of the source switch of the flow
         */
        public short getInputPort() {
            return inputPort;
        }
        /**
         * Get the Ether type in the flow cache entry object
         * @return ether type of the flow
         */
        public short getEtherType() {
            return etherType;
        }
        /**
         * Get the source network address of the flow cache entry object
         * @return source network address of the flow
         */
        public int getSrcIpAddr() {
            return srcIpAddr;
        }
        /**
         * Get the destination network address of the flow cache entry object
         * @return destination network address of the flow
         */
        public int getDestIpAddr() {
            return destIpAddr;
        }
        /**
         * Get the wildcard field in the flow cache entry object
         * @return the wildcard field of the flow
         */
        public int getWildcards() {
            return wildcards;
        }
        /**
         * Get the flow-mod priority the flow cache entry object
         * @return flow-mod priority
         */
        public short getOfPri() {
            return ofPri;
        }
        /**
         * Get the network protocol field in the flow entry object
         * @return network protocol field
         */
        public byte getProtocol() {
            return protocol;
        }
        /**
         * Get the state of the flow such as FCStateACTIVE, FCStateINACTIVE, 
         * FCStateUNUSED.
         * @return state of the flow
         */
        public byte getState() {
            return state;
        }
        /**
         * Get the PERMIT or DENY action associated with the flow entry
         * @return permit or deny action of the flow entry
         */
        public byte getAction() {
            return action;
        }
        /**
         * Get the network type of service field in the flow entry
         * @return network type of service field
         */
        public byte getNwTos() {
            return nwTos;
        }
        /**
         * Get the 802.1Q priority code point in the flow entry
         * @return priority code point 
         */
        public byte getPcp() {
            return pcp;
        }

        public byte getScanCount() {
            return scanCnt;
        }
        
        @Override
        public String toString() {
            String str = "SwDpid: " + HexString.toHexString(srcSwitchDpid) + " ";
            str += "srcL4Port: " + srcL4Port + " ";
            str += "destL4Port: " + destL4Port + " ";
            str += "inputPort: " + inputPort + " ";
            str += "etherType: " + etherType + " ";
            str += "srcIpAddr: " + IPv4.fromIPv4Address(srcIpAddr) + " ";
            str += "destIpAddr: " + IPv4.fromIPv4Address(destIpAddr) + " ";
            str += "wildcards: 0x" + Integer.toHexString(wildcards);
            str += "ofPri: " + ofPri + " ";
            str += "protocol: " + protocol + " ";
            str += "state: " + state + " ";
            str += "action: " + action + " ";
            str += "nwTos: " + nwTos + " ";
            str += "pcp: " + pcp + " ";
            str += "scanCnt: " + scanCnt;
            
            return str;
        }
    }

    private FCEntry getFCEntryByOFMatchAndPriority(OFMatchWithSwDpid ofmWithSwDpid, short pri) {

        if (fce.compareWithFce(ofmWithSwDpid, pri)) {
            return fce;
        }

        if (fceList != null) {
            for (int idx=0; idx < fceList.size(); idx++) {
                if (fceList.get(idx).compareWithFce(ofmWithSwDpid, pri)) {
                    return fceList.get(idx);
                }
            }
        }
        // Not found
        return null;
    }

    /**
     * Returns FCEntry if it is in the FlowCacheObj otherwise returns null
     * @param ofmWithSwDpid
     * @param priority
     * @param action
     * @return flow cache entry object
     */
    private FCEntry getFCEntry(OFMatchWithSwDpid ofmWithSwDpid, short priority, byte action) {

        if (fce.compareWithFce(ofmWithSwDpid, priority) && (fce.action == action)) {
            return fce;
        }

        if (fceList != null) {
            FCEntry tempFce;
            int fceListSize = fceList.size();
            for (int idx=0; idx < fceListSize; idx++) {
                tempFce = fceList.get(idx);
                if (tempFce.compareWithFce(ofmWithSwDpid, priority) && 
                        (tempFce.action == action)) {
                    return tempFce;
                }
            }
        }
        // Not found
        return null;
    }

    protected FCEntry getFCEntryWithCookie(
            OFMatchWithSwDpid ofmWithSwDpid, long cookie, short priority, byte action) {
        if (this.cookie != cookie) {
            return null;
        } 
        return getFCEntry(ofmWithSwDpid, priority, action);
    }

    private FCEntry findFreeFce() {
        // Only fce entry can be in unused state. Entries in fceList are in
        // either active or inactive state
        if (fce.state != FCStateACTIVE) {
            return fce;
        }
        if (fceList != null) {
            int fceListSize = fceList.size();
            for (int idx=0; idx < fceListSize; idx++) {
                if (fceList.get(idx).state == FCStateINACTIVE) {
                    return fceList.get(idx);
                }
            }
        }
        // No free entry found
        return null;
    }

    private void initFCObj(OFMatchWithSwDpid ofmWithSwDpid, long cookie,
                                            short priority, byte action) {
        fceList      = null;
        fce.fcEntryInit(ofmWithSwDpid, priority, action);
        fce.state    = FCStateACTIVE;
        this.cookie  = cookie;
    }

    /**
     * Stores a flow in the flow object
     * @param ofmWithSwDpid the flow match structure
     * @param cookie the flow-mod cookie
     * @param priority the flow mod priority
     * @param action the flow mod action - PERMIT or DENY
     * @param bfc the flow cache object into which the flow if to be inserted
     * @return
     */
    protected FCOper storeFCEntry(OFMatchWithSwDpid ofmWithSwDpid, Long cookie, 
                            short priority, byte action, BetterFlowCache bfc) {

        /* Get the time. We seem to need it in all cases! */
        long curTimeNs = System.nanoTime();
        /* If Cookie is different then reset the FlowCacheObj and 
         * add this entry
         */
        if (this.cookie != cookie) {
            /* TODO - check match */
            initFCObj(ofmWithSwDpid, cookie, priority, action);
            installTimeNs = curTimeNs;
            return FCOper.NEW_ENTRY;
        }

        /* Cookie is same - check if the FC entry is already there */
        FCEntry tempFce = getFCEntry(ofmWithSwDpid, priority, action);

        if (tempFce != null) {
            /* Entry already there, see if this add need to be dampened */

            if (tempFce.state == FCStateINACTIVE) {
                /* Flow cache is in removed state. This means that the flow
                 * cache entry was not deleted to avoid frequent creation and
                 * deletion of objects. Make it active
                 */
                tempFce.state = FCStateACTIVE;
                installTimeNs = curTimeNs; 
                return FCOper.ACTIVATED;
            }

            /* Flow mod is in flow cache and it is in active state */
            if ((curTimeNs - installTimeNs) < DAMPEN_TIMEOUT_NS) {
                /* flow mod was installed less than DAMPEN_TIMEOUT_NS.
                 * Dampen this flow mod, so that it is not installed again now
                 */
                return FCOper.DAMPENED; 
            } else {
                installTimeNs = curTimeNs;
                return FCOper.NOT_DAMPENED;
            }
        }

        /* New flow mod, store it
         * Check if the OF Match exists (action is different)
         */
        installTimeNs = curTimeNs;

        tempFce = getFCEntryByOFMatchAndPriority(ofmWithSwDpid, priority);
        if (tempFce != null) {
            /* Found FC Entry with same OFMatch, update this entry */
            if (tempFce.state == FCStateINACTIVE) {
                tempFce.state    = FCStateACTIVE;
                tempFce.action   = action;
                return FCOper.ACTIVATED;
            } else {
                // Entry already in active state
                tempFce.action   = action;
                return FCOper.NOP;
            }
        }

        /* OF Match doesn't exist add it to flow cache fce is never set to 
         * null, instead the whole FlowCacheObj would be deleted
         */
        tempFce = findFreeFce(); /* Returns an entry in inactive state */

        if (tempFce != null) {
            byte state = tempFce.state;
            tempFce.fcEntryInit(ofmWithSwDpid, priority, action);
            if (state == FCStateINACTIVE) {
                return FCOper.ACTIVATED;
            } else {
                return FCOper.NEW_ENTRY;
            }
        } else {
            /* Check if the flow cache is full */
            if (bfc.getBfcCore().isFlowCacheFull()) {
                /* Update the cookie indicating that the flow is not stored in 
                 * the cache
                 * cookie |= FC_COOKIE_NOT_FLOW_STORED_IN_CACHE;
                 */
                return FCOper.NOT_STORED_FULL;
            } else { /* Flow cache is not full */
                /* fce was not free, no free entry found in array or array 
                 * was null create a new FCEntry and store it in array
                 */
                tempFce = new FCEntry(ofmWithSwDpid, priority, action);
                if (fceList == null) {
                    fceList = new ArrayList<FCEntry>();
                }
                fceList.add(tempFce);
                return FCOper.NEW_ENTRY;
            }
        }

    }

    /**
     * Remove FCEntry - this method doesn't actually remove the entry from the
     * flow cache. It marks the entry, if found, inactive. If the same flow is
     * installed soon enough then a bunch of objects can be reused instead of
     * being recreated. All inactive entries are removed every time flow-cache
     * scan-interval period. (Flow cache is scanned periodically to take care 
     * of entries for which flow-mod removal messages were lost.
     * @param ofmWithSwDpid
     * @param priority
     * @return
     */
    protected FCOper removeFCEntry(OFMatchWithSwDpid ofmWithSwDpid, short priority) {

        /* Get the entry */
        FCEntry tempFce = getFCEntryByOFMatchAndPriority(ofmWithSwDpid, priority);
        if (tempFce != null) {
            if (tempFce.state != FCStateINACTIVE) {
                if (logger.isTraceEnabled()) {
                    logger.trace("removeFCEntry: inactivate flow fce {}", tempFce);
                }
                tempFce.state = FCStateINACTIVE;
                tempFce.scanCnt = 0;
                return FCOper.DEACTIVATED;
            } else { // entry already in inactive state
                return FCOper.NOT_ACTIVE;
            }

        }
        return FCOper.NOT_FOUND;
    }

    /**
     * Delete FCEntry - this method actually removes the entry from the
     * flow cache.
     * @param ofmWithSwDpid
     * @param priority
     * @return
     */
    protected FCOper deleteFCEntry(OFMatchWithSwDpid ofmWithSwDpid, short priority,
                                                        BetterFlowCache bfc) {
        boolean found = false;
        /* Check if fce is this entry */
        if (fce.compareWithFce(ofmWithSwDpid, priority)) {
            /* found the entry */
            if ((fceList == null) || fceList.isEmpty()) {
                /* adjust count since the FlowCacheObj would be deleted */
                if (fce.state == FCStateACTIVE) {
                    bfc.getBfcCore().updateCountsLocal(FCOper.DELETED_ACTIVE);
                } else {
                    bfc.getBfcCore().updateCountsLocal(FCOper.DELETED_INACTIVE);
                }
                return FCOper.FCOBJ_FREE;
            } else {
                if (fce.state == FCStateACTIVE) {
                    /* FlowCacheObj can't be freed */
                    if (logger.isTraceEnabled()) {
                        logger.trace("deleteFCEntry: inactivate flow fce {}",
                                     fce);
                    }
                    fce.state = FCStateINACTIVE;
                    fce.scanCnt = 0;
                    return FCOper.DEACTIVATED;
                } else {
                    return FCOper.NOT_ACTIVE;
                }
            }
        } else {
            /* Not found in fce, check fceList */
            if (fceList != null) {
                /* Check if it is in the array
                 * User iterator since we may have to delete the entry */
                ListIterator< FCEntry> iter = fceList.listIterator();
                while (iter.hasNext()) {
                    FCEntry e = iter.next();
                    if (e.compareWithFce(ofmWithSwDpid, priority)) {
                        // Found the entry
                        found = true;
                        if (e.state == FCStateACTIVE) {
                            bfc.getBfcCore()
                            .updateCountsLocal(FCOper.DELETED_ACTIVE);
                        } else {
                            bfc.getBfcCore()
                            .updateCountsLocal(FCOper.DELETED_INACTIVE);
                        }
                        iter.remove();
                        break;
                    }
                }
            }
            if (found) {
                if ((fce.state != FCStateACTIVE) && (fceList.isEmpty())) {
                    fceList = null;
                    return FCOper.FCOBJ_FREE;
                }
                return FCOper.NOP;
            } else {
                return FCOper.NOT_FOUND;
            }
        }
    }

    /**
     * Delete FCEntry if the source switch of the entry matches a given switch
     * - this method actually removes the entry from the flow cache.
     * @param switch id
     * @return
     */
    protected FCOper deleteFCEntry(long swId, BetterFlowCache bfc) {
        /* Check if fce is on the given switch id. */
        int numRemovedActiveFlows = 0;
        int numRemovedInactiveFlows = 0;
        int numDelFlows = 0;
        if (fce.srcSwitchDpid == swId) {
            /* found the entry */
            if (fce.state == FCStateACTIVE) {
                if (logger.isTraceEnabled()) {
                    logger.trace("deleteFCEntry 2: inactivate flow fce {}", fce);
                }
                fce.state = FCStateINACTIVE;
                numRemovedActiveFlows++;
                fce.scanCnt = 0;
            } else if (fce.state == FCStateINACTIVE) {
                numRemovedInactiveFlows++;
            }
            numDelFlows++;
        }

        /* Check fceList */
        if (fceList != null) {
            /* Check if it is in the array
             * User iterator since we may have to delete the entry */
            ListIterator< FCEntry> iter = fceList.listIterator();
            while (iter.hasNext()) {
                FCEntry e = iter.next();
                if (e.srcSwitchDpid == swId) {
                    // match found
                    switch (e.state) {
                        case FCStateACTIVE:
                            numRemovedActiveFlows++;
                            break;

                        case FCStateINACTIVE:
                            numRemovedInactiveFlows++;
                            break;

                        default:
                            break;
                    }
                    numDelFlows++;
                    iter.remove();
                }
            }
        }

        bfc.getBfcCore().fcLocalCounters.get().activeCnt -= numRemovedActiveFlows;
        bfc.getBfcCore().fcLocalCounters.get().inactiveCnt -= numRemovedInactiveFlows;
        bfc.getBfcCore().fcLocalCounters.get().delCnt += numDelFlows;
        
        if (fce.srcSwitchDpid == swId &&
            ((fceList == null) || fceList.isEmpty())) {
            return FCOper.FCOBJ_FREE;
        }

        return FCOper.NOP;
    }

    /**
     * Deactivate stale FCEntry - entries with scanCount greater than 2.
     * 
     * @return is the flow cache object can be deleted from map or not
     */
    protected FCOper deactivateStaleFlows(int staleScanCnt, BetterFlowCache bfc) {
        int numDeletedFlows = 0;
        /* Check if fce is stale. Consider only active flows */
        if (fce.state == FCStateACTIVE) {
            if (fce.scanCnt > staleScanCnt) {
                /* found the entry */
                if (logger.isTraceEnabled()) {
                    logger.trace("deleteStaleFlows: inactivate flow fce {}", fce);
                }
                fce.state = FCStateINACTIVE;
                fce.scanCnt = 0;
                numDeletedFlows++;
            } else {
                fce.scanCnt++;
            }
        }

        /* Check fceList */
        if (fceList != null) {
            /* Check if it is in the array
             * User iterator since we may have to delete the entry */
            ListIterator< FCEntry> iter = fceList.listIterator();
            while (iter.hasNext()) {
                FCEntry e = iter.next();
                if (e.state == FCStateACTIVE) {
                    if (e.scanCnt > staleScanCnt) {
                        e.state = FCStateINACTIVE;
                        e.scanCnt = 0;
                        numDeletedFlows++;
                    } else {
                        e.scanCnt++;
                    }
                }
            }
        }
        
        if (numDeletedFlows > 0) {
            bfc.getBfcCore().fcLocalCounters.get().activeCnt -= numDeletedFlows;
            bfc.getBfcCore().fcLocalCounters.get().inactiveCnt += numDeletedFlows;
        }
        
        return FCOper.NOP;
    }


    /**
     * Removes all inactive entries from fceList
     * @return true if neither of fce and fceList contain any ACTIVE flow
     *         false otherwise 
     */
    protected boolean deleteInactiveFlows() {
        if (fceList != null) {
            ListIterator< FCEntry> iter = fceList.listIterator();
            while (iter.hasNext()) {
                if (iter.next().state != FCStateACTIVE) {
                    iter.remove();
                }
            }
        }
        if (((fceList == null) || fceList.isEmpty()) && 
                                        (fce.state != FCStateACTIVE)) {
            return true; /* this FlowCacheObj can be freed */
        }
        return false;
    }

    /**
     * Removes all inactive entries from fceList
     * @return true if neither of fce and fceList contain any ACTIVE flow
     *         false otherwise 
     */
    protected boolean deleteFlowsBySwitch(long switchDpid, BetterFlowCache bfc) {
        int numActiveFlows = 0;
        int numInactiveFlows = 0;
        if ((fce.state == FCStateACTIVE) && 
            (fce.srcSwitchDpid == switchDpid)) {
            if (logger.isTraceEnabled()) {
                logger.trace("deleteFlowsBySwitch: inactivate flow fce {}", fce);
            }
            fce.state = FCStateINACTIVE;
            fce.scanCnt = 0;
            numActiveFlows++;
            numInactiveFlows++;
        }
        if (fceList != null) {
            ListIterator< FCEntry> iter = fceList.listIterator();
            while (iter.hasNext()) {
                FCEntry e = iter.next();
                if (e.srcSwitchDpid == switchDpid) {
                    if (e.state == FCStateACTIVE) {
                        numActiveFlows++;
                    } else {
                        numInactiveFlows--;
                    }
                    iter.remove();
                }
            }
        }
        
        if (((fceList == null) || fceList.isEmpty()) &&
                                        (fce.state != FCStateACTIVE)) {
            if (numActiveFlows > 0) {
                bfc.getBfcCore().fcLocalCounters.get().activeCnt -= numActiveFlows;
            }
            return true; /* this FlowCacheObj can be freed */
        } else {
            bfc.getBfcCore().fcLocalCounters.get().activeCnt -= numActiveFlows;
            bfc.getBfcCore().fcLocalCounters.get().inactiveCnt += numInactiveFlows;
            return false;
        }
    }
}
