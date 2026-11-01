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
        localBuildCacheDirectory = new File(testProjectDir, '.local-cache')
        localBuildCacheDirectory.mkdir()
        new File(testProjectDir, 'settings.gradle') << """
            buildCache {
                local {
                    directory '${localBuildCacheDirectory.toURI()}'
                }
            }
        """
        buildFile = new File(testProjectDir, 'build.gradle')
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation "junit:junit:\${project.ext.junitVersion}"
            }
        """
    }

    def "should calculate up-to-date correctly"() {
        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

    }

    def "should cache task outputs"() {
        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "clean", "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.FROM_CACHE
    }

    def "should rebuild report on dependency change"() {
        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.11")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

    }

    def "should rebuild report on configured projects change"() {
        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.11")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

    }

    def "should invalidate cache when transitive dependency version changes"() {
        setup:
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation "junit:junit:4.12"
            }
            configurations.all {
                resolutionStrategy {
                    force "org.hamcrest:hamcrest-core:\${project.ext.hamcrestVersion}"
                }
            }
        """

        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PhamcrestVersion=1.3")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "clean", "generateLicenseReport", "-PhamcrestVersion=1.3")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.FROM_CACHE

        when:
        result = runBuildWith('--build-cache', "clean", "generateLicenseReport", "-PhamcrestVersion=1.1")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
    }

    def "should cache task outputs for filter"() {
        when:
        addFilterToBuildFile("foo")
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addFilterToBuildFile("foo")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        addFilterToBuildFile("bar")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addFilterToBuildFile("bar")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

    }

    def "should cache task outputs for renderer"() {
        when:
        addRendererToBuildFile("foo")
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addRendererToBuildFile("foo")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        addRendererToBuildFile("bar")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        addRendererToBuildFile("bar")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE
    }

    def "should rebuild report when settings of a built-in renderer change"() {
        when:
        setBuiltInComponents("new InventoryHtmlReportRenderer('index.html', 'Title A')")
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        setBuiltInComponents("new InventoryHtmlReportRenderer('index.html', 'Title B')")
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        new File(testProjectDir, 'build/reports/dependency-license/index.html').text.contains('Title B')
    }

    def "should rebuild report when the normalizer bundle content changes"() {
        given:
        File bundle = new File(testProjectDir, 'normalizer.json')
        bundle.text = '{ "bundles": [], "transformationRules": [] }'
        setBuiltInComponents("new SimpleHtmlReportRenderer()", "new LicenseBundleNormalizer(bundlePath: '${AbstractGradleRunnerFunctionalSpec.fixPathForBuildFile(bundle.absolutePath)}')")

        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        bundle.text = '{ "bundles": [ { "bundleName": "x", "licenseName": "X License" } ], "transformationRules": [] }'
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
    }

    def "should accept custom components without input annotations"() {
        given:
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation "junit:junit:4.12"
            }

            import com.github.jk1.license.ProjectData
            import com.github.jk1.license.render.ReportRenderer

            class UnannotatedRenderer implements ReportRenderer {
                String title = 'unannotated'
                String getSomething() { 'something' }
                void render(ProjectData data) {
                    new File(data.extension.absoluteOutputDir, 'custom.txt').text = title
                }
            }

            licenseReport {
                renderers = [new UnannotatedRenderer()]
            }
        """

        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
        new File(testProjectDir, 'build/reports/dependency-license/custom.txt').text == 'unannotated'
    }

    def "should accept every built-in renderer, importer and filter as task input"() {
        given:
        new File(testProjectDir, 'external.xml').text = "<chapter title='External'></chapter>"
        new File(testProjectDir, 'overrides.txt').text = "junit:junit:4.12|https://junit.org|EPL|https://www.eclipse.org/legal/epl-v10.html"
        setBuiltInComponents(
            """new CsvReportRenderer(), new ExtendedJsonReportRenderer('extended.json'), new InventoryHtmlReportRenderer('inventory.html', 'Title', file('overrides.txt')),
               new InventoryMarkdownReportRenderer(), new JsonReportRenderer(), new SimpleHtmlReportRenderer('simple.html'), new TextReportRenderer(), new XmlReportRenderer()""",
            """new LicenseBundleNormalizer(), new SpdxLicenseBundleNormalizer(), new ReduceDuplicateLicensesFilter(),
               new ExcludeTransitiveDependenciesFilter(), new ExcludeDependenciesWithoutArtifactsFilter()""",
            """new XmlReportImporter('External', file('external.xml')), new NpxLicenseCheckerImporter('npx', []), new PnpmLicenseImporter('pnpm', [])"""
        )

        when:
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE
    }

    def "should accept built-in components with optional settings left empty"() {
        given:
        new File(testProjectDir, 'external.xml').text = "<chapter title='External'></chapter>"

        when:
        setBuiltInComponents("new XmlReportRenderer('index.xml', 'Libraries', null), new InventoryMarkdownReportRenderer('licenses.md', null, null, null, null)")
        BuildResult result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        setBuiltInComponents(
            "new SimpleHtmlReportRenderer()",
            '',
            "new XmlReportImporter(null, file('external.xml')), new NpxLicenseCheckerImporter(null, null), new PnpmLicenseImporter(null, null)"
        )
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateLicenseReport", "-PjunitVersion=4.12")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE
    }

    def "should track generated importer files without requiring a dependency on their generating task"() {
        given:
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation "junit:junit:4.12"
            }

            import com.github.jk1.license.ImportedModuleBundle
            import com.github.jk1.license.importer.*
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.Internal

            class FileImporter implements DependencyDataImporter {
                File source
                @Internal String getImporterName() { 'custom' }
                @InputFile File getSourceFile() { source }
                Collection<ImportedModuleBundle> doImport() { [] }
            }

            def frontend = layout.buildDirectory.file('generated/frontend.xml')
            def custom = layout.buildDirectory.file('generated/custom.txt')
            def version = providers.gradleProperty('frontendVersion')
            tasks.register('generateFrontendReport') {
                outputs.files(frontend, custom)
                inputs.property('version', version)
                doLast {
                    frontend.get().asFile.text = "<chapter title='Frontend'></chapter><!-- \${version.get()} -->"
                    custom.get().asFile.text = version.get()
                }
            }

            // deliberately no dependency of generateLicenseReport on generateFrontendReport
            licenseReport {
                importers = [new XmlReportImporter('Frontend', frontend.get().asFile), new FileImporter(source: custom.get().asFile)]
            }
        """

        when:
        BuildResult result = runBuildWith('--build-cache', "generateFrontendReport", "generateLicenseReport", "-PfrontendVersion=1")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "generateFrontendReport", "generateLicenseReport", "-PfrontendVersion=1")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.UP_TO_DATE

        when:
        result = runBuildWith('--build-cache', "generateFrontendReport", "generateLicenseReport", "-PfrontendVersion=2")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS
    }

    def "should cache the report when checkLicense ran before"() {
        given:
        new File(testProjectDir, 'allowed-licenses.json').text = '{ "allowedLicenses": [ { "moduleLicense": ".*" } ] }'
        buildFile << """
            licenseReport {
                allowedLicensesFile = file('allowed-licenses.json')
            }
        """

        when:
        BuildResult result = runBuildWith('--build-cache', "checkLicense", "-PjunitVersion=4.12")

        then:
        result.task(':checkLicense').outcome == TaskOutcome.SUCCESS
        new File(testProjectDir, 'build/reports/dependency-license-check/dependencies-without-allowed-license.json').exists()
        !new File(testProjectDir, 'build/reports/dependency-license/dependencies-without-allowed-license.json').exists()

        when: "the report is regenerated while the output of checkLicense exists"
        result = runBuildWith('--build-cache', "checkLicense", "-PjunitVersion=4.11")

        then:
        result.task(':generateLicenseReport').outcome == TaskOutcome.SUCCESS

        when:
        result = runBuildWith('--build-cache', "clean", "checkLicense", "-PjunitVersion=4.11")

        then: "it was stored in the build cache"
        result.task(':generateLicenseReport').outcome == TaskOutcome.FROM_CACHE
        result.task(':checkLicense').outcome == TaskOutcome.FROM_CACHE
    }

    private void setBuiltInComponents(String renderers, String filters = '', String importers = '') {
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation "junit:junit:\${project.ext.junitVersion}"
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.importer.*
            import com.github.jk1.license.render.*
            licenseReport {
                renderers = [$renderers]
                filters = [$filters]
                importers = [$importers]
            }
        """
    }

    private BuildResult runBuildWith(String... params) {
        return GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir)
            .withArguments(params)
            .withDebug(true)
            .forwardOutput()
            .build()
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
                String getInputCache() { return this.input }

                @Override
                ProjectData filter(ProjectData source) {
                    return source
                }
            }

            dependencies {
                implementation "junit:junit:\${project.ext.junitVersion}"
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
                String getInputCache() { return this.input }

                @Override
                void render(ProjectData data) {
                }
            }

            dependencies {
                implementation "junit:junit:\${project.ext.junitVersion}"
            }

            licenseReport {
                renderers = [new MyRenderer("${string}")]
            }
        """
    }
}
