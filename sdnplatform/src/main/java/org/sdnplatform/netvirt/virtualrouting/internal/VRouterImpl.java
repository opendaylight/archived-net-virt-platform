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

package org.sdnplatform.netvirt.virtualrouting.internal;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.List;

import org.sdnplatform.devicegroup.IDeviceGroup;
import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction;
import org.sdnplatform.netvirt.virtualrouting.GatewayNode;
import org.sdnplatform.netvirt.virtualrouting.IVRouter;
import org.sdnplatform.netvirt.virtualrouting.ForwardingAction.DropReason;
import org.sdnplatform.packet.IPv4;
import org.sdnplatform.routing.IRoutingDecision.RoutingAction;
import org.sdnplatform.util.IPV4Subnet;
import org.sdnplatform.util.IPV4SubnetTrie;



public class VRouterImpl implements IVRouter {
    public enum RuleAction {
        PERMIT,
        DROP
    }

    public enum RuleEntityType {
        /* A higher value indicates higher priority */
        HOST(50),
        NetVirt(40),
        TENANT(30),
        SUBNET(20),
        ALL(10);
        private int priority;

        private RuleEntityType(int value) {
            this.priority = value;
        }

        public int getPriority() {
            return priority;
        }
    }


    /**
     * A routing rule contains two rule entities. One for the source and one for
     * the dest. It consists of the type, name and an IP subnet. The name can
     * be a name of a tenant, a NetVirt or a host
     */
    protected static class RuleEntity {
        RuleEntityType type;
        String name;
        IPV4Subnet ip;

        /**
         * Create a rule entity. Based on the 'tenantName', 'netVirtName', 'ipStr'
         * and 'maskStr', this function figures out the rule entity type and
         * priority and creates a rule entity object.
         * @param tenantName The tenant name
         * @param netVirtName The netVirt name
         * @param ipStr The ip address
         * @param maskStr The subnet mask
         */
        public RuleEntity(String tenantName, String netVirtName,
                          String ipStr, String maskStr) {
            if (maskStr != null && maskStr.equals("0.0.0.0")) {
                this.type = RuleEntityType.HOST;
            } else if (netVirtName != null) {
                this.type = RuleEntityType.NetVirt;
            } else if (tenantName != null) {
                this.type = RuleEntityType.TENANT;
            } else if (maskStr != null && maskStr.equals("255.255.255.255")) {
                this.type = RuleEntityType.ALL;
            } else {
                this.type = RuleEntityType.SUBNET;
            }

            switch (this.type) {
                case HOST:
                    this.name = ipStr;
                    break;
                case SUBNET:
                    this.name = null;
                    break;
                case ALL:
                    this.name = null;
                    break;
                case NetVirt:
                    this.name = netVirtName;
                    break;
                case TENANT:
                    this.name = tenantName;
                    break;
            }

            int addr;
            if (ipStr != null)
                addr = IPv4.toIPv4Address(ipStr);
            else
                addr = 0;

            int maskIp;
            if (maskStr != null)
                maskIp = IPv4.toIPv4Address(maskStr);
            else
                maskIp = 0;

            short maskBits = IPV4Subnet.invertedMaskIpToLen(maskIp);
            this.ip = new IPV4Subnet(addr, maskBits);
        }

        /**
         * @param rule The routing rule predicate
         * @param entity The device group
         * @param ip The device IP address
         * @return true if the entity + ip matches this routing rule
         *         false otherwise
         */
        public boolean entityMatches(IDeviceGroup entity, int ip) {
            switch (this.type) {
                case HOST:
                    if (this.ip.address != ip)
                        return false;
                    break;

                case NetVirt:
                    if (entity == null || !this.name.equals(entity.getName()))
                        return false;
                    break;

                case TENANT:
                    if (entity == null)
                        return false;
                    String tenant = entity.getName().split("\\|")[0];
                    if (!this.name.equals(tenant))
                        return false;
                    break;

                case SUBNET:
                    if (!this.ip.contains(ip))
                        return false;
                    break;

                case ALL:
                    /* Matches */
                    break;
            }
            return true;
        }

    }

    protected static class ForwardingRule implements Comparable<ForwardingRule> {
        RuleEntity src;
        RuleEntity dst;

        String outIface;        /* The interface to send the packet out of */
        int nextHopIp;          /* The next hop IP address */
        RuleAction action;      /* The action (permit, deny) */
        String nextHopGatewayPool; /* The next hop gateway pool */

        public ForwardingRule(RuleEntity src, RuleEntity dst, String outIface,
                              String nextHopIp, RuleAction action,
                              String nextHopGatewayPool) {
            this.src = src;
            this.dst = dst;
            this.outIface = outIface;
            if (nextHopIp != null)
                this.nextHopIp = IPv4.toIPv4Address(nextHopIp);
            else
                this.nextHopIp = 0;
            this.nextHopGatewayPool = nextHopGatewayPool;
            this.action = action;
        }

        /**
         * @param srcEntity The source device group (NetVirt)
         * @param srcIp The source IP address
         * @param dstEntity The dest device group (NetVirt)
         * @param dstIp the dest IP
         * @return true, if the source and dest match this forwarding rule
         *         false, otherwise
         */
        public boolean matches(IDeviceGroup srcEntity, int srcIp,
                               IDeviceGroup dstEntity, int dstIp) {
            return (src.entityMatches(srcEntity, srcIp) &&
                    dst.entityMatches(dstEntity, dstIp));
        }

        /**
         * Compares two rule entities
         * @param o1 The first entity
         * @param o2 The second entity
         * @return a negative integer if o1 compares lower to o2 (i.e. will
         *         appear before in an ascending order
         *         a positive integer if o1 compares higher to o2
         *         0, if the two rules are equal
         */
        private int compareEntity(RuleEntity o1,  RuleEntity o2) {
            int rule1Priority = o1.type.getPriority();
            int rule2Priority = o2.type.getPriority();
            if (rule1Priority != rule2Priority) {
                /* If rule1 is of higher priority (higher numerical value) we
                 * need to return a negative integer
                 */
                return rule2Priority - rule1Priority;
            }

            /* The types are of the same priority */
            switch (o1.type) {
                case HOST:
                case NetVirt:
                case TENANT:
                    /* This will return 0 if the two entities are the same */
                    return o1.name.compareTo(o2.name);

                case SUBNET:
                    int rule1Mask = o1.ip.maskBits;
                    int rule2Mask = o2.ip.maskBits;
                    if (rule1Mask != rule2Mask) {
                        /* A more specific IP should appear before a less
                         * specific IP when arranged in negative order. Return
                         * a negative integer if rule1 is more specific
                         */
                        return rule2Mask - rule1Mask;
                    }
                    /* Both entities have the same subnet mask. Return 0 if
                     * the two subnets are the same, otherwise their comparision
                     * does not really matter
                     */
                    return o1.ip.compareTo(o2.ip);

                case ALL:
                default:
                    break;
            }
            return 0;
        }

        @Override
        public int compareTo(ForwardingRule o) {
            /* The source has higher priority than destination */
            int result = compareEntity(this.src, o.src);
            if (result != 0) {
                /* The source comparison yielded a result */
                return result;
            } else {
                return compareEntity(this.dst, o.dst);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ForwardingRule) {
                if (compareTo((ForwardingRule)o) == 0) {
                    return true;
                } else {
                    return false;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            assert false : "hashCode not designed";
            return 42; // any arbitrary constant will do
        }
    }

    /**
     * Models an object that matches a forwarding rule only on the basis of the
     * destination host/netVirt/tenant and IP
     */
    protected static class DestinationMatcher {
        /* Store all the rules with a host/netVirt/tenant specification */
        SortedSet<ForwardingRule> ruleSet;
        /* Store all the rules with a subnet specification */
        IPV4SubnetTrie<ForwardingRule> subnetTrie;

        public DestinationMatcher() {
            ruleSet = new TreeSet<ForwardingRule>();
            subnetTrie = new IPV4SubnetTrie<ForwardingRule>();
        }

        /**
         * Add 'rule' to the destination matcher
         * @param rule
         */
        public void addRule(ForwardingRule rule) {
            switch (rule.dst.type) {
                case HOST:
                case NetVirt:
                case TENANT:
                    ruleSet.add(rule);
                    return;
                default: /* SUBNET and ALL */
                    subnetTrie.put(rule.dst.ip, rule);
                    return;
            }
        }

        /**
         * Looks up the rules in this destination matcher that matches the
         * source and the dest.
         * @param srcEntity The source device group (NetVirt)
         * @param srcIp The source IP
         * @param dstEntity The dest device group (NetVirt)
         * @param dstIp The dest IP
         * @return The highest priority forwarding rule that matches
         *         null, if no match is found
         */
        public ForwardingRule findMatch(IDeviceGroup srcEntity, int srcIp,
                                        IDeviceGroup dstEntity, int dstIp) {
            for (ForwardingRule rule : ruleSet) {
                if (rule.matches(srcEntity, srcIp, dstEntity, dstIp))
                    return rule;
            }

            List<Entry<IPV4Subnet, ForwardingRule>> matchList;
            matchList = subnetTrie.prefixSearch(new IPV4Subnet(dstIp,
                                                               (short) 32));
            if (matchList == null)
                return null;

            /* The last entry in the list will be the longest prefix match */
            return matchList.get(matchList.size() - 1).getValue();
        }
    }

    /* Name of this router */
    protected String name;
    /* Name of tenant owning this router */
    protected String tenant;
    /* The map of all interfaces on this router based on their names */
    protected Map<String, VRouterInterface> interfaceMap;
    /* The map of all entity names to interface */
    protected Map<String, VRouterInterface> entityIfaceMap;
    /* The trie of all subnets to interfaces */
    protected IPV4SubnetTrie<VRouterInterface> subnetTrie;
    /* The map of all tenants to outgoing interface. This is mainly used by the
     * system router
     */
    protected Map<String, VRouterInterface> tenantIfaceMap;
    /* Store all rules having a source host name or a /32 IP address */
    protected Map<String, DestinationMatcher> srcHostRuleMap;
    /* Store all rules having a source NetVirt name */
    protected Map<String, DestinationMatcher> srcNetVirtRuleMap;
    /* Store all rules having a source Tenant name */
    protected Map<String, DestinationMatcher> srcTenantRuleMap;
    /* Store all rules which have a source subnet specified (everything except
     * /32)
     */
    protected IPV4SubnetTrie<DestinationMatcher> srcSubnetRuleTrie;
    /* The map of all gateway pools on this router based on their names */
    protected Map<String, GatewayPoolImpl> gatewayPoolMap;
    /* The default router interface. This is the interface connecting a tenant
     * router to the system router
     */
    VRouterInterface defaultIface;
    /* The virtual MAC for this router. Each router has a unique virtual mac */
    protected Long vMac;

    VirtualRouterManager vRtrManager;

    public VRouterImpl(String name, String tenant, Long vMac,
                       VirtualRouterManager vRtrManager) {
        this.name = name;
        this.tenant = tenant;
        this.defaultIface = null;
        interfaceMap = new HashMap<String, VRouterInterface>();
        entityIfaceMap = new HashMap<String, VRouterInterface>();
        tenantIfaceMap = new HashMap<String, VRouterInterface>();
        srcHostRuleMap = new HashMap<String, DestinationMatcher>();
        srcNetVirtRuleMap = new HashMap<String, DestinationMatcher>();
        srcTenantRuleMap = new HashMap<String, DestinationMatcher>();
        gatewayPoolMap = new HashMap<String, GatewayPoolImpl>();
        srcSubnetRuleTrie = new IPV4SubnetTrie<DestinationMatcher>();
        subnetTrie = new IPV4SubnetTrie<VRouterInterface>();
        this.vMac = vMac;
        this.vRtrManager = vRtrManager;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTenant() {
        return tenant;
    }

    @Override
    public void createInterface(String ifaceName, String netVirtName,
                                String rtrName, boolean active) {
        VRouterInterface iface = new VRouterInterface(this, ifaceName, netVirtName,
                                                      rtrName, active);
        interfaceMap.put(ifaceName, iface);
        String tenantName;
        if (netVirtName != null) {
            entityIfaceMap.put(netVirtName, iface);
        } else {
            entityIfaceMap.put(rtrName, iface);
            String[] n = rtrName.split("\\|");
            tenantName = n[0];
            if (tenantName.equals("system") || tenantName.equals("external"))
                defaultIface = iface;
            /* XXX The following works since there is one router per tenant */
            tenantIfaceMap.put(tenantName, iface);
        }
    }

    @Override
    public void assignInterfaceAddr(String ifaceName, String ip, String subnet)
                                            throws IllegalArgumentException {
        int ipAddr = IPv4.toIPv4Address(ip);
        int subnetMask = IPv4.toIPv4Address(subnet);
        short maskLen = IPV4Subnet.invertedMaskIpToLen(subnetMask);
        VRouterInterface iface = interfaceMap.get(ifaceName);
        if (iface == null) {
            String err = new StringBuilder().append("Invalid iface ").
                    append(ifaceName).append(" on router ").append(name).
                    toString();
            throw new IllegalArgumentException(err);
        }
        IPV4Subnet addr = new IPV4Subnet(ipAddr, maskLen);
        iface.addAddr(addr);
        subnetTrie.put(addr, iface);
        vRtrManager.addSubnetOwner(addr, name);
        vRtrManager.addIfaceIpMap(ipAddr, iface);
    }

    @Override
    public void addRoutingRule(String srcTenant, String srcNetVirt, String srcIp,
                               String srcMask, String dstTenant, String dstNetVirt,
                               String dstIp, String dstMask, String outIface,
                               String nextHop, String action,
                               String nextHopGatewayPool)
                                       throws IllegalArgumentException {
        RuleAction act;

        if (outIface != null) {
            VRouterInterface iface = interfaceMap.get(outIface);
            if (iface == null) {
                String err = new StringBuilder().append("Invalid iface ").
                        append(outIface).append(" on router ").append(name).
                        toString();
                throw new IllegalArgumentException(err);
            }
        }

        RuleEntity srcEntity = new RuleEntity(srcTenant, srcNetVirt, srcIp,
                                              srcMask);
        RuleEntity dstEntity = new RuleEntity(dstTenant, dstNetVirt, dstIp,
                                              dstMask);
        if (action.equals("permit")) {
            act = RuleAction.PERMIT;
        } else {
            act = RuleAction.DROP;
        }

        ForwardingRule rule = new ForwardingRule(srcEntity, dstEntity, outIface,
                                                 nextHop, act,
                                                 nextHopGatewayPool);
        DestinationMatcher destMatcher;
        /* Find the Source map to store the rule */
        switch (srcEntity.type) {
            case HOST:
                destMatcher = srcHostRuleMap.get(srcEntity.name);
                if (destMatcher == null) {
                    destMatcher = new DestinationMatcher();
                    srcHostRuleMap.put(srcEntity.name, destMatcher);
                }
                destMatcher.addRule(rule);
                return;

            case NetVirt:
                destMatcher = srcNetVirtRuleMap.get(srcEntity.name);
                if (destMatcher == null) {
                    destMatcher = new DestinationMatcher();
                    srcNetVirtRuleMap.put(srcEntity.name, destMatcher);
                }
                destMatcher.addRule(rule);
                return;

            case TENANT:
                destMatcher = srcTenantRuleMap.get(srcEntity.name);
                if (destMatcher == null) {
                    destMatcher = new DestinationMatcher();
                    srcTenantRuleMap.put(srcEntity.name, destMatcher);
                }
                destMatcher.addRule(rule);
                return;

            case ALL:
            case SUBNET:
                destMatcher = srcSubnetRuleTrie.get(srcEntity.ip);
                if (destMatcher == null) {
                    destMatcher = new DestinationMatcher();
                    srcSubnetRuleTrie.put(srcEntity.ip, destMatcher);
                }
                destMatcher.addRule(rule);
                return;
        }
    }

    @Override
    public void addRoutingRule(String srcTenant, String srcNetVirt, String srcIp,
                               String srcMask, String dstTenant, String dstNetVirt,
                               String dstIp, String dstMask, String outIface,
                               String nextHop, String action)
                               throws IllegalArgumentException {
        this.addRoutingRule(srcTenant, srcNetVirt, srcIp, srcMask, dstTenant,
                            dstNetVirt, dstIp, dstMask, outIface, nextHop,
                            action, null);
    }

    /* Getters for testing purposes */
    protected Map<String, VRouterInterface> getInterfaceMap() {
        return interfaceMap;
    }

    protected Map<String, DestinationMatcher> getSrcHostRuleMap() {
        return srcHostRuleMap;
    }

    protected Map<String, DestinationMatcher> getSrcNetVirtRuleMap() {
        return srcNetVirtRuleMap;
    }

    protected Map<String, DestinationMatcher> getSrcTenantRuleMap() {
        return srcTenantRuleMap;
    }

    protected IPV4SubnetTrie<DestinationMatcher> getSrcSubnetRuleTrie() {
        return srcSubnetRuleTrie;
    }

    @Override
    public boolean isIfaceDown(String entityName) {
        VRouterInterface iface = entityIfaceMap.get(entityName);
        if (iface == null) {
            return true;
        }

        return !iface.isActive();
    }

    /**
     * Creates a forwarding action based on the routing rule and the source
     * interface, source IP, dest IP and the dest device group.
     * @param rule The matching routing rule
     * @param srcIface The source interface
     * @param dst The destination device group (NetVirt)
     * @param srcIp The source IP
     * @param dstIp The dest IP
     * @return The forwarding action
     */
    private ForwardingAction createForwardingAction(ForwardingRule rule,
                                                    VRouterInterface srcIface,
                                                    IDeviceGroup dst, int srcIp,
                                                    int dstIp) {
        ForwardingAction action = new ForwardingAction();
        /* By default the action is DROP */
        if (rule == null || rule.action == RuleAction.DROP) {
            action.setDropReason(DropReason.DROP_RULE);
            action.setDropInfo(name);
            return action;
        }

        /* The rule action is to permit the flow */
        VRouterInterface iface = null;
        String outIface = rule.outIface;
        if (outIface != null) {
            /* Pick the explicitly configured outgoing interface */
            iface = interfaceMap.get(outIface);
        } else if (rule.nextHopIp != 0) {
            /* The user has explicitly specified a next hop IP. We will assume
             * that the user wants us to send the packet to this next hop and
             * so no further router processing is required. The packet will be
             * dropped later on in case the next hop does not belong to the same
             * NetVirt as the destination
             * Search for the interface directly connected to this router that
             * has the subnet containing the next hop IP.
             */
            List<Entry<IPV4Subnet, VRouterInterface>> ifaceList;
            IPV4Subnet dstIpSubnet = new IPV4Subnet(rule.nextHopIp, (short) 32);
            ifaceList = subnetTrie.prefixSearch(dstIpSubnet);
            if (ifaceList != null && ifaceList.size() > 0) {
                /* The next hop IP subnet is directly connected. Select the
                 * longest prefix match
                 */
                iface = ifaceList.get(ifaceList.size() - 1).getValue();
            }
        } else if (dst != null) {
            /* Pick an appropriate NetVirt from the destination */
            String dstNetVirt = dst.getName();
            /* If the NetVirt is directly connected to this router, use that netVirt */
            iface = entityIfaceMap.get(dstNetVirt);
            if (iface == null) {
                /* The NetVirt is not directly connected, get the tenant router */
                String[] n = dstNetVirt.split("\\|");
                iface = tenantIfaceMap.get(n[0]);
                if (iface == null) {
                    /* If the tenant router is unavailable, use the default
                     * interface. For tenant routers, this is the system router
                     * interface. There is no default interface for the
                     * system router
                     */
                    iface = defaultIface;
                }
            }
        } else {
            /* This is pure L3 routing since destination device isn't known.
             * However the flow is permitted so we need to figure out where
             * to send the packet.
             * First check if the dest IP is for a subnet directly connected to
             * this router
             */
            List<Entry<IPV4Subnet, VRouterInterface>> ifaceList;
            IPV4Subnet dstIpSubnet = new IPV4Subnet(dstIp, (short) 32);
            ifaceList = subnetTrie.prefixSearch(dstIpSubnet);
            if (ifaceList != null && ifaceList.size() > 0) {
                /* The IP subnet is directly connected. Select the longest
                 * prefix match
                 */
                iface = ifaceList.get(ifaceList.size() - 1).getValue();
            } else {
                /* Query the virtual router manager for the tenant router that
                 * owns this subnet
                 */
                String ownerRtr = vRtrManager.findSubnetOwner(dstIpSubnet);
                if (ownerRtr != null) {
                    String[] n = ownerRtr.split("\\|");
                    iface = tenantIfaceMap.get(n[0]);
                }
                if (iface == null) {
                    /* Select the default interface */
                    iface = defaultIface;
                }
            }
        }

        if (iface == null) {
            action.setDropReason(DropReason.DST_IFACE_NOT_FOUND);
            action.setDropInfo(name);
            return action;
        }
        if (!iface.isActive()) {
            action.setDropReason(DropReason.IFACE_DOWN);
            action.setDropInfo(name + "|" + iface.getName());
            return action;
        }
        if (iface == srcIface) {
            /* There is no known way to forward this packet */
            action.setDropReason(DropReason.ROUTE_ERROR);
            action.setDropInfo(name + "|" + iface.getName());
            return action;
        }

        if (!iface.isNetVirt()) {
            /* Next hop is a router */
            action.setNextRtrName(iface.getvRouter());
        } else {
            action.setDstNetVirtName(iface.getNetVirt());
            Set<IPV4Subnet> addrs = iface.getAddrs();
            if (addrs.size() > 0) {
                /* If this interface has an IP, check if we need to rewrite
                 * source MAC
                 */
                boolean rewriteSrcMac = true;
                for (IPV4Subnet s : addrs) {
                    int ipMask = ~0 << (32 - s.maskBits);
                    if ((s.address & ipMask) == (srcIp & ipMask)) {
                        /* The source is in the same subnet as the netVirt */
                        rewriteSrcMac = false;
                        break;
                    }
                }
                if (rewriteSrcMac) {
                    action.setNewSrcMac(vMac);
                }
            }
        }
        action.setAction(RoutingAction.FORWARD);
        if (rule.nextHopIp != 0) {
            action.setNextHopIp(rule.nextHopIp);
        } else if (rule.nextHopGatewayPool != null) {
            action.setNextHopGatewayPool(rule.nextHopGatewayPool);
            action.setNextHopGatewayPoolRouter(this);
        } else {
            action.setNextHopIp(dstIp);
        }
        return action;
    }

    /*
     * The algorithm to look up the routing table rules is as follows:
     * The simple logic is find out all the rules that match the source and pick
     * the highest priority rule among those on the basis of the destination.
     * Routing rules can be one of the following types in order of priority:
     * HOST > NetVirt > TENANT > SUBNET > ALL
     * Matching the source has a higher priority than the dest. So, we start off
     * by looking at rules for the source host. If none are found, look for
     * rules that match the source NetVirt, then the tenant, then the longest prefix
     * for the source subnet.
     * If at any point we find a rule matching the source, we check if it
     * applies to the destination. If so, we make use of the rule. If there are
     * multiple such rules, we select on the basis of the destination priority
     */
    @Override
    public ForwardingAction getForwardingAction(String srcIfaceEntity,
                                                IDeviceGroup src, int srcIp,
                                                IDeviceGroup dst, int dstIp) {
        /* Get the source interface. If a source interface is not found it means
         * that this entity is not connected to this router and there is a
         * config error
         */
        VRouterInterface srcIface = entityIfaceMap.get(srcIfaceEntity);
        if (srcIface == null) {
            ForwardingAction action = new ForwardingAction();
            action.setDropReason(DropReason.SRC_IFACE_NOT_FOUND);
            String info;
            info = name + " " + srcIfaceEntity;
            action.setDropInfo(info);
            return action;
        }

        String srcNetVirtName = src.getName();

        /* The names are assumed to be of the format <tenant>|<netVirt> */
        String srcTenantName = srcNetVirtName.split("\\|")[0];

        String srcIpStr = IPv4.fromIPv4Address(srcIp);
        DestinationMatcher dm;
        ForwardingRule rule;

        dm = srcHostRuleMap.get(srcIpStr);
        if (dm != null) {
            /* Check whether destination matches */
            rule = dm.findMatch(src, srcIp, dst, dstIp);
            if (rule != null)
                return createForwardingAction(rule, srcIface, dst, srcIp,
                                              dstIp);
        }

        dm = srcNetVirtRuleMap.get(srcNetVirtName);
        if (dm != null) {
            /* Check whether destination matches */
            rule = dm.findMatch(src, srcIp, dst, dstIp);
            if (rule != null)
                return createForwardingAction(rule, srcIface, dst, srcIp,
                                              dstIp);
        }

        dm = srcTenantRuleMap.get(srcTenantName);
        if (dm != null) {
            /* Check whether destination matches */
            rule = dm.findMatch(src, srcIp, dst, dstIp);
            if (rule != null)
                return createForwardingAction(rule, srcIface, dst, srcIp,
                                              dstIp);
        }

        List<Entry<IPV4Subnet, DestinationMatcher>> dmList;
        dmList = srcSubnetRuleTrie.prefixSearch(new IPV4Subnet(srcIp,
                                                               (short) 32));
        if (dmList != null) {
            ListIterator<Entry<IPV4Subnet, DestinationMatcher>> li =
                    dmList.listIterator(dmList.size());
            while (li.hasPrevious()) {
                /* Go in reverse order in the list since the longest prefix
                 *  match is at the end
                 */
                dm = li.previous().getValue();
                rule = dm.findMatch(src, srcIp, dst, dstIp);
                if (rule != null)
                    return createForwardingAction(rule, srcIface, dst, srcIp,
                                                  dstIp);
            }
        }

        return createForwardingAction(null, srcIface, dst, srcIp, dstIp);
    }

    @Override
    public long getVMac(String netVirtName, int ip) {
        VRouterInterface iface = entityIfaceMap.get(netVirtName);
        if (iface == null) {
            /* This NetVirt is not connected to this router */
            return 0;
        }
        Set<IPV4Subnet> addrs = iface.getAddrs();
        for (IPV4Subnet addr : addrs) {
            if (addr.address == ip) {
                /* This interface is configured with the IP */
                return vMac;
            }
        }
        /* This IP does not belong to this interface */
        return 0;
    }

    @Override
    public int getRtrIp(String netVirtName, int ip) {
        VRouterInterface iface = entityIfaceMap.get(netVirtName);
        if (iface == null) {
            /* This NetVirt is not connected to this router */
            return 0;
        }
        Set<IPV4Subnet> addrs = iface.getAddrs();
        for (IPV4Subnet addr : addrs) {
            if (addr.contains(ip)) {
                /* This is the subnet to which the ip address belongs */
                return addr.address;
            }
        }
        /* This IP does not belong to this interface */
        return 0;
    }

    @Override
    public void createGatewayPool(String gatewayPoolName) {
        GatewayPoolImpl gatewayPool = new GatewayPoolImpl(gatewayPoolName,
                                                          vRtrManager);
        gatewayPoolMap.put(gatewayPoolName, gatewayPool);
    }

    @Override
    public GatewayPoolImpl getGatewayPool(String gatewayPoolName) {
        return gatewayPoolMap.get(gatewayPoolName);
    }

    @Override
    public void addGatewayPoolNode(String gatewayPoolName, String ip)
                                   throws IllegalArgumentException {
        GatewayPoolImpl gatewayPool = gatewayPoolMap.get(gatewayPoolName);
        if (gatewayPool == null) {
            String err = new StringBuilder().append("Invalid gateway pool name ")
                                            .append(gatewayPoolName)
                                            .append(" on router ")
                                            .append(name)
                                            .append(" for adding gateway node ")
                                            .append(ip)
                                            .toString();
            throw new IllegalArgumentException(err);
        }
        gatewayPool.addGatewayNode(ip);
    }

    @Override
    public GatewayNode getOptimalGatewayNodeInfo(String gatewayPoolName,
                                                 IDevice srcDev,
                                                 Short vlan)
                                       throws IllegalArgumentException {
        GatewayPoolImpl gatewayPool = gatewayPoolMap.get(gatewayPoolName);
        if (gatewayPool == null) {
            String err = new StringBuilder().append("Invalid gateway pool name ")
                                            .append(gatewayPoolName)
                                            .append(" on router ")
                                            .append(name).toString();
            throw new IllegalArgumentException(err);
        }
        return gatewayPool.getOptimalGatewayNodeInfo(srcDev, vlan);
    }

    @Override
    public void removeGatewayPoolNode(String gatewayPoolName, String ip)
                                      throws IllegalArgumentException {
        GatewayPoolImpl gatewayPool = gatewayPoolMap.get(gatewayPoolName);
        if (gatewayPool == null) {
            String err = new StringBuilder().append("Invalid gateway pool name ")
                                            .append(gatewayPoolName)
                                            .append(" on router ")
                                            .append(name).toString();
            throw new IllegalArgumentException(err);
        }
        gatewayPool.removeGatewayNode(ip);
    }
}
