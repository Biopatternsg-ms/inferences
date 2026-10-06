/*
 * Copyright © 2026 biopatternsg (biopatternsg@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.biopatternsg.infrastructure.clients;

import com.biopatternsg.infrastructure.dtos.KbEventDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "pubmed-integration-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PubmedIntegrationHttpClient {

    @GET
    @Path("/pubmed/kb-events/{pipelineId}/by-term/{term}")
    List<KbEventDTO> getKbEventsByTerm(
            @PathParam("pipelineId") String pipelineId,
            @PathParam("term") String term
    );

    @GET
    @Path("/pubmed/kb-events/{pipelineId}")
    List<KbEventDTO> getKbEventsByPipeline(
            @PathParam("pipelineId") String pipelineId
    );
}
