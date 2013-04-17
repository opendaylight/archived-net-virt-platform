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

package org.sdnplatform.perfmon;

import java.util.List;

import org.openflow.protocol.OFMessage;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.IPlatformService;


public interface IPktInProcessingTimeService extends IPlatformService {

    /**
     * Creates time buckets for a set of modules to measure their performance
     * @param listeners The message listeners to create time buckets for
     */
    public void bootstrap(List<IOFMessageListener> listeners);
    
    /**
     * Stores a timestamp in ns. Used right before a service handles an
     * OF message. Only stores if the service is enabled.
     */
    public void recordStartTimeComp(IOFMessageListener listener);
    
    public void recordEndTimeComp(IOFMessageListener listener);
    
    public void recordStartTimePktIn();
    
    public void recordEndTimePktIn(IOFSwitch sw, OFMessage m, ListenerContext cntx);
    
    public boolean isEnabled();
    
    public void setEnabled(boolean enabled);
    
    public CumulativeTimeBucket getCtb();
}
