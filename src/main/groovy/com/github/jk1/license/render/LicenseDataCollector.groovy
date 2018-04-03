package com.github.jk1.license.render

import com.github.jk1.license.License
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.ModuleData

class LicenseDataCollector {

    protected static List<String> singleModuleLicenseInfo(ModuleData data) {
        def info = multiModuleLicenseInfo(data)
        def moduleUrl = lastOrNull(info.moduleUrls)
        def license = lastOrNull(info.licenses)
        def moduleLicense = license?.name
        def moduleLicenseUrl = license?.url

        [moduleUrl, moduleLicense, moduleLicenseUrl]
    }

    protected static MultiLicenseInfo multiModuleLicenseInfo(ModuleData data) {
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

        data.licenseFiles*.fileDetails.flatten().each { LicenseFileDetails details ->
            if (details.license || details.licenseUrl) {
                info.licenses << new License(name: details.license, url: details.licenseUrl)
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
