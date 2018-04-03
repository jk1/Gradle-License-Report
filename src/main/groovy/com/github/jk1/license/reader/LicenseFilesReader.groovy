package com.github.jk1.license.reader

import com.github.jk1.license.LicenseFileData
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ReportTask
import com.github.jk1.license.util.Files
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class LicenseFilesReader {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)
    private LicenseReportExtension config

    LicenseFileData read(Project project, ResolvedArtifact artifact) {
        config = project.licenseReport
        String fileExtension = Files.getExtension(artifact.file.name)?.toLowerCase()
        if (!fileExtension) {
            LOGGER.debug("No file extension found for file: $artifact.file")
            return null
        }
        switch (fileExtension) {
            case "zip":
            case "jar":
                Collection<String> files = readLicenseFiles(artifact, new ZipFile(artifact.file, ZipFile.OPEN_READ))
                if (files.isEmpty()) return null

                def data = new LicenseFileData()
                files.forEach {
                    data.files << it
                    data.fileDetails << createFileDetails(it)
                }
                return data
                break
            default:
                return null
        }
    }

    private Collection<String> readLicenseFiles(ResolvedArtifact artifact, ZipFile zipFile) {
        Set<String> licenseFileBaseNames = [
                "license",
                "readme",
                "notice",
                "copying",
                "copying.lesser"
        ]
        Set<ZipEntry> entryNames = zipFile.entries().toList().findAll { ZipEntry entry ->
            String name = entry.getName()
            String baseName = substringAfterLast(name, "/") ?: name
            String fileExtension = Files.getExtension(baseName)
            if (fileExtension?.equalsIgnoreCase("class")) return null // Skip class files
            if (fileExtension) baseName -= ".$fileExtension"
            return licenseFileBaseNames.find { it.equalsIgnoreCase(baseName) }
        }
        if (!entryNames) return Collections.emptyList()
        return entryNames.collect { ZipEntry entry ->
            String entryName = entry.name
            if (!entryName.startsWith("/")) entryName = "/$entryName"
            String path = "${artifact.file.name}${entryName}"
            File file = new File(config.outputDir, path)
            file.parentFile.mkdirs()
            file.text = zipFile.getInputStream(entry).text
            return path
        }
    }

    private String substringAfterLast(String str, String separator) {
        if (!str || !separator) {
            return ""
        }
        int pos = str.lastIndexOf(separator)
        if (pos == -1 || pos == str.length() - separator.length()) {
            return ""
        }
        return str.substring(pos + separator.length())
    }

    private LicenseFileDetails createFileDetails(String file) {
        String moduleLicense = null
        String moduleLicenseUrl = null

        def text = new File(config.outputDir, file).text
        if (text.contains('Apache License, Version 2.0')) {
            moduleLicense = 'Apache License, Version 2.0'
            moduleLicenseUrl = 'http://www.apache.org/licenses/LICENSE-2.0'
        }
        if (text.contains('Apache Software License, Version 1.1')) {
            moduleLicense = 'Apache Software License, Version 1.1'
            moduleLicenseUrl = 'http://www.apache.org/licenses/LICENSE-1.1'
        }
        if (text.contains('CDDL')) {
            moduleLicense = 'COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0'
            moduleLicenseUrl = 'http://opensource.org/licenses/CDDL-1.0'
        }

        new LicenseFileDetails(file: file, license: moduleLicense, licenseUrl: moduleLicenseUrl)
    }
}
