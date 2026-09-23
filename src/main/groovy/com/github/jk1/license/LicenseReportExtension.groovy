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

/**
 * Build script DSL of the plugin. Only read at configuration time: tasks receive a configuration-cache compatible
 * snapshot of it (see {@link LicenseReportSettings}).
 */
class LicenseReportExtension {

    public static final String[] ALL = []

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
    public boolean unionParentPomLicenses
    public Object allowedLicensesFile

    private final Project project

    LicenseReportExtension(Project project) {
        this.project = project
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

    String getAbsoluteOutputDir(){
        if (new File(outputDir).isAbsolute()) {
            return outputDir
        } else {
            return reportedProject.layout.projectDirectory.dir(outputDir).asFile.absolutePath
        }
    }

    /** The project the report is named after: the first of the configured {@link #projects}. */
    Project getReportedProject() {
        projects ? projects.first() : project
    }
}
