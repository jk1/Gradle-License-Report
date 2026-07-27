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

class ImportedModuleNameFilterSpec extends Specification {
    def filter = new ImportedModuleNameFilter()
    ProjectBuilder builder = new ProjectBuilder()

    def "filter imported modules by exact name"() {
        def data = builder.project {
            importedModulesBundle("bundle1") {
                importedModule(name: "mod1", license: "Apache  2", licenseUrl: "apache-url")
                importedModule(name: "mod2", license: "Apache  2", licenseUrl: "apache-url")
            }
        }
        data.extension.excludes = ["mod2", "mod"]

        when:
        def result = filter.filter(data)

        then:
        !result.importedModules.isEmpty()
        result.importedModules*.modules.flatten().name == ["mod1"]
    }

    def "filter imported modules by regex"() {
        def data = builder.project {
            importedModulesBundle("bundle1") {
                importedModule(name: "ext1", license: "Apache  2", licenseUrl: "apache-url")
                importedModule(name: "mod1", license: "Apache  2", licenseUrl: "apache-url")
                importedModule(name: "mod2", license: "Apache  2", licenseUrl: "apache-url")
            }
        }
        data.extension.excludes = ["mod.*"]

        when:
        def result = filter.filter(data)

        then:
        !result.importedModules.isEmpty()
        result.importedModules*.modules.flatten().name == ["ext1"]
    }
}
