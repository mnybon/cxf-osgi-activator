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
package com.github.mnybon.deployer.testresources;

import com.github.mnybon.deployer.jetty.service.EngineConfiguration;
import org.osgi.service.component.annotations.Component;
import com.github.mnybon.deployer.rest.Constants;
import com.github.mnybon.deployer.testresources.service.TestService1;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

/**
 *
 * @author mnn
 */
@Component(enabled = false, immediate = true, property = Constants.TARGET_SERVER+"="+"https://0.0.0.0:9191/secure")
public class TestResourceSingleServiceWithAddressSSL1 implements TestService1 {
    private EngineConfiguration config;
    
    
    @Override
    public String getTestString1() {
        return "1";
    }
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void bindEngineConfiguration(EngineConfiguration config){
        this.config = config;
    }
    
    public void unbindEngineConfiguration(EngineConfiguration config){
        this.config = null;
    }
    
    
    
    
}
