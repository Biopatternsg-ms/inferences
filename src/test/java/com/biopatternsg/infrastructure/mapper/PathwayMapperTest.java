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
                        new PathwayEvent("IFNA1", "association", "IRF7", 1),
                        new PathwayEvent("IRF7", "bind", "IRF3", 2)
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
        assertEquals(1, entity.getEvents().get(0).getPosition());
        assertEquals("ligand", entity.getObjects().get(0).getRole());

        entity.id = new ObjectId();
        BiologicalPathway convertedBack = mapper.toModel(entity);
        assertNotNull(convertedBack);
        assertEquals(entity.id.toHexString(), convertedBack.getId());
        assertEquals("p-123", convertedBack.getPipelineId());
        assertEquals(2, convertedBack.getEvents().size());
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
        kbEvent.setPipelineId("pipe-1");
        kbEvent.setFirst("IFNA1");
        kbEvent.setRelation("bind");
        kbEvent.setSecond("IRF7");
        kbEvent.setPubmedIds(List.of("12345", "67890"));

        PathwayEvent eventModel = mapper.toEventModel(kbEvent);
        assertNotNull(eventModel);
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
}
