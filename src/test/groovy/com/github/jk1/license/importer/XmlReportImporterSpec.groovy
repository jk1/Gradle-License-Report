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
