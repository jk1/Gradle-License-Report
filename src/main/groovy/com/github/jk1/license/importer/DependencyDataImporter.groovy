package com.github.jk1.license.importer

import com.github.jk1.license.ImportedModuleBundle

interface DependencyDataImporter  {

    String getImporterName()

    Collection<ImportedModuleBundle> doImport()

}