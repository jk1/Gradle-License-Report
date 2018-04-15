package com.github.jk1.license

import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

class LicenseReportExtension {
    public static final String[] ALL = []

    public String outputDir
    public ReportRenderer[] renderers
    public DependencyDataImporter[] importers
    public DependencyFilter[] filters
    public String[] configurations
    public String[] excludeGroups
    public String[] excludes

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
