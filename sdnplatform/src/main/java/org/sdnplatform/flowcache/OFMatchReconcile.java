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

import org.openflow.protocol.OFMatchWithSwDpid;
import org.sdnplatform.core.ListenerContext;

/**
 * OFMatchReconcile class to indicate result of a flow-reconciliation.
 */
public class OFMatchReconcile  {
 
    /**
     * The enum ReconcileAction. Specifies the result of reconciliation of a 
     * flow.
     */
    public enum ReconcileAction {

        /** Delete the flow-mod from the switch */
        DROP,
        /** Leave the flow-mod as-is. */
        NO_CHANGE,
        /** Program this new flow mod. */
        NEW_ENTRY,
        /** 
         * Reprogram the flow mod as the path of the flow might have changed,
         * for example when a host is moved or when a link goes down. */
        UPDATE_PATH,
        /* Flow is now in a different NetVirt */
        APP_INSTANCE_CHANGED,
        /* Delete the flow-mod - used to delete, for example, drop flow-mods
         * when the source and destination are in the same NetVirt after a 
         * configuration change */
        DELETE
    }

    /** The open flow match after reconciliation. */
    public OFMatchWithSwDpid ofmWithSwDpid;
    /** flow mod. priority */
    public short priority;
    /** Action of this flow-mod PERMIT or DENY */
    public byte action;
    /** flow mod. cookie */
    public long cookie;
    /** The application instance name. */
    public String appInstName;
    /**
     * The new application instance name. This is null unless the flow
     * has moved to a different NetVirt due to NetVirt config change or device
     * move to a different switch port etc.*/
    public String newAppInstName;
    /** The reconcile action. */
    public ReconcileAction rcAction;
    /** Outport in the event of UPDATE_PATH action**/
    public short outPort;

    // The context for the reconcile action
    public ListenerContext cntx;
    
    /**
     * Instantiates a new oF match reconcile object.
     */
    public OFMatchReconcile() {
        ofmWithSwDpid      = new OFMatchWithSwDpid();
        rcAction = ReconcileAction.NO_CHANGE;
        cntx = new ListenerContext();
    }
    
    public OFMatchReconcile(OFMatchReconcile copy) {
        ofmWithSwDpid =
            new OFMatchWithSwDpid(copy.ofmWithSwDpid.getOfMatch(),
                    copy.ofmWithSwDpid.getSwitchDataPathId());
        priority = copy.priority;
        action = copy.action;
        cookie = copy.cookie;
        appInstName = copy.appInstName;
        newAppInstName = copy.newAppInstName;
        rcAction = copy.rcAction;
        outPort = copy.outPort;
        cntx = new ListenerContext();
    }
    
    @Override
    public String toString() {
        return "OFMatchReconcile [" + ofmWithSwDpid + " priority=" + priority + " action=" + action + 
                " cookie=" + cookie + " appInstName=" + appInstName + " newAppInstName=" + newAppInstName + 
                " ReconcileAction=" + rcAction + "outPort=" + outPort + "]";
    }
}