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

class InventoryHtmlReportRendererSpec extends AbstractInventoryReportRendererSpec {

    def "check the correct generation of html"() {
        def renderer = new InventoryHtmlReportRenderer(outputFile.name, "name", overrides)

        when:
        renderer.render(projectData)

        then:
        outputFile.exists()
        outputFile.text.stripIndent().contains("""<h2>Apache License, Version 2.0</h2>
<div class='dependency'>
<p><strong> 1.</strong> <strong>Group:</strong> dummy-group <strong>Name:</strong> mod1 <strong>Version:</strong> 0.0.1 </p><label>Manifest Project URL</label>
<div class='dependency-value'><a href='http://dummy-mani-url'>http://dummy-mani-url</a></div>
<label>Manifest License</label>
<div class='dependency-value'>Apache 2.0 (Not Packaged)</div>
<label>POM Project URL</label>
<div class='dependency-value'><a href='http://dummy-pom-project-url'>http://dummy-pom-project-url</a></div>
<label>POM License</label>
<div class='dependency-value'>Apache License, Version 2.0 - <a href='https://www.apache.org/licenses/LICENSE-2.0'>https://www.apache.org/licenses/LICENSE-2.0</a></div>
  - Embedded license files:
    - apache2.license</div>
<div class='dependency'>
<p><strong> 2.</strong> <strong>Group:</strong> dummy-group <strong>Name:</strong> mod2 <strong>Version:</strong> 0.0.1 </p><label>Project URL</label>
<div class='dependency-value'><a href='https://projecturl'>https://projecturl</a></div>
<label>License URL</label>
<div class='dependency-value'><a href='http://www.apache.org/licenses/LICENSE-2.0.txt'>Apache License, Version 2.0</a></div>""")
        outputFile.text.stripIndent().contains("""<h2>Unknown</h2>
<div class='dependency'>
<p><strong> 3.</strong> <strong>Group:</strong> dummy-group <strong>Name:</strong> mod3 <strong>Version:</strong> 0.0.1 </p><label>POM Project URL</label>""")
    }
}
