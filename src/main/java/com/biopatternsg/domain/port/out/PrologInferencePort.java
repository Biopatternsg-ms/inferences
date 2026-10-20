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
package com.biopatternsg.domain.port.out;

import com.biopatternsg.domain.model.BiologicalPathway;
import com.biopatternsg.domain.model.PathwayEvent;

import java.util.List;

public interface PrologInferencePort {

    /**
     * Consulta los eventos de inicio válidos según las reglas Prolog.
     */
    List<PathwayEvent> findStartEvents(String pathwaysObjectsPath, String kBasePath, String rulesPath);

    /**
     * Consulta los eventos de fin válidos según las reglas Prolog.
     * Si endObject es nulo o vacío, busca cualquier evento final.
     */
    List<PathwayEvent> findEndEvents(String pathwaysObjectsPath, String kBasePath, String rulesPath, String endObject);

    /**
     * Consulta los eventos intermedios que parten desde currentObject.
     */
    List<PathwayEvent> findMiddleEvents(String pathwaysObjectsPath, String kBasePath, String rulesPath, String currentObject);

    /**
     * Ejecuta la inferencia y reconstrucción completa de rutas biológicas.
     */
    List<BiologicalPathway> inferPathways(String pathwaysObjectsPath, String kBasePath, String rulesPath);
}
