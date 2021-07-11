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
package com.github.jk1.license

import org.gradle.api.Project
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static com.github.jk1.license.AbstractGradleRunnerFunctionalSpec.fixPathForBuildFile

class PluginSpec extends Specification {

    private final static def supportedGradleVersions = ["5.6", "6.8.2"]
    private final static def unsupportedGradleVersions = [ "4.10" ]

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File outputDir
    File licenseResultJsonFile

    def setup() {
        testProjectDir.create()
        outputDir = new File(testProjectDir.root, "/build/licenses")
        licenseResultJsonFile = new File(outputDir, "index.json")

        buildFile = testProjectDir.newFile('build.gradle')

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            apply plugin:'java'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.ehcache:ehcache:3.3.1'
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                filters = [ new LicenseBundleNormalizer() ]
                renderers = [ new InventoryHtmlReportRenderer('report.html','Backend'), new JsonReportRenderer(onlyOneLicensePerModule: false) ]
                configurations = ALL
            }
        """
    }

    def "plugin should be applicable to a project"() {
        Project project = org.gradle.testfixtures.ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply 'com.github.jk1.dependency-license-report'

        then:
        project.licenseReport
    }

    @Unroll
    def "run plugin with gradle #gradleVersion"(String gradleVersion) {
        when:
        def runResult = runGradle(gradleVersion)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseResultJsonFile.exists()
        licenseResultJsonFile.text == """{
    "dependencies": [
        {
            "moduleName": "org.ehcache:ehcache",
            "moduleVersion": "3.3.1",
            "moduleUrls": [
                "ehcache-3.3.1.jar/LICENSE.html",
                "http://ehcache.org"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                },
                {
                    "moduleLicense": "LICENSE",
                    "moduleLicenseUrl": null
                }
            ]
        },
        {
            "moduleName": "org.slf4j:slf4j-api",
            "moduleVersion": "1.7.7",
            "moduleUrls": [
                "http://www.slf4j.org"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "MIT License",
                    "moduleLicenseUrl": "https://opensource.org/licenses/MIT"
                }
            ]
        }
    ]
}"""

        where:
        gradleVersion << supportedGradleVersions
    }

    @Unroll
    def "the plugin doesn't start if the required gradle-version is not met (#gradleVersion)"(String gradleVersion) {
        when:
        runGradle(gradleVersion)

        then:
        def ex = thrown(UnexpectedBuildFailure)
        ex.message.contains("License Report Plugin requires Gradle")

        where:
        gradleVersion << unsupportedGradleVersions
    }

    private def runGradle(String gradleVersion) {
        GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments("generateLicenseReport", "--info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()
    }
}
