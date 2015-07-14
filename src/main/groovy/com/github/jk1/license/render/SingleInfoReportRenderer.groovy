package com.github.jk1.license.render

import com.github.jk1.license.ModuleData

import static com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension

/**
 * Abstract class for renderers using only one license per module
 */
abstract class SingleInfoReportRenderer implements ReportRenderer {

    protected List<String> moduleLicenseInfo(LicenseReportExtension config, ModuleData data) {
        String moduleUrl = null
        String moduleLicense = null
        String moduleLicenseUrl = null

        data.manifests.each {
            if (it.url) {
                moduleUrl = it.url
            }
            if (it.license) {
                if (it.license.startsWith('http')) {
                    moduleLicenseUrl = it.license
                }
                moduleLicense = it.license

            }
        }
        data.poms.each {
            if (it.projectUrl) {
                moduleUrl = it.projectUrl
            }
            if (it.licenses) {
                it.licenses.each {
                    if (it.name) {
                        moduleLicense = it.name
                    }
                    if (it.url && it.url.startsWith('http')) {
                        moduleLicenseUrl = it.url
                    }
                }
            }
        }
        if (!moduleLicenseUrl) {
            data.licenseFiles.each {
                it.files.each {
                    def text = new File(config.outputDir, it).text
                    if (text.contains('Apache License, Version 2.0')) {
                        moduleLicense = 'Apache License, Version 2.0'
                        moduleLicenseUrl = 'http://www.apache.org/licenses/LICENSE-2.0'
                    }
                    if (text.contains('Apache Software License, Version 1.1')) {
                        moduleLicense = 'Apache Software License, Version 1.1'
                        moduleLicenseUrl = 'http://www.apache.org/licenses/LICENSE-1.1'
                    }
                    if (text.contains('CDDL')) {
                        moduleLicense = 'COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0'
                        moduleLicenseUrl = 'http://opensource.org/licenses/CDDL-1.0'
                    }
                }
            }
        }
        [moduleUrl, moduleLicense, moduleLicenseUrl]
    }
}
