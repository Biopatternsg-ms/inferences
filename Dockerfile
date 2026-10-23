#
# Copyright © 2026 biopatternsg (biopatternsg@gmail.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM eclipse-temurin:21-jre-jammy

# 1. Actualizar ca-certificates del SO para anular la firma cruzada de Sectigo/eMudhra
RUN apt-get update && \
    apt-get install --only-upgrade -y ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# 2. Eliminar certificados de eMudhra / emSign de cacerts (CVE-2026-91403)
RUN keytool -delete -alias "cn_emsign_ecc_root_ca__c3,o_emudhra_inc,ou_emsign_pki,c_us [jdk]" -keystore /opt/java/openjdk/lib/security/cacerts -storepass changeit || true && \
    keytool -delete -alias "cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" -keystore /opt/java/openjdk/lib/security/cacerts -storepass changeit || true && \
    keytool -delete -alias "cn_emsign_root_ca__c1,o_emudhra_inc,ou_emsign_pki,c_us [jdk]" -keystore /opt/java/openjdk/lib/security/cacerts -storepass changeit || true && \
    keytool -delete -alias "cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" -keystore /opt/java/openjdk/lib/security/cacerts -storepass changeit || true

WORKDIR /app

COPY target/quarkus-app .

EXPOSE 8080

CMD ["java", "-Dquarkus.http.host=0.0.0.0", "-jar", "quarkus-run.jar"]
