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

package org.openflow.protocol;

import org.openflow.util.HexString;

public class OFMatchWithSwDpid {
    protected OFMatch ofMatch;
    protected long  switchDataPathId;

    public OFMatchWithSwDpid() {
    	this.ofMatch = new OFMatch();
    	this.switchDataPathId = 0;
    }
    
    public OFMatchWithSwDpid(OFMatch ofm, long swDpid) {
    	this.ofMatch = ofm.clone();
    	this.switchDataPathId = swDpid;
    }
    public OFMatch getOfMatch() {
		return ofMatch;
	}

	public void setOfMatch(OFMatch ofMatch) {
		this.ofMatch = ofMatch.clone();
	}

	public long getSwitchDataPathId() {
        return this.switchDataPathId;
    }

    public OFMatchWithSwDpid setSwitchDataPathId(long dpid) {
        this.switchDataPathId = dpid;
        return this;
    }
    
    @Override
    public String toString() {
        return "OFMatchWithSwDpid [" + HexString.toHexString(switchDataPathId) + ofMatch + "]";
    }
}
