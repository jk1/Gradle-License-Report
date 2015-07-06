package com.github.jk1.license.importer

import com.github.jk1.license.ImportedModuleData


class XmlReportImporter implements DependencyDataImporter {

    def String importerName

    private File externalReport

    public XmlReportImporter(String name, File externalReport) {
        this.importerName = name
        this.externalReport = externalReport
    }

    @Override
    Collection<ImportedModuleData> doImport() {
        def gPath = new XmlSlurper().parse(externalReport)
        def importedModules = gPath.table.tr.collect {
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
        return importedModules
    }
}
