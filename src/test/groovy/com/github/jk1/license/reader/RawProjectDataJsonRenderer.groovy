package com.github.jk1.license.reader

import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.ProjectData
import com.github.jk1.license.render.ReportRenderer
import groovy.json.JsonBuilder

class RawProjectDataJsonRenderer implements ReportRenderer {
    static String RAW_PROJECT_JSON_NAME = "raw-project-data.json"

    @Override
    void render(ProjectData data) {
        LicenseReportPlugin.LicenseReportExtension config = data.project?.licenseReport
        File outputFile = new File(config.outputDir, RAW_PROJECT_JSON_NAME)
        outputFile.createNewFile()

        def project = data.project
        data.project = null

        def json = new JsonBuilder(data).toPrettyString()
        outputFile << json

        data.project = project
    }
}
