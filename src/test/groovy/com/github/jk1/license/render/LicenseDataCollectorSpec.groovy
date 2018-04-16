package com.github.jk1.license.render

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectBuilder
import com.github.jk1.license.ProjectData
import spock.lang.Specification

import static com.github.jk1.license.ProjectBuilder.json
import static com.github.jk1.license.ProjectDataFixture.APACHE2_LICENSE

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

    def "keep manifest-license when name/url not matches a existing licenses name or url"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                    manifest("mani1") {
                        license("Apache 2.0")
                    }
                    manifest("mani2") {
                        license("https://someUrl")
                    }
                }
            }
        }

        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.multiModuleLicenseInfo(moduleData)

        then:
        json(result) == """{
    "moduleUrls": [
        "http://dummy-mani-url",
        "http://dummy-pom-project-url"
    ],
    "licenses": [
        {
            "comments": null,
            "distribution": null,
            "url": "https://someUrl",
            "name": null
        },
        {
            "comments": null,
            "distribution": null,
            "url": null,
            "name": "Apache 2.0"
        },
        {
            "comments": "A business-friendly OSS license",
            "distribution": "repo",
            "url": "https://www.apache.org/licenses/LICENSE-2.0",
            "name": "Apache License, Version 2.0"
        }
    ]
}"""
    }

    def "remove manifest-license when name/url matches a existing licenses name or url"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                    manifest("mani1") {
                        license(APACHE2_LICENSE().name)
                    }
                    manifest("mani2") {
                        license(APACHE2_LICENSE().url)
                    }
                }
            }
        }

        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.multiModuleLicenseInfo(moduleData)

        then:
        json(result) == """{
    "moduleUrls": [
        "http://dummy-mani-url",
        "http://dummy-pom-project-url"
    ],
    "licenses": [
        {
            "comments": "A business-friendly OSS license",
            "distribution": "repo",
            "url": "https://www.apache.org/licenses/LICENSE-2.0",
            "name": "Apache License, Version 2.0"
        }
    ]
}"""
    }

    def "keep license-file-license when name not matches a existing licenses name or url"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                    licenseFiles {
                        licenseFileDetails(file: "apache2-license.txt", license: "Apache 2.0", licenseUrl: APACHE2_LICENSE().url)
                    }
                }
            }
        }
        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.multiModuleLicenseInfo(moduleData)

        then:
        json(result) == """{
    "moduleUrls": [
        "http://dummy-pom-project-url"
    ],
    "licenses": [
        {
            "comments": null,
            "distribution": null,
            "url": "https://www.apache.org/licenses/LICENSE-2.0",
            "name": "Apache 2.0"
        },
        {
            "comments": "A business-friendly OSS license",
            "distribution": "repo",
            "url": "https://www.apache.org/licenses/LICENSE-2.0",
            "name": "Apache License, Version 2.0"
        }
    ]
}"""
    }

    def "remove license-file-license when name matches a existing licenses name or url"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                    licenseFiles {
                        licenseFileDetails(file: "apache2-license.txt", license: APACHE2_LICENSE().name, licenseUrl: APACHE2_LICENSE().url)
                        licenseFileDetails(file: "apache2-license.txt", license: APACHE2_LICENSE().name, licenseUrl: "http://some-url")
                    }
                }
            }
        }
        when:
        ModuleData moduleData = projectData.configurations*.dependencies.flatten().first()
        def result = LicenseDataCollector.multiModuleLicenseInfo(moduleData)

        then:
        json(result) == """{
    "moduleUrls": [
        "http://dummy-pom-project-url"
    ],
    "licenses": [
        {
            "comments": "A business-friendly OSS license",
            "distribution": "repo",
            "url": "https://www.apache.org/licenses/LICENSE-2.0",
            "name": "Apache License, Version 2.0"
        }
    ]
}"""
    }
}
