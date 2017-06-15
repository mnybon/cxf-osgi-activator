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
import com.github.mnybon.deployer.jetty.service.EngineEventHandler;
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

/**
 *
 * @author mnn
 */
@Component(immediate = true)
public class JettyConfigurator implements EngineEventHandler {

    private static Logger LOGGER = LoggerFactory.getLogger(JettyConfigurator.class);

    private JettyHTTPServerEngineFactory factory;
    private ServiceTracker<EngineConfiguration, EngineConfiguration> configurationSources;
    private ServiceTracker<MBeanServer, MBeanServer> mBeanServerSources;

    @Activate
    public void activate(BundleContext context) throws InvalidSyntaxException {
        LOGGER.info("Starting configurator");
        mBeanServerSources = new ServiceTracker<>(context, MBeanServer.class, null);
        factory = new JettyHTTPServerEngineFactory() {
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
            EngineConfiguration conf = super.addingService(reference);
            int port = conf.getConfiguredPort();
            TLSServerParameters tlsParams = conf.getTLSParameters();
            JettyHTTPServerEngine engine = factory.retrieveJettyHTTPServerEngine(port);

            if (engine != null) {
                engine.shutdown();
            }

            try {
                factory.setTLSServerParametersForPort(port, tlsParams);
                factory.retrieveJettyHTTPServerEngine(port);
            } catch (GeneralSecurityException | IOException ex) {
                return conf;
            }

            return conf;
        }

    }

}
