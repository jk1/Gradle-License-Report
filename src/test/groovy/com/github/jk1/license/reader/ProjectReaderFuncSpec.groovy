package com.github.jk1.license.reader

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProjectReaderFuncSpec extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File outputDir
    File rawJsonFile
    List<File> pluginClasspath
    JsonSlurper jsonSlurper = new JsonSlurper()

    def setup() {
        testProjectDir.create()
        outputDir = new File(testProjectDir.root, "/build/licenses")
        rawJsonFile = new File(outputDir, RawProjectDataJsonRenderer.RAW_PROJECT_JSON_NAME)

        buildFile = testProjectDir.newFile('build.gradle')
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

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "$outputDir.absolutePath"
                renderer = new com.github.jk1.license.reader.RawProjectDataJsonRenderer()
                configurations = ['forTesting']
            }
        """

        // add also the test-classes to the classpath to access some helper-renderers
        def classpath = PluginUnderTestMetadataReading.readImplementationClasspath()
        pluginClasspath = classpath + classpath.collect {
            // there is surely a better way to add the test-classpath. Help appreciated.
            new File(it.parentFile, "test")
        }
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

        licenseFileString == """[
    {
        "fileDetails": [
            {
                "licenseUrl": null,
                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                "license": null
            },
            {
                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
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


    private def runGradleBuild() {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('generateLicenseReport', '--stacktrace')
            .withPluginClasspath(pluginClasspath)
            .build()
    }

    private static String prettyPrintJson(Object obj) {
        new JsonBuilder(obj).toPrettyString()
    }
}
