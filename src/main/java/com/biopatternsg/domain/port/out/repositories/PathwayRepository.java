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
package com.biopatternsg.domain.port.out.repositories;

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.PathwayGraph;

import java.util.List;
import java.util.Optional;

public interface PathwayRepository {
    void savePathwaysBatch(List<BiologicalPathway> pathways);
    void savePathwayGraph(PathwayGraph graph);
    Optional<PathwayGraph> findGraphByPipelineId(String pipelineId);
    List<BiologicalPathway> findByPipelineId(String pipelineId, String startObject, String endObject, String containsObject, int page, int size);
    long countByPipelineId(String pipelineId);
    long deleteByPipelineId(String pipelineId);
}
