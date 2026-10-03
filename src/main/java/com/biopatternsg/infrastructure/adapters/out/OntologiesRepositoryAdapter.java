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

import com.biopatternsg.domain.port.out.repositories.OntologiesRepository;
import com.biopatternsg.infrastructure.clients.OntologiesHttpClient;
import com.biopatternsg.infrastructure.dtos.CheckAllMeshTypesRequest;
import com.biopatternsg.infrastructure.dtos.CheckAllMeshTypesResponse;
import com.biopatternsg.infrastructure.dtos.MeshIdResponse;
import com.biopatternsg.infrastructure.dtos.SearchMeshIdRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class OntologiesRepositoryAdapter implements OntologiesRepository {

    private final OntologiesHttpClient ontologiesHttpClient;

    @Inject
    public OntologiesRepositoryAdapter(@RestClient OntologiesHttpClient ontologiesHttpClient) {
        this.ontologiesHttpClient = ontologiesHttpClient;
    }

    @Override
    public Optional<String> searchMeshId(List<String> synonyms) {
        if (synonyms == null || synonyms.isEmpty()) {
            return Optional.empty();
        }

        try (Response response = ontologiesHttpClient.searchMeshId(new SearchMeshIdRequest(synonyms))) {
            if (response != null && response.getStatus() == Response.Status.OK.getStatusCode()) {
                MeshIdResponse meshIdResponse = response.readEntity(MeshIdResponse.class);
                if (meshIdResponse != null && meshIdResponse.meshId() != null && !meshIdResponse.meshId().isBlank()) {
                    return Optional.of(meshIdResponse.meshId());
                }
            }
            return Optional.empty();
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                log.debug("MeSH ID not found for synonyms: {}", synonyms);
                return Optional.empty();
            }
            log.error("WebApplicationException calling searchMeshId for synonyms {}: {}", synonyms, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error calling searchMeshId for synonyms {}: {}", synonyms, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, Boolean> checkAllTypes(String meshId) {
        if (meshId == null || meshId.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            CheckAllMeshTypesResponse response = ontologiesHttpClient.checkAllTypes(new CheckAllMeshTypesRequest(meshId));
            if (response != null && response.categories() != null) {
                return response.categories();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Error calling checkAllTypes for meshId {}: {}", meshId, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
