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
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import org.gradle.api.Project
import org.gradle.api.tasks.Input

/**
 * Renders dependency report in the following XML notation:
 *
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE topic SYSTEM "html-entities.dtd">
 * <topic xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="topic.v2.xsd"  id="third-party-libs" title="Third Party Libraries">
 *   <chapter title="Libraries" id="Libs">
 *     <table>
 *       <tr>
 *         <td>Project</td>
 *         <td>Version</td>
 *         <td>License</td>
 *       </tr>
 *       <tr>
 *         <td><a href="http://commons.apache.org/exec/">commons-exec</a></td>
 *         <td>1.1</td>
 *         <td><a href="http://www.apache.org/licenses/LICENSE-2.0">Apache-2.0</a></td>
 *       </tr>
 *     </table>
 *   </chapter>
 * </topic>
 */
class XmlReportRenderer implements ReportRenderer {

    private String fileName
    private String chapterName
    private Project project
    private LicenseReportExtension config
    private File output
    private String schemaBaseUrl

    XmlReportRenderer(String fileName = 'index.xml', String chapterName = 'Third-party libraries', String schemaBaseUrl="") {
        this.schemaBaseUrl = schemaBaseUrl
        this.fileName = fileName
        this.chapterName = chapterName
    }

    @Input
    String getFileNameCache() { return this.fileName }

    @Input
    String getChapterNameCache() { return this.chapterName }

    void render(ProjectData data) {
        project = data.project
        config = project.licenseReport
        output = new File(config.absoluteOutputDir, fileName)
        output.text = '<?xml version="1.0" encoding="UTF-8"?>\n'
        output << '<!DOCTYPE topic SYSTEM "' + schemaBaseUrl + 'html-entities.dtd">\n'
        output << '<topic xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="' + schemaBaseUrl + 'topic.v2.xsd"  id="third-party-libs" title="Third Party Libraries">\n'
        output << '<chunk include-id="third-party-libs-tables">\n'
        output << "<chapter title=\"$chapterName\" id=\"${chapterName.replaceAll(' ', '_')}\">\n"
        output << '<table>\n'
        output << '<tr><td>Project</td><td>Version</td><td>License</td></tr>\n'
        data.allDependencies.sort().each {
            printDependency(it)
        }
        output << '</table>\n'
        output << '</chapter>\n'
        data.importedModules.each {
            output << "<chapter title=\"$it.name\" id=\"${it.name.replaceAll(' ', '_')}\">\n"
            output << '<table>\n'
            output << '<tr><td>Project</td><td>Version</td><td>License</td></tr>\n'
            it.modules.each { printImportedModule(it) }
            output << '</table>\n'
            output << '</chapter>\n'
        }
        output << '</chunk>\n'
        output << '</topic>'
    }

    private void printDependency(ModuleData data) {
        def moduleName = "${data.group}:${data.name}"
        def moduleVersion = data.version
        def (String moduleUrl, String moduleLicense, String moduleLicenseUrl) = LicenseDataCollector.singleModuleLicenseInfo(data)

        output << "<tr>\n"
        if (moduleUrl) {
            output << "<td><a href='$moduleUrl'>$moduleName</a></td>\n"
        } else {
            output << "<td>$moduleName</td>\n"
        }
        output << "<td>$moduleVersion</td>\n"
        if (moduleLicense) {
            if (moduleLicenseUrl) {
                output << "<td><a href='$moduleLicenseUrl'>$moduleLicense</a></td>\n"
            } else {
                output << "<td>$moduleLicense</td>\n"
            }
        } else {
            output << '<td>No license information found</td>\n'
        }

        output << "</tr>\n"
    }

    private void printImportedModule(ImportedModuleData data) {
        output << "<tr>\n"
        if (data.projectUrl) {
            output << "<td><a href='$data.projectUrl'>$data.name</a></td>\n"
        } else {
            output << "<td>$data.name</td>\n"
        }
        output << "<td>$data.version</td>\n"
        if (data.license) {
            if (data.licenseUrl) {
                output << "<td><a href='$data.licenseUrl'>$data.license</a></td>\n"
            } else {
                output << "<td>$data.license</td>\n"
            }
        } else {
            output << '<td>No license information found</td>\n'
        }

        output << "</tr>\n"
    }

}
