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

import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.TempDir

class LicenseCheckerSpec extends Specification {

    @TempDir
    File testProjectDir

    File allowedLicenseFile
    File projectDataFile
    File notPassedDependenciesFile

    List<AllowedLicense> importNotPassedDependencies(File file) {
        def slurpResult = new JsonSlurper().setType(JsonParserType.LAX).parse(file)
        return slurpResult.dependenciesWithoutAllowedLicenses.collect { new AllowedLicense(it.moduleName, it.moduleVersion, it.moduleLicense) }
    }

    def setup() {
        allowedLicenseFile = new File(testProjectDir, 'test-allowed-licenses.json')
        projectDataFile = new File(testProjectDir, 'test-projectData.json')
        notPassedDependenciesFile = new File(testProjectDir, 'test-not-passed-dependencies.json')
    }

    def "check if constructor working with json file."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache Software License, Version 1.1",
                    "moduleName": "org.jetbrains",
                },
                {
                    "moduleLicense": "Apache Software License,
                    Version 1.1", "moduleName": "org.jetbrains.*"
                },
                {
                    "moduleLicense": "Apache Software License, Version 1.1",
                    "moduleName": "org.jetbrains"
                },
                {
                    "moduleLicense": "Apache Software License, Version 1.1"
                },
                {
                    "moduleLicense": "Apache License, Version 2.0"
                },
                {
                    "moduleLicense": "The 2-Clause BSD License"
                },
                {
                    "moduleLicense": "The 3-Clause BSD License"
                },
                {
                    "moduleLicense": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL), Version 1.0"
                },
                {
                    "moduleLicense": "MIT License"
                },
                {
                    "moduleLicense": ".*", "moduleName": "org.jetbrains"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains only allowed licenses will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache Software License, Version 1.1"
                },
                {
                    "moduleLicense": "MIT License"
                }
            ]
        }"""
        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "MIT License"
                        }
                    ],
                    "moduleName": "dummy-group:mod2"
                },
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache Software License, Version 1.1"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains not allowed licenses will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache Software License, Version 1.1"
                },
                {
                    "moduleLicense": "MIT License"
                }
            ]
        }"""
        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "some-other-license"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:

        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod1"]
        notPassedDependencies.moduleLicense == ["some-other-license"]
        thrown GradleException
    }

    def "check when ProjectData contains only allowed moduleName will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleName": ".*mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "some-other-license"
                        }
                    ], "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains not allowed moduleName will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                { "moduleName": "mod1" }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod2"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod2"]
        notPassedDependencies.moduleLicense == ["Apache License, Version 2.0"]
        thrown GradleException
    }

    def "check when ProjectData only contains allowed moduleName will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleName": "dummy-group:.*"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                },
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod2"
                },
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod3"
                },
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod4"
                },
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                        "moduleName": "dummy-group:mod5"
                },
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "MIT License"
                        }
                    ],
                    "moduleName": "dummy-group:mod6"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains different license but same moduleName will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache Software License, Version 1.1", "moduleName": "mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "MIT License"
                        }
                    ],
                    "moduleName": "dummy-group:mod2"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod2"]
        notPassedDependencies.moduleLicense == ["MIT License"]
        thrown GradleException
    }

    def "check when ProjectData contains moduleVersion and allowedLicense has the same will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "MIT License", "moduleName": ".*mod2", "moduleVersion": "1.0"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "MIT License"
                        }
                    ],
                    "moduleName": "dummy-group:mod2",
                    "moduleVersion": "1.0"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains moduleVersion and allowedLicense with different moduleVersion will fail."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "MIT License", "moduleName": ".*mod2", "moduleVersion": "2.0"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "MIT License"
                        }
                    ],
                    "moduleName": "dummy-group:mod2",
                    "moduleVersion": "1.0"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod2"]
        notPassedDependencies.moduleLicense == ["MIT License"]
        notPassedDependencies.moduleVersion == ["1.0"]
        thrown GradleException
    }

    def "check when ProjectData contains moduleVersion and allowedLicense with no moduleVersion will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "MIT License", "moduleName": ".*mod2"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "MIT License"
                        }
                    ],
                    "moduleName": "dummy-group:mod2",
                    "moduleVersion": "1.0"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "Check when ProjectData contains no license but same moduleName will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)


        then:
        noExceptionThrown()
    }

    def "Check when allowedLicenses contains .* in moduleLicense and projectData contain same moduleName will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": ".*",
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)


        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains same license but different moduleName will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleName": "mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod2"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod2"]
        notPassedDependencies.moduleLicense == ["Apache License, Version 2.0"]
        thrown GradleException
    }
    def "check when ProjectData contains same license and same moduleName will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "GNU LESSER GENERAL PUBLIC LICENSE, Version 3",
                    "moduleName": ".*mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "GNU LESSER GENERAL PUBLIC LICENSE, Version 3"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains same license and different moduleName will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "GNU LESSER GENERAL PUBLIC LICENSE, Version 3",
                    "moduleName": "some-other-groups"
                }
            ]
        }"""
        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "GNU LESSER GENERAL PUBLIC LICENSE, Version 3"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod1"]
        notPassedDependencies.moduleLicense == ["GNU LESSER GENERAL PUBLIC LICENSE, Version 3"]
        thrown GradleException
    }

    def "check when ProjectData contains different moduleName will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleName": "some-other-groups:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "GNU LESSER GENERAL PUBLIC LICENSE, Version 3"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod1"]
        notPassedDependencies.moduleLicense == ["GNU LESSER GENERAL PUBLIC LICENSE, Version 3"]
        thrown GradleException
    }

    def "check when ProjectData contains same moduleName will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains one different value(license) will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "some-other-license"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod1"]
        notPassedDependencies.moduleLicense == ["some-other-license"]
        thrown GradleException
    }

    def "check when ProjectData contains one different value(moduleName) will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod2"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod2"]
        notPassedDependencies.moduleLicense == ["Apache License, Version 2.0"]
        thrown GradleException
    }

    def "check when there is nothing in allowedLicenses it will fail as expected."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod1"]
        notPassedDependencies.moduleLicense == ["Apache License, Version 2.0"]
        thrown GradleException
    }

    def "check when ProjectData contains multiple licenses and match with one allowed license will pass."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {
                            "moduleLicense": "License1"
                        },
                        {
                            "moduleLicense": "License2"
                        },
                        {
                            "moduleLicense": "Apache License, Version 2.0"
                        }
                    ],
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        noExceptionThrown()
    }

    def "check when ProjectData contains multiple licenses and have match with no allowed license will fail."() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleLicenses": [
                        {"moduleLicense": "License1"},
                        {"moduleLicense": "License2"},
                        {"moduleLicense": "License3"}
                    ],
                "moduleName": "dummy-group:mod1"
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
            allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["dummy-group:mod1", "dummy-group:mod1", "dummy-group:mod1"]
        notPassedDependencies.moduleLicense == ["License1", "License2", "License3"]
        thrown GradleException
    }

    def "check when ProjectData contains no licenses it is treated as allowed with empty license name rule"() {

        allowedLicenseFile << """
        {
            "allowedLicenses":[
                {
                    "moduleLicense": "",
                    "moduleName": "dummy-group:.*"
                }
            ]
        }"""

        projectDataFile << """
        {
            "dependencies":[
                {
                    "moduleName": "dummy-group:allowed1",
                    "moduleLicenses": [],
                },
                {
                    "moduleName": "dummy-group:allowed2",
                    "moduleLicenses": null,
                },
                {
                    "moduleName": "dummy-group:allowed3",
                    "moduleLicenses": [
                        {
                            "moduleLicense": "",
                        }
                    ],
                },
                {
                    "moduleName": "notallowed-group:mod",
                    "moduleLicenses": [],
                },
                {
                    "moduleName": "dummy-group:notallowed1",
                    "moduleLicenses": [
                        {
                            "moduleLicense": "some-other-license",
                        }
                    ],
                }
            ]
        }"""

        when:
        def licenseChecker = new LicenseChecker()
        licenseChecker.checkAllDependencyLicensesAreAllowed(
                allowedLicenseFile, projectDataFile, notPassedDependenciesFile)

        then:
        thrown GradleException
        def notPassedDependencies = importNotPassedDependencies(notPassedDependenciesFile)
        notPassedDependencies.moduleName == ["notallowed-group:mod", "dummy-group:notallowed1"]
        notPassedDependencies.moduleLicense == ["", "some-other-license"]
    }
}
