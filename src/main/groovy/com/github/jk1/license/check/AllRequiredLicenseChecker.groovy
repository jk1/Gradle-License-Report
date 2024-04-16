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

/**
 * All licenses of a dependency must be found inside allowedLicenses to pass.
 */
class AllRequiredLicenseChecker implements LicenseChecker {
    @Override
    List<Tuple2<Dependency, List<ModuleLicense>>> checkAllDependencyLicensesAreAllowed(List<AllowedLicense> allowedLicenses, List<Dependency> allDependencies) {
        removeNullLicenses(allDependencies)
        List<Tuple2<Dependency, List<ModuleLicense>>> result = new ArrayList<>()
        for (Dependency dependency : (allDependencies)) {
            List<AllowedLicense> perDependencyAllowedLicenses = allowedLicenses.findAll { isDependencyNameMatchesAllowedLicense(dependency, it) && isDependencyVersionMatchesAllowedLicense(dependency, it) }
            // allowedLicense matches anything, so we don't need to further check
            if (perDependencyAllowedLicenses.any { it.moduleLicense == null || it.moduleLicense == ".*" }) {
                continue
            }
            List<ModuleLicense> notAllowedLicenses = dependency.moduleLicenses.findAll { !isDependencyLicenseMatchesAllowedLicense(it, perDependencyAllowedLicenses) }
            if (!notAllowedLicenses.isEmpty()) {
                result.add(new Tuple2(dependency, notAllowedLicenses))
            }
        }
        return result
    }

    private static boolean isDependencyNameMatchesAllowedLicense(Dependency dependency, AllowedLicense allowedLicense) {
        return dependency.moduleName ==~ allowedLicense.moduleName || allowedLicense.moduleName == null || dependency.moduleName == allowedLicense.moduleName
    }

    private static boolean isDependencyVersionMatchesAllowedLicense(Dependency dependency, AllowedLicense allowedLicense) {
        return dependency.moduleVersion ==~ allowedLicense.moduleVersion || allowedLicense.moduleVersion == null || dependency.moduleVersion == allowedLicense.moduleVersion
    }

    private static boolean isDependencyLicenseMatchesAllowedLicense(ModuleLicense moduleLicense, List<AllowedLicense> allowedLicenses) {
        for (AllowedLicense allowedLicense : allowedLicenses) {
            if (allowedLicense.moduleLicense == null || allowedLicense.moduleLicense == ".*") return true

            if (moduleLicense.moduleLicense ==~ allowedLicense.moduleLicense || moduleLicense.moduleLicense == allowedLicense.moduleLicense) return true
        }
        return false
    }

    /**
     * removes 'null'-licenses from dependencies which have at least one more license
     */
    private static void removeNullLicenses(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            if (dependency.moduleLicenses.any { it.moduleLicense == null } && !dependency.moduleLicenses.every {
                it.moduleLicense == null
            }) {
                dependency.moduleLicenses = dependency.moduleLicenses.findAll { it.moduleLicense != null }
            }
        }
    }
}
