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
import com.biopatternsg.infrastructure.dtos.GroupedPathwayDTO;
import com.biopatternsg.infrastructure.dtos.GroupedPathwayObjectDTO;
import com.biopatternsg.infrastructure.dtos.GroupedPathwayStepDTO;
import com.biopatternsg.infrastructure.dtos.GroupedPathwayStepEventDTO;
import com.biopatternsg.infrastructure.dtos.GroupedPathwaysResponseDTO;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public com.biopatternsg.infrastructure.dtos.PathwayGraphResponseDTO toGraphResponseDTO(PathwayGraph graph) {
        if (graph == null) {
            return null;
        }
        List<com.biopatternsg.infrastructure.dtos.PathwayGraphNodeDTO> nodeDTOs = graph.getNodes() != null
                ? graph.getNodes().stream().map(this::toNodeDTO).toList()
                : new ArrayList<>();

        List<com.biopatternsg.infrastructure.dtos.PathwayGraphEdgeDTO> edgeDTOs = graph.getEdges() != null
                ? graph.getEdges().stream().map(this::toEdgeDTO).toList()
                : new ArrayList<>();

        return com.biopatternsg.infrastructure.dtos.PathwayGraphResponseDTO.builder()
                .pipelineId(graph.getPipelineId())
                .user(graph.getUser())
                .totalPathways(graph.getTotalPathways())
                .executionTimeMs(graph.getExecutionTimeMs())
                .nodes(nodeDTOs)
                .edges(edgeDTOs)
                .build();
    }

    public com.biopatternsg.infrastructure.dtos.PathwayGraphNodeDTO toNodeDTO(PathwayObject node) {
        if (node == null) {
            return null;
        }
        String rawRole = node.getRole() != null && !node.getRole().isBlank() ? node.getRole() : "protein";
        return com.biopatternsg.infrastructure.dtos.PathwayGraphNodeDTO.builder()
                .name(node.getName())
                .role(rawRole)
                .type(formatRoleDisplayType(rawRole))
                .build();
    }

    public com.biopatternsg.infrastructure.dtos.PathwayGraphEdgeDTO toEdgeDTO(GraphEdge edge) {
        if (edge == null) {
            return null;
        }
        return com.biopatternsg.infrastructure.dtos.PathwayGraphEdgeDTO.builder()
                .first(edge.getFirst())
                .relation(edge.getRelation())
                .second(edge.getSecond())
                .object1(edge.getFirst())
                .interaction(edge.getRelation())
                .object2(edge.getSecond())
                .pathwayCount(edge.getPathwayCount())
                .build();
    }

    /**
     * Groups biological pathways that share the exact same length and the exact same
     * ordered sequence of objects across every transition step, consolidating all
     * relations per step and across the group.
     */
    public List<GroupedPathwayDTO> groupPathways(
            List<BiologicalPathway> pathways,
            Map<String, String> roleOverrides) {
        return groupPathways(pathways, roleOverrides, Collections.emptyMap());
    }

    public List<GroupedPathwayDTO> groupPathways(
            List<BiologicalPathway> pathways,
            Map<String, String> roleOverrides,
            Map<String, List<String>> eventPubmedMap) {
        if (pathways == null || pathways.isEmpty()) {
            return List.of();
        }

        Map<String, GroupAccumulator> groupsMap = new LinkedHashMap<>();

        for (BiologicalPathway pathway : pathways) {
            if (pathway == null) {
                continue;
            }
            List<PathwayEvent> events = extractOrderedEvents(pathway);
            if (events.isEmpty()) {
                continue;
            }

            Map<String, String> pathwayObjectRoles = new LinkedHashMap<>();
            if (pathway.getObjects() != null) {
                for (PathwayObject po : pathway.getObjects()) {
                    if (po != null && po.getName() != null && po.getRole() != null && !po.getRole().isBlank()) {
                        pathwayObjectRoles.put(po.getName(), po.getRole());
                    }
                }
            }

            int length = events.size();
            StringBuilder keyBuilder = new StringBuilder();
            keyBuilder.append(length).append("|");

            List<String> orderedObjectNames = new ArrayList<>(length + 1);
            boolean isContiguousChain = true;

            for (int i = 0; i < length; i++) {
                PathwayEvent ev = events.get(i);
                String first = ev.getFirst() != null ? ev.getFirst() : "";
                String second = ev.getSecond() != null ? ev.getSecond() : "";
                if (i > 0) {
                    keyBuilder.append(";");
                    String prevSecond = events.get(i - 1).getSecond() != null ? events.get(i - 1).getSecond() : "";
                    if (!first.equals(prevSecond)) {
                        isContiguousChain = false;
                    }
                }
                keyBuilder.append(first).append("->").append(second);

                if (i == 0) {
                    orderedObjectNames.add(first);
                } else if (!first.equals(orderedObjectNames.get(orderedObjectNames.size() - 1))) {
                    orderedObjectNames.add(first);
                }
                orderedObjectNames.add(second);
            }

            String groupKey = keyBuilder.toString();
            String groupId = isContiguousChain
                    ? String.join("->", orderedObjectNames)
                    : keyBuilder.substring(keyBuilder.indexOf("|") + 1);

            GroupAccumulator acc = groupsMap.computeIfAbsent(groupKey, k ->
                    new GroupAccumulator(groupId, length, orderedObjectNames, events, eventPubmedMap));

            acc.addPathway(events, pathwayObjectRoles, roleOverrides);
        }

        List<GroupedPathwayDTO> result = new ArrayList<>(groupsMap.size());
        for (GroupAccumulator acc : groupsMap.values()) {
            result.add(acc.toDTO());
        }
        return result;
    }

    private Map<String, List<String>> resolvePubmedIdsForPipeline(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            List<KbEventCollection> kbEvents = KbEventCollection.list("pipelineId", pipelineId);
            if (kbEvents != null) {
                for (KbEventCollection kbe : kbEvents) {
                    List<String> pmids = kbe.getPubmedIds() != null ? kbe.getPubmedIds() : List.of();
                    if (kbe.id != null) {
                        map.put(kbe.id.toHexString(), pmids);
                    }
                    String f = kbe.getFirst() != null ? kbe.getFirst().trim().toLowerCase() : "";
                    String r = kbe.getRelation() != null ? kbe.getRelation().trim().toLowerCase() : "";
                    String s = kbe.getSecond() != null ? kbe.getSecond().trim().toLowerCase() : "";
                    if (!f.isEmpty() && !r.isEmpty() && !s.isEmpty()) {
                        map.putIfAbsent(f + "|" + r + "|" + s, pmids);
                    }
                }
            }
        } catch (Exception ignored) {
            // MongoDB not active or unit test environment
        }
        return map;
    }

    public GroupedPathwaysResponseDTO toGroupedPathwaysResponseDTO(
            String pipelineId,
            List<BiologicalPathway> pathways,
            Map<String, String> roleOverrides,
            int page,
            int size) {
        List<BiologicalPathway> safePathways = pathways != null ? pathways : List.of();
        Map<String, List<String>> eventPubmedMap = resolvePubmedIdsForPipeline(pipelineId);
        List<GroupedPathwayDTO> allGroups = groupPathways(safePathways, roleOverrides, eventPubmedMap);

        int totalGroups = allGroups.size();
        List<GroupedPathwayDTO> pagedGroups;
        if (page >= 0 && size > 0) {
            int fromIndex = page * size;
            if (fromIndex >= totalGroups) {
                pagedGroups = List.of();
            } else {
                int toIndex = Math.min(fromIndex + size, totalGroups);
                pagedGroups = allGroups.subList(fromIndex, toIndex);
            }
        } else {
            pagedGroups = allGroups;
        }

        return GroupedPathwaysResponseDTO.builder()
                .pipelineId(pipelineId)
                .totalRawPathways((long) safePathways.size())
                .totalGroups(totalGroups)
                .page(Math.max(page, 0))
                .size(size)
                .groups(pagedGroups)
                .build();
    }

    private List<PathwayEvent> extractOrderedEvents(BiologicalPathway pathway) {
        if (pathway.getEvents() != null && !pathway.getEvents().isEmpty()) {
            return pathway.getEvents();
        }
        String raw = pathway.getRawPattern();
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String[] segments = raw.split(";");
        List<PathwayEvent> parsed = new ArrayList<>(segments.length);
        int pos = 1;
        for (String seg : segments) {
            if (seg == null || seg.isBlank()) {
                continue;
            }
            String[] parts = seg.split(",");
            if (parts.length >= 3) {
                String first = stripQuotes(parts[0].trim());
                String relation = stripQuotes(parts[1].trim());
                String second = stripQuotes(parts[2].trim());
                parsed.add(PathwayEvent.builder()
                        .first(first)
                        .relation(relation)
                        .second(second)
                        .position(pos++)
                        .build());
            }
        }
        return parsed;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static final class GroupAccumulator {
        private final String groupId;
        private final int length;
        private final String startObject;
        private final String endObject;
        private final List<String> orderedObjectNames;
        private final Map<String, List<String>> eventPubmedMap;
        private final Map<String, String> objectRoles = new LinkedHashMap<>();
        private final Set<String> allRelations = new LinkedHashSet<>();
        private final List<StepAccumulator> stepAccumulators;
        private final Set<List<String>> relationCombinations = new LinkedHashSet<>();
        private final Set<List<String>> eventIdCombinations = new LinkedHashSet<>();
        private int pathwaysCount = 0;

        private GroupAccumulator(
                String groupId,
                int length,
                List<String> orderedObjectNames,
                List<PathwayEvent> templateEvents,
                Map<String, List<String>> eventPubmedMap) {
            this.groupId = groupId;
            this.length = length;
            this.orderedObjectNames = orderedObjectNames;
            this.eventPubmedMap = eventPubmedMap != null ? eventPubmedMap : Collections.emptyMap();
            this.startObject = templateEvents.get(0).getFirst();
            this.endObject = templateEvents.get(templateEvents.size() - 1).getSecond();
            this.stepAccumulators = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                PathwayEvent ev = templateEvents.get(i);
                this.stepAccumulators.add(new StepAccumulator(i + 1, ev.getFirst(), ev.getSecond()));
            }
        }

        private void addPathway(
                List<PathwayEvent> events,
                Map<String, String> pathwayObjectRoles,
                Map<String, String> roleOverrides) {
            this.pathwaysCount++;

            for (String objName : orderedObjectNames) {
                if (roleOverrides != null && roleOverrides.containsKey(objName)) {
                    objectRoles.put(objName, roleOverrides.get(objName));
                } else if (!objectRoles.containsKey(objName)) {
                    String role = pathwayObjectRoles.getOrDefault(objName, "protein");
                    objectRoles.put(objName, role);
                }
            }

            List<String> combination = new ArrayList<>(events.size());
            List<String> eventIdCombination = new ArrayList<>(events.size());

            for (int i = 0; i < events.size(); i++) {
                PathwayEvent ev = events.get(i);
                String rel = ev.getRelation();
                String evId = ev.getId() != null ? ev.getId() : "";
                if (rel != null && !rel.isBlank()) {
                    allRelations.add(rel);
                    StepAccumulator stepAcc = stepAccumulators.get(i);
                    List<String> pmids = eventPubmedMap.getOrDefault(evId,
                            eventPubmedMap.getOrDefault(
                                    (stepAcc.first != null ? stepAcc.first.trim().toLowerCase() : "") + "|" +
                                    rel.trim().toLowerCase() + "|" +
                                    (stepAcc.second != null ? stepAcc.second.trim().toLowerCase() : ""),
                                    List.of()
                            )
                    );
                    stepAcc.addEvent(rel, evId, pmids);
                    combination.add(rel);
                    eventIdCombination.add(evId);
                } else {
                    combination.add("");
                    eventIdCombination.add(evId);
                }
            }
            relationCombinations.add(combination);
            eventIdCombinations.add(eventIdCombination);
        }

        private GroupedPathwayDTO toDTO() {
            List<GroupedPathwayObjectDTO> objectsDTO = new ArrayList<>(orderedObjectNames.size());
            for (int i = 0; i < orderedObjectNames.size(); i++) {
                String name = orderedObjectNames.get(i);
                String role = objectRoles.getOrDefault(name, "protein");
                objectsDTO.add(GroupedPathwayObjectDTO.builder()
                        .name(name)
                        .role(role)
                        .position(i + 1)
                        .build());
            }

            List<GroupedPathwayStepDTO> stepsDTO = new ArrayList<>(stepAccumulators.size());
            for (StepAccumulator stepAcc : stepAccumulators) {
                stepsDTO.add(GroupedPathwayStepDTO.builder()
                        .position(stepAcc.position)
                        .first(stepAcc.first)
                        .second(stepAcc.second)
                        .relations(new ArrayList<>(stepAcc.relations))
                        .events(new ArrayList<>(stepAcc.eventsByRelation.values()))
                        .build());
            }

            return GroupedPathwayDTO.builder()
                    .groupId(groupId)
                    .length(length)
                    .startObject(startObject)
                    .endObject(endObject)
                    .pathwaysCount(pathwaysCount)
                    .objects(objectsDTO)
                    .relations(new ArrayList<>(allRelations))
                    .steps(stepsDTO)
                    .relationCombinations(new ArrayList<>(relationCombinations))
                    .eventIdCombinations(new ArrayList<>(eventIdCombinations))
                    .build();
        }
    }

    private static final class StepAccumulator {
        private final int position;
        private final String first;
        private final String second;
        private final Set<String> relations = new LinkedHashSet<>();
        private final Map<String, GroupedPathwayStepEventDTO> eventsByRelation = new LinkedHashMap<>();

        private StepAccumulator(int position, String first, String second) {
            this.position = position;
            this.first = first;
            this.second = second;
        }

        private void addEvent(String relation, String eventId, List<String> pubmedIds) {
            if (relation != null && !relation.isBlank()) {
                relations.add(relation);
                GroupedPathwayStepEventDTO existing = eventsByRelation.get(relation);
                if (existing == null) {
                    eventsByRelation.put(relation, GroupedPathwayStepEventDTO.builder()
                            .id(eventId)
                            .relation(relation)
                            .pubmedIds(pubmedIds != null ? new ArrayList<>(pubmedIds) : new ArrayList<>())
                            .build());
                } else {
                    if ((existing.getId() == null || existing.getId().isBlank()) && eventId != null && !eventId.isBlank()) {
                        existing.setId(eventId);
                    }
                    if (pubmedIds != null && !pubmedIds.isEmpty()) {
                        for (String pmid : pubmedIds) {
                            if (!existing.getPubmedIds().contains(pmid)) {
                                existing.getPubmedIds().add(pmid);
                            }
                        }
                    }
                }
            }
        }
    }

    public static String formatRoleDisplayType(String role) {
        if (role == null || role.isBlank()) {
            return "Protein";
        }
        String normalized = role.toLowerCase().trim().replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "protein" -> "Protein";
            case "gene" -> "Gene";
            case "enzyme" -> "Enzyme";
            case "receptor" -> "Receptor";
            case "ligand" -> "Ligand";
            case "transcription_factor", "tf" -> "Transcription Factor";
            case "adaptor_protein" -> "Adaptor Protein";
            case "disease" -> "Disease";
            case "variant" -> "Variant";
            default -> {
                String[] parts = normalized.split("_");
                StringBuilder sb = new StringBuilder();
                for (String p : parts) {
                    if (!p.isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
                    }
                }
                yield sb.isEmpty() ? "Protein" : sb.toString();
            }
        };
    }
}
