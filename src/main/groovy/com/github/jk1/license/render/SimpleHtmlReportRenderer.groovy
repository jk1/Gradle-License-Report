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
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.util.Files
import org.gradle.api.Project
import org.gradle.api.tasks.Input

class SimpleHtmlReportRenderer implements ReportRenderer {

    private Project project
    private LicenseReportExtension config
    private File output
    private int counter
    private String fileName

    SimpleHtmlReportRenderer(String fileName = 'index.html') {
        this.fileName = fileName
    }

    @Input
    String getFileNameCache() { return this.fileName }

    void render(ProjectData data) {
        project = data.project
        config = project.licenseReport
        output = new File(config.absoluteOutputDir, fileName)
        output.text = """
<!DOCTYPE html>
<html lang="en">
<head>
    <title>
        Dependency License Report for $project.name
    </title>
    <meta charset="utf-8">
</head>
<body>
    <h1>
        Dependency License Report for $project.name ${if (!'unspecified'.equals(project.version)) project.version else ''}
    </h1>
"""
        printDependencies(data)
        output << """
    <hr />
        <p id="timestamp">
            This report was generated at
            <em>
                ${new Date()}
            </em>.
        </p>
</body>
</html>
"""
    }

    private void printDependencies(ProjectData data) {
        data.allDependencies.sort().each {
            printModuleDependency(it)
        }
        data.importedModules.modules.flatten().sort().each {
            printImportedModuleDependency(it)
        }
    }

    private void printModuleDependency(ModuleData data) {
        boolean projectUrlDone = false
        output << "\n    <hr />"
        output << "" +
            "\n        <p>" +
            "\n            <strong>${++counter}.</strong>"
        if (data.group) output << "" +
            "\n            <strong>Group:</strong> $data.group"
        if (data.name) output << "" +
            "\n            <strong>Name:</strong> $data.name"
        if (data.version) output << "" +
            "\n            <strong>Version:</strong> $data.version"
        output << "" +
            "\n        </p>"
        if (data.poms.isEmpty() && data.manifests.isEmpty()) {
            output << "" +
                "\n        <p>" +
                "\n            <strong>No license information found</strong>" +
                "\n        </p>"
            return
        }

        if (!data.manifests.isEmpty() && !data.poms.isEmpty()) {
            ManifestData manifest = data.manifests.first()
            PomData pomData = data.poms.first()
            if (manifest.url && pomData.projectUrl && manifest.url == pomData.projectUrl) {
                output << "" +
                    "\n        <p>" +
                    "\n            <strong>Project URL:</strong>" +
                    "\n            <code>" +
                    "\n                <a href=\"$manifest.url\">" +
                    "\n                    $manifest.url" +
                    "\n                </a>" +
                    "\n            </code>" +
                    "\n        </p>"
                projectUrlDone = true
            }
        }

        if (!data.manifests.isEmpty()) {
            ManifestData manifest = data.manifests.first()
            if (manifest.url && !projectUrlDone) {
                output << "" +
                    "\n        <p>" +
                    "\n            <strong>Manifest Project URL:</strong>" +
                    "\n            <code>" +
                    "\n                <a href=\"$manifest.url\">" +
                    "\n                    $manifest.url" +
                    "\n                </a>" +
                    "\n            </code>" +
                    "\n        </p>"
            }
            if (manifest.license) {
                if (Files.maybeLicenseUrl(manifest.licenseUrl)) {
                    output << "" +
                        "\n        <p>" +
                        "\n            <strong>Manifest license URL:</strong>" +
                        "\n            <a href=\"$manifest.licenseUrl\">" +
                        "\n                $manifest.license" +
                        "\n            </a>" +
                        "\n        </p>"
                } else if (manifest.hasPackagedLicense) {
                    output << "" +
                        "\n        <p>" +
                        "\n            <strong>Packaged License File:</strong>" +
                        "\n            <a href=\"$manifest.url\">" +
                        "\n                $manifest.license" +
                        "\n            </a>" +
                        "\n        </p>"
                } else {
                    output << "" +
                        "\n        <p>" +
                        "\n            <strong>Manifest License:</strong> $manifest.license (Not packaged)" +
                        "\n        </p>"
                }
            }
        }

        if (!data.poms.isEmpty()) {
            PomData pomData = data.poms.first()
            if (pomData.projectUrl && !projectUrlDone) {
                output << "" +
                    "\n        <p>" +
                    "\n            <strong>POM Project URL:</strong>" +
                    "\n            <code>" +
                    "\n                <a href=\"$pomData.projectUrl\">" +
                    "\n                    $pomData.projectUrl" +
                    "\n                </a>" +
                    "\n            </code>" +
                    "\n        </p>"
            }
            if (pomData.licenses) {
                pomData.licenses.each { License license ->
                    output << "" +
                        "\n        <p>" +
                        "\n            <strong>POM License: $license.name</strong>"
                    if (license.url) {
                        if (Files.maybeLicenseUrl(license.url)) {
                            output << "" +
                                "\n            - " +
                                "\n            <a href=\"$license.url\">" +
                                "\n                $license.url" +
                                "\n            </a>"
                        } else {
                            output << "" +
                                "\n        <p>" +
                                "\n            <strong>License:</strong> " +
                                "\n            $license.url" +
                                "\n        </p>"
                        }
                        output << "\n        </p>"
                    }
                }
            }
        }
        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().fileDetails.isEmpty()) {
            output << '' +
                '\n        <p>' +
                '\n            <strong>Embedded license files:</strong> '
            output << data.licenseFiles.first().fileDetails.collect({ "" +
                "\n            <a href=\"$it.file\">" +
                "\n                $it.file" +
                "\n            </a> " }).join('<br/>')
            output << '' +
                '\n        </p>'
        }
        output << '\n'
    }

    private void printImportedModuleDependency(ImportedModuleData module) {
        output << "\n    <hr />"
        output << "" +
            "\n        <p>" +
            "\n            <strong>${++counter}.</strong>"
        output << "" +
            "\n            <strong>Name:</strong> $module.name" +
            "\n            <strong>Version:</strong> $module.version"
        output << "" +
            "\n        </p>"

        if (Files.maybeLicenseUrl(module.projectUrl)) {
            output << "" +
                "\n        <p>" +
                "\n            <strong>Project URL:</strong>" +
                "\n            <code>" +
                "\n                <a href=\"$module.projectUrl\">" +
                "\n                    $module.projectUrl" +
                "\n                </a>" +
                "\n            </code>" +
                "\n        </p>"
        }

        if (Files.maybeLicenseUrl(module.licenseUrl)) {
            output << "" +
                "\n        <p>" +
                "\n            <strong>License:</strong> $module.license - " +
                "\n            <a href=\"$module.licenseUrl\">" +
                "\n                $module.licenseUrl" +
                "\n            </a>" +
                "\n        </p>"
        } else {
            output << "" +
                "\n        <p>" +
                "\n            <strong>License:</strong> $module.license" +
                "\n        </p>"
        }

        output << '\n'
    }

}
