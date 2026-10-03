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
            existing.setRestrictionLevel(inference.getRestrictionLevel());
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
