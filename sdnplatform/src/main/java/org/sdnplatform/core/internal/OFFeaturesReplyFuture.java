/*
 * Copyright (c) 2012,2013 Big Switch Networks, Inc.
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

package org.sdnplatform.core.internal;

import java.util.concurrent.TimeUnit;


import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.threadpool.IThreadPoolService;

/**
 * A concrete implementation that handles asynchronously receiving
 * OFFeaturesReply
 * 
 * @author Shudong Zhou
 */
public class OFFeaturesReplyFuture extends
        OFMessageFuture<OFFeaturesReply> {

    protected volatile boolean finished;

    public OFFeaturesReplyFuture(IThreadPoolService tp,
            IOFSwitch sw, int transactionId) {
        super(tp, sw, OFType.FEATURES_REPLY, transactionId);
        init();
    }

    public OFFeaturesReplyFuture(IThreadPoolService tp,
            IOFSwitch sw, int transactionId, long timeout, TimeUnit unit) {
        super(tp, sw, OFType.FEATURES_REPLY, transactionId, timeout, unit);
        init();
    }

    private void init() {
        this.finished = false;
        this.result = null;
    }

    @Override
    protected void handleReply(IOFSwitch sw, OFMessage msg) {
        this.result = (OFFeaturesReply) msg;
        this.finished = true;
    }

    @Override
    protected boolean isFinished() {
        return finished;
    }

    @Override
    protected void unRegister() {
        super.unRegister();
        sw.cancelFeaturesReply(transactionId);
    }
}
