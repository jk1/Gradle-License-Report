package com.github.jk1.license.task

import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ProjectData
import com.github.jk1.license.reader.ProjectReader
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction

class ReportTask extends DefaultTask {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    @TaskAction
     void generateReport() {
        LOGGER.info("Processing dependencies for project ${getProject().name}")
        LicenseReportExtension config = getProject().licenseReport
        config.beforeExecute()
        ProjectData data = new ProjectReader().read(getProject())
        LOGGER.info("Building report for project ${getProject().name}")
        config.renderer.render(data)
        LOGGER.info("Dependency license report for project ${getProject().name} created in ${config.outputDir}")
    }
}
