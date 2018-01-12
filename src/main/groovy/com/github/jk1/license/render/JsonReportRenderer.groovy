package com.github.jk1.license.render

import com.github.jk1.license.render.*
import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
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
        config = project.licenseReport
        output = new File(config.outputDir, fileName)
        output.text = '{\n'
        output << '"dependencies": [\n'
        data.allDependencies.sort().eachWithIndex { it, i ->
            printDependency(it)
            if (i < data.allDependencies.size() - 1) {
                output << ',\n'    
            }
        }
        output << ']\n'
        if (data.importedModules.size() > 0) {
            output << ', "importedModules": [\n'
            data.importedModules.eachWithIndex { importedModule, importedModuleIndex ->
                output << "{name: \"$importedModule.name\",\n"
                output << '"dependencies": [\n'
                importedModule.modules.eachWithIndex { module, moduleIndex -> 
                    printImportedModule(module)
                    if (moduleIndex < importedModule.modules.size() -1) {
                        output << ',\n'
                    } 
                }
                output << ']\n'
                output << '}\n'
                if (importedModuleIndex < data.importedModules.size() -1) {
                    output << ',\n'
                }
            }
            output << ']\n'
        }
        output << '}'
    }

    private void printDependency(ModuleData data) {
        def moduleName = "${data.group}:${data.name}"
        def moduleVersion = data.version
        def (String moduleUrl, String moduleLicense, String moduleLicenseUrl) = moduleLicenseInfo(config, data)

        output << "{\n"
        output << "\"moduleName\": \"$moduleName\",\n"
        
        if (moduleUrl) {
            output << "\"moduleUrl\": \"$moduleUrl\",\n"
        }
        output << "\"moduleVersion\": \"$moduleVersion\""
        if (moduleLicense) {
            output << ",\n\"moduleLicense\": \"$moduleLicense\""    
            if (moduleLicenseUrl) {
                output << ",\n\"moduleLicenseUrl\": \"$moduleLicenseUrl\"\n"
            }
        }

        output << "\n}"
    }

    private void printImportedModule(ImportedModuleData data) {
        output << '{\n'
        output << "{\"moduleName\": \"$data.name\",\n"
        
        if (data.projectUrl) {
            output << "{\"moduleUrl\": \"$data.projectUrl\",\n"
        }
        output << "\"version\": \"$data.version\""
        if (data.license) {
            output << ',\n\"moduleLicense\": \"$data.license\",\n'
            if (data.licenseUrl) {
                output << "\"moduleLicenseUrl\": \"$data.licenseUrl\",\n"
            }
        }

        output << '\n}'
    }

}