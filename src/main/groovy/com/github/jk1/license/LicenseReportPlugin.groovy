package com.github.jk1.license

import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
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

    static class LicenseReportExtension {

        public static final String[] ALL = []

        String outputDir
        ReportRenderer[] renderers
        DependencyDataImporter[] importers
        DependencyFilter[] filters
        String[] configurations
        String[] excludeGroups
        String[] excludes

        LicenseReportExtension(Project project) {
            outputDir = "${project.buildDir}/reports/dependency-license"
            renderers = new SimpleHtmlReportRenderer()
            configurations = ['runtime']
            excludeGroups = [project.group]
            excludes = []
            importers = []
            filters = []
        }

        /**
         * Use #renderers instead
         */
        @Deprecated
        void setRenderer(ReportRenderer renderer) {
            renderers = renderer
        }

        /**
         * Use #renderers instead
         */
        @Deprecated
        ReportRenderer getRenderer() {
            if (renderers != null && renderers.size() > 0) {
                return renderers[0]
            }
            return null
        }

        boolean isExcluded(ResolvedDependency module) {
            return excludeGroups.contains(module.moduleGroup) ||
                excludes.contains("$module.moduleGroup:$module.moduleName")
        }

        // configuration snapshot for the up-to-date check
        String getSnapshot() {
            StringBuilder builder = new StringBuilder()

            renderers.each { builder.append(it.class.name) }
            importers.each { builder.append(it.class.name) }
            filters.each { builder.append(it.class.name) }
            configurations.each { builder.append(it) }
            excludeGroups.each { builder.append(it) }
            excludes.each { builder.append(it) }
            return builder.toString()
        }
    }
}
