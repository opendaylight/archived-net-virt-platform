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

package org.sdnplatform.forwarding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.sdnplatform.addressspace.IAddressSpaceManagerService;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.ListenerContextStore;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.SwitchPort;



public class RewriteServiceImpl implements IRewriteService, IModule {
    protected static final String REWRITE_RULE = 
            "org.sdnplatform.forwarding.rewrite_rule";
    
    protected static final ListenerContextStore<RewriteRule> rwStore = 
            new ListenerContextStore<RewriteRule>();
    
    protected class RewriteRule {
        protected Long origDstMac;
        protected Long finalDstMac;
        protected Long origSrcMac;
        protected Long finalSrcMac;
        protected Integer ttlDecrement;
        protected Short transportVlan;
    }

    protected IAddressSpaceManagerService addressSpaceManager;
    
    protected RewriteRule getRuleFromContext(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule;
        if (cntx == null)
            throw new NullPointerException("cntx cannot be null");
        rule = rwStore.get(cntx, REWRITE_RULE);
        if (rule == null) {
            // no rules in context yet
            rule = new RewriteRule();
            rwStore.put(cntx, REWRITE_RULE, rule);
        }
        return rule;
    }
    
    protected RewriteRule getRuleFromContextIfExists(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule;
        if (cntx == null)
            throw new NullPointerException("cntx cannot be null");
        rule = rwStore.get(cntx, REWRITE_RULE);
        return rule;
    }
    
    /*********************
     * IRewriteService 
     ********************/
    
    @Override
    public boolean hasRewriteRules(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return false;
        return (rule.origDstMac != null || 
                rule.finalDstMac != null ||
                rule.origSrcMac != null ||
                rule.finalSrcMac != null ||
                rule.ttlDecrement != null ||
                rule.transportVlan != null );
    }

    @Override
    public Short getSwitchPortVlanMode(SwitchPort swp, 
                                       String addressSpaceName, 
                                       Short currentVlan,
                                       boolean tunnelEnabled) 
            throws NullPointerException, IllegalArgumentException {
        // address space manager will do sanity checking of the args 
        return addressSpaceManager.getSwitchPortVlanMode(swp, 
                                                         addressSpaceName,
                                                         currentVlan,
                                                         tunnelEnabled);
        
    }
    
    @Override
    public void setIngressDstMac(Long origMac, Long finalMac,
                                    ListenerContext cntx) 
                                    throws NullPointerException {
        if (origMac == null)
            throw new NullPointerException("origMac cannot be null");
        if (finalMac == null)
            throw new NullPointerException("newMac cannot be null");
        RewriteRule rule = getRuleFromContext(cntx);
        if (origMac.equals(finalMac))
            return;
        rule.origDstMac = origMac;
        rule.finalDstMac = finalMac;
        // no need to put it into the cntx again. it's already there
    }

    @Override
    public Long getOrigIngressDstMac(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return null;
        return rule.origDstMac;
    }

    @Override
    public Long getFinalIngressDstMac(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return null;
        return rule.finalDstMac;
    }

    @Override
    public void clearIngressDstMac(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return;
        rule.origDstMac = null;
        rule.finalDstMac = null;
    }

    @Override
    public void setTransportVlan(Short vlan, ListenerContext cntx) 
            throws NullPointerException, IllegalArgumentException {
        if (vlan == null)
            throw new NullPointerException("vlan cannot be null");
        /*
        boolean isValidVlan = (vlan>0 && vlan<4096 
                               || vlan.equals(Ethernet.VLAN_UNTAGGED));
        */
        boolean isValidVlan = (vlan>0 && vlan<4096);
        if (!isValidVlan)
            throw new IllegalArgumentException("Invalid VLAN " + vlan);
        RewriteRule rule = getRuleFromContext(cntx);
        rule.transportVlan = vlan;
        // no need to put it into the cntx again. it's already there
    }

    @Override
    public Short getTransportVlan(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return null;
        return rule.transportVlan;
    }

    @Override
    public void clearTransportVlan(ListenerContext cntx) 
            throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return;
        rule.transportVlan = null;
    }

    @Override
    public void setEgressSrcMac(Long origMac, Long finalMac,
                                ListenerContext cntx)
                                throws NullPointerException {
        if (origMac == null)
            throw new NullPointerException("origMac cannot be null");
        if (finalMac == null)
            throw new NullPointerException("newMac cannot be null");
        RewriteRule rule = getRuleFromContext(cntx);
        if (origMac.equals(finalMac))
            return;
        rule.origSrcMac = origMac;
        rule.finalSrcMac = finalMac;
        // no need to put it into the cntx again. it's already there
    }

    @Override
    public Long getOrigEgressSrcMac(ListenerContext cntx) 
                                throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return null;
        return rule.origSrcMac;
    }

    @Override
    public Long getFinalEgressSrcMac(ListenerContext cntx) 
                                throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return null;
        return rule.finalSrcMac;
    }

    @Override
    public void clearEgressSrcMac(ListenerContext cntx) 
                                throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return;
        rule.origSrcMac = null;
        rule.finalSrcMac = null;
    }

    @Override
    public void setTtlDecrement(int numHops, ListenerContext cntx)
                                throws NullPointerException {
        if (numHops <= 0 || numHops >= 255) {
            throw new IllegalArgumentException(
                             "Number of hops for TTL decrement must be > 0" +
                             " and < 255");
        }
        RewriteRule rule = getRuleFromContext(cntx);
        rule.ttlDecrement = numHops;
    }

    @Override
    public Integer getTtlDecrement(ListenerContext cntx) 
                                throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return null;
        return rule.ttlDecrement;
    }
    
    @Override
    public void clearTtlDecrement(ListenerContext cntx)
                                throws NullPointerException {
        RewriteRule rule = getRuleFromContextIfExists(cntx);
        if (rule == null)
            return;
        rule.ttlDecrement = null;
    }

    /*********************
     * IModule 
     ********************/
    
    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IRewriteService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>, IPlatformService> m = 
                    new HashMap<Class<? extends IPlatformService>,
                            IPlatformService>();
        // We are the class that implements the service
        m.put(IRewriteService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>>
            getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IAddressSpaceManagerService.class);
        return l;
    }

    @Override
    public void init(ModuleContext context) 
            throws ModuleException {
        addressSpaceManager = 
                context.getServiceImpl(IAddressSpaceManagerService.class);
    }

    @Override
    public void startUp(ModuleContext context) {
        // no-op
    }

}
