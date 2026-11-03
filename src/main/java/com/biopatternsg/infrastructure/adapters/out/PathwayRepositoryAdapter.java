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

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.GroupedPathway;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.port.out.repositories.PathwayRepository;
import com.biopatternsg.infrastructure.mapper.PathwayMapper;
import com.biopatternsg.infrastructure.mongo.GroupedPathwayCollection;
import com.biopatternsg.infrastructure.mongo.PathwayCollection;
import com.biopatternsg.infrastructure.mongo.PathwayGraphCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PathwayRepositoryAdapter implements PathwayRepository, PanacheMongoRepository<PathwayCollection> {

    private static final int BATCH_SIZE = 5000;
    private static final int GROUPED_BATCH_SIZE = 1000;

    private final PathwayMapper pathwayMapper;

    @PostConstruct
    void initIndexes() {
        try {
            mongoCollection().createIndex(Indexes.ascending("pipelineId", "startObject", "endObject"));
            mongoCollection().createIndex(Indexes.ascending("pipelineId", "objects.name"));
            GroupedPathwayCollection.mongoCollection().createIndex(Indexes.ascending("pipelineId", "length"));
            GroupedPathwayCollection.mongoCollection().createIndex(Indexes.ascending("pipelineId", "startObject", "endObject"));
            GroupedPathwayCollection.mongoCollection().createIndex(Indexes.ascending("pipelineId", "objects.name"));
            PathwayGraphCollection.mongoCollection().createIndex(
                    Indexes.ascending("pipelineId"),
                    new IndexOptions().unique(true)
            );
            log.info("MongoDB indexes successfully initialized for pathways, grouped_pathways, and pathway_graphs");
        } catch (Exception e) {
            log.warn("Could not create MongoDB indexes automatically: {}", e.getMessage());
        }
    }

    @Override
    public void savePathwaysBatch(List<BiologicalPathway> pathways) {
        if (pathways == null || pathways.isEmpty()) {
            return;
        }
        int total = pathways.size();
        int totalBatches = (total + BATCH_SIZE - 1) / BATCH_SIZE;
        int batchIndex = 0;

        for (int i = 0; i < total; i += BATCH_SIZE) {
            batchIndex++;
            int end = Math.min(i + BATCH_SIZE, total);
            List<PathwayCollection> batchEntities = new ArrayList<>(end - i);
            for (int j = i; j < end; j++) {
                batchEntities.add(pathwayMapper.toEntity(pathways.get(j)));
            }
            persist(batchEntities);
            log.info("[MONGO BATCH] Persisted batch {}/{} ({}/{} pathways)",
                    batchIndex, totalBatches, end, total);
        }
        log.info("Persisted {} total pathways into MongoDB in {} batches of up to {}",
                total, totalBatches, BATCH_SIZE);
    }

    @Override
    public void saveGroupedPathwaysBatch(List<GroupedPathway> groupedPathways) {
        if (groupedPathways == null || groupedPathways.isEmpty()) {
            return;
        }
        int total = groupedPathways.size();
        int totalBatches = (total + GROUPED_BATCH_SIZE - 1) / GROUPED_BATCH_SIZE;
        int batchIndex = 0;

        for (int i = 0; i < total; i += GROUPED_BATCH_SIZE) {
            batchIndex++;
            int end = Math.min(i + GROUPED_BATCH_SIZE, total);
            List<GroupedPathwayCollection> batchEntities = new ArrayList<>(end - i);
            for (int j = i; j < end; j++) {
                batchEntities.add(pathwayMapper.toGroupedEntity(groupedPathways.get(j)));
            }
            GroupedPathwayCollection.persist(batchEntities);
            log.info("[MONGO GROUPED BATCH] Persisted batch {}/{} ({}/{} grouped pathways)",
                    batchIndex, totalBatches, end, total);
        }
        log.info("Persisted {} total grouped pathways into MongoDB in {} batches of up to {}",
                total, totalBatches, GROUPED_BATCH_SIZE);
    }

    @Override
    public void savePathwayGraph(PathwayGraph graph) {
        if (graph == null) {
            return;
        }
        PathwayGraphCollection existing = PathwayGraphCollection.find("pipelineId", graph.getPipelineId()).firstResult();
        PathwayGraphCollection entity = pathwayMapper.toEntity(graph);
        if (existing != null) {
            entity.id = existing.id;
            entity.update();
        } else {
            entity.persist();
        }
        log.info("Persisted consolidated pathway graph for pipelineId: {}", graph.getPipelineId());
    }

    @Override
    public Optional<PathwayGraph> findGraphByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Optional.empty();
        }
        PathwayGraphCollection entity = PathwayGraphCollection.find("pipelineId", pipelineId).firstResult();
        return Optional.ofNullable(entity).map(pathwayMapper::toModel);
    }

    @Override
    public List<BiologicalPathway> findByPipelineId(String pipelineId, String startObject, String endObject, String containsObject, int page, int size) {
        Document query = new Document("pipelineId", pipelineId);
        if (startObject != null && !startObject.isBlank()) {
            query.append("startObject", startObject);
        }
        if (endObject != null && !endObject.isBlank()) {
            query.append("endObject", endObject);
        }
        if (containsObject != null && !containsObject.isBlank()) {
            query.append("objects.name", containsObject);
        }

        PanacheQuery<PathwayCollection> panacheQuery = find(query);
        if (page >= 0 && size > 0) {
            panacheQuery = panacheQuery.page(Page.of(page, size));
        }
        return panacheQuery.list().stream()
                .map(pathwayMapper::toModel)
                .toList();
    }

    @Override
    public List<GroupedPathway> findGroupedByPipelineId(
            String pipelineId,
            String startObject,
            String endObject,
            String containsObject,
            Integer length,
            String search,
            int page,
            int size) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }
        Document query = buildGroupedQuery(pipelineId, startObject, endObject, containsObject, length, search);
        PanacheQuery<GroupedPathwayCollection> panacheQuery = GroupedPathwayCollection.find(query);
        if (page >= 0 && size > 0) {
            panacheQuery = panacheQuery.page(Page.of(page, size));
        }
        return panacheQuery.list().stream()
                .map(pathwayMapper::toGroupedModel)
                .toList();
    }

    @Override
    public long countGroupedByPipelineId(
            String pipelineId,
            String startObject,
            String endObject,
            String containsObject,
            Integer length,
            String search) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return 0L;
        }
        Document query = buildGroupedQuery(pipelineId, startObject, endObject, containsObject, length, search);
        return GroupedPathwayCollection.count(query);
    }

    @Override
    public List<Integer> findDistinctGroupedLengthsByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }
        try {
            List<Integer> lengths = new ArrayList<>();
            GroupedPathwayCollection.mongoCollection()
                    .distinct("length", new Document("pipelineId", pipelineId), Integer.class)
                    .into(lengths);
            lengths.sort(Integer::compareTo);
            return lengths;
        } catch (Exception e) {
            log.warn("Could not query distinct grouped pathway lengths for pipelineId {}: {}", pipelineId, e.getMessage());
            return List.of();
        }
    }

    private Document buildGroupedQuery(
            String pipelineId,
            String startObject,
            String endObject,
            String containsObject,
            Integer length,
            String search) {
        Document query = new Document("pipelineId", pipelineId);
        if (startObject != null && !startObject.isBlank()) {
            query.append("startObject", startObject);
        }
        if (endObject != null && !endObject.isBlank()) {
            query.append("endObject", endObject);
        }
        if (containsObject != null && !containsObject.isBlank()) {
            query.append("objects.name", containsObject);
        }
        if (length != null && length > 0) {
            query.append("length", length);
        }
        if (search != null && !search.isBlank()) {
            String escaped = Pattern.quote(search.trim());
            Document regexDoc = new Document("$regex", escaped).append("$options", "i");
            query.append("$or", List.of(
                    new Document("groupId", regexDoc),
                    new Document("objects.name", regexDoc),
                    new Document("relations", regexDoc)
            ));
        }
        return query;
    }

    @Override
    public long countByPipelineId(String pipelineId) {
        return count("pipelineId", pipelineId);
    }

    @Override
    public long deleteByPipelineId(String pipelineId) {
        long deletedPathways = delete("pipelineId", pipelineId);
        long deletedGrouped = GroupedPathwayCollection.delete("pipelineId", pipelineId);
        PathwayGraphCollection.delete("pipelineId", pipelineId);
        log.info("Deleted {} pathways, {} grouped pathways, and graph for pipelineId: {}",
                deletedPathways, deletedGrouped, pipelineId);
        return deletedPathways;
    }
}
