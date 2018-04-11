package com.github.jk1.license.filter

import com.github.jk1.license.AbstractGradleRunnerFunctionalSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore

import static com.github.jk1.license.reader.ProjectReaderFuncSpec.prettyPrintJson

class LicenseBundleNormalizerFuncSpec extends AbstractGradleRunnerFunctionalSpec {

    File licenseResultJsonFile
    File normalizerFile

    def setup() {
        licenseResultJsonFile = new File(outputDir, "index.json")

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
            import com.github.jk1.license.reader.*
            licenseReport {
                outputDir = "$outputDir.absolutePath"
                filters = new LicenseBundleNormalizer(bundlePath: "$normalizerFile.absolutePath", createDefaultTransformationRules: false)
                renderer = new MultiReportRenderer(new JsonReportRenderer(onlyOneLicensePerModule: false), new RawProjectDataJsonRenderer())
                configurations = ['forTesting']
            }
        """

        normalizerFile << """
            {
              "bundles" : [
                { "bundleName" : "apache1", "licenseName" : "Apache Software License, Version 1.1", "licenseUrl" : "https://www.apache.org/licenses/LICENSE-1.1" },
                { "bundleName" : "apache2", "licenseName" : "Apache License, Version 2.0", "licenseUrl" : "https://www.apache.org/licenses/LICENSE-2.0" },
                { "bundleName" : "cddl1", "licenseName" : "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0", "licenseUrl" : "https://opensource.org/licenses/CDDL-1.0" },
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
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["https://www.apache.org/licenses/LICENSE-2.0", "http://www.apache.org/licenses/LICENSE-2.0"].toSet()
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
                { "bundleName" : "apache2", "licenseUrlPattern" : ".*www.apache.org/licenses/LICENSE-2.0.*" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["https://www.apache.org/licenses/LICENSE-2.0", null].toSet()
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
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["http://www.apache.org/licenses/LICENSE-2.0"/*LicenseFile*/, "https://www.apache.org/licenses/LICENSE-2.0"/*Pom*/, null/*Manifest*/].toSet()
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

    def "licenseFileDetails are extended with the license information"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.9"
                forTesting "org.apache.commons:commons-lang3:3.7"
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
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        def licenseFilesGPath = resultFileGPath.configurations*.dependencies.flatten().licenseFiles.flatten()
        def licenseFileString = prettyPrintJson(licenseFilesGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseFileString == """[
    {
        "fileDetails": [
            {
                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                "file": "joda-time-2.9.9.jar/META-INF/LICENSE.txt",
                "license": "Apache License, Version 2.0"
            },
            {
                "licenseUrl": null,
                "file": "joda-time-2.9.9.jar/META-INF/NOTICE.txt",
                "license": null
            }
        ],
        "files": [
            "joda-time-2.9.9.jar/META-INF/LICENSE.txt",
            "joda-time-2.9.9.jar/META-INF/NOTICE.txt"
        ]
    },
    {
        "fileDetails": [
            {
                "licenseUrl": null,
                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                "license": null
            },
            {
                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                "license": "Apache License, Version 2.0"
            }
        ],
        "files": [
            "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
            "commons-lang3-3.7.jar/META-INF/LICENSE.txt"
        ]
    }
]"""
    }

    def "normalizes dependencies by configured license text and exports result to json"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.9"
                forTesting "org.apache.commons:commons-lang3:3.7"
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

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseResultJsonFile.text == """{
    "dependencies": [
        {
            "moduleName": "joda-time:joda-time",
            "moduleVersion": "2.9.9",
            "moduleUrls": [
                "http://www.joda.org/joda-time/",
                "http://www.joda.org/joda-time/"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache 2.0",
                    "moduleLicenseUrl": null
                },
                {
                    "moduleLicense": "Apache 2",
                    "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0.txt"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        },
        {
            "moduleName": "org.apache.commons:commons-lang3",
            "moduleVersion": "3.7",
            "moduleUrls": [
                "http://commons.apache.org/proper/commons-lang/",
                "http://commons.apache.org/proper/commons-lang/"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": null,
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        }
    ]
}"""
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
}
