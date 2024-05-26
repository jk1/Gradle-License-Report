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
import spock.lang.Snapshot
import spock.lang.Snapshotter
import spock.lang.Specification
import spock.lang.TempDir

import static com.github.jk1.license.ProjectDataFixture.*

class AbstractInventoryReportRendererSpec extends Specification {

    @Snapshot
    Snapshotter snapshotter
    @TempDir
    File testProjectDir
    File outputFile
    File overrides

    ProjectBuilder builder = new ProjectBuilder()
    ProjectData projectData

    def setup() {
        outputFile = new File(testProjectDir, "output")
        outputFile.delete()
        overrides = new File(testProjectDir, "overrides.txt")
        overrides.text = "dummy-group:mod2:0.0.1|https://projecturl|Apache License, Version 2.0|http://www.apache.org/licenses/LICENSE-2.0.txt"

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
                            license(UNKNOWN_LICENSE())
                        }
                    }
                    module("mod3") {
                        pom("pom3") {
                            license(UNKNOWN_LICENSE())
                        }
                    }

                    module("mod4") {
                        pom("pom2") {
                            license(APACHE2_LICENSE())
                        }
                        pom("pom3") {
                            license(APACHE2_LICENSE())
                            license(MIT_LICENSE())
                        }
                        licenseFiles {
                            licenseFileDetails(file: "apache2.license", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org / licenses / LICENSE - 2.0 ")
                        }
                        manifest("mani1") {
                            license("Apache 2.0")
                        }
                    }
                }

                importedModulesBundle("bundle1") {
                    importedModule(name: "mod1", license: "Apache  2", licenseUrl: "apache-url")
                    importedModule(name: "mod2", license: "Apache  2", licenseUrl: "apache-url")
                }
            }
        }
    }
}
