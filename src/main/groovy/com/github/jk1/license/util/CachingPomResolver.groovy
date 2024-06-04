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
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class CachingPomResolver {

    private static Logger LOGGER = Logging.getLogger(CachingPomResolver.class)
    private Map<Map<String, String>, Collection<File>> cache = new HashMap<>()
    private GradleProject project

    CachingPomResolver(GradleProject project) {
        this.project = project
    }

    Collection<File> resolveArtifacts(Map<String, String> spec) {
        Map<String, String> copy = new HashMap<String, String>()
        spec.each { copy.put(it.key.trim(), it.value.trim()) }
        if (!cache.containsKey(copy)) {
            cache.put(copy, doResolveArtifact(copy))
        }
        return cache.get(copy)
    }

    private Collection<File> doResolveArtifact(Object spec) {
        try {
            return project
                  .dependencies
                  .createArtifactResolutionQuery()
                  .forModule(spec.group, spec.name, spec.version)
                  .withArtifacts(MavenModule, MavenPomArtifact)
                  .execute()
                  .resolvedComponents
                  .collectMany { it.getArtifacts(MavenPomArtifact) }
                  *.file
        } catch (Throwable ignored) {
            LOGGER.info("Could not resolve $spec.group:$spec.name:$spec.version. It will be skipped.")
            return null
        }
    }
}
