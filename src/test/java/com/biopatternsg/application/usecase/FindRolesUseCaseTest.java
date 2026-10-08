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
import com.biopatternsg.domain.model.KbEvent;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.OntologiesRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindRolesUseCaseTest {

    @Mock
    private InferenceRepository inferenceRepository;

    @Mock
    private OntologiesRepository ontologiesRepository;

    @Mock
    private PubmedIntegrationRepository pubmedIntegrationRepository;

    @Mock
    private ConfigAndControlRepository configAndControlRepository;

    @InjectMocks
    private FindRolesUseCase findRolesUseCase;

    private Inference veryRestrictedInference;
    private Inference restrictedInference;

    @BeforeEach
    void setUp() {
        veryRestrictedInference = Inference.builder()
                .id("60c72b2f9b1d8b2bad000001")
                .pipelineId("pipeline-123")
                .restrictionLevel("VERY_RESTRICTED")
                .build();

        restrictedInference = Inference.builder()
                .id("60c72b2f9b1d8b2bad000002")
                .pipelineId("pipeline-456")
                .restrictionLevel("RESTRICTED")
                .build();
    }

    @Test
    @DisplayName("execute for VERY_RESTRICTED queries MeSH IDs and evaluates categories for found terms")
    void testExecute_VeryRestricted_Success() {
        when(inferenceRepository.findByPipelineId("pipeline-123")).thenReturn(Optional.of(veryRestrictedInference));

        List<String> alignedObjects = List.of("BRCA1", "UNKNOWN_ENTITY", "TP53");

        when(ontologiesRepository.searchMeshId(List.of("BRCA1"))).thenReturn(Optional.of("D019084"));
        when(ontologiesRepository.searchMeshId(List.of("UNKNOWN_ENTITY"))).thenReturn(Optional.empty());
        when(ontologiesRepository.searchMeshId(List.of("TP53"))).thenReturn(Optional.of("D016159"));

        Map<String, Boolean> brca1Categories = Map.of(
                "PROTEIN", true,
                "ENZYME", false,
                "RECEPTOR", false,
                "LIGAND", false,
                "TRANSCRIPTION_FACTOR", false,
                "ADAPTOR_PROTEIN", false
        );
        Map<String, Boolean> tp53Categories = Map.of(
                "PROTEIN", true,
                "ENZYME", false,
                "RECEPTOR", false,
                "LIGAND", false,
                "TRANSCRIPTION_FACTOR", true,
                "ADAPTOR_PROTEIN", false
        );

        when(ontologiesRepository.checkAllTypes("D019084")).thenReturn(brca1Categories);
        when(ontologiesRepository.checkAllTypes("D016159")).thenReturn(tp53Categories);

        findRolesUseCase.execute("pipeline-123", alignedObjects, "user-123");

        ArgumentCaptor<Inference> inferenceCaptor = ArgumentCaptor.forClass(Inference.class);
        verify(inferenceRepository).save(inferenceCaptor.capture());

        Inference saved = inferenceCaptor.getValue();
        assertNotNull(saved.getRoles());
        assertEquals(3, saved.getRoles().size());
        assertEquals(List.of("PROTEIN"), saved.getRoles().get("BRCA1"));
        assertEquals(List.of("PROTEIN", "TRANSCRIPTION_FACTOR"), saved.getRoles().get("TP53"));
        assertTrue(saved.getRoles().containsKey("UNKNOWN_ENTITY"));
        assertTrue(saved.getRoles().get("UNKNOWN_ENTITY").isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq("pipeline-123"),
                eq("FIND_ROLES"),
                eq("COMPLETED"),
                eq("user-123"),
                metricsCaptor.capture()
        );

        Map<String, String> capturedMetrics = metricsCaptor.getValue();
        assertEquals("VERY_RESTRICTED", capturedMetrics.get("restrictionLevel"));
        assertEquals("3", capturedMetrics.get("totalAlignedObjects"));
        assertEquals("2", capturedMetrics.get("meshIdsFound"));
        assertEquals("3", capturedMetrics.get("rolesIdentified"));
        assertEquals("2", capturedMetrics.get("entitiesWithActiveRoles"));
    }

    @Test
    @DisplayName("execute for VERY_RESTRICTED deduplicates aligned objects to avoid redundant network calls")
    void testExecute_VeryRestricted_DeduplicatesSymbols() {
        when(inferenceRepository.findByPipelineId("pipeline-123")).thenReturn(Optional.of(veryRestrictedInference));

        List<String> duplicateAlignedObjects = List.of("BRCA1", "BRCA1", "  BRCA1  ", "TP53");

        when(ontologiesRepository.searchMeshId(List.of("BRCA1"))).thenReturn(Optional.of("D019084"));
        when(ontologiesRepository.searchMeshId(List.of("TP53"))).thenReturn(Optional.of("D016159"));

        when(ontologiesRepository.checkAllTypes("D019084")).thenReturn(Map.of("PROTEIN", true));
        when(ontologiesRepository.checkAllTypes("D016159")).thenReturn(Map.of("PROTEIN", true));

        findRolesUseCase.execute("pipeline-123", duplicateAlignedObjects, "user-123");

        // Verify searchMeshId was called only ONCE for BRCA1 despite 3 occurrences
        verify(ontologiesRepository, times(1)).searchMeshId(List.of("BRCA1"));
        verify(ontologiesRepository, times(1)).searchMeshId(List.of("TP53"));
        verify(ontologiesRepository, times(1)).checkAllTypes("D019084");
        verify(ontologiesRepository, times(1)).checkAllTypes("D016159");
    }

    @Test
    @DisplayName("execute for RESTRICTED fetches events from pubmed-integration and evaluates union of base and companion objects")
    void testExecute_Restricted_Success_EvaluatesUnionOfObjects() {
        when(inferenceRepository.findByPipelineId("pipeline-456")).thenReturn(Optional.of(restrictedInference));

        // Base aligned objects
        List<String> alignedObjects = List.of("BRCA1");

        // Biological events involving BRCA1
        KbEvent event1 = new KbEvent("pipeline-456", "BRCA1", "BINDS_TO", "RAD51", List.of("11111"));
        KbEvent event2 = new KbEvent("pipeline-456", "BARD1", "INTERACTS_WITH", "BRCA1", List.of("22222"));
        when(pubmedIntegrationRepository.getEventsByTerm("pipeline-456", "BRCA1")).thenReturn(List.of(event1, event2));

        // Mock MeSH searches for BRCA1, RAD51, BARD1
        when(ontologiesRepository.searchMeshId(List.of("BRCA1"))).thenReturn(Optional.of("D019084"));
        when(ontologiesRepository.searchMeshId(List.of("RAD51"))).thenReturn(Optional.of("D011833"));
        when(ontologiesRepository.searchMeshId(List.of("BARD1"))).thenReturn(Optional.of("D000072080"));

        when(ontologiesRepository.checkAllTypes("D019084")).thenReturn(Map.of("PROTEIN", true));
        when(ontologiesRepository.checkAllTypes("D011833")).thenReturn(Map.of("PROTEIN", true, "ENZYME", true));
        when(ontologiesRepository.checkAllTypes("D000072080")).thenReturn(Map.of("PROTEIN", true));

        findRolesUseCase.execute("pipeline-456", alignedObjects, "user-123");

        // Verify roles saved
        ArgumentCaptor<Inference> inferenceCaptor = ArgumentCaptor.forClass(Inference.class);
        verify(inferenceRepository).save(inferenceCaptor.capture());

        Inference saved = inferenceCaptor.getValue();
        assertNotNull(saved.getRoles());
        assertEquals(3, saved.getRoles().size());
        assertEquals(List.of("PROTEIN"), saved.getRoles().get("BRCA1"));
        assertEquals(List.of("ENZYME", "PROTEIN"), saved.getRoles().get("RAD51"));
        assertEquals(List.of("PROTEIN"), saved.getRoles().get("BARD1"));

        // Verify metrics
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq("pipeline-456"),
                eq("FIND_ROLES"),
                eq("COMPLETED"),
                eq("user-123"),
                metricsCaptor.capture()
        );

        Map<String, String> capturedMetrics = metricsCaptor.getValue();
        assertEquals("RESTRICTED", capturedMetrics.get("restrictionLevel"));
        assertEquals("1", capturedMetrics.get("totalAlignedObjects"));
        assertEquals("2", capturedMetrics.get("coOccurringObjectsFound"));
        assertEquals("3", capturedMetrics.get("totalEvaluatedObjects"));
        assertEquals("3", capturedMetrics.get("meshIdsFound"));
        assertEquals("3", capturedMetrics.get("rolesIdentified"));
        assertEquals("3", capturedMetrics.get("entitiesWithActiveRoles"));
    }

    @Test
    @DisplayName("execute for RESTRICTED when no events are found evaluates only base aligned objects")
    void testExecute_Restricted_WhenNoEventsFound_EvaluatesOnlyBaseObjects() {
        when(inferenceRepository.findByPipelineId("pipeline-456")).thenReturn(Optional.of(restrictedInference));

        List<String> alignedObjects = List.of("BRCA1");
        when(pubmedIntegrationRepository.getEventsByTerm("pipeline-456", "BRCA1")).thenReturn(Collections.emptyList());

        when(ontologiesRepository.searchMeshId(List.of("BRCA1"))).thenReturn(Optional.of("D019084"));
        when(ontologiesRepository.checkAllTypes("D019084")).thenReturn(Map.of("PROTEIN", true));

        findRolesUseCase.execute("pipeline-456", alignedObjects, "user-123");

        ArgumentCaptor<Inference> inferenceCaptor = ArgumentCaptor.forClass(Inference.class);
        verify(inferenceRepository).save(inferenceCaptor.capture());

        Inference saved = inferenceCaptor.getValue();
        assertEquals(1, saved.getRoles().size());
        assertEquals(List.of("PROTEIN"), saved.getRoles().get("BRCA1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq("pipeline-456"),
                eq("FIND_ROLES"),
                eq("COMPLETED"),
                eq("user-123"),
                metricsCaptor.capture()
        );

        Map<String, String> capturedMetrics = metricsCaptor.getValue();
        assertEquals("RESTRICTED", capturedMetrics.get("restrictionLevel"));
        assertEquals("1", capturedMetrics.get("totalAlignedObjects"));
        assertEquals("0", capturedMetrics.get("coOccurringObjectsFound"));
        assertEquals("1", capturedMetrics.get("totalEvaluatedObjects"));
    }

    @Test
    @DisplayName("execute for UNRESTRICTED evaluates all objects found in pipeline events")
    void testExecute_Unrestricted_Success_EvaluatesAllObjectsInPipelineEvents() {
        Inference unrestricted = Inference.builder()
                .pipelineId("pipeline-789")
                .restrictionLevel("UNRESTRICTED")
                .build();
        when(inferenceRepository.findByPipelineId("pipeline-789")).thenReturn(Optional.of(unrestricted));

        KbEvent event1 = new KbEvent("pipeline-789", "TP53", "binds", "MDM2", List.of("111"));
        KbEvent event2 = new KbEvent("pipeline-789", "MDM2", "degrades", "CDKN1A", List.of("222"));
        when(pubmedIntegrationRepository.getEventsByPipeline("pipeline-789")).thenReturn(List.of(event1, event2));

        when(ontologiesRepository.searchMeshId(List.of("TP53"))).thenReturn(Optional.of("D016159"));
        when(ontologiesRepository.searchMeshId(List.of("MDM2"))).thenReturn(Optional.of("D000071239"));
        when(ontologiesRepository.searchMeshId(List.of("CDKN1A"))).thenReturn(Optional.of("D019941"));

        when(ontologiesRepository.checkAllTypes("D016159")).thenReturn(Map.of("PROTEIN", true));
        when(ontologiesRepository.checkAllTypes("D000071239")).thenReturn(Map.of("PROTEIN", true, "ENZYME", true));
        when(ontologiesRepository.checkAllTypes("D019941")).thenReturn(Map.of("PROTEIN", true));

        findRolesUseCase.execute("pipeline-789", Collections.emptyList(), "user-123");

        ArgumentCaptor<Inference> inferenceCaptor = ArgumentCaptor.forClass(Inference.class);
        verify(inferenceRepository).save(inferenceCaptor.capture());

        Inference saved = inferenceCaptor.getValue();
        assertEquals(3, saved.getRoles().size());
        assertTrue(saved.getRoles().containsKey("TP53"));
        assertTrue(saved.getRoles().containsKey("MDM2"));
        assertTrue(saved.getRoles().containsKey("CDKN1A"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq("pipeline-789"),
                eq("FIND_ROLES"),
                eq("COMPLETED"),
                eq("user-123"),
                metricsCaptor.capture()
        );

        Map<String, String> metrics = metricsCaptor.getValue();
        assertEquals("UNRESTRICTED", metrics.get("restrictionLevel"));
        assertEquals("2", metrics.get("totalEvents"));
        assertEquals("3", metrics.get("totalEvaluatedObjects"));
        assertEquals("3", metrics.get("rolesIdentified"));
    }

    @Test
    @DisplayName("execute for UNRESTRICTED when no events exist completes with zero evaluated objects")
    void testExecute_Unrestricted_WhenNoEventsFound() {
        Inference unrestricted = Inference.builder()
                .pipelineId("pipeline-789")
                .restrictionLevel("NO_RESTRICTED")
                .build();
        when(inferenceRepository.findByPipelineId("pipeline-789")).thenReturn(Optional.of(unrestricted));
        when(pubmedIntegrationRepository.getEventsByPipeline("pipeline-789")).thenReturn(Collections.emptyList());

        findRolesUseCase.execute("pipeline-789", Collections.emptyList(), "user-123");

        verifyNoInteractions(ontologiesRepository);

        ArgumentCaptor<Inference> inferenceCaptor = ArgumentCaptor.forClass(Inference.class);
        verify(inferenceRepository).save(inferenceCaptor.capture());
        assertTrue(inferenceCaptor.getValue().getRoles().isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configAndControlRepository).updateStep(
                eq("pipeline-789"),
                eq("FIND_ROLES"),
                eq("COMPLETED"),
                eq("user-123"),
                metricsCaptor.capture()
        );

        Map<String, String> metrics = metricsCaptor.getValue();
        assertEquals("NO_RESTRICTED", metrics.get("restrictionLevel"));
        assertEquals("0", metrics.get("totalEvents"));
        assertEquals("0", metrics.get("totalEvaluatedObjects"));
    }

    @Test
    @DisplayName("execute for unknown restriction level skips role evaluation and completes step")
    void testExecute_UnknownRestrictionLevel_SkipsRoleCheck() {
        Inference customLevel = Inference.builder()
                .pipelineId("pipeline-999")
                .restrictionLevel("CUSTOM_LEVEL")
                .build();
        when(inferenceRepository.findByPipelineId("pipeline-999")).thenReturn(Optional.of(customLevel));

        findRolesUseCase.execute("pipeline-999", List.of("BRCA1"), "user-123");

        verifyNoInteractions(ontologiesRepository);
        verifyNoInteractions(pubmedIntegrationRepository);
        verify(configAndControlRepository).updateStep(
                eq("pipeline-999"),
                eq("FIND_ROLES"),
                eq("COMPLETED"),
                eq("user-123"),
                argThat(metrics -> metrics.get("statusMessage").contains("deferred"))
        );
    }

    @Test
    @DisplayName("execute when pipeline inference configuration not found marks step as FAILED")
    void testExecute_InferenceNotFound_MarksFailed() {
        when(inferenceRepository.findByPipelineId("unknown-pipeline")).thenReturn(Optional.empty());

        findRolesUseCase.execute("unknown-pipeline", List.of("BRCA1"), "user-123");

        verify(configAndControlRepository).updateStep(
                eq("unknown-pipeline"),
                eq("FIND_ROLES"),
                eq("FAILED"),
                eq("user-123"),
                argThat(metrics -> metrics.containsKey("error"))
        );
    }

    @Test
    @DisplayName("execute handles unexpected exceptions by marking step as FAILED")
    void testExecute_UnexpectedException_MarksFailed() {
        when(inferenceRepository.findByPipelineId("pipeline-123")).thenReturn(Optional.of(veryRestrictedInference));
        when(ontologiesRepository.searchMeshId(anyList())).thenThrow(new RuntimeException("Network down"));

        findRolesUseCase.execute("pipeline-123", List.of("BRCA1"), "user-123");

        verify(configAndControlRepository).updateStep(
                eq("pipeline-123"),
                eq("FIND_ROLES"),
                eq("FAILED"),
                eq("user-123"),
                argThat(metrics -> metrics.containsKey("error"))
        );
    }
}
