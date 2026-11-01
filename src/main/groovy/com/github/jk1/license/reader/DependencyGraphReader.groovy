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

import org.gradle.api.Transformer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Collects all components reachable from the root of a resolved dependency graph (the root itself excluded),
 * de-duplicated by {@code group:name}. Unresolved dependencies and dependency constraints are skipped.
 */
class DependencyGraphReader implements Transformer<CollectedConfiguration, ResolvedComponentResult>, Serializable {
    private static final long serialVersionUID = 1L

    private final String configurationName

    DependencyGraphReader(String configurationName) {
        this.configurationName = configurationName
    }

    @Override
    CollectedConfiguration transform(ResolvedComponentResult root) {
        Map<String, CollectedModule> modules = new TreeMap<>()
        Set<ResolvedComponentResult> visited = new HashSet<>()
        Deque<ResolvedComponentResult> queue = new ArrayDeque<>(selectedComponents(root))
        visited.add(root)

        while (!queue.isEmpty()) {
            ResolvedComponentResult component = queue.removeFirst()
            if (!visited.add(component)) continue

            ModuleVersionIdentifier id = component.moduleVersion
            if (id != null) {
                modules.putIfAbsent("${id.group}:${id.name}".toString(),
                    new CollectedModule(group: id.group, name: id.name, version: id.version,
                        projectDependency: component.id instanceof ProjectComponentIdentifier))
            }
            queue.addAll(selectedComponents(component))
        }

        new CollectedConfiguration(
            name: configurationName,
            directDependencies: selectedComponents(root)
                .collect { it.moduleVersion }
                .findAll { it != null }
                .collect { "${it.group}:${it.name}:${it.version}".toString() }
                .unique(),
            modules: modules.values().toList()
        )
    }

    private static List<ResolvedComponentResult> selectedComponents(ResolvedComponentResult component) {
        component.dependencies
            .findAll { DependencyResult it -> it instanceof ResolvedDependencyResult && !it.constraint }
            .collect { ((ResolvedDependencyResult) it).selected }
    }
}
