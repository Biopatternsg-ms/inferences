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
import com.biopatternsg.domain.port.in.ResetBiologicalObjectsRoles;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ResetBiologicalObjectsRolesUseCase implements ResetBiologicalObjectsRoles {

    private static final String STEP_UPDATE_BIOLOGICAL_OBJECTS = "UPDATE_BIOLOGICAL_OBJECTS";
    private static final String STATUS_PENDING = "PENDING";

    private final InferenceRepository inferenceRepository;
    private final PubmedIntegrationRepository pubmedIntegrationRepository;
    private final ConfigAndControlRepository configAndControlRepository;

    @Override
    public void execute(String pipelineId, String userId) {
        log.info("Executing ResetBiologicalObjectsRoles for pipelineId: {}, userId: {}", pipelineId, userId);

        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("PipelineId must not be null or blank");
        }

        // 1. Reset roles in kb_objects in pubmed-integration
        pubmedIntegrationRepository.resetKbObjectRoles(pipelineId);

        // 2. Clear roles in inference document
        Optional<Inference> inferenceOpt = inferenceRepository.findByPipelineId(pipelineId);
        if (inferenceOpt.isPresent()) {
            Inference inference = inferenceOpt.get();
            inference.setRoles(null);
            inferenceRepository.save(inference);
        }

        // 3. Reset step in config-and-control
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("statusMessage", "Biological objects roles reset to default biotypes and MeSH criteria");

        configAndControlRepository.updateStep(
                pipelineId,
                STEP_UPDATE_BIOLOGICAL_OBJECTS,
                STATUS_PENDING,
                userId != null && !userId.isBlank() ? userId : "system",
                metrics
        );

        log.info("ResetBiologicalObjectsRoles completed successfully for pipelineId: {}", pipelineId);
    }
}
