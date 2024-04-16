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
 * A Dependency, which has at least one license inside allowedLicenses, will pass.
 */
class OneRequiredLicenseChecker implements LicenseChecker {

    @Override
    List<Tuple2<Dependency, List<ModuleLicense>>> checkAllDependencyLicensesAreAllowed(List<AllowedLicense> allowedLicenses, List<Dependency> allDependencies) {
        List<Dependency> notPassedDependencies = allDependencies.findAll { !isDependencyHasAllowedLicense(it, allowedLicenses) }
        return notPassedDependencies.collect { new Tuple2(it, it.moduleLicenses.isEmpty() ? null : it.moduleLicenses) }
    }

    private boolean isDependencyHasAllowedLicense(Dependency dependency, List<AllowedLicense> allowedLicenses) {
        for (allowedLicense in allowedLicenses) {
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

        for (moduleLicenses in dependency.moduleLicenses)
            if (moduleLicenses.moduleLicense ==~ allowedLicense.moduleLicense ||
                    moduleLicenses.moduleLicense == allowedLicense.moduleLicense) return true
        return false
    }
}
