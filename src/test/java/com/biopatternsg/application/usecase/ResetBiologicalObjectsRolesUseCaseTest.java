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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResetBiologicalObjectsRolesUseCaseTest {

    @Mock
    private InferenceRepository inferenceRepository;

    @Mock
    private PubmedIntegrationRepository pubmedIntegrationRepository;

    @Mock
    private ConfigAndControlRepository configAndControlRepository;

    private ResetBiologicalObjectsRolesUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new ResetBiologicalObjectsRolesUseCase(
                inferenceRepository,
                pubmedIntegrationRepository,
                configAndControlRepository
        );
    }

    @Test
    void execute_successfulReset() {
        String pipelineId = "pipe-123";
        String userId = "user-abc";
        Inference existingInference = new Inference();
        existingInference.setRoles(Map.of("TP53", List.of("PROTEIN")));

        when(inferenceRepository.findByPipelineId(pipelineId)).thenReturn(Optional.of(existingInference));

        useCase.execute(pipelineId, userId);

        verify(pubmedIntegrationRepository).resetKbObjectRoles(pipelineId);

        ArgumentCaptor<Inference> inferenceCaptor = ArgumentCaptor.forClass(Inference.class);
        verify(inferenceRepository).save(inferenceCaptor.capture());
        assertNull(inferenceCaptor.getValue().getRoles());

        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq(pipelineId),
                eq("UPDATE_BIOLOGICAL_OBJECTS"),
                eq("PENDING"),
                eq(userId),
                metricsCaptor.capture()
        );
    }

    @Test
    void execute_throwsExceptionWhenPipelineIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, "user-1"));
    }

    @Test
    void execute_throwsExceptionWhenPipelineIdIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("   ", "user-1"));
    }
}
