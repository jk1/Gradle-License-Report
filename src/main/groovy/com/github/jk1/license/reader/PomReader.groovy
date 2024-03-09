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

import com.github.jk1.license.GradleProject
import com.github.jk1.license.License
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.PomData
import com.github.jk1.license.PomDeveloper
import com.github.jk1.license.PomOrganization
import com.github.jk1.license.task.ReportTask
import com.github.jk1.license.util.CachingArtifactResolver
import com.github.jk1.license.util.Files
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.xml.sax.SAXException

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class PomReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private LicenseReportExtension config
    private CachingArtifactResolver resolver

    PomReader(LicenseReportExtension config) {
        this.config = config
    }

    PomData readPomData(GradleProject project, ResolvedArtifact artifact) {
        resolver = new CachingArtifactResolver(project)
        GPathResult pomContent = findAndSlurpPom(artifact.file)
        boolean pomRepresentsArtifact = true
        boolean pomHasLicense = true

        if (pomContent) {
            pomRepresentsArtifact = areArtifactAndPomGroupAndArtifactIdEqual(artifact, pomContent)
            if (!pomRepresentsArtifact) {
                LOGGER.debug("Use remote pom because the found pom seems not to represent artifact. " +
                    "Artifact: ${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name} / " +
                    "Pom: ${pomContent.groupId.text()}:${pomContent.artifactId.text()})")
            }
            pomHasLicense = hasLicense(pomContent)
        }

        if (!pomContent || !pomRepresentsArtifact || !pomHasLicense) {
            pomContent = fetchRemoteArtifactPom(artifact) ?: pomContent
        }

        if (!pomContent) {
            LOGGER.info("No POM content found for: $artifact.file")
            return null
        } else {
            return readPomFile(pomContent)
        }
    }

    private GPathResult findAndSlurpPom(File toSlurp) {
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
            case "zip":
            case "jar":
                LOGGER.debug("Processing pom from archive: $toSlurp")
                return slurpFirstPomFromZip(toSlurp)
        }

        LOGGER.debug("No idea how to process a pom from: $toSlurp")
        return null
    }

    private GPathResult slurpFirstPomFromZip(File archiveToSearch) {
        ZipFile archive = new ZipFile(archiveToSearch, ZipFile.OPEN_READ)
        ZipEntry pomEntry = archive.entries().toList().find { ZipEntry entry ->
            entry.name.endsWith("pom.xml") || entry.name.endsWith(".pom")
        }
        LOGGER.debug("Searching for POM file in $archiveToSearch -- found ${pomEntry?.name}")
        if (!pomEntry) return null
        try {
            return createParser().parse(archive.getInputStream(pomEntry))
        } catch (SAXException e) {
            LOGGER.warn("Error parsing $pomEntry.name in $archiveToSearch", e)
            return null
        } catch (IOException e) {
            LOGGER.warn("Error reading $pomEntry.name in $archiveToSearch", e)
            return null
        }
    }

    private GPathResult fetchRemoteArtifactPom(ResolvedArtifact artifact) {
        Collection<ResolvedArtifact> artifacts = fetchRemoteArtifactPoms(artifact.moduleVersion.id.group,
            artifact.moduleVersion.id.name, artifact.moduleVersion.id.version)

        return artifacts.collect {
            try {
                findAndSlurpPom(it.file)
            } catch (Exception e) {
                LOGGER.warn("Error slurping pom from $it.file", e)
                null
            }
        }.find {
            it != null
        }
    }

    private Collection<ResolvedArtifact> fetchRemoteArtifactPoms(String group, String name, String version) {
        Map<String, String> pomId = [
            "group"  : group,
            "name"   : name,
            "version": version,
            "ext"    : "pom"
        ]

        LOGGER.debug("Fetch: $pomId")
        try {
            resolver.resolveArtifacts(pomId)
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve artifacts for " + pomId, e)
            Collections.emptyList()
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

        String groupId = parentContent.groupId.text()
        String artifactId = parentContent.artifactId.text()
        String version = parentContent.version.text()

        Collection<ResolvedArtifact> parentArtifacts = fetchRemoteArtifactPoms(groupId, artifactId, version)

        if (parentArtifacts) {
            (parentArtifacts*.file as Set).each { File file ->
                LOGGER.debug("Processing parent POM file: $file")
                GPathResult childPomGPath = slurpPomItself(file)

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

    private static boolean areArtifactAndPomGroupAndArtifactIdEqual(ResolvedArtifact artifact, GPathResult pom) {
        if (artifact == null) return false
        artifact.moduleVersion.id.group == tryReadGroupId(pom) &&
            artifact.moduleVersion.id.name == pom.artifactId.text()
    }

    private static boolean hasLicense(GPathResult pom) {
        return pom.licenses != null && !pom.licenses.isEmpty()
    }

    private static String tryReadGroupId(GPathResult pom) {
        pom.groupId?.text() ?: pom.parent?.groupId?.text()
    }
}
