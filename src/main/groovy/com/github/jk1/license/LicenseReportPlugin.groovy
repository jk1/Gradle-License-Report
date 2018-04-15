package com.github.jk1.license

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class LicenseReportPlugin implements Plugin<Project> {

    final def MINIMUM_REQUIRED_GRADLE_VERSION = "3.3"

    @Override
    void apply(Project project) {
        assertCompatibleGradleVersion()

        project.extensions.create('licenseReport', LicenseReportExtension, project)
        project.tasks.create('generateLicenseReport', ReportTask)
    }

    private void assertCompatibleGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_REQUIRED_GRADLE_VERSION)) {
            throw new GradleException("License Report Plugin requires Gradle $MINIMUM_REQUIRED_GRADLE_VERSION. ${GradleVersion.current()} detected.")
        }
    }
}
