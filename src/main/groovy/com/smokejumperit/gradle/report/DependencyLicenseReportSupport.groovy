package com.smokejumperit.gradle.report

import groovy.transform.*
import groovy.util.slurpersupport.GPathResult

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency

import com.google.common.io.Files


class DependencyLicenseReportSupport {

	static void startProject(DependencyLicenseReport report) {
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

		if(project.description) {
			outputFile.text << "<p id=\"projectDescription\">$project.description</p>"
		}

		outputFile.text << """
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

	static void linkProjectToConfiguration(DependencyLicenseReport report, Configuration configuration) {
		File outputFile = report.getOutputFile()
		outputFile.text << """
<h3><a name="$configuration.name">$configuration.name</a></h3>
<p>$configuration.description</p>
<p class="configurationLink"><strong>Configuration Report:</strong>
  <a href="${configuration.name}.html">${configuration.name}.html</a>
</p>
"""
	}

	static void completeProject(DependencyLicenseReport report) {
		File outputFile = report.getOutputFile()
		outputFile.text << """
<hr />
<p id="timestamp">This report was generated at <em>${new Date()}</em>.</p>
</body>
</html>
"""
	}

	static void startConfiguration(DependencyLicenseReport report, Configuration configuration) {
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

	static void completeConfiguration(DependencyLicenseReport report, Configuration configuration) {
		Project project = report.getProject()
		File outputFile = new File(report.getOutputDir(), "${configuration.name}.html");
		outputFile << "</body></html>"
	}

	static void reportDependency(DependencyLicenseReport report, Configuration configuration, ResolvedDependency dependency) {
		ResolvedConfiguration resolved = configuration.resolvedConfiguration
		Project project = report.project
		File outputFile = new File(report.outputDir, "${configuration.name}.html");

		String name = dependency.name ?: dependency.toString()

		outputFile << "<h3>$name</h3>"

		if(dependency.moduleName) outputFile << "<p><strong>Module Name:</strong> $dependency.moduleName</p>"
		if(dependency.moduleGroup) outputFile << "<p><strong>Module Group:</strong> $dependency.moduleGroup</p>"
		if(dependency.moduleVersion) outputFile << "<p><strong>Module Version:</strong> $dependency.moduleVersion</p>"

		dependency.moduleArtifacts.each  { ResolvedArtifact artifact ->
			ManifestData manifestData = readManifestData(artifact)
			if(manifestData) {
				outputFile << "<h4>Manifest Metadata - $artifact.file.name</h4>"
				if(manifestData.name) outputFile << "<p><strong>Name:</strong> $manifestData.name</p>"
				if(manifestData.description) outputFile << "<p><strong>Description:</strong> $manifestData.description</p>"
				if(manifestData.url) {
					outputFile << "<p><strong>Project URL:</strong> <code><a href=\"$manifestData.url\">$manifestData.url</a></code></p>"
				}
				if(manifestData.vendor) outputFile << "<p><strong>Vendor:</strong> $manifestData.vendor</p>"
				if(manifestData.version) outputFile << "<p><strong>Version:</strong> $manifestData.version</p>"
				if(manifestData.license) {
					if(manifestData.license.startsWith("http")) {
						outputFile << "<p><strong>License URL:</strong> <a href=\"$manifestData.license\">$manifestData.license</a></p>"
					} else {
						outputFile << "<p><strong>License:</strong> $manifestData.license</p>"
					}
				}
			}

			PomData pomData = readPomData(artifact)
			if(pomData) {
				outputFile << "<h4>Maven Metadata - $artifact.file.name</h4>"
				if(pomData.name)  outputFile<< "<p><strong>Name:</strong> $pomData.name</p>"
				if(pomData.description) outputFile << "<p><strong>Description:</strong> $pomData.description</p>"
				if(pomData.projectUrl) {
					outputFile << "<p><strong>Project URL:</strong> <code><a href=\"$pomData.projectUrl\">$pomData.projectUrl</a></code></p>"
				}
				if(pomData.licenses) {
					pomData.licenses.each { PomData.License license ->
						outputFile << "<h5>License: $license.name</h5>"
						if(license.url) {
							outputFile << "<p><strong>License URL:</strong> <code><a href=\"$license.url\">$license.url</a></code></p>"
						}
						if(license.distribution) {
							outputFile << "<p><strong>Distribution:</strong> $license.distribution</p>"
						}
						if(license.comments) {
							outputFile << "<p><strong>Comment:</strong> $license.comments</p>"
						}
					}
				}
				if(pomData.developers) {
					outputFile << "<h5>Developers</h5>"
					outputFile << "<ul>"
					pomData.developers.each { PomData.Developer developer ->
						outputFile << "<li>"
						if(developer.email) {
							outputFile << "<a href=\"mailto:$developer.email\">$developer.name</a> "
						} else {
							outputFile << "$developer.name "
						}
						if(developer.organization) outputFile << "($developer.organization) "
						if(developer.roles) {
							outputFile << "&mdash; ${developer.roles.join(', ')}"
						}
						outputFile << "</li>"
					}
					outputFile << "</ul>"
				}
			}
		}
		outputFile << "<hr />"
	}

	static ManifestData readManifestData(ResolvedArtifact artifact) {
		String fileExtension = Files.getFileExtension(artifact.file.name)?.toLowerCase()
		if(!fileExtension) return null
		switch(fileExtension) {
			case "mf":
				Manifest mf = new Manifest(artifact.file.newInputStream())
				return manifestToData(mf)
			case "jar":
			case "zip":
				Manifest mf = lookupManifest(artifact.file)
				if(mf) return manifestToData(mf)
				break
		}
		return null
	}

	static Manifest lookupManifest(File file) {
		try {
			return new JarFile(file).manifest
		} catch(Exception e) {
			return null
		}
	}

	static ManifestData manifestToData(Manifest mf) {
		Attributes attr = mf.mainAttributes
		ManifestData data = new ManifestData()
		data.name = attr.getValue('Bundle-Name') ?: attr.getValue('Implementation-Title') ?: attr.getValue('Bundle-SymbolicName')
		data.version = attr.getValue('Bundle-Version') ?: attr.getValue('Implementation-Version') ?: attr.getValue('Specification-Version')
		data.description = attr.getValue('Bundle-Description')
		data.license = attr.getValue('Bundle-License')
		data.vendor = attr.getValue('Implementation-Vendor')
		data.url = attr.getValue('Bundle-DocURL')
		return data
	}

	static PomData readPomData(ResolvedArtifact artifact) {
		GPathResult pomContent = slurpPom(artifact.file)
		if(!pomContent) return null
		if(pomContent) {
			return readPomFile(pomContent)
		}
	}

	static GPathResult slurpPom(File toSlurp) {
		if(toSlurp.name == "pom.xml") return slurpPomItself(toSlurp)

		String fileSuffix = Files.getFileExtension(toSlurp.name)?.toLowerCase()
		if(!fileSuffix) return null
		switch(fileSuffix) {
			case "zip":
			case "jar":
				return slurpPomFromZip(toSlurp)
		}

		return null
	}

	static GPathResult slurpPomFromZip(File archiveToSearch) {
		ZipFile archive = new ZipFile(archiveToSearch, ZipFile.OPEN_READ)
		ZipEntry pomEntry = archive.entries().toList().find { ZipEntry entry ->
			entry.name.endsWith("pom.xml")
		}
		if(!pomEntry) return null
		return new XmlSlurper().parse(archive.getInputStream(pomEntry))
	}

	static GPathResult slurpPomItself(File toSlurp) {
		return new XmlSlurper().parse(toSlurp)
	}

	static PomData readPomFile(GPathResult pomContent) {
		if(!pomContent) return null

		PomData pomData = new PomData()
		
		pomData.name = pomContent.name?.text()
		pomData.description = pomContent.description?.text()
		pomData.projectUrl = pomContent.url?.text()

		pomContent.developers?.developer?.each { GPathResult developer ->
			pomData.developers << new PomData.Developer(
					name: developer.name?.text(),
					email: developer.email?.text(),
					organization: developer.organization?.text(),
					roles: developer.roles?.role?.collect { it.text() }
					)
		}

		pomContent.licenses?.license?.each { GPathResult license ->
			pomData.licenses << new PomData.License(
					name: license.name?.text(),
					url: license.url?.text(),
					distribution: license.distribution?.text(),
					comments: license.comments?.text()
					)
		}

		return pomData
	}
}
