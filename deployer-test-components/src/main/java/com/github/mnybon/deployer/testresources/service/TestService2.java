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
package com.github.mnybon.deployer.testresources.service;

import com.github.mnybon.deployer.rest.annotation.TargetServer;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 *
 * @author mnn
 */
@TargetServer("/services")
@Path("/test2")
public interface TestService2 {
    
    @GET
    @Path("/teststring")
    public String getTestString2();
    
}
