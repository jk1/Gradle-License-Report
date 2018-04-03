package com.github.jk1.license

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class PluginCompatibilityTest extends Specification {

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
                outputDir = "$outputDir.absolutePath"
                filters = [ new LicenseBundleNormalizer() ]
                renderers = [ new InventoryHtmlReportRenderer('report.html','Backend'), new JsonReportRenderer(onlyOneLicensePerModule: false) ]
                configurations = ALL
            }
        """
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
                "https://github.com/ehcache/sizeof"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": null,
                    "moduleLicenseUrl": "LICENSE"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
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
        gradleVersion << [ "3.3", "3.5.1", "4.0.1", "4.6" ]
    }

    @Unroll
    def "the plugin doesn't start if the required gradle-version is not met (#gradleVersion)"(String gradleVersion) {
        when:
        runGradle(gradleVersion)

        then:
        def ex = thrown(UnexpectedBuildFailure)
        ex.message.contains("License Report Plugin requires Gradle")

        where:
        gradleVersion << [ "3.2" ]
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
