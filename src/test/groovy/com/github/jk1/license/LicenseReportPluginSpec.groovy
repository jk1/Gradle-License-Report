package com.github.jk1.license

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class LicenseReportPluginSpec extends Specification {

    def "plugin should be applicable to a project"() {
        Project project = ProjectBuilder.builder().build()

        when:
        project.pluginManager.apply 'com.github.jk1.dependency-license-report'

        then:
        project.licenseReport
    }
}
