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
package com.github.jk1.license.reader

import groovy.xml.XmlSlurper
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedArtifactResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

/**
 * Resolves the POM files of a set of modules, including all their parent POMs.
 *
 * Artifact resolution queries require the {@link DependencyHandler} of the project, which is not available at task
 * execution time with the configuration cache enabled. This resolver is therefore invoked from a provider that Gradle
 * evaluates when storing the configuration cache entry (or, without the configuration cache, when the owning task
 * reads it), within the context of the project owning the dependencies.
 */
class PomResolver {
    private static final Logger LOGGER = Logging.getLogger(PomResolver.class)

    private final DependencyHandler dependencies

    PomResolver(DependencyHandler dependencies) {
        this.dependencies = dependencies
    }

    /**
     * @param coordinates {@code group:name:version} of the modules to resolve the POMs for
     * @return POM files keyed by {@code group:name:version}, for the given modules and all their ancestors
     */
    Map<String, List<File>> resolve(Collection<String> coordinates) {
        Map<String, List<File>> result = [:]
        Set<String> pending = new LinkedHashSet<>(coordinates)
        while (!pending.isEmpty()) {
            Map<String, List<File>> resolved = resolveBatch(pending)
            result.putAll(resolved)
            pending = resolved.values().flatten()
                .collect { parentCoordinates(it as File) }
                .findAll { it != null && !result.containsKey(it) } as Set<String>
        }
        result
    }

    private Map<String, List<File>> resolveBatch(Collection<String> coordinates) {
        Map<String, List<File>> result = coordinates.collectEntries { [(it): []] }
        try {
            def query = dependencies.createArtifactResolutionQuery()
            coordinates.each { String gav ->
                def (group, name, version) = gav.split(':', 3)
                query.forModule(group, name, version)
            }
            query
                .withArtifacts(MavenModule, MavenPomArtifact)
                .execute()
                .resolvedComponents
                .each { component ->
                    String gav = "${component.id.group}:${component.id.module}:${component.id.version}"
                    component.getArtifacts(MavenPomArtifact).each {
                        if (it instanceof ResolvedArtifactResult) {
                            result.computeIfAbsent(gav) { [] } << it.file
                        } else if (it instanceof UnresolvedArtifactResult) {
                            LOGGER.info("POM of $gav unresolved due to ${it.failure}. It will be skipped.")
                        }
                    }
                }
        } catch (Exception e) {
            LOGGER.info("Could not resolve POMs due to $e. They will be skipped.")
        }
        result
    }

    private static String parentCoordinates(File pom) {
        try {
            def parent = new XmlSlurper(false, false).parse(pom).parent
            String group = parent.groupId.text().trim()
            String name = parent.artifactId.text().trim()
            String version = parent.version.text().trim()
            return group && name && version ? "$group:$name:$version" : null
        } catch (Exception e) {
            LOGGER.warn("Error reading parent of $pom", e)
            return null
        }
    }
}
