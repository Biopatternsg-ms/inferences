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
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ManageInferenceUseCase implements InferenceUseCase {

    private final InferenceRepository inferenceRepository;

    @Override
    public Inference createInference(Inference inference) {
        log.info("Processing inference for model: {}", inference.getModelName());
        if (inference.getCreatedAt() == null) {
            inference.setCreatedAt(Instant.now());
        }
        if (inference.getStatus() == null || inference.getStatus().isBlank()) {
            inference.setStatus("COMPLETED");
        }
        return inferenceRepository.save(inference);
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
