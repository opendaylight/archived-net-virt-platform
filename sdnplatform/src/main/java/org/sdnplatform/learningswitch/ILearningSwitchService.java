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

package org.sdnplatform.learningswitch;

import java.util.Map;

import org.sdnplatform.core.IOFSwitch;
import org.sdnplatform.core.module.IPlatformService;
import org.sdnplatform.core.types.MacVlanPair;


public interface ILearningSwitchService extends IPlatformService {
    /**
     * Returns the LearningSwitch's learned host table
     * @return The learned host table
     */
    public Map<IOFSwitch, Map<MacVlanPair,Short>> getTable();
}
