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
import com.biopatternsg.domain.model.RoleEvaluationSummary;
import com.biopatternsg.domain.port.in.FindRoles;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.OntologiesRepository;
import com.biopatternsg.domain.port.out.repositories.PubmedIntegrationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class FindRolesUseCase implements FindRoles {

    private static final String STEP_FIND_ROLES = "FIND_ROLES";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String VERY_RESTRICTED = "VERY_RESTRICTED";
    private static final String RESTRICTED = "RESTRICTED";
    private static final String UNRESTRICTED = "UNRESTRICTED";
    private static final String NO_RESTRICTED = "NO_RESTRICTED";

    private final InferenceRepository inferenceRepository;
    private final OntologiesRepository ontologiesRepository;
    private final PubmedIntegrationRepository pubmedIntegrationRepository;
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

            List<String> effectiveAlignedObjects = alignedObjects;
            if ((effectiveAlignedObjects == null || effectiveAlignedObjects.isEmpty())
                    && (VERY_RESTRICTED.equalsIgnoreCase(restrictionLevel) || RESTRICTED.equalsIgnoreCase(restrictionLevel))) {
                effectiveAlignedObjects = pubmedIntegrationRepository.getAlignedObjects(pipelineId);
                log.info("Pipeline {}: Fetched {} aligned objects from pubmed-integration",
                        pipelineId, effectiveAlignedObjects != null ? effectiveAlignedObjects.size() : 0);
            }

            if (VERY_RESTRICTED.equalsIgnoreCase(restrictionLevel)) {
                processVeryRestricted(inference, effectiveAlignedObjects, userId);
            } else if (RESTRICTED.equalsIgnoreCase(restrictionLevel)) {
                processRestricted(inference, effectiveAlignedObjects, userId);
            } else if (UNRESTRICTED.equalsIgnoreCase(restrictionLevel) || NO_RESTRICTED.equalsIgnoreCase(restrictionLevel)) {
                processUnrestricted(inference, userId);
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
        int totalObjects = (alignedObjects != null) ? alignedObjects.size() : 0;

        List<String> distinctSymbols = (alignedObjects != null)
                ? alignedObjects.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList()
                : Collections.emptyList();

        log.info("Pipeline {}: evaluating {} aligned objects (distinct: {}, symbols: {})",
                pipelineId, totalObjects, distinctSymbols.size(), distinctSymbols);

        if (distinctSymbols.isEmpty()) {
            log.warn("Pipeline {}: No valid symbols found to evaluate in alignedObjects", pipelineId);
        }

        RoleEvaluationSummary evalResult = evaluateRolesForSymbols(distinctSymbols);

        inference.setRoles(evalResult.roles());
        inferenceRepository.save(inference);
        log.info("Pipeline {} roles evaluated. Found {} MeSH IDs, recorded {} entities with categories",
                pipelineId, evalResult.foundMeshIds(), evalResult.roles().size());

        Map<String, String> metrics = getStringStringMap(inference, totalObjects, evalResult);

        configAndControlRepository.updateStep(
                pipelineId,
                STEP_FIND_ROLES,
                STATUS_COMPLETED,
                userId,
                metrics
        );
    }

    private static Map<String, String> getStringStringMap(Inference inference, int totalObjects, RoleEvaluationSummary evalResult) {
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("restrictionLevel", inference.getRestrictionLevel());
        metrics.put("totalAlignedObjects", String.valueOf(totalObjects));
        metrics.put("meshIdsFound", String.valueOf(evalResult.foundMeshIds()));
        metrics.put("rolesIdentified", String.valueOf(evalResult.roles().size()));
        metrics.put("entitiesWithActiveRoles", String.valueOf(evalResult.entitiesWithActiveRoles()));
        metrics.put("statusMessage", "Biological roles identified successfully");
        return metrics;
    }

    private void processRestricted(Inference inference, List<String> alignedObjects, String userId) {
        String pipelineId = inference.getPipelineId();
        int totalBaseObjects = (alignedObjects != null) ? alignedObjects.size() : 0;

        List<String> baseSymbols = (alignedObjects != null)
                ? alignedObjects.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList()
                : Collections.emptyList();

        log.info("Pipeline {}: starting RESTRICTED evaluation with {} base objects (distinct: {})",
                pipelineId, totalBaseObjects, baseSymbols.size());

        // Case-insensitive map preserving canonical symbol representation and insertion order
        Map<String, String> canonicalSymbols = new LinkedHashMap<>();
        for (String base : baseSymbols) {
            canonicalSymbols.put(base.toUpperCase(), base);
        }
        Set<String> coOccurringSymbols = new LinkedHashSet<>();

        for (String baseSymbol : baseSymbols) {
            log.info("Pipeline {}: fetching biological events for base symbol '{}'", pipelineId, baseSymbol);
            List<KbEvent> events = pubmedIntegrationRepository.getEventsByTerm(pipelineId, baseSymbol);

            if (events == null || events.isEmpty()) {
                log.info("Pipeline {}: no biological events found for base symbol '{}'", pipelineId, baseSymbol);
                continue;
            }

            log.info("Pipeline {}: found {} events for base symbol '{}'", pipelineId, events.size(), baseSymbol);
            for (KbEvent event : events) {
                String first = event.first() != null ? event.first().trim() : "";
                String second = event.second() != null ? event.second().trim() : "";

                if (!first.isBlank() && !first.equalsIgnoreCase(baseSymbol)) {
                    String upperFirst = first.toUpperCase();
                    if (!canonicalSymbols.containsKey(upperFirst)) {
                        canonicalSymbols.put(upperFirst, first);
                        coOccurringSymbols.add(first);
                    }
                }
                if (!second.isBlank() && !second.equalsIgnoreCase(baseSymbol)) {
                    String upperSecond = second.toUpperCase();
                    if (!canonicalSymbols.containsKey(upperSecond)) {
                        canonicalSymbols.put(upperSecond, second);
                        coOccurringSymbols.add(second);
                    }
                }
            }
        }

        List<String> distinctSymbols = new ArrayList<>(canonicalSymbols.values());
        log.info("Pipeline {}: RESTRICTED evaluation pool prepared: {} base objects, {} co-occurring objects, {} total to evaluate: {}",
                pipelineId, baseSymbols.size(), coOccurringSymbols.size(), distinctSymbols.size(), distinctSymbols);

        RoleEvaluationSummary evalResult = evaluateRolesForSymbols(distinctSymbols);

        inference.setRoles(evalResult.roles());
        inferenceRepository.save(inference);
        log.info("Pipeline {} RESTRICTED roles evaluated. Found {} MeSH IDs, recorded {} entities with categories",
                pipelineId, evalResult.foundMeshIds(), evalResult.roles().size());

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("restrictionLevel", inference.getRestrictionLevel());
        metrics.put("totalAlignedObjects", String.valueOf(totalBaseObjects));
        metrics.put("coOccurringObjectsFound", String.valueOf(coOccurringSymbols.size()));
        metrics.put("totalEvaluatedObjects", String.valueOf(distinctSymbols.size()));
        metrics.put("meshIdsFound", String.valueOf(evalResult.foundMeshIds()));
        metrics.put("rolesIdentified", String.valueOf(evalResult.roles().size()));
        metrics.put("entitiesWithActiveRoles", String.valueOf(evalResult.entitiesWithActiveRoles()));
        metrics.put("statusMessage", "Biological roles identified successfully for RESTRICTED level");

        configAndControlRepository.updateStep(
                pipelineId,
                STEP_FIND_ROLES,
                STATUS_COMPLETED,
                userId,
                metrics
        );
    }

    private RoleEvaluationSummary evaluateRolesForSymbols(List<String> symbols) {
        Map<String, List<String>> roles = new LinkedHashMap<>();
        Map<String, Map<String, Boolean>> localMeshCache = new LinkedHashMap<>();
        int foundMeshIds = 0;
        int entitiesWithRoles = 0;

        for (String symbol : symbols) {
            Optional<String> meshIdOpt = ontologiesRepository.searchMeshId(List.of(symbol));
            if (meshIdOpt.isEmpty()) {
                log.info("Symbol '{}' has no matching MeSH ID in ontologies, skipping role check", symbol);
                roles.put(symbol, Collections.emptyList());
                continue;
            }

            String meshId = meshIdOpt.get();
            foundMeshIds++;
            log.info("Symbol '{}' matched MeSH ID '{}', querying check-all-types", symbol, meshId);

            Map<String, Boolean> categories = localMeshCache.computeIfAbsent(
                    meshId,
                    ontologiesRepository::checkAllTypes
            );
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
                log.info("Symbol '{}' (MeSH: {}) active roles: {}", symbol, meshId, activeRoles);
            } else {
                roles.put(symbol, Collections.emptyList());
                log.info("Symbol '{}' (MeSH: {}) returned no active categories", symbol, meshId);
            }
        }

        return new RoleEvaluationSummary(roles, foundMeshIds, entitiesWithRoles);
    }

    private void processUnrestricted(Inference inference, String userId) {
        String pipelineId = inference.getPipelineId();
        log.info("Pipeline {}: starting UNRESTRICTED evaluation fetching all biological events", pipelineId);

        List<KbEvent> events = pubmedIntegrationRepository.getEventsByPipeline(pipelineId);
        int totalEvents = (events != null) ? events.size() : 0;
        log.info("Pipeline {}: retrieved {} biological events for UNRESTRICTED evaluation", pipelineId, totalEvents);

        Map<String, String> canonicalSymbols = new LinkedHashMap<>();
        if (events != null) {
            for (KbEvent event : events) {
                String first = event.first() != null ? event.first().trim() : "";
                String second = event.second() != null ? event.second().trim() : "";

                if (!first.isBlank()) {
                    canonicalSymbols.putIfAbsent(first.toUpperCase(), first);
                }
                if (!second.isBlank()) {
                    canonicalSymbols.putIfAbsent(second.toUpperCase(), second);
                }
            }
        }

        List<String> distinctSymbols = new ArrayList<>(canonicalSymbols.values());
        log.info("Pipeline {}: UNRESTRICTED evaluation pool prepared: {} total distinct objects across {} events: {}",
                pipelineId, distinctSymbols.size(), totalEvents, distinctSymbols);

        RoleEvaluationSummary evalResult = evaluateRolesForSymbols(distinctSymbols);

        inference.setRoles(evalResult.roles());
        inferenceRepository.save(inference);
        log.info("Pipeline {} UNRESTRICTED roles evaluated. Found {} MeSH IDs, recorded {} entities with categories",
                pipelineId, evalResult.foundMeshIds(), evalResult.roles().size());

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("restrictionLevel", inference.getRestrictionLevel());
        metrics.put("totalEvents", String.valueOf(totalEvents));
        metrics.put("totalEvaluatedObjects", String.valueOf(distinctSymbols.size()));
        metrics.put("meshIdsFound", String.valueOf(evalResult.foundMeshIds()));
        metrics.put("rolesIdentified", String.valueOf(evalResult.roles().size()));
        metrics.put("entitiesWithActiveRoles", String.valueOf(evalResult.entitiesWithActiveRoles()));
        metrics.put("statusMessage", "Biological roles identified successfully for UNRESTRICTED level");

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
