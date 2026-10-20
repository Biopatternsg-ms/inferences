# Plan de Arquitectura e Implementación: Motor de Inferencias Prolog Concurrente

Este documento define la estrategia técnica y los planes detallados para ejecutar inferencias sobre reglas Prolog ([`pathwaysJPL.pl`](file:///z:/home/debian/actualizado/biopatternsg/scripts/pathwaysJPL.pl)) en el microservicio **`inferences`** (desarrollado con **Quarkus 3 / Java 21** y **MongoDB**), permitiendo soportar múltiples clientes de forma concurrente con aislamiento total de datos.

---

## Índice

1. [Diagnóstico y Reto de Concurrencia](#1-diagnóstico-y-reto-de-concurrencia)
2. [Estrategia de Generación de Datos en Memoria (MongoDB)](#2-estrategia-de-generación-de-datos-en-memoria-mongodb)
3. [Alternativa A: Subprocesos SWI-Prolog CLI con Pool y RAM-Disk](#3-alternativa-a-subprocesos-swi-prolog-cli-con-pool-y-ram-disk-recomendada)
4. [Alternativa B: Motor Embebido JPL con Módulos Dinámicos Aislados](#4-alternativa-b-motor-embebido-jpl-con-módulos-dinámicos-aislados)
5. [Optimización de `pathwaysJPL.pl`: Eliminación del Cuello de Botella O(N)](#5-optimización-de-pathwaysjplpl-eliminación-del-cuello-de-botella-on)
6. [Cuadros Comparativos](#6-cuadros-comparativos)
7. [Diseño Hexagonal en el Servicio `inferences`](#7-diseño-hexagonal-en-el-servicio-inferences)

---

## 1. Diagnóstico y Reto de Concurrencia

En el sistema `biopatternsg`, el archivo [`pathwaysJPL.pl`](file:///z:/home/debian/actualizado/biopatternsg/scripts/pathwaysJPL.pl) calcula rutas biológicas (interacciones ligando-receptor-proteínas-factores de transcripción). Requiere dos fuentes de conocimiento dinámicas:
- **`kBase.pl`**: Hechos de interacciones regulatorias (`event/3`).
- **`pathwaysObjects.pl`**: Identidad y biotipos de las entidades (`protein/1`, `ligand/1`, etc.).

### Los tres problemas críticos a resolver:
1. **Multi-tenancy y Aislamiento de Memoria:** Cada cliente o pipeline procesa su propio subconjunto de hechos. En SWI-Prolog estándar, los predicados son globales (`user`). Si dos clientes consultan al mismo tiempo sin aislamiento, se sobreescriben los hechos mutuamente.
2. **Datos residentes en MongoDB (Sin archivos persistentes en disco):** La información proviene de colecciones de Mongo (`InferenceCollection`, eventos, ontologías) y debe proyectarse a sintaxis Prolog en tiempo de ejecución.
3. **Alto consumo de CPU por Backtracking:** Las búsquedas de caminos biológicos combinatorios requieren control estricto de concurrencia para no agotar los núcleos de procesamiento del servidor.

---

## 2. Estrategia de Generación de Datos en Memoria (MongoDB)

Dado que la información reside en MongoDB, el microservicio debe formatear los documentos en memoria como sentencias Prolog sin desgastar el almacenamiento secundario físico (SSD/HDD).

### Solución: Almacenamiento Efímero en Memoria RAM (`/dev/shm` o `tmpfs`)
En entornos Linux y contenedores Docker, el directorio `/dev/shm` (*shared memory*) es un sistema de archivos `tmpfs` montado directamente en memoria RAM. Escribir allí tiene latencia de microsegundos y no genera I/O físico en disco.

```
MongoDB -> Lógica Quarkus -> Generador en Memoria -> RAM Disk (/dev/shm/pipeline_xxx/) -> SWI-Prolog -> Limpieza Inmediata
```

### Clase de Utilidad: Generador de Sintaxis Prolog en Memoria

```java
package com.biopatternsg.infrastructure.prolog;

import java.util.List;
import java.util.Map;

public class PrologMemoryGenerator {

    /**
     * Genera la representación en texto de kBase en memoria.
     * En formato optimizado (hechos directos).
     */
    public static String generateKBaseFacts(List<Map<String, String>> events) {
        StringBuilder sb = new StringBuilder(events.size() * 64);
        sb.append("% In-Memory Knowledge Base generated from MongoDB\n");
        sb.append(":- dynamic(event/3).\n\n");
        for (Map<String, String> ev : events) {
            String subject = escapeAtom(ev.get("subject"));
            String relation = escapeAtom(ev.get("relation"));
            String object = escapeAtom(ev.get("object"));
            sb.append(String.format("event('%s', %s, '%s').\n", subject, relation, object));
        }
        return sb.toString();
    }

    /**
     * Genera los biotipos en memoria.
     */
    public static String generateBiotypes(Map<String, List<String>> biotypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("% In-Memory Biotypes generated from MongoDB\n");
        sb.append(":- dynamic(protein/1).\n");
        sb.append(":- dynamic(ligand/1).\n\n");
        
        biotypes.getOrDefault("protein", List.of())
                .forEach(p -> sb.append(String.format("protein('%s').\n", escapeAtom(p))));
        biotypes.getOrDefault("ligand", List.of())
                .forEach(l -> sb.append(String.format("ligand('%s').\n", escapeAtom(l))));
        return sb.toString();
    }

    private static String escapeAtom(String val) {
        if (val == null) return "";
        return val.replace("'", "\\'");
    }
}
```

---

## 3. Alternativa A: Subprocesos SWI-Prolog CLI con Pool y RAM-Disk (Recomendada)

### Principio de Funcionamiento
Por cada petición de cliente, Quarkus escribe los datos en `/dev/shm/run_<uuid>/` e invoca el binario `swipl` como un subproceso aislado. Un semáforo limita cuántos procesos corren en simultáneo para proteger la CPU.

```
Cliente 1 ---> [ Quarkus REST Endpoint ]
Cliente 2 ---> [ Semáforo (Max N Cores) ]
                   |
     +-------------+-------------+
     |                           |
[ swipl Proc 1 ]            [ swipl Proc 2 ]
(PID 1024, RAM Disk)        (PID 1025, RAM Disk)
Cero colisión               Cero colisión
```

### Ventajas Clave
1. **Aislamiento Total:** El kernel del sistema operativo garantiza que ningún proceso pueda ver ni alterar la memoria de otro.
2. **Resiliencia de la JVM:** Si una consulta Prolog entra en bucle infinito o consume demasiada memoria, un `timeout` mata únicamente el subproceso con `process.destroyForcibly()`. La JVM de Quarkus jamás se cae.
3. **Sin problemas de JNI/C en Docker:** No depende de compilar enlaces nativos C complejos ni lidiar con librerías dinámicas compartidas dentro del proceso Java.

### Implementación en Quarkus (Java 21)

```java
package com.biopatternsg.infrastructure.adapters.out;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@ApplicationScoped
public class SwiPrologCliExecutorAdapter {

    // Limita la concurrencia al número de cores disponibles
    private final Semaphore concurrencyLimiter;
    private final Path ramDiskDir;
    private final Path rulesScriptPath;

    public SwiPrologCliExecutorAdapter(
            @ConfigProperty(name = "prolog.concurrency.limit", defaultValue = "4") int maxConcurrent,
            @ConfigProperty(name = "prolog.ramdisk.dir", defaultValue = "/dev/shm/prolog_runs") String ramDiskPath,
            @ConfigProperty(name = "prolog.rules.path", defaultValue = "scripts/pathwaysJPL.pl") String rulesPath) {
        this.concurrencyLimiter = new Semaphore(maxConcurrent);
        this.ramDiskDir = Paths.get(ramDiskPath);
        this.rulesScriptPath = Paths.get(rulesPath).toAbsolutePath();
        initRamDisk();
    }

    private void initRamDisk() {
        try {
            if (!Files.exists(ramDiskDir)) {
                Files.createDirectories(ramDiskDir);
            }
        } catch (IOException e) {
            log.warn("No se pudo usar /dev/shm, usando directorio temporal del sistema", e);
        }
    }

    public List<String> executeInference(String kBaseContent, String biotypesContent, String goal, Duration timeout) 
            throws InterruptedException, TimeoutException, IOException {

        // Espera disponibilidad en el semáforo para no saturar CPU
        if (!concurrencyLimiter.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Servidor ocupado: límite de inferencias concurrentes alcanzado");
        }

        Path executionDir = null;
        try {
            // 1. Crear carpeta efímera en RAM Disk
            String runId = "run_" + UUID.randomUUID();
            executionDir = Files.createTempDirectory(ramDiskDir, runId);
            Path kBasePath = executionDir.resolve("kBase.pl");
            Path biotypesPath = executionDir.resolve("pathwaysObjects.pl");

            Files.writeString(kBasePath, kBaseContent, StandardOpenOption.CREATE_NEW);
            Files.writeString(biotypesPath, biotypesContent, StandardOpenOption.CREATE_NEW);

            // 2. Construir comando SWI-Prolog
            // -q: Quiet (sin banners)
            // -s: Consultar scripts
            // -g: Ejecutar meta y salir con halt
            ProcessBuilder pb = new ProcessBuilder(
                    "swipl",
                    "-q",
                    "-s", biotypesPath.toString(),
                    "-s", kBasePath.toString(),
                    "-s", rulesScriptPath.toString(),
                    "-g", goal + ", halt.",
                    "-t", "halt(1)"
            );
            pb.redirectErrorStream(true);

            // 3. Ejecutar subproceso
            Process process = pb.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("La inferencia excedió el tiempo límite de " + timeout.toSeconds() + " segundos");
            }

            // 4. Leer resultados
            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                }
            }

            return outputLines;

        } finally {
            concurrencyLimiter.release();
            // 5. Limpieza garantizada del espacio en RAM
            if (executionDir != null) {
                try {
                    deleteDirectoryRecursively(executionDir);
                } catch (Exception ex) {
                    log.error("Error limpiando directorio efímero: {}", executionDir, ex);
                }
            }
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
        }
    }
}
```

### Configuración en Dockerfile
Para que esta alternativa funcione en el contenedor de Quarkus, solo se requiere instalar el paquete estándar de SWI-Prolog en Debian/Ubuntu:
```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
USER root
RUN microdnf install -y pl && microdnf clean all
USER 185
```

---

## 4. Alternativa B: Motor Embebido JPL con Módulos Dinámicos Aislados

### Principio de Funcionamiento
SWI-Prolog se ejecuta embebido en el mismo proceso de Java a través de JNI (`jpl.jar` + `libjpl.so`). Para evitar que los datos de un cliente colisionen con los de otro, **cada cliente debe crear su propio módulo dinámico (`module(PipelineId)`)**.

```
JVM Quarkus Process
 └── SWI-Prolog Core (JNI C-Engine)
      ├── Module "pipeline_client_1" -> kBase_1 + rules
      ├── Module "pipeline_client_2" -> kBase_2 + rules
      └── Module "pipeline_client_3" -> kBase_3 + rules
```

### Carga en Memoria sin Disco con JPL
SWI-Prolog incluye el predicado `open_string/2` o `atom_to_memory_file/2` que permite consultar código Prolog directamente desde un String en memoria:

```prolog
load_from_string(ModuleName, PrologCode) :-
    open_string(PrologCode, Stream),
    load_files(ModuleName, [stream(Stream), module(ModuleName)]).
```

### Implementación en Java con JPL y Módulos Aislados

```java
package com.biopatternsg.infrastructure.adapters.out;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jpl7.Atom;
import org.jpl7.Compound;
import org.jpl7.Query;
import org.jpl7.Term;
import org.jpl7.Variable;

import java.util.*;

@Slf4j
@ApplicationScoped
public class JplPrologIsolatedAdapter {

    private final Object jplEngineLock = new Object();

    public List<Map<String, String>> executeInferenceInModule(
            String pipelineId, 
            String kBaseCode, 
            String biotypesCode,
            String scriptPath) {

        String moduleName = "mod_" + pipelineId.replace("-", "_");
        List<Map<String, String>> results = new ArrayList<>();

        // JPL sincroniza las consultas sobre el motor de SWI-Prolog
        synchronized (jplEngineLock) {
            try {
                // 1. Cargar las reglas base en el módulo aislado
                String loadRulesQuery = String.format("load_files('%s', [module('%s')]).", scriptPath, moduleName);
                if (!new Query(loadRulesQuery).hasSolution()) {
                    throw new RuntimeException("Error cargando reglas en módulo: " + moduleName);
                }

                // 2. Inyectar datos en memoria directo al módulo usando assertz dinámico
                // (o vía open_string)
                cargarStringEnModulo(moduleName, biotypesCode);
                cargarStringEnModulo(moduleName, kBaseCode);

                // 3. Ejecutar la meta cualificada con el módulo: mod_123:inicio(A, E, B)
                Query inferenceQuery = new Query(new Compound(":", new Term[]{
                        new Atom(moduleName),
                        new Compound("inicio", new Term[]{new Variable("A"), new Variable("E"), new Variable("B")})
                }));

                while (inferenceQuery.hasMoreSolutions()) {
                    Map<String, Term> binding = inferenceQuery.nextSolution();
                    results.add(Map.of(
                            "A", binding.get("A").name(),
                            "E", binding.get("E").name(),
                            "B", binding.get("B").name()
                    ));
                }
                inferenceQuery.close();

            } finally {
                // 4. Limpieza obligatoria: Descargar el módulo para evitar fugas de memoria nativa
                try {
                    String cleanupQuery = String.format("unload_file('%s')", scriptPath);
                    new Query(cleanupQuery).hasSolution();
                    // Limpiar predicados del módulo
                    new Query(String.format("abolish(%s:event/3)", moduleName)).hasSolution();
                } catch (Exception ex) {
                    log.error("Error liberando módulo JPL {}", moduleName, ex);
                }
            }
        }
        return results;
    }

    private void cargarStringEnModulo(String moduleName, String prologCode) {
        // Usa open_string de SWI-Prolog para no tocar disco
        String queryStr = String.format(
            "setup_call_cleanup(open_string('%s', Stream), load_files(%s, [stream(Stream), module(%s)]), close(Stream)).",
            prologCode.replace("'", "\\'"), moduleName, moduleName
        );
        new Query(queryStr).hasSolution();
    }
}
```

---

## 5. Optimización de `pathwaysJPL.pl`: Eliminación del Cuello de Botella O(N)

### ¿En qué consiste el problema en la versión actual?

En el archivo original [`pathwaysJPL.pl`](file:///z:/home/debian/actualizado/biopatternsg/scripts/pathwaysJPL.pl#L10-L15):
```prolog
inicio(A,E,B):-
	base(C),
	buscar_en_lista(event(A,E,B),C),
	relations_start(R_start),
	buscar_en_lista(E,R_start),
	restrictions_start(A,B).
```

1. **Empaquetado en lista monolítica:** El archivo `kBase.pl` empaqueta todos los eventos dentro de una sola lista en `base([event('A', rel, 'B'), ...]).`.
2. **Escaneo lineal $O(N)$:** `buscar_en_lista` es una recursión ingenua sobre listas en Prolog:
   ```prolog
   buscar_en_lista(L,[L|_]).
   buscar_en_lista(L,[_|Ys]):-buscar_en_lista(L,Ys).
   ```
3. **Explosión combinatoria:**
   Al buscar cadenas (`inicio` -> `intermedio` -> `final`), si hay 5,000 eventos:
   * Para cada intento de `inicio`, se recorren 5,000 elementos.
   * Para enlazar el siguiente evento `intermedios(B, E2, C)`, Prolog vuelve a recorrer los 5,000 elementos desde el inicio.
   * La complejidad se vuelve **$O(N^K)$** (donde $K$ es el largo de la ruta). El tiempo de respuesta pasa de milisegundos a varios minutos, saturando la CPU.

---

### ¿En qué consiste la mejora propuesta?

La mejora consiste en **descomponer la lista en hechos indexados de primer orden** y aprovechar el **First-Argument Indexing** nativo de SWI-Prolog:

1. **Hechos directos en `kBase.pl`:**
   En lugar de `base([event(...), ...])`, se generan hechos directos:
   ```prolog
   event('IL6', positive_correlation, 'STAT3').
   event('STAT3', interact, 'JAK1').
   ```
2. **Búsqueda indexada $O(1)$:**
   La máquina virtual WAM de SWI-Prolog crea automáticamente una tabla hash interna para el predicado `event/3` indexando el primer argumento (`A`).
   Cuando se consulta `event('STAT3', E, X)`, **no recorre todos los hechos**, sino que salta inmediatamente al bloque de hechos donde el sujeto es `'STAT3'`.
3. **Uso de predicados estándar (`member/2`):**
   Reemplazar `buscar_en_lista` por el predicado nativo optimizado en C `member/2`.

### Código Mejorado Propuesto (`pathways_optimized.pl`)

```prolog
:- style_check(-discontiguous).
:- dynamic event/3.
:- dynamic protein/1.
:- dynamic ligand/1.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Reglas de Rutas Biológicas Optimizadas (Indexación Hash O(1))
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% 1. Evento inicio: ligando -> receptor o receptor -> receptor
inicio(A, E, B):-
    event(A, E, B),
    relations_start(R_start),
    member(E, R_start),
    restrictions_start(A, B).

% 2. Evento final: transcription_factor -> objeto_de_cierre
final(A, E, B):-
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end),
    restrictions_end(A, B).

% 3. Eventos intermedios: proteina -> proteina
intermedios(A, E, B):-
    event(A, E, B),
    relations_middle(R_middle),
    member(E, R_middle),
    restrictions_middle(A, B).

% 4. Eventos especiales
eventoEspecial(A, E, B):-
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end).

finalEspecial(A, E, B):-
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Versiones con Lista Restringida de Usuario (L)
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

inicio_rest(A, E, B, L):-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_start(R_start),
    member(E, R_start),
    restrictions_start(A, B).

final_rest(A, E, B, L):-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end),
    restrictions_end(A, B).

intermedios_rest(A, E, B, L):-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_middle(R_middle),
    member(E, R_middle),
    restrictions_middle(A, B).

eventoEspecial_rest(A, E, B, L):-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Restricciones Biológicas
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

restrictions_start(A, B):-
    (ligand(A) ; protein(A)),
    protein(B),
    A \= B.

relations_start([association, bind, interact, interaction]).

restrictions_end(A, B):-
    protein(A),
    protein(B).

relations_end([negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]).

restrictions_middle(A, B):-
    (ligand(B) ; protein(B)),
    A \= B.

relations_middle([association, interact, interaction]).
```

---

## 6. Cuadros Comparativos

### Tabla 1: Comparativa de Arquitecturas de Ejecución Concurrente

| Criterio | Alternativa A: Subprocesos CLI (`ProcessBuilder` + `/dev/shm`) | Alternativa B: JPL Embebido (`jpl7` con Módulos) |
| :--- | :--- | :--- |
| **Aislamiento Multi-Cliente** | **Absoluto** (Procesos de SO independientes por petición). | **Lógico** (Requiere gestión manual de módulos `module/2`). |
| **Riesgo para Quarkus (JVM)** | **Nulo**. Si Prolog crashea o agota memoria, el proceso muere y Quarkus sigue sano. | **Alto**. Un fallo en el código C de SWI-Prolog puede tumbar todo el contenedor JVM. |
| **Tiempo de Arranque/Consulta** | Bajo (~30-60 ms por arranque de proceso `swipl`). | **Ultra bajo** (~1-5 ms, memoria directa). |
| **Consumo de Memoria** | Moderado (cada proceso reserva su memoria efímera en `/dev/shm`). | Bajo (memoria compartida dentro del proceso Java). |
| **Control de Concurrencia** | **Simple y robusto** vía `Semaphore` o `ManagedExecutor`. | Complejo (JPL requiere sincronización en el acceso al motor JNI). |
| **Complejidad en Docker** | **Mínima** (`apt-get install swi-prolog` o `microdnf`). | **Alta** (Configurar `LD_LIBRARY_PATH`, `libjpl.so`, dependencias compartidas). |
| **Soporte Nativo GraalVM** | **100% Compatible** (Quarkus puede compilar a nativo sin problemas). | Incompatible o muy difícil con Native Image. |
| **Veredicto** | **Recomendada para Producción en Microservicios**. | Solo si la latencia estricta debe ser < 10 ms. |

---

### Tabla 2: Comparativa de Rendimiento del Script Prolog

| Métrica / Aspecto | `pathwaysJPL.pl` Original | `pathways_optimized.pl` Propuesto |
| :--- | :--- | :--- |
| **Estructura de Datos** | Lista única en `base([event(...)...])` | Hechos individuales `event(A, E, B).` |
| **Mecanismo de Búsqueda** | Recorrido recursivo `buscar_en_lista/2` | Tabla Hash JIT de SWI-Prolog |
| **Complejidad de Búsqueda de Evento** | **$O(N)$** (lineal con el número de eventos) | **$O(1)$** (acceso directo por hashing del sujeto) |
| **Complejidad Búsqueda de Caminos** | **$O(N^K)$** (explosión exponencial) | **$O(B^K)$** (donde $B \ll N$ es solo el factor de ramificación) |
| **Consumo de Pila Prolog (Trail Stack)** | Masivo (mantiene listas grandes en retroceso) | Mínimo (puntos de elección compactos) |
| **Tiempo estimado (10,000 eventos)** | **5 a 45 segundos** por consulta | **< 200 milisegundos** |
| **Escalabilidad Concurrente** | Pobre (satura CPU con 2 o 3 clientes simultáneos) | **Excelente** (soporta decenas de consultas concurrentes) |

> [!NOTE]
> **Validación Empírica Completada:**
> Los resultados de rendimiento y la validación de exactitud biológica de esta propuesta frente al dataset de referencia de COVID-19 (`1,417` hechos, `17,766` rutas generadas con **100% de coincidencia**, **37.25x de aceleración** y **87.8x menos inferencias**) se encuentran detallados en:
> 👉 [`BENCHMARK_Y_VALIDACION_LEGACY.md`](file:///z:/home/debian/nuevos-ms/inferences/docs/BENCHMARK_Y_VALIDACION_LEGACY.md)

---

## 7. Diseño Hexagonal en el Servicio `inferences`

Para integrar esta solución respetando la arquitectura de puertos y adaptadores de `inferences`, se definen los siguientes componentes:

```
[InferenceRestController] (Adapter IN)
            │
            ▼
[ExecuteInferenceUseCase] (Application UseCase)
            │
            ├──► [InferenceRepository] (Port OUT -> MongoDB Panache)
            │
            └──► [PrologInferencePort] (Port OUT)
                        ▲
                        │ Implementa
            [SwiPrologCliExecutorAdapter] (Adapter OUT)
                        │
                        ├──► Lee datos de Mongo en memoria
                        ├──► Escribe en /dev/shm
                        └──► Ejecuta swipl con concurrencia controlada
```

### Contrato del Puerto (`PrologInferencePort.java`)

```java
package com.biopatternsg.domain.port.out;

import java.util.List;
import java.util.Map;

public interface PrologInferencePort {
    
    /**
     * Ejecuta la búsqueda de rutas biológicas concurrentemente de forma aislada.
     */
    List<Map<String, String>> findPathways(
            String pipelineId, 
            List<Map<String, String>> events, 
            Map<String, List<String>> biotypes, 
            List<String> restrictedObjects
    );
}
```

---

## 8. Recomendación y Plan de Acción Inmediato

1. **Adoptar la Alternativa A:** Implementar el adaptador de subprocesos CLI con control de concurrencia mediante `Semaphore`. Es la opción más segura, estable y compatible con la arquitectura de microservicios en contenedores.
2. **Migrar a la versión optimizada de `pathways_optimized.pl`:** Al generar `kBase.pl` desde MongoDB, emitir los hechos como `event(S, R, O).` en lugar de una lista única `base([...])`.
3. **Configurar el Dockerfile:** Asegurarse de que la imagen base de `inferences` incluya el paquete `swi-prolog`.

> [!IMPORTANT]
> **Desglose de Tareas de Implementación (Fase 2):**
> El plan paso a paso con las 8 tareas atómicas y validables para la persistencia de caminos en MongoDB (`pathways` y `pathway_graphs`), la ingesta desde `kb_events`/`kb_objects` y los endpoints de consulta para la UI se encuentra en:
> 👉 [`PLAN_TAREAS_FASE2_PRODUCCION.md`](file:///z:/home/debian/nuevos-ms/inferences/docs/PLAN_TAREAS_FASE2_PRODUCCION.md)
