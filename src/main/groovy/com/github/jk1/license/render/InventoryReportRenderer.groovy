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
 package com.github.jk1.license.render

import com.github.jk1.license.*
import com.github.jk1.license.util.Files
import org.gradle.api.Project
import org.gradle.api.tasks.Input

class InventoryReportRenderer implements ReportRenderer {
    protected String name
    protected String fileName
    protected Project project
    protected LicenseReportExtension config
    protected File output
    protected int counter
    protected Map<String, Map<String, String>> overrides = [:]

    InventoryReportRenderer(String fileName = 'licenses.txt', String name = null, File overridesFilename = null) {
            this.name = name
            this.fileName = fileName
            if (overridesFilename) overrides = parseOverrides(overridesFilename)
        }

    @Input
    private String getFileNameCache() { return this.fileName }


    @Override
    void render(ProjectData data) {
        project = data.project
        if( name == null ) name = project.name
        config = project.licenseReport
        output = new File(config.outputDir, fileName)
        output.delete() // clear old output
        def inventory = buildLicenseInventory(data)
        def externalInventories = buildExternalInventories(data)
        printDependencies(inventory, externalInventories)

    }

    protected Map<String, Map<String, String>> parseOverrides(File file) {
        overrides = [:]
        file.withReader { Reader reader ->
            String line
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(/\|/)
                String groupNameVersion = columns[0]
                overrides[groupNameVersion] = [projectUrl: safeGet(columns, 1), license: safeGet(columns, 2), licenseUrl: safeGet(columns, 3)]
            }
        }
        return overrides
    }

    protected Map<String, List<ModuleData>> buildLicenseInventory(ProjectData data) {
        Map<String, List<ModuleData>> inventory = [:]
        data.allDependencies.each { ModuleData module ->
            boolean anyLicense = false

            for (ManifestData manifestData : module.manifests) {
                if (manifestData.license && Files.maybeLicenseUrl(manifestData.licenseUrl)) {
                    anyLicense = true
                    addModule(inventory, manifestData.license, module)
                }
            }

            for (PomData pom : module.poms) {
                for (License license : pom.licenses) {
                    addModule(inventory, license.name, module)
                    anyLicense = true
                }
            }

            if (!anyLicense) {
                addModule(inventory, module.licenseFiles.isEmpty() ? "Unknown" : "Embedded", module)
            }
        }
        return inventory
    }

    protected Map<String, Map<String, List<ImportedModuleData>>> buildExternalInventories(ProjectData data) {
        Map<String, Map<String, List<ImportedModuleData>>> results = [:]
        data.importedModules.each { ImportedModuleBundle module ->
            Map<String, List<ImportedModuleData>> bundle = [:]
            module.modules.each { ImportedModuleData moduleData ->
                if (!bundle.containsKey(moduleData.license)) bundle[moduleData.license] = []
                bundle[moduleData.license] << moduleData
            }
            results[module.name] = bundle
        }
        return results
    }

    protected void addModule(Map<String, List<ModuleData>> inventory, String key, ModuleData module) {
        String gnv = "${module.group}:${module.name}:${module.version}"
        if (key == "Unknown" && overrides.containsKey(gnv)) {
            if (!inventory.containsKey(overrides[gnv].license)) inventory[overrides[gnv].license] = []
            inventory[overrides[gnv].license] << module
        } else {
            if (!inventory.containsKey(key)) inventory[key] = []
            inventory[key] << module
        }
    }

    private void printDependencies(Map<String, List<ModuleData>> inventory, Map<String, Map<String, List<ImportedModuleData>>> externalInventories) {
        output << "\n"
        output << "Project: ${name}\n"
        output << "Dependency License Report\n"
        output << "Generated: ${new Date().format('yyyy-MM-dd HH:mm:ss z')}\n\n"

        inventory.keySet().sort().each { String license ->
            output << "\n${license}\n"
            output << "".padLeft(license.length(), '=')
            inventory[license].sort({ ModuleData a, ModuleData b -> a.group <=> b.group }).each { ModuleData data ->
                printDependency(data)
            }
        }

        externalInventories.keySet().sort().each { String name ->
            output << "${name}\n"
            output << "".padLeft(name.length(), '=')

            externalInventories[name].each { String license, List<ImportedModuleData> dependencies ->
                output << "\n"
                dependencies.each { ImportedModuleData importedData ->
                    printImportedDependency(importedData)
                }
            }
        }
        output << "\n"
    }

    private void printDependency(ModuleData data) {
        boolean projectUrlDone = false
        output << "\n\n"
        output << "${++counter} "
        if (data.group) output << "Group: $data.group "
        if (data.name) output << " Name: $data.name "
        if (data.version) output << "Version: $data.version "
        output << "\n"

        String gnv = "${data.group}:${data.name}:${data.version}"
        if (overrides.containsKey(gnv)) {
            output << "  - Project URL:\n    - ${overrides[gnv].projectUrl}\n"
            output << "  - License URL:\n    - ${overrides[gnv].licenseUrl}\n"
        } else {
            if (!data.manifests.isEmpty() && !data.poms.isEmpty()) {
                ManifestData manifest = data.manifests.first()
                PomData pomData = data.poms.first()
                if (manifest.url && pomData.projectUrl && manifest.url == pomData.projectUrl) {
                    output << "  - Project URL:\n    - ${manifest.url}\n"
                    projectUrlDone = true
                }
            }

            if (!data.manifests.isEmpty()) {
                ManifestData manifest = data.manifests.first()
                if (manifest.url && !projectUrlDone) {
                    output << "  - Manifest Project URL:\n    - ${manifest.url}\n"
                }
                if (manifest.license) {
                    if (manifest.license.startsWith("http")) {
                        output << "  - Manifest license URL:\n    - ${manifest.license}\n"
                    } else if (manifest.hasPackagedLicense) {
                        output << "  - Packaged License File:\n    - ${manifest.license}\n"
                    } else {
                        output << "  - Manifest License:\n    - ${manifest.license} (Not Packaged)\n"
                    }
                }
            }

            if (!data.poms.isEmpty()) {
                PomData pomData = data.poms.first()
                if (pomData.projectUrl && !projectUrlDone) {
                    output << "  - POM Project URL:\n    - ${pomData.projectUrl}\n"
                }
                if (pomData.licenses) {
                    pomData.licenses.each { License license ->
                        if (license.url) {
                            output << "  - POM License:\n    - ${license.name}\n      - ${license.url}\n"
                        } else {
                            output << "  - POM License:\n    - ${license.name}\n"
                        }
                    }
                }
            }
        }

        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().fileDetails.isEmpty()) {
            output << "  - Embedded license files: \n    - " + data.licenseFiles.first().fileDetails.collect {
                it.file
            }.unique().join(' \n    - ')
        }
        output << "\n"
    }

    private printImportedDependency(ImportedModuleData data) {
        output << "\n\n"
        output << "${++counter}. ${data.name} v${data.version}\n"
        output << "  - Project URL:\n    - ${data.projectUrl}\n"
        output << "  - License URL:\n    - ${data.licenseUrl}\n"
        output << "\n\n"
    }

    private String safeGet(String[] arr, int index) {
        arr.length > index ? arr[index] : null
    }

}

