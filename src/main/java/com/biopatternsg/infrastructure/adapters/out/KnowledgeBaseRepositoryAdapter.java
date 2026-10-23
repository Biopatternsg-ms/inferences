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

import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.port.out.repositories.KnowledgeBaseRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import com.biopatternsg.infrastructure.mapper.PathwayMapper;
import com.biopatternsg.infrastructure.mongo.KbEventCollection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {

    private final PathwayMapper pathwayMapper;
    private final PubmedIntegrationRepository pubmedIntegrationRepository;

    @Override
    public List<PathwayEvent> findEventsByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }

        // Primary source: pubmed-integration REST API
        if (pubmedIntegrationRepository != null) {
            log.info("Fetching kb_events from pubmed-integration for pipelineId: {}", pipelineId);
            try {
                var events = pubmedIntegrationRepository.getEventsByPipeline(pipelineId);
                if (events != null && !events.isEmpty()) {
                    log.info("Retrieved {} kb_events from pubmed-integration for pipelineId: {}", events.size(), pipelineId);
                    return events.stream()
                            .map(e -> PathwayEvent.builder()
                                    .first(e.first())
                                    .relation(e.relation())
                                    .second(e.second())
                                    .build())
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Could not retrieve events from pubmed-integration for pipelineId {}: {}", pipelineId, e.getMessage());
            }
        }

        // Fallback to local DB if available
        List<KbEventCollection> entities = KbEventCollection.list("pipelineId", pipelineId);
        if (!entities.isEmpty()) {
            log.info("Found {} kb_events in local MongoDB for pipelineId: {}", entities.size(), pipelineId);
            return entities.stream()
                    .map(pathwayMapper::toEventModel)
                    .toList();
        }

        return List.of();
    }

    @Override
    public List<PathwayEvent> findEventsByPipelineIdAndRestriction(String pipelineId, String restrictionLevel, List<String> alignedObjects) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }

        // 1. Primary source: pubmed-integration REST API by restriction level
        if (pubmedIntegrationRepository != null) {
            log.info("Fetching kb_events from pubmed-integration for pipelineId: {} with restrictionLevel: {}",
                    pipelineId, restrictionLevel);
            try {
                var events = pubmedIntegrationRepository.getEventsByRestriction(pipelineId, restrictionLevel, alignedObjects);
                if (events != null && !events.isEmpty()) {
                    log.info("Retrieved {} restricted kb_events from pubmed-integration for pipelineId: {} with restrictionLevel: {}",
                            events.size(), pipelineId, restrictionLevel);
                    return events.stream()
                            .map(e -> PathwayEvent.builder()
                                    .first(e.first())
                                    .relation(e.relation())
                                    .second(e.second())
                                    .build())
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Could not retrieve restricted events from pubmed-integration for pipelineId {}: {}", pipelineId, e.getMessage());
            }
        }

        // 2. Fallback to local DB if available
        List<KbEventCollection> entities = KbEventCollection.list("pipelineId", pipelineId);
        if (!entities.isEmpty()) {
            log.info("Applying local restriction fallback on {} kb_events for pipelineId: {}", entities.size(), pipelineId);
            return filterLocalEntitiesByRestriction(entities, restrictionLevel, alignedObjects);
        }

        return List.of();
    }

    private List<PathwayEvent> filterLocalEntitiesByRestriction(List<KbEventCollection> entities, String restrictionLevel, List<String> alignedObjects) {
        if (restrictionLevel == null || restrictionLevel.isBlank()
                || "UNRESTRICTED".equalsIgnoreCase(restrictionLevel)
                || "NO_RESTRICTED".equalsIgnoreCase(restrictionLevel)) {
            return entities.stream().map(pathwayMapper::toEventModel).toList();
        }

        if (alignedObjects == null || alignedObjects.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> alignedSet = alignedObjects.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase())
                .collect(Collectors.toSet());

        if (alignedSet.isEmpty()) {
            return Collections.emptyList();
        }

        if ("VERY_RESTRICTED".equalsIgnoreCase(restrictionLevel)) {
            return entities.stream()
                    .filter(e -> e.getFirst() != null && e.getSecond() != null
                            && alignedSet.contains(e.getFirst().trim().toUpperCase())
                            && alignedSet.contains(e.getSecond().trim().toUpperCase()))
                    .map(pathwayMapper::toEventModel)
                    .toList();
        }

        if ("RESTRICTED".equalsIgnoreCase(restrictionLevel)) {
            return entities.stream()
                    .filter(e -> (e.getFirst() != null && alignedSet.contains(e.getFirst().trim().toUpperCase()))
                            || (e.getSecond() != null && alignedSet.contains(e.getSecond().trim().toUpperCase())))
                    .map(pathwayMapper::toEventModel)
                    .toList();
        }

        return entities.stream().map(pathwayMapper::toEventModel).toList();
    }
}
