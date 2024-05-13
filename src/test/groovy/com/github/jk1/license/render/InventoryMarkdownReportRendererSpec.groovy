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
        outputFile.text.replaceAll("\\s+", " ").contains("""## Apache License, Version 2.0
  
 **1** **Group:** `dummy-group` **Name:** `mod1` **Version:** `0.0.1`
 > - **Manifest Project URL**: [http://dummy-mani-url](http://dummy-mani-url)
 > - **Manifest License**: Apache 2.0 (Not Packaged)
 > - **POM Project URL**: [http://dummy-pom-project-url](http://dummy-pom-project-url)
 > - **POM License**: Apache License, Version 2.0 - [https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)
   - Embedded license files:
     - apache2.license
 **2** **Group:** `dummy-group` **Name:** `mod2` **Version:** `0.0.1`
 > - **Project URL**: [https://projecturl](https://projecturl)
 > - **License URL**: [http://www.apache.org/licenses/LICENSE-2.0.txt](Apache License, Version 2.0)
  
 ## Unknown
  
 **3** **Group:** `dummy-group` **Name:** `mod3` **Version:** `0.0.1`
 > - **POM Project URL**: [http://dummy-pom-project-url](http://dummy-pom-project-url)
 > - **POM License**: Unknown""".replaceAll("\\s+", " "))
    }
}
