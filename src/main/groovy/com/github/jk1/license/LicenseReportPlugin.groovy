package com.github.jk1.license

import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import com.github.jk1.license.task.ReportTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class LicenseReportPlugin implements Plugin<Project> {

    private Logger LOGGER = Logging.getLogger(Plugin.class)

    @Override
     void apply(Project project) {
        LicenseReportExtension ext = new LicenseReportExtension()
        project.extensions.add('licenseReport', ext)
        project.task(['type': ReportTask.class], "generateLicenseReport")
        project.afterEvaluate {
            ext.afterEvaluate(project)
        }
    }

    static class LicenseReportExtension {

        private Logger LOGGER = Logging.getLogger(Plugin.class)

        String outputDir
        ReportRenderer renderer
        DependencyDataImporter[] importers
        String[] configurations
        String[] excludeGroups
        String[] excludes

        void afterEvaluate(Project project) {
            if (!outputDir) {
                outputDir = "${project.buildDir}/reports/dependency-license"
            }
            project.tasks.generateLicenseReport.outputs.dir(outputDir)
            LOGGER.debug("Using dependency license report output dir: $outputDir")
            if (!renderer) {
                renderer = new SimpleHtmlReportRenderer()
            }
            if (!configurations) {
                configurations = ['runtime']
            }
            if (!excludeGroups) {
                excludeGroups = [project.group]
            }
            if (!excludes) {
                excludes = []
            }
            if (!importers) {
                importers = new DependencyDataImporter[0]
            }
        }

        void beforeExecute() {
            new File(outputDir).mkdirs()
        }

        boolean isExcluded(ResolvedDependency module){
            return excludeGroups.contains(module.moduleGroup) ||
                    excludes.contains("$module.moduleGroup:$module.moduleName")
        }
    }
}
