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

import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.ListenerContextStore;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction;
import org.sdnplatform.routing.Route;



/**
 * Constants and methods for creating and  annotating the SDN Platform context for
 * "Explain Packet." Explain packet is a test packet that is injected in the
 * regular packet in processing path via a REST call or CLI. The REST API handler
 * creates a Listener Context in org.sdnplatform.netVirt.web.ExplainResource.java
 * and injects the packet in to the packet in processing chain. The components in the
 * processing chain treat this test packet as a regular packet in except that no
 * flow-mod is programmed and the becon context is annotated with information that are
 * returned to the API caller. Annotated information include the ACLs hit, route
 * computed, netVirt chosen etc. If the destination mac is unknown and if there is a valid
 * IP address then ARPManager would process the test packet as usual, including sending
 * ARP packet and programming ARP flow-mods.
 * 
 * @author subrata
 */
public class NetVirtExplainPacket {

    public static class ExplainPktRoute {
        public static class OneCluster {
            public Long clusterNumber;
            public SwitchPort srcDap;
            public SwitchPort dstDap;
            public Route route;
        }

        public Integer numClusters=0;
        public ArrayList<OneCluster> oc = new ArrayList<OneCluster>(); 

        // the route contains input switch-port and output switch-port.
        public static void ExplainPktAddRouteToContext(ListenerContext cntx,
                                                       Route route,
                                                       Long clusterNum,
                                                       SwitchPort srcDap,
                                                       SwitchPort dstDap) {
            ExplainPktRoute epr = 
                    NetVirtExplainPacket.ExplainRouteStore.
                        get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE);
            if (epr != null) {
                OneCluster oc = new OneCluster();
                oc.clusterNumber = clusterNum;
                oc.srcDap = srcDap;
                oc.dstDap = dstDap;
                oc.route  = route;
                epr.oc.add(oc);
                epr.numClusters++;
            }
        }
    }

    public static class ExplainPktVRouting {
        public static class OneNetVirtIfacePair {
            public VNSInterface srcIface;
            public VNSInterface dstIface;
            public ForwardingAction act;
        }

        public Integer numIterations=0;
        public ArrayList<OneNetVirtIfacePair> arr = new ArrayList<OneNetVirtIfacePair>();

        // the route contains input switch-port and output switch-port.
        public static void ExplainPktAddVRouteToContext(ListenerContext cntx,
                                                        VNSInterface srcIface,
                                                        VNSInterface dstIface,
                                                        ForwardingAction act) {
            ExplainPktVRouting vr =
                    NetVirtExplainPacket.VRoutingStore.
                        get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_VROUTING);
            if (vr != null) {
                OneNetVirtIfacePair ovr = new OneNetVirtIfacePair();
                ovr.srcIface = srcIface;
                ovr.dstIface = dstIface;
                ovr.act = act;
                vr.arr.add(ovr);
                vr.numIterations++;
            }
        }
    }

    public static final String KEY_EXPLAIN_PKT = 
            "org.sdnplatform.netVirt.core.keyExplainPkt";
    public static final String VAL_EXPLAIN_PKT = 
            "org.sdnplatform.netVirt.core.valExplainPkt";
    public static final String KEY_EXPLAIN_PKT_SRC_NetVirt = 
            "org.sdnplatform.netVirt.core.keySrcNetVirtName";
    public static final String KEY_EXPLAIN_PKT_DST_NetVirt = 
            "org.sdnplatform.netVirt.core.keyDstNetVirtName";
    public static final String KEY_EXPLAIN_PKT_ROUTE = 
            "org.sdnplatform.netVirt.core.keyRoute";
    public static final String KEY_EXPLAIN_PKT_ACTION  = 
            "org.sdnplatform.netVirt.core.keyAction";
    public static final String KEY_EXPLAIN_PKT_INP_ACL_NAME = 
            "org.sdnplatform.netVirt.core.keyInputAclName";
    public static final String KEY_EXPLAIN_PKT_INP_ACL_RESULT  = 
            "org.sdnplatform.netVirt.core.keyInputAclResult";
    public static final String KEY_EXPLAIN_PKT_OUT_ACL_NAME = 
            "org.sdnplatform.netVirt.core.keyOutputAclName";
    public static final String KEY_EXPLAIN_PKT_OUT_ACL_RESULT = 
            "org.sdnplatform.netVirt.core.keyInputAclResult";
    public static final String KEY_EXPLAIN_PKT_INP_ACL_ENTRY = 
            "org.sdnplatform.netVirt.core.keyInputAclEntry";
    public static final String KEY_EXPLAIN_PKT_OUT_ACL_ENTRY = 
            "org.sdnplatform.netVirt.core.keyOutputAclEntry";
    public static final String KEY_EXPLAIN_PKT_SERVICE_NAME = 
            "org.sdnplatform.netVirt.core.serviceName";
    public static final String KEY_EXPLAIN_PKT_SERVICE_NODE = 
            "org.sdnplatform.netVirt.core.serviceNode";
    public static final String KEY_EXPLAIN_PKT_VROUTING =
            "org.sdnplatform.netVirt.core.keyVRouting";
    
    public static final ListenerContextStore<String> ExplainStore =
        new ListenerContextStore<String>();
    
    public static final ListenerContextStore<ExplainPktRoute> ExplainRouteStore =
        new ListenerContextStore<ExplainPktRoute>();
    
    public static final ListenerContextStore<ExplainPktVRouting> VRoutingStore =
            new ListenerContextStore<ExplainPktVRouting>();

    public static boolean isExplainPktCntx(ListenerContext cntx) {
        if (cntx == null) {
            return false;
        }
        String explPkt = ExplainStore.get(cntx, KEY_EXPLAIN_PKT);
        if (explPkt != null) {
            if (explPkt.equals(VAL_EXPLAIN_PKT)) {
                return true;
            }
        }
        return false;
    }
    
    public static void explainPacketSetContext(ListenerContext cntx, String key, String value) {
        if (isExplainPktCntx(cntx)) {
            // This is Listener Context for an explain packet, update the context
            ExplainStore.put(cntx, key, value);        
        }
    }
}
