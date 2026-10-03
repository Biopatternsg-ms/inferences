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
import com.biopatternsg.domain.port.in.FindRoles;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.OntologiesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class FindRolesUseCase implements FindRoles {

    private static final String STEP_FIND_ROLES = "FIND_ROLES";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String VERY_RESTRICTED = "VERY_RESTRICTED";

    private final InferenceRepository inferenceRepository;
    private final OntologiesRepository ontologiesRepository;
    private final ConfigAndControlRepository configAndControlRepository;

    @Override
    public void execute(String pipelineId, List<String> alignedObjects, String userId) {
        log.info("Starting FIND_ROLES step execution for pipelineId: {}", pipelineId);

        try {
            Optional<Inference> inferenceOpt = inferenceRepository.findByPipelineId(pipelineId);
            if (inferenceOpt.isEmpty()) {
                log.error("Inference configuration not found for pipelineId: {}", pipelineId);
                configAndControlRepository.updateStep(
                        pipelineId,
                        STEP_FIND_ROLES,
                        STATUS_FAILED,
                        userId,
                        Map.of("error", "Inference configuration not found for pipeline " + pipelineId)
                );
                return;
            }

            Inference inference = inferenceOpt.get();
            String restrictionLevel = inference.getRestrictionLevel();
            log.info("Pipeline {} restrictionLevel is: {}", pipelineId, restrictionLevel);

            if (VERY_RESTRICTED.equalsIgnoreCase(restrictionLevel)) {
                processVeryRestricted(inference, alignedObjects, userId);
            } else {
                processOtherRestrictionLevels(inference, userId);
            }

        } catch (Exception e) {
            log.error("Error executing FindRolesUseCase for pipeline {}: {}", pipelineId, e.getMessage(), e);
            configAndControlRepository.updateStep(
                    pipelineId,
                    STEP_FIND_ROLES,
                    STATUS_FAILED,
                    userId,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unexpected error during role discovery")
            );
        }
    }

    private void processVeryRestricted(Inference inference, List<String> alignedObjects, String userId) {
        String pipelineId = inference.getPipelineId();
        Map<String, List<String>> roles = new LinkedHashMap<>();

        int totalObjects = (alignedObjects != null) ? alignedObjects.size() : 0;
        int foundMeshIds = 0;
        int entitiesWithRoles = 0;

        List<String> distinctSymbols = (alignedObjects != null)
                ? alignedObjects.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList()
                : Collections.emptyList();

        for (String symbol : distinctSymbols) {
            Optional<String> meshIdOpt = ontologiesRepository.searchMeshId(List.of(symbol));
            if (meshIdOpt.isEmpty()) {
                log.debug("Symbol '{}' has no matching MeSH ID, skipping role check", symbol);
                continue;
            }

            String meshId = meshIdOpt.get();
            foundMeshIds++;
            log.debug("Symbol '{}' matched MeSH ID '{}', querying check-all-types", symbol, meshId);

            Map<String, Boolean> categories = ontologiesRepository.checkAllTypes(meshId);
            if (categories != null && !categories.isEmpty()) {
                List<String> activeRoles = categories.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
                roles.put(symbol, activeRoles);
                if (!activeRoles.isEmpty()) {
                    entitiesWithRoles++;
                }
            }
        }

        inference.setRoles(roles);
        inferenceRepository.save(inference);
        log.info("Pipeline {} roles evaluated. Found {} MeSH IDs, recorded {} entities with categories",
                pipelineId, foundMeshIds, roles.size());

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("restrictionLevel", inference.getRestrictionLevel());
        metrics.put("totalAlignedObjects", String.valueOf(totalObjects));
        metrics.put("meshIdsFound", String.valueOf(foundMeshIds));
        metrics.put("rolesIdentified", String.valueOf(roles.size()));
        metrics.put("entitiesWithActiveRoles", String.valueOf(entitiesWithRoles));
        metrics.put("statusMessage", "Biological roles identified successfully");

        configAndControlRepository.updateStep(
                pipelineId,
                STEP_FIND_ROLES,
                STATUS_COMPLETED,
                userId,
                metrics
        );
    }

    private void processOtherRestrictionLevels(Inference inference, String userId) {
        String pipelineId = inference.getPipelineId();
        String restrictionLevel = inference.getRestrictionLevel() != null ? inference.getRestrictionLevel() : "UNKNOWN";

        log.info("Pipeline {} restrictionLevel is '{}'. Role evaluation skipped for this iteration.",
                pipelineId, restrictionLevel);

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("restrictionLevel", restrictionLevel);
        metrics.put("statusMessage", "Role evaluation deferred for restriction level " + restrictionLevel);

        configAndControlRepository.updateStep(
                pipelineId,
                STEP_FIND_ROLES,
                STATUS_COMPLETED,
                userId,
                metrics
        );
    }
}
