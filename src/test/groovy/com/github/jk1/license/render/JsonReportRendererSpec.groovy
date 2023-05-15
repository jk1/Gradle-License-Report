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

class JsonReportRendererSpec extends Specification {

    @TempDir
    File testProjectDir
    File outputJson

    ProjectBuilder builder = new ProjectBuilder()
    ProjectData projectData

    def setup() {
        outputJson = new File(testProjectDir, "index.json")
        outputJson.delete()

        LicenseReportExtension extension = GRADLE_PROJECT().licenseReport
        extension.outputDir = testProjectDir

        // copy apache2 license file
        def apache2LicenseFile = new File(getClass().getResource('/apache2.license').toURI())
        new File(testProjectDir, "apache2.license") << apache2LicenseFile.text

        projectData = builder.project {
            configurations(["runtime", "test"]) { configName ->
                configuration(configName) {
                    module("mod1") {
                        pom("pom1") {
                            license(APACHE2_LICENSE(), url: "https://www.apache.org/licenses/LICENSE-2.0")
                        }
                        licenseFiles {
                            licenseFileDetails(file: "apache2.license", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                        }
                        manifest("mani1") {
                            license("Apache 2.0")
                        }
                    }
                    module("mod2") {
                        pom("pom2") {
                            license(APACHE2_LICENSE())
                        }
                        pom("pom3") {
                            license(APACHE2_LICENSE())
                            license(MIT_LICENSE())
                        }
                        licenseFiles {
                            licenseFileDetails(file: "apache2.license", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                        }
                        manifest("mani1") {
                            license("Apache 2.0")
                        }
                    }
                }
            }
            importedModulesBundle("bundle1") {
                importedModule(name: "mod1", license: "Apache  2", licenseUrl: "apache-url")
                importedModule(name: "mod2", license: "Apache  2", licenseUrl: "apache-url")
            }
        }
    }

    def "writes a one-license-per-module json"() {
        def jsonRenderer = new JsonReportRenderer()

        when:
        jsonRenderer.render(projectData)

        then:
        outputJson.exists()
        outputJson.text == """{
    "dependencies": [
        {
            "moduleName": "dummy-group:mod1",
            "moduleUrl": "http://dummy-pom-project-url",
            "moduleVersion": "0.0.1",
            "moduleLicense": "Apache License, Version 2.0",
            "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
        },
        {
            "moduleName": "dummy-group:mod2",
            "moduleUrl": "http://dummy-pom-project-url",
            "moduleVersion": "0.0.1",
            "moduleLicense": "MIT License",
            "moduleLicenseUrl": "https://opensource.org/licenses/MIT"
        }
    ],
    "importedModules": [
        {
            "moduleName": "bundle1",
            "dependencies": [
                {
                    "moduleName": "mod1",
                    "moduleUrl": "some-projectUrl",
                    "moduleVersion": "some-version",
                    "moduleLicense": "Apache  2",
                    "moduleLicenseUrl": "apache-url"
                },
                {
                    "moduleName": "mod2",
                    "moduleUrl": "some-projectUrl",
                    "moduleVersion": "some-version",
                    "moduleLicense": "Apache  2",
                    "moduleLicenseUrl": "apache-url"
                }
            ]
        }
    ]
}"""
    }

    def "writes a multi-license-per-module json"() {
        def jsonRenderer = new JsonReportRenderer(
            onlyOneLicensePerModule: false
        )

        when:
        jsonRenderer.render(projectData)

        then:
        outputJson.exists()
        outputJson.text == """{
    "dependencies": [
        {
            "moduleName": "dummy-group:mod1",
            "moduleVersion": "0.0.1",
            "moduleUrls": [
                "http://dummy-mani-url",
                "http://dummy-pom-project-url"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache 2.0",
                    "moduleLicenseUrl": null
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        },
        {
            "moduleName": "dummy-group:mod2",
            "moduleVersion": "0.0.1",
            "moduleUrls": [
                "http://dummy-mani-url",
                "http://dummy-pom-project-url"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache 2.0",
                    "moduleLicenseUrl": null
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                },
                {
                    "moduleLicense": "MIT License",
                    "moduleLicenseUrl": "https://opensource.org/licenses/MIT"
                }
            ]
        }
    ],
    "importedModules": [
        {
            "moduleName": "bundle1",
            "dependencies": [
                {
                    "moduleName": "mod1",
                    "moduleUrl": "some-projectUrl",
                    "moduleVersion": "some-version",
                    "moduleLicense": "Apache  2",
                    "moduleLicenseUrl": "apache-url"
                },
                {
                    "moduleName": "mod2",
                    "moduleUrl": "some-projectUrl",
                    "moduleVersion": "some-version",
                    "moduleLicense": "Apache  2",
                    "moduleLicenseUrl": "apache-url"
                }
            ]
        }
    ]
}"""
    }
}
