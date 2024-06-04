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

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.IgnoreIf
import spock.lang.Snapshot
import spock.lang.Snapshotter
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import spock.util.environment.Jvm

import static com.github.jk1.license.AbstractGradleRunnerFunctionalSpec.fixPathForBuildFile

class PluginSpec extends Specification {
    @Snapshot(extension = 'json')
    Snapshotter snapshotter

    // See https://endoflife.date/gradle and/or https://docs.gradle.org/current/userguide/compatibility.html
    private final static def supportedVersions = [
            new GradleTestVersion(version: '7.6.6',  minJdk: 8,  maxJdk: 19),
            new GradleTestVersion(version: '8.14.4', minJdk: 8,  maxJdk: 24),
            new GradleTestVersion(version: '9.3.1',  minJdk: 17, maxJdk: 25),
    ]

    private final static def unsupportedVersions = [
            new GradleTestVersion(version: '5.6.4',  minJdk: 8, maxJdk: 12),
            new GradleTestVersion(version: '6.9.4',  minJdk: 8, maxJdk: 16),
    ]

    static class GradleTestVersion {
        String version
        int minJdk
        int maxJdk
        def jdkNotInSupportedRange(Jvm jvm) { !(minJdk..maxJdk).contains(JavaVersion.toVersion(jvm.javaSpecificationVersion).majorVersion.toInteger()) }
    }

    @TempDir
    File testProjectDir

    File buildFile
    File outputDir
    File licenseResultJsonFile

    def setup() {
        outputDir = new File(testProjectDir, "build/licenses")
        licenseResultJsonFile = new File(outputDir, "index.json")

        buildFile = new File(testProjectDir, 'build.gradle')

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            apply plugin:'java'

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'org.ehcache:ehcache:3.3.1'
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
    @IgnoreIf(value = { (data.gradle as GradleTestVersion).jdkNotInSupportedRange(jvm) }, reason = "Java version is not supported by gradle version")
    def "run plugin with gradle #gradle.version"(GradleTestVersion gradle) {
        when:
        def runResult = runGradle(gradle.version)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseResultJsonFile.exists()
        snapshotter.assertThat(licenseResultJsonFile.text).matchesSnapshot()

        where:
        gradle << supportedVersions
    }

    @Unroll
    @IgnoreIf(value = { (data.gradle as GradleTestVersion).jdkNotInSupportedRange(jvm) }, reason = "Java version is not supported by gradle version")
    def "the plugin doesn't start if the required gradle-version is not met (#gradle.version)"(GradleTestVersion gradle) {
        when:
        runGradle(gradle.version)

        then:
        def ex = thrown(UnexpectedBuildFailure)
        ex.message.contains("License Report Plugin requires Gradle")

        where:
        gradle << unsupportedVersions
    }

    private def runGradle(String gradleVersion) {
        GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir)
                .withArguments("generateLicenseReport", "--info", "--stacktrace")
                .withPluginClasspath()
                .withDebug(true)
                .forwardOutput()
                .build()
    }
}
