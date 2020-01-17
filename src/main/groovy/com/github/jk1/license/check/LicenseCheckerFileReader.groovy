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
package com.github.jk1.license.check

import groovy.json.JsonParserType
import groovy.json.JsonSlurper

class LicenseCheckerFileReader {

    static List<AllowedLicense> importAllowedLicenses(URL allowedLicensesUrl) {
        def slurpResult = new JsonSlurper().setType(JsonParserType.LAX).parse(allowedLicensesUrl)
        return slurpResult.allowedLicenses.collect { new AllowedLicense(it.moduleName, it.moduleVersion, it.moduleLicense) }
    }

    static List<Dependency> importDependencies(File projectDependenciesFile) {
        def slurpResult = new JsonSlurper().setType(JsonParserType.LAX).parse(projectDependenciesFile)
        def allDependencies = slurpResult.dependencies.collect { new Dependency(it.moduleName, it.moduleVersion, it.moduleLicenses) }
        allDependencies += slurpResult.importedModules.collect { it.dependencies.collect { new Dependency(it) } }.flatten()
        return allDependencies
    }
}
