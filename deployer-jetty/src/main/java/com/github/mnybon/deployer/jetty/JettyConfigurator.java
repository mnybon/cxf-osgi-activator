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
package com.github.mnybon.deployer.jetty;

import com.github.mnybon.deployer.jetty.service.EngineConfiguration;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.management.MBeanServer;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.mnybon.deployer.jetty.service.JettyConfiguration;
import com.github.mnybon.deployer.rest.service.RestServiceDeployment;
import java.util.List;
import java.util.logging.Level;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 *
 * @author mnn
 */
@Component(immediate = true)
public class JettyConfigurator implements JettyConfiguration {

    private static Logger LOGGER = LoggerFactory.getLogger(JettyConfigurator.class);

    private RestServiceDeployment restDeployer;
    private JettyHTTPServerEngineFactory factory;
    private ServiceTracker<EngineConfiguration, EngineConfiguration> configurationSources;
    private ServiceTracker<MBeanServer, MBeanServer> mBeanServerSources;

    @Activate
    public void activate(BundleContext context) throws InvalidSyntaxException {
        LOGGER.info("Starting configurator");
        mBeanServerSources = new ServiceTracker<>(context, MBeanServer.class, null);
        factory = new JettyHTTPServerEngineFactory() {
            @Override
            public MBeanServer getMBeanServer() {
                return mBeanServerSources.getService();
            }
        };
        configurationSources = new ConfigurationSourcesTracker(context, EngineConfiguration.class, null);
        configurationSources.open();
        LOGGER.info("Discovering engines");
        for (int i = 0; i < 65535; i++) {
            JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(i);
            if (engine != null) {
                LOGGER.info("Discovered " + engine.getProtocol() + " " + engine.getHost() + " " + engine.getPort() + " " + engine.getHandlers());
            }
        }
    }

    @Deactivate
    public void deactivate(BundleContext context) {
        configurationSources.close();
        factory = null;
        configurationSources = null;
    }

    private class ConfigurationSourcesTracker extends ServiceTracker<EngineConfiguration, EngineConfiguration> {

        public ConfigurationSourcesTracker(BundleContext context, ServiceReference<EngineConfiguration> reference, ServiceTrackerCustomizer<EngineConfiguration, EngineConfiguration> customizer) {
            super(context, reference, customizer);
        }

        public ConfigurationSourcesTracker(BundleContext context, String clazz, ServiceTrackerCustomizer<EngineConfiguration, EngineConfiguration> customizer) {
            super(context, clazz, customizer);
        }

        public ConfigurationSourcesTracker(BundleContext context, Filter filter, ServiceTrackerCustomizer<EngineConfiguration, EngineConfiguration> customizer) {
            super(context, filter, customizer);
        }

        public ConfigurationSourcesTracker(BundleContext context, Class<EngineConfiguration> clazz, ServiceTrackerCustomizer<EngineConfiguration, EngineConfiguration> customizer) {
            super(context, clazz, customizer);
        }

        @Override
        public void removedService(ServiceReference<EngineConfiguration> reference, EngineConfiguration service) {
            super.removedService(reference, service);

        }

        @Override
        public synchronized EngineConfiguration addingService(ServiceReference<EngineConfiguration> reference) {
            LOGGER.info("Discovered new " + EngineConfiguration.class.getSimpleName() + " service. Starting engine configuration");
            EngineConfiguration conf = super.addingService(reference);
            int port = conf.getConfiguredPort();
            try {
                reconfigureServer(port, conf.getTLSParameters());
                if (restDeployer != null) {
                    LOGGER.info("Rebuilding closed servers for port: " + port + ".");
                    restDeployer.rebuildServers(port);
                }
            } catch (Exception ex) {
                LOGGER.error("Error when reconfiguring server", ex);
            }

            return conf;
        }

    }

    @Override
    public void reconfigure(int port) {
        for (EngineConfiguration config : configurationSources.getServices(new EngineConfiguration[configurationSources.size()])) {
            try {
                if (config.getConfiguredPort() == port) {
                    reconfigureServer(port, config.getTLSParameters());
                    if (restDeployer != null) {
                        LOGGER.info("Rebuilding closed servers for port: " + port + ".");
                        restDeployer.rebuildServers(port);
                    }
                    return;
                }
            } catch (Exception ex) {
                LOGGER.info("Could not reconfigure server: " + port, ex);
            }

        }
    }

    @Override
    public boolean isManaged(int port) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean manage(int port) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void reconfigureServer(int port, TLSServerParameters parameters) {
        JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(port);

        try {
            if (engine != null) {
                LOGGER.info("Found existing engine for port " + port + ": " + engine + ". Shutting server down");
                JettyHTTPServerEngineFactory.destroyForPort(port);
            }

            TLSServerParameters tlsParams = parameters;
            LOGGER.info("Setting parameters for port: " + port + ". Setting parameters: " + tlsParams);
            if (tlsParams != null) {
                factory.setTLSServerParametersForPort(port, tlsParams);
            } else {
                factory.createJettyHTTPServerEngine(null, port, "http");
            }

            engine = factory.retrieveJettyHTTPServerEngine(port);
            LOGGER.info("Started new server: " + engine.getHost() + " " + engine.getPort() + " " + engine.getProtocol() + " " + engine.getConnector());
        } catch (GeneralSecurityException | IOException ex) {
            LOGGER.error("An error occured when setting new TLSServerParameters for server: " + port, ex);
        } catch (RuntimeException ex) {
            LOGGER.error("An error occured when building TLSServerParameters for server: " + port, ex);
        } catch (Exception ex) {
            LOGGER.error("An error occured when building TLSServerParameters for server: " + port, ex);
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void bindRestServiceDeployment(RestServiceDeployment deployer) {
        this.restDeployer = deployer;
    }

    public void unbindRestServiceDeployment(RestServiceDeployment deployer) {
        this.restDeployer = null;
    }

}
