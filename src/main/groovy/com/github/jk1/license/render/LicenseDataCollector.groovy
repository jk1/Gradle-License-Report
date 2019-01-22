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
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.ModuleData
import com.github.jk1.license.util.Urls

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

        data.poms.each {
            if (it.projectUrl) {
                info.moduleUrls << it.projectUrl
            }
            if (it.licenses) {
                info.licenses.addAll(it.licenses)
            }
        }

        data.manifests.each { manifest ->
            if (manifest.url) {
                info.moduleUrls << manifest.url
            }
            if (manifest.license) {
                if (Urls.isValidUrl(manifest.license)) {
                    if (!info.licenses.find { it.url == manifest.license })
                        info.licenses << new License(url: manifest.license)
                } else {
                    if (!info.licenses.find { it.name == manifest.license })
                        info.licenses << new License(name: manifest.license)
                }
            }
        }

        data.licenseFiles*.fileDetails.flatten().each { LicenseFileDetails details ->
            if (!info.licenses.find { it.name == details.license }) {
                if (details.license || details.licenseUrl) {
                    info.licenses << new License(name: details.license, url: details.licenseUrl)
                }
            }
        }
        info
    }

    private static def lastOrNull(Collection l) {
        if (l) l.last() else null
    }

    protected static class MultiLicenseInfo {
        Set<String> moduleUrls = new TreeSet<>()
        Set<License> licenses = new TreeSet<>({
            o1, o2 -> o1.name <=> o2.name ?: o1.url <=> o2.url
        })
    }
}
