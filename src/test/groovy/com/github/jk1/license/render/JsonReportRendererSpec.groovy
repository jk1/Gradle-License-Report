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
package com.github.jk1.license.render

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ProjectBuilder
import com.github.jk1.license.ProjectData
import spock.lang.Specification
import spock.lang.TempDir

import static com.github.jk1.license.ProjectDataFixture.*

class JsonReportRendererSpec extends AbstractInventoryReportRendererSpec {
    def "writes a one-license-per-module json"() {
        def jsonRenderer = new JsonReportRenderer(outputFile.name)

        when:
        jsonRenderer.render(projectData)

        then:
        outputFile.exists()
        snapshotter.assertThat(outputFile.text).matchesSnapshot()
    }

    def "writes a multi-license-per-module json"() {
        def jsonRenderer = new JsonReportRenderer(
                outputFile.name,
                false
        )

        when:
        jsonRenderer.render(projectData)

        then:
        outputFile.exists()
        snapshotter.assertThat(outputFile.text).matchesSnapshot()
    }
}
