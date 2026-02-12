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

import groovy.json.JsonOutput
import org.gradle.api.GradleException

/**
 * This class compares the found licences with the allowed licenses and creates a report for any missing license
 */
interface LicenseChecker extends Serializable {
    List<Tuple2<Dependency, List<ModuleLicense>>> checkAllDependencyLicensesAreAllowed(
            List<AllowedLicense> allowedLicenses,
            List<Dependency> allDependencies)

    default void checkAllDependencyLicensesAreAllowed(
            Object allowedLicensesFile, File projectLicensesDataFile, File notPassedDependenciesOutputFile) {
        def notPassedDependencies = checkAllDependencyLicensesAreAllowed(
                parseAllowedLicenseFile(allowedLicensesFile), getProjectDependencies(projectLicensesDataFile))

        generateNotPassedDependenciesFile(notPassedDependencies, notPassedDependenciesOutputFile)
        if (!notPassedDependencies.isEmpty()) {
            throw new GradleException("Some library licenses are not allowed:\n" +
                    "$notPassedDependenciesOutputFile.text\n\n" +
                    "Read [$notPassedDependenciesOutputFile.path] for more information.")
        }
    }

    default List<AllowedLicense> parseAllowedLicenseFile(Object allowedLicenseFile) {
        return LicenseCheckerFileReader.importAllowedLicenses(allowedLicenseFile)
    }

    default List<Dependency> getProjectDependencies(File depenenciesFile) {
        return LicenseCheckerFileReader.importDependencies(depenenciesFile)
    }


    default void generateNotPassedDependenciesFile(List<Tuple2<Dependency, List<ModuleLicense>>> notPassedDependencies, File notPassedDependenciesOutputFile) {
        notPassedDependenciesOutputFile.text = JsonOutput.prettyPrint(
                JsonOutput.toJson([
                        "dependenciesWithoutAllowedLicenses": notPassedDependencies.collect {
                            toAllowedLicenseList(it.getV1(), it.getV2())
                        }.flatten()
                ]))
    }

    default List<AllowedLicense> toAllowedLicenseList(Dependency dependency, List<ModuleLicense> moduleLicenses) {
        if (moduleLicenses.isEmpty()) {
            return [new AllowedLicense(dependency.moduleName, dependency.moduleVersion, null)]
        } else {
            return moduleLicenses.findAll { it }.collect { new AllowedLicense(dependency.moduleName, dependency.moduleVersion, it.moduleLicense) }
        }
    }
}
