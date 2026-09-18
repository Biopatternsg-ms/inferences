# Microservicio: Inferences

Microservicio desarrollado en **Quarkus (Java 21)** con persistencia en **MongoDB** para la gestión y registro de inferencias de modelos dentro de la plataforma `biopatternsg`.

## Arquitectura

El proyecto sigue el patrón de **Arquitectura Hexagonal (Ports & Adapters)**:

- `domain`: Modelos de negocio (`Inference`) y puertos (`InferenceRepository`, `InferenceUseCase`).
- `application`: Casos de uso (`ManageInferenceUseCase`).
- `infrastructure`:
  - `adapters/in/restcontrollers`: Endpoints REST (`InferenceController`).
  - `adapters/out`: Adaptador de persistencia (`InferenceRepositoryAdapter`).
  - `mongo`: Entidades Panache (`InferenceCollection`).
  - `dtos` & `mapper`: Objetos de transferencia y mapeo.

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `DB_URL` | URL de conexión al clúster MongoDB | `mongodb://localhost:27017` |
| `DB_USER` | Usuario de MongoDB | - |
| `DB_PASSWORD` | Contraseña de MongoDB | - |
| `DB_NAME` | Base de datos de MongoDB | `inferences` |
| `INFERENCES_PORT` | Puerto expuesto en el host (Jenkins) | `8008` |

## Endpoints Principales

- `GET /api/inferences/ping`: Chequeo rápido de disponibilidad.
- `POST /api/inferences`: Registro de una nueva inferencia.
- `GET /api/inferences`: Listado de todas las inferencias almacenadas.
- `GET /api/inferences/{id}`: Consulta de inferencia por identificador.
- `GET /q/health`: Chequeo de salud Quarkus SmallRye (Liveness/Readiness y estado de MongoDB).
- `GET /q/swagger-ui`: Interfaz Swagger UI / OpenAPI.

## Integración con Jenkins

El pipeline en `jenkins/Jenkinsfile` automatiza:
1. Pruebas unitarias (`mvn test`).
2. Análisis estático de código con PMD (`mvn verify -DskipTests`).
3. Empaquetado (`mvn package -DskipTests`) y archivado de `quarkus-app`.
4. Construcción de imagen Docker local (`inferences:latest`).
5. Despliegue del contenedor en la red `general-network` conectado a `mongo-db-prod`.
