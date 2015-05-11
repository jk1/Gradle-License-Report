package com.github.jk1.license.reader

import com.github.jk1.license.data.PomData
import com.github.jk1.license.task.DependencyLicenseReport
import com.google.common.io.Files
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.artifacts.ResolvedArtifact

import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class PomReader {

    PomData readPomData(DependencyLicenseReport report, ResolvedArtifact artifact) {
        GPathResult pomContent = slurpPom(report, artifact.file)
        if (!pomContent) {
            Map pomId = [
                    "group"  : artifact.moduleVersion.id.group,
                    "name"   : artifact.moduleVersion.id.name,
                    "version": artifact.moduleVersion.id.version,
                    "ext"    : "pom"
            ]

            Collection<ResolvedArtifact> artifacts = report.resolveArtifacts(pomId)
            pomContent = artifacts?.inject(pomContent) { GPathResult memo, ResolvedArtifact resolved ->
                try {
                    memo = memo ?: slurpPom(report, resolved.file)
                } catch (Exception e) {
                    report.logger.warn("Error slurping pom from $resolved.file", e)
                }
                return memo
            }
        }

        if (!pomContent) {
            report.logger.info("No POM content found for: $artifact.file")
            return null
        } else {
            return readPomFile(report, pomContent)
        }
    }

    GPathResult slurpPom(DependencyLicenseReport report, File toSlurp) {
        if (toSlurp.name == "pom.xml") {
            report.logger.debug("Slurping pom from pom.xml file: $toSlurp")
            return slurpPomItself(toSlurp)
        }

        String fileSuffix = Files.getFileExtension(toSlurp.name)?.toLowerCase()
        if (!fileSuffix) {
            report.logger.debug("No file suffix on potential pom-containing file: $toSlurp")
            return null
        }
        switch (fileSuffix) {
            case "pom":
                report.logger.debug("Slurping pom from *.pom file: $toSlurp")
                return slurpPomItself(toSlurp)
            case "zip":
            case "jar":
                report.logger.debug("Processing pom from archive: $toSlurp")
                return slurpPomFromZip(report, toSlurp)
        }

        report.logger.debug("No idea how to process a pom from: $toSlurp")
        return null
    }

    GPathResult slurpPomFromZip(DependencyLicenseReport report, File archiveToSearch) {
        ZipFile archive = new ZipFile(archiveToSearch, ZipFile.OPEN_READ)
        ZipEntry pomEntry = archive.entries().toList().find { ZipEntry entry ->
            entry.name.endsWith("pom.xml") || entry.name.endsWith(".pom")
        }
        report.logger.debug("Searching for POM file in $archiveToSearch -- found ${pomEntry?.name}")
        if (!pomEntry) return null
        return new XmlSlurper().parse(archive.getInputStream(pomEntry))
    }

    GPathResult slurpPomItself(File toSlurp) {
        return new XmlSlurper().parse(toSlurp)
    }



    PomData readPomFile(DependencyLicenseReport report, GPathResult pomContent) {
        return readPomFile(report, pomContent, new PomData())
    }

    PomData readPomFile(DependencyLicenseReport report, GPathResult pomContent, PomData pomData) {
        if (!pomContent) {
            report.logger.info("No content found in pom")
            return null
        }

        report.logger.debug("POM content children: ${pomContent.children()*.name() as Set}")

        if (!pomContent.parent.children().isEmpty()) {
            report.logger.debug("Processing parent POM: ${pomContent.parent.children()*.name()}")

            GPathResult parentContent = pomContent.parent

            Map<String, String> parent = [
                    "group"  : parentContent.groupId.text(),
                    "name"   : parentContent.artifactId.text(),
                    "version": parentContent.version.text(),
                    "ext"    : "pom"
            ]

            report.logger.debug("Parent to fetch: $parent")

            Collection<ResolvedArtifact> parentArtifacts = report.resolveArtifacts(parent)
            if (parentArtifacts) {
                (parentArtifacts*.file as Set).each { File file ->
                    report.logger.debug("Processing parent POM file: $file")
                    pomData = readPomFile(report, new XmlSlurper().parse(file), pomData)
                }
            }
        }

        pomData.name = pomContent.name?.text()
        pomData.description = pomContent.description?.text()
        pomData.projectUrl = pomContent.url?.text()

        report.logger.debug("POM license : ${pomContent.licenses.children()*.name() as Set}")

        pomContent.licenses?.license?.each { GPathResult license ->
            report.logger.debug("Processing license: ${license.name.text()}")
            pomData.licenses << new PomData.License(
                    name: license.name?.text(),
                    url: license.url?.text(),
                    distribution: license.distribution?.text(),
                    comments: license.comments?.text()
            )
        }

        report.logger.info("Returning pom data: ${pomData.dump()}")
        return pomData
    }
}
