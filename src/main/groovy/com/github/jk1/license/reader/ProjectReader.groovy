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
import com.github.jk1.license.ProjectData
import com.github.jk1.license.task.ReportTask
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ProjectReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private GradleProject[] projects
    private GradleProject[] buildScriptProjects
    private String[] configurations

    private ConfigurationReader configurationReader

    ProjectReader(LicenseReportExtension config) {
        this.projects = config.projects.collect { GradleProject.ofProject(it) }
        this.buildScriptProjects = config.buildScriptProjects.collect { GradleProject.ofScript(it) }
        this.configurations = config.configurations
        this.configurationReader = new ConfigurationReader(config, new CachedModuleReader(config))
    }

    ProjectData read(Project project) {
        ProjectData data = new ProjectData()
        data.project = project

        LOGGER.info("Configured projects: ${projects.join(',')}")
        data.configurations.addAll(readProjects(projects))
        LOGGER.info("Configured buildScript projects: ${buildScriptProjects.join(',')}")
        data.configurations.addAll(readProjects(buildScriptProjects))

        return data
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

    private List<ConfigurationData> readConfigurationData(Collection<Configuration> configurationsToScan, GradleProject project) {
        configurationsToScan.collect { config ->
            LOGGER.info("Reading configuration: " + config)
            configurationReader.read(project, config)
        }
    }

    private List<ConfigurationData> readProjects(GradleProject[] projectsToScan) {
        List<ConfigurationData> readConfigurations = projectsToScan.collectMany { subProject ->
            Set<Configuration> configurationsToScan = withExtendsFrom(findConfigurationsToScan(subProject))
            LOGGER.info("Configurations(${subProject.name}): ${configurationsToScan.join(',')}")
            readConfigurationData(configurationsToScan, subProject)
        }
        mergeConfigurationDataWithSameName(readConfigurations)
    }

    private static List<ConfigurationData> mergeConfigurationDataWithSameName(Collection<ConfigurationData> configData) {
        def configurationsByName = configData.groupBy { it.name }

        configurationsByName.collect { _, configs ->
            mergeConfigurations(configs)
        }
    }

    private static ConfigurationData mergeConfigurations(Collection<ConfigurationData> configs) {
        ConfigurationData merged = new ConfigurationData()

        configs.forEach {
            merged.name = it.name
            merged.dependencies.addAll(it.dependencies)
        }
        merged
    }
}
