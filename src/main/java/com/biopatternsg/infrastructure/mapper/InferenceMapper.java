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

import java.util.List;

@ApplicationScoped
public class InferenceMapper {

    private static final List<String> DEFAULT_START_FIRST_ROLES = List.of("LIGAND");
    private static final List<String> DEFAULT_START_SECOND_ROLES = List.of("PROTEIN");
    private static final List<String> DEFAULT_INTERMEDIATE_FIRST_ROLES = List.of("PROTEIN");
    private static final List<String> DEFAULT_INTERMEDIATE_SECOND_ROLES = List.of("PROTEIN");
    private static final List<String> DEFAULT_END_FIRST_ROLES = List.of("PROTEIN");
    private static final List<String> DEFAULT_END_SECOND_ROLES = List.of("PROTEIN");

    public Inference toModel(InferenceRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return Inference.builder()
                .pipelineId(dto.getPipelineId())
                .restrictionLevel(dto.getRestrictionLevel())
                .summarizeKb(dto.getSummarizeKb() != null ? dto.getSummarizeKb() : Boolean.TRUE)
                .maxDepth(dto.getMaxDepth() != null && dto.getMaxDepth() > 0 ? dto.getMaxDepth() : 10)
                .startFirstRoles(resolveRolesOrDefault(dto.getStartFirstRoles(), DEFAULT_START_FIRST_ROLES))
                .startSecondRoles(resolveRolesOrDefault(dto.getStartSecondRoles(), DEFAULT_START_SECOND_ROLES))
                .intermediateFirstRoles(resolveRolesOrDefault(dto.getIntermediateFirstRoles(), DEFAULT_INTERMEDIATE_FIRST_ROLES))
                .intermediateSecondRoles(resolveRolesOrDefault(dto.getIntermediateSecondRoles(), DEFAULT_INTERMEDIATE_SECOND_ROLES))
                .endFirstRoles(resolveRolesOrDefault(dto.getEndFirstRoles(), DEFAULT_END_FIRST_ROLES))
                .endSecondRoles(resolveRolesOrDefault(dto.getEndSecondRoles(), DEFAULT_END_SECOND_ROLES))
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
                .summarizeKb(model.getSummarizeKb() != null ? model.getSummarizeKb() : Boolean.TRUE)
                .maxDepth(model.getMaxDepth() != null && model.getMaxDepth() > 0 ? model.getMaxDepth() : 10)
                .roles(model.getRoles())
                .closingObjects(model.getClosingObjects())
                .startFirstRoles(resolveRolesOrDefault(model.getStartFirstRoles(), DEFAULT_START_FIRST_ROLES))
                .startSecondRoles(resolveRolesOrDefault(model.getStartSecondRoles(), DEFAULT_START_SECOND_ROLES))
                .intermediateFirstRoles(resolveRolesOrDefault(model.getIntermediateFirstRoles(), DEFAULT_INTERMEDIATE_FIRST_ROLES))
                .intermediateSecondRoles(resolveRolesOrDefault(model.getIntermediateSecondRoles(), DEFAULT_INTERMEDIATE_SECOND_ROLES))
                .endFirstRoles(resolveRolesOrDefault(model.getEndFirstRoles(), DEFAULT_END_FIRST_ROLES))
                .endSecondRoles(resolveRolesOrDefault(model.getEndSecondRoles(), DEFAULT_END_SECOND_ROLES))
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
        entity.setSummarizeKb(model.getSummarizeKb() != null ? model.getSummarizeKb() : Boolean.TRUE);
        entity.setMaxDepth(model.getMaxDepth() != null && model.getMaxDepth() > 0 ? model.getMaxDepth() : 10);
        entity.setRoles(model.getRoles());
        entity.setClosingObjects(model.getClosingObjects());
        entity.setStartFirstRoles(resolveRolesOrDefault(model.getStartFirstRoles(), DEFAULT_START_FIRST_ROLES));
        entity.setStartSecondRoles(resolveRolesOrDefault(model.getStartSecondRoles(), DEFAULT_START_SECOND_ROLES));
        entity.setIntermediateFirstRoles(resolveRolesOrDefault(model.getIntermediateFirstRoles(), DEFAULT_INTERMEDIATE_FIRST_ROLES));
        entity.setIntermediateSecondRoles(resolveRolesOrDefault(model.getIntermediateSecondRoles(), DEFAULT_INTERMEDIATE_SECOND_ROLES));
        entity.setEndFirstRoles(resolveRolesOrDefault(model.getEndFirstRoles(), DEFAULT_END_FIRST_ROLES));
        entity.setEndSecondRoles(resolveRolesOrDefault(model.getEndSecondRoles(), DEFAULT_END_SECOND_ROLES));
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
                .summarizeKb(entity.getSummarizeKb() != null ? entity.getSummarizeKb() : Boolean.TRUE)
                .maxDepth(entity.getMaxDepth() != null && entity.getMaxDepth() > 0 ? entity.getMaxDepth() : 10)
                .roles(entity.getRoles())
                .closingObjects(entity.getClosingObjects())
                .startFirstRoles(resolveRolesOrDefault(entity.getStartFirstRoles(), DEFAULT_START_FIRST_ROLES))
                .startSecondRoles(resolveRolesOrDefault(entity.getStartSecondRoles(), DEFAULT_START_SECOND_ROLES))
                .intermediateFirstRoles(resolveRolesOrDefault(entity.getIntermediateFirstRoles(), DEFAULT_INTERMEDIATE_FIRST_ROLES))
                .intermediateSecondRoles(resolveRolesOrDefault(entity.getIntermediateSecondRoles(), DEFAULT_INTERMEDIATE_SECOND_ROLES))
                .endFirstRoles(resolveRolesOrDefault(entity.getEndFirstRoles(), DEFAULT_END_FIRST_ROLES))
                .endSecondRoles(resolveRolesOrDefault(entity.getEndSecondRoles(), DEFAULT_END_SECOND_ROLES))
                .build();
    }

    private static List<String> resolveRolesOrDefault(List<String> roles, List<String> defaultRoles) {
        if (roles == null || roles.isEmpty()) {
            return defaultRoles;
        }
        List<String> sanitized = roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> r.trim().toUpperCase())
                .distinct()
                .toList();
        return sanitized.isEmpty() ? defaultRoles : sanitized;
    }
}
