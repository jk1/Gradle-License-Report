package com.github.jk1.license.render

import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.ProjectBuilder
import com.github.jk1.license.ProjectData
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static com.github.jk1.license.ProjectDataFixture.*

class JsonReportRendererSpec extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File outputJson

    ProjectBuilder builder = new ProjectBuilder()
    ProjectData projectData

    def setup() {
        testProjectDir.create()
        outputJson = new File(testProjectDir.root, "index.json")
        outputJson.delete()

        LicenseReportPlugin.LicenseReportExtension extension = GRADLE_PROJECT().licenseReport
        extension.outputDir = testProjectDir.root

        // copy apache2 license file
        def apache2LicenseFile = new File(getClass().getResource('/apache2-license.txt').toURI())
        new File(testProjectDir.root, "apache2-license.txt") << apache2LicenseFile.text

        projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), url: "https://www.apache.org/licenses/LICENSE-2.0")
                    }
                    licenseFile(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
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
                    licenseFile(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                    manifest("mani1") {
                        license("Apache 2.0")
                    }
                }
            }
            importedModulesBundle("bundle1") {
                importedModule(name: "mod1", license: "Apache  2", licenseUrl: "apache-url")
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
            "moduleLicense": "Apache License, Version 2.0",
            "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
        }
    ],
    "importedModules": [
        {
            "moduleName": "bundle1",
            "dependencies": {
                "moduleName": "mod1",
                "moduleUrl": "some-projectUrl",
                "moduleVersion": "some-version",
                "moduleLicense": "Apache  2",
                "moduleLicenseUrl": "apache-url"
            }
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
                "http://dummy-pom-project-url",
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
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                },
                {
                    "moduleLicense": "MIT License",
                    "moduleLicenseUrl": "https://opensource.org/licenses/MIT"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        }
    ],
    "importedModules": [
        {
            "moduleName": "bundle1",
            "dependencies": {
                "moduleName": "mod1",
                "moduleUrl": "some-projectUrl",
                "moduleVersion": "some-version",
                "moduleLicense": "Apache  2",
                "moduleLicenseUrl": "apache-url"
            }
        }
    ]
}"""
    }
}
