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
package com.github.jk1.license

import groovy.transform.Canonical
import org.gradle.api.Project

@Canonical
class ProjectData {
    Project project
    Set<ConfigurationData> configurations = new HashSet<ConfigurationData>()
    List<ImportedModuleBundle> importedModules = new ArrayList<ImportedModuleBundle>()
    Set<ModuleData> getAllDependencies() {
        new HashSet<ModuleData>(configurations.collect { it.dependencies }.flatten())
    }
}

@Canonical
class ConfigurationData {
    String name
    Set<ModuleData> dependencies = new HashSet<ModuleData>()
}

@Canonical
class ModuleData implements Comparable<ModuleData> {
    String group, name, version
    Set<ManifestData> manifests = new HashSet<ManifestData>()
    Set<LicenseFileData> licenseFiles = new HashSet<LicenseFileData>()
    Set<PomData> poms = new HashSet<PomData>()

    boolean isEmpty() { manifests.isEmpty() && poms.isEmpty() && licenseFiles.isEmpty() }

    @Override
    int compareTo(ModuleData o) {
        group <=> o.group ?: name <=> o.name ?: version <=> o.version
    }
}

@Canonical
class ManifestData {
    String name, version, description, vendor, license, url
    boolean hasPackagedLicense
}

@Canonical
class PomData {
    String name, description, projectUrl, inceptionYear
    Set<License> licenses = new HashSet<License>()
    PomOrganization organization
    Set<PomDeveloper> developers
}

@Canonical
class PomOrganization {
    String name, url
}

@Canonical
class PomDeveloper {
    String name, email, url
}

@Canonical
class License {
    String name, url, distribution, comments

    @Override
    boolean equals(Object other) {
        name == other.name
    }
}

@Canonical
class LicenseFileData {

    /**
     * @Deprecated Use #fileDetails instead. This will be removed in the future
     */
    @Deprecated
    Collection<String> files = []

    Collection<LicenseFileDetails> fileDetails = []
}

@Canonical
class LicenseFileDetails {
    String file
    String license
    String licenseUrl
}

@Canonical
class ImportedModuleBundle {
    String name
    Collection<ImportedModuleData> modules = new HashSet<ImportedModuleData>()
}

@Canonical
class ImportedModuleData {
    String name
    String version
    String projectUrl
    String license
    String licenseUrl
}
