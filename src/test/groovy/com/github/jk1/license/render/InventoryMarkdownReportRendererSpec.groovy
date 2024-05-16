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

class InventoryMarkdownReportRendererSpec extends AbstractInventoryReportRendererSpec {

    def "check the correct generation of markdown"() {
        def renderer = new InventoryMarkdownReportRenderer(outputFile.name, "name", overrides)
        when:
        renderer.render(projectData)
        then:
        outputFile.exists()
        def sanitizedOutput = outputFile.text.replaceAll("_[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} [A-Z0-9-]+_", "DATE")
        snapshotter.assertThat(sanitizedOutput).matchesSnapshot()
    }
}
