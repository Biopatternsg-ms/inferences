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
import com.biopatternsg.domain.port.in.UpdateBiologicalObjects;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class UpdateBiologicalObjectsUseCase implements UpdateBiologicalObjects {

    private static final String STEP_UPDATE_BIOLOGICAL_OBJECTS = "update_biological_objects";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final InferenceRepository inferenceRepository;
    private final PubmedIntegrationRepository pubmedIntegrationRepository;
    private final ConfigAndControlRepository configAndControlRepository;

    @Override
    public void execute(String pipelineId, Map<String, List<String>> roles, String userId) {
        log.info("Executing UpdateBiologicalObjects for pipelineId: {}, total objects: {}",
                pipelineId, roles != null ? roles.size() : 0);

        if (pipelineId == null || pipelineId.isBlank() || roles == null) {
            throw new IllegalArgumentException("PipelineId and roles map must not be null or blank");
        }

        pubmedIntegrationRepository.updateKbObjectRoles(pipelineId, roles);

        Optional<Inference> inferenceOpt = inferenceRepository.findByPipelineId(pipelineId);
        if (inferenceOpt.isPresent()) {
            Inference inference = inferenceOpt.get();
            inference.setRoles(roles);
            inferenceRepository.save(inference);
        }

        int totalObjects = roles.size();
        long objectsWithRoles = roles.values().stream()
                .filter(r -> r != null && !r.isEmpty())
                .count();
        long totalRolesAssigned = roles.values().stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(List::size)
                .sum();

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("totalBiologicalObjects", String.valueOf(totalObjects));
        metrics.put("objectsWithRoles", String.valueOf(objectsWithRoles));
        metrics.put("totalRolesAssigned", String.valueOf(totalRolesAssigned));
        metrics.put("statusMessage", "Biological objects and roles confirmed by user");

        configAndControlRepository.updateStep(
                pipelineId,
                STEP_UPDATE_BIOLOGICAL_OBJECTS,
                STATUS_COMPLETED,
                userId,
                metrics
        );
        log.info("Step UPDATE_BIOLOGICAL_OBJECTS completed successfully for pipelineId: {}", pipelineId);
    }
}
