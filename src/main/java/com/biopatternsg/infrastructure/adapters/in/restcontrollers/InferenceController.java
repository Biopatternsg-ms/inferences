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
package com.biopatternsg.infrastructure.adapters.in.restcontrollers;

import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.port.in.FindRoles;
import com.biopatternsg.domain.port.in.InferenceUseCase;
import com.biopatternsg.infrastructure.dtos.FindRolesRequestDTO;
import com.biopatternsg.infrastructure.dtos.InferenceRequestDTO;
import com.biopatternsg.infrastructure.dtos.InferenceResponseDTO;
import com.biopatternsg.infrastructure.mapper.InferenceMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@Path("/inferences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Inferences", description = "Endpoints for managing pipeline inferences")
public class InferenceController {

    private final InferenceUseCase inferenceUseCase;
    private final InferenceMapper inferenceMapper;
    private final FindRoles findRoles;
    private final ManagedExecutor executor;

    @POST
    @Path("/find-roles")
    @Operation(summary = "Find biological roles for aligned objects", description = "Triggers the identification of biological roles using MeSH ontology")
    @APIResponses({
            @APIResponse(
                    responseCode = "202",
                    description = "Find roles process initiated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid request payload")
    })
    public Response findRoles(@Valid FindRolesRequestDTO requestDTO, @HeaderParam("x-user-id") String userId) {
        log.info("Received request to find roles for pipeline: {}", requestDTO.getPipelineId());
        CompletableFuture.runAsync(() -> {
            try {
                findRoles.execute(requestDTO.getPipelineId(), requestDTO.getAlignedObjects(), userId);
            } catch (Exception e) {
                log.error("Error executing findRoles asynchronously for pipeline: {}", requestDTO.getPipelineId(), e);
            }
        }, executor);

        return Response.accepted(Map.of(
                "message", "Find roles process initiated",
                "pipelineId", requestDTO.getPipelineId()
        )).build();
    }

    @GET
    @Path("/ping")
    @Operation(summary = "Service ping check", description = "Returns service connectivity status")
    @APIResponse(responseCode = "200", description = "Service is up")
    public Response ping() {
        return Response.ok(Map.of(
                "status", "UP",
                "service", "inferences",
                "timestamp", System.currentTimeMillis()
        )).build();
    }

    @POST
    @Operation(summary = "Save inference configuration", description = "Persists or updates inference restriction level for a pipeline in MongoDB")
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Inference configuration saved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InferenceResponseDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid request payload")
    })
    public Response createInference(@Valid InferenceRequestDTO requestDTO, @HeaderParam("x-user-id") String userId) {
        log.info("Received request to save inference configuration for pipeline: {}", requestDTO.getPipelineId());
        Inference domainModel = inferenceMapper.toModel(requestDTO);
        Inference saved = inferenceUseCase.createInference(domainModel, userId);
        InferenceResponseDTO responseDTO = inferenceMapper.toResponseDTO(saved);
        return Response.created(URI.create("/inferences/" + responseDTO.getId())).entity(responseDTO).build();
    }

    @GET
    @Path("/pipeline/{pipelineId}")
    @Operation(summary = "Get inference configuration by pipelineId", description = "Retrieves the inference configuration associated with a pipeline")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Inference configuration found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InferenceResponseDTO.class))
            ),
            @APIResponse(responseCode = "404", description = "Inference configuration not found")
    })
    public Response getByPipelineId(@PathParam("pipelineId") String pipelineId) {
        return inferenceUseCase.getByPipelineId(pipelineId)
                .map(inferenceMapper::toResponseDTO)
                .map(dto -> Response.ok(dto).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Operation(summary = "List all inferences", description = "Retrieves all inference records stored in MongoDB")
    @APIResponse(
            responseCode = "200",
            description = "List of inferences",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = InferenceResponseDTO.class))
    )
    public Response getAllInferences() {
        List<InferenceResponseDTO> list = inferenceUseCase.getAllInferences().stream()
                .map(inferenceMapper::toResponseDTO)
                .toList();
        return Response.ok(list).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get inference by ID", description = "Retrieves a specific inference record by its MongoDB ID")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Inference found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InferenceResponseDTO.class))
            ),
            @APIResponse(responseCode = "404", description = "Inference not found")
    })
    public Response getInferenceById(@PathParam("id") String id) {
        return inferenceUseCase.getInferenceById(id)
                .map(inferenceMapper::toResponseDTO)
                .map(dto -> Response.ok(dto).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
