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

package com.github.jk1.license.filter;

import com.github.jk1.license.*;

/**
 * This class is designed to function as a filter for removing dependencies from a report that are not associated
 * with any artifacts. For instance, Kotlin Multiplatform modules will be excluded, while platform-specific modules
 * will be retained in the report.
 */
class ExcludeDependenciesWithoutArtifactsFilter implements DependencyFilter {
    @Override
    ProjectData filter(ProjectData source) {
        def configurations = source.configurations
                .collect { c ->
                    new ConfigurationData(c.name, c.dependencies.findAll { it.hasArtifactFile })
                }
                .toSet()

        return new ProjectData(source.project, configurations, source.importedModules);
    }
}
