/*
 * Copyright 2017 mnn.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mnybon.deployer.rest;

import com.github.mnybon.deployer.rest.annotation.TargetServer;
import com.github.mnybon.deployer.rest.service.RestServiceDeployment;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.management.MBeanServer;
import javax.ws.rs.Path;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mnn
 */
@Component(immediate = true)
public class RestDeployer implements ServiceListener, RestServiceDeployment {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestDeployer.class);

    private final Map<Integer, Server> servers = Collections.synchronizedMap(new TreeMap<Integer, Server>());
    private BundleContext context;

    @Activate
    public void activate(BundleContext context) throws InvalidSyntaxException {
        this.context = context;
        inspectRunningServicesForSEI();
        context.addServiceListener(this);
    }

    @Deactivate
    public void deactivate(BundleContext context) {
        context.removeServiceListener(this);
        for (Server service : servers.values()) {
            service.stop();
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        if (event.getType() == ServiceEvent.REGISTERED) {
            LOGGER.debug("Caught registered event on " + getObjectClass(event.getServiceReference()) + " and ServiceID " + getServiceID(event.getServiceReference()));
            deployIfSEI(event.getServiceReference());
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            try {
                if (!isSEI(getObjectClass(event.getServiceReference()))) {
                    return; //Not a tracked service
                }
            } catch (ClassNotFoundException ex) {
                LOGGER.warn("Failed to find a class matching "+getObjectClass(event.getServiceReference())+" from service ID "+getServiceID(event.getServiceReference()), ex);
                return;
            }
            LOGGER.debug("Caught deregistering event on " + getObjectClass(event.getServiceReference()) + " and ServiceID " + getServiceID(event.getServiceReference()));
            deregisterService(event.getServiceReference());
        }
    }

    @Override
    public void rebuildClosedServers() {
        for (Integer serviceID : servers.keySet()) {
            if (!servers.get(serviceID).isStarted()) {
                ServiceReference<?> ref;
                try {
                    ref = getReferenceByID(serviceID);
                } catch (InvalidSyntaxException ex) {
                    LOGGER.error("Could not find reference for serviceID "+serviceID, ex);
                    continue;
                }
                LOGGER.info("Restarting stopped server: " + servers.get(serviceID) + " bound to " + getObjectClass(ref) + " and ServiceID " + getServiceID(ref));
                deployIfSEI(ref);
            }
        }
    }

    public void inspectRunningServicesForSEI() throws InvalidSyntaxException {
        String toSearch = null;
        ServiceReference[] allRefs = context.getServiceReferences(toSearch, null);
        for (ServiceReference<Object> ref : allRefs) {
            deployIfSEI(ref);
        }
        
    }

    protected boolean isSEI(String objectClassName) throws ClassNotFoundException {
        Class<?> classToInspect = Class.forName(objectClassName);
        Path pathAnnotation = classToInspect.getAnnotation(Path.class);
        return pathAnnotation != null;

    }

    protected synchronized void deployIfSEI(ServiceReference<?> reference) {
        LOGGER.info("Considering using "+reference+" as Service Interface");
        String objectClassName = getObjectClass(reference);
        String address = cleanProp(reference, Constants.TARGET_SERVER);
        try {
            Class<?> classToInspect = Class.forName(objectClassName);
            TargetServer addressAnnotation = classToInspect.getAnnotation(TargetServer.class);

            if (address == null) {
                address = addressAnnotation != null ? addressAnnotation.value() : null;
            }

            if (isSEI(objectClassName)) {
                Object implementation = context.getService(reference);

                Server server = registerService(classToInspect, implementation, address);
                servers.put(getServiceID(reference), server);
            }

        } catch (ClassNotFoundException ex) {
            LOGGER.warn("Attemped to build class from " + objectClassName + " but could not find the class");
        }
    }

    public Server registerService(Class<?> sei, Object implementation, String host) {
        LOGGER.info("Registering " + sei.getCanonicalName() + " with implementation " + implementation + " on " + host);
        Path annotation = sei.getAnnotation(Path.class);
        if (annotation == null) {
            return null;
        }

        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(sei);
        sf.setResourceProvider(sei, new SingletonResourceProvider(implementation));
        if (host != null && !host.isEmpty()) {
            sf.setAddress(host);
        }
        BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());
        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        return sf.create();
    }

    public synchronized void deregisterService(ServiceReference<?> ref) {

        Server server = servers.get(ref);
        if(server == null){
            LOGGER.error("Retrieved null Server for "+getObjectClass(ref) + " and ServiceID " + getServiceID(ref)+". This is an unexpected application state. Please report this to the projects github site.");
        }
        LOGGER.info("Stopping server for " + getObjectClass(ref) + " and ServiceID " + getServiceID(ref) + " with server " + server);

        server.stop();

    }
    
    protected ServiceReference<?> getReferenceByID(int serviceID) throws InvalidSyntaxException{
        String clazzRef = null;
        ServiceReference<?>[] references = context.getServiceReferences(clazzRef, "("+org.osgi.framework.Constants.SERVICE_ID+"="+serviceID+")");
        if(references.length > 1){
            throw new RuntimeException("Critical error: A servicereference by ID returned "+references.length+" results");
        }
        if(references.length == 0){
            return null;
        }
        return references[0];
    }
    
    protected String getObjectClass(ServiceReference<?> ref){
        LOGGER.info("Getting ObjectClass from "+ref);
        return cleanProp(ref, org.osgi.framework.Constants.OBJECTCLASS);
    }
    
    protected Integer getServiceID(ServiceReference<?> ref){
        LOGGER.info("Getting serviceID from "+ref);
        String result = cleanProp(ref, org.osgi.framework.Constants.SERVICE_ID);
        LOGGER.info("Got serviceID: "+result);
        if(result == null){
            return 0;
        }
        return Integer.parseInt(result);
    }
    
    protected String cleanProp(ServiceReference<?> ref, String key){
        LOGGER.info("Getting property from "+ref+" ["+key+"]");
        return cleanProp(ref.getProperty(key));
    }
    
    protected String cleanProp(Object property){
        if(property == null){
            LOGGER.info("Property was null.");
            return null;
        }
        LOGGER.info("Parsing "+property+" "+property.getClass());
        if(property instanceof String && property instanceof Number){
            LOGGER.info("Returning value "+property);
            return (String)property;
        }
        if(property instanceof String[]){
            String[] propertyArray = (String[])property;
            if(propertyArray.length>0){
                LOGGER.info("Returning array "+propertyArray[0]);
                return propertyArray[0];
            }else{
                return null;
            }
        }
        return null;
    }

}
