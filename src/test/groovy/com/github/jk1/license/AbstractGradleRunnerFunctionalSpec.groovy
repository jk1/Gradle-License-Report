package com.github.jk1.license

import com.github.jk1.license.render.RawProjectDataJsonRenderer
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractGradleRunnerFunctionalSpec extends Specification {
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

        pluginClasspath = buildPluginClasspathWithTestClasspath()

        buildFile = testProjectDir.newFile('build.gradle')
    }

    protected def runGradleBuild(List<String> additionalArguments = []) {
        List<String> args = ['generateLicenseReport', '--stacktrace']

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(args + additionalArguments)
            .withPluginClasspath(pluginClasspath)
            .forwardOutput()
            .build()
    }

    static String prettyPrintJson(Object obj) {
        new JsonBuilder(obj).toPrettyString()
    }

    static List<File> buildPluginClasspathWithTestClasspath() {
        // add also the test-classes to the classpath to access some helper-renderers
        def classpath = PluginUnderTestMetadataReading.readImplementationClasspath()
        return classpath + classpath.collect {
            // there is surely a better way to add the test-classpath. Help appreciated.
            new File(it.parentFile, "test")
        }
    }

}
