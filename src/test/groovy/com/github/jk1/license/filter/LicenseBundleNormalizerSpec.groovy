package com.github.jk1.license.filter

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Specification

class LicenseBundleNormalizerSpec extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File normalizerFile

    File licenseResultJsonFile
    def jsonSlurper = new JsonSlurper()

    def setup() {
        testProjectDir.create()
        licenseResultJsonFile = new File(testProjectDir.root, "/build/licenses/index.json")

        buildFile = testProjectDir.newFile('build.gradle')
        normalizerFile = testProjectDir.newFile('test-normalizer-config.json')

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "$licenseResultJsonFile.parentFile.absolutePath"
                filters = new LicenseBundleNormalizer("$normalizerFile.absolutePath")
                renderers = new JsonReportRenderer()
                configurations = ['forTesting']
            }
        """

        normalizerFile << """
            {
              "bundles" : [
                { "bundleName" : "apache1", "licenseName" : "Apache Software License, Version 1.1", "licenseUrl" : "http://www.apache.org/licenses/LICENSE-1.1" },
                { "bundleName" : "apache2", "licenseName" : "Apache License, Version 2.0", "licenseUrl" : "http://www.apache.org/licenses/LICENSE-2.0" },
                { "bundleName" : "cddl1", "licenseName" : "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0", "licenseUrl" : "http://opensource.org/licenses/CDDL-1.0" }
              ],
        """
    }

    def "normalizes dependencies by configured license name"() {
        buildFile << """
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-name: "The Apache Software License, Version 2.0"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-name: "Apache License, Version 2.0"
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseNamePattern" : ".*The Apache Software License, Version 2.0.*" }
              ]
            }
        """

        when:
        def runResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('generateLicenseReport')
                .withPluginClasspath()
                .build()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    def "normalizes dependencies by configured license url"() {
        buildFile << """
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-url: "http://www.apache.org/licenses/LICENSE-2.0.txt"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-url: "http://www.apache.org/licenses/LICENSE-2.0"
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseUrlPattern" : ".*http://www.apache.org/licenses/LICENSE-2.0.txt.*" }
              ]
            }
        """

        when:
        def runResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('generateLicenseReport')
                .withPluginClasspath()
                .build()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    def "an error is raised when a normalizer file is specified but not available"() {
        normalizerFile.delete()

        when:
        GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('generateLicenseReport', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        thrown(Exception)
    }

    def "default filter file is used when nothing else specified"() {
        buildFile.setText("") // clear the file first
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "$licenseResultJsonFile.parentFile.absolutePath"
                filters = new LicenseBundleNormalizer()
                renderers = new JsonReportRenderer()
                configurations = ['forTesting']
            }
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-url: "http://www.apache.org/licenses/LICENSE-2.0.txt"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-url: "http://www.apache.org/licenses/LICENSE-2.0"
            }
        """

        when:
        def runResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('generateLicenseReport', '--stacktrace')
                .withPluginClasspath()
                .build()

        def result = jsonSlurper.parse(licenseResultJsonFile)
        println(licenseResultJsonFile.text)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }



    def "it normalizes the dependency on all specified configurations"() {
        buildFile.setText("") // clear the file first
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting1
                forTesting2
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*

            licenseReport {
                outputDir = "$licenseResultJsonFile.parentFile.absolutePath"
                filters = [ new LicenseBundleNormalizer() ]
                renderers = new JsonReportRenderer()
                configurations = ['forTesting1', 'forTesting2']
            }

            dependencies {
                forTesting1 "org.apache.httpcomponents:httpcore:4.4.9"
                forTesting2 "org.apache.httpcomponents:httpcore:4.4.9"
            }
        """

        when:
        def runResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('generateLicenseReport', '--stacktrace')
                .withPluginClasspath()
                .build()

        def result = jsonSlurper.parse(licenseResultJsonFile)
        println(licenseResultJsonFile.text)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    @Ignore("add support for licence-text")
    def "normalizes dependencies by configured license text"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.9" // license-name: Apache 2
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseFileContentPattern" : ".*Apache License, Version 2.0.*" }
              ]
            }
        """

        when:
        def runResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('generateLicenseReport')
                .withPluginClasspath()
                .build()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 1
        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    @Ignore("Think about multiple licenses of a file")
    def "normalizes combined licenses without losing information of one of the single licenses"() {
        // TODO
        // e.g. javax.annotation:javax.annotation-api   CDDL + GPL
    }
}
