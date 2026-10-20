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
package com.biopatternsg.infrastructure.adapters.out;

import com.biopatternsg.domain.model.KbEvent;
import com.biopatternsg.infrastructure.clients.PubmedIntegrationHttpClient;
import com.biopatternsg.infrastructure.dtos.KbEventDTO;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubmedIntegrationRepositoryAdapterTest {

    @Mock
    private PubmedIntegrationHttpClient pubmedIntegrationHttpClient;

    @InjectMocks
    private PubmedIntegrationRepositoryAdapter adapter;

    @Test
    @DisplayName("getEventsByTerm maps DTOs to domain KbEvent correctly")
    void testGetEventsByTerm_Success() {
        KbEventDTO dto = new KbEventDTO("pipe-1", "BRCA1", "BINDS_TO", "RAD51", List.of("12345"));
        when(pubmedIntegrationHttpClient.getKbEventsByTerm("pipe-1", "BRCA1")).thenReturn(List.of(dto));

        List<KbEvent> result = adapter.getEventsByTerm("pipe-1", "BRCA1");

        assertNotNull(result);
        assertEquals(1, result.size());
        KbEvent event = result.get(0);
        assertEquals("pipe-1", event.pipelineId());
        assertEquals("BRCA1", event.first());
        assertEquals("BINDS_TO", event.relation());
        assertEquals("RAD51", event.second());
        assertEquals(List.of("12345"), event.pubmedIds());
    }

    @Test
    @DisplayName("getEventsByTerm handles null or empty response from client gracefully")
    void testGetEventsByTerm_EmptyOrNull() {
        when(pubmedIntegrationHttpClient.getKbEventsByTerm("pipe-1", "BRCA1")).thenReturn(null);
        assertTrue(adapter.getEventsByTerm("pipe-1", "BRCA1").isEmpty());

        when(pubmedIntegrationHttpClient.getKbEventsByTerm("pipe-1", "BRCA1")).thenReturn(Collections.emptyList());
        assertTrue(adapter.getEventsByTerm("pipe-1", "BRCA1").isEmpty());
    }

    @Test
    @DisplayName("getEventsByTerm returns empty list on 404 WebApplicationException")
    void testGetEventsByTerm_404_ReturnsEmpty() {
        Response response = Response.status(404).build();
        when(pubmedIntegrationHttpClient.getKbEventsByTerm("pipe-1", "BRCA1"))
                .thenThrow(new WebApplicationException(response));

        List<KbEvent> result = adapter.getEventsByTerm("pipe-1", "BRCA1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getEventsByTerm returns empty list on general exception")
    void testGetEventsByTerm_GeneralException_ReturnsEmpty() {
        when(pubmedIntegrationHttpClient.getKbEventsByTerm("pipe-1", "BRCA1"))
                .thenThrow(new RuntimeException("Connection timeout"));

        List<KbEvent> result = adapter.getEventsByTerm("pipe-1", "BRCA1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getEventsByTerm returns empty list if parameters are blank")
    void testGetEventsByTerm_BlankParams_ReturnsEmpty() {
        assertTrue(adapter.getEventsByTerm("", "BRCA1").isEmpty());
        assertTrue(adapter.getEventsByTerm("pipe-1", "").isEmpty());
        assertTrue(adapter.getEventsByTerm(null, null).isEmpty());

        verifyNoInteractions(pubmedIntegrationHttpClient);
    }

    @Test
    @DisplayName("getEventsByPipeline maps DTOs to domain KbEvent correctly")
    void testGetEventsByPipeline_Success() {
        KbEventDTO dto1 = new KbEventDTO("pipe-1", "BRCA1", "BINDS_TO", "RAD51", List.of("12345"));
        KbEventDTO dto2 = new KbEventDTO("pipe-1", "TP53", "ACTIVATES", "MDM2", List.of("67890"));
        when(pubmedIntegrationHttpClient.getKbEventsByPipeline("pipe-1")).thenReturn(List.of(dto1, dto2));

        List<KbEvent> result = adapter.getEventsByPipeline("pipe-1");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("BRCA1", result.get(0).first());
        assertEquals("TP53", result.get(1).first());
    }

    @Test
    @DisplayName("getEventsByPipeline handles empty or 404 response gracefully")
    void testGetEventsByPipeline_EmptyAnd404() {
        when(pubmedIntegrationHttpClient.getKbEventsByPipeline("pipe-1")).thenReturn(Collections.emptyList());
        assertTrue(adapter.getEventsByPipeline("pipe-1").isEmpty());

        Response response = Response.status(404).build();
        when(pubmedIntegrationHttpClient.getKbEventsByPipeline("pipe-2"))
                .thenThrow(new WebApplicationException(response));
        assertTrue(adapter.getEventsByPipeline("pipe-2").isEmpty());

        assertTrue(adapter.getEventsByPipeline(null).isEmpty());
        assertTrue(adapter.getEventsByPipeline("   ").isEmpty());
    }

    @Test
    @DisplayName("getAllKbObjects maps DTOs to domain KbObject correctly")
    void testGetAllKbObjects_Success() {
        com.biopatternsg.infrastructure.dtos.KbObjectDTO dto = new com.biopatternsg.infrastructure.dtos.KbObjectDTO(
                "TP53", List.of("P53"), List.of("gene"), List.of("PROTEIN")
        );
        when(pubmedIntegrationHttpClient.getAllKbObjects("pipe-1")).thenReturn(List.of(dto));

        List<com.biopatternsg.domain.model.KbObject> result = adapter.getAllKbObjects("pipe-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("TP53", result.get(0).name());
        assertEquals(List.of("P53"), result.get(0).synonyms());
        assertEquals(List.of("gene"), result.get(0).biotypes());
        assertEquals(List.of("PROTEIN"), result.get(0).roles());
    }

    @Test
    @DisplayName("getAllKbObjects handles empty or error gracefully")
    void testGetAllKbObjects_EmptyAndError() {
        assertTrue(adapter.getAllKbObjects(null).isEmpty());
        assertTrue(adapter.getAllKbObjects("  ").isEmpty());

        when(pubmedIntegrationHttpClient.getAllKbObjects("pipe-1")).thenReturn(Collections.emptyList());
        assertTrue(adapter.getAllKbObjects("pipe-1").isEmpty());

        when(pubmedIntegrationHttpClient.getAllKbObjects("pipe-err"))
                .thenThrow(new RuntimeException("Error"));
        assertTrue(adapter.getAllKbObjects("pipe-err").isEmpty());
    }

    @Test
    @DisplayName("updateKbObjectRoles delegates to client")
    void testUpdateKbObjectRoles() {
        Map<String, List<String>> roles = Map.of("TP53", List.of("PROTEIN"));
        adapter.updateKbObjectRoles("pipe-1", roles);

        verify(pubmedIntegrationHttpClient).updateKbObjectRoles("pipe-1", roles);
    }

    @Test
    @DisplayName("resetKbObjectRoles delegates to client")
    void testResetKbObjectRoles() {
        adapter.resetKbObjectRoles("pipe-1");

        verify(pubmedIntegrationHttpClient).resetKbObjectRoles("pipe-1");
    }
}
