package com.github.jk1.license.importer

import com.github.jk1.license.ImportedModuleData

interface DependencyDataImporter  {

    String getImporterName()

    Collection<ImportedModuleData> doImport()

}