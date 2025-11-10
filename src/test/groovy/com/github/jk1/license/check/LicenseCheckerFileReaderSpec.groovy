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
package com.github.jk1.license.check

import org.gradle.api.file.RegularFile
import org.gradle.api.internal.resources.StringBackedTextResource
import spock.lang.Specification
import spock.lang.TempDir

import java.util.function.Function

class LicenseCheckerFileReaderSpec extends Specification {

    @TempDir
    File testProjectDir

    File allowedLicenseFile
    File projectDataFile

    def setup() {
        allowedLicenseFile = new File(testProjectDir, 'test-allowed-licenses.json')
        projectDataFile = new File(testProjectDir, 'test-projectData.json')

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "License1"
                },
                {
                    "moduleLicense": "License2"
                },
                {
                    "moduleLicense": "License3"
                }
            ]
        }"""
    }

    def "it reads out all the allowed licenses"() {
        when:
        List<AllowedLicense> allowedLicenses = LicenseCheckerFileReader.importAllowedLicenses(allowedLicenseFile)

        then:
        allowedLicenses.collect { it.moduleLicense } == ["License1", "License2", "License3"]
        allowedLicenses.collect { it.moduleName } == [null,  null, null]
    }

    def "read null"() {

        allowedLicenseFile.text = """{"allowedLicenses":[]}"""

        when:
        List<AllowedLicense> allowedLicenses = LicenseCheckerFileReader.importAllowedLicenses(allowedLicenseFile)

        then:
        allowedLicenses == []
    }

    def "read allowedLicenses moduleName"() {

        allowedLicenseFile.text = """
        {
            "allowedLicenses":[
                {
                    "moduleName": "Name1"
                },
                {
                    "moduleName": "Name2"
                },
                {
                    "moduleName": "Name3"
                }
            ]
        }"""

        when:
        List<AllowedLicense> allowedLicenses = LicenseCheckerFileReader.importAllowedLicenses(allowedLicenseFile)

        then:
        allowedLicenses.moduleLicense == [null, null, null]
        allowedLicenses.moduleName == ["Name1", "Name2", "Name3"]
    }

    def "read projectData dependencies moduleName"() {

        projectDataFile.text = """
        {
            "dependencies":[
                {
                    "moduleName": "Name1"
                },
                {
                    "moduleName": "Name2"
                },
                {
                    "moduleName": "Name3"
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == ["Name1", "Name2", "Name3"]
        dependencies.moduleLicenses.moduleLicense == [[], [], []]
    }

    def "read projectData dependencies moduleLicenses and moduleName"() {

        projectDataFile.text = """
        {
            "dependencies":[
                {
                    "moduleName": "Name1",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://=========/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "http://======="
                        }
                    ]
                },
                {
                    "moduleName": "Name2",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://========/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "http://======="
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == ["Name1", "Name2"]
        dependencies.moduleLicenses.moduleLicense == [["License1"], ["License2"]]
    }

    def "read projectData dependencies moduleLicenses"() {

        projectDataFile.text = """
        {
            "dependencies":[
                {
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://=========/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "http://======="
                        }
                    ]
                },
                {
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://========/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "http://======="
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == [null, null]
        dependencies.moduleLicenses.moduleLicense == [["License1"], ["License2"]]
    }

    def "read projectData dependencies null"() {

        projectDataFile.text = """
        {
            "dependencies":[
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == []
        dependencies.moduleLicenses == []
    }

    def "read projectData importedModules"() {

        projectDataFile.text = """
        {
            "importedModules": [
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name1",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                },
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name2",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == ["Name1", "Name2"]
        dependencies.moduleLicenses.moduleLicense == [["License1"], ["License2"]]
    }

    def "read projectData importedModules moduleName"() {

        projectDataFile.text = """
        {
        "dependencies": [
            {
                "moduleName": "Name1",
                "moduleVersion": "some-version",
                "moduleUrls": [
                    "some-url"
                ]
            },
            {
                "moduleName": "Name2",
                "moduleVersion": "some-version",
                "moduleUrls": [
                    "some-projectUrl"
                ]           
            }       
            ],         
            "importedModules": [
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name1",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "some-license",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                },
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name2",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "some-license",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == ["Name1", "Name2"]
        dependencies.moduleLicenses.moduleLicense == [["some-license"], ["some-license"]]
    }

    def "read projectData importedModules moduleLicense"() {

        projectDataFile.text = """
        {
            "importedModules": [
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                },
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == [null, null]
        dependencies.moduleLicenses.moduleLicense == [["License1"], ["License2"]]
    }

    def "read projectData importedModules and dependencies"() {

        projectDataFile.text = """
        {
            "dependencies":[
                {
                    "moduleName": "Name1",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://=========/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "http://======="
                        }
                    ]
                },
                {
                    "moduleName": "Name2",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://========/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "http://======="
                        }
                    ]
                }
            ],
            "importedModules": [
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name3",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "License3",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                },
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name4",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicense": "License4",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == ["Name1", "Name2", "Name3", "Name4"]
        dependencies.moduleLicenses.moduleLicense == [["License1"], ["License2"], ["License3"], ["License4"]]
    }

    def "read projectData dependencies with multiple moduleLicense"() {

        projectDataFile.text = """
        {
            "dependencies":[
                {
                    "moduleName": "Name1",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://=======/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "http://========="
                        },
                        {
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "http://====="
                        },
                        {
                            "moduleLicense": "License3",
                            "moduleLicenseUrl": "http://========"
                        }
                    ]
                },
                {
                    "moduleName": "Name2",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://======/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License2",
                            "moduleLicenseUrl": "http://========"
                        }
                    ]
                }
            ],
            "importedModules": [
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name3",
                            "moduleUrl": "some-projectUrl",
                            "moduleLicense": "License3",
                            "moduleVersion": "some-version",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                },
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name4",
                            "moduleUrl": "some-projectUrl",
                            "moduleLicense": "License4",
                            "moduleVersion": "some-version",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == ["Name1", "Name2", "Name3", "Name4"]
        dependencies.moduleLicenses.moduleLicense == [["License1", "License2", "License3"], ["License2"], ["License3"], ["License4"]]
    }

    def "read projectData random null moduleName and module License of importedModules and dependencies"() {

        projectDataFile.text = """
        {
            "dependencies":[
                {
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://kotlinlang.org/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License1",
                            "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
                        }
                    ]
                },
                {
                    "moduleName": "Name2",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://kotlinlang.org/"
                    ]
                },
                {
                    "moduleName": "Name3",
                    "moduleVersion": "1.2.60",
                    "moduleUrls": [
                        "https://kotlinlang.org/"
                    ],
                    "moduleLicenses": [
                        {
                            "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
                        }
                    ]
                }
            ],
            "importedModules": [
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleUrl": "some-projectUrl",
                            "moduleLicense": "License3",
                            "moduleVersion": "some-version",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                },
                {
                    "moduleName": "bundle1",
                    "dependencies": [
                        {
                            "moduleName": "Name5",
                            "moduleUrl": "some-projectUrl",
                            "moduleVersion": "some-version",
                            "moduleLicenseUrl": "apache-url"
                        }
                    ]
                }
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies.moduleName == [null, "Name2", "Name3", null, "Name5"]
        dependencies.moduleLicenses.moduleLicense == [["License1"], [], [null], ["License3"], [null]]
    }

    def "read projectData random null of importedModules and dependencies"() {

        projectDataFile.text = """
        {
            "dependencies": [
            ],
            "importedModules": [
            ]
        }"""

        when:
        List<Dependency> dependencies = LicenseCheckerFileReader.importDependencies(projectDataFile)

        then:
        dependencies == []
    }

    def "it reads out all the allowed licenses from a #type"(String type, Function<File, Object> fromAllowedLicensesFile) {
        when:
        List<AllowedLicense> allowedLicenses = LicenseCheckerFileReader.importAllowedLicenses(fromAllowedLicensesFile(allowedLicenseFile))

        then:
        allowedLicenses.collect { it.moduleLicense } == ["License1", "License2", "License3"]
        allowedLicenses.collect { it.moduleName } == [null,  null, null]

        where:
        [type, fromAllowedLicensesFile] << [
                ['File', { file -> file }],
                ['String (path to file)', { file -> file.path }],
                ['String (URL to file)', { file -> file.toURI().toURL().toString() }],
                ['Path', { file -> file.toPath() }],
                ['URI (to file)', { file -> file.toURI() }],
                ['URL (to file)', { file -> file.toURI().toURL() }],
                ['RegularFile', { file -> { file } as RegularFile }],
                ['TextResource', { file -> new StringBackedTextResource(null, file.text) }],
        ]
    }
}

