package com.github.jk1.license.importer

import spock.lang.Specification

class XmlReportImporterSpec extends Specification {

    def externalReport = new File(getClass().getResource('/third-party-libs.xml').toURI())

    def "Importer should be able to parse xml input"() {
        def importer = new XmlReportImporter('Importer', externalReport)

        expect:
        !importer.doImport().isEmpty()
    }
}
