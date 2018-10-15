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
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ReportTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ProjectReader {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)
    private CachedModuleReader moduleReader = new CachedModuleReader()
    private ConfigurationReader configurationReader = new ConfigurationReader(moduleReader)

    ProjectData read(Project project) {
        ProjectData data = new ProjectData()
        data.project = project

        Project[] projectsToScan = project.licenseReport.projects
        LOGGER.info("Configured projects: ${projectsToScan.join(',')}")

        List<ConfigurationData> readConfigurations = projectsToScan.collect { subProject ->
            Set<Configuration> configurationsToScan = findConfigurationsToScan(subProject)

            configurationsToScan.addAll(getAllExtendedConfigurations(configurationsToScan))

            LOGGER.info("Configurations(${subProject.name}): ${configurationsToScan.join(',')}")
            readConfigurationData(configurationsToScan, subProject)
        }.flatten()
        readConfigurations = mergeConfigurationDataWithSameName(readConfigurations)

        data.configurations.addAll(readConfigurations)
        return data
    }

    private static Set<Configuration> findConfigurationsToScan(Project project) {
        Set<Configuration> toScan = findConfigured(project)
        Set<Configuration> unresolvable = findUnresolvable(toScan)
        if (unresolvable) {
            throw new UnresolvableConfigurationException("Unable to resolve configurations: $unresolvable")
        }
        if (!toScan) {
            toScan = project.configurations.findAll { config -> isResolvable(config) }
        }
        toScan
    }

    private static Set<Configuration> getAllExtendedConfigurations(Collection<Configuration> configurationsToScan) {
        configurationsToScan.collect { it.extendsFrom }.flatten().findAll { config -> isResolvable(config) }
    }

    private List<ConfigurationData> readConfigurationData(Collection<Configuration> configurationsToScan, Project project) {
        configurationsToScan.collect { config ->
            LOGGER.info("Reading configuration: " + config)
            configurationReader.read(project, config)
        }
    }

    static Set<Configuration> findConfigured(Project project) {
        project.configurations.findAll { config -> config.name in project.licenseReport.configurations }
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
