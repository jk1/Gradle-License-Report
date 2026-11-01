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

import com.github.jk1.license.LicenseReportSettings
import com.github.jk1.license.ProjectInfo
import spock.lang.Specification
import spock.lang.TempDir

class ProjectReaderSpec extends Specification {

    @TempDir
    File outputDir

    ProjectInfo project = new ProjectInfo(name: "root", path: ":", group: "org.example", version: "1.0")
    LicenseReportSettings settings

    def setup() {
        settings = new LicenseReportSettings(unionParentPomLicenses: true, absoluteOutputDir: outputDir.absolutePath)
    }

    def "read() keeps the project it reads for"() {
        when:
        def data = new ProjectReader(settings).read(project, [])

        then:
        data.project == project
        data.configurations.isEmpty()
    }

    def "read() spans the dependencies collected from every project"() {
        given:
        def subA = collected(configuration("cfgA", ["org.a:a:1"]))
        def subB = collected(configuration("cfgB", ["org.b:b:1"]))

        when:
        def data = new ProjectReader(settings).read(project, [subA, subB])

        then:
        data.configurations*.name == ["cfgA", "cfgB"]
        data.allDependencies.collect { "$it.group:$it.name:$it.version".toString() } == ["org.a:a:1", "org.b:b:1"]
    }

    def "read() merges same-name configurations from different projects into one entry"() {
        given:
        def subA = collected(configuration("shared", ["org.a:a:1"], ["org.a:a:1"]))
        def subB = collected(configuration("shared", ["org.b:b:1"], ["org.b:b:1"]))

        when:
        def data = new ProjectReader(settings).read(project, [subA, subB])

        then:
        data.configurations*.name == ["shared"]
        data.configurations.first().dependencies*.name == ["a", "b"]
        data.configurations.first().directDependencies as List == ["org.a:a:1", "org.b:b:1"]
    }

    def "read() applies the configured exclusions"() {
        given:
        settings = new LicenseReportSettings(
            excludeOwnGroup: true,
            ownGroups: ["org.example"],
            excludeGroups: ["org.excluded", "org\\.regex\\..*"],
            excludes: ["org.other:excluded"],
            absoluteOutputDir: outputDir.absolutePath
        )
        def dependencies = collected(configuration("runtimeClasspath", [
            "org.example:own:1",
            "org.excluded:group:1",
            "org.regex.group:matched:1",
            "org.other:excluded:1",
            "org.other:kept:1",
        ]))

        when:
        def data = new ProjectReader(settings).read(project, [dependencies])

        then:
        data.allDependencies.collect { "$it.group:$it.name".toString() } == ["org.other:kept"]
    }

    def "read() only excludes BOMs without artifacts when configured to"() {
        given:
        def dependencies = collected(configuration("runtimeClasspath", ["org.a:a-bom:1", "org.a:bom:1"]))

        expect:
        new ProjectReader(settings).read(project, [dependencies]).allDependencies*.name == ["a-bom", "bom"]

        when:
        settings = new LicenseReportSettings(excludeBoms: true, absoluteOutputDir: outputDir.absolutePath)

        then:
        new ProjectReader(settings).read(project, [dependencies]).allDependencies.isEmpty()
    }

    def "collected dependencies survive a round trip through their file"() {
        given:
        def file = new File(outputDir, "dependencies.json")
        def original = collected(configuration("runtimeClasspath", ["org.a:a:1"], ["org.a:a:1"]))
        original.configurations.first().modules.first().artifacts = ["/some/a-1.jar"]
        original.poms = ["org.a:a:1": ["/some/a-1.pom"]]

        when:
        file.text = original.toJson()

        then:
        CollectedDependencies.fromJson(file) == original
    }

    private static CollectedDependencies collected(CollectedConfiguration... configurations) {
        new CollectedDependencies(configurations: configurations.toList())
    }

    private static CollectedConfiguration configuration(String name, List<String> modules, List<String> direct = []) {
        new CollectedConfiguration(
            name: name,
            directDependencies: direct,
            modules: modules.collect {
                def (group, module, version) = it.split(':')
                new CollectedModule(group: group, name: module, version: version)
            }
        )
    }
}
