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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import static com.github.jk1.license.AbstractGradleRunnerFunctionalSpec.fixPathForBuildFile

class ConfigurationCacheSpec extends Specification {

    @TempDir
    File testProjectDir

    File settingsFile
    File buildFile
    File reportDir

    def setup() {
        reportDir = new File(testProjectDir, 'build/reports/dependency-license')
        settingsFile = new File(testProjectDir, 'settings.gradle')
        settingsFile << "rootProject.name = 'cc-test'\n"
        buildFile = new File(testProjectDir, 'build.gradle')
    }

    def "report is generated from a reused configuration cache entry with gradle #gradle.version"(GradleTestVersion gradle) {
        given:
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.7'
                implementation 'org.ehcache:ehcache:3.3.1'
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*
            licenseReport {
                filters = [new LicenseBundleNormalizer(), new ExcludeTransitiveDependenciesFilter()]
                renderers = [new JsonReportRenderer(), new InventoryHtmlReportRenderer(), new CsvReportRenderer()]
            }
        """
        File report = new File(reportDir, 'index.json')

        when:
        BuildResult result = run(gradle, 'generateLicenseReport')

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        result.output.contains('Configuration cache entry stored')
        def firstReport = report.text
        firstReport.contains('commons-lang3')
        firstReport.contains('Apache License, Version 2.0')

        when:
        reportDir.deleteDir()
        result = run(gradle, 'generateLicenseReport')

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        result.output.contains('Reusing configuration cache')
        report.text == firstReport
        new File(reportDir, 'commons-lang3-3.7.jar/META-INF/LICENSE.txt').exists()

        when:
        result = run(gradle, 'generateLicenseReport')

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE
        result.output.contains('Reusing configuration cache')

        where:
        gradle << GradleTestVersion.supportedVersionsForCurrentJvm
    }

    def "root project reports on its subprojects in parallel with gradle #gradle.version"(GradleTestVersion gradle) {
        given:
        settingsFile << "include 'sub1', 'sub2'\n"
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new JsonReportRenderer()]
                buildScriptProjects = [project]
            }
        """
        subproject('sub1', "implementation 'org.apache.commons:commons-lang3:3.7'")
        subproject('sub2', "implementation 'javax.annotation:javax.annotation-api:1.3.2'\n implementation project(':sub1')")
        File report = new File(reportDir, 'index.json')

        when:
        BuildResult result = run(gradle, '--parallel', 'generateLicenseReport')

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        result.task(':sub1:collectLicenseReportDependenciesForRoot').outcome == TaskOutcome.SUCCESS
        result.task(':sub2:collectLicenseReportDependenciesForRoot').outcome == TaskOutcome.SUCCESS
        report.text.contains('commons-lang3')
        report.text.contains('javax.annotation-api')
        // project dependencies are neither built nor reported
        result.task(':sub1:jar') == null
        !report.text.contains('"sub1"')

        when:
        reportDir.deleteDir()
        result = run(gradle, '--parallel', 'generateLicenseReport')

        then:
        result.output.contains('Reusing configuration cache')
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        report.text.contains('commons-lang3')
        report.text.contains('javax.annotation-api')

        where:
        gradle << GradleTestVersion.supportedVersionsForCurrentJvm
    }

    def "dependency changes invalidate the report with gradle #gradle.version"(GradleTestVersion gradle) {
        given:
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation "junit:junit:\${providers.gradleProperty('junitVersion').get()}"
            }
        """

        when:
        BuildResult result = run(gradle, 'generateLicenseReport', '-PjunitVersion=4.12')

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = run(gradle, 'generateLicenseReport', '-PjunitVersion=4.12')

        then:
        result.output.contains('Reusing configuration cache')
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        result = run(gradle, 'generateLicenseReport', '-PjunitVersion=4.11')

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        new File(reportDir, 'index.html').text.contains('4.11')

        where:
        gradle << GradleTestVersion.supportedVersionsForCurrentJvm
    }

    def "licenses are checked from a reused configuration cache entry with gradle #gradle.version"(GradleTestVersion gradle) {
        given:
        File allowed = new File(testProjectDir, 'allowed-licenses.json')
        allowed << '{ "allowedLicenses": [ { "moduleLicense": "Apache License, Version 2.0" } ] }'
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.7'
            }

            import com.github.jk1.license.filter.*
            licenseReport {
                filters = [new LicenseBundleNormalizer()]
                allowedLicensesFile = file("${fixPathForBuildFile(allowed.name)}")
            }
        """

        when:
        BuildResult result = run(gradle, 'checkLicense')

        then:
        result.task(':checkLicense').outcome == TaskOutcome.SUCCESS

        when:
        allowed.text = '{ "allowedLicenses": [ { "moduleLicense": "MIT License" } ] }'
        result = runAndFail(gradle, 'checkLicense')

        then:
        result.output.contains('Reusing configuration cache')
        result.task(':checkLicense').outcome == TaskOutcome.FAILED
        result.output.contains('org.apache.commons:commons-lang3')

        where:
        gradle << GradleTestVersion.supportedVersionsForCurrentJvm
    }

    def "root project reports on its subprojects in parallel without configuration cache with gradle #gradle.version"(GradleTestVersion gradle) {
        given:
        settingsFile << "include 'sub1', 'sub2'\n"
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new JsonReportRenderer()]
            }
        """
        subproject('sub1', "implementation 'org.apache.commons:commons-lang3:3.7'")
        subproject('sub2', "implementation 'javax.annotation:javax.annotation-api:1.3.2'")

        when:
        BuildResult result = runner(gradle, false, '--parallel', 'generateLicenseReport').build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        new File(reportDir, 'index.json').text.contains('commons-lang3')
        new File(reportDir, 'index.json').text.contains('javax.annotation-api')

        where:
        gradle << GradleTestVersion.supportedVersionsForCurrentJvm
    }

    private void subproject(String name, String dependencies) {
        File dir = new File(testProjectDir, name)
        dir.mkdirs()
        new File(dir, 'build.gradle') << """
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                $dependencies
            }
        """
    }

    private GradleRunner runner(GradleTestVersion gradle, boolean configurationCache = true, String... arguments) {
        GradleRunner.create()
            .withGradleVersion(gradle.version)
            .withProjectDir(testProjectDir)
            .withArguments((configurationCache ? ['--configuration-cache'] : []) + ['--stacktrace'] + arguments.toList())
            .withPluginClasspath()
            .forwardOutput()
    }

    private BuildResult run(GradleTestVersion gradle, String... arguments) {
        runner(gradle, true, arguments).build()
    }

    private BuildResult runAndFail(GradleTestVersion gradle, String... arguments) {
        runner(gradle, true, arguments).buildAndFail()
    }
}
