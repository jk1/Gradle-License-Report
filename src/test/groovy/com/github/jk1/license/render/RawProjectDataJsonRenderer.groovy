package com.github.jk1.license.render

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ProjectData
import groovy.json.JsonBuilder

class RawProjectDataJsonRenderer implements ReportRenderer {
    static final String RAW_PROJECT_JSON_NAME = "raw-project-data.json"

    @Override
    void render(ProjectData data) {
        LicenseReportExtension config = data.project?.licenseReport
        File outputFile = new File(config.outputDir, RAW_PROJECT_JSON_NAME)
        outputFile.createNewFile()

        def project = data.project
        data.project = null

        def json = new JsonBuilder(data).toPrettyString()
        outputFile << json

        data.project = project
    }
}
