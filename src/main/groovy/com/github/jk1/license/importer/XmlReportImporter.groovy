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

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.ImportedModuleData
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.xml.sax.SAXParseException


class XmlReportImporter implements DependencyDataImporter {

    private String importerName

    private Closure<File> externalReport

    XmlReportImporter(String name, File externalReport) {
        this.importerName = name
        this.externalReport = { externalReport }
    }

    XmlReportImporter(String name, Closure<File> externalReport) {
        this.importerName = name
        this.externalReport = externalReport
    }

    @Input
    @Override
    String getImporterName() {
        return importerName
    }

    @Override
    Collection<ImportedModuleBundle> doImport() {
        def bundles = new HashSet<ImportedModuleBundle>()
        try {
            def root = createParser().parse(externalReport.call())
            if ("topic".equals(root.name())) {
                bundles.addAll(parseTopic(root))
            } else if ("chapter".equals(root.name())) {
                bundles.add(parseChapter(root))
            } else {
                throw new GradleException("Dependency data importer: don't know how to parse ${root.name()} root tag")
            }
        } catch (SAXParseException e) {
            // malformed xml?
            def topic = createParser().parseText("<topic><chunk>${externalReport.call().text}</chunk></topic>")
            return parseTopic(topic)
        }
        return bundles
    }

    private Collection<ImportedModuleBundle> parseTopic(GPathResult topic) {
        return topic.chunk.chapter.collect {
            parseChapter(it)
        }
    }

    private ImportedModuleBundle parseChapter(GPathResult chapter) {
        def importedModules = chapter.table.tr.collect {
            new ImportedModuleData(
                    name: it.td[0].a.size() == 0 ? it.td[0].text() : it.td[0].a,
                    version: it.td[1],
                    projectUrl: it.td[0].a.@href,
                    license: it.td[2].a.size() == 0 ? it.td[2].text() : it.td[2].a,
                    licenseUrl: it.td[2].a.@href
            )
        }
        if (!importedModules.isEmpty()) {
            importedModules = importedModules.tail() // strip meaningless header
        }
        return new ImportedModuleBundle(chapter.@title.toString(), importedModules)
    }

    private XmlSlurper createParser() {
        def parser = new XmlSlurper()
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        return parser
    }
}
