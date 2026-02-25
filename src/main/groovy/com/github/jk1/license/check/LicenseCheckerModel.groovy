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

import groovy.transform.Canonical

@Canonical
class AllowedLicense {
    String moduleName, moduleVersion, moduleLicense
}

@Canonical
class Dependency {
    String moduleName, moduleVersion
    List<ModuleLicense> moduleLicenses

    Dependency(String moduleName, String moduleVersion, List moduleLicenses) {
        this.moduleName = moduleName
        this.moduleVersion = moduleVersion
        this.moduleLicenses = moduleLicenses.collect { new ModuleLicense(it.moduleLicense as String) }
    }

    Dependency(Map<String, String> dependencies) {
        this.moduleName = dependencies.moduleName
        this.moduleVersion = dependencies.moduleVersion
        this.moduleLicenses = [ new ModuleLicense(dependencies.moduleLicense) ]
    }

    @Override
    String toString() {
        return "${moduleName}${moduleVersion ? ':' + moduleVersion : ''} - ${moduleLicenses.sort {it.moduleLicense }}"
    }
}

@Canonical
class ModuleLicense {
    String moduleLicense

    @Override
    String toString() {
        return moduleLicense
    }
}
