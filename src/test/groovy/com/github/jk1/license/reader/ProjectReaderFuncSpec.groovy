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
import spock.lang.IgnoreIf
import spock.lang.Snapshot
import spock.lang.Snapshotter

class ProjectReaderFuncSpec extends AbstractGradleRunnerFunctionalSpec {
    @Snapshot(extension = 'json')
    Snapshotter snapshotter

    def setup() {
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
                maven { url "https://oss.jfrog.org/artifactory/oss-snapshot-local" }
                maven { url "https://maven.repository.redhat.com/ga" }
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = ['forTesting']
            }
        """
    }

    def "it stores the licenses of a jar-file into the output-dir"() {
        buildFile << """
            dependencies {
                forTesting "org.apache.commons:commons-lang3:3.7" // has NOTICE.txt and LICENSE.txt
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        new File(outputDir, "commons-lang3-3.7.jar/META-INF/NOTICE.txt").exists()
        new File(outputDir, "commons-lang3-3.7.jar/META-INF/LICENSE.txt").exists()
    }

    def "the project-data contains the license-file information"() {
        buildFile << """
            dependencies {
                forTesting "org.apache.commons:commons-lang3:3.7" // has NOTICE.txt and LICENSE.txt
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        def licenseFilesGPath = resultFileGPath.configurations*.dependencies.flatten().licenseFiles.flatten()
        def licenseFileString = prettyPrintJson(licenseFilesGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        snapshotter.assertThat(licenseFileString).matchesSnapshot()
    }

    def "it reads the correct project url for #dependency"() {
        buildFile << """
            dependencies {
                forTesting "org.ehcache:ehcache:3.3.1"
            }
        """

        when:
        def runResult = runGradleBuild(["--debug"])
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def pomGPath = resultFileGPath.configurations*.dependencies.flatten().poms.flatten()
        def pomsString = prettyPrintJson(pomGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        snapshotter.assertThat(pomsString).matchesSnapshot()
    }

    def "it reads the correct project group from its parent when not available locally for #dependency"() {
        buildFile << """
            dependencies {
                forTesting "org.apache.commons:commons-lang3:3.7"
            }
        """

        when:
        def runResult = runGradleBuild(["--debug"])
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def pomGPath = resultFileGPath.configurations*.dependencies.flatten().poms.flatten()

        def pomsString = prettyPrintJson(pomGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        !runResult.output.contains("Use remote pom because the found pom seems not to represent artifact.")
        snapshotter.assertThat(pomsString).matchesSnapshot()
    }

    def "it ignores POM parsing errors"(){
        buildFile << """
            dependencies {
                forTesting "org.codehaus.woodstox:stax2-api:3.1.3.redhat-1" // has invalid pom.xml
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
    }

    def "it fetches remote POM if packaged one contains no license"(){
        buildFile << """
            dependencies {
                forTesting("org.opensaml:opensaml:2.6.4") {
                    transitive = false
                }
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations[0].dependencies[0].poms
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        snapshotter.assertThat(configurationsString).matchesSnapshot()
    }


    def "it reads dependencies correctly"() {
        buildFile << """
            dependencies {
                forTesting "org.springframework:spring-tx:3.2.3.RELEASE"
                forTesting "org.ehcache:ehcache:3.3.1"
                forTesting "org.apache.commons:commons-lang3:3.7"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        snapshotter.assertThat(configurationsString).matchesSnapshot()
    }

    @IgnoreIf(value = { !jvm.isJavaVersionCompatible(11) }, reason = "openjfx under test requires Java 11")
    def "it reads dependencies with variants correctly"() {
        buildFile.delete()
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'org.openjfx.javafxplugin' version '0.1.0'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = ['forTesting']
            }

            dependencies {
                forTesting("org.openjfx:javafx-base:22.0.1") {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(OperatingSystemFamily, OperatingSystemFamily.LINUX))
                        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(MachineArchitecture, MachineArchitecture.X86_64))
                    }
               }
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        snapshotter.assertThat(configurationsString).matchesSnapshot()
    }

    static void removeDevelopers(Map rawFile) {
        rawFile.configurations*.dependencies.flatten().poms.flatten().each { it.remove("developers") }
    }
}
