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
package com.biopatternsg.application.usecase;

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.DynamicExecutionResult;
import com.biopatternsg.domain.model.DynamicInferenceInput;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.port.out.PrologInferencePort;
import com.biopatternsg.domain.port.out.repositories.KnowledgeBaseRepository;
import com.biopatternsg.domain.port.out.repositories.PathwayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferPathwaysUseCaseDynamicTest {

    @Mock
    private PrologInferencePort prologInferencePort;

    @Mock
    private PathwayRepository pathwayRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private InferenceRepository inferenceRepository;

    private InferPathwaysUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InferPathwaysUseCase(prologInferencePort, pathwayRepository, knowledgeBaseRepository, inferenceRepository, "output", 4, 30);
    }

    @Test
    @DisplayName("Should test role resolution hierarchy: enzyme > ligand > receptor > transcription_factor > protein")
    void shouldResolvePrimaryRoleHierarchy() {
        assertEquals("enzyme", InferPathwaysUseCase.resolvePrimaryRole(List.of("protein", "enzyme", "ligand")));
        assertEquals("ligand", InferPathwaysUseCase.resolvePrimaryRole(List.of("protein", "ligand", "receptor")));
        assertEquals("receptor", InferPathwaysUseCase.resolvePrimaryRole(List.of("protein", "receptor")));
        assertEquals("transcription_factor", InferPathwaysUseCase.resolvePrimaryRole(List.of("protein", "tf")));
        assertEquals("protein", InferPathwaysUseCase.resolvePrimaryRole(List.of("protein")));
        assertEquals("protein", InferPathwaysUseCase.resolvePrimaryRole(List.of()));
        assertEquals("protein", InferPathwaysUseCase.resolvePrimaryRole(null));
    }

    @Test
    @DisplayName("Should test summarizeKnowledgeBase normalizing and deduplicating relations")
    void shouldSummarizeKnowledgeBaseCorrectly() {
        List<PathwayEvent> rawEvents = List.of(
                new PathwayEvent("TLR4", "stimulate", "IRF3"),
                new PathwayEvent("TLR4", "activate", "IRF3"),
                new PathwayEvent("IRF3", "suppress", "NFKB1"),
                new PathwayEvent("TLR4", "complex", "MYD88")
        );

        List<PathwayEvent> summarized = InferPathwaysUseCase.summarizeKnowledgeBase(rawEvents);

        // TLR4 -> stimulate -> IRF3 and TLR4 -> activate -> IRF3 both map to positive_correlation, so deduplicated to 1
        assertEquals(3, summarized.size());
        assertTrue(summarized.stream().anyMatch(e ->
                "TLR4".equals(e.getFirst()) && "positive_correlation".equals(e.getRelation()) && "IRF3".equals(e.getSecond())
        ));
        assertTrue(summarized.stream().anyMatch(e ->
                "IRF3".equals(e.getFirst()) && "negative_correlation".equals(e.getRelation()) && "NFKB1".equals(e.getSecond())
        ));
        assertTrue(summarized.stream().anyMatch(e ->
                "TLR4".equals(e.getFirst()) && "bind".equals(e.getRelation()) && "MYD88".equals(e.getSecond())
        ));
    }

    @Test
    @DisplayName("Should execute dynamic inference in RAM-disk and build pathways and graph")
    void shouldExecuteDynamicInferenceInRamDisk() {
        List<KbObject> objects = List.of(
                KbObject.builder().name("IFNA1").roles(List.of("ligand", "protein")).build(),
                KbObject.builder().name("IRF7").roles(List.of("protein", "transcription_factor")).build(),
                KbObject.builder().name("IRF3").roles(List.of("transcription_factor", "protein")).build()
        );

        List<PathwayEvent> events = List.of(
                new PathwayEvent("IFNA1", "association", "IRF7"),
                new PathwayEvent("IRF7", "bind", "IRF3")
        );

        DynamicInferenceInput input = DynamicInferenceInput.builder()
                .pipelineId("pipe-dynamic-test")
                .user("test-researcher")
                .objects(objects)
                .events(events)
                .restrictedObjects(List.of("IFNA1", "IRF7", "IRF3"))
                .closingObjects(List.of("IRF3"))
                .summarizeKb(false)
                .maxDepth(5)
                .build();

        DynamicExecutionResult result = useCase.runDynamicInference(input);

        assertNotNull(result);
        assertEquals("pipe-dynamic-test", result.getPipelineId());
        assertEquals("test-researcher", result.getUser());
        assertEquals(1, result.getGeneratedPathwaysCount());

        BiologicalPathway pathway = result.getPathways().get(0);
        assertNotNull(pathway);
        assertEquals(2, pathway.getEvents().size());
        assertEquals(1, pathway.getEvents().get(0).getPosition());
        assertEquals(2, pathway.getEvents().get(1).getPosition());
        assertEquals("IFNA1", pathway.getStartObject());
        assertEquals("IRF3", pathway.getEndObject());

        // Check object roles
        assertEquals("ligand", pathway.getObjects().stream()
                .filter(o -> "IFNA1".equals(o.getName())).findFirst().orElseThrow().getRole());
        assertEquals("transcription_factor", pathway.getObjects().stream()
                .filter(o -> "IRF3".equals(o.getName())).findFirst().orElseThrow().getRole());

        // Check consolidated graph
        PathwayGraph graph = result.getGraph();
        assertNotNull(graph);
        assertEquals("pipe-dynamic-test", graph.getPipelineId());
        assertEquals(1, graph.getTotalPathways());
        assertEquals(3, graph.getNodes().size());
        assertEquals(2, graph.getEdges().size());
        assertEquals(1L, graph.getEdges().get(0).getPathwayCount());
    }

    @Test
    @DisplayName("Should respect maxPathwaysLimit when running with many objects and no closing objects")
    void shouldRespectMaxPathwaysLimitWithoutClosingObjects() {
        InferPathwaysUseCase cappedUseCase = new InferPathwaysUseCase(
                prologInferencePort, pathwayRepository, knowledgeBaseRepository, inferenceRepository,
                "output", 4, 30, 5, 60);

        List<KbObject> objects = new ArrayList<>();
        List<PathwayEvent> events = new ArrayList<>();

        objects.add(KbObject.builder().name("LIG1").roles(List.of("ligand", "protein")).build());
        for (int i = 1; i <= 15; i++) {
            String pName = "PROT_" + i;
            objects.add(KbObject.builder().name(pName).roles(List.of("protein")).build());
            events.add(new PathwayEvent("LIG1", "association", pName));
            for (int j = 1; j <= 15; j++) {
                if (i != j) {
                    events.add(new PathwayEvent(pName, "association", "PROT_" + j));
                    events.add(new PathwayEvent(pName, "inhibit", "PROT_" + j));
                }
            }
        }

        DynamicInferenceInput input = DynamicInferenceInput.builder()
                .pipelineId("pipe-capped-test")
                .user("test-user")
                .objects(objects)
                .events(events)
                .closingObjects(List.of())
                .summarizeKb(true)
                .maxDepth(5)
                .build();

        DynamicExecutionResult result = cappedUseCase.runDynamicInference(input);

        assertNotNull(result);
        assertEquals(5, result.getGeneratedPathwaysCount());
        assertEquals(5, result.getPathways().size());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when pipelineId is blank")
    void shouldThrowWhenPipelineIdIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                useCase.runPipelineInference("", "user", List.of(), List.of(), false, 5));
        assertThrows(IllegalArgumentException.class, () ->
                useCase.runPipelineInference(null, "user", List.of(), List.of(), false, 5));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when no events found in KB for pipelineId")
    void shouldThrowWhenNoEventsFoundInKnowledgeBase() {
        Inference inference = Inference.builder()
                .pipelineId("empty-pipe")
                .roles(Map.of("A", List.of("protein")))
                .build();
        when(inferenceRepository.findByPipelineId("empty-pipe")).thenReturn(Optional.of(inference));
        when(knowledgeBaseRepository.findEventsByPipelineIdAndRestriction(eq("empty-pipe"), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () ->
                useCase.runPipelineInference("empty-pipe", "user", List.of(), List.of(), false, 5));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when no biological objects with roles found in inferences collection")
    void shouldThrowWhenNoObjectsFoundInInferencesCollection() {
        String pipeId = "pipe-no-objects";
        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                useCase.runPipelineInference(pipeId, "user", List.of(), List.of(), false, 5));
    }

    @Test
    @DisplayName("Should run pipeline inference, persist pathways and graph to repositories and return result")
    void shouldRunPipelineInferenceAndPersistToRepositories() {
        String pipeId = "prod-pipe-01";
        List<PathwayEvent> events = List.of(
                new PathwayEvent("IFNA1", "association", "IRF7"),
                new PathwayEvent("IRF7", "bind", "IRF3")
        );

        Map<String, List<String>> rolesMap = Map.of(
                "IFNA1", List.of("ligand"),
                "IRF7", List.of("protein"),
                "IRF3", List.of("transcription_factor")
        );
        Inference inference = Inference.builder()
                .pipelineId(pipeId)
                .roles(rolesMap)
                .build();

        when(knowledgeBaseRepository.findEventsByPipelineIdAndRestriction(eq(pipeId), any(), any())).thenReturn(events);
        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(inference));

        DynamicExecutionResult result = useCase.runPipelineInference(
                pipeId,
                "investigador_01",
                List.of("IFNA1", "IRF7", "IRF3"),
                List.of("IRF3"),
                false,
                5
        );

        assertNotNull(result);
        assertEquals(pipeId, result.getPipelineId());
        assertEquals("investigador_01", result.getUser());
        assertEquals(1, result.getGeneratedPathwaysCount());

        // Verify repository interactions
        verify(pathwayRepository).deleteByPipelineId(pipeId);
        verify(pathwayRepository).savePathwaysBatch(result.getPathways());
        verify(pathwayRepository).savePathwayGraph(result.getGraph());
    }

    @Test
    @DisplayName("Should query graph by pipelineId")
    void shouldQueryGraphByPipelineId() {
        PathwayGraph graph = PathwayGraph.builder().pipelineId("test-pipe").build();
        when(pathwayRepository.findGraphByPipelineId("test-pipe")).thenReturn(java.util.Optional.of(graph));
        when(pathwayRepository.findGraphByPipelineId("unknown")).thenReturn(java.util.Optional.empty());

        assertTrue(useCase.getPathwayGraph("test-pipe").isPresent());
        assertEquals("test-pipe", useCase.getPathwayGraph("test-pipe").get().getPipelineId());
        assertTrue(useCase.getPathwayGraph("unknown").isEmpty());
        assertTrue(useCase.getPathwayGraph("").isEmpty());
    }

    @Test
    @DisplayName("Should query pathways, count, and delete by pipelineId")
    void shouldQueryPathwaysCountAndDelete() {
        BiologicalPathway p = BiologicalPathway.builder().pipelineId("test-pipe").build();
        when(pathwayRepository.findByPipelineId("test-pipe", "IFNA1", "IRF3", null, 0, 10))
                .thenReturn(List.of(p));
        when(pathwayRepository.countByPipelineId("test-pipe")).thenReturn(42L);
        when(pathwayRepository.deleteByPipelineId("test-pipe")).thenReturn(42L);

        List<BiologicalPathway> res = useCase.getPathways("test-pipe", "IFNA1", "IRF3", null, 0, 10);
        assertEquals(1, res.size());
        assertEquals(42L, useCase.countPathways("test-pipe"));
        assertEquals(42L, useCase.deletePathways("test-pipe"));

        assertEquals(0, useCase.getPathways("", null, null, null, 0, 10).size());
        assertEquals(0L, useCase.countPathways(""));
        assertEquals(0L, useCase.deletePathways(""));
    }

    @Test
    @DisplayName("Should run pipeline inference loading objects with roles from inferences collection")
    void shouldRunPipelineInferenceLoadingObjectsFromInferencesCollection() {
        InferPathwaysUseCase useCaseWithInferenceRepo = new InferPathwaysUseCase(
                prologInferencePort,
                pathwayRepository,
                knowledgeBaseRepository,
                inferenceRepository,
                "output",
                4,
                30
        );

        String pipeId = "prod-pipe-inferences-roles";
        Map<String, List<String>> rolesMap = Map.of(
                "IFNA1", List.of("ligand"),
                "IRF7", List.of("protein"),
                "IRF3", List.of("transcription_factor")
        );
        Inference inference = Inference.builder()
                .pipelineId(pipeId)
                .roles(rolesMap)
                .build();

        List<PathwayEvent> events = List.of(
                new PathwayEvent("IFNA1", "association", "IRF7"),
                new PathwayEvent("IRF7", "bind", "IRF3")
        );

        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(inference));
        when(knowledgeBaseRepository.findEventsByPipelineIdAndRestriction(eq(pipeId), any(), any())).thenReturn(events);

        DynamicExecutionResult result = useCaseWithInferenceRepo.runPipelineInference(
                pipeId,
                "investigador_01",
                List.of("IFNA1", "IRF7", "IRF3"),
                List.of("IRF3"),
                false,
                5
        );

        assertNotNull(result);
        assertEquals(pipeId, result.getPipelineId());
        assertEquals(1, result.getGeneratedPathwaysCount());

        verify(pathwayRepository).savePathwaysBatch(result.getPathways());
        verify(pathwayRepository).savePathwayGraph(result.getGraph());
    }

    @Test
    @DisplayName("Should pass restrictionLevel from inference and aligned objects to KnowledgeBaseRepository")
    void shouldPassRestrictionLevelAndAlignedObjectsToKnowledgeBaseRepository() {
        String pipeId = "pipe-restricted-events";
        Inference inference = Inference.builder()
                .pipelineId(pipeId)
                .restrictionLevel("RESTRICTED")
                .roles(Map.of("IFNA1", List.of("ligand"), "IRF3", List.of("transcription_factor")))
                .build();
        List<PathwayEvent> events = List.of(new PathwayEvent("IFNA1", "association", "IRF3"));

        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(inference));
        when(knowledgeBaseRepository.findEventsByPipelineIdAndRestriction(eq(pipeId), eq("RESTRICTED"), any()))
                .thenReturn(events);

        DynamicExecutionResult result = useCase.runPipelineInference(
                pipeId,
                "user1",
                List.of(),
                List.of(),
                false,
                5
        );

        assertNotNull(result);
        verify(knowledgeBaseRepository).findEventsByPipelineIdAndRestriction(eq(pipeId), eq("RESTRICTED"), any());
    }
}
