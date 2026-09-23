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

import com.github.jk1.license.task.CollectDependenciesTask.ScannedConfiguration
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.util.GradleVersion

/**
 * Selects the configurations of a project to report on, and sets up their lazy resolution. Used at configuration
 * time, from within the providers of {@link com.github.jk1.license.task.CollectDependenciesTask} so that
 * configurations are only resolved when a report is actually generated.
 */
class ConfigurationScanner {
    private static final Logger LOGGER = Logging.getLogger(ConfigurationScanner.class)
    private static final boolean HAS_LAZY_ROOT_COMPONENT = GradleVersion.current() >= GradleVersion.version("7.4")

    private final Project project

    ConfigurationScanner(Project project) {
        this.project = project
    }

    List<ScannedConfiguration> scan(String[] configurations, boolean buildScript) {
        ConfigurationContainer container = buildScript ? project.buildscript.configurations : project.configurations
        String owner = buildScript ? "${project.name}/buildScript" : project.name
        Set<Configuration> toScan = withExtendsFrom(findConfigurationsToScan(container, configurations))
        LOGGER.info("Configurations(${owner}): ${toScan.join(',')}")
        toScan.collect { Configuration configuration ->
            new ScannedConfiguration(
                rootComponent(configuration).map(new DependencyGraphReader(configuration.name)),
                configuration.incoming.artifactView { ArtifactView.ViewConfiguration view ->
                    view.lenient(true)
                    // project dependencies are reported by their own coordinates, not by artifacts that need building
                    view.componentFilter(new ModuleComponentsOnly())
                }.artifacts,
                buildScript
            )
        }
    }

    /**
     * Resolves the POMs of all modules of the given configurations, using the repositories of the project or its
     * build script respectively.
     */
    Map<String, List<File>> resolvePoms(List<ScannedConfiguration> scanned) {
        Map<String, List<File>> poms = [:]
        [false, true].each { boolean buildScript ->
            List<String> coordinates = scanned
                .findAll { it.buildScript == buildScript }
                .collectMany { it.graph.get().modules.findAll { !it.projectDependency }*.coordinates }
                .unique()
            if (coordinates) {
                def dependencies = buildScript ? project.buildscript.dependencies : project.dependencies
                new PomResolver(dependencies).resolve(coordinates).each { key, files -> poms.putIfAbsent(key, files) }
            }
        }
        poms
    }

    /** The configurations to scan when none are configured, based on the first project covered by a report. */
    static String[] defaultConfigurations(Project project) {
        LOGGER.info("No configurations defined, falling back to the default ones")
        project.plugins.hasPlugin('com.android.application') ? ['releaseRuntimeClasspath'] : ['runtimeClasspath']
    }

    private Set<Configuration> findConfigurationsToScan(ConfigurationContainer container, String[] configurations) {
        if (configurations.length == 0) {
            LOGGER.info("Using all resolvable configurations")
            return container.matching { it.canBeResolved }.toSet()
        }
        Set<Configuration> toScan = container.matching { it.name in configurations }.toSet()
        Set<Configuration> unresolvable = toScan.findAll { !it.canBeResolved }
        if (!unresolvable.empty) {
            throw new UnresolvableConfigurationException("Unable to resolve configurations: $unresolvable")
        }
        toScan
    }

    private static Set<Configuration> withExtendsFrom(Set<Configuration> configurationsToScan) {
        new LinkedHashSet<>(configurationsToScan + configurationsToScan.collectMany { it.extendsFrom.findAll { it.canBeResolved } })
    }

    private Provider<ResolvedComponentResult> rootComponent(Configuration configuration) {
        ResolutionResult resolutionResult = configuration.incoming.resolutionResult
        HAS_LAZY_ROOT_COMPONENT ? resolutionResult.rootComponent : project.provider { resolutionResult.root }
    }

    static class ModuleComponentsOnly implements Spec<ComponentIdentifier>, Serializable {
        private static final long serialVersionUID = 1L

        @Override
        boolean isSatisfiedBy(ComponentIdentifier id) {
            id instanceof ModuleComponentIdentifier
        }
    }
}
