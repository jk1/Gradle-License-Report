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
package com.github.jk1.license

import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

class LicenseReportExtension {
    public static final String[] ALL = []

    public String outputDir
    public Project[] projects
    public ReportRenderer[] renderers
    public DependencyDataImporter[] importers
    public DependencyFilter[] filters
    public String[] configurations
    public String[] excludeGroups
    public String[] excludes

    LicenseReportExtension(Project project) {
        outputDir = "${project.buildDir}/reports/dependency-license"
        projects = [project] + project.subprojects
        renderers = new SimpleHtmlReportRenderer()
        configurations = ['runtime']
        excludeGroups = []
        excludes = []
        importers = []
        filters = []
    }

    @Input
    private String getLicenseReportExtensionSnapshot() {
        def snapshot = []
        snapshot << 'projects'
        snapshot += projects.collect { it.path }
        snapshot << 'renderers'
        snapshot += renderers.collect { it.class.name }
        snapshot << 'importers'
        snapshot += importers.collect { it.class.name }
        snapshot << 'filters'
        snapshot += filters.collect { it.class.name }
        snapshot << 'configurations '
        snapshot += configurations
        snapshot << 'exclude'
        snapshot += excludeGroups
        snapshot << 'excludes'
        snapshot += excludes
        snapshot.join("!")
    }

    @Nested
    private List<ReportRenderer> getRenderersCache() { return this.renderers }

    @Nested
    private List<DependencyDataImporter> getImportersCache() { return this.importers }

    @Nested
    private List<DependencyFilter> getFiltersCache() { return this.filters }

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
        return projects.any { module.moduleGroup == it.group } ||
            excludeGroups.contains(module.moduleGroup) ||
            excludes.contains("$module.moduleGroup:$module.moduleName")
    }

}
