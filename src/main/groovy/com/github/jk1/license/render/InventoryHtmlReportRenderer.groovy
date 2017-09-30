package com.github.jk1.license.render

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.License
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData
import org.gradle.api.Project

class InventoryHtmlReportRenderer implements ReportRenderer {

    private String name
    private String fileName
    private Project project
    private LicenseReportExtension config
    private File output
    private int counter
    private Map<String, Map<String, String>> overrides = [:]

    InventoryHtmlReportRenderer(String fileName = 'index.html', String name = null, File overridesFilename = null) {
        this.name = name
        this.fileName = fileName
        if (overridesFilename) overrides = parseOverrides(overridesFilename)
    }

    private Map<String, Map<String, String>> parseOverrides(File file) {
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

    void render(ProjectData data) {
        project = data.project
        if( name == null ) name = project.name
        config = project.licenseReport
        output = new File(config.outputDir, fileName)
        output.text = """
<html>
<head>
<title>Dependency License Report for ${name}</title>
<style>
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
        width: 300px;
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
        left: 300px;
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
<head>
<body>
<div class="container">
"""
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

    private Map<String, List<ModuleData>> buildLicenseInventory(ProjectData data) {
        Map<String, List<ModuleData>> inventory = [:]
        data.allDependencies.each { ModuleData module ->
            if (!module.poms.isEmpty()) {
                PomData pom = module.poms.first()
                if (pom.licenses.isEmpty()) {
                    addModule(inventory, module.licenseFiles.isEmpty() ? "Unknown" : "Embedded", module)
                } else {
                    pom.licenses.each { License license ->
                        addModule(inventory, license.name, module)
                    }
                }
            } else {
                addModule(inventory, module.licenseFiles.isEmpty() ? "Unknown" : "Embedded", module)
            }
        }
        return inventory
    }

    private Map<String, Map<String, List<ImportedModuleData>>> buildExternalInventories(ProjectData data) {
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

    private void addModule(Map<String, List<ModuleData>> inventory, String key, ModuleData module) {
        String gnv = "${module.group}:${module.name}:${module.version}"
        if (key == "Unknown" && overrides.containsKey(gnv)) {
            if (!inventory.containsKey(overrides[gnv].license)) inventory[overrides[gnv].license] = []
            inventory[overrides[gnv].license] << module
        } else {
            if (!inventory.containsKey(key)) inventory[key] = []
            inventory[key] << module
        }
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

    private void printDependencies(Map<String, List<ModuleData>> inventory, Map<String, Map<String, List<ImportedModuleData>>> externalInventories) {
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
                output << "<a id='${sanitize(name, license)}'></a>\n"
                dependencies.each { ImportedModuleData importedData ->
                    printImportedDependency(importedData)
                }
            }
        }
        output << "</div>\n"
    }

    private void printDependency(ModuleData data) {
        boolean projectUrlDone = false
        output << "<div class='dependency'>\n"
        output << "<p><strong> ${++counter}.</strong> "
        if (data.group) output << "<strong>Group:</strong> $data.group "
        if (data.name) output << "<strong>Name:</strong> $data.name "
        if (data.version) output << "<strong>Version:</strong> $data.version "
        output << "</p>"

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

            if (!data.poms.isEmpty()) {
                PomData pomData = data.poms.first()
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
        }

        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().files.isEmpty()) {
            output << section("Embedded license files", data.licenseFiles.first().files.collect {
                link(it, it)
            }.join(''))
        }
        output << "</div>\n"
    }

    private printImportedDependency(ImportedModuleData data) {
        output << "<div class='dependency'>\n"
        output << "<p>${++counter}. <strong>${data.name} v${data.version}</strong></p>"
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
