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
        projects.each { builder.append(it.name) }
        renderers.each { builder.append(it.class.name) }
        importers.each { builder.append(it.class.name) }
        filters.each { builder.append(it.class.name) }
        configurations.each { builder.append(it) }
        excludeGroups.each { builder.append(it) }
        excludes.each { builder.append(it) }
        return builder.toString()
    }
}
