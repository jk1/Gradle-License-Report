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

class ExcludesFuncSpec extends AbstractGradleRunnerFunctionalSpec {

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

    def "report task respects module excludes"() {
        setup:
        buildFile << generateBuildWith(
            """
                runtime "javax.activation:activation:1.1.1"
                runtime "org.apache.commons:commons-lang3:3.7"
            """.trim(),
            """excludes = ["org.apache.commons:commons-lang3"]"""
        )

        when:
        def runResult = runGradleBuild()
        def configurationsString = prettyPrintJson(jsonSlurper.parse(rawJsonFile).configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == javaxActivationOutput
    }

    def "report task respects bom excludes"() {
        setup:
        buildFile << generateBuildWith(
            """
                runtime platform("com.fasterxml.jackson:jackson-bom:2.12.3")
                runtime "javax.activation:activation:1.1.1"
            """.trim(),
            """excludeBoms = true"""
        )

        when:
        def runResult = runGradleBuild()
        def configurationsString = prettyPrintJson(jsonSlurper.parse(rawJsonFile).configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == javaxActivationOutput
    }

    def "report task respects transitive bom excludes"() {
        setup:
        buildFile << generateBuildWith(
            """
                runtime "com.fasterxml.jackson.core:jackson-core:2.12.3" // adds com.fasterxml.jackson:jackson-bom:2.12.3
            """.trim(),
            """excludeBoms = true"""
        )

        when:
        def runResult = runGradleBuild()
        def configurationsString = prettyPrintJson(jsonSlurper.parse(rawJsonFile).configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == jacksonCoreOutput
    }

    def "report task respects group excludes"() {
        setup:
        buildFile << generateBuildWith(
            """
                runtime "javax.activation:activation:1.1.1"
                runtime "org.apache.commons:commons-lang3:3.7"
            """.trim(),
            """excludeGroups = ["org.apache.commons"]"""
        )

        when:
        def runResult = runGradleBuild()
        def configurationsString = prettyPrintJson(jsonSlurper.parse(rawJsonFile).configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == javaxActivationOutput
    }

    def "module excludes support regular expressions"() {
        setup:
        buildFile << generateBuildWith(
            """
                runtime "javax.activation:activation:1.1.1"
                runtime "org.apache.commons:commons-lang3:3.7"
            """.trim(),
            """excludes = ["org.apache.commons:commons-.*"]"""
        )

        when:
        def runResult = runGradleBuild()
        def configurationsString = prettyPrintJson(jsonSlurper.parse(rawJsonFile).configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == javaxActivationOutput
    }

    def "group excludes support regular expressions"() {
        setup:
        buildFile << generateBuildWith(
            """
                runtime "javax.activation:activation:1.1.1"
                runtime "org.apache.commons:commons-lang3:3.7"
            """.trim(),
            """excludeGroups = ["org.apache.*"]"""
        )

        when:
        def runResult = runGradleBuild()
        def configurationsString = prettyPrintJson(jsonSlurper.parse(rawJsonFile).configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == javaxActivationOutput
    }


    private def generateBuildWith(String dependencies, String exclude) {
        """
            import com.github.jk1.license.render.*

            dependencies {
                $dependencies
            }

            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderer = new com.github.jk1.license.render.RawProjectDataJsonRenderer()
                $exclude
                configurations = ["runtime"]
            }
        """
    }

    private String javaxActivationOutput = """[
    {
        "dependencies": [

        ],
        "name": "compile"
    },
    {
        "dependencies": [
            {
                "group": "javax.activation",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": "Sun Microsystems, Inc.",
                        "hasPackagedLicense": false,
                        "version": "1.1.1",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "Sun Java System Application Server"
                    }
                ],
                "version": "1.1.1",
                "poms": [
                    {
                        "developers": [

                        ],
                        "inceptionYear": "",
                        "projectUrl": "http://java.sun.com/javase/technologies/desktop/javabeans/jaf/index.jsp",
                        "description": "The JavaBeans(TM) Activation Framework is used by the JavaMail(TM) API to manage MIME data",
                        "name": "JavaBeans(TM) Activation Framework",
                        "organization": null,
                        "licenses": [
                            {
                                "url": "https://glassfish.dev.java.net/public/CDDLv1.0.html",
                                "name": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://opensource.org/licenses/CDDL-1.0",
                                "file": "activation-1.1.1.jar/META-INF/LICENSE.txt",
                                "license": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "activation"
            }
        ],
        "name": "runtime"
    }
]"""

    private String jacksonCoreOutput = """[
    {
        "dependencies": [

        ],
        "name": "compile"
    },
    {
        "dependencies": [
            {
                "group": "com.fasterxml.jackson.core",
                "manifests": [
                    {
                        "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                        "vendor": "FasterXML",
                        "hasPackagedLicense": false,
                        "version": "2.12.3",
                        "license": null,
                        "description": "Core Jackson processing abstractions (aka Streaming API), implementation for JSON",
                        "url": "https://github.com/FasterXML/jackson-core",
                        "name": "Jackson-core"
                    }
                ],
                "version": "2.12.3",
                "poms": [
                    {
                        "developers": [

                        ],
                        "inceptionYear": "2008",
                        "projectUrl": "https://github.com/FasterXML/jackson-core",
                        "description": "Core Jackson processing abstractions (aka Streaming API), implementation for JSON",
                        "name": "Jackson-core",
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
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "jackson-core-2.12.3.jar/META-INF/LICENSE",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "jackson-core-2.12.3.jar/META-INF/NOTICE",
                                "license": null
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "jackson-core"
            }
        ],
        "name": "runtime"
    }
]"""
}
