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

class ReportTaskCachingSpec extends Specification {

    @TempDir
    File testProjectDir

    File buildFile
    File localBuildCacheDirectory

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        localBuildCacheDirectory = new File(testProjectDir, '.local-cache')
        localBuildCacheDirectory.mkdir()
        new File(testProjectDir, 'settings.gradle') << """
        buildCache {
            local {
                directory '${localBuildCacheDirectory.toURI()}'
            }
        }
    """
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "junit:junit:\${project.ext.junitVersion}"
            }
        """
    }

    def "should calculate up-to-date correctly"() {
        when:
        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

    }

    def "should cache task outputs"() {
        when:
        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "clean", "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.FROM_CACHE
    }

    def "should rebuild report on dependency change"() {
        when:
        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.11")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

    }

    def "should rebuild report on configured projects change"() {
        when:
        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.11")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

    }

    def "should cache task outputs for filter"() {
        when:
        addFilterToBuildFile("foo")
        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addFilterToBuildFile("foo")
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        addFilterToBuildFile("bar")
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addFilterToBuildFile("bar")
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

    }

    def "should cache task outputs for renderer"() {
        when:
        addRendererToBuildFile("foo")
        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addRendererToBuildFile("foo")
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        addRendererToBuildFile("bar")
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addRendererToBuildFile("bar")
        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE
    }

    private def addFilterToBuildFile(String string) {
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }

            repositories {
                mavenCentral()
            }

            apply plugin: 'java'

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.ProjectData
            import org.gradle.api.tasks.Input

            class MyFilter implements DependencyFilter{
                private String input

                MyFilter(String string) {
                    this.input = string
                }

                @Input
                private String getInputCache() { return this.input }

                @Override
                ProjectData filter(ProjectData source) {
                    return source
                }
            }

            dependencies {
                compile "junit:junit:\${project.ext.junitVersion}"
            }

            licenseReport {
                filters = [new MyFilter("${string}")]
            }
        """
    }

    private addRendererToBuildFile(String string) {
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }

            repositories {
                mavenCentral()
            }

            apply plugin: 'java'

            import com.github.jk1.license.render.*
            import com.github.jk1.license.ProjectData
            import org.gradle.api.tasks.Input

            class MyRenderer implements ReportRenderer{

                private String input
                MyRenderer(String string) {
                    this.input = string
                }

                @Input
                private String getInputCache() { return this.input }

                @Override
                void render(ProjectData data) {
                }
            }

            dependencies {
                compile "junit:junit:\${project.ext.junitVersion}"
            }

            licenseReport {
                renderers = [new MyRenderer("${string}")]
            }
        """
    }
}
