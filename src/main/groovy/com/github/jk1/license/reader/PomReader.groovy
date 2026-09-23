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
import com.github.jk1.license.LicenseReportSettings
import com.github.jk1.license.PomData
import com.github.jk1.license.PomDeveloper
import com.github.jk1.license.PomOrganization
import com.github.jk1.license.task.ReportTask
import com.github.jk1.license.util.Files
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.xml.sax.SAXException

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class PomReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private LicenseReportSettings config
    private ResolvedPoms poms

    PomReader(LicenseReportSettings config, ResolvedPoms poms) {
        this.config = config
        this.poms = poms
    }

    /** Reads the POM data of a module from its artifact, falling back to its resolved POM. */
    PomData readPomData(CollectedModule module, File artifact) {
        GPathResult pomContent = findAndSlurpPom(artifact, module)
        boolean pomHasLicense = true

        if (pomContent) {
            pomHasLicense = hasLicense(pomContent)
        }

        if (!pomContent || !pomHasLicense) {
            pomContent = fetchRemoteArtifactPom(module) ?: pomContent
        }

        if (!pomContent) {
            LOGGER.info("No POM content found for: $artifact")
            return null
        } else {
            return readPomFile(pomContent)
        }
    }

    /** Reads the POM data from a POM file. */
    PomData readPomData(File pom) {
        GPathResult pomContent = findAndSlurpPom(pom, null)
        return pomContent ? readPomFile(pomContent) : null
    }

    private GPathResult findAndSlurpPom(File toSlurp, CollectedModule module) {
        if (toSlurp.name == "pom.xml") {
            LOGGER.debug("Slurping pom from pom.xml file: $toSlurp")
            return slurpPomItself(toSlurp)
        }

        String fileSuffix = Files.getExtension(toSlurp.name)?.toLowerCase()
        if (!fileSuffix) {
            LOGGER.debug("No file suffix on potential pom-containing file: $toSlurp")
            return null
        }
        switch (fileSuffix) {
            case "pom":
                LOGGER.debug("Slurping pom from *.pom file: $toSlurp")
                return slurpPomItself(toSlurp)
            case ["zip", "jar"]:
                LOGGER.debug("Processing pom from archive: $toSlurp")
                return slurpBestMatchPomFromZip(toSlurp, module)
        }

        LOGGER.debug("No idea how to process a pom from: $toSlurp")
        return null
    }

    private GPathResult slurpBestMatchPomFromZip(File archiveToSearch, CollectedModule module) {
        try (ZipFile archive = new ZipFile(archiveToSearch, ZipFile.OPEN_READ)) {
            List<ZipEntry> pomEntries = archive.entries().toList().<ZipEntry> findAll { ZipEntry entry ->
                entry.name.endsWith("pom.xml") || entry.name.endsWith(".pom")
            }
            LOGGER.debug("Searching for POM file in $archiveToSearch -- found ${pomEntries?.size()}")
            if (!pomEntries) return null
            try {
                if (1 == pomEntries.size()) {
                    LOGGER.debug("Only one POM file was found in $archiveToSearch")
                    return createParser().parse(archive.getInputStream(pomEntries.first()))
                }

                for (final ZipEntry zipEntry in pomEntries) {
                    final GPathResult pom = createParser().parse(archive.getInputStream(zipEntry))

                    if (areModuleAndPomGroupAndArtifactIdEqual(module, pom)) {
                        LOGGER.debug("POM file in $archiveToSearch matched the artifact.")
                        return pom
                    } else {
                        LOGGER.debug("POM file in $archiveToSearch does not match the artifact, trying another one.")
                    }
                }
            } catch (SAXException e) {
                LOGGER.warn("Error parsing $pomEntries.name in $archiveToSearch", e)
                return null
            } catch (IOException e) {
                LOGGER.warn("Error reading $pomEntries.name in $archiveToSearch", e)
                return null
            }
        }

        return null
    }

    private GPathResult fetchRemoteArtifactPom(CollectedModule module) {
        Collection<File> pomFiles = poms.find(module.group, module.name, module.version)

        return pomFiles.collect {
            try {
                findAndSlurpPom(it, module)
            } catch (Exception e) {
                LOGGER.warn("Error slurping pom from $it", e)
                null
            }
        }.find {
            it != null
        }
    }

    private PomData readPomFile(GPathResult pomContent) {
        List<GPathResult> children = collectChildGPaths(pomContent)
        return createPomData(pomContent, children)
    }

    private List<GPathResult> collectChildGPaths(GPathResult rootPomGPath) {
        List<GPathResult> results = []

        LOGGER.debug("POM content children: ${rootPomGPath.children()*.name() as Set}")
        if (rootPomGPath.parent.children().isEmpty()) return []

        LOGGER.debug("Processing parent POM: ${rootPomGPath.parent.children()*.name()}")
        GPathResult parentContent = rootPomGPath.parent

        String groupId = parentContent.groupId.text().trim()
        String artifactId = parentContent.artifactId.text().trim()
        String version = parentContent.version.text().trim()

        Collection<File> parentPoms = poms.find(groupId, artifactId, version)

        if (parentPoms) {
            parentPoms.each { File parentPom ->
                LOGGER.debug("Processing parent POM file: $parentPom")
                GPathResult childPomGPath = slurpPomItself(parentPom)

                if (childPomGPath) {
                    results += childPomGPath
                    results += collectChildGPaths(childPomGPath)
                }
            }
        }
        return results
    }

    private PomData createPomData(GPathResult rootPom, List<GPathResult> childPoms) {
        List<GPathResult> allPoms = [rootPom] + childPoms

        PomData pomData = new PomData()

        pomData.name = rootPom.name?.text()
        pomData.description = rootPom.description?.text()
        pomData.projectUrl = rootPom.url?.text()
        pomData.inceptionYear = rootPom.inceptionYear?.text()

        def developers = rootPom.developers?.developer?.collect { GPathResult developer ->
            new PomDeveloper(
                name: developer.name?.text(),
                email: developer.email?.text(),
                url: developer.url?.text()
            )
        }
        if (developers) pomData.developers.addAll(developers)

        allPoms.reverse().each { pom ->
            def organizationName = pom.organization?.name?.text()
            def organizationUrl = pom.organization?.url?.text()
            if (organizationName || organizationUrl) {
                pomData.organization = new PomOrganization(name: organizationName, url: organizationUrl)
            }
        }

        LOGGER.debug("POM license : ${rootPom.licenses.children()*.name() as Set}")

        // How to interpret parent poms is a question best left up to the user
        // https://github.com/jk1/Gradle-License-Report/issues/264
        def licensePoms = [rootPom]
        if (config.unionParentPomLicenses) {
            licensePoms = allPoms
        }
        licensePoms.each { pom ->
            pom.licenses?.license?.each { GPathResult license ->
                LOGGER.debug("Processing license: ${license.name.text()}")
                pomData.licenses << new License(
                    name: license.name?.text(),
                    url: license.url?.text()
                )
            }
        }
        // If we didn't find a license in the root pom, then parent pom always applies (if it has one)
        if ( !pomData.licenses ) {
            childPoms.each { pom ->
                pom.licenses?.license?.each { GPathResult license ->
                    LOGGER.debug("Processing license: ${license.name.text()}")
                    pomData.licenses << new License(
                            name: license.name?.text(),
                            url: license.url?.text()
                    )
                }
            }
        }

        LOGGER.info("Returning pom data: ${pomData.dump()}")
        return pomData
    }

    private GPathResult slurpPomItself(File toSlurp) {
        try {
            return createParser().parse(toSlurp)
        } catch (SAXException e) {
            LOGGER.warn("Error parsing $toSlurp", e)
            return null
        } catch (IOException e) {
            LOGGER.warn("Error reading $toSlurp", e)
            return null
        }
    }

    private static XmlSlurper createParser() {
        // non-validating, non-namespace aware
        return new XmlSlurper(false, false)
    }

    private static boolean areModuleAndPomGroupAndArtifactIdEqual(CollectedModule module, GPathResult pom) {
        if (module == null) return false
        module.group == tryReadGroupId(pom) &&
                module.name == pom.artifactId.text()
    }

    private static boolean hasLicense(GPathResult pom) {
        return pom.licenses != null && !pom.licenses.isEmpty()
    }

    private static String tryReadGroupId(GPathResult pom) {
        pom.groupId?.text()?.trim() ?: pom.parent?.groupId?.text()?.trim()
    }
}
