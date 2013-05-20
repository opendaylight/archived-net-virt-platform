/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.sndplatform.adaptors.controllerservice;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dm.Component;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.sdnplatform.core.IControllerService;
import org.sdnplatform.core.IOFSwitch;


public class Activator extends ComponentActivatorAbstractBase {

    /**
     * Function called when the activator starts just after some
     * initializations are done by the
     * ComponentActivatorAbstractBase.
     *
     */
    public void init() {

    }

    /**
     * Function called when the activator stops just before the
     * cleanup done by ComponentActivatorAbstractBase
     *
     */
    public void destroy() {

    }

    /**
     * Function that is used to communicate to dependency manager the
     * list of known implementations for services inside a container
     *
     *
     * @return An array containing all the CLASS objects that will be
     * instantiated in order to get an fully working implementation
     * Object
     */
    public Object[] getImplementations() {
        Object[] res = { ControllerServiceAdaptor.class, OFSwitchAdaptor.class };
        return res;
    }

    /**
     * Function that is called when configuration of the dependencies
     * is required.
     *
     * @param c dependency manager Component object, used for
     * configuring the dependencies exported and imported
     * @param imp Implementation class that is being configured,
     * needed as long as the same routine can configure multiple
     * implementations
     * @param containerName The containerName being configured, this allow
     * also optional per-container different behavior if needed, usually
     * should not be the case though.
     */
    public void configureInstance(Component c, Object imp, String containerName) {
        if (imp.equals(ControllerServiceAdaptor.class)) {
            /*
             *  Export the fact that the Component exports the IListenDataPacket and
             *  IControllerService interfaces
             *  
             *  First create a dictionary and put the "salListenerName" property into it
             *  the "salListenerName" should be unique, and we use the package name here.
             */
            Dictionary<String, String> props = new Hashtable<String, String>();
            props.put("salListenerName", this.getClass().getPackage().getName());
            
            // Then set that we provide the IListenDataPacket and IControllerService interface with that property
            c.setInterface(new String[] {IListenDataPacket.class.getName(),
                        IControllerService.class.getName()}, props);

        } else if (imp.equals(OFSwitchAdaptor.class)) {
         // Export the fact that we export an IOFSwitch interface
            c.setInterface(IOFSwitch.class.getName(), null);
        }
    }
}

