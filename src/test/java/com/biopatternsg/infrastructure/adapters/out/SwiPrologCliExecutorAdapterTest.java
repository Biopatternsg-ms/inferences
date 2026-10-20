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

import com.biopatternsg.domain.model.PathwayEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwiPrologCliExecutorAdapterTest {

    private SwiPrologCliExecutorAdapter adapter;
    private String pathwaysObjectsPath;
    private String kBasePath;
    private String rulesPath;

    @BeforeEach
    void setUp() {
        // Concurrencia máxima 4, binario swipl (se invoca vía WSL si corre en Windows)
        adapter = new SwiPrologCliExecutorAdapter(4, "swipl", 30);

        Path legacyDir = Paths.get("src/main/resources/legacy").toAbsolutePath();
        pathwaysObjectsPath = legacyDir.resolve("pathwaysObjects.pl").toString();
        kBasePath = legacyDir.resolve("kBase_optimized.pl").toString();
        rulesPath = legacyDir.resolve("pathways_optimized.pl").toString();

        assertTrue(new File(pathwaysObjectsPath).exists(), "pathwaysObjects.pl debe existir");
        assertTrue(new File(kBasePath).exists(), "kBase_optimized.pl debe existir");
        assertTrue(new File(rulesPath).exists(), "pathways_optimized.pl debe existir");
    }

    @Test
    @DisplayName("Debe encontrar eventos de inicio válidos en el caso real de COVID-19")
    void shouldFindStartEventsSuccessfully() {
        List<PathwayEvent> startEvents = adapter.findStartEvents(pathwaysObjectsPath, kBasePath, rulesPath);

        assertNotNull(startEvents);
        assertFalse(startEvents.isEmpty(), "Debe encontrar al menos un evento de inicio");

        // Validar que encuentra el evento de inicio 'IFNA1', association, 'IRF7' presente en pathways.txt
        boolean containsExpectedStart = startEvents.stream().anyMatch(e ->
                "IFNA1".equals(e.getSubject()) &&
                "IRF7".equals(e.getObject()) &&
                ("association".equals(e.getRelation()) || "bind".equals(e.getRelation()))
        );

        assertTrue(containsExpectedStart, "Debe contener el evento de inicio IFNA1 -> IRF7 de pathways.txt");
    }

    @Test
    @DisplayName("Debe encontrar eventos finales válidos en el caso real de COVID-19")
    void shouldFindEndEventsSuccessfully() {
        List<PathwayEvent> endEvents = adapter.findEndEvents(pathwaysObjectsPath, kBasePath, rulesPath, null);

        assertNotNull(endEvents);
        assertFalse(endEvents.isEmpty(), "Debe encontrar eventos finales");

        // Validar que encuentra el evento de cierre 'IRF7', bind, 'IRF3' presente en pathways.txt
        boolean containsExpectedEnd = endEvents.stream().anyMatch(e ->
                "IRF7".equals(e.getSubject()) &&
                "IRF3".equals(e.getObject())
        );

        assertTrue(containsExpectedEnd, "Debe contener el evento final IRF7 -> IRF3 de pathways.txt");
    }

    @Test
    @DisplayName("Debe soportar ejecución concurrente de múltiples clientes sin colisiones")
    void shouldSupportConcurrentClients() throws Exception {
        int clientCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);

        List<Callable<List<PathwayEvent>>> tasks = List.of(
                () -> adapter.findStartEvents(pathwaysObjectsPath, kBasePath, rulesPath),
                () -> adapter.findStartEvents(pathwaysObjectsPath, kBasePath, rulesPath),
                () -> adapter.findEndEvents(pathwaysObjectsPath, kBasePath, rulesPath, null),
                () -> adapter.findStartEvents(pathwaysObjectsPath, kBasePath, rulesPath),
                () -> adapter.findEndEvents(pathwaysObjectsPath, kBasePath, rulesPath, null)
        );

        List<Future<List<PathwayEvent>>> results = executor.invokeAll(tasks);

        for (Future<List<PathwayEvent>> f : results) {
            List<PathwayEvent> evts = f.get();
            assertNotNull(evts);
            assertFalse(evts.isEmpty(), "Cada cliente concurrente debe recibir sus eventos");
        }

        executor.shutdown();
    }
}
