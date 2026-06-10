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

import com.github.jk1.license.LicenseReportExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ProjectReaderSpec extends Specification {

    Project root
    Project subA
    Project subB
    LicenseReportExtension config

    def setup() {
        root = ProjectBuilder.builder().withName("root").build()
        subA = ProjectBuilder.builder().withParent(root).withName("subA").build()
        subB = ProjectBuilder.builder().withParent(root).withName("subB").build()

        subA.configurations.create("cfgA") {
            it.canBeResolved = true
            it.canBeConsumed = false
        }
        subB.configurations.create("cfgB") {
            it.canBeResolved = true
            it.canBeConsumed = false
        }

        config = new LicenseReportExtension(root)
        config.projects = [root, subA, subB] as Project[]
        config.configurations = []
    }

    def "readAllProjects() spans every configured project"() {
        when:
        def reader = new ProjectReader(config)
        def data = reader.readAllProjects()

        then:
        data.project == root
        data.configurations*.name as Set == ["cfgA", "cfgB"] as Set
    }

    def "readAllProjects() merges same-name configurations from different projects into one entry"() {
        given:
        subA.configurations.create("shared") { it.canBeResolved = true; it.canBeConsumed = false }
        subB.configurations.create("shared") { it.canBeResolved = true; it.canBeConsumed = false }
        config.configurations = ["shared"] as String[]

        when:
        def reader = new ProjectReader(config)
        def data = reader.readAllProjects()

        then:
        data.configurations*.name == ["shared"]
    }

    def "resolvedDependencyKeys() returns a sorted set of GAV strings for the scanned configurations"() {
        when:
        def keys = new ProjectReader(config).readAllDependencyKeysOnly()

        then:
        // Empty configurations resolve to empty dependency sets.
        keys instanceof SortedSet
        keys.isEmpty()
    }

    def "resolvedDependencyKeys() walks the same scanned configurations as readAllProjects()"() {
        // Pins the equivalence relied on for cache-key correctness: any GAV the report
        // would surface must appear in the cache key, and vice versa.
        when:
        def reader = new ProjectReader(config)
        def fromReport = reader.readAllProjects().allDependencies
                .collect { "${it.group}:${it.name}:${it.version}".toString() } as Set
        def fromCacheKey = new ProjectReader(config).readAllDependencyKeysOnly() as Set

        then:
        fromCacheKey == fromReport
    }
}
