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

import com.github.jk1.license.render.RawProjectDataJsonRenderer
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import spock.lang.Specification
import spock.lang.TempDir

abstract class AbstractGradleRunnerFunctionalSpec extends Specification {

    @TempDir
    File testProjectDir

    File settingsGradle
    File buildFile
    File outputDir
    File rawJsonFile

    List<File> pluginClasspath
    JsonSlurper jsonSlurper = new JsonSlurper()

    def setup() {
        outputDir = new File(testProjectDir, "/build/licenses")
        rawJsonFile = new File(outputDir, RawProjectDataJsonRenderer.RAW_PROJECT_JSON_NAME)

        pluginClasspath = buildPluginClasspathWithTestClasspath()

        buildFile = new File(testProjectDir, 'build.gradle')
    }

    protected def runGradleBuild(List<String> additionalArguments = []) {
        List<String> args = ['generateLicenseReport', '--stacktrace']

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(args + additionalArguments)
            .withPluginClasspath(pluginClasspath)
            .withDebug(true)
            .forwardOutput()
            .build()
    }

    protected File newSubBuildFile(String subFolderName) {
        File subFolder = new File(testProjectDir, subFolderName)
        subFolder.mkdirs()
        settingsGradle << "include '${subFolderName.replace('/', ':')}'\n"

        File buildFile = new File(subFolder, "build.gradle")
        buildFile.createNewFile()
        buildFile
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

    // When a path is used in a gradle build script, a single backslash breaks the script.
    // Therefore to avoid any problems, a normal slash is used (and it seems to work)
    static String fixPathForBuildFile(String path) {
        return path.replaceAll("\\\\", "/")
    }
}
