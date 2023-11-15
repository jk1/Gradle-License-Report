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

import com.github.jk1.license.LicenseFileData
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.task.ReportTask
import com.github.jk1.license.util.Files
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class LicenseFilesReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private LicenseReportExtension config

    LicenseFilesReader(LicenseReportExtension config) {
        this.config = config
    }

    LicenseFileData read(ResolvedArtifact artifact) {
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
                "copying.lesser",
                "about"
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
            File file = new File(config.absoluteOutputDir, path)
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

        def text = new File(config.absoluteOutputDir, file).text
        if (text.contains('Eclipse\n\t\tPublic License Version 2.0')) {
            moduleLicense = 'Eclipse Public License Version 2.0'
            moduleLicenseUrl = 'http://www.eclipse.org/legal/epl-2.0'
        }
        if (text.contains('Apache License, Version 2.0')) {
            moduleLicense = 'Apache License, Version 2.0'
            moduleLicenseUrl = 'https://www.apache.org/licenses/LICENSE-2.0'
        }
        if (text.contains('Apache Software License, Version 1.1')) {
            moduleLicense = 'Apache Software License, Version 1.1'
            moduleLicenseUrl = 'https://www.apache.org/licenses/LICENSE-1.1'
        }
        if (text.contains('CDDL')) {
            moduleLicense = 'COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0'
            moduleLicenseUrl = 'https://opensource.org/licenses/CDDL-1.0'
        }
        if (text.contains('Eclipse Public License - v 2.0')) {
            moduleLicense = 'Eclipse Public License - v 2.0'
            moduleLicenseUrl = 'https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt'
        }
        new LicenseFileDetails(file: file, license: moduleLicense, licenseUrl: moduleLicenseUrl)
    }
}
