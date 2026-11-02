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
package com.biopatternsg.infrastructure.mapper;

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.GraphEdge;
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.model.PathwayObject;
import com.biopatternsg.infrastructure.dtos.GroupedPathwayDTO;
import com.biopatternsg.infrastructure.dtos.GroupedPathwaysResponseDTO;
import java.util.Map;
import com.biopatternsg.infrastructure.mongo.GraphEdgeDocument;
import com.biopatternsg.infrastructure.mongo.KbEventCollection;
import com.biopatternsg.infrastructure.mongo.KbObjectCollection;
import com.biopatternsg.infrastructure.mongo.PathwayCollection;
import com.biopatternsg.infrastructure.mongo.PathwayGraphCollection;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PathwayMapperTest {

    private PathwayMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PathwayMapper();
    }

    @Test
    @DisplayName("Should map BiologicalPathway to PathwayCollection and back to model")
    void shouldMapBiologicalPathwayBidirectional() {
        BiologicalPathway model = BiologicalPathway.builder()
                .pipelineId("p-123")
                .user("user-test")
                .rawPattern("'IFNA1',association,'IRF7';'IRF7',bind,'IRF3'")
                .length(2)
                .startObject("IFNA1")
                .endObject("IRF3")
                .events(List.of(
                        new PathwayEvent("ev-1", "IFNA1", "association", "IRF7", 1),
                        new PathwayEvent("ev-2", "IRF7", "bind", "IRF3", 2)
                ))
                .objects(List.of(
                        new PathwayObject("IFNA1", "ligand"),
                        new PathwayObject("IRF7", "transcription_factor"),
                        new PathwayObject("IRF3", "transcription_factor")
                ))
                .build();

        PathwayCollection entity = mapper.toEntity(model);
        assertNotNull(entity);
        assertEquals("p-123", entity.getPipelineId());
        assertEquals("user-test", entity.getUser());
        assertEquals(2, entity.getLength());
        assertEquals(2, entity.getEvents().size());
        assertEquals("ev-1", entity.getEvents().get(0).getId());
        assertEquals(1, entity.getEvents().get(0).getPosition());
        assertEquals("ligand", entity.getObjects().get(0).getRole());

        entity.id = new ObjectId();
        BiologicalPathway convertedBack = mapper.toModel(entity);
        assertNotNull(convertedBack);
        assertEquals(entity.id.toHexString(), convertedBack.getId());
        assertEquals("p-123", convertedBack.getPipelineId());
        assertEquals(2, convertedBack.getEvents().size());
        assertEquals("ev-1", convertedBack.getEvents().get(0).getId());
        assertEquals(3, convertedBack.getObjects().size());
        assertEquals("IFNA1", convertedBack.getStartObject());
    }

    @Test
    @DisplayName("Should map PathwayGraph to PathwayGraphCollection and back to model")
    void shouldMapPathwayGraphBidirectional() {
        PathwayGraph graph = PathwayGraph.builder()
                .pipelineId("p-graph")
                .user("test-user")
                .totalPathways(17766)
                .executionTimeMs(350L)
                .nodes(List.of(
                        new PathwayObject("TLR4", "receptor"),
                        new PathwayObject("IRF3", "transcription_factor")
                ))
                .edges(List.of(
                        new GraphEdge("TLR4", "bind", "IRF3", 42L)
                ))
                .build();

        PathwayGraphCollection entity = mapper.toEntity(graph);
        assertNotNull(entity);
        assertEquals("p-graph", entity.getPipelineId());
        assertEquals(17766, entity.getTotalPathways());
        assertEquals(2, entity.getNodes().size());
        assertEquals(1, entity.getEdges().size());
        assertEquals(42L, entity.getEdges().get(0).getPathwayCount());

        PathwayGraph converted = mapper.toModel(entity);
        assertNotNull(converted);
        assertEquals("p-graph", converted.getPipelineId());
        assertEquals(17766, converted.getTotalPathways());
        assertEquals(2, converted.getNodes().size());
        assertEquals(1, converted.getEdges().size());
    }

    @Test
    @DisplayName("Should map KbEventCollection and KbObjectCollection to domain models")
    void shouldMapKbEntitiesToDomain() {
        KbEventCollection kbEvent = new KbEventCollection();
        kbEvent.id = new ObjectId();
        kbEvent.setPipelineId("pipe-1");
        kbEvent.setFirst("IFNA1");
        kbEvent.setRelation("bind");
        kbEvent.setSecond("IRF7");
        kbEvent.setPubmedIds(List.of("12345", "67890"));

        PathwayEvent eventModel = mapper.toEventModel(kbEvent);
        assertNotNull(eventModel);
        assertEquals(kbEvent.id.toHexString(), eventModel.getId());
        assertEquals("IFNA1", eventModel.getFirst());
        assertEquals("bind", eventModel.getRelation());
        assertEquals("IRF7", eventModel.getSecond());
        assertNull(eventModel.getPosition());

        KbObjectCollection kbObj = new KbObjectCollection();
        kbObj.setPipelineId("pipe-1");
        kbObj.setName("IRF7");
        kbObj.setSynonyms(List.of("IRF-7"));
        kbObj.setBiotypes(List.of("protein"));
        kbObj.setRoles(List.of("protein", "transcription_factor"));

        KbObject objModel = mapper.toKbObjectModel(kbObj);
        assertNotNull(objModel);
        assertEquals("pipe-1", objModel.getPipelineId());
        assertEquals("IRF7", objModel.getName());
        assertEquals(2, objModel.getRoles().size());
    }

    @Test
    @DisplayName("Should map DynamicExecutionResult to PathwayInferenceResponseDTO")
    void shouldMapDynamicExecutionResultToResponseDTO() {
        com.biopatternsg.domain.model.DynamicExecutionResult result = com.biopatternsg.domain.model.DynamicExecutionResult.builder()
                .pipelineId("p-dynamic")
                .user("user-01")
                .executionTimeMs(120L)
                .generatedPathwaysCount(15)
                .graph(PathwayGraph.builder()
                        .nodes(List.of(new PathwayObject("A", "ligand"), new PathwayObject("B", "receptor")))
                        .edges(List.of(new GraphEdge("A", "bind", "B", 15L)))
                        .build())
                .build();

        com.biopatternsg.infrastructure.dtos.PathwayInferenceResponseDTO dto = mapper.toResponseDTO(result);
        assertNotNull(dto);
        assertEquals("SUCCESS", dto.getStatus());
        assertEquals("p-dynamic", dto.getPipelineId());
        assertEquals("user-01", dto.getUser());
        assertEquals(15, dto.getGeneratedPathwaysCount());
        assertEquals(2, dto.getUniqueNodesCount());
        assertEquals(1, dto.getUniqueEdgesCount());
        assertEquals(120L, dto.getExecutionTimeMs());
    }

    @Test
    @DisplayName("Should map PathwayGraph to PathwayGraphResponseDTO with formatted types and link aliases")
    void shouldMapPathwayGraphToResponseDTO() {
        PathwayGraph graph = PathwayGraph.builder()
                .pipelineId("p-graph-dto")
                .user("user-02")
                .totalPathways(42)
                .executionTimeMs(85L)
                .nodes(List.of(
                        new PathwayObject("ABCG8", "gene"),
                        new PathwayObject("IRF3", "transcription_factor")
                ))
                .edges(List.of(
                        new GraphEdge("ABCG8", "positive_correlation", "IRF3", 12L)
                ))
                .build();

        com.biopatternsg.infrastructure.dtos.PathwayGraphResponseDTO dto = mapper.toGraphResponseDTO(graph);
        assertNotNull(dto);
        assertEquals("p-graph-dto", dto.getPipelineId());
        assertEquals("user-02", dto.getUser());
        assertEquals(42, dto.getTotalPathways());
        assertEquals(85L, dto.getExecutionTimeMs());
        assertEquals(2, dto.getNodes().size());
        assertEquals("ABCG8", dto.getNodes().get(0).getName());
        assertEquals("gene", dto.getNodes().get(0).getRole());
        assertEquals("Gene", dto.getNodes().get(0).getType());
        assertEquals("Transcription Factor", dto.getNodes().get(1).getType());
        assertEquals(1, dto.getEdges().size());
        assertEquals("ABCG8", dto.getEdges().get(0).getFirst());
        assertEquals("ABCG8", dto.getEdges().get(0).getObject1());
        assertEquals("positive_correlation", dto.getEdges().get(0).getRelation());
        assertEquals("positive_correlation", dto.getEdges().get(0).getInteraction());
        assertEquals("IRF3", dto.getEdges().get(0).getSecond());
        assertEquals("IRF3", dto.getEdges().get(0).getObject2());
        assertEquals(12L, dto.getEdges().get(0).getPathwayCount());
    }

    @Test
    @DisplayName("Should group pathways by identical ordered object sequences and length, and not group different orders or lengths")
    void shouldGroupPathwaysByExactOrderedObjectsAndLength() {
        // Pathway 1: A -> relacionA -> B; B -> relacionB -> C; C -> relacionC -> D
        BiologicalPathway p1 = BiologicalPathway.builder()
                .pipelineId("pipe-test")
                .length(3)
                .startObject("A")
                .endObject("D")
                .events(List.of(
                        new PathwayEvent("A", "relacionA", "B", 1),
                        new PathwayEvent("B", "relacionB", "C", 2),
                        new PathwayEvent("C", "relacionC", "D", 3)
                ))
                .objects(List.of(
                        new PathwayObject("A", "ligand"),
                        new PathwayObject("B", "protein"),
                        new PathwayObject("C", "protein"),
                        new PathwayObject("D", "protein")
                ))
                .build();

        // Pathway 2: A -> relacionX -> B; B -> relacionY -> C; C -> relacionY -> D (SAME sequence A->B->C->D)
        BiologicalPathway p2 = BiologicalPathway.builder()
                .pipelineId("pipe-test")
                .length(3)
                .startObject("A")
                .endObject("D")
                .events(List.of(
                        new PathwayEvent("A", "relacionX", "B", 1),
                        new PathwayEvent("B", "relacionY", "C", 2),
                        new PathwayEvent("C", "relacionY", "D", 3)
                ))
                .objects(List.of(
                        new PathwayObject("A", "ligand"),
                        new PathwayObject("B", "protein"),
                        new PathwayObject("C", "protein"),
                        new PathwayObject("D", "protein")
                ))
                .build();

        // Pathway 3: A -> relacionA -> C; C -> relacionB -> B; B -> relacionC -> D (DIFFERENT order: A->C->B->D)
        BiologicalPathway p3 = BiologicalPathway.builder()
                .pipelineId("pipe-test")
                .length(3)
                .startObject("A")
                .endObject("D")
                .events(List.of(
                        new PathwayEvent("A", "relacionA", "C", 1),
                        new PathwayEvent("C", "relacionB", "B", 2),
                        new PathwayEvent("B", "relacionC", "D", 3)
                ))
                .objects(List.of(
                        new PathwayObject("A", "ligand"),
                        new PathwayObject("C", "protein"),
                        new PathwayObject("B", "protein"),
                        new PathwayObject("D", "protein")
                ))
                .build();

        // Pathway 4: A -> relacionA -> B; B -> relacionB -> C (DIFFERENT length: 2 events)
        BiologicalPathway p4 = BiologicalPathway.builder()
                .pipelineId("pipe-test")
                .length(2)
                .startObject("A")
                .endObject("C")
                .events(List.of(
                        new PathwayEvent("A", "relacionA", "B", 1),
                        new PathwayEvent("B", "relacionB", "C", 2)
                ))
                .objects(List.of(
                        new PathwayObject("A", "ligand"),
                        new PathwayObject("B", "protein"),
                        new PathwayObject("C", "protein")
                ))
                .build();

        List<BiologicalPathway> inputList = List.of(p1, p2, p3, p4);
        List<GroupedPathwayDTO> groups = mapper.groupPathways(inputList, Map.of("A", "ligand", "D", "transcription_factor"));

        assertNotNull(groups);
        assertEquals(3, groups.size(), "Should produce exactly 3 groups (A->B->C->D, A->C->B->D, and A->B->C)");

        // Group 1: A->B->C->D (pathways p1 and p2)
        GroupedPathwayDTO group1 = groups.get(0);
        assertEquals("A->B->C->D", group1.getGroupId());
        assertEquals(3, group1.getLength());
        assertEquals("A", group1.getStartObject());
        assertEquals("D", group1.getEndObject());
        assertEquals(2, group1.getPathwaysCount());
        assertEquals(4, group1.getObjects().size());
        assertEquals("A", group1.getObjects().get(0).getName());
        assertEquals("ligand", group1.getObjects().get(0).getRole());
        assertEquals("D", group1.getObjects().get(3).getName());
        assertEquals("transcription_factor", group1.getObjects().get(3).getRole());

        // Verify consolidated relations in group 1
        assertEquals(List.of("relacionA", "relacionB", "relacionC", "relacionX", "relacionY"), group1.getRelations());

        // Verify steps in group 1
        assertEquals(3, group1.getSteps().size());
        assertEquals("A", group1.getSteps().get(0).getFirst());
        assertEquals("B", group1.getSteps().get(0).getSecond());
        assertEquals(List.of("relacionA", "relacionX"), group1.getSteps().get(0).getRelations());

        assertEquals("B", group1.getSteps().get(1).getFirst());
        assertEquals("C", group1.getSteps().get(1).getSecond());
        assertEquals(List.of("relacionB", "relacionY"), group1.getSteps().get(1).getRelations());

        assertEquals("C", group1.getSteps().get(2).getFirst());
        assertEquals("D", group1.getSteps().get(2).getSecond());
        assertEquals(List.of("relacionC", "relacionY"), group1.getSteps().get(2).getRelations());

        // Verify relation combinations
        assertEquals(2, group1.getRelationCombinations().size());
        assertEquals(List.of("relacionA", "relacionB", "relacionC"), group1.getRelationCombinations().get(0));
        assertEquals(List.of("relacionX", "relacionY", "relacionY"), group1.getRelationCombinations().get(1));

        // Group 2: A->C->B->D
        GroupedPathwayDTO group2 = groups.get(1);
        assertEquals("A->C->B->D", group2.getGroupId());
        assertEquals(1, group2.getPathwaysCount());
        assertEquals(List.of("A", "C", "B", "D"), group2.getObjects().stream().map(o -> o.getName()).toList());

        // Group 3: A->B->C
        GroupedPathwayDTO group3 = groups.get(2);
        assertEquals("A->B->C", group3.getGroupId());
        assertEquals(2, group3.getLength());
        assertEquals(1, group3.getPathwaysCount());
    }

    @Test
    @DisplayName("Should paginate grouped pathways in toGroupedPathwaysResponseDTO")
    void shouldPaginateGroupedPathwaysResponseDTO() {
        BiologicalPathway p1 = BiologicalPathway.builder()
                .pipelineId("p-test")
                .events(List.of(new PathwayEvent("A", "rel1", "B", 1)))
                .build();
        BiologicalPathway p2 = BiologicalPathway.builder()
                .pipelineId("p-test")
                .events(List.of(new PathwayEvent("B", "rel2", "C", 1)))
                .build();
        BiologicalPathway p3 = BiologicalPathway.builder()
                .pipelineId("p-test")
                .events(List.of(new PathwayEvent("C", "rel3", "D", 1)))
                .build();

        // Page 0, Size 2 (out of 3 groups)
        GroupedPathwaysResponseDTO response = mapper.toGroupedPathwaysResponseDTO("p-test", List.of(p1, p2, p3), Map.of(), 0, 2);
        assertNotNull(response);
        assertEquals("p-test", response.getPipelineId());
        assertEquals(3L, response.getTotalRawPathways());
        assertEquals(3, response.getTotalGroups());
        assertEquals(0, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(2, response.getGroups().size());

        // Page 1, Size 2 (remaining 1 group)
        GroupedPathwaysResponseDTO page1 = mapper.toGroupedPathwaysResponseDTO("p-test", List.of(p1, p2, p3), Map.of(), 1, 2);
        assertEquals(1, page1.getGroups().size());
        assertEquals("C->D", page1.getGroups().get(0).getGroupId());
    }
    @Test
    @DisplayName("Should include eventIds and pubmedIds in steps and eventIdCombinations")
    void shouldIncludeEventIdsAndPubmedIds() {
        BiologicalPathway p1 = BiologicalPathway.builder()
                .pipelineId("p-test")
                .length(2)
                .startObject("A")
                .endObject("C")
                .events(List.of(
                        new PathwayEvent("ev-1", "A", "binds", "B", 1),
                        new PathwayEvent("ev-2", "B", "activates", "C", 2)
                ))
                .objects(List.of(
                        new PathwayObject("A", "ligand"),
                        new PathwayObject("B", "protein"),
                        new PathwayObject("C", "transcription_factor")
                ))
                .build();

        BiologicalPathway p2 = BiologicalPathway.builder()
                .pipelineId("p-test")
                .length(2)
                .startObject("A")
                .endObject("C")
                .events(List.of(
                        new PathwayEvent("ev-3", "A", "inhibits", "B", 1),
                        new PathwayEvent("ev-2", "B", "activates", "C", 2)
                ))
                .objects(List.of(
                        new PathwayObject("A", "ligand"),
                        new PathwayObject("B", "protein"),
                        new PathwayObject("C", "transcription_factor")
                ))
                .build();

        Map<String, List<String>> pubmedMap = Map.of(
                "ev-1", List.of("11111", "22222"),
                "ev-2", List.of("33333"),
                "ev-3", List.of("44444")
        );

        List<GroupedPathwayDTO> groups = mapper.groupPathways(List.of(p1, p2), Map.of(), pubmedMap);
        assertNotNull(groups);
        assertEquals(1, groups.size());

        GroupedPathwayDTO g = groups.get(0);
        assertEquals("A->B->C", g.getGroupId());
        assertEquals(2, g.getPathwaysCount());

        // Combinations
        assertEquals(List.of(List.of("binds", "activates"), List.of("inhibits", "activates")), g.getRelationCombinations());
        assertEquals(List.of(List.of("ev-1", "ev-2"), List.of("ev-3", "ev-2")), g.getEventIdCombinations());

        // Step 1: A -> B
        var step1 = g.getSteps().get(0);
        assertEquals(2, step1.getEvents().size());
        assertEquals("ev-1", step1.getEvents().get(0).getId());
        assertEquals("binds", step1.getEvents().get(0).getRelation());
        assertEquals(List.of("11111", "22222"), step1.getEvents().get(0).getPubmedIds());

        assertEquals("ev-3", step1.getEvents().get(1).getId());
        assertEquals("inhibits", step1.getEvents().get(1).getRelation());
        assertEquals(List.of("44444"), step1.getEvents().get(1).getPubmedIds());

        // Step 2: B -> C
        var step2 = g.getSteps().get(1);
        assertEquals(1, step2.getEvents().size());
        assertEquals("ev-2", step2.getEvents().get(0).getId());
        assertEquals("activates", step2.getEvents().get(0).getRelation());
        assertEquals(List.of("33333"), step2.getEvents().get(0).getPubmedIds());

        GroupedPathwaysResponseDTO dtoResponse = mapper.toGroupedPathwaysResponseDTO("p-test", List.of(p1, p2), Map.of(), pubmedMap, 0, 10);
        assertNotNull(dtoResponse);
        assertEquals(1, dtoResponse.getGroups().size());
        assertEquals(List.of("11111", "22222"), dtoResponse.getGroups().get(0).getSteps().get(0).getEvents().get(0).getPubmedIds());
    }
}
