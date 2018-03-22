package com.github.jk1.license.filter

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Specification

class LicenseBundleNormalizerFuncSpec extends Specification {

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
                renderer = new JsonReportRenderer(onlyOneLicensePerModule: false)
                configurations = ['forTesting']
            }
        """

        normalizerFile << """
            {
              "bundles" : [
                { "bundleName" : "apache1", "licenseName" : "Apache Software License, Version 1.1", "licenseUrl" : "http://www.apache.org/licenses/LICENSE-1.1" },
                { "bundleName" : "apache2", "licenseName" : "Apache License, Version 2.0", "licenseUrl" : "http://www.apache.org/licenses/LICENSE-2.0" },
                { "bundleName" : "cddl1", "licenseName" : "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0", "licenseUrl" : "http://opensource.org/licenses/CDDL-1.0" },
                { "bundleName" : "gpl2", "licenseName" : "GNU GENERAL PUBLIC LICENSE, Version 2", "licenseUrl" : "https://www.gnu.org/licenses/gpl-2.0" }
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
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0", null].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
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
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0", null].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    def "normalizes manifest with pom data"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.7" // has manifest and pom information
            }
        """
        normalizerFile << """
              "transformationRules" : [
                   { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2.*" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0", null].toSet()
    }

    def "an error is raised when a normalizer file is specified but not available"() {
        normalizerFile.delete()

        when:
        runGradleBuild()

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
                renderer = new JsonReportRenderer()
                configurations = ['forTesting']
            }
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-url: "http://www.apache.org/licenses/LICENSE-2.0.txt"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-url: "http://www.apache.org/licenses/LICENSE-2.0"
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

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
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 1
        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    @Ignore("Think about multiple licenses in a module AND multiple licenses within one license-name")
    def "normalizes combined licenses without losing information of one of the single licenses"() {
        buildFile << """
            dependencies {
                forTesting "javax.annotation:javax.annotation-api:1.3.2" // CDDL + GPL
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "cddl1", "licenseNamePattern" : "CDDL + GPLv2 with classpath exception" },
                { "bundleName" : "gpl2", "licenseNamePattern" : "CDDL + GPLv2 with classpath exception" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 1
        result.dependencies*.moduleLicense.toSet() == ["Apache License, Version 2.0", "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0"].toSet()
        result.dependencies*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0", "http://opensource.org/licenses/CDDL-1.0"].toSet()
    }


    private def runGradleBuild() {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('generateLicenseReport', '--stacktrace')
            .withPluginClasspath()
            .build()
    }
}
