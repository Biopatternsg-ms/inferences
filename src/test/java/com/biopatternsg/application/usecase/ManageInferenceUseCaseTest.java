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

import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManageInferenceUseCaseTest {

    @Mock
    private InferenceRepository inferenceRepository;

    @Mock
    private ConfigAndControlRepository configAndControlRepository;

    @InjectMocks
    private ManageInferenceUseCase manageInferenceUseCase;

    private Inference sampleInference;

    @BeforeEach
    void setUp() {
        sampleInference = Inference.builder()
                .id("60c72b2f9b1d8b2bad000001")
                .pipelineId("pipeline-123")
                .restrictionLevel("RESTRICTED")
                .summarizeKb(true)
                .maxDepth(5)
                .build();
    }

    @Test
    @DisplayName("createInference saves inference configuration with defaults (summarizeKb=true, maxDepth=5) and updates pipeline step")
    void testCreateInference() {
        when(inferenceRepository.save(any(Inference.class))).thenReturn(sampleInference);

        Inference input = Inference.builder()
                .pipelineId("pipeline-123")
                .restrictionLevel("RESTRICTED")
                .build();

        Inference created = manageInferenceUseCase.createInference(input, "user-123");

        assertNotNull(created);
        assertEquals("pipeline-123", created.getPipelineId());
        assertEquals("RESTRICTED", created.getRestrictionLevel());
        assertEquals(Boolean.TRUE, input.getSummarizeKb());
        assertEquals(10, input.getMaxDepth());
        assertEquals(List.of("LIGAND"), input.getStartFirstRoles());
        assertEquals(List.of("PROTEIN"), input.getStartSecondRoles());
        assertEquals(List.of("PROTEIN"), input.getIntermediateFirstRoles());
        assertEquals(List.of("PROTEIN"), input.getIntermediateSecondRoles());
        assertEquals(List.of("PROTEIN"), input.getEndFirstRoles());
        assertEquals(List.of("PROTEIN"), input.getEndSecondRoles());
        verify(inferenceRepository).save(any(Inference.class));

        Map<String, String> expectedMetrics = new LinkedHashMap<>();
        expectedMetrics.put("Restriction Level", "RESTRICTED");
        expectedMetrics.put("Summarize KB", "true");
        expectedMetrics.put("Max Depth", "10");
        expectedMetrics.put("Start First Roles", "LIGAND");
        expectedMetrics.put("Start Second Roles", "PROTEIN");
        expectedMetrics.put("Intermediate First Roles", "PROTEIN");
        expectedMetrics.put("Intermediate Second Roles", "PROTEIN");
        expectedMetrics.put("End First Roles", "PROTEIN");
        expectedMetrics.put("End Second Roles", "PROTEIN");

        verify(configAndControlRepository).updateStep(
                "pipeline-123",
                "CONFIGURE_INFERENCES",
                "COMPLETED",
                "user-123",
                expectedMetrics
        );
    }

    @Test
    @DisplayName("getByPipelineId returns inference when found")
    void testGetByPipelineId() {
        when(inferenceRepository.findByPipelineId("pipeline-123")).thenReturn(Optional.of(sampleInference));

        Optional<Inference> result = manageInferenceUseCase.getByPipelineId("pipeline-123");

        assertTrue(result.isPresent());
        assertEquals("RESTRICTED", result.get().getRestrictionLevel());
    }

    @Test
    @DisplayName("getInferenceById returns inference when found")
    void testGetInferenceByIdFound() {
        when(inferenceRepository.findById("60c72b2f9b1d8b2bad000001")).thenReturn(Optional.of(sampleInference));

        Optional<Inference> result = manageInferenceUseCase.getInferenceById("60c72b2f9b1d8b2bad000001");

        assertTrue(result.isPresent());
        assertEquals("60c72b2f9b1d8b2bad000001", result.get().getId());
    }

    @Test
    @DisplayName("getAllInferences returns all items")
    void testGetAllInferences() {
        when(inferenceRepository.findAllInferences()).thenReturn(List.of(sampleInference));

        List<Inference> list = manageInferenceUseCase.getAllInferences();

        assertEquals(1, list.size());
        assertEquals("pipeline-123", list.get(0).getPipelineId());
    }
}
