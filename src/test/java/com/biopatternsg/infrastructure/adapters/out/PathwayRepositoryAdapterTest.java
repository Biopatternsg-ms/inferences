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

import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.infrastructure.mapper.PathwayMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PathwayRepositoryAdapterTest {

    @Mock
    private PathwayMapper pathwayMapper;

    @InjectMocks
    private PathwayRepositoryAdapter adapter;

    @Test
    @DisplayName("savePathwaysBatch should handle null or empty list gracefully")
    void shouldHandleEmptyBatch() {
        assertDoesNotThrow(() -> adapter.savePathwaysBatch(null));
        assertDoesNotThrow(() -> adapter.savePathwaysBatch(Collections.emptyList()));
    }

    @Test
    @DisplayName("saveGroupedPathwaysBatch should handle null or empty list gracefully")
    void shouldHandleEmptyGroupedBatch() {
        assertDoesNotThrow(() -> adapter.saveGroupedPathwaysBatch(null));
        assertDoesNotThrow(() -> adapter.saveGroupedPathwaysBatch(Collections.emptyList()));
    }

    @Test
    @DisplayName("findGroupedByPipelineId and countGroupedByPipelineId should handle null or blank pipelineId gracefully")
    void shouldHandleBlankPipelineIdInGroupedQueries() {
        assertTrue(adapter.findGroupedByPipelineId(null, null, null, null, null, null, null, 0, 10).isEmpty());
        assertTrue(adapter.findGroupedByPipelineId("  ", null, null, null, null, null, null, 0, 10).isEmpty());
        assertEquals(0L, adapter.countGroupedByPipelineId(null, null, null, null, null, null, null));
        assertEquals(0L, adapter.countGroupedByPipelineId("  ", null, null, null, null, null, null));
        assertTrue(adapter.findDistinctGroupedLengthsByPipelineId(null).isEmpty());
        assertTrue(adapter.findDistinctGroupedLengthsByPipelineId(" ").isEmpty());
        assertFalse(adapter.updateGroupedPathwayFavorite(null, "grp1", true));
        assertFalse(adapter.updateGroupedPathwayFavorite("pipe1", null, true));
        assertFalse(adapter.updateGroupedPathwayFavorite("   ", "grp1", true));
        assertFalse(adapter.updateGroupedPathwayFavorite("pipe1", "   ", true));
    }

    @Test
    @DisplayName("savePathwayGraph should handle null graph gracefully")
    void shouldHandleNullGraph() {
        assertDoesNotThrow(() -> adapter.savePathwayGraph(null));
    }

    @Test
    @DisplayName("findGraphByPipelineId should return empty Optional for null or blank pipelineId")
    void shouldReturnEmptyForBlankPipelineId() {
        Optional<PathwayGraph> resultNull = adapter.findGraphByPipelineId(null);
        Optional<PathwayGraph> resultBlank = adapter.findGraphByPipelineId("  ");

        assertTrue(resultNull.isEmpty());
        assertTrue(resultBlank.isEmpty());
    }
}
