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

import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.port.out.repositories.KnowledgeBaseRepository;
import com.biopatternsg.infrastructure.mapper.PathwayMapper;
import com.biopatternsg.infrastructure.mongo.KbEventCollection;
import com.biopatternsg.infrastructure.mongo.KbObjectCollection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {

    private final PathwayMapper pathwayMapper;

    @Override
    public List<PathwayEvent> findEventsByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }
        List<KbEventCollection> entities = KbEventCollection.list("pipelineId", pipelineId);
        log.info("Found {} kb_events for pipelineId: {}", entities.size(), pipelineId);
        return entities.stream()
                .map(pathwayMapper::toEventModel)
                .toList();
    }

    @Override
    public List<KbObject> findObjectsByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }
        List<KbObjectCollection> entities = KbObjectCollection.list("pipelineId", pipelineId);
        log.info("Found {} kb_objects for pipelineId: {}", entities.size(), pipelineId);
        return entities.stream()
                .map(pathwayMapper::toKbObjectModel)
                .toList();
    }
}
