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
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class InventoryHtmlReportRenderer extends InventoryReportRenderer {

    InventoryHtmlReportRenderer(String fileName = 'index.html', String name = null, File overridesFile = null) {
        super(fileName, name, overridesFile)
    }

    @Override
    void render(ProjectData data) {
        project = data.project
        if( name == null ) name = project.name
        config = project.licenseReport
        output = new File(config.absoluteOutputDir, fileName)
        output.text = getHtmlStart()
        def inventory = buildLicenseInventory(data)
        def externalInventories = buildExternalInventories(data)
        printInventory(name, inventory, externalInventories)
        printDependencies(inventory, externalInventories)
        output << """
</div>
</body>
</html>
"""
    }

    protected GString getHtmlStart() {
        """
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Dependency License Report for ${name}</title>
    <meta charset="utf-8">
    <style>
    @media print {
        .inventory {
            display: none;
        }

        .content {
            position: static !important;
        }
    }

    html, body, section {
      height: 100%;
    }

    body {
      font-family: sans-serif;
      line-height: 125%;
      margin: 0;
      background: #eee;
    }

    .header {
        /* #22aa44 */
        background: #ff5588;
        color: white;
        padding: 2em 1em 1em 1em;
    }

    .header h1 {
        font-size: 16pt;
        margin: 0.5em 0;
    }

    .header h2 {
        font-size: 10pt;
        margin: 0;
    }

    .container {
    }

    .inventory {
        background: #292e34;
        color: #ddd;
        padding: 0;
        position: fixed;
        left: 0;
        top: 0;
        height: 100%;
        width: 20em;
        overflow: auto;
    }

    .inventory ul {
        margin: 0;
        padding: 0;
    }

    .inventory li {
        list-style: none;
        padding: 0;
        margin: 0;
    }

    .inventory li a {
        width: 100%;
        box-sizing: border-box;
        color: #ddd;
        text-decoration: none;
        display: flex;
        flex-direction: row;
        padding: 15px 12px;
    }

    .inventory li a:hover {
        background: #383f45;
        color: white;
    }

    .license .name {
        flex-grow: 1;

    }

    .license .badge {
        background: #ff5588;
        padding: 10px 15px;
        border-radius: 20px;
        color: white;
    }

    .content {
        padding: 1rem;
        position: absolute;
        top: 0;
        bottom: 0;
        left: 20em;
    }

    .dependency {
        background: white;
        padding: 1em;
        margin-bottom: 1em;
    }

    .dependency label {
        font-weight: bold;
    }

    .dependency-value {
    }
    </style>
</head>
<body>
<div class="container">
"""
    }

    private void printInventory(String title, Map<String, List<ModuleData>> inventory, Map<String, Map<String, List<ImportedModuleData>>> externalInventories) {
        output << "<div class='inventory'>\n"
        output << "<div class='header'>\n"
        output << "<h1>$project.name ${!'unspecified'.equals(project.version) ? project.version : ''}</h1>\n"
        output << "<h2>Dependency License Report</h2>\n"
        output << "<h2 class='timestamp'><em>${new Date().format('yyyy-MM-dd HH:mm:ss z')}</em>.</h2>"
        output << "</div>\n"

        output << "<h3>${title}</h3>\n"
        output << "<ul>\n"
        inventory.keySet().sort().each { String license ->
            output << "<li><a class='license' href='#${sanitize(title, license)}'><span class='name'>${license}</span> <span class='badge'>${inventory[license].size()}</span></a></li>\n"
        }
        output << "</ul>\n"

        externalInventories.each { String name, Map<String, List<ImportedModuleData>> modules ->
            output << "<h3>${name}</h3>\n"
            output << "<ul>\n"
            modules.each { String license, List<ImportedModuleData> dependencies ->
                output << "<li><a class='license' href='#${sanitize(name, license)}'><span class='name'>${license}</span> <span class='badge'>${dependencies.size()}</span></a></li>\n"
            }
            output << "</ul>\n"
        }

        output << "</div>\n"
    }

    String sanitize(String... values) {
        values.findAll { it != null }.collect { it.replaceAll(/\s/, '_') }.join('_')
    }

    protected void printDependencies(Map<String, List<ModuleData>> inventory, Map<String, Map<String, List<ImportedModuleData>>> externalInventories) {
        output << "<div class='content'>\n"
        output << "<h1>${name}</h1>\n"
        inventory.keySet().sort().each { String license ->
            output << "<a id='${sanitize(name, license)}'></a>\n" << "<h2>${license}</h2>\n"
            inventory[license].sort({ ModuleData a, ModuleData b -> a.group <=> b.group }).each { ModuleData data ->
                printDependency(data)
            }
        }

        externalInventories.keySet().sort().each { String name ->
            output << "<h1>${name}</h1>\n"
            externalInventories[name].each { String license, List<ImportedModuleData> dependencies ->
                output << "<a id='${sanitize(name, license)}'></a>\n" << "<h2>${license}</h2>\n"
                dependencies.each { ImportedModuleData importedData ->
                    printImportedDependency(importedData)
                }
            }
        }
        output << "</div>\n"
    }

    protected void printDependency(ModuleData data) {
        boolean projectUrlDone = false
        printDependencyMetaInformation(data.group, data.name, data.version)

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
        output << "</div>\n"
    }

    protected void printDependencyMetaInformation(String group, String name, String version) {
        output << "<div class='dependency'>\n"
        output << "<p><strong> ${++counter}.</strong> "
        if (group) output << "<strong>Group:</strong> ${group} "
        if (name) output << "<strong>Name:</strong> ${name} "
        if (version) output << "<strong>Version:</strong> ${version} "
        output << "</p>"
    }

    protected void printDependencyManifest(ManifestData manifest, boolean projectUrlDone) {
        if (manifest.url && !projectUrlDone) {
            output << sectionLink("Manifest Project URL", manifest.url, manifest.url)
        }
        if (manifest.license) {
            if (Files.maybeLicenseUrl(manifest.licenseUrl)) {
                output << section("Manifest License", "${manifest.license} - ${Files.maybeLicenseUrl(manifest.licenseUrl) ? link(manifest.licenseUrl, manifest.licenseUrl) : section("License", manifest.licenseUrl)}")
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
                    output << section("POM License", "${license.name} - ${Files.maybeLicenseUrl(license.url) ? link(license.url, license.url) : section("License", license.url)}")
                } else {
                    output << section("POM License", license.name)
                }
            }
        }
    }

    protected printDependencyLicenseFiles(TreeSet<LicenseFileData> licenseFiles) {
        output << section("Embedded license files", licenseFiles.first().fileDetails.collect {
            link(it.file, it.file)
        }.unique().join('<br>'))
    }

    protected printImportedDependency(ImportedModuleData data) {
        output << "<div class='dependency'>\n"
        output << "<p><strong>${++counter}. ${data.name} v${data.version}</strong></p>"
        output << sectionLink("Project URL", data.projectUrl, data.projectUrl)
        output << sectionLink("License URL", data.license, data.licenseUrl)
        output << "</div>\n"
    }

    private GString section(String label, String value) {
        "<label>${label}</label>\n<div class='dependency-value'>${value}</div>\n"
    }

    private GString link(String name, String url) {
        "<a href='${url}'>${name}</a>"
    }

    private GString sectionLink(String label, String name, String url) {
        section(label, link(name, url))
    }

    private String safeGet(String[] arr, int index) {
        arr.length > index ? arr[index] : null
    }

}
