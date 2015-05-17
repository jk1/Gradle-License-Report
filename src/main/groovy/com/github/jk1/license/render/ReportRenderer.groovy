package com.github.jk1.license.render

import com.github.jk1.license.task.LicenseReportTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency


trait ReportRenderer {

    abstract void startProject(LicenseReportTask report)

    abstract void addConfiguration(LicenseReportTask report, Configuration configuration)

    abstract void completeProject(LicenseReportTask report)

    abstract void startConfiguration(LicenseReportTask report, Configuration configuration)

    abstract void addDependency(LicenseReportTask report, Configuration configuration, ResolvedDependency dependency)

    abstract void completeConfiguration(LicenseReportTask report, Configuration configuration)
}