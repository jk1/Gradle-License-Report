package com.github.jk1.license.render

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ProjectData
import groovy.json.JsonBuilder
import org.gradle.api.Project

/**
 * Renders a simply structured JSON dependency report
 *  {
 *  "dependencies": [
 *   {
 *      "moduleName": "...",
 *      "moduleUrl": "...",
 *      "moduleVersion": "...",
 *      "moduleLicense": "...",
 *      "moduleLicenseUrl": "...",
 *   }, ...],
 *  "importedModules": [
 *   {
 *       "name": "...",
 *       "dependencies": [
 *           "moduleName": "...",
 *           "moduleUrl": "...",
 *           "moduleVersion": "...",
 *           "moduleLicense": "...",
 *           "moduleLicenseUrl": "..."
 *       ]
 *   }, ...]
 * }
 */

class JsonReportRenderer extends SingleInfoReportRenderer {

    private String fileName
    private Project project
    private LicenseReportExtension config
    private File output

    JsonReportRenderer(String fileName = 'index.json') {
        this.fileName = fileName
    }

    void render(ProjectData data) {
        project = data.project
        config = project?.licenseReport
        output = new File(config.outputDir, fileName)

        def jsonReport = [:]
        jsonReport.dependencies = readDependencies(data.allDependencies)
        jsonReport.importedModules = readImportedModules(data.importedModules)

        output.text = new JsonBuilder(trimAndremoveNullEntries(jsonReport)).toPrettyString()
    }

    def readDependencies(def allDependencies) {
        allDependencies.collect {
            String moduleName = "${it.group}:${it.name}"
            String moduleVersion = it.version
            def (String moduleUrl, String moduleLicense, String moduleLicenseUrl) = moduleLicenseInfo(config, it)
            trimAndremoveNullEntries([moduleName      : moduleName,
                                      moduleUrl       : moduleUrl,
                                      moduleVersion   : moduleVersion,
                                      moduleLicense   : moduleLicense,
                                      moduleLicenseUrl: moduleLicenseUrl])
        }.sort { it.moduleName }
    }

    def readImportedModules(def incModules) {
        incModules.collect { ImportedModuleBundle importedModuleBundle ->
            trimAndremoveNullEntries([moduleName  : importedModuleBundle.name,
                                      dependencies: readModuleDependencies(importedModuleBundle.modules)])
        }.sort { it.moduleName }
    }

    def readModuleDependencies(def modules) {
        modules.collectEntries {
            trimAndremoveNullEntries([moduleName      : it.name,
                                      moduleUrl       : it.projectUrl,
                                      moduleVersion   : it.version,
                                      moduleLicense   : it.license,
                                      moduleLicenseUrl: it.licenseUrl])
        }
    }

    def trimAndremoveNullEntries(def map) {
        map.collectEntries { k, v ->
            v ? [(k): v instanceof String ? v.trim() : v] : [:]
        }
    }
}