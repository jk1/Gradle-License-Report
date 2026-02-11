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
                id 'com.github.hierynomus.license-base' version '0.16.1' apply false
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
                id 'com.diffplug.spotless' version '6.13.0' apply false
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

        !new File(testProjectDir, "build/reports/dependency-license").exists()
        new File(testProjectDir, "sub1/build/reports/dependency-license").list().contains("xmltool-3.3.jar")
        new File(testProjectDir, "sub2/build/reports/dependency-license").list().contains("org.eclipse.jgit-5.13.1.202206130422-r.jar")

        configurationsSub1String.contains("mycila-xmltool")
        !configurationsSub1String.contains("JGit - Core")

        configurationsSub2String.contains("JGit - Core")
        !configurationsSub2String.contains("mycila-xmltool")
    }

    def "plugin is executed in module independently and globally on root project when configured in allprojects"() {
        setup:
        settingsGradle = new File(testProjectDir, "settings.gradle")

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'com.github.ben-manes.versions' version '0.48.0' apply false
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
                id 'com.github.hierynomus.license-base' version '0.16.1' apply false
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
                id 'com.diffplug.spotless' version '6.13.0' apply false
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
        new File(testProjectDir, "build/reports/dependency-license").list().contains("mxparser-1.2.2.jar")
        new File(testProjectDir, "build/reports/dependency-license").list().contains("xmltool-3.3.jar")
        new File(testProjectDir, "build/reports/dependency-license").list().contains("org.eclipse.jgit-5.13.1.202206130422-r.jar")

        new File(testProjectDir, "sub1/build/reports/dependency-license").list().contains("xmltool-3.3.jar")
        new File(testProjectDir, "sub2/build/reports/dependency-license").list().contains("org.eclipse.jgit-5.13.1.202206130422-r.jar")

        configurationsRootString.contains("mycila-xmltool")
        configurationsRootString.contains("JGit - Core")
        configurationsRootString.contains("mxparser")

        configurationsSub1String.contains("mycila-xmltool")
        !configurationsSub1String.contains("JGit - Core")
        !configurationsSub1String.contains("mxparser")

        !configurationsSub2String.contains("mycila-xmltool")
        configurationsSub2String.contains("JGit - Core")
        !configurationsSub2String.contains("mxparser")
    }
}
