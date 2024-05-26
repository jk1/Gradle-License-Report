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

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ProjectData
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
        notCompatibleWithConfigurationCache("com.github.jk1:gradle-license-report is not configuration-cache compatible")
    }

    @Nested
    LicenseReportExtension config

    @OutputDirectory
    File getOutputFolder() {
        return new File(config.absoluteOutputDir)
    }

    @Input
    String[] getClasspath() {
        def reader = new ProjectReader(config)
        // take configurations' shallow snapshot but don't revolve them
        def deps = getConfig().projects
                .collectMany { reader.findConfigurationsToScan(it) }
                .collectMany { it.allDependencies }
                .collect { it.name + it.group + it.version}
        deps
    }

    @TaskAction
    void generateReport() {
        def project = config.projects.first()
        LOGGER.info("Processing dependencies for project ${project.name}")
        new File(config.absoluteOutputDir).mkdirs()
        ProjectData data = new ProjectReader(config).read(project)
        LOGGER.info("Importing external dependency data. A total of ${config.importers.length} configured.")
        config.importers.each {
            data.importedModules.addAll(it.doImport())
        }
        LOGGER.info("Applying dependency filters. A total of ${config.filters.length} configured.")
        config.filters.each {
            data = it.filter(data)
        }
        LOGGER.info("Building report for project ${project.name}")
        config.renderers.each {
            it.render(data)
        }
        LOGGER.info("Dependency license report for project ${project.name} created in ${config.absoluteOutputDir}")
    }
}
