/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license.reader

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.task.ReportTask
import com.github.jk1.license.util.Files
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class ManifestReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private LicenseReportExtension config

    ManifestReader(LicenseReportExtension config) {
        this.config = config
    }

    ManifestData readManifestData(ResolvedArtifact artifact) {
        String fileExtension = Files.getExtension(artifact.file.name)?.toLowerCase()
        if (!fileExtension) {
            LOGGER.debug("No file extension found for file: $artifact.file")
            return null
        }
        switch (fileExtension) {
            case "mf":
                LOGGER.debug("Processing manifest file: $artifact.file")
                Manifest mf = new Manifest(artifact.file.newInputStream())
                return manifestToData(mf)
            case "jar":
            case "zip":
                LOGGER.debug("Processing manifest from archive file: $artifact.file")
                Manifest mf = lookupManifest(artifact.file)
                if (mf) {
                    ManifestData data = manifestToData(mf)
                    def path = findLicenseFile(artifact.file, data.license)
                    if (path != null){
                        data.hasPackagedLicense = true
                        File dest = new File(config.absoluteOutputDir, "${artifact.file.name}/${data.license}.html")
                        data.url="${artifact.file.name}/${data.license}.html"
                        writeLicenseFile(artifact.file, path, dest)
                    }
                    return data
                }
                break
        }
        LOGGER.debug("No manifest found for file extension: $fileExtension")
        return null
    }

    private Manifest lookupManifest(File file) {
        try {
            return new JarFile(file).manifest
        } catch (Exception e) {
            LOGGER.info("No manifest found for file: $file", e)
            return null
        }
    }

    private ManifestData manifestToData(Manifest mf) {
        Attributes attr = mf.mainAttributes

        LOGGER.debug("Manifest main attributes: " + attr.dump())

        ManifestData data = new ManifestData()
        data.name = attr.getValue('Bundle-Name') ?: attr.getValue('Implementation-Title') ?: attr.getValue('Bundle-SymbolicName')
        data.version = attr.getValue('Bundle-Version') ?: attr.getValue('Implementation-Version') ?: attr.getValue('Specification-Version')
        data.description = attr.getValue('Bundle-Description')
        String bundleLicense = attr.getValue('Bundle-License')
        data.vendor = attr.getValue('Bundle-Vendor') ?: attr.getValue('Implementation-Vendor')
        data.url = attr.getValue('Bundle-DocURL')
        if (Files.maybeLicenseUrl(bundleLicense)) {
			def allLicenseParts = bundleLicense.split(';')
            data.licenseUrl = allLicenseParts[0]
			allLicenseParts.each {
				def additionalParameter = it.split('=')

				if (additionalParameter[0] == 'description')
					data.license = additionalParameter[1]
			}
        }
        else {
            data.license = bundleLicense
        }
        LOGGER.info("Returning manifest data: " + data.dump())
        return data
    }

    private String findLicenseFile(File artifactFile, String licenseFileName) {
        try {
            ZipFile file = new ZipFile(artifactFile, ZipFile.OPEN_READ)
            return [
                    "/$licenseFileName",
                    "/META-INF/$licenseFileName",
                    licenseFileName,
                    "META-INF/$licenseFileName"
            ].find {
                // licenseFileName may be null
                it != null && file.getEntry(it)
            }
        } catch (Exception e) {
            LOGGER.warn("No license file $licenseFileName found in $artifactFile", e)
            return null
        }
    }

    private void writeLicenseFile(File artifactFile, String licenseFileName, File destinationFile) {
        try {
            ZipFile file = new ZipFile(artifactFile, ZipFile.OPEN_READ)
            ZipEntry entry = file.getEntry(licenseFileName)
            destinationFile.parentFile.mkdirs()
            destinationFile.text = file.getInputStream(entry).text
        } catch (Exception e) {
            LOGGER.warn("Failed to write license file $licenseFileName from $artifactFile", e)
        }
    }
}
