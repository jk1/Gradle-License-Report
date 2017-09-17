package com.github.jk1.license.render

import com.github.jk1.license.License
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData
import org.gradle.api.Project

class InventoryHtmlReportRenderer implements ReportRenderer {

    private Project project
    private LicenseReportExtension config
    private File output
    private int counter

    void render(ProjectData data) {
        project = data.project
        config = project.licenseReport
        output = new File(config.outputDir, 'index.html')
        output.text = """
<html>
<head>
<title>Dependency License Report for $project.name</title>
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
        display: block;
        padding: 15px 12px;
    }

    .inventory li a:hover {
        background: #383f45;
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

</style>
<head>
<body>
<div class="container">
"""
        def inventory = buildLicenseInventory(data)
        printInventory( inventory )
        printDependencies( inventory )
        output << """
</div>
</body>
</html>
"""
    }

    private Map<String,List<ModuleData>> buildLicenseInventory(ProjectData data) {
        Map<String,List<ModuleData>> inventory = [:]
        data.allDependencies.each { ModuleData module ->
            if( !module.poms.isEmpty() ) {
                PomData pom = module.poms.first()
                if( pom.licenses.isEmpty() ) {
                    addModule(inventory, "Unknown", module)
                } else {
                    pom.licenses.each { License license ->
                        addModule( inventory, license.name, module )
                    }
                }
            } else {
                addModule( inventory, "Unknown", module )
            }
        }
        return inventory
    }

    private void addModule( Map<String,List<ModuleData>> inventory, String key, ModuleData module ) {
        if( !inventory.containsKey(key) ) inventory[ key ] = []
        inventory[key] << module
    }

    private void printInventory( Map<String,List<ModuleData>> inventory ) {
        output << "<div class='inventory'>\n"
        output << "<div class='header'>\n"
        output << "<h1>$project.name ${ !'unspecified'.equals(project.version) ? project.version : ''}</h1>\n"
        output << "<h2>Dependency License Report</h2>\n"
        output << "</div>\n"
        output << "<ul>\n"
        inventory.keySet().sort().each { String license ->
            output << "<li><a href='#${license}'>${license} (${inventory[license].size()})</a></li>\n"
        }
        output << "</ul>\n"
        output << "<p class='timestamp'>This report was generated at <em>${new Date()}</em>.</p>"
        output << "</div>\n"
    }

    private void printDependencies(Map<String,List<ModuleData>> inventory) {
        output << "<div class='content'>\n"
        inventory.keySet().sort().each { String license ->
            output << "<a id='${license}'></a>\n"
            inventory[license].each { ModuleData data ->
                printDependency( data )
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

        if (data.poms.isEmpty() && data.manifests.isEmpty()) {
            output << "<p><strong>No license information found</strong></p>"
            return
        }

        if (!data.manifests.isEmpty() && !data.poms.isEmpty()) {
            ManifestData manifest = data.manifests.first()
            PomData pomData = data.poms.first()
            if (manifest.url && pomData.projectUrl && manifest.url == pomData.projectUrl) {
                output << "<p><strong>Project URL:</strong> <code><a href='$manifest.url'>$manifest.url</a></code></p>"
                projectUrlDone = true
            }
        }

        if (!data.manifests.isEmpty()) {
            ManifestData manifest = data.manifests.first()
            if (manifest.url && !projectUrlDone) {
                output << "<p><strong>Manifest Project URL:</strong> <code><a href='$manifest.url'>$manifest.url</a></code></p>"
            }
            if (manifest.license) {
                if (manifest.license.startsWith("http")) {
                    output << "<p><strong>Manifest license URL:</strong> <a href='$manifest.license'>$manifest.license</a></p>"
                } else if (manifest.hasPackagedLicense) {
                    output << "<p><strong>Packaged License File:</strong> <a href='$manifest.url'>$manifest.license</a></p>"
                } else {
                    output << "<p><strong>Manifest License:</strong> $manifest.license (Not packaged)</p>"
                }
            }
        }

        if (!data.poms.isEmpty()) {
            PomData pomData = data.poms.first()
            if (pomData.projectUrl && !projectUrlDone) {
                output << "<p><strong>POM Project URL:</strong> <code><a href='$pomData.projectUrl'>$pomData.projectUrl</a></code></p>"
            }
            if (pomData.licenses) {
                pomData.licenses.each { License license ->
                    output << "<p><strong>POM License: $license.name</strong>"
                    if (license.url) {
                        if (license.url.startsWith("http")) {
                            output << " - <a href='$license.url'>$license.url</a>"
                        } else {
                            output << "<p><strong>License:</strong> $license.url</p>"
                        }
                    }
                }
            }
        }
        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().files.isEmpty()) {
            output << '<p><strong>Embedded license files:</strong> '
            output << data.licenseFiles.first().files.collect({ "<a href='$it'>$it</a> " }).join('')
            output << '</p>'
        }
        output << "</div>\n"
    }
}
