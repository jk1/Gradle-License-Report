package com.github.jk1.license

import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.reader.ProjectReader
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class ReportTask extends DefaultTask {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    @OutputDirectory
    File getOutputFolder(){
        return new File(getProject().licenseReport.outputDir)
    }

    @TaskAction
    void generateReport() {
        LOGGER.info("Processing dependencies for project ${getProject().name}")
        LicenseReportExtension config = getProject().licenseReport
        new File(config.outputDir).mkdirs()
        ProjectData data = new ProjectReader().read(getProject())
        LOGGER.info("Building report for project ${getProject().name}")
        config.renderer.render(data)
        LOGGER.info("Dependency license report for project ${getProject().name} created in ${config.outputDir}")
    }
}
