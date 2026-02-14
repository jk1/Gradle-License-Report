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

class LicenseChecker {
    private static final String NO_LICENSE_FOUND = ""

    void checkAllDependencyLicensesAreAllowed(
        Object allowedLicensesFile, File projectLicensesDataFile, File notPassedDependenciesOutputFile) {
        List<Dependency> allDependencies = LicenseCheckerFileReader.importDependencies(projectLicensesDataFile)
        List<AllowedLicense> allowedLicenses = LicenseCheckerFileReader.importAllowedLicenses(allowedLicensesFile)
        List<Dependency> notPassedDependencies = searchForNotAllowedDependencies(allDependencies, allowedLicenses)
        generateNotPassedDependenciesFile(notPassedDependencies, notPassedDependenciesOutputFile)

        if (!notPassedDependencies.isEmpty()) {
            throw new GradleException("Some library licenses are not allowed.\n" +
                "Read [$notPassedDependenciesOutputFile.path] for more information.")
        }
    }

    private List<Dependency> searchForNotAllowedDependencies(
        List<Dependency> dependencies, List<AllowedLicense> allowedLicenses) {
        return dependencies.findAll { !isDependencyHasAllowedLicense(it, allowedLicenses) }
    }

    private void generateNotPassedDependenciesFile(
        List<Dependency> notPassedDependencies, File notPassedDependenciesOutputFile) {
        notPassedDependenciesOutputFile.text =
            JsonOutput.prettyPrint(JsonOutput.toJson(
                ["dependenciesWithoutAllowedLicenses": notPassedDependencies.collect { toAllowedLicenseList(it) }.flatten()]))
    }

    private boolean isDependencyHasAllowedLicense(Dependency dependency, List<AllowedLicense> allowedLicenses) {
        for(allowedLicense in allowedLicenses) {
            if (isDependencyMatchesAllowedLicense(dependency, allowedLicense)) return true
        }
        return false
    }

    private boolean isDependencyMatchesAllowedLicense(Dependency dependency, AllowedLicense allowedLicense) {
        return isDependencyNameMatchesAllowedLicense(dependency, allowedLicense) &&
            isDependencyLicenseMatchesAllowedLicense(dependency, allowedLicense) &&
            isDependencyVersionMatchesAllowedLicense(dependency, allowedLicense)
    }

    private boolean isDependencyNameMatchesAllowedLicense(Dependency dependency, AllowedLicense allowedLicense) {
        return dependency.moduleName ==~ allowedLicense.moduleName || allowedLicense.moduleName == null ||
            dependency.moduleName == allowedLicense.moduleName
    }

    private boolean isDependencyVersionMatchesAllowedLicense(Dependency dependency, AllowedLicense allowedLicense) {
        return dependency.moduleVersion ==~ allowedLicense.moduleVersion || allowedLicense.moduleVersion == null ||
            dependency.moduleVersion == allowedLicense.moduleVersion
    }

    private boolean isDependencyLicenseMatchesAllowedLicense(Dependency dependency, AllowedLicense allowedLicense) {
        if (allowedLicense.moduleLicense == null || allowedLicense.moduleLicense == ".*") return true

        // Allow matching to modules with no known licenses via a blank rule
        if (!dependency.moduleLicenses && allowedLicense.moduleLicense == NO_LICENSE_FOUND) return true

        for (moduleLicenses in dependency.moduleLicenses)
            if (moduleLicenses.moduleLicense ==~ allowedLicense.moduleLicense ||
                moduleLicenses.moduleLicense == allowedLicense.moduleLicense) return true
        return false
    }

    private List<AllowedLicense> toAllowedLicenseList(Dependency dependency) {
        if (dependency.moduleLicenses.isEmpty()) {
            return [ new AllowedLicense(dependency.moduleName, dependency.moduleVersion, NO_LICENSE_FOUND) ]
        } else {
            return dependency.moduleLicenses.collect { new AllowedLicense(dependency.moduleName, dependency.moduleVersion, it.moduleLicense) }
        }
    }
}
