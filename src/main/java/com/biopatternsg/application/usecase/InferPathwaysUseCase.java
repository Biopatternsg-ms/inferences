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
package com.biopatternsg.application.usecase;

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.DynamicExecutionResult;
import com.biopatternsg.domain.model.DynamicInferenceInput;
import com.biopatternsg.domain.model.GraphEdge;
import com.biopatternsg.domain.model.Inference;
import com.biopatternsg.domain.model.KbObject;
import com.biopatternsg.domain.model.PathwayEvent;
import com.biopatternsg.domain.model.PathwayGraph;
import com.biopatternsg.domain.model.PathwayObject;
import com.biopatternsg.domain.port.in.InferPathways;
import com.biopatternsg.domain.port.out.PrologInferencePort;
import com.biopatternsg.domain.port.out.repositories.InferenceRepository;
import com.biopatternsg.domain.port.out.repositories.KnowledgeBaseRepository;
import com.biopatternsg.domain.port.out.repositories.PathwayRepository;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ApplicationScoped
public class InferPathwaysUseCase implements InferPathways {

    private static final int DEFAULT_MAX_DEPTH = 5;
    private static final int DEFAULT_MAX_PATHWAYS_LIMIT = 500_000;
    private static final int DEFAULT_EXECUTION_TIMEOUT_SECONDS = 300;
    private static final long PROGRESS_LOG_INTERVAL_MS = 2_000L;
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private final PrologInferencePort prologInferencePort;
    private final PathwayRepository pathwayRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final InferenceRepository inferenceRepository;
    private final String outputDirName;
    private final Semaphore concurrencyLimiter;
    private final int timeoutSeconds;
    private final int maxPathwaysLimit;
    private final int executionTimeoutSeconds;

    @Inject
    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            PathwayRepository pathwayRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            InferenceRepository inferenceRepository,
            @ConfigProperty(name = "inferences.output.dir", defaultValue = "output") String outputDirName,
            @ConfigProperty(name = "inferences.prolog.concurrency", defaultValue = "4") int maxConcurrency,
            @ConfigProperty(name = "inferences.prolog.timeout.seconds", defaultValue = "60") int timeoutSeconds,
            @ConfigProperty(name = "inferences.pathways.max-limit", defaultValue = "500000") int maxPathwaysLimit,
            @ConfigProperty(name = "inferences.execution.timeout.seconds", defaultValue = "300") int executionTimeoutSeconds) {
        this.prologInferencePort = prologInferencePort;
        this.pathwayRepository = pathwayRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.inferenceRepository = inferenceRepository;
        this.outputDirName = outputDirName;
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.timeoutSeconds = timeoutSeconds;
        this.maxPathwaysLimit = maxPathwaysLimit > 0 ? maxPathwaysLimit : DEFAULT_MAX_PATHWAYS_LIMIT;
        this.executionTimeoutSeconds = executionTimeoutSeconds > 0 ? executionTimeoutSeconds : DEFAULT_EXECUTION_TIMEOUT_SECONDS;
    }

    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            PathwayRepository pathwayRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            InferenceRepository inferenceRepository,
            String outputDirName,
            int maxConcurrency,
            int timeoutSeconds) {
        this(prologInferencePort, pathwayRepository, knowledgeBaseRepository, inferenceRepository,
                outputDirName, maxConcurrency, timeoutSeconds, DEFAULT_MAX_PATHWAYS_LIMIT, DEFAULT_EXECUTION_TIMEOUT_SECONDS);
    }

    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            PathwayRepository pathwayRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            String outputDirName,
            int maxConcurrency,
            int timeoutSeconds) {
        this(prologInferencePort, pathwayRepository, knowledgeBaseRepository, null,
                outputDirName, maxConcurrency, timeoutSeconds, DEFAULT_MAX_PATHWAYS_LIMIT, DEFAULT_EXECUTION_TIMEOUT_SECONDS);
    }

    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            String outputDirName,
            int maxConcurrency,
            int timeoutSeconds) {
        this(prologInferencePort, null, null, null,
                outputDirName, maxConcurrency, timeoutSeconds, DEFAULT_MAX_PATHWAYS_LIMIT, DEFAULT_EXECUTION_TIMEOUT_SECONDS);
    }

    @Override
    public List<BiologicalPathway> execute(String pathwaysObjectsPath, String kBasePath, String rulesPath) {
        log.info("Executing InferPathwaysUseCase with inputs: objects='{}', kBase='{}', rules='{}'",
                pathwaysObjectsPath, kBasePath, rulesPath);
        return prologInferencePort.inferPathways(pathwaysObjectsPath, kBasePath, rulesPath);
    }

    @Override
    public Map<String, Object> generateFromFiles() {
        log.info("Executing pathway generation from physical files purely with Java and SWI-Prolog");
        long startTime = System.currentTimeMillis();
        Path legacyDir = getLegacyDir();
        Path outputDir = getOutputDir();

        try {
            Files.createDirectories(outputDir);

            // 1. Ejecutar SWI-Prolog para extraer y filtrar los 5 subconjuntos de eventos biológicos
            runPrologEventExtraction(legacyDir, outputDir);

            // 2. Cargar los eventos filtrados en memoria
            List<String> listaInicio = loadEventsJson(outputDir, "inicios.json");
            List<String> listaFin = loadEventsJson(outputDir, "finales.json");
            List<String> intermediosAll = loadEventsJson(outputDir, "intermedios.json");
            List<String> eventoEspecialAll = loadEventsJson(outputDir, "evento_especial.json");
            List<String> finalEspecialAll = loadEventsJson(outputDir, "final_especial.json");

            // 3. Indexar en estructuras de búsqueda O(1)
            Map<String, List<String>> intermediosMap = new LinkedHashMap<>();
            for (String ev : intermediosAll) {
                String src = ev.split(",")[0];
                intermediosMap.computeIfAbsent(src, k -> new ArrayList<>()).add(ev);
            }

            Map<String, List<String>> eventoEspecialMap = new LinkedHashMap<>();
            for (String ev : eventoEspecialAll) {
                String[] parts = ev.split(",");
                String key = parts[0] + "->" + parts[2];
                eventoEspecialMap.computeIfAbsent(key, k -> new ArrayList<>()).add(parts[1]);
            }

            Map<String, List<String>> finalEspecialMap = new LinkedHashMap<>();
            for (String ev : finalEspecialAll) {
                String[] parts = ev.split(",");
                String key = parts[0] + "->" + parts[2];
                finalEspecialMap.computeIfAbsent(key, k -> new ArrayList<>()).add(parts[1]);
            }

            List<String> objEnlace = new ArrayList<>();
            for (String ev : listaInicio) {
                String dest = ev.split(",")[2];
                if (!objEnlace.contains(dest)) {
                    objEnlace.add(dest);
                }
            }

            List<String> objCierre = new ArrayList<>();
            List<String> ftList = new ArrayList<>();
            for (String ev : listaFin) {
                String[] parts = ev.split(",");
                String s = parts[0];
                String d = parts[2];
                if (!ftList.contains(s)) {
                    ftList.add(s);
                }
                if (!objCierre.contains(d)) {
                    objCierre.add(d);
                }
            }

            List<String> generatedPathways = new ArrayList<>();
            List<List<String>> generatedObjects = new ArrayList<>();

            // 4. Patrones de 2 o 3 eventos (inicio + especial / inicio + especial + fin)
            for (String E : objEnlace) {
                for (String F : objCierre) {
                    List<String> specialEvents = eventoEspecialMap.getOrDefault(E + "->" + F, Collections.emptyList());
                    for (String evento : specialEvents) {
                        String fin = E + "," + evento + "," + F;
                        String[] sep = fin.split(",");
                        String classified = classifyEvent(sep[1]);
                        if ("interact".equals(sep[1]) || "bind".equals(sep[1]) || "regulate".equals(classified) || "inhibit".equals(classified)) {
                            for (String i : listaInicio) {
                                String[] sep1 = i.split(",");
                                if (E.equals(sep1[2]) && !F.equals(sep1[0])) {
                                    String patron = i + ";" + fin;
                                    List<String> objs = listObjectsPatron(patron);
                                    generatedPathways.add(patron);
                                    generatedObjects.add(objs);
                                }
                            }
                        }

                        for (String inEv : listaInicio) {
                            String[] sep1 = inEv.split(",");
                            for (String f : objCierre) {
                                List<String> feEvents = finalEspecialMap.getOrDefault(sep1[0] + "->" + f, Collections.emptyList());
                                for (String fe : feEvents) {
                                    String eFin = sep1[0] + "," + fe + "," + f;
                                    String feClassified = classifyEvent(fe);
                                    if (E.equals(sep1[2]) && sep[2].equals(f) && ("regulate".equals(feClassified) || "inhibit".equals(feClassified))) {
                                        String patron = inEv + ";" + fin;
                                        List<String> objs = new ArrayList<>(listObjectsPatron(patron));
                                        patron += ";" + eFin;
                                        objs.addAll(listObjectsPatron(eFin));
                                        generatedPathways.add(patron);
                                        generatedObjects.add(objs);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Patrones multietapa con eventos intermedios (DFS recursivo)
            List<String> objC = new ArrayList<>();
            for (String f : listaFin) {
                String c = f.split(",")[2];
                if (!objC.contains(c)) {
                    objC.add(c);
                }
            }

            for (String enlace : objEnlace) {
                intermediosDFSLegacy(new ArrayList<>(), enlace, "", 0, objCierre,
                        intermediosMap, ftList, listaInicio, listaFin, objC,
                        generatedPathways, generatedObjects);
            }

            // 6. Deduplicación preservando orden
            Set<String> seen = new HashSet<>();
            List<String> uniquePathways = new ArrayList<>();
            List<List<String>> uniqueObjects = new ArrayList<>();
            for (int i = 0; i < generatedPathways.size(); i++) {
                String p = generatedPathways.get(i);
                if (seen.add(p)) {
                    uniquePathways.add(p);
                    uniqueObjects.add(generatedObjects.get(i));
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;

            // 7. Escribir pathways_generated.txt en outputDir (solo archivo, sin guardar en MongoDB)
            Path outFile = outputDir.resolve("pathways_generated.txt");
            StringBuilder sb = new StringBuilder(uniquePathways.size() * 128);
            for (int i = 0; i < uniquePathways.size(); i++) {
                String p = uniquePathways.get(i);
                List<String> objs = uniqueObjects.get(i);
                sb.append("\n\n").append(p).append("\n[");
                for (int j = 0; j < objs.size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(objs.get(j));
                }
                sb.append("]\n");
            }
            Files.writeString(outFile, sb.toString());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("dataset", "COVID-19 Legacy Dataset");
            result.put("executionTimeMs", durationMs);
            result.put("generatedPathwaysCount", uniquePathways.size());
            result.put("inputDirectory", legacyDir.toString().replace('\\', '/'));
            result.put("outputDirectory", outputDir.toString().replace('\\', '/'));
            result.put("generatedFilePath", outFile.toString().replace('\\', '/'));
            result.put("sampleGeneratedPathways", uniquePathways.subList(0, Math.min(5, uniquePathways.size())));

            log.info("Pathway generation from files completed in {} ms with {} unique pathways (saved to {})",
                    durationMs, uniquePathways.size(), outFile);

            return result;

        } catch (Exception e) {
            log.error("Failed to generate pathways natively from files", e);
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("status", "ERROR");
        fallback.put("message", "Pathway generation from files failed");
        return fallback;
    }

    private void runPrologEventExtraction(Path legacyDir, Path outputDir) throws Exception {
        Path objFile = legacyDir.resolve("pathwaysObjects.pl");
        Path kbFile = legacyDir.resolve("kBase_optimized.pl");

        String objNorm = normalizePath(objFile.toString());
        String kbNorm = normalizePath(kbFile.toString());

        Path iniciosFile = outputDir.resolve("inicios.json");
        Path finalesFile = outputDir.resolve("finales.json");
        Path intermediosFile = outputDir.resolve("intermedios.json");
        Path evEspFile = outputDir.resolve("evento_especial.json");
        Path finEspFile = outputDir.resolve("final_especial.json");

        String iniciosNorm = normalizePath(iniciosFile.toString());
        String finalesNorm = normalizePath(finalesFile.toString());
        String intermediosNorm = normalizePath(intermediosFile.toString());
        String evEspNorm = normalizePath(evEspFile.toString());
        String finEspNorm = normalizePath(finEspFile.toString());

        String runnerTemplate = """
:- style_check(-discontiguous).
:- use_module(library(http/json)).
:- consult('{obj_file}').
:- consult('{kb_file}').

restrictions_start_ligand(A, B) :-
    ligand(A),
    protein(B),
    A \\= B.

inicio_rule(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    member(E, [association, bind]),
    restrictions_start_ligand(A, B).

final_rule(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    member(E, [association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]),
    protein(A),
    protein(B),
    A \\= B.

intermedios_rule(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    member(E, [association]),
    protein(A),
    protein(B),
    A \\= B.

evento_especial_rule(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    member(E, [association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]).

final_especial_rule(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    member(E, [association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]).

dump_list(File, List) :-
    open(File, write, S, [encoding(utf8)]),
    json_write(S, List, [width(0)]),
    close(S).

run :-
    L = ['IFNA1', 'IRF1', 'IRF3', 'IRF7', 'IRF9', 'N', 'S', 'TLR2', 'TLR3', 'TLR4', 'TLR5', 'TLR7', 'TLR8', 'TLR9'],
    findall([A, E, B], inicio_rule(A, E, B, L), Inicios),
    dump_list('{inicios_file}', Inicios),
    
    findall([A, E, B], final_rule(A, E, B, L), Finales),
    dump_list('{finales_file}', Finales),
    
    findall([A, E, B], intermedios_rule(A, E, B, L), Intermedios),
    dump_list('{intermedios_file}', Intermedios),
    
    findall([A, E, B], evento_especial_rule(A, E, B, L), EvEsp),
    dump_list('{ev_esp_file}', EvEsp),
    
    findall([A, E, B], final_especial_rule(A, E, B, L), FinEsp),
    dump_list('{fin_esp_file}', FinEsp),
    halt.

:- initialization(run, main).
""";

        String runnerContent = runnerTemplate
                .replace("{obj_file}", objNorm)
                .replace("{kb_file}", kbNorm)
                .replace("{inicios_file}", iniciosNorm)
                .replace("{finales_file}", finalesNorm)
                .replace("{intermedios_file}", intermediosNorm)
                .replace("{ev_esp_file}", evEspNorm)
                .replace("{fin_esp_file}", finEspNorm);

        Path runnerFile = outputDir.resolve("dump_runner.pl");
        Files.writeString(runnerFile, runnerContent);

        runProcess("swipl", "-q", "-s", normalizePath(runnerFile.toString()));
    }

    private List<String> loadEventsJson(Path folder, String name) throws IOException {
        Path p = folder.resolve(name);
        if (!Files.exists(p)) return Collections.emptyList();
        String[][] list = JSON_MAPPER.readValue(Files.readString(p), String[][].class);
        List<String> result = new ArrayList<>(list.length);
        for (String[] trip : list) {
            result.add(String.format("'%s',%s,'%s'", trip[0], trip[1], trip[2]));
        }
        return result;
    }

    private static String eventSource(String ev) {
        int idx = ev.indexOf("',");
        return idx >= 0 ? ev.substring(0, idx + 1) : ev.split(",")[0];
    }

    private static String eventRelation(String ev) {
        int firstIdx = ev.indexOf("',");
        int lastIdx = ev.lastIndexOf(",'");
        if (firstIdx >= 0 && lastIdx > firstIdx) {
            return ev.substring(firstIdx + 2, lastIdx);
        }
        return ev.split(",")[1];
    }

    private static String eventDest(String ev) {
        int idx = ev.lastIndexOf(",'");
        return idx >= 0 ? ev.substring(idx + 1) : ev.split(",")[2];
    }

    private static String unquoteAtom(String quoted) {
        if (quoted == null) return "";
        String s = quoted.trim();
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\'", "'").replace("\\\\", "\\");
    }

    private static String normalizeObjectName(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("\\'", "'").replace(";", ",");
    }

    private String classifyEvent(String rel) {
        if ("cause".equals(rel) || "positive_correlation".equals(rel)) {
            return "positive_correlation";
        } else if ("inhibition".equals(rel) || "negative_correlation".equals(rel)) {
            return "negative_correlation";
        } else if ("association".equals(rel)) {
            return "association";
        } else if ("bind".equals(rel)) {
            return "bind";
        }
        return rel;
    }

    private List<String> listObjectsPatron(String patron) {
        List<String> res = new ArrayList<>();
        String[] events = patron.split(";");
        for (String ev : events) {
            if (ev.isBlank()) continue;
            String first = eventSource(ev);
            String second = eventDest(ev);
            if (!res.contains(first)) {
                res.add(first);
            }
            if (!res.contains(second)) {
                res.add(second);
            }
        }
        return res;
    }

    private void intermediosDFSLegacy(
            List<String> enlista,
            String objini,
            String patron,
            int maxDepth,
            List<String> cierreList,
            Map<String, List<String>> intermediosMap,
            List<String> ftList,
            List<String> listaInicio,
            List<String> listaFin,
            List<String> objC,
            List<String> generatedPathways,
            List<List<String>> generatedObjects) {

        List<String> lista = new ArrayList<>(enlista);
        lista.add(objini);

        List<String> solutions = intermediosMap.getOrDefault(objini, Collections.emptyList());
        for (String even : solutions) {
            String[] separa = even.split(",");
            String dest = separa[2];

            List<String> cierre = new ArrayList<>();
            for (String x : cierreList) {
                if (!x.equals(dest)) {
                    cierre.add(x);
                }
            }

            for (String factorT : ftList) {
                if (dest.equals(factorT) && !lista.contains(dest) && !cierre.isEmpty()) {
                    List<String> listaAux = new ArrayList<>(lista);
                    listaAux.add(dest);
                    encadenarPatronLegacy(patron + ";" + even, listaAux, listaInicio, listaFin, objC, generatedPathways, generatedObjects);
                }
            }

            if (!lista.contains(dest) && !cierre.isEmpty()) {
                String patronaux = patron + ";" + even;
                if (maxDepth < 10) {
                    intermediosDFSLegacy(lista, dest, patronaux, maxDepth + 1, cierre, intermediosMap, ftList, listaInicio, listaFin, objC, generatedPathways, generatedObjects);
                }
            }
        }
    }

    private void encadenarPatronLegacy(
            String patron,
            List<String> enlista,
            List<String> listaInicio,
            List<String> listaFin,
            List<String> objC,
            List<String> generatedPathways,
            List<List<String>> generatedObjects) {

        String[] sep = patron.split(",");
        String primero = sep[0].replace(";", "");
        String ultimo = sep[sep.length - 1];

        List<String> patrones = new ArrayList<>();
        for (String objin : listaInicio) {
            String[] sep1 = objin.split(",");
            if (primero.equals(sep1[2]) && !enlista.contains(sep1[0])) {
                String pataux = objin + patron;
                if (!patrones.contains(pataux)) {
                    patrones.add(pataux);
                }
            }
        }

        for (String p : patrones) {
            for (String fin : listaFin) {
                String[] sep2 = fin.split(",");
                if (ultimo.equals(sep2[0]) && p != null) {
                    String patronF = p + ";" + fin;
                    List<String> objs = listObjectsPatron(patronF);
                    String lastObj = objs.get(objs.size() - 1);
                    for (String cierreObj : objC) {
                        if (cierreObj.equals(lastObj)) {
                            generatedPathways.add(patronF);
                            generatedObjects.add(objs);
                        }
                    }
                }
            }
        }
    }

    /**
     * DFS optimizado O(1) con indexación de eventos de inicio/fin, control de profundidad configurable,
     * límite máximo de rutas y timeout de ejecución.
     */
    private void intermediosDFSOptimized(
            Set<String> visitedInPath,
            String objini,
            String patron,
            int currentDepth,
            int maxAllowedDepth,
            List<String> objCierre,
            Map<String, List<String>> intermediosMap,
            Set<String> ftSet,
            Map<String, List<String>> iniciosByDest,
            Map<String, List<String>> finalesBySrc,
            Set<String> objCSet,
            Set<String> uniquePathways,
            long deadlineMs,
            AtomicBoolean limitReached,
            AtomicLong dfsVisitedNodes) {

        if (limitReached.get()) {
            return;
        }
        long visitedCount = dfsVisitedNodes.incrementAndGet();
        if ((visitedCount & 0x3FFL) == 0L && System.currentTimeMillis() > deadlineMs) {
            limitReached.set(true);
            return;
        }
        if (uniquePathways.size() >= maxPathwaysLimit) {
            limitReached.set(true);
            return;
        }

        visitedInPath.add(objini);

        List<String> solutions = intermediosMap.getOrDefault(objini, Collections.emptyList());
        for (String even : solutions) {
            if (limitReached.get()) {
                break;
            }
            String dest = eventDest(even);

            if (visitedInPath.contains(dest)) {
                continue;
            }

            if (!hasRemainingClosingObject(objCierre, visitedInPath, dest)) {
                continue;
            }

            String nextPatron = patron + ";" + even;

            if (ftSet.contains(dest)) {
                visitedInPath.add(dest);
                encadenarPatronOptimized(nextPatron, visitedInPath, iniciosByDest, finalesBySrc, objCSet, uniquePathways, limitReached);
                visitedInPath.remove(dest);
                if (limitReached.get()) {
                    break;
                }
            }

            if (currentDepth < maxAllowedDepth) {
                intermediosDFSOptimized(
                        visitedInPath,
                        dest,
                        nextPatron,
                        currentDepth + 1,
                        maxAllowedDepth,
                        objCierre,
                        intermediosMap,
                        ftSet,
                        iniciosByDest,
                        finalesBySrc,
                        objCSet,
                        uniquePathways,
                        deadlineMs,
                        limitReached,
                        dfsVisitedNodes
                );
            }
        }

        visitedInPath.remove(objini);
    }

    private boolean hasRemainingClosingObject(List<String> objCierre, Set<String> visitedInPath, String currentDest) {
        if (objCierre.isEmpty()) {
            return false;
        }
        // Si hay más objetos de cierre que la cantidad total de nodos visitados + 1, siempre queda al menos uno libre
        if (objCierre.size() > visitedInPath.size() + 1) {
            return true;
        }
        for (String c : objCierre) {
            if (!c.equals(currentDest) && !visitedInPath.contains(c)) {
                return true;
            }
        }
        return false;
    }

    private void encadenarPatronOptimized(
            String patron,
            Set<String> visitedInPath,
            Map<String, List<String>> iniciosByDest,
            Map<String, List<String>> finalesBySrc,
            Set<String> objCSet,
            Set<String> uniquePathways,
            AtomicBoolean limitReached) {

        int firstSemi = patron.indexOf(';', 1);
        String firstEv = firstSemi > 0 ? patron.substring(1, firstSemi) : patron.substring(1);
        String primero = eventSource(firstEv);
        int lastSemi = patron.lastIndexOf(';');
        String lastEv = lastSemi >= 0 ? patron.substring(lastSemi + 1) : patron;
        String ultimo = eventDest(lastEv);

        List<String> candidateInicios = iniciosByDest.getOrDefault(primero, Collections.emptyList());
        if (candidateInicios.isEmpty()) {
            return;
        }

        List<String> candidateFinales = finalesBySrc.getOrDefault(ultimo, Collections.emptyList());
        if (candidateFinales.isEmpty()) {
            return;
        }

        List<String> patrones = new ArrayList<>();
        Set<String> patronesSeen = new HashSet<>();
        for (String objin : candidateInicios) {
            String startSrc = eventSource(objin);
            if (!visitedInPath.contains(startSrc)) {
                String pataux = objin + patron;
                if (patronesSeen.add(pataux)) {
                    patrones.add(pataux);
                }
            }
        }

        for (String p : patrones) {
            for (String fin : candidateFinales) {
                String patronF = p + ";" + fin;
                List<String> objs = listObjectsPatron(patronF);
                String lastObj = objs.get(objs.size() - 1);
                if (objCSet.contains(lastObj)) {
                    uniquePathways.add(patronF);
                    if (uniquePathways.size() >= maxPathwaysLimit) {
                        limitReached.set(true);
                        return;
                    }
                }
            }
        }
    }

    private Path getLegacyDir() {
        Path legacyDir = Paths.get("src/main/resources/legacy").toAbsolutePath();
        if (!Files.exists(legacyDir)) {
            legacyDir = Paths.get("target/classes/legacy").toAbsolutePath();
        }
        return legacyDir;
    }

    private Path getOutputDir() {
        return Paths.get(outputDirName).toAbsolutePath();
    }

    private String normalizePath(String pathStr) {
        if (pathStr == null) return "";
        Path p = Paths.get(pathStr).toAbsolutePath();
        String formatted = p.toString().replace("\\", "/");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows && formatted.matches("^[A-Za-z]:/.*")) {
            if (formatted.toLowerCase().startsWith("z:/")) {
                formatted = formatted.substring(2);
            } else {
                char drive = Character.toLowerCase(formatted.charAt(0));
                formatted = "/mnt/" + drive + formatted.substring(2);
            }
        }
        return formatted;
    }

    private void runProcess(String executable, String... args) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> commandList = new ArrayList<>();
        if (isWindows) {
            commandList.add("wsl");
        }
        commandList.add(executable);
        commandList.addAll(Arrays.asList(args));

        log.debug("Executing CLI process: {}", commandList);
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        List<String> outputLines = new ArrayList<>();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
                log.debug("[CLI] {}", line);
            }
        }
        boolean completed = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            p.destroyForcibly();
            throw new TimeoutException("CLI process timed out after " + timeoutSeconds + "s");
        }
        if (p.exitValue() != 0) {
            String err = String.join("\n", outputLines);
            log.error("CLI process failed with exit code {}: {}", p.exitValue(), err);
            throw new IllegalStateException("CLI error (exit code " + p.exitValue() + "): " + err);
        }
    }

    private String escapePrologAtom(String value) {
        if (value == null) return "";
        String normalized = normalizeObjectName(value);
        return normalized.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Override
    public DynamicExecutionResult runDynamicInference(DynamicInferenceInput input) {
        if (input == null) {
            throw new IllegalArgumentException("DynamicInferenceInput cannot be null");
        }
        long startTime = System.currentTimeMillis();
        long deadlineMs = startTime + (executionTimeoutSeconds * 1000L);
        boolean acquired = false;
        Path ramDir = null;
        String pipelineId = input.getPipelineId() != null ? input.getPipelineId() : "anon";
        int effectiveMaxDepth = input.getMaxDepth() > 0
                ? input.getMaxDepth()
                : DEFAULT_MAX_DEPTH;

        try {
            acquired = concurrencyLimiter.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new TimeoutException("Prolog inference concurrency limit reached (timeout waiting for slot)");
            }

            // 1. Crear directorio en RAM (/dev/shm si está disponible)
            Path shm = Paths.get("/dev/shm");
            String uniqueName = "inferences_run_" + pipelineId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (Files.exists(shm) && Files.isWritable(shm)) {
                ramDir = shm.resolve(uniqueName);
                Files.createDirectories(ramDir);
            } else {
                ramDir = Files.createTempDirectory(uniqueName);
            }

            // 2. Resolver roles principales y generar pathwaysObjects.pl + hechos indexados
            Map<String, String> primaryRoleMap = new LinkedHashMap<>();
            StringBuilder objectsPl = new StringBuilder();
            objectsPl.append(":- style_check(-discontiguous).\n");
            objectsPl.append(":- dynamic protein/1, enzyme/1, ligand/1, receptor/1, transcription_factor/1, adaptor_protein/1, disease/1, restricted_obj/1, closing_obj/1.\n");
            objectsPl.append(":- discontiguous protein/1, enzyme/1, ligand/1, receptor/1, transcription_factor/1, adaptor_protein/1, disease/1, restricted_obj/1, closing_obj/1.\n\n");

            Set<String> allObjectNames = new LinkedHashSet<>();
            int roleFactsCount = 0;
            if (input.getObjects() != null) {
                for (KbObject obj : input.getObjects()) {
                    if (obj.getName() == null || obj.getName().isBlank()) continue;
                    String name = normalizeObjectName(obj.getName());
                    String escapedName = escapePrologAtom(name);
                    allObjectNames.add(name);
                    String primaryRole = resolvePrimaryRole(obj.getRoles());
                    primaryRoleMap.put(name, primaryRole);

                    List<String> roles = obj.getRoles() != null ? obj.getRoles() : List.of();
                    for (String r : roles) {
                        String cleanR = r.toLowerCase().trim().replace("-", "_").replace(" ", "_");
                        if (cleanR.equals("ligand")) {
                            objectsPl.append("ligand('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("receptor")) {
                            objectsPl.append("receptor('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("enzyme")) {
                            objectsPl.append("enzyme('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("transcription_factor") || cleanR.equals("tf")) {
                            objectsPl.append("transcription_factor('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("adaptor_protein")) {
                            objectsPl.append("adaptor_protein('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("disease")) {
                            objectsPl.append("disease('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("gene")) {
                            objectsPl.append("gene('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        } else if (cleanR.equals("variant")) {
                            objectsPl.append("variant('").append(escapedName).append("').\n");
                            roleFactsCount++;
                        }
                    }
                    objectsPl.append("protein('").append(escapedName).append("').\n");
                    roleFactsCount++;
                }
            }

            // 3. Procesar eventos (resumir o usar directos) y generar kBase_optimized.pl
            List<PathwayEvent> sourceEvents = input.getEvents() != null ? input.getEvents() : List.of();
            Map<String, String> eventIdMap = new LinkedHashMap<>();
            for (PathwayEvent ev : sourceEvents) {
                if (ev != null && ev.getId() != null) {
                    String f = normalizeObjectName(ev.getFirst());
                    String r = ev.getRelation() != null ? ev.getRelation().toLowerCase().trim().replace("-", "_").replace(" ", "_") : "";
                    String s = normalizeObjectName(ev.getSecond());
                    eventIdMap.putIfAbsent(f + "|" + r + "|" + s, ev.getId());
                    eventIdMap.putIfAbsent(f + "|" + normalizeRelation(r) + "|" + s, ev.getId());
                }
            }

            List<PathwayEvent> effectiveEvents = input.isSummarizeKb()
                    ? summarizeKnowledgeBase(sourceEvents)
                    : sourceEvents;

            StringBuilder kbPl = new StringBuilder();
            kbPl.append(":- style_check(-discontiguous).\n");
            kbPl.append(":- discontiguous event/3.\n\n");
            Set<String> seenEvents = new HashSet<>();
            for (PathwayEvent ev : effectiveEvents) {
                if (ev.getFirst() == null || ev.getRelation() == null || ev.getSecond() == null) continue;
                String f = normalizeObjectName(ev.getFirst());
                String r = ev.getRelation().toLowerCase().trim().replace("-", "_").replace(" ", "_");
                String s = normalizeObjectName(ev.getSecond());
                allObjectNames.add(f);
                allObjectNames.add(s);
                primaryRoleMap.putIfAbsent(f, "protein");
                primaryRoleMap.putIfAbsent(s, "protein");

                String key = f + "|" + r + "|" + s;
                if (seenEvents.add(key)) {
                    kbPl.append("event('").append(escapePrologAtom(f)).append("', ")
                            .append(r).append(", '")
                            .append(escapePrologAtom(s)).append("').\n");
                }
            }

            // 4. Determinar moléculas restringidas e inyectarlas como hechos indexados O(1) en Prolog
            List<String> restricted = input.getRestrictedObjects() != null && !input.getRestrictedObjects().isEmpty()
                    ? input.getRestrictedObjects()
                    : new ArrayList<>(allObjectNames);
            Set<String> uniqueRestricted = new LinkedHashSet<>();
            for (String rObj : restricted) {
                if (rObj != null && !rObj.isBlank()) {
                    String trimmed = normalizeObjectName(rObj);
                    if (uniqueRestricted.add(trimmed)) {
                        objectsPl.append("restricted_obj('").append(escapePrologAtom(trimmed)).append("').\n");
                    }
                }
            }

            // 5. Configurar filtro opcional de objetos de cierre (closingObjects) como hechos indexados O(1)
            boolean hasClosingFilter = false;
            Set<String> uniqueClosing = new LinkedHashSet<>();
            if (input.getClosingObjects() != null && !input.getClosingObjects().isEmpty()) {
                for (String cObj : input.getClosingObjects()) {
                    if (cObj != null && !cObj.isBlank()) {
                        String trimmed = normalizeObjectName(cObj);
                        if (uniqueClosing.add(trimmed)) {
                            objectsPl.append("closing_obj('").append(escapePrologAtom(trimmed)).append("').\n");
                            hasClosingFilter = true;
                        }
                    }
                }
            }
            String closingFilter = hasClosingFilter ? "    closing_obj(B),\n" : "";

            Path objFile = ramDir.resolve("pathwaysObjects.pl");
            Files.writeString(objFile, objectsPl.toString());

            Path kbFile = ramDir.resolve("kBase_optimized.pl");
            Files.writeString(kbFile, kbPl.toString());

            log.info("[STEP 2/7 - PROLOG GEN] [pipelineId={}] Generated Prolog files in '{}': objects={} (roleFacts={}), restrictedObjects={}, closingObjects={}, sourceEvents={} -> effectiveEvents={} (summarizeKb={})",
                    pipelineId, ramDir, allObjectNames.size(), roleFactsCount, uniqueRestricted.size(),
                    uniqueClosing.size(), sourceEvents.size(), seenEvents.size(), input.isSummarizeKb());

            // 6. Generar dump_runner.pl con indexación O(1) sobre event/3
            Path iniciosFile = ramDir.resolve("inicios.json");
            Path finalesFile = ramDir.resolve("finales.json");
            Path intermediosFile = ramDir.resolve("intermedios.json");
            Path evEspFile = ramDir.resolve("evento_especial.json");
            Path finEspFile = ramDir.resolve("final_especial.json");

            String startFirstRolesExpr = buildStartRoleDisjunction(input.getStartFirstRoles(), "A", "ligand");
            String startSecondRolesExpr = buildStartRoleDisjunction(input.getStartSecondRoles(), "B", "protein");
            String intermediateFirstRolesExpr = buildStartRoleDisjunction(input.getIntermediateFirstRoles(), "A", "protein");
            String intermediateSecondRolesExpr = buildStartRoleDisjunction(input.getIntermediateSecondRoles(), "B", "protein");
            String endFirstRolesExpr = buildStartRoleDisjunction(input.getEndFirstRoles(), "A", "protein");
            String endSecondRolesExpr = buildStartRoleDisjunction(input.getEndSecondRoles(), "B", "protein");

            String runnerTemplate = """
:- style_check(-discontiguous).
:- use_module(library(http/json)).
:- consult('{obj_file}').
:- consult('{kb_file}').

restrictions_start_ligand(A, B) :-
    {start_first_roles},
    {start_second_roles},
    A \\= B.

inicio_rule(A, E, B) :-
    event(A, E, B),
    member(E, [association, bind]),
    restricted_obj(A),
    restricted_obj(B),
    restrictions_start_ligand(A, B).

final_rule(A, E, B) :-
    event(A, E, B),
{closing_filter}    member(E, [association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]),
    restricted_obj(A),
    restricted_obj(B),
    {end_first_roles},
    {end_second_roles},
    A \\= B.

intermedios_rule(A, E, B) :-
    event(A, E, B),
    member(E, [association]),
    restricted_obj(A),
    restricted_obj(B),
    {intermediate_first_roles},
    {intermediate_second_roles},
    A \\= B.

evento_especial_rule(A, E, B) :-
    event(A, E, B),
    restricted_obj(A),
    restricted_obj(B),
    member(E, [association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]).

final_especial_rule(A, E, B) :-
    event(A, E, B),
    restricted_obj(A),
    restricted_obj(B),
    member(E, [association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]).

dump_list(File, List) :-
    open(File, write, S, [encoding(utf8)]),
    json_write(S, List, [width(0)]),
    close(S).

run :-
    findall([A, E, B], inicio_rule(A, E, B), Inicios),
    dump_list('{inicios_file}', Inicios),
    
    findall([A, E, B], final_rule(A, E, B), Finales),
    dump_list('{finales_file}', Finales),
    
    findall([A, E, B], intermedios_rule(A, E, B), Intermedios),
    dump_list('{intermedios_file}', Intermedios),
    
    findall([A, E, B], evento_especial_rule(A, E, B), EvEsp),
    dump_list('{ev_esp_file}', EvEsp),
    
    findall([A, E, B], final_especial_rule(A, E, B), FinEsp),
    dump_list('{fin_esp_file}', FinEsp),
    halt.

:- initialization(run, main).
""";

            String runnerContent = runnerTemplate
                    .replace("{obj_file}", normalizePath(objFile.toString()))
                    .replace("{kb_file}", normalizePath(kbFile.toString()))
                    .replace("{start_first_roles}", startFirstRolesExpr)
                    .replace("{start_second_roles}", startSecondRolesExpr)
                    .replace("{intermediate_first_roles}", intermediateFirstRolesExpr)
                    .replace("{intermediate_second_roles}", intermediateSecondRolesExpr)
                    .replace("{end_first_roles}", endFirstRolesExpr)
                    .replace("{end_second_roles}", endSecondRolesExpr)
                    .replace("{closing_filter}", closingFilter)
                    .replace("{inicios_file}", normalizePath(iniciosFile.toString()))
                    .replace("{finales_file}", normalizePath(finalesFile.toString()))
                    .replace("{intermedios_file}", normalizePath(intermediosFile.toString()))
                    .replace("{ev_esp_file}", normalizePath(evEspFile.toString()))
                    .replace("{fin_esp_file}", normalizePath(finEspFile.toString()));

            Path runnerFile = ramDir.resolve("dump_runner.pl");
            Files.writeString(runnerFile, runnerContent);

            // 7. Ejecutar SWI-Prolog en el directorio RAM
            log.info("[STEP 3/7 - SWI-PROLOG EXEC] [pipelineId={}] Launching swipl process...", pipelineId);
            long prologStart = System.currentTimeMillis();
            runProcess("swipl", "-q", "-s", normalizePath(runnerFile.toString()));
            long prologElapsed = System.currentTimeMillis() - prologStart;
            log.info("[STEP 3/7 - SWI-PROLOG EXEC] [pipelineId={}] swipl completed in {} ms", pipelineId, prologElapsed);

            // 8. Cargar e indexar eventos biológicos en estructuras O(1)
            List<String> listaInicio = loadEventsJson(ramDir, "inicios.json");
            List<String> listaFin = loadEventsJson(ramDir, "finales.json");
            List<String> intermediosAll = loadEventsJson(ramDir, "intermedios.json");
            List<String> eventoEspecialAll = loadEventsJson(ramDir, "evento_especial.json");
            List<String> finalEspecialAll = loadEventsJson(ramDir, "final_especial.json");

            Map<String, List<String>> iniciosByDest = new LinkedHashMap<>();
            List<String> objEnlace = new ArrayList<>();
            Set<String> objEnlaceSeen = new HashSet<>();
            for (String ev : listaInicio) {
                String dest = eventDest(ev);
                iniciosByDest.computeIfAbsent(dest, k -> new ArrayList<>()).add(ev);
                if (objEnlaceSeen.add(dest)) {
                    objEnlace.add(dest);
                }
            }

            Map<String, List<String>> finalesBySrc = new LinkedHashMap<>();
            List<String> objCierre = new ArrayList<>();
            Set<String> objCSet = new LinkedHashSet<>();
            Set<String> ftSet = new LinkedHashSet<>();
            for (String ev : listaFin) {
                String s = eventSource(ev);
                String d = eventDest(ev);
                finalesBySrc.computeIfAbsent(s, k -> new ArrayList<>()).add(ev);
                ftSet.add(s);
                if (objCSet.add(d)) {
                    objCierre.add(d);
                }
            }

            Map<String, List<String>> intermediosMap = new LinkedHashMap<>();
            for (String ev : intermediosAll) {
                String src = eventSource(ev);
                intermediosMap.computeIfAbsent(src, k -> new ArrayList<>()).add(ev);
            }

            Map<String, List<String>> eventoEspecialMap = new LinkedHashMap<>();
            for (String ev : eventoEspecialAll) {
                String key = eventSource(ev) + "->" + eventDest(ev);
                eventoEspecialMap.computeIfAbsent(key, k -> new ArrayList<>()).add(eventRelation(ev));
            }

            Map<String, List<String>> finalEspecialMap = new LinkedHashMap<>();
            for (String ev : finalEspecialAll) {
                String key = eventSource(ev) + "->" + eventDest(ev);
                finalEspecialMap.computeIfAbsent(key, k -> new ArrayList<>()).add(eventRelation(ev));
            }

            log.info("[STEP 4/7 - EVENTS EXTRACTED] [pipelineId={}] inicios={}, finales={}, intermedios={}, eventoEspecial={}, finalEspecial={} | linkObjects={}, ftSources={}, closingTargets={}",
                    pipelineId, listaInicio.size(), listaFin.size(), intermediosAll.size(),
                    eventoEspecialAll.size(), finalEspecialAll.size(),
                    objEnlace.size(), ftSet.size(), objCierre.size());

            Set<String> uniquePathways = new LinkedHashSet<>();
            AtomicBoolean limitReached = new AtomicBoolean(false);

            // 9. Encadenamiento optimizado O(1) de 2-eventos y 3-eventos especiales
            long shortStart = System.currentTimeMillis();
            for (String E : objEnlace) {
                if (limitReached.get()) break;
                List<String> matchingInicios = iniciosByDest.getOrDefault(E, Collections.emptyList());
                if (matchingInicios.isEmpty()) continue;

                for (String F : objCierre) {
                    if (limitReached.get()) break;
                    List<String> specialEvents = eventoEspecialMap.getOrDefault(E + "->" + F, Collections.emptyList());
                    if (specialEvents.isEmpty()) continue;

                    for (String evento : specialEvents) {
                        String fin = E + "," + evento + "," + F;
                        String classified = classifyEvent(evento);
                        if ("interact".equals(evento) || "bind".equals(evento) || "regulate".equals(classified) || "inhibit".equals(classified)) {
                            for (String i : matchingInicios) {
                                String startSrc = eventSource(i);
                                if (!F.equals(startSrc)) {
                                    uniquePathways.add(i + ";" + fin);
                                    if (uniquePathways.size() >= maxPathwaysLimit) {
                                        limitReached.set(true);
                                        break;
                                    }
                                }
                            }
                        }
                        if (limitReached.get()) break;

                        for (String inEv : matchingInicios) {
                            String startSrc = eventSource(inEv);
                            List<String> feEvents = finalEspecialMap.getOrDefault(startSrc + "->" + F, Collections.emptyList());
                            for (String fe : feEvents) {
                                String feClassified = classifyEvent(fe);
                                if ("regulate".equals(feClassified) || "inhibit".equals(feClassified)) {
                                    String eFin = startSrc + "," + fe + "," + F;
                                    uniquePathways.add(inEv + ";" + fin + ";" + eFin);
                                    if (uniquePathways.size() >= maxPathwaysLimit) {
                                        limitReached.set(true);
                                        break;
                                    }
                                }
                            }
                            if (limitReached.get()) break;
                        }
                    }
                }
            }
            long shortElapsed = System.currentTimeMillis() - shortStart;
            log.info("[STEP 5/7 - SHORT PATTERNS] [pipelineId={}] Generated {} unique short pathways (2-3 events) in {} ms",
                    pipelineId, uniquePathways.size(), shortElapsed);

            // 10. Patrones multietapa con eventos intermedios (DFS optimizado con logs de progreso)
            long dfsStart = System.currentTimeMillis();
            long lastLogTime = dfsStart;
            AtomicLong dfsVisitedNodes = new AtomicLong(0L);
            log.info("[STEP 6/7 - DFS MULTI-STEP] [pipelineId={}] Starting multi-step DFS across {} link objects (effectiveMaxDepth={}, maxPathwaysLimit={}, timeout={}s)",
                    pipelineId, objEnlace.size(), effectiveMaxDepth, maxPathwaysLimit, executionTimeoutSeconds);

            for (int idx = 0; idx < objEnlace.size(); idx++) {
                if (limitReached.get() || System.currentTimeMillis() > deadlineMs) {
                    limitReached.set(true);
                    break;
                }
                String enlace = objEnlace.get(idx);
                Set<String> visitedInPath = new HashSet<>();
                intermediosDFSOptimized(
                        visitedInPath,
                        enlace,
                        "",
                        0,
                        effectiveMaxDepth,
                        objCierre,
                        intermediosMap,
                        ftSet,
                        iniciosByDest,
                        finalesBySrc,
                        objCSet,
                        uniquePathways,
                        deadlineMs,
                        limitReached,
                        dfsVisitedNodes
                );

                long now = System.currentTimeMillis();
                if (now - lastLogTime >= PROGRESS_LOG_INTERVAL_MS || idx == objEnlace.size() - 1) {
                    lastLogTime = now;
                    int pct = objEnlace.isEmpty() ? 100 : (int) ((idx + 1) * 100L / objEnlace.size());
                    log.info("[STEP 6/7 - DFS PROGRESS] [pipelineId={}] Link object {}/{} ({}%) | uniquePathways={} | dfsNodesVisited={} | elapsed={} ms",
                            pipelineId, idx + 1, objEnlace.size(), pct, uniquePathways.size(),
                            dfsVisitedNodes.get(), now - startTime);
                }
            }

            if (limitReached.get()) {
                log.warn("[STEP 6/7 - DFS LIMIT REACHED] [pipelineId={}] Search stopped safely at {} unique pathways (maxLimit={}, timeout={}s, elapsed={} ms)",
                        pipelineId, uniquePathways.size(), maxPathwaysLimit, executionTimeoutSeconds,
                        System.currentTimeMillis() - startTime);
            }

            long durationMs = System.currentTimeMillis() - startTime;

            // 11. Construir BiologicalPathway y PathwayGraph
            Map<String, Long> edgeCounts = new LinkedHashMap<>();
            Map<String, String> graphNodes = new LinkedHashMap<>();
            List<BiologicalPathway> domainPathways = new ArrayList<>(uniquePathways.size());

            for (String p : uniquePathways) {
                String[] segments = p.split(";");
                List<PathwayEvent> evList = new ArrayList<>(segments.length);
                List<PathwayObject> objList = new ArrayList<>();
                Set<String> pathwayObjNames = new LinkedHashSet<>();

                for (int pos = 0; pos < segments.length; pos++) {
                    String seg = segments[pos];
                    String first = unquoteAtom(eventSource(seg));
                    String rel = unquoteAtom(eventRelation(seg));
                    String second = unquoteAtom(eventDest(seg));
                    String eventKey = first + "|" + rel + "|" + second;
                    String eventId = eventIdMap.get(eventKey);
                    if (eventId == null) {
                        eventId = eventIdMap.get(first + "|" + normalizeRelation(rel) + "|" + second);
                    }
                    evList.add(PathwayEvent.builder()
                            .id(eventId)
                            .first(first)
                            .relation(rel)
                            .second(second)
                            .position(pos + 1)
                            .build());
                    pathwayObjNames.add(first);
                    pathwayObjNames.add(second);

                    String edgeKey = first + "|" + rel + "|" + second;
                    edgeCounts.put(edgeKey, edgeCounts.getOrDefault(edgeKey, 0L) + 1L);
                }

                for (String name : pathwayObjNames) {
                    String role = primaryRoleMap.getOrDefault(name, "protein");
                    objList.add(new PathwayObject(name, role));
                    graphNodes.putIfAbsent(name, role);
                }

                domainPathways.add(BiologicalPathway.builder()
                        .pipelineId(pipelineId)
                        .user(input.getUser())
                        .rawPattern(p)
                        .length(evList.size())
                        .startObject(evList.get(0).getFirst())
                        .endObject(evList.get(evList.size() - 1).getSecond())
                        .events(evList)
                        .objects(objList)
                        .build());
            }

            List<PathwayObject> nodes = graphNodes.entrySet().stream()
                    .map(e -> new PathwayObject(e.getKey(), e.getValue()))
                    .toList();

            List<GraphEdge> edges = edgeCounts.entrySet().stream()
                    .map(e -> {
                        String[] parts = e.getKey().split("\\|");
                        return new GraphEdge(parts[0], parts[1], parts[2], e.getValue());
                    })
                    .toList();

            PathwayGraph graph = PathwayGraph.builder()
                    .pipelineId(pipelineId)
                    .user(input.getUser())
                    .totalPathways(domainPathways.size())
                    .executionTimeMs(durationMs)
                    .nodes(nodes)
                    .edges(edges)
                    .build();

            log.info("[STEP 7/7 - GRAPH BUILT] [pipelineId={}] Dynamic inference completed in {} ms: {} pathways, {} nodes, {} edges",
                    pipelineId, durationMs, domainPathways.size(), nodes.size(), edges.size());

            return DynamicExecutionResult.builder()
                    .pipelineId(pipelineId)
                    .user(input.getUser())
                    .executionTimeMs(durationMs)
                    .generatedPathwaysCount(domainPathways.size())
                    .pathways(domainPathways)
                    .graph(graph)
                    .build();

        } catch (Throwable t) {
            log.error("[INFERENCE ERROR] [pipelineId={}] Failed executing dynamic inference in RAM-disk after {} ms: {}",
                    pipelineId, System.currentTimeMillis() - startTime, t.getMessage(), t);
            if (t instanceof Error err) {
                throw err;
            }
            throw new RuntimeException("Dynamic inference error: " + t.getMessage(), t);
        } finally {
            if (ramDir != null) {
                deleteDirectoryRecursively(ramDir);
            }
            if (acquired) {
                concurrencyLimiter.release();
            }
        }
    }

    @Override
    public DynamicExecutionResult runPipelineInference(
            String pipelineId,
            String user,
            List<String> restrictedObjects,
            List<String> closingObjects,
            boolean summarizeKb,
            Integer maxDepth) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId cannot be null or blank");
        }
        if (knowledgeBaseRepository == null || pathwayRepository == null) {
            throw new IllegalStateException("Repositories are not configured for pipeline inference");
        }

        if (inferenceRepository == null) {
            throw new IllegalStateException("InferenceRepository is not configured for pipeline inference");
        }
        Inference inference = inferenceRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("No inference configuration found in inferences collection for pipelineId: " + pipelineId));

        boolean effectiveSummarizeKb = summarizeKb || Boolean.TRUE.equals(inference.getSummarizeKb());
        int configuredMaxDepth = inference.getMaxDepth() != null && inference.getMaxDepth() > 0
                ? inference.getMaxDepth()
                : DEFAULT_MAX_DEPTH;
        int effectiveMaxDepth = maxDepth != null && maxDepth > 0 ? maxDepth : configuredMaxDepth;

        List<KbObject> objects = resolveBiologicalObjects(inference, pipelineId);
        if (objects.isEmpty()) {
            throw new IllegalArgumentException("No biological objects found in knowledge base for pipelineId: " + pipelineId);
        }

        String restrictionLevel = inference.getRestrictionLevel() != null ? inference.getRestrictionLevel() : "UNRESTRICTED";
        List<String> targetObjects = restrictedObjects != null && !restrictedObjects.isEmpty()
                ? restrictedObjects
                : objects.stream().map(KbObject::getName).toList();

        List<PathwayEvent> events = knowledgeBaseRepository.findEventsByPipelineIdAndRestriction(pipelineId, restrictionLevel, targetObjects);

        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("No biological events found in knowledge base for pipelineId: " + pipelineId);
        }

        List<String> configuredClosing = inference.getClosingObjects() != null
                ? inference.getClosingObjects()
                : List.of();
        List<String> effectiveClosing = closingObjects != null && !closingObjects.isEmpty()
                ? closingObjects
                : configuredClosing;

        List<String> effectiveStartFirstRoles = inference.getStartFirstRoles() != null && !inference.getStartFirstRoles().isEmpty()
                ? inference.getStartFirstRoles()
                : List.of("ligand");
        List<String> effectiveStartSecondRoles = inference.getStartSecondRoles() != null && !inference.getStartSecondRoles().isEmpty()
                ? inference.getStartSecondRoles()
                : List.of("protein");
        List<String> effectiveIntermediateFirstRoles = inference.getIntermediateFirstRoles() != null && !inference.getIntermediateFirstRoles().isEmpty()
                ? inference.getIntermediateFirstRoles()
                : List.of("protein");
        List<String> effectiveIntermediateSecondRoles = inference.getIntermediateSecondRoles() != null && !inference.getIntermediateSecondRoles().isEmpty()
                ? inference.getIntermediateSecondRoles()
                : List.of("protein");
        List<String> effectiveEndFirstRoles = inference.getEndFirstRoles() != null && !inference.getEndFirstRoles().isEmpty()
                ? inference.getEndFirstRoles()
                : List.of("protein");
        List<String> effectiveEndSecondRoles = inference.getEndSecondRoles() != null && !inference.getEndSecondRoles().isEmpty()
                ? inference.getEndSecondRoles()
                : List.of("protein");

        log.info("[STEP 1/7 - KB LOAD] [pipelineId={}] Starting production inference: user={}, restrictionLevel={}, objects={}, events={}, closingObjects={}, summarizeKb={}, maxDepth={}, startRoles=[{}, {}], intermediateRoles=[{}, {}], endRoles=[{}, {}]",
                pipelineId, user, restrictionLevel, objects.size(), events.size(),
                effectiveClosing.size(), effectiveSummarizeKb, effectiveMaxDepth,
                effectiveStartFirstRoles, effectiveStartSecondRoles,
                effectiveIntermediateFirstRoles, effectiveIntermediateSecondRoles,
                effectiveEndFirstRoles, effectiveEndSecondRoles);

        // 1. Borrar inmediatamente cualquier información de inferencia previa del pipeline (vías y grafo)
        long deletedPrevious = pathwayRepository.deleteByPipelineId(pipelineId);
        log.info("[STEP 1/7 - KB LOAD] [pipelineId={}] Cleared {} previous pathways before launching new inference",
                pipelineId, deletedPrevious);

        DynamicInferenceInput input = DynamicInferenceInput.builder()
                .pipelineId(pipelineId)
                .user(user)
                .events(events)
                .objects(objects)
                .restrictedObjects(restrictedObjects != null ? restrictedObjects : List.of())
                .closingObjects(effectiveClosing)
                .summarizeKb(effectiveSummarizeKb)
                .maxDepth(effectiveMaxDepth)
                .startFirstRoles(effectiveStartFirstRoles)
                .startSecondRoles(effectiveStartSecondRoles)
                .intermediateFirstRoles(effectiveIntermediateFirstRoles)
                .intermediateSecondRoles(effectiveIntermediateSecondRoles)
                .endFirstRoles(effectiveEndFirstRoles)
                .endSecondRoles(effectiveEndSecondRoles)
                .build();

        DynamicExecutionResult result = runDynamicInference(input);

        if (result.getPathways() != null && !result.getPathways().isEmpty()) {
            log.info("[STEP 7/7 - MONGO PERSISTENCE] [pipelineId={}] Persisting {} inferred pathways to MongoDB...",
                    pipelineId, result.getPathways().size());
            pathwayRepository.savePathwaysBatch(result.getPathways());
        } else {
            log.info("[STEP 7/7 - MONGO PERSISTENCE] [pipelineId={}] 0 pathways generated; skipping batch insert", pipelineId);
        }

        if (result.getGraph() != null) {
            log.info("[STEP 7/7 - MONGO PERSISTENCE] [pipelineId={}] Persisting pathway graph ({} nodes, {} edges) to MongoDB...",
                    pipelineId, result.getGraph().getNodes().size(), result.getGraph().getEdges().size());
            pathwayRepository.savePathwayGraph(result.getGraph());
        }

        log.info("[INFERENCE COMPLETE] [pipelineId={}] Production pipeline inference finished successfully in {} ms (pathways={})",
                pipelineId, result.getExecutionTimeMs(), result.getGeneratedPathwaysCount());

        return result;
    }

    @Override
    public Optional<PathwayGraph> getPathwayGraph(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Optional.empty();
        }
        if (pathwayRepository == null) {
            throw new IllegalStateException("PathwayRepository is not configured");
        }
        Optional<PathwayGraph> graphOpt = pathwayRepository.findGraphByPipelineId(pipelineId);
        if (graphOpt.isPresent() && inferenceRepository != null) {
            PathwayGraph graph = graphOpt.get();
            inferenceRepository.findByPipelineId(pipelineId).ifPresent(inference -> {
                Map<String, List<String>> rolesMap = inference.getRoles();
                if (rolesMap != null && !rolesMap.isEmpty() && graph.getNodes() != null) {
                    for (PathwayObject node : graph.getNodes()) {
                        if (node != null && node.getName() != null) {
                            List<String> configuredRoles = rolesMap.get(node.getName());
                            if (configuredRoles != null && !configuredRoles.isEmpty()) {
                                node.setRole(resolvePrimaryRole(configuredRoles));
                            }
                        }
                    }
                }
            });
        }
        return graphOpt;
    }

    @Override
    public List<BiologicalPathway> getPathways(String pipelineId, String startObject, String endObject, String containsObject, int page, int size) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return List.of();
        }
        if (pathwayRepository == null) {
            throw new IllegalStateException("PathwayRepository is not configured");
        }
        return pathwayRepository.findByPipelineId(pipelineId, startObject, endObject, containsObject, page, size);
    }

    @Override
    public long countPathways(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return 0L;
        }
        if (pathwayRepository == null) {
            throw new IllegalStateException("PathwayRepository is not configured");
        }
        return pathwayRepository.countByPipelineId(pipelineId);
    }

    @Override
    public long deletePathways(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return 0L;
        }
        if (pathwayRepository == null) {
            throw new IllegalStateException("PathwayRepository is not configured");
        }
        return pathwayRepository.deleteByPipelineId(pipelineId);
    }

    public static String resolvePrimaryRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "protein";
        }
        List<String> lower = roles.stream().map(String::toLowerCase).toList();
        if (lower.contains("enzyme")) return "enzyme";
        if (lower.contains("ligand")) return "ligand";
        if (lower.contains("receptor")) return "receptor";
        if (lower.contains("transcription_factor") || lower.contains("tf")) return "transcription_factor";
        if (lower.contains("adaptor_protein")) return "adaptor_protein";
        if (lower.contains("disease")) return "disease";
        if (lower.contains("variant")) return "variant";
        if (lower.contains("gene")) return "gene";
        return "protein";
    }

    public static String toPrologRolePredicate(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String clean = role.toLowerCase().trim().replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "ligand" -> "ligand";
            case "receptor" -> "receptor";
            case "enzyme" -> "enzyme";
            case "transcription_factor", "tf" -> "transcription_factor";
            case "adaptor_protein" -> "adaptor_protein";
            case "disease" -> "disease";
            case "variant" -> "variant";
            case "gene" -> "gene";
            case "protein" -> "protein";
            default -> null;
        };
    }

    public static String buildStartRoleDisjunction(List<String> roles, String varName, String defaultPredicate) {
        List<String> predicates = new ArrayList<>();
        if (roles != null) {
            for (String r : roles) {
                String pred = toPrologRolePredicate(r);
                if (pred != null && !predicates.contains(pred)) {
                    predicates.add(pred);
                }
            }
        }
        if (predicates.isEmpty()) {
            predicates.add(defaultPredicate);
        }
        if (predicates.size() == 1) {
            return predicates.get(0) + "(" + varName + ")";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < predicates.size(); i++) {
            if (i > 0) {
                sb.append(" ; ");
            }
            sb.append(predicates.get(i)).append("(").append(varName).append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    public static List<PathwayEvent> summarizeKnowledgeBase(List<PathwayEvent> events) {
        if (events == null) return Collections.emptyList();
        Map<String, PathwayEvent> uniqueEvents = new LinkedHashMap<>();
        for (PathwayEvent ev : events) {
            String normalizedRel = normalizeRelation(ev.getRelation());
            String key = ev.getFirst() + "|" + normalizedRel + "|" + ev.getSecond();
            uniqueEvents.putIfAbsent(key, PathwayEvent.builder()
                    .id(ev.getId())
                    .first(ev.getFirst())
                    .relation(normalizedRel)
                    .second(ev.getSecond())
                    .build());
        }
        return new ArrayList<>(uniqueEvents.values());
    }

    public static String normalizeRelation(String rel) {
        if (rel == null) return "association";
        String r = rel.toLowerCase().trim().replace("-", "_").replace(" ", "_");
        switch (r) {
            case "cause":
            case "induce":
            case "stimulate":
            case "stimulation":
            case "activate":
            case "up_regulate":
            case "promote":
            case "enhance":
            case "increase":
                return "positive_correlation";
            case "inhibit":
            case "inhibition":
            case "suppress":
            case "down_regulate":
            case "repress":
            case "block":
            case "decrease":
            case "prevent":
            case "prevention":
            case "reduce":
                return "negative_correlation";
            case "interact":
            case "complex":
            case "colocalize":
                return "bind";
            default:
                return r;
        }
    }

    private List<KbObject> resolveBiologicalObjects(Inference inference, String pipelineId) {
        if (inference == null) {
            throw new IllegalArgumentException("No inference configuration found in inferences collection for pipelineId: " + pipelineId);
        }
        Map<String, List<String>> rolesMap = inference.getRoles();
        if (rolesMap == null || rolesMap.isEmpty()) {
            throw new IllegalArgumentException("No biological objects with roles found in inferences collection for pipelineId: " + pipelineId);
        }

        log.info("Loaded {} biological objects with roles strictly from inferences collection for pipelineId: {}", rolesMap.size(), pipelineId);
        return rolesMap.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .map(e -> KbObject.builder()
                        .pipelineId(pipelineId)
                        .name(e.getKey().trim())
                        .roles(e.getValue() != null ? e.getValue() : List.of())
                        .build())
                .toList();
    }

    private void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        } catch (Exception e) {
            log.warn("Could not cleanly delete directory {}: {}", dir, e.getMessage());
        }
    }
}
