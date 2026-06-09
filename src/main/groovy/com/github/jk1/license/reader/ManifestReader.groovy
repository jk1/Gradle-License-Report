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

import com.github.jk1.license.License
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
                    data.licenses.each { License license ->
                        if (!license.name) return
                        def path = findLicenseFile(artifact.file, license.name)
                        if (path != null) {
                            data.hasPackagedLicense = true
                            String relativeUrl = "${artifact.file.name}/${license.name}.html"
                            data.licenses.remove(license)
                            data.licenses << new License(name: license.name, url: relativeUrl)
                            writeLicenseFile(artifact.file, path, new File(config.absoluteOutputDir, relativeUrl))
                        }
                    }
                    return data
                }
                break
        }
        LOGGER.debug("No manifest found for file extension: $fileExtension")
        return null
    }

    private Manifest lookupManifest(File artifactFile) {
        try (JarFile jarFile = new JarFile(artifactFile)) {
            return jarFile.manifest
        } catch (Exception e) {
            LOGGER.info("No manifest found for file: $artifactFile", e)
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
        data.vendor = attr.getValue('Bundle-Vendor') ?: attr.getValue('Implementation-Vendor')
        data.url = attr.getValue('Bundle-DocURL')
        data.licenses += bundleLicenses(attr.getValue('Bundle-License'))
        LOGGER.info("Returning manifest data: " + data.dump())
        return data
    }

    /**
     * Parses the OSGi `Bundle-License` header, which may contain multiple licenses.
     *
     *   Bundle-License ::= '<<EXTERNAL>>' |
     *                         ( license ( ',' license ) * )
     *   license        ::= license-identifier ( ';' license-attr ) *
     *   license-attr   ::= description | link
     *   description    ::= 'description' '=' string
     *   link           ::= 'link' '=' <url>
     */
    private List<License> bundleLicenses(String bundleLicenseHeader) {
        if (!bundleLicenseHeader) return []

        try {
            def licenses = OsgiHeader.parse(bundleLicenseHeader)
                    .collect { clauseToLicense(it) }
                    .findAll()

            return licenseDidntParseCleanly(bundleLicenseHeader, licenses)
                    ? [new License(name: bundleLicenseHeader)]
                    : licenses
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Bundle-License header: $bundleLicenseHeader", e)
            return []
        }
    }

    private static License clauseToLicense(OsgiHeader.Clause clause) {
        String name = clause.names.find()
        String link = clause.attributes.link
        String description = clause.attributes.description

        // only use description as a fallback name
        def license = new License(name: Files.maybeLicenseUrl(name) ? description : name)

        // Prefer url from any link attribute; else check the name
        if (Files.maybeLicenseUrl(link)) {
            license.url = link
        } else if (Files.maybeLicenseUrl(name)) {
            license.url = name
        }

        license.name == null && license.url == null ? null : license
    }

    /**
     * If the license header had a comma-space, but none of the licenses appear to represent URLs; conclude
     * that something is probably not strictly compliant with the spec, and a raw license name value is
     * probably present. This will fail to parse a header like `Apache-2.0, MIT` as two separate licenses,
     * so may need to be re-evaluated if this becomes more common. An example license that fails to parse
     * sensibly from its `Bundle-License` with strict parsing was `org.freemarker:freemarker:2.3.34`.
     */
    private static licenseDidntParseCleanly(String bundleLicenseHeader, List<License> licenses) {
        bundleLicenseHeader.contains(", ") && licenses.every { !it.url }
    }

    private String findLicenseFile(File artifactFile, String licenseFileName) {
        try (ZipFile file = new ZipFile(artifactFile, ZipFile.OPEN_READ)) {
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
        try (ZipFile file = new ZipFile(artifactFile, ZipFile.OPEN_READ)) {
            ZipEntry entry = file.getEntry(licenseFileName)
            destinationFile.parentFile.mkdirs()
            destinationFile.text = file.getInputStream(entry).text
        } catch (Exception e) {
            LOGGER.warn("Failed to write license file $licenseFileName from $artifactFile", e)
        }
    }
}
