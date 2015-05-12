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

class DetailedHtmlRenderer implements ReportRenderer {

    private PomReader pomReader = new PomReader()
    private ManifestReader manifestReader = new ManifestReader()
    private LicenseFilesReader filesReader = new LicenseFilesReader()

    void startProject(DependencyLicenseReport report) {
        Project project = report.getProject()
        File outputFile = report.getOutputFile()
        outputFile.text = """
<html>
<head>
<title>Dependency License Report for $project.name</title>
<head>
<body>
<h1>Dependency License Report for $project.name</h1>
"""

        if (project.description) {
            outputFile << "<p id=\"projectDescription\">$project.description</p>"
        } else {
            report.logger.debug("No description for project; skipping");
        }

        outputFile << """
<p id="reportIntroduction">The dependencies in this project are tracked by
the Gradle build system,
which uses the Maven dependency resolution system. This is the same system used
by Ivy. Dependencies are collected into <em>configurations</em>, which are
the different contexts where code is executed. Two of the possible configurations
are <em>compile</em> and <em>runtime</em>, which are configuration used when
the code is being compiled (<em>compile</em>) and the configuration used to
determine what libraries are shipped with the packaged application
(<em>runtime</em>). Other configurations, if any, are described below.</p>

<h2>Reported Configurations</h2>
"""
    }

    void addConfiguration(DependencyLicenseReport report, Configuration configuration) {
        File outputFile = report.getOutputFile()
        outputFile << """
<h3><a name="$configuration.name">$configuration.name</a></h3>
<p>$configuration.description</p>
<p class="configurationLink"><strong>Configuration Report:</strong>
  <a href="${configuration.name}.html">${configuration.name}.html</a>
</p>
"""
        report.logger.debug("Wrote link into output file ($outputFile) to configuration: $configuration");
    }

    void completeProject(DependencyLicenseReport report) {
        File outputFile = report.getOutputFile()
        outputFile << """
<hr />
<p id="timestamp">This report was generated at <em>${new Date()}</em>.</p>
</body>
</html>
"""
        report.logger.debug("Wrote project footer into output file ($outputFile)");
    }

    void startConfiguration(DependencyLicenseReport report, Configuration configuration) {
        Project project = report.getProject()
        File outputFile = new File(report.getOutputDir(), "${configuration.name}.html");
        outputFile.text = """
<html>
<head>
<title>Dependency License Report for $project.name - $configuration.name configuration</title>
</head>
<body>
<h1>$configuration.name</h1>
<p>$configuration.description</p>
<h2>Dependencies</h2>
"""
    }

    void completeConfiguration(DependencyLicenseReport report, Configuration configuration) {
        Project project = report.getProject()
        File outputFile = new File(report.getOutputDir(), "${configuration.name}.html");
        outputFile << "</body></html>"
    }

    void addDependency(DependencyLicenseReport report, Configuration configuration, ResolvedDependency dependency) {
        if (!dependency.moduleArtifacts) {
            report.logger.info("Skipping $dependency -- no module artifacts found: ${dependency.dump()}")
            return
        }

        ResolvedConfiguration resolved = configuration.resolvedConfiguration
        Project project = report.project
        File outputFile = new File(report.outputDir, "${configuration.name}.html");

        String name = dependency.name ?: dependency.toString()

        outputFile << "<h3>$name</h3>"

        if (dependency.moduleName) outputFile << "<p><strong>Module Name:</strong> $dependency.moduleName</p>"
        if (dependency.moduleGroup) outputFile << "<p><strong>Module Group:</strong> $dependency.moduleGroup</p>"
        if (dependency.moduleVersion) outputFile << "<p><strong>Module Version:</strong> $dependency.moduleVersion</p>"

        dependency.moduleArtifacts.each { ResolvedArtifact artifact ->
            report.logger.info("Processing artifact: $artifact ($artifact.file)")
            ManifestData manifestData = manifestReader.readManifestData(report, artifact)
            if (!manifestData) {
                report.logger.info("No manifest data found in $artifact.file");
            } else {
                outputFile << "<h3>Manifest Metadata - $artifact.file.name</h3>"
                if (manifestData.name) outputFile << "<p><strong>Name:</strong> $manifestData.name</p>"
                if (manifestData.description) outputFile << "<p><strong>Description:</strong> $manifestData.description</p>"
                if (manifestData.url) {
                    outputFile << "<p><strong>Project URL:</strong> <code><a href=\"$manifestData.url\">$manifestData.url</a></code></p>"
                }
                if (manifestData.vendor) outputFile << "<p><strong>Vendor:</strong> $manifestData.vendor</p>"
                if (manifestData.version) outputFile << "<p><strong>Version:</strong> $manifestData.version</p>"
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
                outputFile << "<h3>Maven Metadata - $artifact.file.name</h3>"
                if (pomData.name) outputFile << "<p><strong>Name:</strong> $pomData.name</p>"
                if (pomData.description) outputFile << "<p><strong>Description:</strong> $pomData.description</p>"
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
                        if (license.distribution) {
                            outputFile << "<p><strong>Distribution:</strong> $license.distribution</p>"
                        }
                        if (license.comments) {
                            outputFile << "<p><strong>Comment:</strong> $license.comments</p>"
                        }
                    }
                }
            }

            if (!pomData && !manifestData) {
                outputFile << "<p><strong>No POM or Manifest File Found</strong></p>"
            }

            Collection<String> licenseFilePaths = filesReader.readLicenseFiles(report, artifact)
            if (licenseFilePaths) {
                outputFile << "<h3>License Files - $artifact.file.name</h3>"

                outputFile << "<ul>"
                outputFile << licenseFilePaths.collect({ String path -> "<li><a href=\"$path\">$path</a></li>" }).join("")
                outputFile << "</ul>"
            }
        }
        outputFile << "<hr />"
    }
}
