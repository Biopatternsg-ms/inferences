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

import com.biopatternsg.infrastructure.dtos.AlignedResultResponseDTO;
import com.biopatternsg.infrastructure.dtos.KbEventDTO;
import com.biopatternsg.infrastructure.dtos.KbEventsByRestrictionRequestDTO;
import com.biopatternsg.infrastructure.dtos.KbObjectDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

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

    @POST
    @Path("/pubmed/kb-events/{pipelineId}/by-restriction")
    List<KbEventDTO> getKbEventsByRestriction(
            @PathParam("pipelineId") String pipelineId,
            KbEventsByRestrictionRequestDTO request
    );

    @GET
    @Path("/pubmed/kb-objects/{pipelineId}/all")
    List<KbObjectDTO> getAllKbObjects(
            @PathParam("pipelineId") String pipelineId
    );

    @PATCH
    @Path("/pubmed/kb-objects/{pipelineId}/roles")
    Response updateKbObjectRoles(
            @PathParam("pipelineId") String pipelineId,
            Map<String, List<String>> rolesMap
    );

    @DELETE
    @Path("/pubmed/kb-objects/{pipelineId}/roles")
    Response resetKbObjectRoles(
            @PathParam("pipelineId") String pipelineId
    );

    @GET
    @Path("/pubmed/aligned-results/{pipelineId}")
    AlignedResultResponseDTO getAlignedResults(
            @PathParam("pipelineId") String pipelineId
    );
}
