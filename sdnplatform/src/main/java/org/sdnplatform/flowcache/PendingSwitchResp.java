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

import org.sdnplatform.flowcache.IFlowCacheService.FCQueryEvType;

/**
 * The Class PendingSwitchResp. This object is used to track the pending
 * responses to switch flow table queries.
 */
public class PendingSwitchResp {
    protected FCQueryEvType evType;

    public PendingSwitchResp(
            FCQueryEvType evType) {
        this.evType      = evType;
    }
    
    public FCQueryEvType getEvType() {
        return evType;
    }

    public void setEvType(FCQueryEvType evType) {
        this.evType = evType;
    }
}
