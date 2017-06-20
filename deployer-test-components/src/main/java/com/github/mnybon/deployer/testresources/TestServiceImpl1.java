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

import com.github.mnybon.deployer.testresources.service.TestService1;
import com.github.mnybon.deployer.testresources.service.TestService2;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author mnn
 */
@Component(enabled = false, immediate = true)
public class TestServiceImpl1 implements TestService1, TestService2 {

    @Override
    public String getTestString1() {
        return "1";
    }

    @Override
    public String getTestString2() {
        return "2";
    }
    
    
    
}
