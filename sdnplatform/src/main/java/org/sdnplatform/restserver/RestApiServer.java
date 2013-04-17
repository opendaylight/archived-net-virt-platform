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

package org.sdnplatform.restserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.service.StatusService;
import org.sdnplatform.core.module.ModuleContext;
import org.sdnplatform.core.module.ModuleException;
import org.sdnplatform.core.module.IModule;
import org.sdnplatform.core.module.IPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RestApiServer
    implements IModule, IRestApiService {
    protected static Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    protected List<RestletRoutable> restlets;
    protected ModuleContext fmlContext;
    protected int restPort = 8080;
    
    // ***********
    // Application
    // ***********
    
    protected class RestApplication extends Application {
        protected Context context;
        
        public RestApplication() {
            super(new Context());
            this.context = getContext();
        }
        
        @Override
        public Restlet createInboundRoot() {
            Router baseRouter = new Router(context);
            baseRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);
            for (RestletRoutable rr : restlets) {
                baseRouter.attach(rr.basePath(), rr.getRestlet(context));
            }

            Filter slashFilter = new Filter() {            
                @Override
                protected int beforeHandle(Request request, Response response) {
                    Reference ref = request.getResourceRef();
                    String originalPath = ref.getPath();
                    if (originalPath.contains("//"))
                    {
                        String newPath = originalPath.replaceAll("/+", "/");
                        ref.setPath(newPath);
                    }
                    return Filter.CONTINUE;
                }

            };
            slashFilter.setNext(baseRouter);
            
            return slashFilter;
        }
        
        public void run(ModuleContext fmlContext, int restPort) {
            setStatusService(new StatusService() {
                @Override
                public Representation getRepresentation(Status status,
                                                        Request request,
                                                        Response response) {
                    return new JacksonRepresentation<Status>(status);
                }                
            });
            
            // Add everything in the module context to the rest
            for (Class<? extends IPlatformService> s : fmlContext.getAllServices()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Adding {} for service {} into context",
                                 s.getCanonicalName(), fmlContext.getServiceImpl(s));
                }
                context.getAttributes().put(s.getCanonicalName(), 
                                            fmlContext.getServiceImpl(s));
            }
            
            // Start listening for REST requests
            try {
                final Component component = new Component();
                component.getServers().add(Protocol.HTTP, restPort);
                component.getDefaultHost().attach(this);
                component.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    // ***************
    // IRestApiService
    // ***************
    
    @Override
    public void addRestletRoutable(RestletRoutable routable) {
        restlets.add(routable);
    }

    @Override
    public void run() {
        if (logger.isDebugEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("REST API routables: ");
            for (RestletRoutable routable : restlets) {
                sb.append(routable.getClass().getSimpleName());
                sb.append(" (");
                sb.append(routable.basePath());
                sb.append("), ");
            }
            logger.debug(sb.toString());
        }
        
        RestApplication restApp = new RestApplication();
        restApp.run(fmlContext, restPort);
    }
    
    // *****************
    // IModule
    // *****************
    
    @Override
    public Collection<Class<? extends IPlatformService>> getModuleServices() {
        Collection<Class<? extends IPlatformService>> services =
                new ArrayList<Class<? extends IPlatformService>>(1);
        services.add(IRestApiService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IPlatformService>, IPlatformService>
            getServiceImpls() {
        Map<Class<? extends IPlatformService>,
        IPlatformService> m = 
            new HashMap<Class<? extends IPlatformService>,
                        IPlatformService>();
        m.put(IRestApiService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IPlatformService>> getModuleDependencies() {
        // We don't have any
        return null;
    }

    @Override
    public void init(ModuleContext context)
            throws ModuleException {
        // This has to be done here since we don't know what order the
        // startUp methods will be called
        this.restlets = new ArrayList<RestletRoutable>();
        this.fmlContext = context;
        
        // read our config options
        Map<String, String> configOptions = context.getConfigParams(this);
        String port = configOptions.get("port");
        if (port != null) {
            restPort = Integer.parseInt(port);
        }
        logger.debug("REST port set to {}", restPort);
    }

    @Override
    public void startUp(ModuleContext Context) {
        // no-op
    }
}