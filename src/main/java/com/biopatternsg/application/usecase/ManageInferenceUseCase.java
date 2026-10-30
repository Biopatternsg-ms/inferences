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
import com.biopatternsg.domain.port.in.InferenceUseCase;
import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
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
public class ManageInferenceUseCase implements InferenceUseCase {

    private final InferenceRepository inferenceRepository;
    private final ConfigAndControlRepository configAndControlRepository;

    @Override
    public Inference createInference(Inference inference, String userId) {
        boolean summarizeKb = inference.getSummarizeKb() == null || inference.getSummarizeKb();
        int maxDepth = inference.getMaxDepth() != null && inference.getMaxDepth() > 0 ? inference.getMaxDepth() : 10;
        List<String> startFirstRoles = inference.getStartFirstRoles() != null && !inference.getStartFirstRoles().isEmpty()
                ? inference.getStartFirstRoles()
                : List.of("LIGAND");
        List<String> startSecondRoles = inference.getStartSecondRoles() != null && !inference.getStartSecondRoles().isEmpty()
                ? inference.getStartSecondRoles()
                : List.of("PROTEIN");
        List<String> intermediateFirstRoles = inference.getIntermediateFirstRoles() != null && !inference.getIntermediateFirstRoles().isEmpty()
                ? inference.getIntermediateFirstRoles()
                : List.of("PROTEIN");
        List<String> intermediateSecondRoles = inference.getIntermediateSecondRoles() != null && !inference.getIntermediateSecondRoles().isEmpty()
                ? inference.getIntermediateSecondRoles()
                : List.of("PROTEIN");
        List<String> endFirstRoles = inference.getEndFirstRoles() != null && !inference.getEndFirstRoles().isEmpty()
                ? inference.getEndFirstRoles()
                : List.of("PROTEIN");
        List<String> endSecondRoles = inference.getEndSecondRoles() != null && !inference.getEndSecondRoles().isEmpty()
                ? inference.getEndSecondRoles()
                : List.of("PROTEIN");

        inference.setSummarizeKb(summarizeKb);
        inference.setMaxDepth(maxDepth);
        inference.setStartFirstRoles(startFirstRoles);
        inference.setStartSecondRoles(startSecondRoles);
        inference.setIntermediateFirstRoles(intermediateFirstRoles);
        inference.setIntermediateSecondRoles(intermediateSecondRoles);
        inference.setEndFirstRoles(endFirstRoles);
        inference.setEndSecondRoles(endSecondRoles);

        log.info("Saving inference configuration for pipeline: {} with restrictionLevel: {}, summarizeKb: {}, maxDepth: {}, startRoles=[{}, {}], intermediateRoles=[{}, {}], endRoles=[{}, {}]",
                inference.getPipelineId(), inference.getRestrictionLevel(), summarizeKb, maxDepth,
                startFirstRoles, startSecondRoles, intermediateFirstRoles, intermediateSecondRoles, endFirstRoles, endSecondRoles);
        Inference saved = inferenceRepository.save(inference);

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("Restriction Level", inference.getRestrictionLevel());
        metrics.put("Summarize KB", String.valueOf(summarizeKb));
        metrics.put("Max Depth", String.valueOf(maxDepth));
        metrics.put("Start First Roles", String.join(", ", startFirstRoles));
        metrics.put("Start Second Roles", String.join(", ", startSecondRoles));
        metrics.put("Intermediate First Roles", String.join(", ", intermediateFirstRoles));
        metrics.put("Intermediate Second Roles", String.join(", ", intermediateSecondRoles));
        metrics.put("End First Roles", String.join(", ", endFirstRoles));
        metrics.put("End Second Roles", String.join(", ", endSecondRoles));

        configAndControlRepository.updateStep(
                inference.getPipelineId(),
                "CONFIGURE_INFERENCES",
                "COMPLETED",
                userId,
                metrics
        );
        return saved;
    }

    @Override
    public Optional<Inference> getByPipelineId(String pipelineId) {
        log.info("Fetching inference configuration for pipelineId: {}", pipelineId);
        return inferenceRepository.findByPipelineId(pipelineId);
    }

    @Override
    public Optional<Inference> getInferenceById(String id) {
        log.info("Fetching inference by id: {}", id);
        return inferenceRepository.findById(id);
    }

    @Override
    public List<Inference> getAllInferences() {
        log.info("Fetching all inferences");
        return inferenceRepository.findAllInferences();
    }
}
