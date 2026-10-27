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
import com.biopatternsg.domain.model.GraphEdge;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.model.PathwayObject;
import com.biopatternsg.domain.port.in.InferPathways;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaveClosingObjectsUseCaseTest {

    @Mock
    private InferenceRepository inferenceRepository;

    @Mock
    private ConfigAndControlRepository configAndControlRepository;

    @Mock
    private InferPathways inferPathways;

    private SaveClosingObjectsUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new SaveClosingObjectsUseCase(
                inferenceRepository,
                configAndControlRepository,
                inferPathways
        );
    }

    @Test
    @DisplayName("execute should throw IllegalArgumentException when pipelineId is null or blank")
    void execute_nullPipelineId_throws() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, List.of("TP53"), false, 5, "user-1"));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("   ", List.of("TP53"), false, 5, "user-1"));
    }

    @Test
    @DisplayName("execute should save closing objects, notify config-and-control, and run inference")
    void execute_successfulRun() {
        String pipeId = "pipe-100";
        List<String> closing = List.of("IRF3", "IRF7");
        Inference inference = Inference.builder()
                .pipelineId(pipeId)
                .restrictionLevel("RESTRICTED")
                .build();

        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(inference));

        DynamicExecutionResult mockResult = DynamicExecutionResult.builder()
                .pipelineId(pipeId)
                .executionTimeMs(150L)
                .generatedPathwaysCount(2)
                .pathways(List.of(
                        BiologicalPathway.builder().id("pw-1").build(),
                        BiologicalPathway.builder().id("pw-2").build()
                ))
                .graph(PathwayGraph.builder()
                        .pipelineId(pipeId)
                        .nodes(List.of(
                                PathwayObject.builder().name("A").build(),
                                PathwayObject.builder().name("B").build(),
                                PathwayObject.builder().name("C").build()
                        ))
                        .edges(List.of(GraphEdge.builder().first("A").relation("association").second("B").build()))
                        .build())
                .build();

        when(inferPathways.runPipelineInference(eq(pipeId), eq("user-1"), isNull(), eq(closing), eq(false), eq(5)))
                .thenReturn(mockResult);

        DynamicExecutionResult result = useCase.execute(pipeId, closing, false, 5, "user-1");

        assertNotNull(result);
        assertEquals(2, result.getPathways().size());
        assertEquals(closing, inference.getClosingObjects());
        verify(inferenceRepository).save(inference);

        // Verify config-and-control calls:
        // 1. SELECT_CLOSING_OBJECTS -> COMPLETED
        ArgumentCaptor<Map<String, String>> closingMetricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq(pipeId),
                eq("SELECT_CLOSING_OBJECTS"),
                eq("COMPLETED"),
                eq("user-1"),
                closingMetricsCaptor.capture()
        );
        assertEquals("2", closingMetricsCaptor.getValue().get("closingObjectsCount"));

        // 2. INFER_PATHWAYS -> IN_PROGRESS
        verify(configAndControlRepository).updateStep(
                eq(pipeId),
                eq("INFER_PATHWAYS"),
                eq("IN_PROGRESS"),
                eq("user-1"),
                anyMap()
        );

        // 3. INFER_PATHWAYS -> COMPLETED
        ArgumentCaptor<Map<String, String>> inferMetricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq(pipeId),
                eq("INFER_PATHWAYS"),
                eq("COMPLETED"),
                eq("user-1"),
                inferMetricsCaptor.capture()
        );
        assertEquals("2", inferMetricsCaptor.getValue().get("pathwaysCount"));
        assertEquals("3", inferMetricsCaptor.getValue().get("graphNodesCount"));
        assertEquals("1", inferMetricsCaptor.getValue().get("graphEdgesCount"));
    }

    @Test
    @DisplayName("execute should handle empty closing objects and use default summarizeKb=true and maxDepth=5")
    void execute_emptyClosingObjects() {
        String pipeId = "pipe-200";
        Inference inference = Inference.builder().pipelineId(pipeId).build();
        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(inference));

        DynamicExecutionResult mockResult = DynamicExecutionResult.builder()
                .pipelineId(pipeId)
                .executionTimeMs(90L)
                .generatedPathwaysCount(0)
                .pathways(Collections.emptyList())
                .graph(PathwayGraph.builder().nodes(List.of()).edges(List.of()).build())
                .build();

        when(inferPathways.runPipelineInference(eq(pipeId), any(), isNull(), eq(List.of()), anyBoolean(), anyInt()))
                .thenReturn(mockResult);

        DynamicExecutionResult result = useCase.execute(pipeId, Collections.emptyList(), null, null, "user-2");

        assertNotNull(result);
        assertTrue(inference.getClosingObjects().isEmpty());
        verify(inferPathways).runPipelineInference(eq(pipeId), eq("user-2"), isNull(), eq(List.of()), eq(true), eq(5));
    }

    @Test
    @DisplayName("execute should mark INFER_PATHWAYS as FAILED if inference throws an exception")
    void execute_inferenceThrows_marksFailed() {
        String pipeId = "pipe-err";
        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(new Inference()));
        when(inferPathways.runPipelineInference(any(), any(), any(), any(), anyBoolean(), anyInt()))
                .thenThrow(new RuntimeException("Prolog timeout"));

        assertThrows(RuntimeException.class, () -> useCase.execute(pipeId, List.of("TP53"), false, 5, "user-err"));

        verify(configAndControlRepository).updateStep(
                eq(pipeId),
                eq("INFER_PATHWAYS"),
                eq("FAILED"),
                eq("user-err"),
                anyMap()
        );
    }

    @Test
    @DisplayName("execute should mark INFER_PATHWAYS as FAILED even if a fatal Error (e.g. OutOfMemoryError) is thrown")
    void execute_inferenceThrowsError_marksFailed() {
        String pipeId = "pipe-oom";
        when(inferenceRepository.findByPipelineId(pipeId)).thenReturn(Optional.of(new Inference()));
        when(inferPathways.runPipelineInference(any(), any(), any(), any(), anyBoolean(), anyInt()))
                .thenThrow(new OutOfMemoryError("Java heap space"));

        assertThrows(OutOfMemoryError.class, () -> useCase.execute(pipeId, List.of(), true, 5, "user-oom"));

        verify(configAndControlRepository).updateStep(
                eq(pipeId),
                eq("INFER_PATHWAYS"),
                eq("FAILED"),
                eq("user-oom"),
                anyMap()
        );
    }

    @Test
    @DisplayName("getClosingObjects should return saved closing objects or empty list")
    void getClosingObjects_returnsList() {
        when(inferenceRepository.findByPipelineId("pipe-saved")).thenReturn(Optional.of(
                Inference.builder().closingObjects(List.of("TP53", "MDM2")).build()
        ));
        when(inferenceRepository.findByPipelineId("pipe-none")).thenReturn(Optional.empty());

        assertEquals(List.of("TP53", "MDM2"), useCase.getClosingObjects("pipe-saved"));
        assertEquals(Collections.emptyList(), useCase.getClosingObjects("pipe-none"));
        assertEquals(Collections.emptyList(), useCase.getClosingObjects("   "));
    }
}
