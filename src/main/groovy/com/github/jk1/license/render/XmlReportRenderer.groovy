package com.github.jk1.license.render

import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import org.gradle.api.Project

/**
 * Renders dependency report in the following XML notation:
 *
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE topic SYSTEM "http://helpserver.labs.intellij.net/help/html-entities.dtd">
 * <topic xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://helpserver.labs.intellij.net/help/topic.v2.xsd"  id="third-party-libs">
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
class XmlReportRenderer extends SingleInfoReportRenderer {

    private String fileName = 'index.xml'
    private String chapterName = 'Third-party libraries'
    private Project project
    private LicenseReportExtension config
    private File output

    public XmlReportRenderer() {
    }

    XmlReportRenderer(String fileName, String chapterName) {
        this.fileName = fileName
        this.chapterName = chapterName
    }

    def void render(ProjectData data) {
        project = data.project
        config = project.licenseReport
        output = new File(config.outputDir, fileName)
        output.text = '<?xml version="1.0" encoding="UTF-8"?>\n'
        output << '<!DOCTYPE topic SYSTEM "http://helpserver.labs.intellij.net/help/html-entities.dtd">\n'
        output << '<topic xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://helpserver.labs.intellij.net/help/topic.v2.xsd"  id="third-party-libs">\n'
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

    private def void printDependency(ModuleData data) {
        def moduleName = "${data.group}:${data.name}"
        def moduleVersion = data.version
        def (String moduleUrl, String moduleLicense, String moduleLicenseUrl) = moduleLicenseInfo(config, data)

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

    private def void printImportedModule(ImportedModuleData data) {
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
