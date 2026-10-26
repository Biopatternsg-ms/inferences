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

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.DynamicExecutionResult;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.port.in.InferPathways;
import com.biopatternsg.domain.port.in.SaveClosingObjects;
import com.biopatternsg.infrastructure.dtos.SaveClosingObjectsRequestDTO;
import com.biopatternsg.domain.port.in.FindRoles;
import com.biopatternsg.domain.port.in.InferenceUseCase;
import com.biopatternsg.infrastructure.dtos.FindRolesRequestDTO;
import com.biopatternsg.infrastructure.dtos.InferenceRequestDTO;
import com.biopatternsg.infrastructure.dtos.InferenceResponseDTO;
import com.biopatternsg.infrastructure.dtos.PathwayInferenceRequestDTO;
import com.biopatternsg.infrastructure.dtos.PathwayInferenceResponseDTO;
import com.biopatternsg.infrastructure.mapper.InferenceMapper;
import com.biopatternsg.infrastructure.mapper.PathwayMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
    private final InferPathways inferPathways;
    private final PathwayMapper pathwayMapper;
    private final FindRoles findRoles;
    private final com.biopatternsg.domain.port.in.GetBiologicalObjects getBiologicalObjects;
    private final com.biopatternsg.domain.port.in.UpdateBiologicalObjects updateBiologicalObjects;
    private final com.biopatternsg.domain.port.in.ResetBiologicalObjectsRoles resetBiologicalObjectsRoles;
    private final SaveClosingObjects saveClosingObjects;
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
        int objectsCount = (requestDTO.getAlignedObjects() != null) ? requestDTO.getAlignedObjects().size() : 0;
        log.info("Received request to find roles for pipeline: {} with {} aligned objects",
                requestDTO.getPipelineId(), objectsCount);
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

    @GET
    @Path("/pathways/from-files")
    @Operation(summary = "Generate biological pathways from files", description = "Generates biological pathways from physical Prolog files and writes output/pathways_generated.txt without persisting to MongoDB")
    @APIResponse(responseCode = "200", description = "Generation statistics and output file path")
    public Response generatePathwaysFromFiles() {
        log.info("Received request to generate biological pathways from physical files");
        Map<String, Object> result = inferPathways.generateFromFiles();
        return Response.ok(result).build();
    }

    @POST
    @Path("/pathways/run")
    @Operation(summary = "Execute production pathway inference from MongoDB knowledge base",
            description = "Loads biological events and objects from kb_events and kb_objects for the specified pipelineId, executes dynamic inference in RAM, and persists generated pathways and graph to MongoDB.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Inference completed and persisted successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PathwayInferenceResponseDTO.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid request or missing pipelineId"),
            @APIResponse(responseCode = "404", description = "Knowledge base events or objects not found for the pipelineId"),
            @APIResponse(responseCode = "500", description = "Internal inference error")
    })
    public Response runPathwayInference(
            @HeaderParam("x-user-id") String headerUser,
            @Valid PathwayInferenceRequestDTO request) {
        if (request == null || request.getPipelineId() == null || request.getPipelineId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(PathwayInferenceResponseDTO.builder()
                            .status("ERROR")
                            .message("pipelineId is required")
                            .build())
                    .build();
        }

        String effectiveUser = request.getUser();
        if (effectiveUser == null || effectiveUser.isBlank()) {
            effectiveUser = (headerUser != null && !headerUser.isBlank()) ? headerUser : "anonymous";
        }

        try {
            DynamicExecutionResult result = inferPathways.runPipelineInference(
                    request.getPipelineId(),
                    effectiveUser,
                    request.getRestrictedObjects(),
                    request.getClosingObjects(),
                    request.isSummarizeKb(),
                    request.getMaxDepth()
            );

            PathwayInferenceResponseDTO response = pathwayMapper.toResponseDTO(result);
            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid inference request for pipelineId {}: {}", request.getPipelineId(), e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(PathwayInferenceResponseDTO.builder()
                            .status("ERROR")
                            .pipelineId(request.getPipelineId())
                            .message(e.getMessage())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Error executing production pathway inference for pipelineId: {}", request.getPipelineId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(PathwayInferenceResponseDTO.builder()
                            .status("ERROR")
                            .pipelineId(request.getPipelineId())
                            .message("Inference failed: " + e.getMessage())
                            .build())
                    .build();
        }
    }

    @GET
    @Path("/pathways/{pipelineId}/graph")
    @Operation(summary = "Get consolidated pathway graph for pipeline",
            description = "Returns the consolidated biological pathway graph (nodes and edges with pathway frequencies) for the specified pipelineId")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pathway graph found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PathwayGraph.class))),
            @APIResponse(responseCode = "404", description = "Pathway graph not found for pipelineId")
    })
    public Response getPathwayGraph(@PathParam("pipelineId") String pipelineId) {
        log.info("Fetching pathway graph for pipelineId: {}", pipelineId);
        return inferPathways.getPathwayGraph(pipelineId)
                .map(graph -> Response.ok(graph).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("status", "NOT_FOUND", "message", "Pathway graph not found for pipelineId: " + pipelineId))
                        .build());
    }

    @GET
    @Path("/pathways/{pipelineId}")
    @Operation(summary = "Get paginated biological pathways for pipeline",
            description = "Retrieves paginated biological pathways with optional filtering by startObject, endObject, and containsObject")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of biological pathways",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = BiologicalPathway.class))),
            @APIResponse(responseCode = "404", description = "No pathways found for pipelineId")
    })
    public Response getPathways(
            @PathParam("pipelineId") String pipelineId,
            @QueryParam("startObject") String startObject,
            @QueryParam("endObject") String endObject,
            @QueryParam("containsObject") String containsObject,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        log.info("Fetching pathways for pipelineId: {}, page: {}, size: {}, startObject: {}, endObject: {}, containsObject: {}",
                pipelineId, page, size, startObject, endObject, containsObject);

        long total = inferPathways.countPathways(pipelineId);
        List<BiologicalPathway> pathways = inferPathways.getPathways(pipelineId, startObject, endObject, containsObject, page, size);

        return Response.ok(pathways)
                .header("X-Total-Count", total)
                .header("X-Page", page)
                .header("X-Page-Size", size)
                .build();
    }

    @DELETE
    @Path("/pathways/{pipelineId}")
    @Operation(summary = "Delete pathways and graph for pipeline",
            description = "Deletes all biological pathways and the consolidated graph associated with the specified pipelineId")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pathways and graph deleted successfully"),
            @APIResponse(responseCode = "500", description = "Internal error deleting pathways")
    })
    public Response deletePathways(@PathParam("pipelineId") String pipelineId) {
        log.info("Deleting pathways and graph for pipelineId: {}", pipelineId);
        long deletedCount = inferPathways.deletePathways(pipelineId);
        return Response.ok(Map.of(
                "status", "SUCCESS",
                "pipelineId", pipelineId,
                "deletedPathwaysCount", deletedCount
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

    @GET
    @Path("/biological-objects/{pipelineId}")
    @Operation(summary = "Get biological objects with unified roles", description = "Retrieves evaluated biological objects with unified MeSH roles and biotypes")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Biological objects retrieved successfully"),
            @APIResponse(responseCode = "404", description = "Inference configuration not found")
    })
    public Response getBiologicalObjects(@PathParam("pipelineId") String pipelineId) {
        log.info("Received request to get biological objects for pipeline: {}", pipelineId);
        List<com.biopatternsg.domain.model.BiologicalObject> domainList = getBiologicalObjects.execute(pipelineId);
        List<com.biopatternsg.infrastructure.dtos.BiologicalObjectDTO> dtos = domainList.stream()
                .map(bo -> new com.biopatternsg.infrastructure.dtos.BiologicalObjectDTO(
                        bo.name(),
                        bo.synonyms(),
                        bo.biotypes(),
                        bo.meshRoles(),
                        bo.roles()
                ))
                .toList();
        return Response.ok(dtos).build();
    }

    @PUT
    @Path("/biological-objects/{pipelineId}")
    @Operation(summary = "Update biological objects roles", description = "Persists edited roles to kb_objects asynchronously and completes the UPDATE_BIOLOGICAL_OBJECTS step")
    @APIResponses({
            @APIResponse(
                    responseCode = "202",
                    description = "Update biological objects roles process initiated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid request payload")
    })
    public Response updateBiologicalObjects(
            @PathParam("pipelineId") String pipelineId,
            @Valid com.biopatternsg.infrastructure.dtos.UpdateBiologicalObjectsRequestDTO requestDTO,
            @HeaderParam("x-user-id") String userId
    ) {
        log.info("Received request to update biological objects roles for pipeline: {}", pipelineId);
        if (requestDTO == null || requestDTO.roles() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Payload with roles map is required"))
                    .build();
        }
        CompletableFuture.runAsync(() -> {
            try {
                updateBiologicalObjects.execute(pipelineId, requestDTO.roles(), userId);
            } catch (Exception e) {
                log.error("Error executing updateBiologicalObjects asynchronously for pipeline: {}", pipelineId, e);
            }
        }, executor);

        return Response.accepted(Map.of(
                "message", "Update biological objects roles initiated",
                "pipelineId", pipelineId
        )).build();
    }

    @DELETE
    @Path("/biological-objects/{pipelineId}/roles")
    @Operation(summary = "Reset biological objects roles", description = "Resets biological objects roles back to the initial biotypes and MeSH evaluation")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Biological objects roles reset successfully")
    })
    public Response resetBiologicalObjectsRoles(
            @PathParam("pipelineId") String pipelineId,
            @HeaderParam("x-user-id") String userId
    ) {
        log.info("Received request to reset biological objects roles for pipeline: {}", pipelineId);
        resetBiologicalObjectsRoles.execute(pipelineId, userId);
        return Response.ok(Map.of("message", "Biological objects roles reset successfully", "pipelineId", pipelineId)).build();
    }

    @GET
    @Path("/closing-objects/{pipelineId}")
    @Operation(summary = "Get configured closing objects for pipeline", description = "Retrieves previously saved target closing objects for the pipeline")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Closing objects retrieved successfully")
    })
    public Response getClosingObjects(@PathParam("pipelineId") String pipelineId) {
        log.info("Received request to get closing objects for pipeline: {}", pipelineId);
        List<String> closingObjects = saveClosingObjects.getClosingObjects(pipelineId);
        return Response.ok(Map.of(
                "pipelineId", pipelineId,
                "closingObjects", closingObjects != null ? closingObjects : List.of()
        )).build();
    }

    @POST
    @Path("/closing-objects/{pipelineId}")
    @Operation(summary = "Save closing objects and trigger pathway inference",
            description = "Asynchronously saves selected target closing objects and triggers pathway inference.")
    @APIResponses({
            @APIResponse(
                    responseCode = "202",
                    description = "Pathway inference process initiated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class))
            ),
            @APIResponse(responseCode = "400", description = "Invalid request payload")
    })
    public Response saveClosingObjects(
            @PathParam("pipelineId") String pipelineId,
            SaveClosingObjectsRequestDTO requestDTO,
            @HeaderParam("x-user-id") String userId
    ) {
        log.info("Received request to save closing objects and trigger inference for pipeline: {}", pipelineId);
        if (pipelineId == null || pipelineId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Path parameter 'pipelineId' is required"))
                    .build();
        }

        List<String> closingObjects = (requestDTO != null && requestDTO.closingObjects() != null)
                ? requestDTO.closingObjects()
                : List.of();
        Boolean summarize = requestDTO != null ? requestDTO.summarizeKb() : null;
        Integer maxDepth = requestDTO != null ? requestDTO.maxDepth() : null;

        CompletableFuture.runAsync(() -> {
            try {
                saveClosingObjects.execute(pipelineId, closingObjects, summarize, maxDepth, userId);
            } catch (Exception e) {
                log.error("Error executing saveClosingObjects asynchronously for pipeline: {}", pipelineId, e);
            }
        }, executor);

        return Response.accepted(Map.of(
                "message", "Pathway inference process initiated",
                "pipelineId", pipelineId
        )).build();
    }
}
