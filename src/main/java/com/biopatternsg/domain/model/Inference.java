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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inference {
    private String id;
    private String pipelineId;
    private String restrictionLevel;
    private Boolean summarizeKb;
    private Integer maxDepth;
    private Map<String, List<String>> roles;
    private List<String> closingObjects;
    private List<String> startFirstRoles;
    private List<String> startSecondRoles;
    private List<String> intermediateFirstRoles;
    private List<String> intermediateSecondRoles;
    private List<String> endFirstRoles;
    private List<String> endSecondRoles;

    public Map<String, List<String>> getRoles() {
        return roles != null ? Collections.unmodifiableMap(roles) : null;
    }

    public void setRoles(Map<String, List<String>> roles) {
        this.roles = roles != null ? new HashMap<>(roles) : null;
    }

    public List<String> getClosingObjects() {
        return closingObjects != null ? Collections.unmodifiableList(closingObjects) : null;
    }

    public void setClosingObjects(List<String> closingObjects) {
        this.closingObjects = closingObjects != null ? new java.util.ArrayList<>(closingObjects) : null;
    }

    public List<String> getStartFirstRoles() {
        return startFirstRoles != null ? Collections.unmodifiableList(startFirstRoles) : null;
    }

    public void setStartFirstRoles(List<String> startFirstRoles) {
        this.startFirstRoles = startFirstRoles != null ? new java.util.ArrayList<>(startFirstRoles) : null;
    }

    public List<String> getStartSecondRoles() {
        return startSecondRoles != null ? Collections.unmodifiableList(startSecondRoles) : null;
    }

    public void setStartSecondRoles(List<String> startSecondRoles) {
        this.startSecondRoles = startSecondRoles != null ? new java.util.ArrayList<>(startSecondRoles) : null;
    }

    public List<String> getIntermediateFirstRoles() {
        return intermediateFirstRoles != null ? Collections.unmodifiableList(intermediateFirstRoles) : null;
    }

    public void setIntermediateFirstRoles(List<String> intermediateFirstRoles) {
        this.intermediateFirstRoles = intermediateFirstRoles != null ? new java.util.ArrayList<>(intermediateFirstRoles) : null;
    }

    public List<String> getIntermediateSecondRoles() {
        return intermediateSecondRoles != null ? Collections.unmodifiableList(intermediateSecondRoles) : null;
    }

    public void setIntermediateSecondRoles(List<String> intermediateSecondRoles) {
        this.intermediateSecondRoles = intermediateSecondRoles != null ? new java.util.ArrayList<>(intermediateSecondRoles) : null;
    }

    public List<String> getEndFirstRoles() {
        return endFirstRoles != null ? Collections.unmodifiableList(endFirstRoles) : null;
    }

    public void setEndFirstRoles(List<String> endFirstRoles) {
        this.endFirstRoles = endFirstRoles != null ? new java.util.ArrayList<>(endFirstRoles) : null;
    }

    public List<String> getEndSecondRoles() {
        return endSecondRoles != null ? Collections.unmodifiableList(endSecondRoles) : null;
    }

    public void setEndSecondRoles(List<String> endSecondRoles) {
        this.endSecondRoles = endSecondRoles != null ? new java.util.ArrayList<>(endSecondRoles) : null;
    }
}
