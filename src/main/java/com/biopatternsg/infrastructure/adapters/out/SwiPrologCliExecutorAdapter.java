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
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.model.PathwayObject;
import com.biopatternsg.domain.port.out.PrologInferencePort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class SwiPrologCliExecutorAdapter implements PrologInferencePort {

    private final Semaphore concurrencyLimiter;
    private final String configuredBinary;
    private final Duration defaultTimeout;

    public SwiPrologCliExecutorAdapter(
            @ConfigProperty(name = "prolog.concurrency.limit", defaultValue = "4") int maxConcurrent,
            @ConfigProperty(name = "prolog.binary.path", defaultValue = "swipl") String binaryPath,
            @ConfigProperty(name = "prolog.timeout.seconds", defaultValue = "30") long timeoutSeconds) {
        this.concurrencyLimiter = new Semaphore(maxConcurrent);
        this.configuredBinary = binaryPath;
        this.defaultTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public List<PathwayEvent> findStartEvents(String pathwaysObjectsPath, String kBasePath, String rulesPath) {
        String goal = "forall(inicio(A,E,B), format('EVENT:~w,~w,~w~n', [A,E,B]))";
        return executeQuery(pathwaysObjectsPath, kBasePath, rulesPath, goal);
    }

    @Override
    public List<PathwayEvent> findEndEvents(String pathwaysObjectsPath, String kBasePath, String rulesPath, String endObject) {
        String goal;
        if (endObject != null && !endObject.trim().isEmpty()) {
            goal = String.format("forall(final(A,E,'%s'), format('EVENT:~w,~w,%s~n', [A,E]))", 
                    escapePrologAtom(endObject), escapePrologAtom(endObject));
        } else {
            goal = "forall(final(A,E,B), format('EVENT:~w,~w,~w~n', [A,E,B]))";
        }
        return executeQuery(pathwaysObjectsPath, kBasePath, rulesPath, goal);
    }

    @Override
    public List<PathwayEvent> findMiddleEvents(String pathwaysObjectsPath, String kBasePath, String rulesPath, String currentObject) {
        String goal = String.format("forall(intermedios('%s',E,B), format('EVENT:%s,~w,~w~n', [E,B]))",
                escapePrologAtom(currentObject), escapePrologAtom(currentObject));
        return executeQuery(pathwaysObjectsPath, kBasePath, rulesPath, goal);
    }

    @Override
    public List<BiologicalPathway> inferPathways(String pathwaysObjectsPath, String kBasePath, String rulesPath) {
        log.info("Starting pathway inference using SWI-Prolog CLI adapter");
        List<PathwayEvent> startEvents = findStartEvents(pathwaysObjectsPath, kBasePath, rulesPath);
        List<PathwayEvent> endEvents = findEndEvents(pathwaysObjectsPath, kBasePath, rulesPath, null);

        log.info("Found {} start events and {} end events", startEvents.size(), endEvents.size());
        if (startEvents.isEmpty() || endEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<BiologicalPathway> pathways = new ArrayList<>();
        Set<String> initialTargets = startEvents.stream()
                .map(PathwayEvent::getObject)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String targetObj : initialTargets) {
            explorePathway(targetObj, startEvents, endEvents, new ArrayList<>(), pathways, 
                    pathwaysObjectsPath, kBasePath, rulesPath, 0, 5);
        }

        log.info("Inference completed. Total pathways discovered: {}", pathways.size());
        return pathways;
    }

    private void explorePathway(
            String currentObj,
            List<PathwayEvent> startEvents,
            List<PathwayEvent> endEvents,
            List<PathwayEvent> currentPath,
            List<BiologicalPathway> results,
            String pathwaysObjectsPath,
            String kBasePath,
            String rulesPath,
            int depth,
            int maxDepth) {

        if (depth > maxDepth) {
            return;
        }

        List<PathwayEvent> middleEvents = findMiddleEvents(pathwaysObjectsPath, kBasePath, rulesPath, currentObj);

        for (PathwayEvent nextEvent : middleEvents) {
            // Prevenir ciclos en la ruta
            boolean cycle = currentPath.stream().anyMatch(e -> e.getSubject().equals(nextEvent.getObject()));
            if (cycle) {
                continue;
            }

            List<PathwayEvent> updatedPath = new ArrayList<>(currentPath);
            updatedPath.add(nextEvent);

            // Verificar si el destino conecta con un evento final
            boolean connectsToEnd = endEvents.stream()
                    .anyMatch(end -> end.getSubject().equals(nextEvent.getObject()));

            if (connectsToEnd) {
                // Registrar las combinaciones completas de inicio + intermedias + fin
                registerFullPathway(updatedPath, startEvents, endEvents, results);
            } else {
                explorePathway(nextEvent.getObject(), startEvents, endEvents, updatedPath, results,
                        pathwaysObjectsPath, kBasePath, rulesPath, depth + 1, maxDepth);
            }
        }
    }

    private void registerFullPathway(
            List<PathwayEvent> middleChain,
            List<PathwayEvent> startEvents,
            List<PathwayEvent> endEvents,
            List<BiologicalPathway> results) {

        if (middleChain.isEmpty()) {
            return;
        }

        String firstMiddleSubject = middleChain.get(0).getSubject();
        String lastMiddleObject = middleChain.get(middleChain.size() - 1).getObject();

        List<PathwayEvent> matchingStarts = startEvents.stream()
                .filter(s -> s.getObject().equals(firstMiddleSubject))
                .toList();

        List<PathwayEvent> matchingEnds = endEvents.stream()
                .filter(e -> e.getSubject().equals(lastMiddleObject))
                .toList();

        for (PathwayEvent start : matchingStarts) {
            for (PathwayEvent end : matchingEnds) {
                List<PathwayEvent> fullEvents = new ArrayList<>();
                fullEvents.add(new PathwayEvent(start.getFirst(), start.getRelation(), start.getSecond(), 1));
                int pos = 2;
                for (PathwayEvent m : middleChain) {
                    fullEvents.add(new PathwayEvent(m.getFirst(), m.getRelation(), m.getSecond(), pos++));
                }
                fullEvents.add(new PathwayEvent(end.getFirst(), end.getRelation(), end.getSecond(), pos));

                List<PathwayObject> objects = new ArrayList<>();
                objects.add(PathwayObject.builder().name(start.getFirst()).build());
                for (PathwayEvent ev : fullEvents) {
                    if (objects.stream().noneMatch(o -> o.getName().equals(ev.getSecond()))) {
                        objects.add(PathwayObject.builder().name(ev.getSecond()).build());
                    }
                }

                String pattern = fullEvents.stream()
                        .map(PathwayEvent::toRawEvent)
                        .collect(Collectors.joining(";"));

                results.add(BiologicalPathway.builder()
                        .rawPattern(pattern)
                        .length(fullEvents.size())
                        .startObject(start.getFirst())
                        .endObject(end.getSecond())
                        .objects(objects)
                        .events(fullEvents)
                        .build());
            }
        }
    }

    public List<PathwayEvent> executeQuery(String pathwaysObjectsPath, String kBasePath, String rulesPath, String goal) {
        try {
            boolean acquired = concurrencyLimiter.tryAcquire(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new TimeoutException("Límite de concurrencia de inferencias alcanzado (timeout esperando turno)");
            }

            try {
                return runProcess(pathwaysObjectsPath, kBasePath, rulesPath, goal);
            } finally {
                concurrencyLimiter.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ejecución de inferencia interrumpida", e);
        } catch (Exception e) {
            log.error("Error ejecutando inferencia SWI-Prolog: {}", e.getMessage(), e);
            throw new RuntimeException("Error en ejecución de inferencia Prolog: " + e.getMessage(), e);
        }
    }

    private List<PathwayEvent> runProcess(String pathwaysObjectsPath, String kBasePath, String rulesPath, String goal) 
            throws Exception {

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean useWsl = isWindows && "swipl".equalsIgnoreCase(configuredBinary);

        String objP = normalizePath(pathwaysObjectsPath, useWsl);
        String kbP = normalizePath(kBasePath, useWsl);
        String rulesP = normalizePath(rulesPath, useWsl);

        // Crear script efímero runner para evitar problemas de escape de comillas y caracteres en bash/wsl
        Path tempDir = Paths.get("target").toAbsolutePath();
        if (!Files.exists(tempDir)) {
            tempDir = Paths.get(".").toAbsolutePath();
        }
        Path runnerFile = Files.createTempFile(tempDir, "prolog_runner_", ".pl");

        String runnerContent = String.format(
                ":- style_check(-discontiguous).\n" +
                ":- consult('%s').\n" +
                ":- consult('%s').\n" +
                ":- consult('%s').\n\n" +
                "run :-\n" +
                "    %s,\n" +
                "    halt.\n\n" +
                ":- initialization(run, main).\n",
                objP, kbP, rulesP, goal
        );

        Files.writeString(runnerFile, runnerContent);

        try {
            String runnerPathNormalized = normalizePath(runnerFile.toString(), useWsl);

            List<String> command = new ArrayList<>();
            if (useWsl) {
                command.add("wsl");
                command.add("swipl");
            } else {
                command.add(configuredBinary);
            }

            command.add("-q");
            command.add("-s");
            command.add(runnerPathNormalized);

            log.debug("Lanzando subproceso Prolog con runner script: {}", command);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<PathwayEvent> events = new ArrayList<>();

            List<String> rawOutput = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rawOutput.add(line);
                    if (line.startsWith("EVENT:")) {
                        String eventStr = line.substring("EVENT:".length()).trim();
                        String[] parts = eventStr.split(",");
                        if (parts.length == 3) {
                            events.add(new PathwayEvent(parts[0].trim(), parts[1].trim(), parts[2].trim()));
                        }
                    }
                }
            }

            boolean finished = process.waitFor(defaultTimeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("La consulta Prolog excedió el tiempo límite permitido");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorDetails = String.join(" | ", rawOutput);
                log.error("El subproceso Prolog finalizó con código de salida {}: {}", exitCode, errorDetails);
                throw new IllegalStateException("SWI-Prolog error (exit code " + exitCode + "): " + errorDetails);
            }

            return events;

        } finally {
            try {
                Files.deleteIfExists(runnerFile);
            } catch (Exception ignored) {
            }
        }
    }

    private String normalizePath(String pathStr, boolean useWsl) {
        if (pathStr == null) return "";
        Path p = Paths.get(pathStr).toAbsolutePath();
        String formatted = p.toString().replace("\\", "/");
        if (useWsl && formatted.matches("^[A-Za-z]:/.*")) {
            // Convertir Z:/home/... a /home/... si es la unidad mapeada de WSL
            if (formatted.toLowerCase().startsWith("z:/")) {
                formatted = formatted.substring(2);
            } else {
                char drive = Character.toLowerCase(formatted.charAt(0));
                formatted = "/mnt/" + drive + formatted.substring(2);
            }
        }
        return formatted;
    }

    private String escapePrologAtom(String val) {
        if (val == null) return "";
        return val.replace("'", "\\'");
    }
}
