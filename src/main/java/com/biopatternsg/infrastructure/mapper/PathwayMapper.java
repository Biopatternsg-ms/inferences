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

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.GraphEdge;
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.model.PathwayObject;
import com.biopatternsg.infrastructure.mongo.GraphEdgeDocument;
import com.biopatternsg.infrastructure.mongo.KbEventCollection;
import com.biopatternsg.infrastructure.mongo.KbObjectCollection;
import com.biopatternsg.infrastructure.mongo.PathwayCollection;
import com.biopatternsg.infrastructure.mongo.PathwayEventDocument;
import com.biopatternsg.infrastructure.mongo.PathwayGraphCollection;
import com.biopatternsg.infrastructure.mongo.PathwayObjectDocument;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PathwayMapper {

    public PathwayCollection toEntity(BiologicalPathway model) {
        if (model == null) {
            return null;
        }
        PathwayCollection entity = new PathwayCollection();
        if (model.getId() != null && ObjectId.isValid(model.getId())) {
            entity.id = new ObjectId(model.getId());
        }
        entity.setPipelineId(model.getPipelineId());
        entity.setUser(model.getUser());
        entity.setRawPattern(model.getRawPattern());
        entity.setLength(model.getLength());
        entity.setStartObject(model.getStartObject());
        entity.setEndObject(model.getEndObject());

        if (model.getEvents() != null) {
            entity.setEvents(model.getEvents().stream()
                    .map(this::toEventDocument)
                    .toList());
        }

        if (model.getObjects() != null) {
            entity.setObjects(model.getObjects().stream()
                    .map(this::toObjectDocument)
                    .toList());
        }

        entity.setCreatedAt(Instant.now());
        return entity;
    }

    public BiologicalPathway toModel(PathwayCollection entity) {
        if (entity == null) {
            return null;
        }
        List<PathwayEvent> events = entity.getEvents() != null
                ? entity.getEvents().stream().map(this::toEventModel).toList()
                : new ArrayList<>();

        List<PathwayObject> objects = entity.getObjects() != null
                ? entity.getObjects().stream().map(this::toObjectModel).toList()
                : new ArrayList<>();

        return BiologicalPathway.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .pipelineId(entity.getPipelineId())
                .user(entity.getUser())
                .rawPattern(entity.getRawPattern())
                .length(entity.getLength())
                .startObject(entity.getStartObject())
                .endObject(entity.getEndObject())
                .events(events)
                .objects(objects)
                .build();
    }

    public PathwayEventDocument toEventDocument(PathwayEvent event) {
        if (event == null) {
            return null;
        }
        return PathwayEventDocument.builder()
                .id(event.getId())
                .first(event.getFirst())
                .relation(event.getRelation())
                .second(event.getSecond())
                .position(event.getPosition())
                .build();
    }

    public PathwayEvent toEventModel(PathwayEventDocument doc) {
        if (doc == null) {
            return null;
        }
        return PathwayEvent.builder()
                .id(doc.getId())
                .first(doc.getFirst())
                .relation(doc.getRelation())
                .second(doc.getSecond())
                .position(doc.getPosition())
                .build();
    }

    public PathwayObjectDocument toObjectDocument(PathwayObject obj) {
        if (obj == null) {
            return null;
        }
        return PathwayObjectDocument.builder()
                .name(obj.getName())
                .role(obj.getRole())
                .build();
    }

    public PathwayObject toObjectModel(PathwayObjectDocument doc) {
        if (doc == null) {
            return null;
        }
        return PathwayObject.builder()
                .name(doc.getName())
                .role(doc.getRole())
                .build();
    }

    public PathwayGraphCollection toEntity(PathwayGraph model) {
        if (model == null) {
            return null;
        }
        PathwayGraphCollection entity = new PathwayGraphCollection();
        entity.setPipelineId(model.getPipelineId());
        entity.setUser(model.getUser());
        entity.setTotalPathways(model.getTotalPathways());
        entity.setExecutionTimeMs(model.getExecutionTimeMs());

        if (model.getNodes() != null) {
            entity.setNodes(model.getNodes().stream()
                    .map(this::toObjectDocument)
                    .toList());
        }

        if (model.getEdges() != null) {
            entity.setEdges(model.getEdges().stream()
                    .map(this::toEdgeDocument)
                    .toList());
        }

        entity.setCreatedAt(Instant.now());
        return entity;
    }

    public PathwayGraph toModel(PathwayGraphCollection entity) {
        if (entity == null) {
            return null;
        }
        List<PathwayObject> nodes = entity.getNodes() != null
                ? entity.getNodes().stream().map(this::toObjectModel).toList()
                : new ArrayList<>();

        List<GraphEdge> edges = entity.getEdges() != null
                ? entity.getEdges().stream().map(this::toEdgeModel).toList()
                : new ArrayList<>();

        return PathwayGraph.builder()
                .pipelineId(entity.getPipelineId())
                .user(entity.getUser())
                .totalPathways(entity.getTotalPathways())
                .executionTimeMs(entity.getExecutionTimeMs())
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    public GraphEdgeDocument toEdgeDocument(GraphEdge edge) {
        if (edge == null) {
            return null;
        }
        return GraphEdgeDocument.builder()
                .first(edge.getFirst())
                .relation(edge.getRelation())
                .second(edge.getSecond())
                .pathwayCount(edge.getPathwayCount())
                .build();
    }

    public GraphEdge toEdgeModel(GraphEdgeDocument doc) {
        if (doc == null) {
            return null;
        }
        return GraphEdge.builder()
                .first(doc.getFirst())
                .relation(doc.getRelation())
                .second(doc.getSecond())
                .pathwayCount(doc.getPathwayCount())
                .build();
    }

    public PathwayEvent toEventModel(KbEventCollection entity) {
        if (entity == null) {
            return null;
        }
        return PathwayEvent.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .first(entity.getFirst())
                .relation(entity.getRelation())
                .second(entity.getSecond())
                .position(null)
                .build();
    }

    public KbObject toKbObjectModel(KbObjectCollection entity) {
        if (entity == null) {
            return null;
        }
        return KbObject.builder()
                .pipelineId(entity.getPipelineId())
                .name(entity.getName())
                .synonyms(entity.getSynonyms() != null ? entity.getSynonyms() : new ArrayList<>())
                .biotypes(entity.getBiotypes() != null ? entity.getBiotypes() : new ArrayList<>())
                .roles(entity.getRoles() != null ? entity.getRoles() : new ArrayList<>())
                .build();
    }

    public com.biopatternsg.infrastructure.dtos.PathwayInferenceResponseDTO toResponseDTO(com.biopatternsg.domain.model.DynamicExecutionResult result) {
        if (result == null) {
            return null;
        }
        int nodesCount = result.getGraph() != null && result.getGraph().getNodes() != null
                ? result.getGraph().getNodes().size()
                : 0;
        int edgesCount = result.getGraph() != null && result.getGraph().getEdges() != null
                ? result.getGraph().getEdges().size()
                : 0;

        return com.biopatternsg.infrastructure.dtos.PathwayInferenceResponseDTO.builder()
                .status("SUCCESS")
                .pipelineId(result.getPipelineId())
                .user(result.getUser())
                .generatedPathwaysCount(result.getGeneratedPathwaysCount())
                .uniqueNodesCount(nodesCount)
                .uniqueEdgesCount(edgesCount)
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }
}
