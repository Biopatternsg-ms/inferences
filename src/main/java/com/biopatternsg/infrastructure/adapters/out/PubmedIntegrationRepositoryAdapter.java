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
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import com.biopatternsg.infrastructure.clients.PubmedIntegrationHttpClient;
import com.biopatternsg.infrastructure.dtos.KbEventDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Collections;
import java.util.List;

@Slf4j
@ApplicationScoped
public class PubmedIntegrationRepositoryAdapter implements PubmedIntegrationRepository {

    private final PubmedIntegrationHttpClient pubmedIntegrationHttpClient;

    @Inject
    public PubmedIntegrationRepositoryAdapter(@RestClient PubmedIntegrationHttpClient pubmedIntegrationHttpClient) {
        this.pubmedIntegrationHttpClient = pubmedIntegrationHttpClient;
    }

    @Override
    public List<KbEvent> getEventsByTerm(String pipelineId, String term) {
        if (pipelineId == null || pipelineId.isBlank() || term == null || term.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<KbEventDTO> dtos = pubmedIntegrationHttpClient.getKbEventsByTerm(pipelineId, term);
            if (dtos == null || dtos.isEmpty()) {
                log.debug("No events found in pubmed-integration for pipelineId: {} and term: {}", pipelineId, term);
                return Collections.emptyList();
            }

            return dtos.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(dto -> new KbEvent(
                            dto.pipelineId(),
                            dto.first(),
                            dto.relation(),
                            dto.second(),
                            dto.pubmedIds()
                    ))
                    .toList();

        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                log.debug("Events not found (404) in pubmed-integration for pipelineId: {} and term: {}", pipelineId, term);
            } else {
                log.warn("Error calling pubmed-integration for pipelineId: {} and term: {}: {}", pipelineId, term, e.getMessage());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Unexpected error fetching events from pubmed-integration for pipelineId: {} and term: {}: {}",
                    pipelineId, term, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<KbEvent> getEventsByPipeline(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<KbEventDTO> dtos = pubmedIntegrationHttpClient.getKbEventsByPipeline(pipelineId);
            if (dtos == null || dtos.isEmpty()) {
                log.debug("No events found in pubmed-integration for pipelineId: {}", pipelineId);
                return Collections.emptyList();
            }

            return dtos.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(dto -> new KbEvent(
                            dto.pipelineId(),
                            dto.first(),
                            dto.relation(),
                            dto.second(),
                            dto.pubmedIds()
                    ))
                    .toList();

        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                log.debug("Events not found (404) in pubmed-integration for pipelineId: {}", pipelineId);
            } else {
                log.warn("Error calling pubmed-integration for pipelineId: {}: {}", pipelineId, e.getMessage());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Unexpected error fetching events from pubmed-integration for pipelineId: {}: {}",
                    pipelineId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<com.biopatternsg.domain.model.KbObject> getAllKbObjects(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<com.biopatternsg.infrastructure.dtos.KbObjectDTO> dtos = pubmedIntegrationHttpClient.getAllKbObjects(pipelineId);
            if (dtos == null || dtos.isEmpty()) {
                log.debug("No kb_objects found in pubmed-integration for pipelineId: {}", pipelineId);
                return Collections.emptyList();
            }

            return dtos.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(dto -> new com.biopatternsg.domain.model.KbObject(
                            dto.name(),
                            dto.synonyms() != null ? dto.synonyms() : Collections.emptyList(),
                            dto.biotypes() != null ? dto.biotypes() : Collections.emptyList(),
                            dto.roles() != null ? dto.roles() : Collections.emptyList()
                    ))
                    .toList();

        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                log.debug("KB objects not found (404) in pubmed-integration for pipelineId: {}", pipelineId);
            } else {
                log.warn("Error calling pubmed-integration getAllKbObjects for pipelineId: {}: {}", pipelineId, e.getMessage());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Unexpected error fetching kb_objects from pubmed-integration for pipelineId: {}: {}",
                    pipelineId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void updateKbObjectRoles(String pipelineId, java.util.Map<String, List<String>> roles) {
        if (pipelineId == null || pipelineId.isBlank() || roles == null || roles.isEmpty()) {
            return;
        }

        try {
            pubmedIntegrationHttpClient.updateKbObjectRoles(pipelineId, roles);
            log.info("Updated roles in pubmed-integration for pipelineId: {}, count: {}", pipelineId, roles.size());
        } catch (Exception e) {
            log.error("Error updating roles in pubmed-integration for pipelineId: {}: {}", pipelineId, e.getMessage(), e);
            throw new RuntimeException("Failed to update roles in pubmed-integration", e);
        }
    }
}
