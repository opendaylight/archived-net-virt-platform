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

package org.sdnplatform.netvirt.web;

import java.util.List;


import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.util.HexString;
import org.restlet.data.Status;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.devicemanager.IDeviceService;
import org.sdnplatform.devicemanager.SwitchPort;
import org.sdnplatform.netvirt.core.NetVirtExplainPacket;
import org.sdnplatform.packet.Data;
import org.sdnplatform.packet.Ethernet;
import org.sdnplatform.packet.ICMP;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.packet.TCP;
import org.sdnplatform.packet.UDP;
import org.sdnplatform.routing.IRoutingDecision;
import org.sdnplatform.topology.NodePortTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Explain the policy decisions on a packet specified by the posted packet 
 * parameters
 * @author readams
 */
public class ExplainResource extends ServerResource {
    protected static Logger logger = 
                                LoggerFactory.getLogger(ExplainResource.class);
    byte[] ExplainPktSerialized;

    /**
     * The input packet parameters to be received via POST
     * @author readams, subrata
     */
    public static class PacketParameters {
        // Layer 2 parameters
        protected String destinationMACAddress = "00:00:00:00:00:00";
        protected String sourceMACAddress = "00:00:00:00:00:00";
        protected String priorityCode = "0";
        protected String vlanID = Integer.toString(Ethernet.VLAN_UNTAGGED);
        protected String etherType = Integer.toString(Ethernet.TYPE_IPv4);
        // Layer 3 parameters
        protected String sourceIpAddress = "0.0.0.0";
        protected String destinationIpAddress = "0.0.0.0";
        protected String ipv4Protocol = Integer.toString(IPv4.PROTOCOL_TCP);
        // Layer 4 parameters
        protected String ipL4SourcePort = "0";
        protected String ipL4DestinationPort = "0";
        // Switch and port from where the packet would have come 
        protected String srcSwitchDpid   = "00:00:00:00:00:00:00:00";
        protected String srcSwitchInPort = "0";
        protected String dstSwitchDpid   = "00:00:00:00:00:00:00:00";
        protected String dstSwitchInPort = "0";
        // setters and getters used by jackson.
        public String getDestinationMACAddress() {
            return destinationMACAddress;
        }
        public void setDestinationMACAddress(String destinationMACAddress) {
            this.destinationMACAddress = destinationMACAddress;
        }    
        public String getSourceMACAddress() {
            return sourceMACAddress;
        }
        public void setSourceMACAddress(String sourceMACAddress) {
           this.sourceMACAddress = sourceMACAddress;
        }
        public String getPriorityCode() {
            return priorityCode;
        }
        public void setPriorityCode(String priorityCode) {
            this.priorityCode = priorityCode;
        }
        public String getVlanID() {
            return vlanID;
        }
        public void setVlanID(String vlanID) {
            this.vlanID = vlanID;
        }
        public String getEtherType() {
            return etherType;
        }
        public void setEtherType(String etherType) {
            this.etherType = etherType;
        }
        public String getSourceIpAddress() {
            return sourceIpAddress;
        }
        public void setSourceIpAddress(String sourceIpAddress) {
            this.sourceIpAddress = sourceIpAddress;
        }
        public String getDestinationIpAddress() {
            return destinationIpAddress;
        }
        public void setDestinationIpAddress(String destinationIpAddress) {
            this.destinationIpAddress = destinationIpAddress;
        }
        public String getIpv4Protocol() {
            return ipv4Protocol;
        }
        public void setIpv4Protocol(String ipv4Protocol) {
            this.ipv4Protocol = ipv4Protocol;
        }
        public String getIpL4SourcePort() {
            return ipL4SourcePort;
        }
        public void setIpL4SourcePort(String ipL4SourcePort) {
            this.ipL4SourcePort = ipL4SourcePort;
        }     
        public String getIpL4DestinationPort() {
            return ipL4DestinationPort;     
        }
        public void setIpL4DestinationPort(String ipL4DestinationPort) {
            this.ipL4DestinationPort = ipL4DestinationPort;     
        }
        public String getSrcSwitchDpid() {
            return srcSwitchDpid;
        }
        public void setSrcSwitchDpid(String srcSwitchDpid) {
            this.srcSwitchDpid = srcSwitchDpid;     
        }     
        public String getSrcSwitchInPort() {
            return srcSwitchInPort;
        }    
        public void setSrcSwitchInPort(String inPort) {     
            this.srcSwitchInPort = inPort;     
        }
        public String getDstSwitchDpid() {
            return dstSwitchDpid;
        }
        public void setDstSwitchDpid(String dstSwitchDpid) {
            this.dstSwitchDpid = dstSwitchDpid;
        }
        public String getDstSwitchInPort() {
            return dstSwitchInPort;
        }
        public void setDstSwitchInPort(String dstSwitchInPort) {
            this.dstSwitchInPort = dstSwitchInPort;
        }
        
        @Override
        public String toString() {
            String vlanStr;

            if (vlanID.equals("-1")) {
                vlanStr = "Untagged";
            } else {
                vlanStr = vlanID;
            }

            return "[Dst. MAC Addr.="
                    + destinationMACAddress + ", Src MAC Addr.="
                    + sourceMACAddress + ", Priority=" + priorityCode
                    + ", VLAN=" + vlanStr + ", Ether Type=" + etherType
                    + ", Src IP Addr.=" + sourceIpAddress
                    + ", Dst IP Addr.=" + destinationIpAddress
                    + ", IP Protocol=" + ipv4Protocol + ", L4 Src Port="
                    + ipL4SourcePort + ", L4 Dest Port="
                    + ipL4DestinationPort + ", Src Switch DPID=" + srcSwitchDpid
                    + ", Src Switch Input Port=" + srcSwitchInPort + "]";
        }
    }

    /**
     * The output model that contains the result of the explain
     * @author readams
     */
    public static class ExplainOutput {
        public static class Acl {
            protected String aclName;
            protected String aclEntry;
            protected String aclResult;

            public String getAclName() {
                return aclName;
            }
            public void setAclName(String aclName) {
                this.aclName = aclName;
            }
            public String getAclEntry() {
                return aclEntry;
            }
            public void setAclEntry(String aclEntry) {
                this.aclEntry = aclEntry;
            }
            public String getAclResult() {
                return aclResult;
            }
            public void setAclResult(String aclResult) {
                this.aclResult = aclResult;
            }
        }

        public static class ExpRoute {
            public static class RouteEntry {
                String  dpid;
                short   inPort;
                short   outPort;
                
                public RouteEntry(String dpid, short inPort, short outPort) {
                    this.dpid = dpid;
                    this.inPort = inPort;
                    this.outPort = outPort;
                }
                
                public String getDpid() {
                    return dpid;
                }
                public void setDpid(String dpid) {
                    this.dpid = dpid;
                }
                public short getInPort() {
                    return inPort;
                }
                public void setInPort(short inPort) {
                    this.inPort = inPort;
                }
                public short getOutPort() {
                    return outPort;
                }
                public void setOutPort(short outPort) {
                    this.outPort = outPort;
                }
            }
            protected long clusterNum;
            protected int numSwitches;
            protected RouteEntry[] path;

            public long getClusterNum() {
                return clusterNum;
            }
            public void setClusterNum(long clusterNum) {
                this.clusterNum = clusterNum;
            }
            public int getNumSwitches() {
                return numSwitches;
            }
            public void setNumSwitches(int numSwitches) {
                this.numSwitches = numSwitches;
            }
            public RouteEntry[] getPath() {
                return path;
            }
            public void setPath(RouteEntry[] path) {
                this.path = path;
            }
        }

        public static class ExpVRouting {
            private String srcIface;
            private String dstIface;
            private String action;
            private String dropReason;
            private String nextHopIp;
            private String nextHopGatewayPool;

            public String getSrcIface() {
                return srcIface;
            }
            public void setSrcIface(String srcIface) {
                this.srcIface = srcIface;
            }
            public String getDstIface() {
                return dstIface;
            }
            public void setDstIface(String dstIface) {
                this.dstIface = dstIface;
            }
            public String getAction() {
                return action;
            }
            public void setAction(String action) {
                this.action = action;
            }
            public String getDropReason() {
                return dropReason;
            }
            public void setDropReason(String dropReason) {
                this.dropReason = dropReason;
            }
            public String getNextHopIp() {
                return nextHopIp;
            }
            public void setNextHopIp(String nextHopIp) {
                this.nextHopIp = nextHopIp;
            }
            public String getNextHopGatewayPool() {
                return nextHopGatewayPool;
            }
            public void setNextHopGatewayPool(String nextHopGatewayPool) {
                this.nextHopGatewayPool = nextHopGatewayPool;
            }
        }

        private PacketParameters ExplainPktParams;
        private String Status;
        private String Explanation;
        /* Number of times virtual routing was performed */
        private int numVRIterations;
        private ExpVRouting[] expPktVRouting;
        private String srcNetVirtName;
        private String destNetVirtName;
        private Acl    inputAcl;
        private Acl    outputAcl;
        private int    numClusters;
        private String routingAction;
        private ExpRoute[]  expPktRoute;
        private String serviceName;
        private String serviceNode;


        public String getServiceName() {
            return serviceName;
        }
        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
        public String getServiceNode() {
            return serviceNode;
        }
        public void setServiceNode(String serviceNode) {
            this.serviceNode = serviceNode;
        }
        public PacketParameters getExplainPktParams() {
            return ExplainPktParams;
        }
        public void setExplainPktParams(PacketParameters explainPktParams) {
            ExplainPktParams = explainPktParams;
        }
        public String getSrcNetVirtName() {
            return srcNetVirtName;
        }
        public void setSrcNetVirtName(String srcNetVirtName) {
            this.srcNetVirtName = srcNetVirtName;
        }
        public String getDestNetVirtName() {
            return destNetVirtName;
        }
        public void setDestNetVirtName(String destNetVirtName) {
            this.destNetVirtName = destNetVirtName;
        }
        public Acl getInputAcl() {
            return inputAcl;
        }
        public void setInputAcl(Acl inputAcl) {
            this.inputAcl = inputAcl;
        }
        public Acl getOutputAcl() {
            return outputAcl;
        }
        public void setOutputAcl(Acl outputAcl) {
            this.outputAcl = outputAcl;
        }
        public int getNumClusters() {
            return numClusters;
        }
        public void setNumClusters(int numClusters) {
            this.numClusters = numClusters;
        }
        public ExpRoute[] getExpPktRoute() {
            return expPktRoute;
        }
        public void setExpPktRoute(ExpRoute[] expPktRoute) {
            this.expPktRoute = expPktRoute;
        }
        public String getRoutingAction() {
            return routingAction;
        }
        public void setRoutingAction(String routingAction) {
            this.routingAction = routingAction;
        }
        public String getStatus() {
            return Status;
        }
        public void setStatus(String status) {
            Status = status;
        }
        public String getExplanation() {
            return Explanation;
        }
        public void setExplanation(String explanation) {
            Explanation = explanation;
        }
        public ExplainOutput() {
            inputAcl  = new Acl();
            outputAcl = new Acl();
        }
        public int getNumVRIterations() {
            return numVRIterations;
        }
        public void setNumVRIterations(int numVRIterations) {
            this.numVRIterations = numVRIterations;
        }
        public ExpVRouting[] getExpPktVRouting() {
            return expPktVRouting;
        }
        public void setExpPktVRouting(ExpVRouting[] expPktVRouting) {
            this.expPktVRouting = expPktVRouting;
        }
    }

    /**
     * 
     * @param param: Input parameters of packet fields
     * @return: packet-in to be injected in the pkt processing chain
     */

    private OFPacketIn CreateExplainPacket(PacketParameters param, 
            IControllerService controllerProvider) {

        Ethernet ExplainPkt = new Ethernet();
        ExplainPkt.setSourceMACAddress(param.sourceMACAddress);
        ExplainPkt.setDestinationMACAddress(param.destinationMACAddress);
        ExplainPkt.setEtherType(Short.parseShort(param.etherType));
        ExplainPkt.setVlanID(Short.parseShort(param.vlanID));
        ExplainPkt.setPriorityCode(Byte.parseByte(param.priorityCode));

        switch (Short.parseShort(param.etherType)) {
            case Ethernet.TYPE_IPv4:
                IPv4 ipv4Pkt = new IPv4();
                ExplainPkt.setPayload(ipv4Pkt);

                ipv4Pkt.setProtocol(Byte.parseByte(param.ipv4Protocol));
                ipv4Pkt.setSourceAddress(param.sourceIpAddress);
                ipv4Pkt.setDestinationAddress(param.destinationIpAddress);
                ipv4Pkt.setTtl((byte) 255);

                switch (Byte.parseByte(param.ipv4Protocol)) {
                    case IPv4.PROTOCOL_TCP:
                        TCP tcpPkt = new TCP();
                        ipv4Pkt.setPayload(tcpPkt);
                        tcpPkt.setSourcePort(
                                Short.parseShort(param.ipL4SourcePort));
                        tcpPkt.setDestinationPort(
                                Short.parseShort(param.ipL4DestinationPort));
                        break;

                    case IPv4.PROTOCOL_UDP: 
                        UDP udpPkt = new UDP();
                        ipv4Pkt.setPayload(udpPkt);
                        udpPkt.setSourcePort(
                                Short.parseShort(param.ipL4SourcePort));
                        udpPkt.setDestinationPort(
                                Short.parseShort(param.ipL4DestinationPort));
                        break;

                    case IPv4.PROTOCOL_ICMP:
                        ICMP icmpPkt = new ICMP();
                        ipv4Pkt.setPayload(icmpPkt);
                        icmpPkt.setIcmpType((byte) 8); // Type 8 = Echo
                        icmpPkt.setIcmpCode((byte) 0);
                        icmpPkt.setPayload(new Data(new byte[] {1, 2 , 3}));
                        break;
                }

                ExplainPktSerialized = ExplainPkt.serialize();
                break;

            case Ethernet.TYPE_ARP:
                // TBD
                // ARP arpPkt = new ARP();
                // ExplainPkt.setPayload(arpPkt);
                // Set the ARP pkt payload - TBD
                // TBD
                break;
        }

        OFPacketIn ExplainPacketIn = 
            (OFPacketIn) controllerProvider.getOFMessageFactory().
                                                getMessage(OFType.PACKET_IN);
        ExplainPacketIn.setInPort(Short.parseShort(param.srcSwitchInPort));
        ExplainPacketIn.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        ExplainPacketIn.setReason(OFPacketInReason.NO_MATCH);
        ExplainPacketIn.setPacketData(ExplainPktSerialized);
        ExplainPacketIn.setTotalLength((short) ExplainPktSerialized.length); // TODO - shouodn't this be minimum plus the serialized?
        // ExplainPacketIn.setLength((short)OFPacketIn.MINIMUM_LENGTH);
        return ExplainPacketIn;

    }

    @Post("json")
    public ExplainOutput handle(PacketParameters params) {
        IControllerService controllerProvider = 
                (IControllerService)getContext().getAttributes().
                    get(IControllerService.class.getCanonicalName());
        
        // Get the device manager handle
        IDeviceService deviceManager = 
                (IDeviceService)getContext().getAttributes().
                    get(IDeviceService.class.getCanonicalName());
        
        return handle(params, controllerProvider, deviceManager);
    }
    
    public ExplainOutput handle(PacketParameters params,
                                IControllerService controllerProvider,
                                IDeviceService deviceManager) {
        ExplainOutput output = new ExplainOutput();
        output.setExplainPktParams(params);

        // Get latest, unblocked attachment point if source switch was not
        // specified
        if (HexString.toLong(params.srcSwitchDpid) == 0) {
            long srcMac = HexString.toLong(params.sourceMACAddress);
            Short vlan = (params.vlanID != null)
                    ? Short.valueOf(params.vlanID)
                    : null;
            Integer ip = (params.sourceIpAddress != null)
                    ? IPv4.toIPv4Address(params.sourceIpAddress)
                    : null;
            Long dpid = (params.srcSwitchDpid != null)
                    ? HexString.toLong(params.srcSwitchDpid)
                    : null;
            Integer port = (params.srcSwitchInPort != null)
                    ? Integer.valueOf(params.srcSwitchInPort)
                    : null;           
            IDevice srcDev = deviceManager.findDevice(srcMac, vlan, ip, dpid, port);
            if (srcDev == null) {
                // Given Source device doesn't exist - return error
                output.Status = "ERROR";
                output.Explanation = "Source device not found";
                setStatus(Status.SUCCESS_OK);
                return output;
            }

            SwitchPort[] srcDaps = srcDev.getAttachmentPoints();
            if (srcDaps == null || srcDaps.length == 0) {
                // Given Source device's attachemnt point doesn't exist - 
                // return error
                output.Status = "ERROR";
                output.Explanation = 
                                "No attachemnt point found for source device";
                setStatus(Status.SUCCESS_OK);
                return output;
            }

            params.srcSwitchInPort = Integer.toString(srcDaps[0].getPort());
            params.srcSwitchDpid = Long.toString(srcDaps[0].getSwitchDPID());
        }

        // Check that the source switch is in the network
        IOFSwitch ofSwitch = controllerProvider.getSwitches().get(
                                    HexString.toLong(params.srcSwitchDpid));
        if (ofSwitch == null) {
            // Given Source Switch doesn't exist - return error
            output.Status = "ERROR";
            output.Explanation = "Source switch not found";
            setStatus(Status.SUCCESS_OK);
            return output;            
        }

        // Create a new listener context
        ListenerContext cntx = new ListenerContext();

        // set appropriate context 
        NetVirtExplainPacket.ExplainStore.put(cntx, 
                        NetVirtExplainPacket.KEY_EXPLAIN_PKT, NetVirtExplainPacket.VAL_EXPLAIN_PKT);
        NetVirtExplainPacket.ExplainPktRoute epr = new NetVirtExplainPacket.ExplainPktRoute();
        NetVirtExplainPacket.ExplainRouteStore.put(cntx, 
                            NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE, epr);
        NetVirtExplainPacket.ExplainPktVRouting vr =
                new NetVirtExplainPacket.ExplainPktVRouting();
        NetVirtExplainPacket.VRoutingStore.put(cntx,
                            NetVirtExplainPacket.KEY_EXPLAIN_PKT_VROUTING, vr);

        // Create the packet
        OFPacketIn ExplainPacketIn = CreateExplainPacket(params, controllerProvider);

        // Inject the ExplainPacket into the processing chain
        controllerProvider.injectOfMessage(ofSwitch, ExplainPacketIn, cntx);

        // Prepare the output
        String srcNetVirtName = NetVirtExplainPacket.ExplainStore.get(
                            cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_SRC_NetVirt);
        output.setSrcNetVirtName(srcNetVirtName);
        String dstNetVirtName = NetVirtExplainPacket.ExplainStore.get(
                            cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_DST_NetVirt);
        output.setDestNetVirtName(dstNetVirtName);
        String inAclName   = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_NAME);
        output.inputAcl.setAclName(inAclName); 
        String outAclName  = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_NAME);
        output.outputAcl.setAclName(outAclName); 
        String inAclEntry  = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_ENTRY);
        output.inputAcl.setAclEntry(inAclEntry); 
        String outAclEntry  = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_ENTRY);
        output.outputAcl.setAclEntry(outAclEntry); 
        String inAclResult   = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_INP_ACL_RESULT);
        output.inputAcl.setAclResult(inAclResult); 
        String outAclResult  = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_OUT_ACL_RESULT);
        output.outputAcl.setAclResult(outAclResult);
        String serviceNameResult  = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_SERVICE_NAME);
        output.setServiceName(serviceNameResult);
        String serviceNodeResult  = NetVirtExplainPacket.ExplainStore.get(
                        cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_SERVICE_NODE);
        output.setServiceNode(serviceNodeResult);
        IRoutingDecision vrd;
        vrd = IRoutingDecision.rtStore.get(
                                    cntx, IRoutingDecision.CONTEXT_DECISION);
        if (vrd != null) {
            output.setRoutingAction(vrd.getRoutingAction().toString());
        } else {
            String ExpPktAction = NetVirtExplainPacket.ExplainStore.get(cntx, 
                                    NetVirtExplainPacket.KEY_EXPLAIN_PKT_ACTION);
            output.setRoutingAction(ExpPktAction);
        }
        NetVirtExplainPacket.ExplainPktVRouting vrOut =
                NetVirtExplainPacket.VRoutingStore.
                    get(cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_VROUTING);
        if (vrOut != null) {
            output.numVRIterations = vrOut.numIterations;
            output.expPktVRouting =
                    new ExplainOutput.ExpVRouting[output.numVRIterations];
            for (int i = 0; i < output.numVRIterations; i++) {
                ExplainOutput.ExpVRouting evr = new ExplainOutput.ExpVRouting();
                evr.srcIface = vrOut.arr.get(i).srcIface.getName();
                if (vrOut.arr.get(i).dstIface != null)
                    evr.dstIface = vrOut.arr.get(i).dstIface.getName();
                else
                    evr.dstIface = "";
                evr.action = vrOut.arr.get(i).act.getAction().toString();
                evr.nextHopIp = IPv4.fromIPv4Address(vrOut.arr.get(i).act.
                                                     getNextHopIp());
                if (vrOut.arr.get(i).act.getNextHopGatewayPool() != null &&
                    vrOut.arr.get(i).act.getNextHopGatewayPoolRouter() != null) {
                    evr.nextHopGatewayPool = new StringBuilder()
                          .append(vrOut.arr.get(i).act.
                                  getNextHopGatewayPoolRouter().getName())
                          .append("|")
                          .append(vrOut.arr.get(i).act.getNextHopGatewayPool())
                          .toString();
                }
                evr.dropReason = vrOut.arr.get(i).act.getReasonStr();
                output.expPktVRouting[i] = evr;
            }
        }

        NetVirtExplainPacket.ExplainPktRoute eprOut = 
                NetVirtExplainPacket.ExplainRouteStore.get(
                                cntx, NetVirtExplainPacket.KEY_EXPLAIN_PKT_ROUTE);

        if (eprOut != null) {
            output.numClusters = eprOut.numClusters;

            // Check for errors
            if (output.numClusters == 0) {
                output.Status = "ERROR";
                output.Explanation = "Route not found";
                setStatus(Status.SUCCESS_OK);
                return output;
            }

            output.expPktRoute = new ExplainOutput.ExpRoute[output.numClusters];
            for (int idx=0; idx < eprOut.numClusters; idx++) {
                int pathSize=0;
                output.expPktRoute[idx] = new ExplainOutput.ExpRoute();
                (output.expPktRoute[idx]).clusterNum = eprOut.oc.get(idx).clusterNumber;
                //short srcPort = eprOut.oc[idx].srcDap.getSwitchPort().getPort();
                //long  srcDpid = eprOut.oc[idx].srcDap.getSwitchPort().getSw().getId();
                //short dstPort = eprOut.oc[idx].dstDap.getSwitchPort().getPort();
                //long  dstDpid = eprOut.oc[idx].dstDap.getSwitchPort().getSw().getId();
                // path size in terms of number of switches
                pathSize = eprOut.oc.get(idx).route.getPath().size();
                output.expPktRoute[idx].numSwitches = pathSize/2;
                output.expPktRoute[idx].path = 
                            new ExplainOutput.ExpRoute.RouteEntry[pathSize/2];

                // Add the source switch to the route
                if ((eprOut.oc.get(idx).srcDap != null) || 
                        (eprOut.oc.get(idx).dstDap != null)) {
                    List<NodePortTuple> nptList = 
                            eprOut.oc.get(idx).route.getPath();
                    int pidx = 0;
                    for (int x = 0; x < pathSize; x=x+2) {
                        // x and x+1 will have the same switch
                        long dpid = nptList.get(x).getNodeId();
                        short inPort = nptList.get(x).getPortId();
                        short outPort = nptList.get(x+1).getPortId();

                        output.expPktRoute[idx].path[pidx] =
                            new ExplainOutput.ExpRoute.RouteEntry(
                                HexString.toHexString(dpid), inPort, outPort);
                        pidx++;
                    }
                }
            }
        }

        setStatus(Status.SUCCESS_OK);
        output.Status = "SUCCESS";
        output.Explanation = "OK";

        return output;
    }
}
