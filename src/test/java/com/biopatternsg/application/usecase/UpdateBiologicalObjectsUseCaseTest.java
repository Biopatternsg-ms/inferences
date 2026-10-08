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
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateBiologicalObjectsUseCaseTest {

    @Mock
    private InferenceRepository inferenceRepository;

    @Mock
    private PubmedIntegrationRepository pubmedIntegrationRepository;

    @Mock
    private ConfigAndControlRepository configAndControlRepository;

    private UpdateBiologicalObjectsUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new UpdateBiologicalObjectsUseCase(
                inferenceRepository,
                pubmedIntegrationRepository,
                configAndControlRepository
        );
    }

    @Test
    void execute_updatesRolesAndCompletesStep() {
        String pipelineId = "pipe-123";
        String userId = "user-abc";
        Map<String, List<String>> rolesMap = Map.of(
                "TP53", List.of("PROTEIN", "TRANSCRIPTION_FACTOR"),
                "BRCA1", List.of("PROTEIN"),
                "UNASSIGNED", Collections.emptyList()
        );

        Inference inference = Inference.builder()
                .pipelineId(pipelineId)
                .roles(Map.of("TP53", List.of("PROTEIN")))
                .build();

        when(inferenceRepository.findByPipelineId(pipelineId)).thenReturn(Optional.of(inference));

        useCase.execute(pipelineId, rolesMap, userId);

        verify(pubmedIntegrationRepository).updateKbObjectRoles(pipelineId, rolesMap);
        verify(inferenceRepository).save(inference);
        assertEquals(rolesMap, inference.getRoles());

        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq(pipelineId),
                eq("update_biological_objects"),
                eq("COMPLETED"),
                eq(userId),
                metricsCaptor.capture()
        );

        Map<String, String> metrics = metricsCaptor.getValue();
        assertEquals("3", metrics.get("totalBiologicalObjects"));
        assertEquals("2", metrics.get("objectsWithRoles"));
        assertEquals("3", metrics.get("totalRolesAssigned"));
    }

    @Test
    void execute_throwsExceptionWhenParametersAreInvalid() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, Map.of(), "user-1"));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("   ", Map.of(), "user-1"));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("pipe-1", null, "user-1"));
    }
}
