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
package com.biopatternsg.infrastructure.mapper;

import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.infrastructure.dtos.InferenceRequestDTO;
import com.biopatternsg.infrastructure.dtos.InferenceResponseDTO;
import com.biopatternsg.infrastructure.mongo.InferenceCollection;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class InferenceMapper {

    public Inference toModel(InferenceRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return Inference.builder()
                .pipelineId(dto.getPipelineId())
                .restrictionLevel(dto.getRestrictionLevel())
                .build();
    }

    public InferenceResponseDTO toResponseDTO(Inference model) {
        if (model == null) {
            return null;
        }
        return InferenceResponseDTO.builder()
                .id(model.getId())
                .pipelineId(model.getPipelineId())
                .restrictionLevel(model.getRestrictionLevel())
                .build();
    }

    public InferenceCollection toEntity(Inference model) {
        if (model == null) {
            return null;
        }
        InferenceCollection entity = new InferenceCollection();
        if (model.getId() != null && ObjectId.isValid(model.getId())) {
            entity.id = new ObjectId(model.getId());
        }
        entity.setPipelineId(model.getPipelineId());
        entity.setRestrictionLevel(model.getRestrictionLevel());
        return entity;
    }

    public Inference toModel(InferenceCollection entity) {
        if (entity == null) {
            return null;
        }
        return Inference.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .pipelineId(entity.getPipelineId())
                .restrictionLevel(entity.getRestrictionLevel())
                .build();
    }
}
