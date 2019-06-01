package com.github.jk1.license


import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class MultiProjectReportCachingSpec extends Specification {

    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    File buildFile
    File localBuildCacheDirectory

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        localBuildCacheDirectory = testProjectDir.newFolder('.local-cache')
        testProjectDir.newFile('settings.gradle') << """
        buildCache {
            local {
                directory '${localBuildCacheDirectory.toURI()}'
            }
        }
        
        include 'sub'
    """
    }


    def "task cache should be invalidated on configured project set change"() {
        when:
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }

            licenseReport {
                projects = [project]
            }
        """

        File subFolder = new File(testProjectDir.root, "sub")
        subFolder.mkdirs()
        File subFile = new File(subFolder, "build.gradle")
        subFile.createNewFile()
        subFile << """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile "javax.annotation:javax.annotation-api:1.3.2"
            }
        """


        BuildResult result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments('--build-cache', "generateLicenseReport")
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        buildFile.delete()
        testProjectDir.newFile('build.gradle') << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
        """

        result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments('--build-cache', "generateLicenseReport", "--debug")
            .forwardOutput()
            .build()

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
    }
}
