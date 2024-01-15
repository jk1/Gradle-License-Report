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

import org.gradle.testkit.runner.TaskOutcome

import static com.github.jk1.license.reader.ProjectReaderFuncSpec.removeDevelopers

class MultiProjectBuildScriptFuncSpec extends AbstractGradleRunnerFunctionalSpec {

    def "plugin is executed in each module independently if configured for submodules"() {
        setup:
        settingsGradle = new File(testProjectDir, "settings.gradle")

        newSubBuildFile("sub1") << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'org.owasp.dependencycheck' version '9.0.7'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                filters = new com.github.jk1.license.filter.LicenseBundleNormalizer()
                configurations = []
                buildScriptProjects = [project] + project.subprojects
            }
        """

        newSubBuildFile("sub2") << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'com.diffplug.spotless' version '6.24.0'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                filters = new com.github.jk1.license.filter.LicenseBundleNormalizer()
                configurations = []
                buildScriptProjects = [project] + project.subprojects
            }
        """

        when:
        def runResult = runGradleBuild()
        def sub1RawGPath = jsonSlurper.parse(new File(testProjectDir, "sub1/build/reports/dependency-license/raw-project-data.json"))
        def sub2RawGPath = jsonSlurper.parse(new File(testProjectDir, "sub2/build/reports/dependency-license/raw-project-data.json"))
        removeDevelopers(sub1RawGPath)
        removeDevelopers(sub2RawGPath)
        def configurationsSub1String = prettyPrintJson(sub1RawGPath.configurations)
        def configurationsSub2String = prettyPrintJson(sub2RawGPath.configurations)

        then:
        runResult.task(":sub1:generateLicenseReport").outcome == TaskOutcome.SUCCESS
        runResult.task(":sub2:generateLicenseReport").outcome == TaskOutcome.SUCCESS

        System.out.println(testProjectDir)
        !new File(testProjectDir, "build/reports/dependency-license").exists()
        new File(testProjectDir, "sub1/build/reports/dependency-license/dependency-check-core-9.0.7.jar/META-INF/LICENSE.txt").exists()
        new File(testProjectDir, "sub2/build/reports/dependency-license/commons-codec-1.16.0.jar/META-INF/NOTICE.txt").exists()
        new File(testProjectDir, "sub2/build/reports/dependency-license/commons-codec-1.16.0.jar/META-INF/LICENSE.txt").exists()

        configurationsSub1String.contains("Dependency-Check Core")
        !configurationsSub1String.contains("commons-codec")

        configurationsSub2String.contains("commons-codec")
        !configurationsSub2String.contains("Dependency-Check Core")
    }

    def "plugin is executed in module independently and globally on root project when configured in allprojects"() {
        setup:
        settingsGradle = new File(testProjectDir, "settings.gradle")

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'com.github.ben-manes.versions' version '0.48.0'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                filters = new com.github.jk1.license.filter.LicenseBundleNormalizer()
                configurations = []
                buildScriptProjects = [project] + project.subprojects
            }
"""

        newSubBuildFile("sub1") << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'org.owasp.dependencycheck' version '9.0.7'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                filters = new com.github.jk1.license.filter.LicenseBundleNormalizer()
                configurations = []
                buildScriptProjects = [project] + project.subprojects
            }
        """

        newSubBuildFile("sub2") << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'com.diffplug.spotless' version '6.24.0'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                filters = new com.github.jk1.license.filter.LicenseBundleNormalizer()
                configurations = []
                buildScriptProjects = [project] + project.subprojects
            }
        """

        when:
        def runResult = runGradleBuild()
        def rootRawGPath = jsonSlurper.parse(new File(testProjectDir, "build/reports/dependency-license/raw-project-data.json"))
        def sub1RawGPath = jsonSlurper.parse(new File(testProjectDir, "sub1/build/reports/dependency-license/raw-project-data.json"))
        def sub2RawGPath = jsonSlurper.parse(new File(testProjectDir, "sub2/build/reports/dependency-license/raw-project-data.json"))
        def configurationsRootString = prettyPrintJson(rootRawGPath.configurations)
        def configurationsSub1String = prettyPrintJson(sub1RawGPath.configurations)
        def configurationsSub2String = prettyPrintJson(sub2RawGPath.configurations)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        runResult.task(":sub1:generateLicenseReport").outcome == TaskOutcome.SUCCESS
        runResult.task(":sub2:generateLicenseReport").outcome == TaskOutcome.SUCCESS

        // root project should contains all the deps
        new File(testProjectDir, "build/reports/dependency-license/mxparser-1.2.2.jar/META-INF/LICENSE").exists()
        new File(testProjectDir, "build/reports/dependency-license/dependency-check-core-9.0.7.jar/META-INF/LICENSE.txt").exists()
        new File(testProjectDir, "build/reports/dependency-license/commons-codec-1.16.0.jar/META-INF/NOTICE.txt").exists()
        new File(testProjectDir, "build/reports/dependency-license/commons-codec-1.16.0.jar/META-INF/LICENSE.txt").exists()

        new File(testProjectDir, "sub1/build/reports/dependency-license/dependency-check-core-9.0.7.jar/META-INF/LICENSE.txt").exists()

        new File(testProjectDir, "sub2/build/reports/dependency-license/commons-codec-1.16.0.jar/META-INF/NOTICE.txt").exists()
        new File(testProjectDir, "sub2/build/reports/dependency-license/commons-codec-1.16.0.jar/META-INF/LICENSE.txt").exists()

        configurationsRootString.contains("Dependency-Check Core")
        configurationsRootString.contains("commons-codec")
        configurationsRootString.contains("mxparser")

        configurationsSub1String.contains("Dependency-Check Core")
        !configurationsSub1String.contains("commons-codec")
        !configurationsSub1String.contains("mxparser")

        !configurationsSub2String.contains("Dependency-Check Core")
        configurationsSub2String.contains("commons-codec")
        !configurationsSub2String.contains("mxparser")


    }
}
