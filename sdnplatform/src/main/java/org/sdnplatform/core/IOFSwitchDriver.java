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

package org.sdnplatform.core;

import org.openflow.protocol.statistics.OFDescriptionStatistics;

public interface IOFSwitchDriver {
    /**
     * Return an IOFSwitch object based on switch's manufacturer description
     * from OFDescriptionStatitics.
     * @param registered_desc string used to register this driver
     * @param description DescriptionStatistics from the switch instance
     */
    public IOFSwitch getOFSwitchImpl(String registered_desc,
            OFDescriptionStatistics description);
}
