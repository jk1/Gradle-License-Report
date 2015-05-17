package com.github.jk1.license.reader

import com.github.jk1.license.LicenseFileData
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.util.Files
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class LicenseFilesReader {

    private Logger LOGGER = Logging.getLogger(Task.class);
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
                Collection<String> files = readLicenseFiles(artifact, new ZipFile(artifact.file, ZipFile.OPEN_READ));
                return files.isEmpty() ? null : new LicenseFileData(files)
                break;
            default:
                return null;
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
            return "";
        }
        int pos = str.lastIndexOf(separator);
        if (pos == -1 || pos == str.length() - separator.length()) {
            return "";
        }
        return str.substring(pos + separator.length());
    }

}
