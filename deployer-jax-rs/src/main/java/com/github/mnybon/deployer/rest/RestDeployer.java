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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javax.ws.rs.Path;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mnn
 */
@Component(immediate = true)
public class RestDeployer implements ServiceListener, RestServiceDeployment {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestDeployer.class);

    private final Map<String, ServerPath> servers = Collections.synchronizedMap(new HashMap<String, ServerPath>());
    private BundleContext context;

    @Activate
    public void activate(BundleContext context) throws InvalidSyntaxException, ClassNotFoundException {
        this.context = context;
        inspectRunningServicesForSEI();
        context.addServiceListener(this);
    }

    @Deactivate
    public void deactivate(BundleContext context) {
        context.removeServiceListener(this);
        for (ServerPath service : servers.values()) {
            service.getServer().stop();
        }
        servers.clear();
        this.context = null;
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        if (event.getType() == ServiceEvent.REGISTERED) {
            LOGGER.debug("Caught registered event on " + getObjectClass(event.getServiceReference()) + " and ServiceID " + getServiceID(event.getServiceReference()));
            try {
                deployIfSEI(event.getServiceReference());
            } catch (ClassNotFoundException ex) {
                LOGGER.warn("Failed to deploy " + getObjectClass(event.getServiceReference()) + " from service ID " + getServiceID(event.getServiceReference()), ex);
            }
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            try {
                if (!isSEI(getObjectClass(event.getServiceReference()))) {
                    return; //Not a tracked service
                }
                LOGGER.debug("Deregistering event on " + getObjectClass(event.getServiceReference()) + " and ServiceID " + getServiceID(event.getServiceReference()));

                deregisterService(event.getServiceReference());
                context.ungetService(event.getServiceReference());
            } catch (ClassNotFoundException ex) {
                LOGGER.warn("Caught deregistering event on " + getObjectClass(event.getServiceReference()) + " from service ID " + getServiceID(event.getServiceReference()), ex);
            }

        }
    }

    @Override
    public synchronized void rebuildClosedServers() {
        LOGGER.info("Restarting stopped servers");
        for (String path : servers.keySet()) {
            ServerPath serverPath = servers.get(path);
            if (serverPath.getServer() == null || !serverPath.getServer().isStarted()) {
                LOGGER.info("Restarting stopped server: " + servers.get(path));
                rebuildServer(serverPath);

            }
        }
    }

    @Override
    public synchronized void rebuildServers(Integer port) {
        LOGGER.info("Restarting servers for port: "+port);
        for (String path : servers.keySet()) {
            ServerPath serverPath = servers.get(path);
            
            String url = serverPath.getPath();
            int portIndex = url.lastIndexOf(":");
            int portEndIndex = url.indexOf("/", portIndex);
            LOGGER.info("Serverpath: "+serverPath+" "+portIndex+" "+portEndIndex);
            Integer serverPort = null;

            if(portIndex >= 0){
                String portString = url.substring(portIndex+1, portEndIndex < 0 ? url.length() : portEndIndex);
                LOGGER.info("Serverport "+portString);
                serverPort = Integer.parseInt(portString);
            }
            LOGGER.info("Serverport: "+serverPort);
            if (serverPort != null && serverPort.equals(port)) {
                LOGGER.info("Restarting server: " + servers.get(path));
                rebuildServer(serverPath);
            }
        }
    }

    public synchronized void inspectRunningServicesForSEI() throws InvalidSyntaxException {
        String toSearch = null;
        ServiceReference[] allRefs = context.getServiceReferences(toSearch, null);
        for (ServiceReference<Object> ref : allRefs) {
            try {
                deployIfSEI(ref);
            } catch (ClassNotFoundException ex) {
                LOGGER.warn("Failed to import running service. Could not resolve classes " + getObjectClasses(ref) + " from Reference " + ref, ex);
            }
        }

    }

    protected boolean isSEI(String objectClassNames) throws ClassNotFoundException {
        Set<String> objectClasses = getObjectClasses(objectClassNames);

        for (String objectClassName : objectClasses) {
            Class<?> classToInspect = Class.forName(objectClassName);
            Path pathAnnotation = classToInspect.getAnnotation(Path.class);
            if (pathAnnotation != null) {
                return true;
            }
        }
        return false;

    }

    protected synchronized void deployIfSEI(ServiceReference<?> reference) throws ClassNotFoundException {
        LOGGER.info("Considering using " + reference + " as Service Interface");
        Object service = null;
        String addressProperty = getAddressByProperty(reference);

        Set<String> pathsToRebuild = new HashSet<>();

        Set<String> objectClasses = getObjectClasses(reference);
        for (String objectClass : objectClasses) {
            LOGGER.info("Inspecting class: " + objectClass);
            if (isSEI(objectClass)) {
                if (service == null) {
                    service = context.getService(reference);
                }
                Class<?> classToInspect = Class.forName(objectClass);
                TargetServer addressAnnotation = classToInspect.getAnnotation(TargetServer.class);
                Path pathAnnotation = classToInspect.getAnnotation(Path.class);
                String seiAddress = getAddress(addressProperty, addressAnnotation);

                ServerPath path = servers.get(seiAddress);
                if (path == null) {
                    path = new ServerPath(seiAddress);
                    servers.put(seiAddress, path);
                }

                ResourcePath resourcepath = new ResourcePath(pathAnnotation.value(), classToInspect, service);
                if (path.getResources().contains(resourcepath)) {
                    LOGGER.error("Attempted to register a second service on " + seiAddress + " with relative path " + resourcepath.getPath());
                    continue;
                }
                path.getResources().add(resourcepath);
                pathsToRebuild.add(path.getPath());
            }

        }
        rebuildServers(pathsToRebuild);

    }

    public synchronized void deregisterService(ServiceReference<?> ref) throws ClassNotFoundException {
        Set<String> pathsToRebuild = new HashSet<>();

        for (String objectClass : getObjectClasses(ref)) {
            if (isSEI(objectClass)) {
                Class<?> classToInspect = Class.forName(objectClass);
                TargetServer addressAnnotation = classToInspect.getAnnotation(TargetServer.class);
                Path pathAnnotation = classToInspect.getAnnotation(Path.class);
                String pathString = getAddress(getAddressByProperty(ref), addressAnnotation);
                ServerPath path = servers.get(pathString);
                List<ResourcePath> resourcePaths = path.getResources();
                for (int i = resourcePaths.size()-1 ; i>=0 ; i--) {
                    if (resourcePaths.get(i).getPath().equals(pathAnnotation.value())) {
                        resourcePaths.remove(i);
                        pathsToRebuild.add(pathString);
                    }
                }
            }

        }
        rebuildServers(pathsToRebuild);
    }

    private void rebuildServers(Set<String> pathsToBuild) {
        for (String path : pathsToBuild) {
            ServerPath serverPath = servers.get(path);
            if (serverPath == null) {
                LOGGER.error("Could not find serverPath for this server. This is an unexpected system state. Please notify the project owner: " + path);
                continue;
            }
            rebuildServer(serverPath);
        }
    }

    private void rebuildServer(ServerPath path) {
        LOGGER.info("Rebuilding "+path);
        if (path.getServer() != null && path.getServer().isStarted()) {
            path.getServer().stop();
        }

        if (path.getResources().isEmpty()) {
            servers.remove(path.getPath());
            return;
        }

        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(getResourceClasses(path));
        for (ResourcePath resource : path.getResources()) {
            sf.setResourceProvider(resource.getSei(), new SingletonResourceProvider(resource.getResource()));
        }
        sf.setAddress(path.getPath());
        
        
        BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());
        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        Server server = sf.create();
        path.setServer(server);

    }

    public List<Class<?>> getResourceClasses(ServerPath path) {
        List<Class<?>> classes = new ArrayList<>();
        for (ResourcePath resource : path.getResources()) {
            classes.add(resource.getSei());
        }
        return classes;

    }

    protected String getAddress(String addressProperty, TargetServer annotation) {
        if (addressProperty != null && !addressProperty.isEmpty()) {
            return addressProperty;
        }
        if (annotation != null) {
            return annotation.value();
        }
        return "/rest";

    }

    protected Set<String> getObjectClasses(ServiceReference ref) {
        Object objectClasses = ref.getProperty(org.osgi.framework.Constants.OBJECTCLASS);
        LOGGER.info("Getting object classes from " + ref + ": " + objectClasses);
        if (objectClasses != null) {
            LOGGER.info("Getting object class: "+objectClasses.getClass());
            if (objectClasses instanceof String) {
                return getObjectClasses((String) objectClasses);
            }
            if (objectClasses instanceof String[]) {
                String[] objectClassesArray = (String[])objectClasses;
                return new HashSet<>(Arrays.asList(objectClassesArray));
            }
        } 
        return new HashSet<>();
        

    }
    
    protected String getAddressByProperty(ServiceReference ref){
        return cleanProp(ref, Constants.TARGET_SERVER);
    }

    protected Set<String> getObjectClasses(String objectClassNames) {
        Set<String> objectClasses = new HashSet<>(Arrays.asList(objectClassNames.replaceAll("/s", "").split(",")));

        return objectClasses;

    }

    protected ServiceReference<?> getReferenceByID(int serviceID) throws InvalidSyntaxException {
        String clazzRef = null;
        ServiceReference<?>[] references = context.getServiceReferences(clazzRef, "(" + org.osgi.framework.Constants.SERVICE_ID + "=" + serviceID + ")");
        if (references.length > 1) {
            throw new RuntimeException("Critical error: A servicereference by ID returned " + references.length + " results");
        }
        if (references.length == 0) {
            return null;
        }
        return references[0];
    }

    protected String getObjectClass(ServiceReference<?> ref) {
        LOGGER.info("Getting ObjectClass from " + ref);
        return cleanProp(ref, org.osgi.framework.Constants.OBJECTCLASS);
    }

    protected Integer getServiceID(ServiceReference<?> ref) {
        LOGGER.info("Getting serviceID from " + ref);
        String result = cleanProp(ref, org.osgi.framework.Constants.SERVICE_ID);
        LOGGER.info("Got serviceID: " + result);
        if (result == null) {
            return 0;
        }
        return Integer.parseInt(result);
    }

    protected String cleanProp(ServiceReference<?> ref, String key) {
        LOGGER.info("Getting property from " + ref + " [" + key + "]: " + ref.getProperty(key));
        return cleanProp(ref.getProperty(key));
    }

    protected String cleanProp(Object property) {
        if (property == null) {
            return null;
        }
        if (property instanceof String || property instanceof Number) {
            LOGGER.info("Returning value " + property);
            return property.toString();
        }
        if (property instanceof String[]) {
            String[] propertyArray = (String[]) property;
            if (propertyArray.length > 0) {
                LOGGER.info("Returning array " + propertyArray[0]);
                return propertyArray[0];
            } else {
                return null;
            }
        }
        return null;
    }

    private static class ServerPath implements Comparable<ServerPath> {

        private final String path;
        private Server server;
        private final List<ResourcePath> resources = new ArrayList<>();

        public ServerPath(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public Server getServer() {
            return server;
        }

        public void setServer(Server server) {
            this.server = server;
        }

        public List<ResourcePath> getResources() {
            return resources;
        }

        @Override
        public int compareTo(ServerPath o) {
            return path.compareTo(o.path);
        }

        @Override
        public String toString() {
            return "ServerPath{" + "path=" + path + ", server=" + server + ", resources=" + resources + '}';
        }
        
        

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + Objects.hashCode(this.path);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ServerPath other = (ServerPath) obj;
            if (!Objects.equals(this.path, other.path)) {
                return false;
            }
            return true;
        }
        
        

    }

    private static class ResourcePath implements Comparable<ResourcePath> {

        private String path;
        private Class sei;
        private Object resource;

        public ResourcePath(String path, Class sei, Object resource) {
            this.path = path;
            this.sei = sei;
            this.resource = resource;
        }

        public String getPath() {
            return path;
        }

        public Class getSei() {
            return sei;
        }

        public Object getResource() {
            return resource;
        }

        @Override
        public int compareTo(ResourcePath o) {
            return path.compareTo(o.path);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 53 * hash + Objects.hashCode(this.path);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ResourcePath other = (ResourcePath) obj;
            if (!Objects.equals(this.path, other.path)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "ResourcePath{" + "path=" + path + ", sei=" + sei + ", resource=" + resource + '}';
        }
        
        
        
    }

}
