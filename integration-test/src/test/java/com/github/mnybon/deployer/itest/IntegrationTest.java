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
package com.github.mnybon.deployer.itest;

import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.*;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import static org.ops4j.pax.exam.CoreOptions.*;
import org.ops4j.pax.exam.Option;

import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mnn
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class IntegrationTest {

    @Inject
    private BundleContext context;

    @Inject
    private ConfigurationAdmin configAdmin;

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTest.class);

    @Configuration
    public Option[] config() throws Exception {
        MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.karaf")
                .artifactId("apache-karaf")
                .versionAsInProject()
                .type("tar.gz");
        MavenUrlReference karafStandardRepo = maven()
                .groupId("org.apache.karaf.features")
                .artifactId("standard")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference cxfRepo = maven()
                .groupId("org.apache.cxf.karaf")
                .artifactId("apache-cxf")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        MavenUrlReference serviceDeployerRepo = maven()
                .groupId("com.github.mnybon")
                .artifactId("cxf-osgi-activator-feature")
                .classifier("features")
                .type("xml")
                .versionAsInProject();

        return new Option[]{
            // KarafDistributionOption.debugConfiguration("5005", true),
            karafDistributionConfiguration()
            .frameworkUrl(karafUrl)
            .unpackDirectory(new File("exam"))
            .useDeployFolder(false),
            keepRuntimeFolder(),
            features(karafStandardRepo, "scr", "webconsole"),
            features(cxfRepo, "cxf"),
            features(serviceDeployerRepo, "cxf_sei_service", "cxf_sei_service_test_components"),
            replaceConfigurationFile("etc/org.ops4j.pax.logging.cfg", new File(this.getClass().getClassLoader().getResource("com/github/mnybon/deployer/itest/org.ops4j.pax.logging.cfg").toURI())),
            replaceConfigurationFile("etc/org.ops4j.pax.url.mvn.cfg", new File(this.getClass().getClassLoader().getResource("com/github/mnybon/deployer/itest/org.ops4j.pax.url.mvn.cfg").toURI())),};
    }
    
    @Test
    public void dontStopTillYouGetEnough() throws Exception{
        System.in.read();
    }

}
