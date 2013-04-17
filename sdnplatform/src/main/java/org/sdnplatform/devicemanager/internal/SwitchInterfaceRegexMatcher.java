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

package org.sdnplatform.devicemanager.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.openflow.protocol.OFPhysicalPort;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.IOFSwitchListener;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.devicemanager.SwitchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@LogMessageCategory("Device Management")
public class SwitchInterfaceRegexMatcher implements IOFSwitchListener {
    protected class SwitchInterfaceRegex {
        @Override
        public String toString() {
            return "SwitchInterfaceRegex [dpid=" + dpid + ", pattern="
                    + pattern + "]";
        }
        public SwitchInterfaceRegex(Long dpid, Pattern pattern) {
            super();
            this.dpid = dpid;
            this.pattern = pattern;
        }
        Long dpid;
        Pattern pattern;
    }
    
    protected static Logger logger = 
            LoggerFactory.getLogger(BetterDeviceManagerImpl.class);
    
    /**
     * Map keys (tags) to the appropriate regular expression
     */
    
    protected ConcurrentHashMap<String, SwitchInterfaceRegex> regexMap;

    /**
     * Map a key (tag) to the collection of switch/ports that matc
     */
    protected ConcurrentHashMap<String, Collection<SwitchPort>> 
            matchKey2Interfaces;
   
    
    protected Object configWriteLock;
    
    protected IControllerService controllerProvider;
    
    public SwitchInterfaceRegexMatcher(
            IControllerService controllerProvider) {
        super();
        regexMap = new ConcurrentHashMap<String, SwitchInterfaceRegex>();
        matchKey2Interfaces = 
                new ConcurrentHashMap<String, Collection<SwitchPort>>();
        this.controllerProvider = controllerProvider;
        configWriteLock = new Object();
    }
    
    /**
     * Add or update an entry for a given key (tag)
     * @param key the key to add or update
     * @param dpid the dpid of the switch for which we add an entry. Null means
     * "all switches"
     * @param regex the regular expression matching interfaces names. 
     */
    @LogMessageDoc(level="ERROR",
            message="Invalid regular expression {expr} at index {index}",
            explanation="A transient error occurred while notifying tog changes",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    public void addOrUpdate(String key, Long dpid, String regex) {
        synchronized(configWriteLock) {
            // we check if this add or update involves only a single switch. 
            SwitchInterfaceRegex oldEntry = regexMap.get(key);
            Pattern pattern = null;
            try {
                pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                logger.error("Invalid regular expression '{}' at index {}: {} ",
                             new Object[] { e.getPattern(), e.getIndex(), 
                                            e.getDescription() });
                return;
            }
            SwitchInterfaceRegex newEntry = 
                    new SwitchInterfaceRegex(dpid, pattern);
            if (logger.isDebugEnabled()) {
                logger.debug("Old entry {}, new entry {}",
                             new Object[] { oldEntry, newEntry } );
            }
            regexMap.put(key, new SwitchInterfaceRegex(dpid, pattern));
            
            ArrayList<SwitchPort> interfaces = new ArrayList<SwitchPort>();
            if (dpid != null) {
                // a specific DPID, only need to check this switch
                IOFSwitch sw = controllerProvider.getSwitches().get(dpid);
                if (sw != null) {
                    addMatchingInterfaces(interfaces, sw, pattern);
                }
            } else {
                for (IOFSwitch sw: controllerProvider.getSwitches().values()) 
                    addMatchingInterfaces(interfaces, sw, pattern);
            }
            if (!interfaces.isEmpty())
                matchKey2Interfaces.put(key, interfaces);
        }
    }
    
    /**
     * Remove the given key (tag) from matching 
     * @param key
     */
    public void remove(String key) {
        synchronized(configWriteLock) {
            SwitchInterfaceRegex oldEntry = regexMap.remove(key);
            if (oldEntry == null) {
                logger.debug("Trying to remove interface regex for {} which "
                             + "doesn't exist", key);
                return;
            }
            matchKey2Interfaces.remove(key);
        }
    }

    /**
     * Get the collection of SwitchPorts that match the given key (tag). 
     * Returns null if the key doesn't exist or no interfaces match.
     * @param key
     * @return
     */
    public Collection<SwitchPort> getInterfacesByKey(String key) {
        Collection<SwitchPort> interfaces = matchKey2Interfaces.get(key);
        if (interfaces == null)
            return null;
        return Collections.unmodifiableCollection(interfaces);
    }

    
    /**
     * Add all interfaces matching pattern on switch sw to the collection 
     * of interfaces
     * 
     * @param interfaces
     * @param sw
     * @param pattern
     */
    protected void addMatchingInterfaces(Collection<SwitchPort> interfaces,
                                         IOFSwitch sw, Pattern pattern) {
        for (OFPhysicalPort port: sw.getEnabledPorts()) {
            Matcher m = pattern.matcher(port.getName());
            if (m.matches()) {
                interfaces.add(new SwitchPort(sw.getId(), port.getPortNumber()));
            }
        }
    }
    
    
    /**
     * Update match maps when a switch changes (added, deleted, ports changed)
     * 
     * NOTE: Caller needs to hold configWriteLock when calling this method
     * @param sw
     */
    protected void updateForSwitchChange(IOFSwitch sw, boolean deleted) {
        for (Entry<String, SwitchInterfaceRegex> entry: regexMap.entrySet()) {
            SwitchInterfaceRegex interfaceSpec = entry.getValue();
            if (interfaceSpec.dpid != null &&
                    !interfaceSpec.dpid.equals(sw.getId()))
                continue; // won't match for this switch
            Collection<SwitchPort> interfaces = 
                    matchKey2Interfaces.get(entry.getKey());
            if (interfaces == null)
                interfaces = new ArrayList<SwitchPort>();
            else {
                // create copy of list 
                interfaces = new ArrayList<SwitchPort>(interfaces);
                // remove all interfaces from this switch. We need to do
                // this on a copy otherwise so we don't have a time window
                // were some ports that should be matching are missing
                Iterator<SwitchPort> it = interfaces.iterator();
                while (it.hasNext()) {
                    SwitchPort swp = it.next();
                    if (swp.getSwitchDPID() == sw.getId())
                        it.remove();
                }
            }
            if (!deleted) {
                // now add all interfaces that match
                addMatchingInterfaces(interfaces, sw, interfaceSpec.pattern);
            }
            // add back to matching structures
            if (interfaces.isEmpty())
                matchKey2Interfaces.remove(entry.getKey());
            else 
                matchKey2Interfaces.put(entry.getKey(), interfaces);
        }
    }
    
    
    // IOFSwitchListener

    @Override
    public void addedSwitch(IOFSwitch sw) {
        synchronized(configWriteLock) {
            updateForSwitchChange(sw, false);
        }
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        synchronized(configWriteLock) {
            updateForSwitchChange(sw, true);
        }
    }

    @Override
    public void switchPortChanged(Long switchId) {
        synchronized(configWriteLock) {
            IOFSwitch sw = controllerProvider.getSwitches().get(switchId);
            if (sw != null)
                updateForSwitchChange(sw, false);
        }
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

}
