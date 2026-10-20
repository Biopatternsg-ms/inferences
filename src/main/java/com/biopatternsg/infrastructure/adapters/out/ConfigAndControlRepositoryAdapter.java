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

import com.biopatternsg.domain.port.out.repositories.ConfigAndControlRepository;
import com.biopatternsg.infrastructure.clients.ConfigAndControlHttpClient;
import com.biopatternsg.infrastructure.dtos.PipelineStepRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@ApplicationScoped
public class ConfigAndControlRepositoryAdapter implements ConfigAndControlRepository {

    private final ConfigAndControlHttpClient configAndControlHttpClient;

    @Inject
    public ConfigAndControlRepositoryAdapter(@RestClient ConfigAndControlHttpClient configAndControlHttpClient) {
        this.configAndControlHttpClient = configAndControlHttpClient;
    }

    @Override
    public void updateStep(String pipelineId, String step, String status, String userId, Map<String, String> metrics) {
        configAndControlHttpClient.updateStep(new PipelineStepRequest(pipelineId, step, status, metrics), userId);
    }
}
