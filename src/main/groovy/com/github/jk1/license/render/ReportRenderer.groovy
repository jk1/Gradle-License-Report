package com.github.jk1.license.render

import com.github.jk1.license.task.DependencyLicenseReport
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency


trait ReportRenderer {

    abstract void startProject(DependencyLicenseReport report)

    abstract void addConfiguration(DependencyLicenseReport report, Configuration configuration)

    abstract void completeProject(DependencyLicenseReport report)

    abstract void startConfiguration(DependencyLicenseReport report, Configuration configuration)

    abstract void addDependency(DependencyLicenseReport report, Configuration configuration, ResolvedDependency dependency)

    abstract void completeConfiguration(DependencyLicenseReport report, Configuration configuration)
}