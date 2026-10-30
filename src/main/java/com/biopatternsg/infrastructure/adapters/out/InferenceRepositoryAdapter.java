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

import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.infrastructure.mapper.InferenceMapper;
import com.biopatternsg.infrastructure.mongo.InferenceCollection;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class InferenceRepositoryAdapter implements InferenceRepository, PanacheMongoRepository<InferenceCollection> {

    private final InferenceMapper inferenceMapper;

    @Override
    public Inference save(Inference inference) {
        InferenceCollection existing = find("pipelineId", inference.getPipelineId()).firstResult();
        if (existing != null) {
            if (inference.getRestrictionLevel() != null) {
                existing.setRestrictionLevel(inference.getRestrictionLevel());
            }
            if (inference.getSummarizeKb() != null) {
                existing.setSummarizeKb(inference.getSummarizeKb());
            } else if (existing.getSummarizeKb() == null) {
                existing.setSummarizeKb(Boolean.TRUE);
            }
            if (inference.getMaxDepth() != null && inference.getMaxDepth() > 0) {
                existing.setMaxDepth(inference.getMaxDepth());
            } else if (existing.getMaxDepth() == null || existing.getMaxDepth() <= 0) {
                existing.setMaxDepth(10);
            }
            if (inference.getRoles() != null) {
                existing.setRoles(inference.getRoles());
            }
            if (inference.getClosingObjects() != null) {
                existing.setClosingObjects(inference.getClosingObjects());
            }
            if (inference.getStartFirstRoles() != null && !inference.getStartFirstRoles().isEmpty()) {
                existing.setStartFirstRoles(inference.getStartFirstRoles());
            } else if (existing.getStartFirstRoles() == null || existing.getStartFirstRoles().isEmpty()) {
                existing.setStartFirstRoles(List.of("LIGAND"));
            }
            if (inference.getStartSecondRoles() != null && !inference.getStartSecondRoles().isEmpty()) {
                existing.setStartSecondRoles(inference.getStartSecondRoles());
            } else if (existing.getStartSecondRoles() == null || existing.getStartSecondRoles().isEmpty()) {
                existing.setStartSecondRoles(List.of("PROTEIN"));
            }
            if (inference.getIntermediateFirstRoles() != null && !inference.getIntermediateFirstRoles().isEmpty()) {
                existing.setIntermediateFirstRoles(inference.getIntermediateFirstRoles());
            } else if (existing.getIntermediateFirstRoles() == null || existing.getIntermediateFirstRoles().isEmpty()) {
                existing.setIntermediateFirstRoles(List.of("PROTEIN"));
            }
            if (inference.getIntermediateSecondRoles() != null && !inference.getIntermediateSecondRoles().isEmpty()) {
                existing.setIntermediateSecondRoles(inference.getIntermediateSecondRoles());
            } else if (existing.getIntermediateSecondRoles() == null || existing.getIntermediateSecondRoles().isEmpty()) {
                existing.setIntermediateSecondRoles(List.of("PROTEIN"));
            }
            if (inference.getEndFirstRoles() != null && !inference.getEndFirstRoles().isEmpty()) {
                existing.setEndFirstRoles(inference.getEndFirstRoles());
            } else if (existing.getEndFirstRoles() == null || existing.getEndFirstRoles().isEmpty()) {
                existing.setEndFirstRoles(List.of("PROTEIN"));
            }
            if (inference.getEndSecondRoles() != null && !inference.getEndSecondRoles().isEmpty()) {
                existing.setEndSecondRoles(inference.getEndSecondRoles());
            } else if (existing.getEndSecondRoles() == null || existing.getEndSecondRoles().isEmpty()) {
                existing.setEndSecondRoles(List.of("PROTEIN"));
            }
            update(existing);
            return inferenceMapper.toModel(existing);
        }
        InferenceCollection entity = inferenceMapper.toEntity(inference);
        persistOrUpdate(entity);
        return inferenceMapper.toModel(entity);
    }

    @Override
    public Optional<Inference> findByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Optional.empty();
        }
        InferenceCollection entity = find("pipelineId", pipelineId).firstResult();
        return Optional.ofNullable(entity).map(inferenceMapper::toModel);
    }

    @Override
    public Optional<Inference> findById(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        InferenceCollection entity = findById(new ObjectId(id));
        return Optional.ofNullable(entity).map(inferenceMapper::toModel);
    }

    @Override
    public List<Inference> findAllInferences() {
        return listAll().stream()
                .map(inferenceMapper::toModel)
                .toList();
    }
}
