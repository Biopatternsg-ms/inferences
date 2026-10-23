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
package com.biopatternsg.domain.port.in;

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.DynamicExecutionResult;
import com.biopatternsg.domain.model.DynamicInferenceInput;
import com.biopatternsg.domain.model.PathwayGraph;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InferPathways {
    List<BiologicalPathway> execute(String pathwaysObjectsPath, String kBasePath, String rulesPath);
    Map<String, Object> generateFromFiles();
    DynamicExecutionResult runDynamicInference(DynamicInferenceInput input);
    DynamicExecutionResult runPipelineInference(
            String pipelineId,
            String user,
            List<String> restrictedObjects,
            List<String> closingObjects,
            boolean summarizeKb,
            Integer maxDepth);
    Optional<PathwayGraph> getPathwayGraph(String pipelineId);
    List<BiologicalPathway> getPathways(String pipelineId, String startObject, String endObject, String containsObject, int page, int size);
    long countPathways(String pipelineId);
    long deletePathways(String pipelineId);
}
