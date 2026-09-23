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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ConfigurationScannerSpec extends Specification {

    Project project = ProjectBuilder.builder().withName("root").build()

    def setup() {
        project.configurations.create("cfgA") {
            it.canBeResolved = true
            it.canBeConsumed = false
        }
        project.configurations.create("cfgB") {
            it.canBeResolved = true
            it.canBeConsumed = false
            it.extendsFrom(project.configurations.create("cfgParent"))
        }
        project.configurations.create("consumable") {
            it.canBeResolved = false
            it.canBeConsumed = true
        }
    }

    def "scans all resolvable configurations when configured to"() {
        when:
        def scanned = new ConfigurationScanner(project).scan([] as String[], false)

        then:
        scanned*.graph*.get()*.name as Set == ["cfgA", "cfgB", "cfgParent"] as Set
        !scanned.any { it.buildScript }
    }

    def "scans the configured configurations and the resolvable ones they extend from"() {
        when:
        def scanned = new ConfigurationScanner(project).scan(["cfgB"] as String[], false)

        then:
        scanned*.graph*.get()*.name as Set == ["cfgB", "cfgParent"] as Set
    }

    def "falls back to runtimeClasspath"() {
        expect:
        ConfigurationScanner.defaultConfigurations(project) == ["runtimeClasspath"] as String[]
    }

    def "scans build script configurations"() {
        when:
        def scanned = new ConfigurationScanner(project).scan(["classpath"] as String[], true)

        then:
        scanned*.graph*.get()*.name == ["classpath"]
        scanned.every { it.buildScript }
    }

    def "fails on configurations which cannot be resolved"() {
        when:
        new ConfigurationScanner(project).scan(["consumable"] as String[], false)

        then:
        def e = thrown(UnresolvableConfigurationException)
        e.message.contains("consumable")
    }

    def "resolves no POMs without external dependencies"() {
        when:
        def scanner = new ConfigurationScanner(project)
        def poms = scanner.resolvePoms(scanner.scan([] as String[], false))

        then:
        poms.isEmpty()
    }
}
