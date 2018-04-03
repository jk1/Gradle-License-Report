package com.github.jk1.license.render

import com.github.jk1.license.License
import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.ModuleData

class LicenseDataCollector {

    protected static List<String> singleModuleLicenseInfo(LicenseReportPlugin.LicenseReportExtension config, ModuleData data) {
        def info = multiModuleLicenseInfo(config, data, false)
        def moduleUrl = lastOrNull(info.moduleUrls)
        def license = lastOrNull(info.licenses)
        def moduleLicense = license?.name
        def moduleLicenseUrl = license?.url

        [moduleUrl, moduleLicense, moduleLicenseUrl]
    }

    protected static MultiLicenseInfo multiModuleLicenseInfo(
        LicenseReportPlugin.LicenseReportExtension config, ModuleData data, boolean alwaysCheckLicenseFiles = true) {
        MultiLicenseInfo info = new MultiLicenseInfo()

        data.manifests.each {
            if (it.url) {
                info.moduleUrls << it.url
            }
            if (it.license) {
                if (isValidUrl(it.license)) {
                    info.licenses << new License(url: it.license)
                } else {
                    info.licenses << new License(name: it.license)
                }
            }
        }
        data.poms.each {
            if (it.projectUrl) {
                info.moduleUrls << it.projectUrl
            }
            if (it.licenses) {
                info.licenses.addAll(it.licenses)
            }
        }

        // Check just here for backward compatibility reason of "simple" json-renderer.
        // think about removing this at all and risk that the simple-renderers gets a different result in case
        // several licenses are available
        if (info.licenses.isEmpty() || !info.licenses.last().url || alwaysCheckLicenseFiles) {
            data.licenseFiles.each {
                it.fileDetails.each {
                    String moduleLicense = null
                    String moduleLicenseUrl = null
                    def text = new File(config.outputDir, it.file).text
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

                    if (moduleLicense || moduleLicenseUrl) {
                        info.licenses << new License(name: moduleLicense, url: moduleLicenseUrl)
                    }
                }
            }
        }
        info
    }

    private static boolean isValidUrl(String url) {
        try {
            new URI(url)
            return true
        } catch (URISyntaxException e) {
            return false
        }
    }

    private static def lastOrNull(List l) {
        if (l) l.last() else null
    }

    protected static class MultiLicenseInfo {
        List<String> moduleUrls = new ArrayList<>()
        List<License> licenses = new ArrayList<>()
    }
}
