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
