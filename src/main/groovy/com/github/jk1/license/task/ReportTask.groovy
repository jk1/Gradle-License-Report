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
package com.github.jk1.license.task

import com.github.jk1.license.LicenseReportSettings
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ProjectInfo
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.reader.CollectedDependencies
import com.github.jk1.license.reader.ProjectReader
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject

@CacheableTask
abstract class ReportTask extends DefaultTask {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    ReportTask() {
        group = 'Reporting'
        description = 'Generates license report for all dependencies of this project and its subprojects'
    }

    /** Dependency data of the covered projects, as written by their {@link CollectDependenciesTask}s. */
    @Internal
    abstract ConfigurableFileCollection getDependencyData()

    /**
     * Coordinates of the dependencies of the covered projects. Only these are needed for the cache key, not the
     * (machine specific) locations of the artifacts: any dependency change the report would surface invalidates it.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getDependencyCoordinates()

    @Input
    abstract Property<ProjectInfo> getProjectInfo()

    @Input
    abstract Property<LicenseReportSettings> getSettings()

    @Internal
    abstract ListProperty<ReportRenderer> getRenderers()

    @Internal
    abstract ListProperty<DependencyDataImporter> getImporters()

    @Internal
    abstract ListProperty<DependencyFilter> getFilters()

    @Nested
    List<ReportRenderer> getBuiltInRenderers() {
        ReportComponents.builtIn(renderers.get())
    }

    @Nested
    List<DependencyDataImporter> getBuiltInImporters() {
        ReportComponents.builtIn(importers.get())
    }

    @Nested
    List<DependencyFilter> getBuiltInFilters() {
        ReportComponents.builtIn(filters.get())
    }

    /** See {@link ReportComponents}: custom renderers, importers and filters are fingerprinted leniently. */
    @Input
    List<String> getCustomComponentInputs() {
        ReportComponents.customInputValues(allComponents()) { Object files -> objects.fileCollection().from(files) }
    }

    @Inject
    protected abstract ObjectFactory getObjects()

    private List<Object> allComponents() {
        (renderers.get() as List<Object>) + importers.get() + filters.get()
    }

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Internal
    File getOutputFolder() {
        outputDir.get().asFile
    }

    @TaskAction
    void generateReport() {
        File outputFolder = getOutputFolder()
        outputFolder.mkdirs()
        LicenseReportSettings settings = getSettings().get().withOutputDir(outputFolder.absolutePath)
        ProjectInfo project = getProjectInfo().get().withSettings(settings)

        List<CollectedDependencies> collected = dependencyData.files.collect { CollectedDependencies.fromJson(it) }
        ProjectData data = new ProjectReader(settings).read(project, collected)
        List<DependencyDataImporter> importers = getImporters().get()
        LOGGER.info("Importing external dependency data. A total of ${importers.size()} configured.")
        importers.each {
            data.importedModules.addAll(it.doImport())
        }
        List<DependencyFilter> filters = getFilters().get()
        LOGGER.info("Applying dependency filters. A total of ${filters.size()} configured.")
        filters.each {
            data = it.filter(data)
        }
        LOGGER.info("Building report for project ${data.project.name}")
        (getRenderers().get() + checkLicenseDataRenderer()).each {
            it.render(data)
        }
        LOGGER.info("Dependency license report for project ${data.project.name} created in ${outputFolder}")
    }

    /** Renders the data consumed by {@link CheckLicenseTask}. */
    private static ReportRenderer checkLicenseDataRenderer() {
        new JsonReportRenderer(CheckLicenseTask.PROJECT_JSON_FOR_LICENSE_CHECKING_FILE, false)
    }
}
