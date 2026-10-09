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

import com.biopatternsg.domain.model.BiologicalObject;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GetBiologicalObjectsUseCaseTest {

    @Mock
    private InferenceRepository inferenceRepository;

    @Mock
    private PubmedIntegrationRepository pubmedIntegrationRepository;

    private GetBiologicalObjectsUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetBiologicalObjectsUseCase(inferenceRepository, pubmedIntegrationRepository);
    }

    @Test
    void execute_unifiesMeshRolesAndBiotypes() {
        String pipelineId = "pipe-123";
        Map<String, List<String>> meshRoles = new LinkedHashMap<>();
        meshRoles.put("TP53", List.of("PROTEIN", "TRANSCRIPTION_FACTOR"));
        meshRoles.put("UNKNOWN", Collections.emptyList());

        Inference inference = Inference.builder()
                .pipelineId(pipelineId)
                .restrictionLevel("RESTRICTED")
                .roles(meshRoles)
                .build();

        KbObject kbObj1 = new KbObject("TP53", List.of("P53"), List.of("gene"), Collections.emptyList());

        when(inferenceRepository.findByPipelineId(pipelineId)).thenReturn(Optional.of(inference));
        when(pubmedIntegrationRepository.getAllKbObjects(pipelineId)).thenReturn(List.of(kbObj1));

        List<BiologicalObject> result = useCase.execute(pipelineId);

        assertEquals(2, result.size());

        BiologicalObject tp53 = result.get(0);
        assertEquals("TP53", tp53.name());
        assertEquals(List.of("PROTEIN", "TRANSCRIPTION_FACTOR"), tp53.meshRoles());
        assertEquals(List.of("GENE"), tp53.biotypes());
        // Unified roles should contain PROTEIN, TRANSCRIPTION_FACTOR, and GENE
        assertTrue(tp53.roles().contains("PROTEIN"));
        assertTrue(tp53.roles().contains("TRANSCRIPTION_FACTOR"));
        assertTrue(tp53.roles().contains("GENE"));

        BiologicalObject unknown = result.get(1);
        assertEquals("UNKNOWN", unknown.name());
        assertTrue(unknown.meshRoles().isEmpty());
        assertTrue(unknown.roles().isEmpty());
    }

    @Test
    void execute_usesSavedRolesIfPresentInKbObject() {
        String pipelineId = "pipe-123";
        Map<String, List<String>> meshRoles = Map.of("TP53", List.of("PROTEIN"));

        Inference inference = Inference.builder()
                .pipelineId(pipelineId)
                .roles(meshRoles)
                .build();

        KbObject kbObj = new KbObject("TP53", List.of("P53"), List.of("gene"), List.of("CUSTOM_ROLE", "PROTEIN"));

        when(inferenceRepository.findByPipelineId(pipelineId)).thenReturn(Optional.of(inference));
        when(pubmedIntegrationRepository.getAllKbObjects(pipelineId)).thenReturn(List.of(kbObj));

        List<BiologicalObject> result = useCase.execute(pipelineId);

        assertEquals(1, result.size());
        assertEquals(List.of("CUSTOM_ROLE", "PROTEIN"), result.get(0).roles());
    }

    @Test
    void execute_returnsEmptyWhenPipelineNotFound() {
        when(inferenceRepository.findByPipelineId("nonexistent")).thenReturn(Optional.empty());

        List<BiologicalObject> result = useCase.execute("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void execute_resolvesKbObjectViaSynonymWhenNameDiffers() {
        String pipelineId = "pipe-123";
        Map<String, List<String>> meshRoles = Map.of("P53", List.of("PROTEIN"));

        Inference inference = Inference.builder()
                .pipelineId(pipelineId)
                .roles(meshRoles)
                .build();

        KbObject kbObj = new KbObject("TP53", List.of("P53"), List.of("gene"), List.of("TUMOR_SUPPRESSOR"));

        when(inferenceRepository.findByPipelineId(pipelineId)).thenReturn(Optional.of(inference));
        when(pubmedIntegrationRepository.getAllKbObjects(pipelineId)).thenReturn(List.of(kbObj));

        List<BiologicalObject> result = useCase.execute(pipelineId);

        assertEquals(1, result.size());
        assertEquals("P53", result.get(0).name());
        assertEquals(List.of("TUMOR_SUPPRESSOR"), result.get(0).roles());
    }

    @Test
    void execute_includesKbObjectsWithoutMeshRolesUsingBiotypes() {
        String pipelineId = "pipe-123";
        Map<String, List<String>> meshRoles = new LinkedHashMap<>();
        meshRoles.put("TP53", List.of("PROTEIN"));
        meshRoles.put("EGFR", Collections.emptyList()); // Evaluated in restriction, but no MeSH match

        Inference inference = Inference.builder()
                .pipelineId(pipelineId)
                .roles(meshRoles)
                .build();

        KbObject tp53 = new KbObject("TP53", List.of("P53"), List.of("protein"), Collections.emptyList());
        KbObject egfr = new KbObject("EGFR", List.of("ERBB1"), List.of("gene"), Collections.emptyList());

        when(inferenceRepository.findByPipelineId(pipelineId)).thenReturn(Optional.of(inference));
        when(pubmedIntegrationRepository.getAllKbObjects(pipelineId)).thenReturn(List.of(tp53, egfr));

        List<BiologicalObject> result = useCase.execute(pipelineId);

        assertEquals(2, result.size());

        BiologicalObject tp53Result = result.stream().filter(b -> b.name().equals("TP53")).findFirst().orElseThrow();
        assertEquals(List.of("PROTEIN"), tp53Result.meshRoles());
        assertEquals(List.of("PROTEIN"), tp53Result.biotypes());
        assertTrue(tp53Result.roles().contains("PROTEIN"));

        BiologicalObject egfrResult = result.stream().filter(b -> b.name().equals("EGFR")).findFirst().orElseThrow();
        assertTrue(egfrResult.meshRoles().isEmpty());
        assertEquals(List.of("GENE"), egfrResult.biotypes());
        assertEquals(List.of("GENE"), egfrResult.roles());
    }
}
