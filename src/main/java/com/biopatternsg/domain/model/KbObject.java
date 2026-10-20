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
package com.biopatternsg.domain.model;

import lombok.Builder;

import java.util.Collections;
import java.util.List;

@Builder(toBuilder = true)
public record KbObject(
        String pipelineId,
        String name,
        List<String> synonyms,
        List<String> biotypes,
        List<String> roles
) {
    public KbObject(String name, List<String> synonyms, List<String> biotypes, List<String> roles) {
        this(null, name, synonyms, biotypes, roles);
    }

    public KbObject(String name, List<String> synonyms, List<String> biotypes) {
        this(null, name, synonyms, biotypes, Collections.emptyList());
    }

    public String getName() {
        return name();
    }

    public List<String> getSynonyms() {
        return synonyms();
    }

    public List<String> getBiotypes() {
        return biotypes();
    }

    public List<String> getRoles() {
        return roles();
    }

    public String getPipelineId() {
        return pipelineId();
    }
}
