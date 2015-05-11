package com.github.jk1.license.reader

import com.github.jk1.license.task.DependencyLicenseReport
import com.google.common.io.Files
import org.apache.commons.lang3.StringUtils
import org.gradle.api.artifacts.ResolvedArtifact

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class LicenseFilesReader {

    Collection<String> readLicenseFiles(DependencyLicenseReport report, ResolvedArtifact artifact) {
        String fileExtension = Files.getFileExtension(artifact.file.name)?.toLowerCase()
        if (!fileExtension) {
            report.logger.debug("No file extension found for file: $artifact.file")
            return null
        }
        switch (fileExtension) {
            case "zip":
            case "jar":
                return readLicenseFiles(report, artifact, new ZipFile(artifact.file, ZipFile.OPEN_READ))
                break;
            default:
                return null;
        }
    }

    Collection<String> readLicenseFiles(DependencyLicenseReport report, ResolvedArtifact artifact, ZipFile zipFile) {
        Set<String> licenseFileBaseNames = [
                "license",
                "readme",
                "notice",
                "copying",
                "copying.lesser"
        ]
        Set<ZipEntry> entryNames = zipFile.entries().toList().findAll { ZipEntry entry ->
            String name = entry.getName()
            String baseName = StringUtils.substringAfterLast(name, "/") ?: name
            String fileExtension = Files.getFileExtension(baseName)
            if (fileExtension?.equalsIgnoreCase("class")) return null // Skip class files
            if (fileExtension) baseName -= ".$fileExtension"
            return licenseFileBaseNames.find { it.equalsIgnoreCase(baseName) }
        }
        if (!entryNames) return null
        return entryNames.collect { ZipEntry entry ->
            String entryName = entry.name
            if (!entryName.startsWith("/")) entryName = "/$entryName"
            String path = "${artifact.file.name}${entryName}"
            File file = new File(report.outputDir, path)
            file.parentFile.mkdirs()
            file.text = zipFile.getInputStream(entry).text
            return path
        }
    }

    String hasLicenseFile(DependencyLicenseReport report, File artifactFile, String licenseFileName) {
        try {
            ZipFile file = new ZipFile(artifactFile, ZipFile.OPEN_READ)
            return [
                    "/$licenseFileName",
                    "/META-INF/$licenseFileName",
                    licenseFileName,
                    "META-INF/$licenseFileName"
            ].find { file.getEntry(it) }
        } catch (Exception e) {
            report.logger.info("No license file $licenseFileName found in $artifactFile", e)
            return false
        }
    }

    void writeLicenseFile(DependencyLicenseReport report, File artifactFile, String licenseFileName, File destinationFile) {
        try {
            String entryName = hasLicenseFile(report, artifactFile, licenseFileName) ?: licenseFileName
            ZipFile file = new ZipFile(artifactFile, ZipFile.OPEN_READ)
            ZipEntry entry = file.getEntry(entryName)
            destinationFile.parentFile.mkdirs()
            destinationFile.text = file.getInputStream(entry).text
        } catch (Exception e) {
            report.logger.warn("Failed to write license file $licenseFileName from $artifactFile", e)
        }
    }
}
