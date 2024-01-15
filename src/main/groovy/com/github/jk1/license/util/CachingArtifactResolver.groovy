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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class CachingArtifactResolver {

    private static Logger LOGGER = Logging.getLogger(CachingArtifactResolver.class)
    private Map<String, Collection<ResolvedArtifact>> cache = new HashMap<>()
    private GradleProject project

    CachingArtifactResolver(GradleProject project) {
        this.project = project
    }

    Collection<ResolvedArtifact> resolveArtifacts(String dependencyNotation) {
        if (!cache.containsKey(dependencyNotation)) {
            cache.put(dependencyNotation, doResolveArtifact(dependencyNotation))
        }
        return cache.get(dependencyNotation)
    }

    private Collection<ResolvedArtifact> doResolveArtifact(String dependencyNotation) {
        Dependency dependency = project.dependencies.create(dependencyNotation)
        Configuration config = project.configurations.detachedConfiguration(dependency).setTransitive(false)
        try {
            Collection<ResolvedArtifact> artifacts = config.resolvedConfiguration.resolvedArtifacts
            if (artifacts != null) {
                // Exercise #getFile() to download the file and catch exceptions here
                for (ResolvedArtifact artifact : artifacts) {
                    artifact.getFile()
                }
            }
            return artifacts
        } catch (Throwable ignored) {
            LOGGER.info("Could not resolve $dependencyNotation. It will be skipped.")
            return null
        } finally {
            project.configurations.remove(config)
        }
    }
}
