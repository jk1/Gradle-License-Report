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
package com.github.jk1.license.filter

import com.github.jk1.license.ProjectBuilder
import com.github.jk1.license.ProjectData
import spock.lang.Specification

import static com.github.jk1.license.ProjectBuilder.json
import static com.github.jk1.license.ProjectDataFixture.GRADLE_PROJECT

class SpdxLicenseNormalizerSpec extends Specification {

    File pluginOutputDir

    ProjectBuilder builder = new ProjectBuilder()

    def setup() {
        pluginOutputDir = new File(GRADLE_PROJECT().licenseReport.outputDir)
        pluginOutputDir.mkdirs()

        // copy apache2 license file
        def apache2LicenseFile = new File(getClass().getResource("/apache2.license").toURI())
        new File(pluginOutputDir, "apache2.license") << apache2LicenseFile.text
    }

    def "normalize license of manifest by matching license name"() {
        when:
        def result = new SpdxLicenseBundleNormalizer().filter(buildProjectWithManifestLicense("Apache 2"))

        then:
        json(result) == json(buildProjectWithManifestLicense("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"))
    }

    def "spdx bundle normalizes legitimate BSD-3-Clause '#inputName' (#inputUrl)"() {
        when:
        def result = new SpdxLicenseBundleNormalizer().filter(buildProjectWithPomLicense(inputName, inputUrl))

        then:
        json(result) == json(buildProjectWithPomLicense("BSD-3-Clause", expectedUrl))

        where:
        inputName                                  | inputUrl                                              | expectedUrl
        "BSD-3-Clause"                             | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "The 3-Clause BSD License"                 | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD 3-Clause"                             | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD 3-clause"                             | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD 3-Clause License"                     | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "The BSD 3-Clause License"                 | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD-3-Clause License"                     | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD License (3-Clause)"                   | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        'BSD 3-Clause "New" or "Revised" License'  | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "New BSD License"                          | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "The New BSD License"                      | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "Modified BSD License"                     | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD"                                      | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD License"                              | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "BSD-License"                              | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "bsd license"                              | null                                                  | "https://opensource.org/licenses/BSD-3-Clause"
        "Unknown"                                  | "https://opensource.org/licenses/BSD-3-Clause"        | "https://opensource.org/licenses/BSD-3-Clause"
        null                                       | "http://www.opensource.org/licenses/BSD-3-Clause"     | "https://opensource.org/licenses/BSD-3-Clause"
        "Eclipse Distribution License - v 1.0"     | "https://www.eclipse.org/org/documents/edl-v10.php"   | "https://www.eclipse.org/org/documents/edl-v10.php"
    }

    def "spdx bundle does not collapse BSD-3-Clause derivative '#inputName' into BSD-3-Clause"() {
        when:
        def result = new SpdxLicenseBundleNormalizer().filter(buildProjectWithPomLicense(inputName))

        then:
        result.configurations.every { cfg ->
            cfg.dependencies.every { mod ->
                mod.poms.every { pom ->
                    pom.licenses.size() == 1 && pom.licenses[0].name != "BSD-3-Clause"
                }
            }
        }
        json(result) == json(buildProjectWithPomLicense(expectedName, expectedUrl))

        where:
        inputName                                        | expectedName                           | expectedUrl
        "BSD 3-clause License w/nuclear disclaimer"      | "BSD-3-Clause-No-Nuclear-Warranty"     | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-Warranty.html"
        "BSD 3-Clause License with nuclear disclaimer"   | "BSD-3-Clause-No-Nuclear-Warranty"     | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-Warranty.html"
        "BSD-3-Clause-No-Nuclear-Warranty"               | "BSD-3-Clause-No-Nuclear-Warranty"     | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-Warranty.html"
        "BSD 3-Clause No Nuclear Warranty"               | "BSD-3-Clause-No-Nuclear-Warranty"     | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-Warranty.html"
        "BSD-3-Clause-No-Nuclear-License"                | "BSD-3-Clause-No-Nuclear-License"      | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-License.html"
        "BSD 3-Clause No Nuclear License"                | "BSD-3-Clause-No-Nuclear-License"      | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-License.html"
        "BSD-3-Clause-No-Nuclear-License-2014"           | "BSD-3-Clause-No-Nuclear-License-2014" | "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-License-2014.html"
        "BSD-3-Clause-No-Military-License"               | "BSD-3-Clause-No-Military-License"     | "https://spdx.org/licenses/BSD-3-Clause-No-Military-License.html"
        "BSD 3-Clause No Military License"               | "BSD-3-Clause-No-Military-License"     | "https://spdx.org/licenses/BSD-3-Clause-No-Military-License.html"
        "BSD 3-clause License w/military disclaimer"     | "BSD-3-Clause-No-Military-License"     | "https://spdx.org/licenses/BSD-3-Clause-No-Military-License.html"
        "BSD 3-Clause License with military restriction" | "BSD-3-Clause-No-Military-License"     | "https://spdx.org/licenses/BSD-3-Clause-No-Military-License.html"
        "BSD-3-Clause-Clear"                             | "BSD-3-Clause-Clear"                   | "https://spdx.org/licenses/BSD-3-Clause-Clear.html"
        "BSD 3-Clause Clear License"                     | "BSD-3-Clause-Clear"                   | "https://spdx.org/licenses/BSD-3-Clause-Clear.html"
        "BSD-3-Clause-Attribution"                       | "BSD-3-Clause-Attribution"             | "https://spdx.org/licenses/BSD-3-Clause-Attribution.html"
        "BSD 3-Clause Attribution"                       | "BSD-3-Clause-Attribution"             | "https://spdx.org/licenses/BSD-3-Clause-Attribution.html"
        "BSD-3-Clause-LBNL"                              | "BSD-3-Clause-LBNL"                    | "https://opensource.org/licenses/BSD-3-Clause-LBNL"
        "BSD-3-Clause-Modification"                      | "BSD-3-Clause-Modification"            | "https://spdx.org/licenses/BSD-3-Clause-Modification.html"
        "BSD-3-Clause-Open-MPI"                          | "BSD-3-Clause-Open-MPI"                | "https://opensource.org/licenses/BSD-3-Clause-Open-MPI"
        "BSD-3-Clause-Sun"                               | "BSD-3-Clause-Sun"                     | "https://spdx.org/licenses/BSD-3-Clause-Sun.html"
        "BSD-3-Clause-HP"                                | "BSD-3-Clause-HP"                      | null
        "BSD 3-Clause with custom restriction"           | "BSD 3-Clause with custom restriction" | null
        "BSD 3-Clause License (commercial and military use)" | "BSD 3-Clause License (commercial and military use)" | null
        "BSD License w/ nuclear disclaimer"              | "BSD License w/ nuclear disclaimer"    | null
        "Modified BSD License with nuclear disclaimer"   | "Modified BSD License with nuclear disclaimer" | null
        "The New BSD License with nuclear warranty"      | "The New BSD License with nuclear warranty" | null
    }

    def "spdx bundle normalizes nuclear disclaimer in pom and manifest"() {
        ProjectData projectData = buildProjectWithPomAndManifestLicense("BSD 3-clause License w/nuclear disclaimer")
        ProjectData expected = buildProjectWithPomAndManifestLicense(
            "BSD-3-Clause-No-Nuclear-Warranty",
            "https://spdx.org/licenses/BSD-3-Clause-No-Nuclear-Warranty.html"
        )

        when:
        def result = new SpdxLicenseBundleNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    private ProjectData buildProjectWithPomLicense(String name, String url = null) {
        return builder.project {
            configurations(["runtime", "test"]) { configName ->
                configuration(configName) {
                    module("mod1") {
                        pom("pom1") {
                            license(name: name, url: url)
                        }
                    }
                }
            }
        }
    }

    private ProjectData buildProjectWithPomAndManifestLicense(String name, String url = null) {
        return builder.project {
            configurations(["runtime", "test"]) { configName ->
                configuration(configName) {
                    module("mod1") {
                        pom("pom1") {
                            license(name: name, url: url)
                        }
                        manifest("mani1") {
                            license(name: name, url: url)
                        }
                    }
                }
            }
        }
    }

    private ProjectData buildProjectWithManifestLicense(String name, String url = null){
        return builder.project {
            configurations(["runtime", "test"]) { configName ->
                configuration(configName) {
                    module("mod1") {
                        manifest("mani1") {
                            license(name: name, url: url)
                        }
                    }
                }
            }
        }
    }
}
