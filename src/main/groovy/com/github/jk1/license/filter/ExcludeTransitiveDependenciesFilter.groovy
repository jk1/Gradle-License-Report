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
package com.github.jk1.license.filter

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.task.ReportTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.stream.Collectors

class ExcludeTransitiveDependenciesFilter implements DependencyFilter {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    @Override
    public ProjectData filter(ProjectData source) {

        Set<ResolvedDependency> firstLevelDependencies = source.getConfigurations()
            .stream()
            .flatMap({ c ->
                Optional<Configuration> conf = getConfig(source, c.getName());
                if (conf.isPresent()) {
                    conf.get().getResolvedConfiguration()
                        .getFirstLevelModuleDependencies()
                        .stream()
                }
            })
            .collect(Collectors.toSet());

        Set<ModuleData> moduleDataSet = source.getAllDependencies()
            .stream()
            .filter({ md ->
                firstLevelDependencies
                    .stream()
                    .anyMatch({ dependency ->
                        md.getName() == dependency.getModuleName() &&
                            md.getGroup() == dependency.getModuleGroup() &&
                            md.getVersion() == dependency.getModuleVersion()
                    })
            })
            .collect(Collectors.toSet())

        return new ProjectData(source.getProject(), source.getProjects(), source.getConfigurations(), source.getImportedModules()) {
            @Override
            Set<ModuleData> getAllDependencies() {
                return moduleDataSet
            }
        };
    }

    private Optional<Configuration> getConfig(ProjectData source, String name) {
        LOGGER.info('Find config for ' + name);
        source.projects.toList().stream()
            .map({p -> p.getConfigurations().findByName(name)})
            .filter({c -> c != null})
            .findFirst()    // TODO: Merge configurations with same name - see ProjectData.groovy
    }
}
