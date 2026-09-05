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

import com.github.jk1.license.*;
import java.util.stream.Collectors

class ImportedModuleNameFilter implements DependencyFilter {
    @Override
    ProjectData filter(ProjectData projectData) {
        def config = projectData.extension
        projectData.importedModules.forEach { m ->
            {
                m.modules = m.modules.
                    stream()
                    .filter { module ->
                        !shouldExcludeArtifact(module, config)
                    }
                    .collect(Collectors.toSet())
            }
        }
        return projectData
    }

    private boolean shouldExcludeArtifact(ImportedModuleData module, LicenseReportExtension config) {
        return config.excludes.contains(module.name) || config.excludes.any { module.name.matches(it) }
    }
}
