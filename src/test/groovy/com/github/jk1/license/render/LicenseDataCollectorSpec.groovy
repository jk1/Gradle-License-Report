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
        def result = LicenseDataCollector.singleModuleLicenseInfo(null, moduleData)

        then:
        result.licenses.isEmpty()
    }

}
