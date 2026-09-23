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
package com.github.jk1.license.task

import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class ReportComponentsSpec extends Specification {

    @TempDir
    File tempDir

    Closure<FileCollection> toFiles = { Object files -> ProjectBuilder.builder().withProjectDir(tempDir).build().files(files) }

    // defined outside the plugin's packages, like renderers of a build script or buildSrc
    static final Class<?> CUSTOM_RENDERER = new GroovyClassLoader(ReportComponentsSpec.classLoader).parseClass('''
        package org.example

        import com.github.jk1.license.ProjectData
        import com.github.jk1.license.render.ReportRenderer
        import org.gradle.api.tasks.Input
        import org.gradle.api.tasks.InputFile

        class CustomRenderer implements ReportRenderer {
            @Input
            String annotatedField = 'field'
            String unannotated = 'ignored'
            File template

            @Input
            String getAnnotatedGetter() { 'getter' }

            @InputFile
            File getTemplateFile() { template }

            void render(ProjectData data) {}
        }
    ''')

    def "built-in components are recognized by their origin"() {
        expect:
        ReportComponents.isBuiltIn(new JsonReportRenderer())
        ReportComponents.isBuiltIn(new LicenseBundleNormalizer())
        !ReportComponents.isBuiltIn(CUSTOM_RENDERER.newInstance())
    }

    def "custom components are fingerprinted by their type and annotated values only"() {
        when:
        def inputs = ReportComponents.customInputValues([new JsonReportRenderer(), CUSTOM_RENDERER.newInstance()], toFiles)

        then:
        inputs == [
            'org.example.CustomRenderer',
            'org.example.CustomRenderer.annotatedField=field',
            'org.example.CustomRenderer.annotatedGetter=getter',
            'org.example.CustomRenderer.templateFile=null',
        ]
    }

    def "file inputs of custom components are fingerprinted by their content"() {
        given:
        File template = new File(tempDir, 'template.txt')
        def renderer = CUSTOM_RENDERER.newInstance()
        renderer.template = template

        when:
        template.text = 'first'
        def first = ReportComponents.customInputValues([renderer], toFiles)
        template.text = 'second'
        def second = ReportComponents.customInputValues([renderer], toFiles)

        then:
        first.last().startsWith('org.example.CustomRenderer.templateFile=[template.txt:')
        first != second
    }

    def "path sensitivity of file inputs of custom components is honored"() {
        given:
        def type = new GroovyClassLoader(ReportComponentsSpec.classLoader).parseClass('''
            package org.example

            import com.github.jk1.license.ProjectData
            import com.github.jk1.license.render.ReportRenderer
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.PathSensitive
            import org.gradle.api.tasks.PathSensitivity

            class PathsRenderer implements ReportRenderer {
                File source

                @InputFile
                File getRelative() { source }

                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File getNone() { source }

                @InputFile
                @PathSensitive(PathSensitivity.ABSOLUTE)
                File getAbsolute() { source }

                void render(ProjectData data) {}
            }
        ''')
        File original = new File(tempDir, 'original.txt')
        File renamed = new File(tempDir, 'renamed.txt')
        original.text = 'same content'
        renamed.text = 'same content'

        when:
        Map<String, String> first = fingerprints(type, original)
        Map<String, String> second = fingerprints(type, renamed)

        then:
        first.none == second.none
        first.relative != second.relative
        first.absolute.contains(original.absolutePath)
        !first.relative.contains(tempDir.absolutePath)
    }

    private Map<String, String> fingerprints(Class<?> type, File source) {
        def renderer = type.newInstance()
        renderer.source = source
        ReportComponents.customInputValues([renderer], toFiles).tail().collectEntries {
            def (key, value) = it.split('=', 2)
            [(key.tokenize('.').last()): value]
        }
    }

    def "custom components which are not public are fingerprinted"() {
        given:
        def renderer = new GroovyClassLoader(ReportComponentsSpec.classLoader).parseClass('''
            package org.example

            import com.github.jk1.license.ProjectData
            import com.github.jk1.license.render.ReportRenderer
            import groovy.transform.PackageScope
            import org.gradle.api.tasks.Input

            @PackageScope
            class PackagePrivateRenderer implements ReportRenderer {
                @Input
                String getSetting() { 'value' }
                void render(ProjectData data) {}
            }
        ''').newInstance()

        expect:
        ReportComponents.customInputValues([renderer], toFiles) == ['org.example.PackagePrivateRenderer', 'org.example.PackagePrivateRenderer.setting=value']
    }

    def "values without a meaningful toString() have a stable fingerprint, and failing getters are tolerated"() {
        given:
        def type = new GroovyClassLoader(ReportComponentsSpec.classLoader).parseClass('''
            package org.example

            import com.github.jk1.license.ProjectData
            import com.github.jk1.license.render.ReportRenderer
            import org.gradle.api.tasks.Input

            class ValuesRenderer implements ReportRenderer {
                static class Setting implements Serializable {
                    String value = 'a'
                }

                @Input
                Setting getSetting() { new Setting() }

                @Input
                String getFailing() { throw new IllegalStateException('not available yet') }

                void render(ProjectData data) {}
            }
        ''')

        when:
        def first = ReportComponents.customInputValues([type.newInstance()], toFiles)
        def second = ReportComponents.customInputValues([type.newInstance()], toFiles)

        then:
        first == second
        first.any { it.startsWith('org.example.ValuesRenderer.setting=org.example.ValuesRenderer$Setting@') }
        first.contains('org.example.ValuesRenderer.failing=<unreadable>')
    }

    def "custom components without annotations only contribute their type"() {
        given:
        ReportRenderer renderer = new GroovyClassLoader(ReportComponentsSpec.classLoader).parseClass('''
            package org.example

            import com.github.jk1.license.ProjectData
            import com.github.jk1.license.render.ReportRenderer

            class UnannotatedRenderer implements ReportRenderer {
                String getSomething() { 'something' }
                void render(ProjectData data) {}
            }
        ''').newInstance() as ReportRenderer

        expect:
        ReportComponents.customInputValues([renderer], toFiles) == [renderer.getClass().name]
    }
}
