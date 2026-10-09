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

import com.biopatternsg.domain.port.in.FindRoles;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

class ResetBiologicalObjectsRolesUseCaseTest {

    @Mock
    private PubmedIntegrationRepository pubmedIntegrationRepository;

    @Mock
    private FindRoles findRoles;

    @Mock
    private ConfigAndControlRepository configAndControlRepository;

    private ResetBiologicalObjectsRolesUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new ResetBiologicalObjectsRolesUseCase(
                pubmedIntegrationRepository,
                findRoles,
                configAndControlRepository
        );
    }

    @Test
    void execute_successfulReset() {
        String pipelineId = "pipe-123";
        String userId = "user-abc";

        useCase.execute(pipelineId, userId);

        verify(pubmedIntegrationRepository).resetKbObjectRoles(pipelineId);
        verify(findRoles).execute(eq(pipelineId), isNull(), eq(userId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq(pipelineId),
                eq("UPDATE_BIOLOGICAL_OBJECTS"),
                eq("PENDING"),
                eq(userId),
                metricsCaptor.capture()
        );
        assertEquals("Biological objects roles reset to default biotypes and MeSH criteria",
                metricsCaptor.getValue().get("statusMessage"));
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
