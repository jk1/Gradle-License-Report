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

import com.github.jk1.license.AbstractGradleRunnerFunctionalSpec
import org.gradle.testkit.runner.TaskOutcome

/**
 * Dependencies, which are just available as .pom (usually "-bom"-modules) require a slightly different strategy, because
 * gradle will not put the artifacts inside ResolvedDependency.
 */
class PomDependencyResolutionFuncSpec extends AbstractGradleRunnerFunctionalSpec {

    def setup() {
        settingsGradle = new File(testProjectDir, "settings.gradle")

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
        """
    }

    def "report task resolves bom license"() {
        setup:
        buildFile << generateBuildWith(
                """
                implementation platform("com.fasterxml.jackson:jackson-bom:2.12.3")
            """.trim()
        )

        when:
        def runResult = runGradleBuild()

        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        // 2 licenses because it also resolves jackson-parent, which has its own license
        configurationsString == """[
    {
        "dependencies": [
            {
                "group": "com.fasterxml.jackson",
                "manifests": [
                    
                ],
                "hasArtifactFile": false,
                "version": "2.12.3",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "https://github.com/FasterXML/jackson-bom",
                        "description": "Bill of Materials pom for getting full, complete set of compatible versions\\nof Jackson components maintained by FasterXML.com\\n  ",
                        "name": "Jackson BOM",
                        "organization": {
                            "url": "http://fasterxml.com/",
                            "name": "FasterXML"
                        },
                        "licenses": [
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "Apache License, Version 2.0"
                            },
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    
                ],
                "empty": false,
                "name": "jackson-bom"
            }
        ],
        "name": "runtimeClasspath"
    }
]"""
    }

    private def generateBuildWith(String dependencies) {
        """
            import com.github.jk1.license.render.*

            dependencies {
                $dependencies
            }

            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = ["runtimeClasspath"]
            }
        """
    }

    static void removeDevelopers(Map rawFile) {
        rawFile.configurations*.dependencies.flatten().poms.flatten().each { it.remove("developers") }
    }
}
