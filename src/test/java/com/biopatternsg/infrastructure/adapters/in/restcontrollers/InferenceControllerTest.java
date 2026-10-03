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
package com.biopatternsg.infrastructure.adapters.in.restcontrollers;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class InferenceControllerTest {

    @Test
    @DisplayName("GET /inferences/ping should return 200 and status UP")
    void testPingEndpoint() {
        given()
                .when().get("/inferences/ping")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("service", equalTo("inferences"));
    }

    @Test
    @DisplayName("POST /inferences/find-roles should return 202 when request is valid")
    void testFindRoles_Accepted() {
        given()
                .contentType("application/json")
                .header("x-user-id", "test-user-1")
                .body("""
                        {
                            "pipelineId": "pipeline-test-123",
                            "alignedObjects": ["BRCA1", "TP53"]
                        }
                        """)
                .when().post("/inferences/find-roles")
                .then()
                .statusCode(202)
                .body("message", equalTo("Find roles process initiated"))
                .body("pipelineId", equalTo("pipeline-test-123"));
    }

    @Test
    @DisplayName("POST /inferences/find-roles should return 400 when pipelineId is blank")
    void testFindRoles_BadRequestWhenPipelineIdBlank() {
        given()
                .contentType("application/json")
                .header("x-user-id", "test-user-1")
                .body("""
                        {
                            "pipelineId": "",
                            "alignedObjects": ["BRCA1"]
                        }
                        """)
                .when().post("/inferences/find-roles")
                .then()
                .statusCode(400);
    }
}
