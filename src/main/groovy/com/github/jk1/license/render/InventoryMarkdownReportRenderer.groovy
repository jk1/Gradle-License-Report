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

import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.License
import com.github.jk1.license.LicenseFileData
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData

class InventoryMarkdownReportRenderer extends InventoryReportRenderer {

    InventoryMarkdownReportRenderer(String fileName = 'licenses.md', String name = null, File overridesFile = null) {
        super(fileName, name, overridesFile)
    }

    @Override
    void render(ProjectData data) {
        project = data.project
        if( name == null ) name = project.name
        config = project.licenseReport
        output = new File(config.absoluteOutputDir, fileName)
        output.delete()
        def inventory = buildLicenseInventory(data)
        def externalInventories = buildExternalInventories(data)
        printDependencies(inventory, externalInventories)

    }

    protected void printDependencies(Map<String, List<ModuleData>> inventory, Map<String, Map<String, List<ImportedModuleData>>> externalInventories) {
        printHeader()

        inventory.keySet().sort().each { String license ->
            output << "## ${license}\n\n"
            inventory[license].sort({ ModuleData a, ModuleData b -> a.group <=> b.group }).each { ModuleData data ->
                printDependency(data)
            }
        }

        externalInventories.keySet().sort().each { String name ->
            output << "## ${name}\n\n"
            externalInventories[name].each { String license, List<ImportedModuleData> dependencies ->
                output << "\n"
                dependencies.each { ImportedModuleData importedData ->
                    printImportedDependency(importedData)
                }
            }
        }
        output << "\n"
    }

    protected void printHeader() {
        output << "\n"
        output << "# ${name}\n"
        output << "## Dependency License Report\n"
        output << "_${new Date().format('yyyy-MM-dd HH:mm:ss z')}_\n"
    }

    protected void printDependency(ModuleData data) {
        boolean projectUrlDone = false
        printDependencyMetaInformation(data)

        String gnv = "${data.group}:${data.name}:${data.version}"
        if (overrides.containsKey(gnv)) {
            output << sectionLink("Project URL", overrides[gnv].projectUrl, overrides[gnv].projectUrl)
            output << sectionLink("License URL", overrides[gnv].license, overrides[gnv].licenseUrl)
        } else {
            if (!data.manifests.isEmpty() && !data.poms.isEmpty()) {
                ManifestData manifest = data.manifests.first()
                PomData pomData = data.poms.first()
                if (manifest.url && pomData.projectUrl && manifest.url == pomData.projectUrl) {
                    output << sectionLink("Project URL", manifest.url, manifest.url)
                    projectUrlDone = true
                }
            }

            if (!data.manifests.isEmpty()) {
                ManifestData manifest = data.manifests.first()
                printDependencyManifest(manifest, projectUrlDone)
            }

            if (!data.poms.isEmpty()) {
                PomData pomData = data.poms.first()
                printDependencyPom(pomData, projectUrlDone)
            }
        }


        def licenseFiles = data.licenseFiles
        if (!licenseFiles.isEmpty() && !licenseFiles.first().fileDetails.isEmpty()) {
            printDependencyLicenseFiles(licenseFiles)
        }
        output << "\n"
    }

    protected void printDependencyMetaInformation(ModuleData data) {
        output << "**${++counter}** "
        if (data.group) output << "**Group:** `$data.group` "
        if (data.name) output << "**Name:** `$data.name` "
        if (data.version) output << "**Version:** `$data.version` "
        output << "\n"
    }

    protected void printDependencyManifest(ManifestData manifest, boolean projectUrlDone) {
        if (manifest.url && !projectUrlDone) {
            output << sectionLink("Manifest Project URL", manifest.url, manifest.url)
        }
        if (manifest.license) {
            if (manifest.license.startsWith("http")) {
                output << sectionLink("Manifest license URL", manifest.license, manifest.license)
            } else if (manifest.hasPackagedLicense) {
                output << sectionLink("Packaged License File", manifest.license, manifest.url)
            } else {
                output << section("Manifest License", "${manifest.license} (Not Packaged)")
            }
        }
    }

    protected void printDependencyPom(PomData pomData, boolean projectUrlDone) {
        if (pomData.projectUrl && !projectUrlDone) {
            output << sectionLink("POM Project URL", pomData.projectUrl, pomData.projectUrl)
        }
        if (pomData.licenses) {
            pomData.licenses.each { License license ->
                if (license.url) {
                    output << section("POM License", "${license.name} - ${license.url.startsWith("http") ? link(license.url, license.url) : section("License", license.url)}")
                } else {
                    output << section("POM License", license.name)
                }
            }
        }
    }

    protected printDependencyLicenseFiles(TreeSet<LicenseFileData> licenseFiles) {
        output << section("Embedded license files", licenseFiles.first().fileDetails.collect {
            link(it.file, it.file)
        }.unique().join(' \n    - '))
    }

    protected printImportedDependency(ImportedModuleData data) {
        output << "\n\n"
        output << "${++counter}. **${data.name} v${data.version}**\n"
        output << sectionLink("Project URL", data.projectUrl, data.projectUrl)
        output << sectionLink("License URL", data.license, data.licenseUrl)
        output << "\n\n"
    }

    private GString section(String label, String value) {
        "> - **${label}**: ${value}\n"
    }

    private GString link(String name, String url) {
        "[${url}](${name})"
    }

    private GString sectionLink(String label, String name, String url) {
        section(label, link(name, url))
    }

    private String safeGet(String[] arr, int index) {
        arr.length > index ? arr[index] : null
    }

}
