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
package com.github.jk1.license

import spock.lang.Specification

import static com.github.jk1.license.ProjectDataFixture.*

class ProjectBuilderSpec extends Specification {
    ProjectBuilder builder = new ProjectBuilder()


    def "it creates an empty project when nothing defined"() {
        when:
        ProjectData data = builder.project { }

        then:
        data != null
        data.project != null
        data.configurations.isEmpty()
        data.importedModules.isEmpty()
    }

    def "it creates the specified empty configurations"() {
        when:
        ProjectData data = builder.project {
            configuration("runtime") {}
            configuration("test") { }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set
        data.configurations*.dependencies.flatten().isEmpty()
        data.importedModules.isEmpty()
    }

    def "it creates the specified empty configurations within one call"() {
        when:
        ProjectData data = builder.project {
            configurations(["runtime", "test"]) { configName ->
                configuration(configName) {}
            }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set
        data.configurations*.dependencies.flatten().isEmpty()
        data.importedModules.isEmpty()
    }

    def "it creates the specified configurations with the given empty poms"() {
        when:
        ProjectData data = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") { }
                    pom("pom2") { }
                }
                module("mod2") {
                    pom("pom3") { }
                }
            }
            configuration("test") { }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set
        data.configurations*.dependencies.flatten().poms*.flatten()*.name.flatten() as Set == ["pom1", "pom2", "pom3"] as Set
        data.configurations*.dependencies.flatten().poms*.flatten()*.licenses.flatten().isEmpty()
        data.importedModules.isEmpty()
    }

    def "it creates the poms with licenses"() {
        when:
        ProjectData data = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                    pom("pom2") {
                        license(MIT_LICENSE())
                    }
                }
                module("mod2") {
                    pom("pom3") {
                        license(MIT_LICENSE())
                        license(LGPL_LICENSE())
                    }
                }
            }
            configuration("test") { }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set

        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom1" }.licenses as List == [APACHE2_LICENSE()]
        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom2" }.licenses as List == [MIT_LICENSE()]
        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom3" }.licenses as List == [LGPL_LICENSE(), MIT_LICENSE()]

        data.importedModules.isEmpty()
    }

    def "it creates the poms license with custom name and url"() {
        when:
        ProjectData data = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(name: "abc")
                    }
                    pom("pom2") {
                        license(url: "123")
                        license(LGPL_LICENSE(), name: "lgpl123")
                    }
                }
                module("mod2") {
                    pom("pom3") {
                        license(LGPL_LICENSE())
                    }
                }
            }
            configuration("test") { }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set

        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom1" }.licenses*.name == ["abc"]
        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom2" }.licenses*.name == [null, "lgpl123"]
        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom2" }.licenses*.url == ["123", LGPL_LICENSE().url]
        data.configurations*.dependencies.flatten().poms.flatten().find { it.name == "pom3" }.licenses*.name == [LGPL_LICENSE().name]

        data.importedModules.isEmpty()
    }

    def "it creates manifests with licenses"() {
        when:
        ProjectData data = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license(APACHE2_LICENSE().name)
                    }
                    manifest("mani2") {
                        license(MIT_LICENSE())
                    }
                }
                module("mod2") {
                    manifest("mani3") {
                        license(LGPL_LICENSE().url)
                    }
                }
            }
            configuration("test") { }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set

        data.configurations*.dependencies.flatten().manifests.flatten().find { it.name == "mani1" }*.license == [APACHE2_LICENSE().name]
        data.configurations*.dependencies.flatten().manifests.flatten().find { it.name == "mani2" }*.license == [MIT_LICENSE().name]
        data.configurations*.dependencies.flatten().manifests.flatten().find { it.name == "mani3" }*.licenseUrl == [LGPL_LICENSE().url]

        data.importedModules.isEmpty()
    }

    def "it creates modules with license-files"() {
        when:
        ProjectData data = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFiles {
                        licenseFileDetails(file: "file1", license: "lic1", licenseUrl: "licUrl1")
                        licenseFileDetails(file: "file2", license: "lic2", licenseUrl: "licUrl2")
                    }
                    licenseFiles {
                        licenseFileDetails(file: "file3", license: "lic3", licenseUrl: "licUrl3")
                    }
                }
            }
        }

        then:
        data.configurations*.dependencies.flatten().licenseFiles.flatten().fileDetails.flatten()*.file.sort() ==["file1", "file2", "file3"].sort()
        data.configurations*.dependencies.flatten().licenseFiles.flatten().fileDetails.flatten()*.license.sort() == ["lic1", "lic2", "lic3"].sort()
        data.configurations*.dependencies.flatten().licenseFiles.flatten().fileDetails.flatten()*.licenseUrl.sort() == ["licUrl1", "licUrl2", "licUrl3"].sort()
        data.importedModules.isEmpty()
    }

    def "it creates imported-modules"() {
        when:
        ProjectData data = builder.project {
            importedModulesBundle("bundle1") {
                importedModule(name: "mod1", license: "Apache  2", licenseUrl: "apache-url")
            }
        }

        then:
        !data.importedModules.isEmpty()
        data.importedModules*.modules.flatten().name == ["mod1"]
        data.importedModules*.modules.flatten().license == ["Apache  2"]
        data.importedModules*.modules.flatten().licenseUrl == ["apache-url"]
        data.configurations.isEmpty()
    }

    def "it creates all the configuration details for the given configurations"() {
        when:
        ProjectData data = builder.project {
            configurations(["runtime", "test"]) { configName ->
                configuration(configName) {
                    module("mod1") {
                        pom("pom1") { }
                        manifest("mani1") {
                            license(APACHE2_LICENSE().name)
                        }
                        licenseFiles {
                            licenseFileDetails(file: "file1", license: "lic1", licenseUrl: "licUrl1")
                        }
                    }
                    module("mod2") {
                        pom("pom2") { }
                        manifest("mani2") {
                            license(MIT_LICENSE())
                        }
                        licenseFiles {
                            licenseFileDetails(file: "file2", license: "lic2", licenseUrl: "licUrl2")
                        }
                    }
                }
            }
        }

        then:
        data != null
        data.project != null
        data.configurations*.name as Set == ["runtime", "test"] as Set
        data.configurations.forEach {
            assert it.dependencies.flatten().poms*.flatten()*.name.flatten() as Set == ["pom1", "pom2"] as Set
            assert it.dependencies.flatten().poms*.flatten()*.name.flatten() as Set == ["pom1", "pom2"] as Set

            assert it.dependencies.flatten().manifests.flatten().find { it.name == "mani1" }*.license == [APACHE2_LICENSE().name]
            assert it.dependencies.flatten().manifests.flatten().find { it.name == "mani2" }*.license == [MIT_LICENSE().name]

            assert it.dependencies.flatten().licenseFiles.flatten().fileDetails.flatten()*.file == ["file1", "file2"]
            assert it.dependencies.flatten().licenseFiles.flatten().fileDetails.flatten()*.license == ["lic1", "lic2"]
            assert it.dependencies.flatten().licenseFiles.flatten().fileDetails.flatten()*.licenseUrl == ["licUrl1", "licUrl2"]
        }
        data.importedModules.isEmpty()
    }
}
