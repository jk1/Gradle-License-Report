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
import com.github.jk1.license.LicenseReportSettings
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ProjectInfo
import com.github.jk1.license.task.ReportTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Reads the license data of the dependencies collected by {@link com.github.jk1.license.task.CollectDependenciesTask}s,
 * merging configurations with the same name.
 */
class ProjectReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private final LicenseReportSettings settings

    ProjectReader(LicenseReportSettings settings) {
        this.settings = settings
    }

    ProjectData read(ProjectInfo project, List<CollectedDependencies> collected) {
        LOGGER.info("Processing dependencies for project ${project.name}")
        Map<String, List<File>> poms = [:]
        collected.each {
            it.poms.each { String coordinates, List<String> files -> poms.putIfAbsent(coordinates, files.collect { new File(it) }) }
        }
        ModuleReader moduleReader = new CachedModuleReader(settings, new ResolvedPoms(poms))

        List<ConfigurationData> configurationData = collected
            .collectMany { it.configurations }
            .collect { readConfiguration(it, moduleReader) }

        ProjectData data = new ProjectData(project: project)
        data.configurations.addAll(mergeConfigurationsByName(configurationData))
        return data
    }

    private ConfigurationData readConfiguration(CollectedConfiguration configuration, ModuleReader moduleReader) {
        LOGGER.info("Processing configuration [${configuration.name}]")
        ConfigurationData data = new ConfigurationData(name: configuration.name)
        data.directDependencies.addAll(configuration.directDependencies)
        configuration.modules.each { CollectedModule module ->
            if (settings.isExcluded(module.group, module.name, !module.artifacts.isEmpty())) {
                LOGGER.debug("Not collecting dependency ${module.coordinates} due to explicit exclude configured")
            } else {
                LOGGER.debug("Processing dependency: ${module.coordinates}")
                data.dependencies.add(moduleReader.read(module))
            }
        }
        data
    }

    private static List<ConfigurationData> mergeConfigurationsByName(Collection<ConfigurationData> configData) {
        configData.groupBy { it.name }.collect { name, configs ->
            new ConfigurationData(name: name).tap {
                dependencies.addAll(configs*.dependencies.flatten() as List<ModuleData>)
                directDependencies.addAll(configs*.directDependencies.flatten() as List<String>)
            }
        }
    }
}
