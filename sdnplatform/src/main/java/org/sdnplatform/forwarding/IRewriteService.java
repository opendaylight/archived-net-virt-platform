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

import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.devicemanager.SwitchPort;


/**
 * Manages packet rewriting rules that will be applied to packets on
 * in the given SDN Platform context. 
 * 
 * Implementations of this interface are not synchronized. Usually one 
 * listener context is only ever accessed from a single thread. You will
 * need external synchronization if multi-threaded access is desired.
 * @author gregor
 */
public interface IRewriteService extends IPlatformService {
    /**
     * Returns vlan to use when sending traffic out the given switch-port 
     * for traffic that belongs to the given address-space
     * It returns Ethernet.VLAN_UNTAGGED if the packet should be sent untagged
     * It returns the input vlan if the packet should be tagged
     * It returns null if the vlan is not allowed on this port.
     * 
     * This method should only be called for *attachment point ports*
     * and not for internal ports!
     * For internal ports one should always use the transport vlan.
     * 
     * @param swp
     * @param addressSpaceName 
     * @return 
     * @throws NullPointerException if swp oraddressSpaceVlan is null
     * @throws IllegalArgumentException if vlan is outside the allowed
     *         range of 1..4095
     */
    public Short getSwitchPortVlanMode(SwitchPort swp, 
                                       String addressSpaceName,
                                       Short currentVlan,
                                       boolean tunnelEnabled)
    
            throws NullPointerException, IllegalArgumentException;
    
    
    
    /**
     * Set the Vlan to use for transport encapsulation.
     * @param vlan
     * @param cntx The listener context in which to store the rewrite rule
     * @return 
     * @throws NullPointerException if swp or vlan is null
     * @throws IllegalArgumentException if vlan is outside the allowed
     *         range of 1..4095
     */
    public void setTransportVlan(Short vlan, ListenerContext cntx)
            throws NullPointerException, IllegalArgumentException;
    
    /**
     * Return the currently set Vlan to use for transport encapsulation or 
     * null if no vlan is set.
     * 
     * @param cntx The listener context in which to store the rewrite rule
     * @return 
     * @throws NullPointerException if swp or vlan is null
     */
    public Short getTransportVlan(ListenerContext cntx);
    
    
    /**
     * Clear any set transport encapsulation vlan
     * @param cntx The listener context in which to store the rewrite rule
     */
    public void clearTransportVlan(ListenerContext cntx);


    /**
     * @param cntx
     * @return true if any rewrite rule is set, false otherwise
     * @throws NullPointerException if cntx or mac are null
     */
    public boolean hasRewriteRules(ListenerContext cntx)
            throws NullPointerException;

    /** 
     * Store rewrite rules for dest MAC address in the context if 
     * no rule has been set. 
     * 
     * We need to specify the original Mac and the new Mac address. Forwarding
     * needs to push end-to-end routes. If we get a packet-in in the middle
     * of the network we need a way to know the expected original Mac to
     * install the correct OFMatch on the first hop switch. The rewrite action
     * will be installed on the first hop switch. In the core of the network, 
     * the newMac will be used.
     * 
     * @param origMac the original Mac on the first hop switch
     * @param finalMac the new Mac address we use after the first hop 
     * @param cntx The listener context in which to store the rewrite rule
     * @throws NullPointerException if cntx or any mac are null
     */
    public void setIngressDstMac(Long origMac, Long finalMac, 
                                        ListenerContext cntx)
                                        throws NullPointerException;
   
    /**
     * Return the original DstMac we expect to see on the true AP switch or 
     * null if no mac rewrite rule has been set. 
     * @param cntx
     * @return
     * @throws NullPointerException if cntx is null
     */
    public Long getOrigIngressDstMac(ListenerContext cntx)
            throws NullPointerException;
    
    /**
     * Return the new DstMac we are rewriting to or 
     * null if no mac rewrite rule has been set. 
     * @param cntx
     * @return
     * @throws NullPointerException if cntx is null
     */
    public Long getFinalIngressDstMac(ListenerContext cntx)
            throws NullPointerException;
    
    /**
     * Clear any set ingress destination mac rewrite rule
     * @param cntx
     * @throws NullPointerException if cntx is null
     */
    public void clearIngressDstMac(ListenerContext cntx)
            throws NullPointerException;
    
    /** 
     * Store rewrite rules for src MAC address in the context  
     * 
     * We need to specify the original Mac and the new Mac address. Forwarding
     * needs to push end-to-end routes. If we get a packet-in 
     * we need a way to know the expected original Mac to
     * install the correct OFMatch on the first hop switch. 
     * 
     * @param origMac the original Mac
     * @param finalMac the new Mac address we use 
     * @param cntx The listener context in which to store the rewrite rule
     * @throws NullPointerException if cntx or any mac are null
     */
    public void setEgressSrcMac(Long origMac, Long finalMac, 
                                        ListenerContext cntx)
                                        throws NullPointerException;
   
    /**
     * Return the original SrcMac or null if no mac rewrite rule has been set. 
     * @param cntx
     * @return
     * @throws NullPointerException if cntx is null
     */
    public Long getOrigEgressSrcMac(ListenerContext cntx)
            throws NullPointerException;
    
    /**
     * Return the new SrcMac we are rewriting to or 
     * null if no mac rewrite rule has been set. 
     * @param cntx
     * @return
     * @throws NullPointerException if cntx is null
     */
    public Long getFinalEgressSrcMac(ListenerContext cntx)
            throws NullPointerException;
    
    /**
     * Clear any set egress source mac rewrite rule
     * @param cntx
     * @throws NullPointerException if cntx is null
     */
    public void clearEgressSrcMac(ListenerContext cntx)
            throws NullPointerException;
    
    
    /**
     * Store rewrite rules in the context to decrement the TTL on IP packets. 
     * The TTL is decremented by "numHops"
     * @param numHops
     * @param cntx
     * @throws NullPointerException if the cntx is null
     * @throws IllegalArgumentException if numHops <= 0
     */
    public void setTtlDecrement(int numHops, ListenerContext cntx)
            throws NullPointerException, IllegalArgumentException;
    
    
    /**
     * Get the TTL decrement rewrite rules stored in the context. Returns
     * NULL if no rule is set. 
     * @param cntx
     * @return
     * @throws NullPointerException if the cntx in null.
     */
    public Integer getTtlDecrement(ListenerContext cntx)
            throws NullPointerException;
    
    
    /**
     * Remove any TTL decrement rewrite rules from the context 
     * @param cntx
     * @throws NullPointerException if cntx is null
     */
    public void clearTtlDecrement(ListenerContext cntx)
            throws NullPointerException;
}
