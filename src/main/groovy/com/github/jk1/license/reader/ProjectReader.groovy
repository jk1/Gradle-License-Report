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

    private Set<Configuration> findConfigurationsToScan(GradleProject project) {
        Set<Configuration> toScan
        if (configurations == null) {
            LOGGER.info("No configurations defined, falling back to the default ones")
            configurations = project.getPlugins().hasPlugin('com.android.application') ? ['releaseRuntimeClasspath'] : ['runtimeClasspath']
        }
        if (configurations.length == 0) {
            LOGGER.info("Using all resolvable configurations")
            toScan = findResolvableConfigurations(project)
        } else {
            toScan = findConfiguredConfigurations(project)
            Set<Configuration> unresolvable = findUnresolvable(toScan)
            if (unresolvable) {
                throw new UnresolvableConfigurationException("Unable to resolve configurations: $unresolvable")
            }
        }
        toScan
    }

    private static Set<Configuration> findResolvableConfigurations(GradleProject project) {
        project.configurations.findAll { config -> isResolvable(config) }
    }

    private static Set<Configuration> getAllExtendedConfigurations(Collection<Configuration> configurationsToScan) {
        configurationsToScan.collect { it.extendsFrom }.flatten().findAll { config -> isResolvable(config) }.toSet()
    }

    private List<ConfigurationData> readConfigurationData(Collection<Configuration> configurationsToScan, GradleProject project) {
        configurationsToScan.collect { config ->
            LOGGER.info("Reading configuration: " + config)
            configurationReader.read(project, config)
        }
    }

    private List<ConfigurationData> readProjects(GradleProject[] projectsToScan) {
        List<ConfigurationData> readConfigurations = projectsToScan.collect { subProject ->
            Set<Configuration> configurationsToScan = findConfigurationsToScan(subProject)
            configurationsToScan.addAll(getAllExtendedConfigurations(configurationsToScan))
            LOGGER.info("Configurations(${subProject.name}): ${configurationsToScan.join(',')}")
            readConfigurationData(configurationsToScan, subProject)
        }.flatten()
        mergeConfigurationDataWithSameName(readConfigurations)
    }

    private Set<Configuration> findConfiguredConfigurations(GradleProject project) {
        project.configurations.findAll { config -> config.name in configurations }
    }

    private static Set<Configuration> findUnresolvable(Set<Configuration> toScan) {
        toScan.findAll { config -> !isResolvable(config) }
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

    static boolean isResolvable(Configuration config) {
        config.hasProperty("canBeResolved") && config.canBeResolved
    }
}
