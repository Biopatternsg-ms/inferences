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
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private ManageInferenceUseCase manageInferenceUseCase;

    private Inference sampleInference;

    @BeforeEach
    void setUp() {
        sampleInference = Inference.builder()
                .id("60c72b2f9b1d8b2bad000001")
                .modelName("dna-promoter-analyzer")
                .modelVersion("1.0.0")
                .status("COMPLETED")
                .inputData(Map.of("sequence", "ATGCGATCG"))
                .outputData(Map.of("score", 0.95))
                .confidence(0.95)
                .build();
    }

    @Test
    @DisplayName("createInference sets timestamp and saves inference")
    void testCreateInference() {
        when(inferenceRepository.save(any(Inference.class))).thenReturn(sampleInference);

        Inference input = Inference.builder()
                .modelName("dna-promoter-analyzer")
                .modelVersion("1.0.0")
                .build();

        Inference created = manageInferenceUseCase.createInference(input);

        assertNotNull(created);
        assertEquals("dna-promoter-analyzer", created.getModelName());
        verify(inferenceRepository).save(any(Inference.class));
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
        assertEquals("dna-promoter-analyzer", list.get(0).getModelName());
    }
}
