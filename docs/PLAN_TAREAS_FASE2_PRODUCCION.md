# Plan Detallado de Implementación (Fase 2): Persistencia en MongoDB, Inferencia Dinámica y Endpoints para UI

Este documento desglosa **todo el trabajo restante** del microservicio `inferences` en **8 tareas pequeñas, atómicas y validables incrementalmente**. Cada tarea está diseñada para mantener el proyecto compilando en todo momento y cumpliendo al 100% las reglas de Arquitectura Hexagonal (**ArchUnit**).

---

## Mapa General de Tareas

```
[T1] Modelos de Dominio (Pathway, Event con position, Object con role, Graph)
  │
  ▼
[T2] Colecciones MongoDB (pathways, pathway_graphs, kb_events, kb_objects)
  │
  ▼
[T3] Puertos, Mappers y Adaptadores MongoDB (insertMany por lotes + Índices)
  │
  ▼
[T4] Simplificación del Flujo por Archivos Físicos (solo salida .txt, sin benchmark ni MongoDB)
  │
  ▼
[T5] Motor Dinámico en RAM-Disk (/dev/shm) + Resumen KB + Semáforo Concurrente
  │
  ▼
[T6] Endpoint de Producción POST /api/inferences/pathways/run (Lee y Guarda en MongoDB)
  │
  ▼
[T7] Endpoints de Consulta para la UI (Grafo Consolidado + Caminos Paginados/Filtrados)
  │
  ▼
[T8] Dockerfile de Producción con swi-prolog-nox y Pruebas Integrales
```

---

## Reglas de Oro de Arquitectura (ArchUnit) a Respetar en Todas las Tareas

1. **Nombrado obligatorio por paquete:**
   * Toda clase en `com.biopatternsg.application.usecase..` **debe** terminar en `UseCase`.
   * Toda clase en `com.biopatternsg.infrastructure.adapters.out..` **debe** terminar en `Adapter`.
   * Toda clase en `com.biopatternsg.infrastructure.adapters.in.restcontrollers..` **debe** terminar en `Controller`.
2. **Prohibido usar clases anónimas o internas (`$1`, Builder internos)** dentro de `usecase`, `adapters.out` o `restcontrollers`, ya que el clasificador de bytecode de ArchUnit las evalúa como clases independientes que violan el sufijo obligatorio.
3. **Aislamiento de capas:**
   * `domain` no puede importar nada de `application` ni de `infrastructure`.
   * `application` no puede importar nada de `infrastructure`.

---

## Tarea 1 (T1): Modelos de Dominio para Caminos, Eventos Posicionales, Objetos con Rol y Grafo

### Objetivo
Actualizar y crear las clases de dominio puras para representar exactamente la estructura JSON requerida para cada camino individual y para el grafo consolidado de la UI.

### Archivos a Crear / Modificar
1. **Modificar `com.biopatternsg.domain.model.PathwayEvent`:**
   * Atributos:
     * `String first` (sujeto / nodo origen)
     * `String relation` (tipo de interacción biológica)
     * `String second` (objeto / nodo destino)
     * `Integer position` (orden secuencial del evento en el camino: `1, 2, 3...`)
   * Mantener métodos de compatibilidad o actualizar referencias (`getFirst()`, `getSecond()`).
2. **Crear `com.biopatternsg.domain.model.PathwayObject`:**
   * Atributos:
     * `String name` (ej. `"IFNA1"`, `"TLR2"`, `"IRF7"`)
     * `String role` (tipo biológico: `"ligand"`, `"receptor"`, `"transcription_factor"`, `"enzyme"`, `"protein"`)
3. **Modificar `com.biopatternsg.domain.model.BiologicalPathway`:**
   * Atributos:
     * `String id`
     * `String pipelineId`
     * `String user`
     * `String rawPattern` (ej. `"'IFNA1',association,'IRF7';'IRF7',bind,'IRF3'"`)
     * `Integer length` (cantidad de eventos en la ruta)
     * `String startObject` (nombre del primer objeto de la ruta)
     * `String endObject` (nombre del último objeto de la ruta)
     * `List<PathwayEvent> events`
     * `List<PathwayObject> objects`
4. **Crear `com.biopatternsg.domain.model.GraphEdge`:**
   * Atributos:
     * `String first`
     * `String relation`
     * `String second`
     * `Long pathwayCount` (en cuántos caminos aparece esta interacción)
5. **Crear `com.biopatternsg.domain.model.PathwayGraph`:**
   * Atributos:
     * `String pipelineId`
     * `String user`
     * `Integer totalPathways`
     * `Long executionTimeMs`
     * `List<PathwayObject> nodes`
     * `List<GraphEdge> edges`
6. **Crear `com.biopatternsg.domain.model.KbObject`:**
   * Atributos:
     * `String pipelineId`
     * `String name`
     * `List<String> roles`
     * `List<String> biotypes`

### Criterio de Validación (T1)
* Ejecutar `./mvnw test-compile` y `./mvnw test -Dtest=ArchitectureLayerTest` en WSL.
* Verificar que los 7 tests de ArchUnit pasan sin errores.

---

## Tarea 2 (T2): Colecciones y Documentos MongoDB (Panache)

### Objetivo
Definir las entidades de base de datos en el paquete `com.biopatternsg.infrastructure.mongo` tanto para escribir los resultados (`pathways` y `pathway_graphs`) como para leer las entradas del pipeline (`kb_events` y `kb_objects`).

### Archivos a Crear en `com.biopatternsg.infrastructure.mongo`
1. **`PathwayEventDocument.java`** (POJO embebido para el array `events`):
   ```java
   public class PathwayEventDocument {
       private String first;
       private String relation;
       private String second;
       private Integer position;
   }
   ```
2. **`PathwayObjectDocument.java`** (POJO embebido para el array `objects` / `nodes`):
   ```java
   public class PathwayObjectDocument {
       private String name;
       private String role;
   }
   ```
3. **`PathwayCollection.java`** (`@MongoEntity(collection = "pathways")`):
   ```java
   @MongoEntity(collection = "pathways")
   public class PathwayCollection extends PanacheMongoEntity {
       private String pipelineId;
       private String user;
       private String rawPattern;
       private Integer length;
       private String startObject;
       private String endObject;
       private List<PathwayEventDocument> events;
       private List<PathwayObjectDocument> objects;
       private Instant createdAt;
   }
   ```
4. **`GraphEdgeDocument.java`** (POJO embebido para las aristas consolidadas):
   ```java
   public class GraphEdgeDocument {
       private String first;
       private String relation;
       private String second;
       private Long pathwayCount;
   }
   ```
5. **`PathwayGraphCollection.java`** (`@MongoEntity(collection = "pathway_graphs")`):
   ```java
   @MongoEntity(collection = "pathway_graphs")
   public class PathwayGraphCollection extends PanacheMongoEntity {
       private String pipelineId;
       private String user;
       private Integer totalPathways;
       private Long executionTimeMs;
       private List<PathwayObjectDocument> nodes;
       private List<GraphEdgeDocument> edges;
       private Instant createdAt;
   }
   ```
6. **`KbEventCollection.java`** (`@MongoEntity(collection = "kb_events")`):
   * Espejo exacto de la colección alimentada por `pubmed-integration`:
     * `String pipelineId`
     * `String first`
     * `String relation`
     * `String second`
     * `List<String> pubmedIds` (incluido para compatibilidad total con el esquema de `pubmed-integration` y trazabilidad bibliográfica).
7. **`KbObjectCollection.java`** (`@MongoEntity(collection = "kb_objects")`):
   * Espejo exacto de la colección alimentada por `pubmed-integration`:
     * `String pipelineId`
     * `String name`
     * `List<String> synonyms`
     * `List<String> biotypes`
     * `List<String> roles`

### Criterio de Validación (T2)
* Ejecutar `./mvnw test -Dtest=ArchitectureLayerTest` y confirmar que todas las entidades compilan sin generar clases internas prohibidas.

---

## Tarea 3 (T3): Puertos de Repositorio, Mappers y Adaptadores MongoDB con `insertMany`

### Objetivo
Implementar la capa de persistencia de alto rendimiento capaz de insertar 17,766 caminos en lotes (`insertMany`) en ~150 ms y crear los índices de consulta rápida para la UI.

### Archivos a Crear
1. **Puerto `com.biopatternsg.domain.port.out.repositories.PathwayRepository`:**
   * `void savePathwaysBatch(List<BiologicalPathway> pathways)`
   * `void savePathwayGraph(PathwayGraph graph)`
   * `Optional<PathwayGraph> findGraphByPipelineId(String pipelineId)`
   * `List<BiologicalPathway> findByPipelineId(String pipelineId, String startObject, String endObject, String containsObject, int page, int size)`
   * `long countByPipelineId(String pipelineId)`
   * `long deleteByPipelineId(String pipelineId)`
2. **Puerto `com.biopatternsg.domain.port.out.repositories.KnowledgeBaseRepository`:**
   * `List<PathwayEvent> findEventsByPipelineId(String pipelineId)`
   * `List<KbObject> findObjectsByPipelineId(String pipelineId)`
3. **Mapper `com.biopatternsg.infrastructure.mapper.PathwayMapper`:**
   * Mapea `BiologicalPathway <-> PathwayCollection` y `PathwayGraph <-> PathwayGraphCollection`.
4. **Adaptador `com.biopatternsg.infrastructure.adapters.out.PathwayRepositoryAdapter`:**
   * Implementa `PathwayRepository` y `PanacheMongoRepository<PathwayCollection>`.
   * **Inserción por lotes:** Divide la lista en bloques de 5,000 documentos y llama a `persist(batch)` (que ejecuta `insertMany` en el driver nativo de MongoDB).
   * **Creación automática de índices (`@PostConstruct` o inicialización):**
     * Índice en `pathways`: `{ "pipelineId": 1, "startObject": 1, "endObject": 1 }`
     * Índice en `pathways`: `{ "pipelineId": 1, "objects.name": 1 }`
     * Índice único en `pathway_graphs`: `{ "pipelineId": 1 }`
5. **Adaptador `com.biopatternsg.infrastructure.adapters.out.KnowledgeBaseRepositoryAdapter`:**
   * Implementa `KnowledgeBaseRepository`. Consulta `KbEventCollection` y `KbObjectCollection` filtrando por `pipelineId`.

### Criterio de Validación (T3)
* Crear test unitario `PathwayRepositoryAdapterTest` y correr `./mvnw test`.
* Confirmar que `ArchitectureLayerTest` valida que las clases en `adapters.out` terminan en `Adapter`.

---

## Tarea 4 (T4): Simplificación del Flujo por Archivos Físicos (Solo Salida `.txt` para Pruebas)

### Objetivo
Dejar **únicamente lo mínimo e indispensable** del flujo que lee archivos físicos como camino alternativo para pruebas rápidas: eliminar el benchmark y la comparación contra el ground-truth legado (`legacy/pathways.txt`), y hacer que **únicamente genere y guarde el archivo de salida `output/pathways_generated.txt`** (sin guardar nada en MongoDB).

### Limpieza y Simplificación a Realizar
1. **Eliminar endpoints y métodos innecesarios:**
   * Eliminar de `InferenceController`, `InferPathways` e `InferPathwaysUseCase`:
     * `GET /api/inferences/pathways/benchmark` (`benchmarkRules()`, `runSingleBenchmark()`, scripts `benchmark_original.pl` y `benchmark_optimized.pl`).
     * `GET /api/inferences/pathways/test` (`testInference()`).
     * Toda la lógica de lectura/comparación contra `src/main/resources/legacy/pathways.txt` (`loadGroundTruthLines()`, `missingInGenerated`, `extraInGenerated`, `perfectMatch`).
   * Eliminar archivos físicos que solo se usaban para el benchmark o validación:
     * `src/main/resources/prolog/pathways.pl` (reglas antiguas sin indexar).
     * `src/main/resources/prolog/kBase.pl` (base antigua en una sola línea `kBase([...])`).
     * `src/main/resources/legacy/pathways.txt` (ground-truth de validación).
   * Conservar únicamente los 4 archivos físicos esenciales en `src/main/resources/prolog/`:
     * `pathways_optimized.pl`
     * `kBase_optimized.pl`
     * `pathwaysObjects.pl`
     * `dump_runner.pl`
2. **Flujo limpio por archivos físicos (`GET /api/inferences/pathways/from-files`):**
   * Ejecuta `dump_runner.pl` sobre los archivos físicos de `src/main/resources/prolog/`.
   * Ejecuta el encadenamiento DFS en Java.
   * **Guarda únicamente la salida en formato `.txt` en `output/pathways_generated.txt`** (un camino por línea, ej. `'IFNA1',association,'IRF7';'IRF7',bind,'IRF3'`).
   * **No toca ni guarda nada en MongoDB** (queda 100% aislado como camino alternativo para pruebas).

### Criterio de Validación (T4)
* Ejecutar `./mvnw test` y verificar que el código queda limpio sin métodos de benchmark ni comparación de ground-truth.
* Invocar `GET /api/inferences/pathways/from-files` y verificar que genera `output/pathways_generated.txt` con las 17,766 líneas y devuelve las métricas de ejecución sin interactuar con MongoDB.

---

## Tarea 5 (T5): Ejecución Dinámica en RAM-Disk (`/dev/shm`), Resumen de KB (`summarizeKb`) y Control de Concurrencia

### Objetivo
Permitir que cualquier conjunto de eventos y objetos (proveniente de MongoDB, no solo de COVID-19) se proyecte en memoria RAM (`/dev/shm`) y se ejecute en SWI-Prolog de manera aislada por cada cliente concurrente, incluyendo todas las opciones del sistema legado (`patrones.java`).

### Detalle de Implementación
1. **Directorio Efímero en RAM (`/dev/shm`):**
   * En Linux/WSL/Docker, si `/dev/shm` existe y es escribible, crear una subcarpeta única por ejecución: `/dev/shm/inferences_run_<pipelineId>_<uuid>/`.
   * Si se corre en un sistema sin `/dev/shm`, usar automáticamente el directorio temporal del SO (`Files.createTempDirectory`).
2. **Generación Dinámica de Archivos Prolog en Memoria:**
   * **`pathwaysObjects.pl` dinámico (Doble Rol en Prolog vs Rol Principal en UI):**
     * En Prolog, las reglas de `pathways_optimized.pl` exigen que una molécula tenga simultáneamente `protein('X')` y su rol específico (ej. `ligand('X')` o `receptor('X')`).
     * Por lo tanto, al generar `pathwaysObjects.pl` desde `KbObject.roles`, se escriben **todos los hechos** de la lista `roles` de cada objeto (y se asegura `protein('X').` si el objeto es proteico), agrupados por predicado (`protein/1`, `enzyme/1`, `ligand/1`, `receptor/1`, `transcription_factor/1`) con directivas `:- discontiguous` por seguridad.
   * **Opción de Resumen de Base de Conocimiento (`summarizeKb` / `resumirBaseC` de `patrones.java`):**
     * En el código legado (`patrones.java`, líneas 928–955 y 1461–1489), el usuario puede elegir si usa la base original (`kBase.pl`) o la base resumida (`kBaseR.pl`), donde las relaciones se normalizan mediante `clasificarevento(rel)`:
       * `cause`, `induce`, `stimulate`, `activate`, `up-regulate`, `promote`, `enhance`, `increase` $\rightarrow$ `positive_correlation`
       * `inhibit`, `suppress`, `down-regulate`, `repress`, `block`, `decrease`, `prevent`, `reduce` $\rightarrow$ `negative_correlation`
       * `interact`, `complex`, `colocalize` $\rightarrow$ `bind`
     * Si `summarizeKb == true`, normalizar las relaciones de `kb_events` y deduplicar ternas antes de escribir `kBase_optimized.pl`.
   * **`kBase_optimized.pl` dinámico:**
     * A partir de la lista de eventos procesada, escribir los hechos indexados `event('first', relation, 'second').` escapando comillas simples si existieran.
   * **`dump_runner.pl` parametrizado:**
     * Reemplazar dinámicamente la lista `L = [...]` con las moléculas restringidas (`restrictedObjects`) solicitadas por el usuario (o todos los objetos del pipeline si no envía filtro).
     * Soportar la lista opcional de **objetos de cierre (`closingObjects` / `objCierre`)**: si el usuario especifica objetos de cierre específicos, restringir el tercer argumento `B` de `final_rule(A, E, B, L)` mediante `member(B, ClosingObjects)`; si viene vacío, cualquier `B` válido de `final_rule` actúa como objeto de cierre.
3. **Semáforo y Limpieza Garantizada:**
   * Adquirir permiso en `Semaphore(maxConcurrent)` antes de lanzar `swipl`.
   * Destruir el proceso forzosamente si supera `prolog.timeout.seconds`.
   * En el bloque `finally`, liberar el semáforo y eliminar recursivamente la carpeta `/dev/shm/inferences_run_<pipelineId>_<uuid>/` para no dejar residuos en memoria RAM.

### Criterio de Validación (T5)
* Test unitario/integración ejecutando el flujo dinámico en `/dev/shm` (con y sin `closingObjects` y `summarizeKb`) y comprobando que al finalizar la llamada no quedan archivos residuales en `/dev/shm`.

---

## Tarea 6 (T6): Endpoint de Producción `POST /api/inferences/pathways/run`

### Objetivo
Exponer el endpoint principal de producción que recibe el `pipelineId`, el `user` (por body o header `x-user-id`), los objetos restringidos (`restrictedObjects`), los **objetos de cierre (`closingObjects`)** y el flag `summarizeKb`, lee los eventos y objetos biológicos desde MongoDB (`kb_events` y `kb_objects`), ejecuta la inferencia Prolog + DFS Java, y guarda los caminos y el grafo en MongoDB.

### Archivos a Crear / Modificar
1. **Crear `com.biopatternsg.infrastructure.dtos.PathwayInferenceRequestDTO`:**
   ```json
   {
     "pipelineId": "6801a2b3c4d5e6f7a8b9c0d1",
     "user": "investigador_01",
     "restrictedObjects": ["IFNA1", "IRF7", "IRF3", "TLR7"],
     "closingObjects": ["IRF3"],
     "summarizeKb": false,
     "maxDepth": 10
   }
   ```
   * **Nota sobre `user` y `@HeaderParam("x-user-id")`:** Si el header `x-user-id` viene presente (estándar usado por `config-and-control` y `pubmed-integration`), se toma como fallback cuando `request.user` sea nulo o vacío.
   * **Nota sobre `restrictedObjects`:** Si viene vacío o nulo, se usan todos los objetos del `pipelineId` (o `alignedExpertObjects` si aplica).
   * **Nota sobre `closingObjects` (Objetos de Cierre):** Si el usuario envía objetos específicos (ej. `["IRF3"]`), solo se generan caminos que cierren en ellos. Si viene vacío o nulo (como en el caso de prueba de COVID-19), se toman automáticamente todos los destinos válidos de `final_rule`.
   * **Nota sobre `summarizeKb`:** Por defecto `false` (usa las relaciones exactas de `kb_events`, igual que en el caso COVID-19). Si es `true`, aplica la reducción semántica `clasificarevento` antes de inferir.
2. **Crear `com.biopatternsg.infrastructure.dtos.PathwayInferenceResponseDTO`:**
   ```json
   {
     "status": "SUCCESS",
     "pipelineId": "6801a2b3c4d5e6f7a8b9c0d1",
     "user": "investigador_01",
     "generatedPathwaysCount": 17766,
     "uniqueNodesCount": 14,
     "uniqueEdgesCount": 135,
     "executionTimeMs": 410
   }
   ```
3. **Añadir método `runDynamicInference(...)` en `InferPathways` e `InferPathwaysUseCase`.**
4. **Añadir endpoint `POST /api/inferences/pathways/run` en `InferenceController`.**

### Criterio de Validación (T6)
* Poblar o usar un `pipelineId` de prueba en `kb_events` y `kb_objects`, invocar `POST /api/inferences/pathways/run` y verificar que genera y persiste todos los caminos en MongoDB.

---

## Tarea 7 (T7): Endpoints de Consulta para la UI (Grafo y Caminos Paginados)

### Objetivo
Proveer a la UI (`biopatternsg-ui`) los endpoints necesarios para renderizar el grafo biológico y explorar/rearmar los caminos generados.

### Endpoints a Añadir en `InferenceController.java`
1. **`GET /api/inferences/pathways/{pipelineId}/graph`**
   * Devuelve el documento de `pathway_graphs` con los nodos (`name`, `role`) y las aristas únicas (`first`, `relation`, `second`, `pathwayCount`).
   * Tiempo de respuesta esperado: **< 15 ms**.
2. **`GET /api/inferences/pathways/{pipelineId}`**
   * Parámetros Query opcionales:
     * `page` (default `0`), `size` (default `50`)
     * `startObject` (ej. `IFNA1`)
     * `endObject` (ej. `IRF3`)
     * `containsObject` (ej. `TLR8`)
   * Devuelve la lista paginada de documentos de `pathways` con sus arrays `events` (con `position: 1..N`) y `objects` (con `name` y `role`) para que la UI pueda mostrar el detalle o animar el camino sobre el grafo.
3. **`DELETE /api/inferences/pathways/{pipelineId}`**
   * Elimina de MongoDB los caminos y el grafo asociados a ese `pipelineId`.

### Criterio de Validación (T7)
* Probar con `curl`:
  * `curl "http://localhost:8080/api/inferences/pathways/covid-19-legacy/graph"`
  * `curl "http://localhost:8080/api/inferences/pathways/covid-19-legacy?startObject=IFNA1&endObject=IRF3&page=0&size=10"`
* Añadir tests en `InferenceControllerTest` y verificar que `./mvnw test` pasa al 100%.

---

## Tarea 8 (T8): Actualización del `Dockerfile` para Producción

### Objetivo
Asegurar que el contenedor Docker del microservicio incluya el binario `swipl` listo para ejecutarse en cualquier entorno (Docker Compose, Kubernetes o servidor Linux).

### Archivo a Modificar: `Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends swi-prolog-nox && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/quarkus-app .

EXPOSE 8080

CMD ["java", "-Dquarkus.http.host=0.0.0.0", "-jar", "quarkus-run.jar"]
```

### Criterio de Validación (T8)
* Verificar que toda la suite de tests (`./mvnw test`) pasa en verde y que el `Dockerfile` instala `swi-prolog-nox` limpiamente.
