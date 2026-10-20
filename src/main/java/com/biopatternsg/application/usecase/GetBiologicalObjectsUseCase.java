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

import com.biopatternsg.domain.model.BiologicalObject;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.port.in.FindRoles;
import com.biopatternsg.domain.port.in.GetBiologicalObjects;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
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
public class GetBiologicalObjectsUseCase implements GetBiologicalObjects {

    private final InferenceRepository inferenceRepository;
    private final PubmedIntegrationRepository pubmedIntegrationRepository;
    private final FindRoles findRoles;

    @Override
    public List<BiologicalObject> execute(String pipelineId) {
        log.info("Fetching biological objects with unified roles for pipelineId: {}", pipelineId);
        if (pipelineId == null || pipelineId.isBlank()) {
            return Collections.emptyList();
        }

        Optional<Inference> inferenceOpt = inferenceRepository.findByPipelineId(pipelineId);
        if (inferenceOpt.isEmpty()) {
            log.warn("Inference configuration not found for pipelineId: {}", pipelineId);
            return Collections.emptyList();
        }

        Inference inference = inferenceOpt.get();
        Map<String, List<String>> meshRolesMap = inference.getRoles() != null
                ? inference.getRoles()
                : Collections.emptyMap();

        if (meshRolesMap.isEmpty() && inference.getRestrictionLevel() != null) {
            log.info("No roles found in inference for pipelineId: {}, triggering FindRoles to self-heal", pipelineId);
            try {
                findRoles.execute(pipelineId, null, "system");
                inferenceOpt = inferenceRepository.findByPipelineId(pipelineId);
                if (inferenceOpt.isPresent()) {
                    inference = inferenceOpt.get();
                    meshRolesMap = inference.getRoles() != null ? inference.getRoles() : Collections.emptyMap();
                }
            } catch (Exception e) {
                log.warn("Failed to auto-evaluate roles during GetBiologicalObjects for pipelineId: {}", pipelineId, e);
            }
        }

        List<KbObject> kbObjects = pubmedIntegrationRepository.getAllKbObjects(pipelineId);
        Map<String, KbObject> kbObjectMap = new LinkedHashMap<>();
        if (kbObjects != null) {
            for (KbObject kbObj : kbObjects) {
                if (kbObj == null || kbObj.name() == null || kbObj.name().isBlank()) {
                    continue;
                }
                kbObjectMap.putIfAbsent(kbObj.name().toUpperCase(), kbObj);
                if (kbObj.synonyms() != null) {
                    for (String syn : kbObj.synonyms()) {
                        if (syn != null && !syn.isBlank()) {
                            kbObjectMap.putIfAbsent(syn.toUpperCase(), kbObj);
                        }
                    }
                }
            }
        }

        List<BiologicalObject> result = new ArrayList<>();

        if (!meshRolesMap.isEmpty()) {
            // Strictly honor the evaluated universe for the selected restriction level
            for (Map.Entry<String, List<String>> entry : meshRolesMap.entrySet()) {
                String symbol = entry.getKey();
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                List<String> meshRoles = entry.getValue() != null ? entry.getValue() : Collections.emptyList();

                KbObject kbObj = kbObjectMap.get(symbol.toUpperCase());
                List<String> synonyms = kbObj != null && kbObj.synonyms() != null
                        ? kbObj.synonyms()
                        : Collections.emptyList();
                List<String> rawBiotypes = kbObj != null && kbObj.biotypes() != null
                        ? kbObj.biotypes()
                        : Collections.emptyList();
                List<String> biotypes = rawBiotypes.stream()
                        .filter(b -> b != null && !b.isBlank())
                        .map(b -> b.trim().toUpperCase())
                        .distinct()
                        .toList();

                List<String> unifiedRoles;
                if (kbObj != null && kbObj.roles() != null && !kbObj.roles().isEmpty()) {
                    unifiedRoles = kbObj.roles();
                } else {
                    Set<String> combined = new LinkedHashSet<>(meshRoles);
                    combined.addAll(biotypes);
                    unifiedRoles = new ArrayList<>(combined);
                }

                result.add(new BiologicalObject(
                        symbol,
                        synonyms,
                        biotypes,
                        meshRoles,
                        unifiedRoles
                ));
            }
        } else {
            // Fallback if FindRoles has not run yet: return all KB objects with biotypes only if pipeline is unrestricted
            boolean isRestricted = "VERY_RESTRICTED".equalsIgnoreCase(inference.getRestrictionLevel())
                    || "RESTRICTED".equalsIgnoreCase(inference.getRestrictionLevel());
            if (!isRestricted && kbObjects != null) {
                for (KbObject kbObj : kbObjects) {
                    if (kbObj == null || kbObj.name() == null || kbObj.name().isBlank()) {
                        continue;
                    }
                    List<String> synonyms = kbObj.synonyms() != null ? kbObj.synonyms() : Collections.emptyList();
                    List<String> rawBiotypes = kbObj.biotypes() != null ? kbObj.biotypes() : Collections.emptyList();
                    List<String> biotypes = rawBiotypes.stream()
                            .filter(b -> b != null && !b.isBlank())
                            .map(b -> b.trim().toUpperCase())
                            .distinct()
                            .toList();

                    List<String> unifiedRoles;
                    if (kbObj.roles() != null && !kbObj.roles().isEmpty()) {
                        unifiedRoles = kbObj.roles();
                    } else {
                        unifiedRoles = new ArrayList<>(biotypes);
                    }

                    result.add(new BiologicalObject(
                            kbObj.name(),
                            synonyms,
                            biotypes,
                            Collections.emptyList(),
                            unifiedRoles
                    ));
                }
            }
        }

        log.info("Returning {} biological objects for pipelineId: {}", result.size(), pipelineId);
        return result;
    }
}
