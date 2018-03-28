package com.github.jk1.license

import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

class LicenseReportPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.extensions.add('licenseReport', new LicenseReportExtension(project))
        project.task(['type': ReportTask.class], 'generateLicenseReport')
    }

    static class LicenseReportExtension {

        String outputDir
        ReportRenderer renderer
        DependencyDataImporter[] importers
        DependencyFilter[] filters
        String[] configurations
        String[] excludeGroups
        String[] excludes

        LicenseReportExtension(Project project) {
            outputDir = "${project.buildDir}/reports/dependency-license"
            renderer = new SimpleHtmlReportRenderer()
            configurations = ['runtime']
            excludeGroups = [project.group]
            excludes = []
            importers = []
            filters = []
        }

        boolean isExcluded(ResolvedDependency module) {
            return excludeGroups.contains(module.moduleGroup) ||
                excludes.contains("$module.moduleGroup:$module.moduleName")
        }

        // configuration snapshot for the up-to-date check
        private String getSnapshot() {
            StringBuilder builder = new StringBuilder()
            builder.append(renderer.class.name)
            importers.each { builder.append(it.class.name) }
            filters.each { builder.append(it.class.name) }
            configurations.each { builder.append(it) }
            excludeGroups.each { builder.append(it) }
            excludes.each { builder.append(it) }
            return builder.toString()
        }
    }
}
