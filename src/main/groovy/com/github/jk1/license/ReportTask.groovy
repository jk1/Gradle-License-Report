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

import com.github.jk1.license.reader.ProjectReader
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*

@CacheableTask
class ReportTask extends DefaultTask {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    ReportTask() {
        group = 'Reporting'
        description = 'Generates license report for all dependencies of this project and its subprojects'
    }

    @InputFiles
    FileCollection getClasspath() {
        getConfig().projects
            .collectMany { ProjectReader.findConfiguredConfigurations(it, getConfig()) }
            .inject(project.files(), { FileCollection memo, eachConfiguration -> memo + eachConfiguration })
    }

    @Nested
    LicenseReportExtension getConfig() {
        return getProject().licenseReport
    }

    @OutputDirectory
    File getOutputFolder() {
        return new File(getConfig().outputDir)
    }

    @TaskAction
    void generateReport() {
        LOGGER.info("Processing dependencies for project ${getProject().name}")
        new File(config.outputDir).mkdirs()
        ProjectData data = new ProjectReader(config).read(getProject())
        LOGGER.info("Importing external dependency data. A total of ${config.importers.length} configured.")
        config.importers.each {
            data.importedModules.addAll(it.doImport())
        }
        LOGGER.info("Applying dependency filters. A total of ${config.filters.length} configured.")
        config.filters.each {
            data = it.filter(data)
        }
        LOGGER.info("Building report for project ${getProject().name}")
        config.renderers.each {
            it.render(data)
        }
        LOGGER.info("Dependency license report for project ${getProject().name} created in ${config.outputDir}")
    }
}
