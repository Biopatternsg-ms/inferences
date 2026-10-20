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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@ApplicationScoped
public class InferPathwaysUseCase implements InferPathways {

    private final PrologInferencePort prologInferencePort;
    private final PathwayRepository pathwayRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final InferenceRepository inferenceRepository;
    private final String outputDirName;
    private final Semaphore concurrencyLimiter;
    private final int timeoutSeconds;

    @Inject
    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            PathwayRepository pathwayRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            InferenceRepository inferenceRepository,
            @ConfigProperty(name = "inferences.output.dir", defaultValue = "output") String outputDirName,
            @ConfigProperty(name = "inferences.prolog.concurrency", defaultValue = "4") int maxConcurrency,
            @ConfigProperty(name = "inferences.prolog.timeout.seconds", defaultValue = "60") int timeoutSeconds) {
        this.prologInferencePort = prologInferencePort;
        this.pathwayRepository = pathwayRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.inferenceRepository = inferenceRepository;
        this.outputDirName = outputDirName;
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.timeoutSeconds = timeoutSeconds;
    }

    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            PathwayRepository pathwayRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            String outputDirName,
            int maxConcurrency,
            int timeoutSeconds) {
        this(prologInferencePort, pathwayRepository, knowledgeBaseRepository, null, outputDirName, maxConcurrency, timeoutSeconds);
    }

    public InferPathwaysUseCase(
            PrologInferencePort prologInferencePort,
            String outputDirName,
            int maxConcurrency,
            int timeoutSeconds) {
        this(prologInferencePort, null, null, null, outputDirName, maxConcurrency, timeoutSeconds);
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
                intermediosDFS(new ArrayList<>(), enlace, "", 0, objCierre,
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

write_json_events([], _).
write_json_events([[A, E, B]], S) :-
    !,
    format(S, '["~w", "~w", "~w"]', [A, E, B]).
write_json_events([[A, E, B]|Rest], S) :-
    format(S, '["~w", "~w", "~w"],', [A, E, B]),
    write_json_events(Rest, S).

dump_list(File, List) :-
    open(File, write, S),
    write(S, '['),
    write_json_events(List, S),
    write(S, ']'),
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
        ObjectMapper mapper = new ObjectMapper();
        String[][] list = mapper.readValue(Files.readString(p), String[][].class);
        List<String> result = new ArrayList<>(list.length);
        for (String[] trip : list) {
            result.add(String.format("'%s',%s,'%s'", trip[0], trip[1], trip[2]));
        }
        return result;
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
            String[] parts = ev.split(",");
            if (!res.contains(parts[0])) {
                res.add(parts[0]);
            }
            if (!res.contains(parts[2])) {
                res.add(parts[2]);
            }
        }
        return res;
    }

    private void intermediosDFS(
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
                    encadenarPatron(patron + ";" + even, listaAux, listaInicio, listaFin, objC, generatedPathways, generatedObjects);
                }
            }

            if (!lista.contains(dest) && !cierre.isEmpty()) {
                String patronaux = patron + ";" + even;
                if (maxDepth < 10) {
                    intermediosDFS(lista, dest, patronaux, maxDepth + 1, cierre, intermediosMap, ftList, listaInicio, listaFin, objC, generatedPathways, generatedObjects);
                }
            }
        }
    }

    private void encadenarPatron(
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
        boolean completed = p.waitFor(60, TimeUnit.SECONDS);
        if (!completed) {
            p.destroyForcibly();
            throw new TimeoutException("CLI process timed out after 60s");
        }
        if (p.exitValue() != 0) {
            String err = String.join("\n", outputLines);
            log.error("CLI process failed with exit code {}: {}", p.exitValue(), err);
            throw new IllegalStateException("CLI error (exit code " + p.exitValue() + "): " + err);
        }
    }

    @Override
    public DynamicExecutionResult runDynamicInference(DynamicInferenceInput input) {
        if (input == null) {
            throw new IllegalArgumentException("DynamicInferenceInput cannot be null");
        }
        long startTime = System.currentTimeMillis();
        boolean acquired = false;
        Path ramDir = null;

        try {
            acquired = concurrencyLimiter.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new TimeoutException("Prolog inference concurrency limit reached (timeout waiting for slot)");
            }

            // 1. Crear directorio en RAM (/dev/shm si está disponible)
            Path shm = Paths.get("/dev/shm");
            String pipelineId = input.getPipelineId() != null ? input.getPipelineId() : "anon";
            String uniqueName = "inferences_run_" + pipelineId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (Files.exists(shm) && Files.isWritable(shm)) {
                ramDir = shm.resolve(uniqueName);
                Files.createDirectories(ramDir);
            } else {
                ramDir = Files.createTempDirectory(uniqueName);
            }

            // 2. Resolver roles principales y generar pathwaysObjects.pl
            Map<String, String> primaryRoleMap = new LinkedHashMap<>();
            StringBuilder objectsPl = new StringBuilder();
            objectsPl.append(":- style_check(-discontiguous).\n");
            objectsPl.append(":- discontiguous protein/1, enzyme/1, ligand/1, receptor/1, transcription_factor/1.\n\n");

            Set<String> allObjectNames = new LinkedHashSet<>();
            if (input.getObjects() != null) {
                for (KbObject obj : input.getObjects()) {
                    if (obj.getName() == null || obj.getName().isBlank()) continue;
                    String name = obj.getName().trim();
                    allObjectNames.add(name);
                    String primaryRole = resolvePrimaryRole(obj.getRoles());
                    primaryRoleMap.put(name, primaryRole);

                    List<String> roles = obj.getRoles() != null ? obj.getRoles() : List.of();
                    for (String r : roles) {
                        String cleanR = r.toLowerCase().trim();
                        if (cleanR.equals("ligand")) {
                            objectsPl.append("ligand('").append(name).append("').\n");
                        } else if (cleanR.equals("receptor")) {
                            objectsPl.append("receptor('").append(name).append("').\n");
                        } else if (cleanR.equals("enzyme")) {
                            objectsPl.append("enzyme('").append(name).append("').\n");
                        } else if (cleanR.equals("transcription_factor") || cleanR.equals("tf")) {
                            objectsPl.append("transcription_factor('").append(name).append("').\n");
                        }
                    }
                    objectsPl.append("protein('").append(name).append("').\n");
                }
            }

            Path objFile = ramDir.resolve("pathwaysObjects.pl");
            Files.writeString(objFile, objectsPl.toString());

            // 3. Procesar eventos (resumir o usar directos) y generar kBase_optimized.pl
            List<PathwayEvent> sourceEvents = input.getEvents() != null ? input.getEvents() : List.of();
            List<PathwayEvent> effectiveEvents = input.isSummarizeKb()
                    ? summarizeKnowledgeBase(sourceEvents)
                    : sourceEvents;

            StringBuilder kbPl = new StringBuilder();
            kbPl.append(":- style_check(-discontiguous).\n");
            kbPl.append(":- discontiguous event/3.\n\n");
            Set<String> seenEvents = new HashSet<>();
            for (PathwayEvent ev : effectiveEvents) {
                if (ev.getFirst() == null || ev.getRelation() == null || ev.getSecond() == null) continue;
                String f = ev.getFirst().trim();
                String r = ev.getRelation().toLowerCase().trim().replace("-", "_").replace(" ", "_");
                String s = ev.getSecond().trim();
                allObjectNames.add(f);
                allObjectNames.add(s);
                primaryRoleMap.putIfAbsent(f, "protein");
                primaryRoleMap.putIfAbsent(s, "protein");

                String key = f + "|" + r + "|" + s;
                if (seenEvents.add(key)) {
                    kbPl.append("event('").append(f).append("', ").append(r).append(", '").append(s).append("').\n");
                }
            }

            Path kbFile = ramDir.resolve("kBase_optimized.pl");
            Files.writeString(kbFile, kbPl.toString());

            // 4. Determinar lista L de moléculas restringidas
            List<String> restricted = input.getRestrictedObjects() != null && !input.getRestrictedObjects().isEmpty()
                    ? input.getRestrictedObjects()
                    : new ArrayList<>(allObjectNames);

            StringBuilder restrictedPrologList = new StringBuilder("[");
            for (int i = 0; i < restricted.size(); i++) {
                if (i > 0) restrictedPrologList.append(", ");
                restrictedPrologList.append("'").append(restricted.get(i).trim()).append("'");
            }
            restrictedPrologList.append("]");

            // 5. Configurar filtro opcional de objetos de cierre (closingObjects)
            String closingFilter = "";
            if (input.getClosingObjects() != null && !input.getClosingObjects().isEmpty()) {
                StringBuilder closingListStr = new StringBuilder("[");
                for (int i = 0; i < input.getClosingObjects().size(); i++) {
                    if (i > 0) closingListStr.append(", ");
                    closingListStr.append("'").append(input.getClosingObjects().get(i).trim()).append("'");
                }
                closingListStr.append("]");
                closingFilter = "    member(B, " + closingListStr + "),\n";
            }

            // 6. Generar dump_runner.pl
            Path iniciosFile = ramDir.resolve("inicios.json");
            Path finalesFile = ramDir.resolve("finales.json");
            Path intermediosFile = ramDir.resolve("intermedios.json");
            Path evEspFile = ramDir.resolve("evento_especial.json");
            Path finEspFile = ramDir.resolve("final_especial.json");

            String runnerTemplate = """
:- style_check(-discontiguous).
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
{closing_filter}    event(A, E, B),
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

write_json_events([], _).
write_json_events([[A, E, B]], S) :-
    !,
    format(S, '["~w", "~w", "~w"]', [A, E, B]).
write_json_events([[A, E, B]|Rest], S) :-
    format(S, '["~w", "~w", "~w"],', [A, E, B]),
    write_json_events(Rest, S).

dump_list(File, List) :-
    open(File, write, S),
    write(S, '['),
    write_json_events(List, S),
    write(S, ']'),
    close(S).

run :-
    L = {restricted_objects},
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
                    .replace("{obj_file}", normalizePath(objFile.toString()))
                    .replace("{kb_file}", normalizePath(kbFile.toString()))
                    .replace("{closing_filter}", closingFilter)
                    .replace("{restricted_objects}", restrictedPrologList.toString())
                    .replace("{inicios_file}", normalizePath(iniciosFile.toString()))
                    .replace("{finales_file}", normalizePath(finalesFile.toString()))
                    .replace("{intermedios_file}", normalizePath(intermediosFile.toString()))
                    .replace("{ev_esp_file}", normalizePath(evEspFile.toString()))
                    .replace("{fin_esp_file}", normalizePath(finEspFile.toString()));

            Path runnerFile = ramDir.resolve("dump_runner.pl");
            Files.writeString(runnerFile, runnerContent);

            // 7. Ejecutar SWI-Prolog en el directorio RAM
            runProcess("swipl", "-q", "-s", normalizePath(runnerFile.toString()));

            // 8. Cargar e indexar eventos biológicos
            List<String> listaInicio = loadEventsJson(ramDir, "inicios.json");
            List<String> listaFin = loadEventsJson(ramDir, "finales.json");
            List<String> intermediosAll = loadEventsJson(ramDir, "intermedios.json");
            List<String> eventoEspecialAll = loadEventsJson(ramDir, "evento_especial.json");
            List<String> finalEspecialAll = loadEventsJson(ramDir, "final_especial.json");

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

            // 9. Encadenamiento 2-eventos y 3-eventos especiales
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

            // 10. Patrones multietapa con eventos intermedios (DFS)
            List<String> objC = new ArrayList<>();
            for (String f : listaFin) {
                String c = f.split(",")[2];
                if (!objC.contains(c)) {
                    objC.add(c);
                }
            }

            for (String enlace : objEnlace) {
                intermediosDFS(new ArrayList<>(), enlace, "", 0, objCierre,
                        intermediosMap, ftList, listaInicio, listaFin, objC,
                        generatedPathways, generatedObjects);
            }

            // 11. Deduplicación
            Set<String> seen = new HashSet<>();
            List<String> uniquePathways = new ArrayList<>();
            for (String p : generatedPathways) {
                if (seen.add(p)) {
                    uniquePathways.add(p);
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;

            // 12. Construir BiologicalPathway y PathwayGraph
            Map<String, Long> edgeCounts = new LinkedHashMap<>();
            Map<String, String> graphNodes = new LinkedHashMap<>();
            List<BiologicalPathway> domainPathways = new ArrayList<>(uniquePathways.size());

            for (String p : uniquePathways) {
                String[] segments = p.split(";");
                List<PathwayEvent> evList = new ArrayList<>(segments.length);
                List<PathwayObject> objList = new ArrayList<>();
                Set<String> pathwayObjNames = new LinkedHashSet<>();

                for (int pos = 0; pos < segments.length; pos++) {
                    String[] parts = segments[pos].split(",");
                    String first = parts[0].trim().replace("'", "");
                    String rel = parts[1].trim().replace("'", "");
                    String second = parts[2].trim().replace("'", "");
                    evList.add(new PathwayEvent(first, rel, second, pos + 1));
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

            log.info("Dynamic inference completed in {} ms: {} pathways, {} nodes, {} edges",
                    durationMs, domainPathways.size(), nodes.size(), edges.size());

            return DynamicExecutionResult.builder()
                    .pipelineId(pipelineId)
                    .user(input.getUser())
                    .executionTimeMs(durationMs)
                    .generatedPathwaysCount(domainPathways.size())
                    .pathways(domainPathways)
                    .graph(graph)
                    .build();

        } catch (Exception e) {
            log.error("Failed executing dynamic inference in RAM-disk", e);
            throw new RuntimeException("Dynamic inference error: " + e.getMessage(), e);
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

        log.info("Starting production pipeline inference for pipelineId: {}, user: {}, summarizeKb: {}",
                pipelineId, user, summarizeKb);

        if (inferenceRepository == null) {
            throw new IllegalStateException("InferenceRepository is not configured for pipeline inference");
        }
        Inference inference = inferenceRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("No inference configuration found in inferences collection for pipelineId: " + pipelineId));

        List<KbObject> objects = resolveBiologicalObjects(inference, pipelineId);
        if (objects == null || objects.isEmpty()) {
            throw new IllegalArgumentException("No biological objects found in knowledge base for pipelineId: " + pipelineId);
        }

        String restrictionLevel = inference.getRestrictionLevel() != null ? inference.getRestrictionLevel() : "UNRESTRICTED";
        List<String> targetObjects = (restrictedObjects != null && !restrictedObjects.isEmpty())
                ? restrictedObjects
                : objects.stream().map(KbObject::getName).toList();

        List<PathwayEvent> events = knowledgeBaseRepository.findEventsByPipelineIdAndRestriction(pipelineId, restrictionLevel, targetObjects);

        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("No biological events found in knowledge base for pipelineId: " + pipelineId);
        }

        // 1. Borrar inmediatamente cualquier información de inferencia previa del pipeline (vías y grafo)
        long deletedPrevious = pathwayRepository.deleteByPipelineId(pipelineId);
        log.info("Cleared {} previous pathways and graph for pipelineId: {} before launching new inference",
                deletedPrevious, pipelineId);

        DynamicInferenceInput input = DynamicInferenceInput.builder()
                .pipelineId(pipelineId)
                .user(user)
                .events(events)
                .objects(objects)
                .restrictedObjects(restrictedObjects != null ? restrictedObjects : List.of())
                .closingObjects(closingObjects != null ? closingObjects : List.of())
                .summarizeKb(summarizeKb)
                .maxDepth(maxDepth != null && maxDepth > 0 ? maxDepth : 10)
                .build();

        DynamicExecutionResult result = runDynamicInference(input);

        if (result.getPathways() != null && !result.getPathways().isEmpty()) {
            log.info("Persisting {} inferred pathways to MongoDB for pipelineId: {}",
                    result.getPathways().size(), pipelineId);
            pathwayRepository.savePathwaysBatch(result.getPathways());
        }

        if (result.getGraph() != null) {
            log.info("Persisting pathway graph ({} nodes, {} edges) to MongoDB for pipelineId: {}",
                    result.getGraph().getNodes().size(), result.getGraph().getEdges().size(), pipelineId);
            pathwayRepository.savePathwayGraph(result.getGraph());
        }

        log.info("Production pipeline inference finished successfully for pipelineId: {} in {} ms",
                pipelineId, result.getExecutionTimeMs());

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
        return pathwayRepository.findGraphByPipelineId(pipelineId);
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
        return "protein";
    }

    public static List<PathwayEvent> summarizeKnowledgeBase(List<PathwayEvent> events) {
        if (events == null) return Collections.emptyList();
        Map<String, PathwayEvent> uniqueEvents = new LinkedHashMap<>();
        for (PathwayEvent ev : events) {
            String normalizedRel = normalizeRelation(ev.getRelation());
            String key = ev.getFirst() + "|" + normalizedRel + "|" + ev.getSecond();
            uniqueEvents.putIfAbsent(key, new PathwayEvent(ev.getFirst(), normalizedRel, ev.getSecond()));
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

    private List<KbObject> resolveBiologicalObjects(String pipelineId) {
        if (inferenceRepository == null) {
            throw new IllegalStateException("InferenceRepository is not configured for pipeline inference");
        }
        Inference inference = inferenceRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("No inference configuration found in inferences collection for pipelineId: " + pipelineId));
        return resolveBiologicalObjects(inference, pipelineId);
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
