package com.github.jk1.license.render

import com.github.jk1.license.reader.LicenseFilesReader
import com.github.jk1.license.reader.ManifestReader
import com.github.jk1.license.reader.PomReader
import com.github.jk1.license.task.DependencyLicenseReport
import com.github.jk1.license.data.ManifestData
import com.github.jk1.license.data.PomData
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency

class SimpleHtmlRenderer implements ReportRenderer {

    private PomReader pomReader = new PomReader()
    private ManifestReader manifestReader = new ManifestReader()
    private LicenseFilesReader filesReader = new LicenseFilesReader()
    private File outputFile;

    void startProject(DependencyLicenseReport report) {
        Project project = report.getProject()
        outputFile = report.getOutputFile()
        outputFile.text = """
<html>
<head>
<title>Dependency License Report for $project.name</title>
<head>
<body>
<h1>Dependency License Report for $project.name $project.version</h1>
"""
    }

    void addConfiguration(DependencyLicenseReport report, Configuration configuration) {}

    void completeProject(DependencyLicenseReport report) {
        outputFile << """
<hr />
<p id="timestamp">This report was generated at <em>${new Date()}</em>.</p>
</body>
</html>
"""
        report.logger.debug("Wrote project footer into output file ($outputFile)");
    }

    void startConfiguration(DependencyLicenseReport report, Configuration configuration) {}

    void completeConfiguration(DependencyLicenseReport report, Configuration configuration) {}

    void addDependency(DependencyLicenseReport report, Configuration configuration, ResolvedDependency dependency) {
        if (!dependency.moduleArtifacts) {
            report.logger.info("Skipping $dependency -- no module artifacts found: ${dependency.dump()}")
            return
        }

        ResolvedConfiguration resolved = configuration.resolvedConfiguration
        Project project = report.project

        String name = dependency.name ?: dependency.toString()

        outputFile << "<p>"
        if (dependency.moduleGroup) outputFile << "<strong>Group:</strong> $dependency.moduleGroup "
        if (dependency.moduleName) outputFile << "<strong>Name:</strong> $dependency.moduleName "
        if (dependency.moduleVersion) outputFile << "<strong>Version:</strong> $dependency.moduleVersion "
        outputFile << "</p>"
        ResolvedArtifact artifact = dependency.moduleArtifacts.iterator().next()
        report.logger.info("Processing artifact: $artifact ($artifact.file)")
        ManifestData manifestData = manifestReader.readManifestData(report, artifact)
        if (!manifestData) {
            report.logger.info("No manifest data found in $artifact.file");
        } else {
            if (manifestData.url) {
                outputFile << "<p><strong>Project URL:</strong> <code><a href=\"$manifestData.url\">$manifestData.url</a></code></p>"
            }
            if (manifestData.license) {
                if (manifestData.license.startsWith("http")) {
                    outputFile << "<p><strong>License URL:</strong> <a href=\"$manifestData.license\">$manifestData.license</a></p>"
                } else if (filesReader.hasLicenseFile(report, artifact.file, manifestData.license)) {
                    String path = "${artifact.file.name}/${manifestData.license}.html"
                    File licenseFile = new File(report.outputDir, path)
                    filesReader.writeLicenseFile(report, artifact.file, manifestData.license, licenseFile)
                    outputFile << "<p><strong>Packaged License File:</strong> <a href=\"$path\">$manifestData.license</a></p>"
                } else {
                    outputFile << "<p><strong>License:</strong> $manifestData.license (Not packaged)</p>"
                }
            }
        }

        PomData pomData = pomReader.readPomData(report.getProject(), artifact)
        if (!pomData) {
            report.logger.info("No pom data found in $artifact.file")
        } else {
            if (pomData.projectUrl) {
                outputFile << "<p><strong>Project URL:</strong> <code><a href=\"$pomData.projectUrl\">$pomData.projectUrl</a></code></p>"
            }
            if (pomData.licenses) {
                pomData.licenses.each { PomData.License license ->
                    outputFile << "<h4>License: $license.name</h4>"
                    if (license.url) {
                        if (license.url.startsWith("http")) {
                            outputFile << "<p><strong>License URL:</strong> <a href=\"$license.url\">$license.url</a></p>"
                        } else if (filesReader.hasLicenseFile(report, artifact.file, license.url)) {
                            String path = "${artifact.file.name}/${license.url}"
                            File licenseFile = new File(report.outputDir, path)
                            filesReader.writeLicenseFile(report, artifact.file, license.url, licenseFile)
                            outputFile << "<p><strong>Packaged License File:</strong> <a href=\"$path\">$license.url</a></p>"
                        } else {
                            outputFile << "<p><strong>License:</strong> $license.url</p>"
                        }
                    }
                }
            }
        }

        if (!pomData && !manifestData) {
            outputFile << "<p><strong>No POM or Manifest File Found</strong></p>"
        }

        Collection<String> licenseFilePaths = filesReader.readLicenseFiles(report, artifact)
        if (licenseFilePaths) {
            outputFile << "<ul>"
            outputFile << licenseFilePaths.collect({ String path -> "<li><a href=\"$path\">$path</a></li>" }).join("")
            outputFile << "</ul>"
        }
        outputFile << "<hr />"
    }
}
