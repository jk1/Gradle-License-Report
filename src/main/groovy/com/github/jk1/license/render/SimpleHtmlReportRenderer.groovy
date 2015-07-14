package com.github.jk1.license.render

import com.github.jk1.license.License
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData
import org.gradle.api.Project

class SimpleHtmlReportRenderer implements ReportRenderer {

    private Project project
    private LicenseReportExtension config
    private File output
    private int counter

    void render(ProjectData data) {
        project = data.project
        config = project.licenseReport
        output = new File(config.outputDir, 'index.html')
        output.text = """
<html>
<head>
<title>Dependency License Report for $project.name</title>
<head>
<body>
<h1>Dependency License Report for $project.name ${if (!'unspecified'.equals(project.version)) project.version else ''}</h1>
"""
        printDependencies(data)
        output << """
<hr />
<p id="timestamp">This report was generated at <em>${new Date()}</em>.</p>
</body>
</html>
"""
    }

    private void printDependencies(ProjectData data) {
        data.configurations.collect { it.dependencies }.flatten().sort().each {
            printDependency(it)
        }
    }

    private String printDependency(ModuleData data) {
        output << "<hr />"
        output << "<p><strong> ${++counter}.</strong> "
        if (data.group) output << "<strong>Group:</strong> $data.group "
        if (data.name) output << "<strong>Name:</strong> $data.name "
        if (data.version) output << "<strong>Version:</strong> $data.version "
        output << "</p>"

        if (data.poms.isEmpty() && data.manifests.isEmpty()) {
            output << "<p><strong>No license information found</strong></p>"
            return
        }

        if (!data.manifests.isEmpty()) {
            ManifestData manifest = data.manifests.first()
            if (manifest.url) {
                output << "<p><strong>Project URL:</strong> <code><a href=\"$manifest.url\">$manifest.url</a></code></p>"
            }
            if (manifest.license) {
                if (manifest.license.startsWith("http")) {
                    output << "<p><strong>Manifest license URL:</strong> <a href=\"$manifest.license\">$manifest.license</a></p>"
                } else if (manifest.hasPackagedLicense) {
                    output << "<p><strong>Packaged License File:</strong> <a href=\"$path\">$manifest.license</a></p>"
                } else {
                    output << "<p><strong>Manifest License:</strong> $manifest.license (Not packaged)</p>"
                }
            }
        }

        if (!data.poms.isEmpty()) {
            PomData pomData = data.poms.first()
            if (pomData.projectUrl) {
                output << "<p><strong>Project URL:</strong> <code><a href=\"$pomData.projectUrl\">$pomData.projectUrl</a></code></p>"
            }
            if (pomData.licenses) {
                pomData.licenses.each { License license ->
                    output << "<p><strong>POM License: $license.name</strong>"
                    if (license.url) {
                        if (license.url.startsWith("http")) {
                            output << " - <a href=\"$license.url\">$license.url</a>"
                        } else if (false) {//filesReader.hasLicenseFile(report, artifact.file, license.url)) {
                            //String path = "${artifact.file.name}/${license.url}"
                            //File licenseFile = new File(report.outputDir, path)
                            //filesReader.writeLicenseFile(report, artifact.file, license.url, licenseFile)
                            output << "<p><strong>Packaged License File:</strong> <a href=\"$path\">$license.url</a></p>"
                        } else {
                            output << "<p><strong>License:</strong> $license.url</p>"
                        }
                    }
                }
            }
        }
        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().files.isEmpty()) {
            output << '<p><strong>Embedded license files:</strong> '
            output << data.licenseFiles.first().files.collect({ "<a href=\"$it\">$it</a> " }).join('')
            output << '</p>'
        }
    }
}
