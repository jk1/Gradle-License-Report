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

class ExcludeTransitiveDependenciesFilter implements DependencyFilter {

    @Override
    ProjectData filter(ProjectData source) {
        Set<String> firstLevelDependencies = source.configurations
            .collectMany { it.directDependencies } as Set<String>

        Set<ModuleData> moduleDataSet = source.allDependencies
            .findAll { "${it.group}:${it.name}:${it.version}".toString() in firstLevelDependencies } as Set<ModuleData>

        return new ProjectData(source.getProject(), source.getConfigurations(), source.getImportedModules()) {
            @Override
            Set<ModuleData> getAllDependencies() {
                return moduleDataSet
            }
        }
    }
}
