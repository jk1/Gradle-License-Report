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
import org.gradle.api.InvalidUserDataException

class LicenseCheckerFileReader {

    static List<AllowedLicense> importAllowedLicenses(Object allowedLicensesFile) {
        def slurpResult
        if(allowedLicensesFile instanceof File ) {
            slurpResult = new JsonSlurper().setType(JsonParserType.LAX).parse(allowedLicensesFile)
        } else if(allowedLicensesFile instanceof URL) {
            slurpResult = new JsonSlurper().setType(JsonParserType.LAX).parse(allowedLicensesFile)
        } else if(allowedLicensesFile instanceof String) {
            def source
            try {
                source = new URL(allowedLicensesFile)
            } catch (MalformedURLException ignored) {
                source = new File(allowedLicensesFile)
            }
            return importAllowedLicenses(source)
        } else {
            throw new InvalidUserDataException("Unknown type for allowedLicensesFile: " + allowedLicensesFile.getClass())
        }
        return slurpResult.allowedLicenses.collect { new AllowedLicense(it.moduleName, it.moduleVersion, it.moduleLicense) }
    }

    static List<Dependency> importDependencies(File projectDependenciesFile) {
        def slurpResult = new JsonSlurper().setType(JsonParserType.LAX).parse(projectDependenciesFile)
        def allDependencies = slurpResult.dependencies.collect { new Dependency(it.moduleName, it.moduleVersion, it.moduleLicenses) }
        def importedDependencies = slurpResult.importedModules.collect { it.dependencies.collect { new Dependency(it) } }.flatten()
        allDependencies.findAll { it.moduleLicenses.isEmpty() }.collect {
            def importedDependency = importedDependencies.find { importedDep ->
                importedDep.moduleName == it.moduleName
                        && importedDep.moduleVersion == it.moduleVersion
            }
            if (Objects.nonNull(importedDependency)) {
                it.moduleLicenses = importedDependency.moduleLicenses
                importedDependencies.remove(importedDependency)
            }
        }
        return allDependencies + importedDependencies
    }
}
