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

import com.biopatternsg.domain.model.DynamicExecutionResult;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.port.in.InferPathways;
import com.biopatternsg.domain.port.in.SaveClosingObjects;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SaveClosingObjectsUseCase implements SaveClosingObjects {

    private static final String STEP_SELECT_CLOSING_OBJECTS = "SELECT_CLOSING_OBJECTS";
    private static final String STEP_INFER_PATHWAYS = "INFER_PATHWAYS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_FAILED = "FAILED";

    private final InferenceRepository inferenceRepository;
    private final ConfigAndControlRepository configAndControlRepository;
    private final InferPathways inferPathways;

    @Override
    public DynamicExecutionResult execute(
            String pipelineId,
            List<String> closingObjects,
            Boolean summarizeKb,
            Integer maxDepth,
            String userId
    ) {
        log.info("Executing SaveClosingObjects for pipelineId: {}, closingObjects count: {}, userId: {}",
                pipelineId, closingObjects != null ? closingObjects.size() : 0, userId);

        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("PipelineId must not be null or blank");
        }

        List<String> sanitizedClosing = (closingObjects != null)
                ? closingObjects.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList()
                : List.of();

        // 1. Guardar closingObjects en el documento Inference
        Optional<Inference> inferenceOpt = inferenceRepository.findByPipelineId(pipelineId);
        if (inferenceOpt.isPresent()) {
            Inference inference = inferenceOpt.get();
            inference.setClosingObjects(new ArrayList<>(sanitizedClosing));
            inferenceRepository.save(inference);
        } else {
            log.warn("No inference document found for pipelineId: {} when saving closing objects", pipelineId);
        }

        // 2. Notificar a config-and-control: SELECT_CLOSING_OBJECTS completado
        Map<String, String> closingMetrics = new LinkedHashMap<>();
        closingMetrics.put("closingObjectsCount", String.valueOf(sanitizedClosing.size()));
        closingMetrics.put("statusMessage", sanitizedClosing.isEmpty()
                ? "No closing filter applied (all terminal objects valid)"
                : "Target closing objects confirmed: " + sanitizedClosing.size());

        try {
            configAndControlRepository.updateStep(
                    pipelineId,
                    STEP_SELECT_CLOSING_OBJECTS,
                    STATUS_COMPLETED,
                    userId,
                    closingMetrics
            );
        } catch (Exception e) {
            log.warn("Failed updating step {} in config-and-control for pipeline {}: {}",
                    STEP_SELECT_CLOSING_OBJECTS, pipelineId, e.getMessage());
        }

        // 3. Notificar a config-and-control: INFER_PATHWAYS en progreso
        try {
            configAndControlRepository.updateStep(
                    pipelineId,
                    STEP_INFER_PATHWAYS,
                    STATUS_IN_PROGRESS,
                    userId,
                    Map.of("statusMessage", "Inference execution triggered in SWI-Prolog")
            );
        } catch (Exception e) {
            log.warn("Failed setting step {} to IN_PROGRESS in config-and-control for pipeline {}: {}",
                    STEP_INFER_PATHWAYS, pipelineId, e.getMessage());
        }

        // 4. Ejecutar la inferencia de vías en RAM
        DynamicExecutionResult result;
        try {
            boolean effectiveSummarize = Boolean.TRUE.equals(summarizeKb);
            int effectiveDepth = (maxDepth != null && maxDepth > 0) ? maxDepth : 10;
            result = inferPathways.runPipelineInference(
                    pipelineId,
                    userId,
                    null,
                    sanitizedClosing,
                    effectiveSummarize,
                    effectiveDepth
            );
        } catch (Exception e) {
            log.error("Error executing pathway inference for pipelineId {}: {}", pipelineId, e.getMessage(), e);
            try {
                configAndControlRepository.updateStep(
                        pipelineId,
                        STEP_INFER_PATHWAYS,
                        STATUS_FAILED,
                        userId,
                        Map.of("statusMessage", "Inference failed: " + e.getMessage())
                );
            } catch (Exception ex) {
                log.warn("Failed marking step {} as FAILED in config-and-control: {}", STEP_INFER_PATHWAYS, ex.getMessage());
            }
            throw e;
        }

        // 5. Notificar a config-and-control: INFER_PATHWAYS completado con métricas
        Map<String, String> inferenceMetrics = new LinkedHashMap<>();
        int pathwaysCount = result.getPathways() != null ? result.getPathways().size() : 0;
        int nodesCount = result.getGraph() != null && result.getGraph().getNodes() != null ? result.getGraph().getNodes().size() : 0;
        int edgesCount = result.getGraph() != null && result.getGraph().getEdges() != null ? result.getGraph().getEdges().size() : 0;

        inferenceMetrics.put("pathwaysCount", String.valueOf(pathwaysCount));
        inferenceMetrics.put("graphNodesCount", String.valueOf(nodesCount));
        inferenceMetrics.put("graphEdgesCount", String.valueOf(edgesCount));
        inferenceMetrics.put("executionTimeMs", String.valueOf(result.getExecutionTimeMs()));
        inferenceMetrics.put("statusMessage", "Pathway inference completed successfully");

        try {
            configAndControlRepository.updateStep(
                    pipelineId,
                    STEP_INFER_PATHWAYS,
                    STATUS_COMPLETED,
                    userId,
                    inferenceMetrics
            );
        } catch (Exception e) {
            log.warn("Failed setting step {} to COMPLETED in config-and-control for pipeline {}: {}",
                    STEP_INFER_PATHWAYS, pipelineId, e.getMessage());
        }

        return result;
    }

    @Override
    public List<String> getClosingObjects(String pipelineId) {
        log.info("Fetching closing objects for pipelineId: {}", pipelineId);
        if (pipelineId == null || pipelineId.isBlank()) {
            return Collections.emptyList();
        }
        return inferenceRepository.findByPipelineId(pipelineId)
                .map(Inference::getClosingObjects)
                .orElse(Collections.emptyList());
    }
}
