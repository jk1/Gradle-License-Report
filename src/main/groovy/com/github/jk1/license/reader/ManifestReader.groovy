package com.github.jk1.license.reader

import com.github.jk1.license.data.ManifestData
import com.github.jk1.license.task.DependencyLicenseReport
import com.google.common.io.Files
import org.gradle.api.artifacts.ResolvedArtifact

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest


class ManifestReader {

    ManifestData readManifestData(DependencyLicenseReport report, ResolvedArtifact artifact) {
        String fileExtension = Files.getFileExtension(artifact.file.name)?.toLowerCase()
        if (!fileExtension) {
            report.logger.debug("No file extension found for file: $artifact.file")
            return null
        }
        switch (fileExtension) {
            case "mf":
                report.logger.debug("Processing manifest file: $artifact.file")
                Manifest mf = new Manifest(artifact.file.newInputStream())
                return manifestToData(report, mf)
            case "jar":
            case "zip":
                report.logger.debug("Processing manifest from archive file: $artifact.file")
                Manifest mf = lookupManifest(report, artifact.file)
                if (mf) return manifestToData(report, mf)
                break
        }
        report.logger.debug("No manifest found for file extension: $fileExtension")
        return null
    }

    Manifest lookupManifest(DependencyLicenseReport report, File file) {
        try {
            return new JarFile(file).manifest
        } catch (Exception e) {
            report.logger.info("No manifest found for file: $file", e)
            return null
        }
    }

    ManifestData manifestToData(DependencyLicenseReport report, Manifest mf) {
        Attributes attr = mf.mainAttributes

        report.logger.debug("Manifest main attributes: " + attr.dump())

        ManifestData data = new ManifestData()
        data.name = attr.getValue('Bundle-Name') ?: attr.getValue('Implementation-Title') ?: attr.getValue('Bundle-SymbolicName')
        data.version = attr.getValue('Bundle-Version') ?: attr.getValue('Implementation-Version') ?: attr.getValue('Specification-Version')
        data.description = attr.getValue('Bundle-Description')
        data.license = attr.getValue('Bundle-License')
        data.vendor = attr.getValue('Bundle-Vendor') ?: attr.getValue('Implementation-Vendor')
        data.url = attr.getValue('Bundle-DocURL')

        report.logger.info("Returning manifest data: " + data.dump())
        return data
    }
}
