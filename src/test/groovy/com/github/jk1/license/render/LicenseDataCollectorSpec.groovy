package com.github.jk1.license.render

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectBuilder
import com.github.jk1.license.ProjectData
import spock.lang.Specification

class LicenseDataCollectorSpec extends Specification {

    ProjectBuilder builder = new ProjectBuilder()

    def "empty module info results in empty license info"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                }
            }
        }

        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.singleModuleLicenseInfo(moduleData)

        then:
        result.licenses.isEmpty()
    }

    def "after normalisation, all license files of all configurations are normalized"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFiles {
                        licenseFileDetails(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }

        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.multiModuleLicenseInfo(moduleData)

        then:
        result.licenses*.name == ["Apache License, Version 2.0"]
        result.licenses*.url == ["https://www.apache.org/licenses/LICENSE-2.0"]
    }

    def "duplicate licenses are sorted out"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(name: "Apache License, Version 2.0", url: "https://www.apache.org/licenses/LICENSE-2.0")
                        license(name: "Apache License, Version 2.0", url: null)
                    }
                    licenseFiles {
                        licenseFileDetails(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }

        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.multiModuleLicenseInfo(moduleData)

        then:
        result.licenses*.name == ["Apache License, Version 2.0", "Apache License, Version 2.0"]
        result.licenses*.url == [null, "https://www.apache.org/licenses/LICENSE-2.0"]
    }
}
