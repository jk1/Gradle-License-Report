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
package com.github.jk1.license.task

import com.github.jk1.license.reader.CollectedConfiguration
import com.github.jk1.license.reader.CollectedDependencies
import com.github.jk1.license.reader.CollectedModule
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Resolves the configurations of a single project for a license report: module coordinates, artifact files and
 * POM files. The plugin registers one of these in every project covered by a {@link ReportTask}, as resolving the
 * configurations of other projects from the report task itself is neither configuration-cache nor
 * parallel-execution safe.
 */
abstract class CollectDependenciesTask extends DefaultTask {

    CollectDependenciesTask() {
        description = 'Resolves the dependencies of this project for a dependency license report'
        // Cheap, and the artifacts are intentionally not declared as inputs: that would make Gradle compute the
        // task dependencies of every scanned configuration, which some plugins (like Android) fail at.
        outputs.upToDateWhen(Specs.satisfyNone())
    }

    @Internal
    abstract ListProperty<ScannedConfiguration> getScannedConfigurations()

    /** POM files of the resolved modules and their parents; resolved when Gradle stores the configuration cache. */
    @Internal
    abstract MapProperty<String, List<File>> getPomFiles()

    /** All collected data, including the locations of the artifact and POM files. */
    @OutputFile
    abstract RegularFileProperty getOutputFile()

    /** Coordinates of the collected dependencies only, so that report tasks can use them as a relocatable input. */
    @OutputFile
    abstract RegularFileProperty getCoordinatesFile()

    @TaskAction
    void collect() {
        def collected = new CollectedDependencies()
        scannedConfigurations.get().each { ScannedConfiguration scanned ->
            Map<String, List<String>> artifactsByModule = [:]
            scanned.artifacts.artifacts.each { ResolvedArtifactResult artifact ->
                def id = artifact.id.componentIdentifier
                if (id instanceof ModuleComponentIdentifier) {
                    artifactsByModule.computeIfAbsent("${id.group}:${id.module}:${id.version}".toString()) { [] } << artifact.file.absolutePath
                }
            }
            CollectedConfiguration graph = scanned.graph.get()
            collected.configurations << new CollectedConfiguration(
                name: graph.name,
                directDependencies: graph.directDependencies,
                modules: graph.modules.collect { CollectedModule module ->
                    new CollectedModule(
                        group: module.group,
                        name: module.name,
                        version: module.version,
                        projectDependency: module.projectDependency,
                        artifacts: artifactsByModule[module.coordinates] ?: []
                    )
                }
            )
        }
        collected.poms = pomFiles.get().collectEntries { String coordinates, List<File> files ->
            [(coordinates): files*.absolutePath]
        } as Map<String, List<String>>

        write(outputFile, collected.toJson())
        write(coordinatesFile, collected.configurations.collectMany { it.coordinates }.join('\n'))
    }

    private static void write(RegularFileProperty file, String text) {
        def output = file.get().asFile
        output.parentFile.mkdirs()
        output.text = text
    }

    /** A configuration to scan: its lazily resolved dependency graph and artifacts. */
    static class ScannedConfiguration {
        final Provider<CollectedConfiguration> graph
        final ArtifactCollection artifacts
        final boolean buildScript

        ScannedConfiguration(Provider<CollectedConfiguration> graph, ArtifactCollection artifacts, boolean buildScript) {
            this.graph = graph
            this.artifacts = artifacts
            this.buildScript = buildScript
        }
    }
}
