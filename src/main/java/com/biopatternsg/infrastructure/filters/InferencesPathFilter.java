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
package com.biopatternsg.infrastructure.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

/**
 * Pre-matching filter to seamlessly route requests from both `/inferences/...`
 * and `/api/inferences/...` to the JAX-RS controllers.
 * Ensures compatibility with Krakend Gateway, Vite Dev Proxy, and direct callers.
 */
@Slf4j
@Provider
@PreMatching
public class InferencesPathFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null) {
            return;
        }

        // Remove leading slash if present for uniform checking
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;

        if (cleanPath.startsWith("inferences/") || cleanPath.equals("inferences")) {
            String newPath = "api/" + cleanPath;
            URI newUri = ctx.getUriInfo().getBaseUriBuilder()
                    .path(newPath)
                    .replaceQuery(ctx.getUriInfo().getRequestUri().getRawQuery())
                    .build();
            log.debug("Rewriting request URI from {} to {}", path, newUri.getPath());
            ctx.setRequestUri(newUri);
        }
    }
}
