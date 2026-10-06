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
}
