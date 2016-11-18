package com.github.jk1.license.importer

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.ImportedModuleData
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.GradleException
import org.xml.sax.SAXParseException


class XmlReportImporter implements DependencyDataImporter {

    String importerName

    private Closure<File> externalReport

    XmlReportImporter(String name, File externalReport) {
        this.importerName = name
        this.externalReport = { externalReport }
    }

    XmlReportImporter(String name, Closure<File> externalReport) {
        this.importerName = name
        this.externalReport = externalReport
    }

    @Override
    Collection<ImportedModuleBundle> doImport() {
        def bundles= new HashSet<ImportedModuleBundle>()
        try {
            def root = createParser().parse(externalReport.call())
            if ("topic".equals(root.name())){
                bundles.addAll(parseTopic(root))
            } else if ("chapter"){
                bundles.add(parseChapter(root))
            } else {
                throw new GradleException("Dependency data importer: don't know how to parse ${root.name()} root tag")
            }
        } catch (SAXParseException e){
            // malformed xml?
            def topic = createParser().parseText("<topic><chunk>${externalReport.call().text}</chunk></topic>")
            return parseTopic(topic)
        }
        return bundles
    }

    private Collection<ImportedModuleBundle> parseTopic(GPathResult topic){
        return topic.chunk.chapter.collect{
            parseChapter(it)
        }
    }

    private ImportedModuleBundle parseChapter(GPathResult chapter){
        def importedModules = chapter.table.tr.collect {
            new ImportedModuleData(
                    name: it.td[0].a,
                    version: it.td[1],
                    projectUrl: it.td[0].a.@href,
                    license: it.td[2].a,
                    licenseUrl: it.td[2].a.@href
            )
        }
        if (!importedModules.isEmpty()) {
            importedModules = importedModules.tail() // strip meaningless header
        }
        return new ImportedModuleBundle(chapter.@title.toString(), importedModules)
    }

    private XmlSlurper createParser(){
        def parser = new XmlSlurper()
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        return parser
    }
}
