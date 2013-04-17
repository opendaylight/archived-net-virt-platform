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

package org.sdnplatform.topology;

import java.util.Date;


import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.sdnplatform.core.web.serializers.DPIDSerializer;
import org.sdnplatform.topology.web.TunnelLinkStatusSerializer;


/**
 * This data structure is defined for a tunnel event.  The srcDPID and
 * dstDPID denote the two endpoints of a unidirectional tunnel link.
 * The time value indicates the time at which this event was created.
 * @author srini
 *
 */
public class TunnelEvent {

    public enum TunnelLinkStatus {
        UNKNOWN {
            @Override
            public String toString() {
                return "unknown";
            }
        },
        DOWN {
            @Override
            public String toString() {
                return "down";
            }
        },
        UP {
            @Override
            public String toString() {
                return "up";
            }
        },
        NOT_ENABLED {
            @Override
            public String toString() {
                return "not-enabled";
            }
        },
        NOT_ACTIVE {
            @Override
            public String toString() {
                return "not-active";
            }
        }
    }

    long srcDPID;
    long dstDPID;
    TunnelLinkStatus status;
    Date lastVerified;

    public TunnelEvent(long srcDPID, long dstDPID, TunnelLinkStatus status) {
        this.srcDPID = srcDPID;
        this.dstDPID = dstDPID;
        this.status = status;
        lastVerified = new Date();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (dstDPID ^ (dstDPID >>> 32));
        result = prime * result + (int) (srcDPID ^ (srcDPID >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TunnelEvent other = (TunnelEvent) obj;
        if (dstDPID != other.dstDPID) return false;
        if (srcDPID != other.srcDPID) return false;
        return true;
    }

    @JsonProperty("last-verified")
    public Date getLastVerified() {
        return lastVerified;
    }

    public void setLastVerified(Date lastVerified) {
        this.lastVerified = lastVerified;
    }

    @JsonProperty("status")
    @JsonSerialize(using=TunnelLinkStatusSerializer.class)
    public TunnelLinkStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "TunnelEvent [srcDPID=" + srcDPID + ", dstDPID=" + dstDPID
                + ", status=" + status + ", lastVerified=" + lastVerified + "]";
    }

    public void setStatus(TunnelLinkStatus status) {
        this.status = status;
    }

    @JsonSerialize(using=DPIDSerializer.class)
    @JsonProperty("src-dpid")
    public long getSrcDPID() {
        return srcDPID;
    }

    @JsonSerialize(using=DPIDSerializer.class)
    @JsonProperty("dst-dpid")
    public long getDstDPID() {
        return dstDPID;
    }
}