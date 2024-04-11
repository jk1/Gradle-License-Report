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
import com.github.jk1.license.task.ReportTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import static com.github.jk1.license.reader.ProjectReader.isResolvable

class ConfigurationReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private LicenseReportExtension config
    private ModuleReader moduleReader

    ConfigurationReader(LicenseReportExtension config, ModuleReader moduleReader) {
        this.config = config
        this.moduleReader = moduleReader
    }

    ConfigurationData read(GradleProject project, Configuration configuration) {
        ConfigurationData data = new ConfigurationData()
        data.name = configuration.name

        if (!isResolvable(configuration)) {
            LOGGER.info("Skipping configuration [$configuration] as it can't be resolved")
            return data
        }

        LOGGER.info("Processing configuration [$configuration], configuration will be resolved")
        configuration.resolvedConfiguration // force configuration resolution

        Set<ResolvedDependency> dependencies = new TreeSet<ResolvedDependency>(new ResolvedDependencyComparator())
        for (ResolvedDependency dependency : configuration.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies()) {
            collectDependencies(dependencies, dependency)
        }

        LOGGER.info("Processing dependencies for configuration [$configuration]: " + dependencies.join(','))
        for (ResolvedDependency dependency : dependencies) {
            LOGGER.debug("Processing dependency: $dependency")
            data.dependencies.add(moduleReader.read(project, dependency))
        }
        return data
    }

    private Set<ResolvedDependency> collectDependencies(Set<ResolvedDependency> accumulator,
                                                        Set<ResolvedDependency> visitedExcludes = [],
                                                        ResolvedDependency root){
        // avoiding dependency cycles
        if (!accumulator.contains(root) && !visitedExcludes.contains(root)) {
            if (config.isExcluded(root)) {
                LOGGER.debug("Not collecting dependency ${root.name} due to explicit exclude configured")
                visitedExcludes.add(root)
            } else {
                LOGGER.debug("Collecting dependency ${root.name}")
                accumulator.add(root)
            }
            root.children.each { collectDependencies(accumulator, visitedExcludes, it)}
        }
        accumulator
    }

    private static class ResolvedDependencyComparator implements Comparator<ResolvedDependency>{
        @Override
        int compare(ResolvedDependency first, ResolvedDependency second) {
            first.moduleGroup <=> second.moduleGroup ?:
                first.moduleName <=> second.moduleName
        }
    }
}
