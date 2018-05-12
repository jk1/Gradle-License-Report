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

import com.github.jk1.license.License
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData
import org.gradle.api.Project

class SimpleHtmlReportRenderer implements ReportRenderer {

    private Project project
    private LicenseReportExtension config
    private File output
    private int counter
    private String fileName

    SimpleHtmlReportRenderer(String fileName = 'index.html') {
        this.fileName = fileName
    }

    void render(ProjectData data) {
        project = data.project
        config = project.licenseReport
        output = new File(config.outputDir, fileName)
        output.text = """
<html>
<head>
<title>Dependency License Report for $project.name</title>
<head>
<body>
<h1>Dependency License Report for $project.name ${if (!'unspecified'.equals(project.version)) project.version else ''}</h1>
"""
        printDependencies(data)
        output << """
<hr />
<p id="timestamp">This report was generated at <em>${new Date()}</em>.</p>
</body>
</html>
"""
    }

    private void printDependencies(ProjectData data) {
        data.allDependencies.sort().each {
            printDependency(it)
        }
    }

    private String printDependency(ModuleData data) {
        boolean projectUrlDone = false
        output << "<hr />"
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
                output << "<p><strong>Project URL:</strong> <code><a href=\"$manifest.url\">$manifest.url</a></code></p>"
                projectUrlDone = true
            }
        }

        if (!data.manifests.isEmpty()) {
            ManifestData manifest = data.manifests.first()
            if (manifest.url && !projectUrlDone) {
                output << "<p><strong>Manifest Project URL:</strong> <code><a href=\"$manifest.url\">$manifest.url</a></code></p>"
            }
            if (manifest.license) {
                if (manifest.license.startsWith("http")) {
                    output << "<p><strong>Manifest license URL:</strong> <a href=\"$manifest.license\">$manifest.license</a></p>"
                } else if (manifest.hasPackagedLicense) {
                    output << "<p><strong>Packaged License File:</strong> <a href=\"$manifest.url\">$manifest.license</a></p>"
                } else {
                    output << "<p><strong>Manifest License:</strong> $manifest.license (Not packaged)</p>"
                }
            }
        }

        if (!data.poms.isEmpty()) {
            PomData pomData = data.poms.first()
            if (pomData.projectUrl && !projectUrlDone) {
                output << "<p><strong>POM Project URL:</strong> <code><a href=\"$pomData.projectUrl\">$pomData.projectUrl</a></code></p>"
            }
            if (pomData.licenses) {
                pomData.licenses.each { License license ->
                    output << "<p><strong>POM License: $license.name</strong>"
                    if (license.url) {
                        if (license.url.startsWith("http")) {
                            output << " - <a href=\"$license.url\">$license.url</a>"
                        } else {
                            output << "<p><strong>License:</strong> $license.url</p>"
                        }
                    }
                }
            }
        }
        if (!data.licenseFiles.isEmpty() && !data.licenseFiles.first().fileDetails.isEmpty()) {
            output << '<p><strong>Embedded license files:</strong> '
            output << data.licenseFiles.first().fileDetails.collect({ "<a href=\"$it.file\">$it.file</a> " }).join('')
            output << '</p>'
        }
    }
}
