/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
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
package com.github.jk1.license.util

import com.github.jk1.license.GradleProject
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedArtifactResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class CachingPomResolver {

    private static Logger LOGGER = Logging.getLogger(CachingPomResolver.class)
    private Map<Map<String, String>, Collection<ResolvedArtifactResult>> cache = new HashMap<>()
    private GradleProject project

    CachingPomResolver(GradleProject project) {
        this.project = project
    }

    Collection<ResolvedArtifactResult> resolveArtifacts(String group, String name, String version) {
        Map<String, String> spec = ["group": group.trim(), "name": name.trim(), "version": version.trim()]
        if (!cache.containsKey(spec)) {
            LOGGER.debug("Fetch: $spec")
            cache.put(spec, doResolveArtifact(spec.group, spec.name, spec.version))
        }
        return cache.get(spec)
    }

    private Collection<ResolvedArtifactResult> doResolveArtifact(String group, String name, String version) {
        try {
            def results = project
                    .dependencies
                    .createArtifactResolutionQuery()
                    .forModule(group, name, version)
                    .withArtifacts(MavenModule, MavenPomArtifact)
                    .execute()
                    .resolvedComponents
                    .collectMany { it.getArtifacts(MavenPomArtifact) }

            results.findResults { ((it !instanceof UnresolvedArtifactResult) && (it !instanceof ResolvedArtifactResult)) ? it : null }
                    .each { throw new AssertionError("Unexpected artifact result type: ${it.getClass().getName()}") }

            results.findResults { it instanceof UnresolvedArtifactResult ? it : null }
                    .each { LOGGER.info("Component $it.id unresolved while resolving POM for $group:$name:$version due to ${it.failure}. It will be skipped.") }

            return results
                    .findResults { it instanceof ResolvedArtifactResult ? it : null }
        } catch (Exception e) {
            LOGGER.info("Could not resolve POM for $group:$name:$version due to $e. It will be skipped.")
            return []
        }
    }
}
