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

class ExcludeDependenciesWithoutArtifactsFilterSpec extends Specification {
    def filter = new ExcludeDependenciesWithoutArtifactsFilter()
    ProjectBuilder builder = new ProjectBuilder()

    def "modules without artifacts should be deleted"() {
        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    hasArtifactFile = true
                }
                module("mod2") {
                    hasArtifactFile = false
                }
            }
            configuration("test") {
                module("mod3") {
                    hasArtifactFile = false
                }
            }
        }

        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    hasArtifactFile = true
                }
            }
            configuration("test")
        }


        when:
        def result = filter.filter(projectData)

        then:
        json(result) == json(expected)
    }
}
