package com.github.jk1.license.render

import com.github.jk1.license.LicenseReportPlugin
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static com.github.jk1.license.ProjectDataFixture.*

class JsonReportRendererSpec extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File outputJson

    def setup() {
        testProjectDir.create()
        outputJson = new File(testProjectDir.root, "index.json")
        outputJson.delete()

        LicenseReportPlugin.LicenseReportExtension extension = GRADLE_PROJECT().licenseReport
        extension.outputDir = testProjectDir.root

        // copy apache2 license file
        def apache2LicenseFile = new File(getClass().getResource('/apache2-license.txt').toURI())
        new File(testProjectDir.root, "apache2-license.txt") << apache2LicenseFile.text
    }

    def "writes a one-license-per-module json"() {
        def jsonRenderer = new JsonReportRenderer()

        when:
        jsonRenderer.render(PROJECT_DATA_TWO_MODULES_AND_IMPORTED_MODULES())

        then:
        outputJson.exists()
        outputJson.text == """{
    "dependencies": [
        {
            "moduleName": "dummy1-group:dummy1-name",
            "moduleUrl": "http://dummy-pom1-project-url",
            "moduleVersion": "0.0.1",
            "moduleLicense": "MIT License",
            "moduleLicenseUrl": "https://opensource.org/licenses/MIT"
        },
        {
            "moduleName": "dummy1-group:dummy1-name",
            "moduleUrl": "http://dummy-pom2-project-url",
            "moduleVersion": "0.0.1",
            "moduleLicense": "Apache License, Version 2.0",
            "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
        }
    ],
    "importedModules": [
        {
            "moduleName": "foo-module-bundle-name",
            "dependencies": {
                "moduleName": "foo-module-data-name",
                "moduleUrl": "http://foo-module-data-url",
                "moduleVersion": "foo-module-data-version",
                "moduleLicense": "foo-module-data-license",
                "moduleLicenseUrl": "http://foo-module-data-license-url"
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
        jsonRenderer.render(PROJECT_DATA_TWO_MODULES_AND_IMPORTED_MODULES())

        then:
        outputJson.exists()
        outputJson.text == """{
    "dependencies": [
        {
            "moduleName": "dummy1-group:dummy1-name",
            "moduleVersion": "0.0.1",
            "moduleUrls": [
                "http://dummy-mani1-url",
                "http://dummy-pom2-project-url",
                "http://dummy-pom1-project-url"
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
                    "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        },
        {
            "moduleName": "dummy1-group:dummy1-name",
            "moduleVersion": "0.0.1",
            "moduleUrls": [
                "http://dummy-mani1-url",
                "http://dummy-pom2-project-url"
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
                    "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        }
    ],
    "importedModules": [
        {
            "moduleName": "foo-module-bundle-name",
            "dependencies": {
                "moduleName": "foo-module-data-name",
                "moduleUrl": "http://foo-module-data-url",
                "moduleVersion": "foo-module-data-version",
                "moduleLicense": "foo-module-data-license",
                "moduleLicenseUrl": "http://foo-module-data-license-url"
            }
        }
    ]
}"""
    }
}
