package com.github.jk1.license.importer

import spock.lang.Specification

class XmlReportImporterSpec extends Specification {

    def externalReport = new File(getClass().getResource('/dependencies.xml').toURI())
    def reportWithNoLinks = new File(getClass().getResource('/nolinks.xml').toURI())
    def namespacedReport = new File(getClass().getResource('/external_dtd.xml').toURI())

    def "Importer should be able to parse xml input"() {
        def importer = new XmlReportImporter('Importer', externalReport)

        expect:
        !importer.doImport().isEmpty()
    }

    def "Importer should be able to handle lazy closures"() {
        def importer = new XmlReportImporter('Importer', { externalReport })

        expect:
        !importer.doImport().isEmpty()
    }

    def "Xml importer should ignore external XML schemas"() {
        def importer = new XmlReportImporter('Importer', namespacedReport)

        expect:
        !importer.doImport().isEmpty()
    }

    def "Xml importer should understand linkless notation"() {
        def importer = new XmlReportImporter('Importer', reportWithNoLinks)

        when:
        def result = importer.doImport()

        then:
        result.size() == 1
        result.first().modules.first().name == "commons-codec"
        result.first().modules.first().version == "1.0"
        result.first().modules.first().projectUrl == ""
        result.first().modules.first().license == "Apache-2.0"
        result.first().modules.first().licenseUrl == ""
    }
}
