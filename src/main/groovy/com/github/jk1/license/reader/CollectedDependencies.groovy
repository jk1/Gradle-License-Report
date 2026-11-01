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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Canonical

/**
 * Dependency data of one project, resolved in the context of that project by
 * {@link com.github.jk1.license.task.CollectDependenciesTask} and handed over to
 * {@link com.github.jk1.license.task.ReportTask} as a file.
 *
 * Resolving the configurations of other projects from a task is neither configuration-cache nor parallel-execution
 * safe, hence dependency resolution and report generation are split into separate tasks.
 */
@Canonical
class CollectedDependencies {
    List<CollectedConfiguration> configurations = []
    /** POM files of the collected modules and their parent POMs, keyed by {@code group:name:version}. */
    Map<String, List<String>> poms = [:]

    String toJson() {
        JsonOutput.toJson([
            configurations: configurations.collect { it.toMap() },
            poms          : poms
        ])
    }

    static CollectedDependencies fromJson(File file) {
        def json = new JsonSlurper().parse(file) as Map
        new CollectedDependencies(
            configurations: json.configurations.collect { CollectedConfiguration.fromMap(it as Map) },
            poms: json.poms as Map<String, List<String>>
        )
    }
}

@Canonical
class CollectedConfiguration implements Serializable {
    private static final long serialVersionUID = 1L

    String name
    /** Coordinates of the dependencies declared directly in the configuration. */
    List<String> directDependencies = []
    List<CollectedModule> modules = []

    Map toMap() {
        [name: name, directDependencies: directDependencies, modules: modules.collect { it.toMap() }]
    }

    /** The coordinates of the configuration's dependencies, without any machine specific information. */
    List<String> getCoordinates() {
        modules.collect { "$name:${it.coordinates}".toString() } + directDependencies.collect { "$name:direct:$it".toString() }
    }

    static CollectedConfiguration fromMap(Map map) {
        new CollectedConfiguration(
            name: map.name,
            directDependencies: map.directDependencies as List<String>,
            modules: map.modules.collect { CollectedModule.fromMap(it as Map) }
        )
    }
}

@Canonical
class CollectedModule implements Serializable {
    private static final long serialVersionUID = 1L

    String group
    String name
    String version
    /** Whether the module is a project of the build rather than an external module. */
    boolean projectDependency
    /** Absolute paths of the resolved artifact files of the module. */
    List<String> artifacts = []

    String getCoordinates() {
        "$group:$name:$version"
    }

    Map toMap() {
        [group: group, name: name, version: version, projectDependency: projectDependency, artifacts: artifacts]
    }

    static CollectedModule fromMap(Map map) {
        new CollectedModule(
            group: map.group,
            name: map.name,
            version: map.version,
            projectDependency: map.projectDependency as boolean,
            artifacts: map.artifacts as List<String>
        )
    }
}
