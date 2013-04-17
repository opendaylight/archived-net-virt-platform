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

/**
 * Performance monitoring package
 */
package org.sdnplatform.perfmon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.openflow.protocol.OFMessage;
import org.sdnplatform.core.ListenerContext;
import org.sdnplatform.core.IOFMessageListener;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.restserver.IRestApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains a set of buckets (called time buckets as the
 * primarily contain 'times' that are used in a circular way to 
 * store information on packet in processing time.
 * Each bucket is meant to store the various processing time 
 * related data for a fixed duration.
 * Buckets are reused to reduce garbage generation! Once the
 * last bucket is used up the LRU bucket is reused.
 * 
 * Naming convention for variable or constants
 * variable_s : value in seconds
 * variable_ms: value in milliseconds
 * variable_us: value in microseconds
 * variable_ns: value in nanoseconds
 * 
 * Key Constants:
 * ONE_BUCKET_DURATION_SECONDS_INT:  time duration of each bucket
 * BUCKET_SET_SIZE: Number of buckets
 * TOT_PROC_TIME_WARN_THRESHOLD_US: if processing time for a packet
 *    exceeds this threshold then a warning LOG message is generated
 * TOT_PROC_TIME_ALERT_THRESHOLD_US: same as above but an alert level
 *    syslog is generated instead
 * 
 */
@LogMessageCategory("Performance Monitoring")
public class PktInProcessingTime
    implements IModule, IPktInProcessingTimeService {

    
    // Our dependencies
    private IRestApiService restApi;
    
    protected long ptWarningThresholdInNano;

    // DB storage tables
    protected static final String ControllerTableName = "controller_controller";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_PERF_MON = "performance_monitor_feature";
    
    protected static  Logger  logger = 
        LoggerFactory.getLogger(PktInProcessingTime.class);
    
    protected boolean isEnabled = false;
    protected boolean isInited = false;
    // Maintains the time when the last packet was processed
    protected long lastPktTime_ns;
    private CumulativeTimeBucket ctb = null;

    
    /***
     * BUCKET_SET_SIZE buckets each holding 10s of processing time data, a total
     * of 30*10s = 5mins of processing time data is maintained
     */
    protected static final int ONE_BUCKET_DURATION_SECONDS = 10;// seconds
    protected static final long ONE_BUCKET_DURATION_NANOSECONDS  =
                                ONE_BUCKET_DURATION_SECONDS * 1000000000;
    
    @Override
    public void bootstrap(List<IOFMessageListener> listeners) {
        if (!isInited) {
            ctb = new CumulativeTimeBucket(listeners);
            isInited = true;
        }
    }
    
    @Override
    public boolean isEnabled() {
        return isEnabled && isInited;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        logger.debug("Setting module to " + isEnabled);
    }
    
    @Override
    public CumulativeTimeBucket getCtb() {
        return ctb;
    }
    
    private long startTimePktNs;
    private long startTimeCompNs;
    @Override
    public void recordStartTimeComp(IOFMessageListener listener) {
        if (isEnabled()) {
            startTimeCompNs = System.nanoTime();
        }
    }
    
    @Override
    public void recordEndTimeComp(IOFMessageListener listener) {
        if (isEnabled()) {
            long procTime = System.nanoTime() - startTimeCompNs;
            ctb.updateOneComponent(listener, procTime);
        }
    }
    
    @Override
    public void recordStartTimePktIn() {
        if (isEnabled()) {
            startTimePktNs = System.nanoTime();
        }
    }
    
    @Override
    @LogMessageDoc(level="WARN",
            message="Time to process packet-in exceeded threshold: {}",
            explanation="Time to process packet-in exceeded the configured " +
            		"performance threshold",
            recommendation=LogMessageDoc.CHECK_CONTROLLER)
    public void recordEndTimePktIn(IOFSwitch sw, OFMessage m, ListenerContext cntx) {
        if (isEnabled()) {
            long procTimeNs = System.nanoTime() - startTimePktNs;
            ctb.updatePerPacketCounters(procTimeNs);
            
            if (ptWarningThresholdInNano > 0 && 
                    procTimeNs > ptWarningThresholdInNano) {
                logger.warn("Time to process packet-in exceeded threshold: {}", 
                            procTimeNs/1000);
            }
        }
    }
    
    // IModule methods
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IPktInProcessingTimeService.class);
        return l;
    }
    
    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m = 
            new HashMap<Class<? extends IPlatformService>,
                        IPlatformService>();
        // We are the class that implements the service
        m.put(IPktInProcessingTimeService.class, this);
        return m;
    }
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        Collection<Class<? extends IPlatformService>> l = 
                new ArrayList<Class<? extends IPlatformService>>();
        l.add(IRestApiService.class);
        return l;
    }
    
    @Override
    public void init(ModuleContext context)
                                             throws ModuleException {
        restApi = context.getServiceImpl(IRestApiService.class);
    }
    
    @Override
    @LogMessageDoc(level="INFO",
        message="Packet processing time threshold for warning" +
            " set to {time} ms.",
        explanation="Performance monitoring will log a warning if " +
    		"packet processing time exceeds the configured threshold")
    public void startUp(ModuleContext context) {
        // Add our REST API
        restApi.addRestletRoutable(new PerfWebRoutable());
        
        // TODO - Alex - change this to a config option
        ptWarningThresholdInNano = Long.parseLong(System.getProperty(
             "org.sdnplatform.core.PTWarningThresholdInMilli", "0")) * 1000000;
        if (ptWarningThresholdInNano > 0) {
            logger.info("Packet processing time threshold for warning" +
            		" set to {} ms.", ptWarningThresholdInNano/1000000);
        }
    }
}
