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
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

class LicenseReportExtension {

    public static final String[] ALL = []

    public boolean unionParentPomLicenses
    public String outputDir
    public Project[] projects
    public Project[] buildScriptProjects
    public ReportRenderer[] renderers
    public DependencyDataImporter[] importers
    public DependencyFilter[] filters
    public String[] configurations
    public boolean excludeOwnGroup
    public boolean excludeBoms
    public String[] excludeGroups
    public String[] excludes
    public Object allowedLicensesFile

    LicenseReportExtension(Project project) {
        unionParentPomLicenses = true
        outputDir = project.layout.buildDirectory.dir("reports/dependency-license").get().asFile.absolutePath
        projects = [project] + project.subprojects
        buildScriptProjects = []
        renderers = new SimpleHtmlReportRenderer()
        configurations = null
        excludeOwnGroup = true
        excludeBoms = false // false - for backwards compatibility
        excludeGroups = []
        excludes = []
        importers = []
        filters = []
    }

    @Nested
    ReportRenderer[] getRenderers() {
        return renderers
    }

    @Nested
    DependencyDataImporter[] getImporters() {
        return importers
    }

    @Nested
    DependencyFilter[] getFilters() {
        return filters
    }

    @Internal
    String getAbsoluteOutputDir(){
        if (new File(outputDir).isAbsolute()) {
            return outputDir
        } else {
            return projects.first().layout.projectDirectory.dir(outputDir).asFile.absolutePath
        }
    }

    @Input
    String getLicenseReportExtensionSnapshot() {
        def snapshot = []
        snapshot << 'projects'
        snapshot += projects.collect { it.path }
        snapshot << 'buildScriptProjects'
        snapshot += buildScriptProjects.collect { it.path }
        snapshot << 'renderers'
        snapshot += renderers.collect { it.class.name }
        snapshot << 'importers'
        snapshot += importers.collect { it.class.name }
        snapshot << 'filters'
        snapshot += filters.collect { it.class.name }
        snapshot << 'configurations '
        snapshot += configurations
        snapshot << 'excludeOwnGroup'
        snapshot += excludeOwnGroup
        snapshot << 'excludeBoms'
        snapshot += excludeBoms
        snapshot << 'exclude'
        snapshot += excludeGroups
        snapshot << 'excludes'
        snapshot += excludes
        snapshot << 'unionParentPomLicenses'
        snapshot += unionParentPomLicenses
        snapshot.join("!")
    }

    // todo: migrate me to a filter
    boolean isExcluded(ResolvedDependency module) {
        return shouldExcludeOwnGroup(module) ||
            shouldExcludeGroup(module) ||
            shouldExcludeBom(module) ||
            shouldExcludeArtifact(module)
    }

    private boolean shouldExcludeOwnGroup(ResolvedDependency module) {
        excludeOwnGroup && projects.any { module.moduleGroup == it.group }
    }

    private boolean shouldExcludeGroup(ResolvedDependency module) {
        excludeGroups.contains(module.moduleGroup) || excludeGroups.any { module.moduleGroup.matches(it) }
    }

    private boolean shouldExcludeBom(ResolvedDependency module) {
        excludeBoms &&
            (module.getModuleName().endsWith("-bom") || module.getModuleName() == "bom") &&
            module.getModuleArtifacts().isEmpty()
    }

    private boolean shouldExcludeArtifact(ResolvedDependency module) {
        def coordinates = "$module.moduleGroup:$module.moduleName"
        excludes.contains(coordinates) || excludes.any { coordinates.matches(it) }
    }
}
