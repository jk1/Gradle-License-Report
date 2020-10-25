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

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import org.gradle.api.tasks.Input

/**
 * Simple CSV license report renderer
 * <br/>
 * "my.super:module:1.0","http://www.mydomain.com","myLicense","http://www.mydomain.com/mylicense.html",
 *
 * <br/>
 * <br/>
 * Setting
 * <br/>
 * licenseReport.renderers = [new com.github.jk1.license.render.CsvReportRenderer()]
 * <br/>
 * licenseReport.renderer.quote = "'"
 * <br/>
 * licenseReport.renderer.separator = ";"
 * <br/>
 * will produce
 * <br/>
 * 'my.super:module:1.0';'http://www.mydomain.com';'myLicense';'http://www.mydomain.com/mylicense.html';
 * <br/>
 * <br/>
 * by default:
 * <br/>
 * String filename = 'licenses.csv'<br/>
 * boolean includeHeaderLine = true<br/>
 * String quote = '\"' <br/>
 * String separator = ','<br/>
 * String nl = '\r\n'<br/>
 */
class CsvReportRenderer implements ReportRenderer {

    String filename
    boolean includeHeaderLine = true

    String quote = '\"'
    String separator = ','
    String nl = '\r\n'

    CsvReportRenderer(String filename = 'licenses.csv') {
        this.filename = filename
    }

    @Input
    String getFileNameCache() { return this.filename }

    @Override
    void render(ProjectData data) {
        LicenseReportExtension config = data.project.licenseReport
        File output = new File(config.outputDir, filename)
        output.write('')

        if (includeHeaderLine) {
            output << "${quote('artifact')}$separator${quote('moduleUrl')}$separator${quote('moduleLicense')}$separator${quote('moduleLicenseUrl')}$separator$nl"
        }

        data.allDependencies.sort().each {
            renderDependency(output, it)
        }
    }

    void renderDependency(File output, ModuleData data) {
        def (String moduleUrl, String moduleLicense, String moduleLicenseUrl) = LicenseDataCollector.singleModuleLicenseInfo(data)

        String artifact = "${data.group}:${data.name}:${data.version}"
        output << "${quote(artifact)}$separator${quote(moduleUrl)}$separator${quote(moduleLicense)}$separator${quote(moduleLicenseUrl)}$separator$nl"
    }

    private String quote(String content) {
        if (content == null || content.isEmpty()) {
            return ''
        }
        content = content.trim()
        content = content.replaceAll(quote, "\\\\$quote")
        "${quote}${content}${quote}"
    }
}
