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

        List<KbObject> kbObjects = pubmedIntegrationRepository.getAllKbObjects(pipelineId);
        Map<String, BiologicalObject> objectMap = new LinkedHashMap<>();

        // 1. Process all objects from the Knowledge Base (kbObjects)
        if (kbObjects != null) {
            for (KbObject kbObj : kbObjects) {
                if (kbObj == null || kbObj.name() == null || kbObj.name().isBlank()) {
                    continue;
                }
                String name = kbObj.name();
                String upperName = name.toUpperCase();

                List<String> synonyms = kbObj.synonyms() != null ? kbObj.synonyms() : Collections.emptyList();
                List<String> biotypes = kbObj.biotypes() != null ? kbObj.biotypes() : Collections.emptyList();

                // Look up MeSH roles: direct match or via synonyms
                List<String> meshRoles = meshRolesMap.get(upperName);
                if (meshRoles == null) {
                    for (String syn : synonyms) {
                        if (syn != null && meshRolesMap.containsKey(syn.toUpperCase())) {
                            meshRoles = meshRolesMap.get(syn.toUpperCase());
                            break;
                        }
                    }
                }
                if (meshRoles == null) {
                    meshRoles = Collections.emptyList();
                }

                List<String> unifiedRoles;
                if (kbObj.roles() != null && !kbObj.roles().isEmpty()) {
                    unifiedRoles = kbObj.roles();
                } else {
                    Set<String> combined = new LinkedHashSet<>(meshRoles);
                    for (String biotype : biotypes) {
                        if (biotype != null && !biotype.isBlank()) {
                            combined.add(biotype.trim().toUpperCase());
                        }
                    }
                    unifiedRoles = new ArrayList<>(combined);
                }

                objectMap.put(upperName, new BiologicalObject(
                        name,
                        synonyms,
                        biotypes,
                        meshRoles,
                        unifiedRoles
                ));
            }
        }

        // 2. Also ensure any symbol from meshRolesMap that was not in kbObjects is included
        for (Map.Entry<String, List<String>> entry : meshRolesMap.entrySet()) {
            String symbol = entry.getKey();
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            String upperSymbol = symbol.toUpperCase();

            boolean alreadyCovered = objectMap.containsKey(upperSymbol) ||
                    objectMap.values().stream().anyMatch(bo ->
                            bo.synonyms() != null && bo.synonyms().stream().anyMatch(s -> s != null && s.equalsIgnoreCase(symbol))
                    );

            if (!alreadyCovered) {
                List<String> meshRoles = entry.getValue() != null ? entry.getValue() : Collections.emptyList();
                objectMap.put(upperSymbol, new BiologicalObject(
                        symbol,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        meshRoles,
                        new ArrayList<>(meshRoles)
                ));
            }
        }

        List<BiologicalObject> result = new ArrayList<>(objectMap.values());
        log.info("Returning {} biological objects for pipelineId: {}", result.size(), pipelineId);
        return result;
    }
}
