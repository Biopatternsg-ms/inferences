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
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.port.out.repositories.KnowledgeBaseRepository;
import com.biopatternsg.domain.port.out.repositories.PathwayRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class InferenceControllerTest {

    @InjectMock
    KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMock
    PathwayRepository pathwayRepository;

    @Test
    @DisplayName("GET /api/inferences/ping should return 200 and status UP")
    void testPingEndpoint() {
        given()
                .when().get("/api/inferences/ping")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("service", equalTo("inferences"));
    }

    @Test
    @DisplayName("GET /inferences/ping (without /api) should be rewritten by filter and return 200")
    void testPingEndpointWithoutApiPrefix() {
        given()
                .when().get("/inferences/ping")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("service", equalTo("inferences"));
    }

    @Test
    @DisplayName("GET /api/inferences/pathways/from-files should return 200 and generate 17,766 pathways")
    void testPathwaysFromFilesEndpoint() {
        given()
                .when().get("/api/inferences/pathways/from-files")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("dataset", equalTo("COVID-19 Legacy Dataset"))
                .body("generatedPathwaysCount", equalTo(17766));
    }

    @Test
    @DisplayName("POST /api/inferences/pathways/run without pipelineId should return 400")
    void testRunEndpointValidation() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/api/inferences/pathways/run")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /api/inferences/pathways/run with non-existent pipelineId should return 404")
    void testRunEndpointNotFound() {
        when(knowledgeBaseRepository.findEventsByPipelineId("non-existent-pipe-999"))
                .thenReturn(Collections.emptyList());

        given()
                .contentType("application/json")
                .body("{\"pipelineId\": \"non-existent-pipe-999\"}")
                .when().post("/api/inferences/pathways/run")
                .then()
                .statusCode(404)
                .body("status", equalTo("ERROR"));
    }

    @Test
    @DisplayName("POST /api/inferences/pathways/run should execute inference and persist to MongoDB")
    void testRunEndpointSuccess() {
        String pipeId = "pipe-api-test-01";
        List<KbObject> objects = List.of(
                KbObject.builder().name("IFNA1").roles(List.of("ligand", "protein")).build(),
                KbObject.builder().name("IRF7").roles(List.of("protein")).build(),
                KbObject.builder().name("IRF3").roles(List.of("transcription_factor", "protein")).build()
        );
        List<PathwayEvent> events = List.of(
                new PathwayEvent("IFNA1", "association", "IRF7"),
                new PathwayEvent("IRF7", "bind", "IRF3")
        );

        when(knowledgeBaseRepository.findEventsByPipelineId(pipeId)).thenReturn(events);
        when(knowledgeBaseRepository.findObjectsByPipelineId(pipeId)).thenReturn(objects);

        given()
                .contentType("application/json")
                .header("x-user-id", "test-header-user")
                .body("""
                        {
                          "pipelineId": "pipe-api-test-01",
                          "restrictedObjects": ["IFNA1", "IRF7", "IRF3"],
                          "closingObjects": ["IRF3"],
                          "summarizeKb": false
                        }
                        """)
                .when().post("/api/inferences/pathways/run")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("pipelineId", equalTo(pipeId))
                .body("user", equalTo("test-header-user"))
                .body("generatedPathwaysCount", equalTo(1))
                .body("uniqueNodesCount", equalTo(3))
                .body("uniqueEdgesCount", equalTo(2));

        verify(pathwayRepository).deleteByPipelineId(pipeId);
        verify(pathwayRepository).savePathwaysBatch(anyList());
        verify(pathwayRepository).savePathwayGraph(any());
    }

    @Test
    @DisplayName("GET /api/inferences/pathways/{pipelineId}/graph should return 200 when graph exists")
    void testGetPathwayGraphFound() {
        String pipeId = "pipe-graph-test";
        PathwayGraph graph = PathwayGraph.builder()
                .pipelineId(pipeId)
                .user("test-user")
                .totalPathways(10)
                .executionTimeMs(15L)
                .build();

        when(pathwayRepository.findGraphByPipelineId(pipeId)).thenReturn(Optional.of(graph));

        given()
                .when().get("/api/inferences/pathways/" + pipeId + "/graph")
                .then()
                .statusCode(200)
                .body("pipelineId", equalTo(pipeId))
                .body("user", equalTo("test-user"))
                .body("totalPathways", equalTo(10))
                .body("executionTimeMs", equalTo(15));
    }

    @Test
    @DisplayName("GET /api/inferences/pathways/{pipelineId}/graph should return 404 when graph not found")
    void testGetPathwayGraphNotFound() {
        when(pathwayRepository.findGraphByPipelineId("unknown-pipe")).thenReturn(Optional.empty());

        given()
                .when().get("/api/inferences/pathways/unknown-pipe/graph")
                .then()
                .statusCode(404)
                .body("status", equalTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/inferences/pathways/{pipelineId} should return 200 with pagination headers and pathways")
    void testGetPathwaysEndpoint() {
        String pipeId = "pipe-pathways-test";
        BiologicalPathway p1 = BiologicalPathway.builder()
                .pipelineId(pipeId)
                .startObject("IFNA1")
                .endObject("IRF3")
                .length(2)
                .build();

        when(pathwayRepository.countByPipelineId(pipeId)).thenReturn(100L);
        when(pathwayRepository.findByPipelineId(eq(pipeId), eq("IFNA1"), eq("IRF3"), eq(null), eq(0), eq(10)))
                .thenReturn(List.of(p1));

        given()
                .queryParam("startObject", "IFNA1")
                .queryParam("endObject", "IRF3")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when().get("/api/inferences/pathways/" + pipeId)
                .then()
                .statusCode(200)
                .header("X-Total-Count", equalTo("100"))
                .header("X-Page", equalTo("0"))
                .header("X-Page-Size", equalTo("10"))
                .body("$", hasSize(1))
                .body("[0].startObject", equalTo("IFNA1"))
                .body("[0].endObject", equalTo("IRF3"));
    }

    @Test
    @DisplayName("DELETE /api/inferences/pathways/{pipelineId} should return 200 and deleted count")
    void testDeletePathwaysEndpoint() {
        String pipeId = "pipe-to-delete";
        when(pathwayRepository.deleteByPipelineId(pipeId)).thenReturn(42L);

        given()
                .when().delete("/api/inferences/pathways/" + pipeId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("pipelineId", equalTo(pipeId))
                .body("deletedPathwaysCount", equalTo(42));
    }

    @Test
    @DisplayName("POST /inferences/find-roles should return 202 when request is valid")
    void testFindRoles_Accepted() {
        given()
                .contentType("application/json")
                .header("x-user-id", "test-user-1")
                .body("""
                        {
                            "pipelineId": "pipeline-test-123",
                            "alignedObjects": ["BRCA1", "TP53"]
                        }
                        """)
                .when().post("/inferences/find-roles")
                .then()
                .statusCode(202)
                .body("message", equalTo("Find roles process initiated"))
                .body("pipelineId", equalTo("pipeline-test-123"));
    }

    @Test
    @DisplayName("POST /inferences/find-roles should return 400 when pipelineId is blank")
    void testFindRoles_BadRequestWhenPipelineIdBlank() {
        given()
                .contentType("application/json")
                .header("x-user-id", "test-user-1")
                .body("""
                        {
                            "pipelineId": "",
                            "alignedObjects": ["BRCA1"]
                        }
                        """)
                .when().post("/inferences/find-roles")
                .then()
                .statusCode(400);
    }
}
