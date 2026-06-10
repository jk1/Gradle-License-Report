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

import com.github.jk1.license.ConfigurationData
import com.github.jk1.license.GradleProject
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.task.ReportTask
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ProjectReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    Project root
    private GradleProject[] projects
    private GradleProject[] buildScriptProjects
    private String[] configurations

    private ConfigurationReader configurationReader

    ProjectReader(LicenseReportExtension config) {
        this.root = config.projects.first()
        this.projects = config.projects.collect { GradleProject.ofProject(it) }
        this.buildScriptProjects = config.buildScriptProjects.collect { GradleProject.ofScript(it) }
        this.configurations = config.configurations
        this.configurationReader = new ConfigurationReader(config, new CachedModuleReader(config))
    }

    /**
     * Reads every configured project and buildScript project, merging configurations
     * with the same name within each group. The returned data's {@code project} field
     * is set to {@code owner}; the configurations span all configured projects.
     */
    ProjectData readAllProjects() {
        LOGGER.info("Processing dependencies for project ${root.name}")
        ProjectData data = new ProjectData(project: root)

        LOGGER.info("Configured projects: ${projects.join(',')}")
        LOGGER.info("Configured buildScript projects: ${buildScriptProjects.join(',')}")

        def configurationData = (projects.toList() + buildScriptProjects.toList())
                .collectMany { GradleProject p -> readGradleProject(p) { Configuration c -> configurationReader.read(p, c) } }

        data.configurations.addAll(mergeConfigurationsByName(configurationData))

        return data
    }

    /**
     * Walks the same scanned configurations as {@link #readAllProjects()} but collects
     * only resolved dependency coordinates ("group:name:version"), skipping POM,
     * manifest, and license-file resolution. Suitable for cache-key fingerprinting,
     * where the full {@link ModuleData} is unnecessary.
     */
    SortedSet<String> readAllDependencyKeysOnly() {
        (projects.toList() + buildScriptProjects.toList())
                .collectMany { readGradleProject(it) { Configuration c -> configurationReader.readDependenciesOnly(c) } }
                .flatten()
                .collect { "${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}" } as TreeSet
    }

    private <T> List<T> readGradleProject(GradleProject project, Closure<T> configHandler) {
        Set<Configuration> configurationsToScan = withExtendsFrom(findConfigurationsToScan(project))
        LOGGER.info("Configurations(${project.name}): ${configurationsToScan.join(',')}")
        configurationsToScan.collect(configHandler)
    }

    private NamedDomainObjectSet<Configuration> findConfigurationsToScan(GradleProject project) {
        if (configurations == null) {
            LOGGER.info("No configurations defined, falling back to the default ones")
            configurations = project.getPlugins().hasPlugin('com.android.application') ? ['releaseRuntimeClasspath'] : ['runtimeClasspath']
        }
        Set<Configuration> toScan
        if (configurations.length == 0) {
            LOGGER.info("Using all resolvable configurations")
            toScan = project.configurations.matching { it.canBeResolved }
        } else {
            toScan = project.configurations.matching { it -> it.name in configurations }
            Set<Configuration> unresolvable = toScan.matching { !it.canBeResolved }
            if (!unresolvable.empty) {
                throw new UnresolvableConfigurationException("Unable to resolve configurations: $unresolvable")
            }
        }
        toScan
    }

    private static Set<Configuration> withExtendsFrom(NamedDomainObjectSet<Configuration> configurationsToScan) {
        configurationsToScan + configurationsToScan.collectMany { it.extendsFrom.findAll { it.canBeResolved } }.toSet()
    }

    private static List<ConfigurationData> mergeConfigurationsByName(Collection<ConfigurationData> configData) {
        configData.groupBy { it.name }.collect { name, configs ->
            new ConfigurationData(name: name).tap {
                dependencies.addAll(configs*.dependencies.flatten() as List<ModuleData>)
            }
        }
    }
}
