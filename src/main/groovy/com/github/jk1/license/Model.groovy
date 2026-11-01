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
import groovy.transform.Sortable
import org.gradle.api.Project

/**
 * Configuration-cache compatible description of the Gradle project a report is generated for.
 * Replaces the live {@link Project} instance, which cannot be used at task execution time.
 */
@Canonical
class ProjectInfo implements Serializable {
    private static final long serialVersionUID = 1L

    String name
    String path
    String group
    String version
    String description

    /**
     * Report settings for the current execution. Transient, as it carries the absolute output directory which must
     * not become part of the (relocatable) task input fingerprint.
     */
    transient LicenseReportSettings settings

    static ProjectInfo of(Project project, LicenseReportSettings settings = null) {
        new ProjectInfo(
            name: project.name,
            path: project.path,
            group: String.valueOf(project.group),
            version: String.valueOf(project.version),
            description: project.description,
            settings: settings
        )
    }

    ProjectInfo withSettings(LicenseReportSettings settings) {
        new ProjectInfo(name: name, path: path, group: group, version: version, description: description, settings: settings)
    }
}

@Canonical
class ProjectData {
    ProjectInfo project
    Set<ConfigurationData> configurations = new TreeSet<ConfigurationData>()
    List<ImportedModuleBundle> importedModules = new ArrayList<ImportedModuleBundle>()
    Set<ModuleData> getAllDependencies() {
        new TreeSet<ModuleData>(configurations*.dependencies.flatten() as List<ModuleData>)
    }
    LicenseReportSettings getExtension() {
        project?.settings
    }
}

@Sortable(includes = "name")
@Canonical
class ConfigurationData {
    String name
    Set<ModuleData> dependencies = new TreeSet<ModuleData>()
    /** Coordinates ({@code group:name:version}) of the dependencies declared directly in this configuration. */
    Set<String> directDependencies = new TreeSet<String>()
}

@Sortable(includes = ["group", "name", "version"])
@Canonical
class ModuleData {
    String group, name, version
    boolean hasArtifactFile
    Set<ManifestData> manifests = new TreeSet<ManifestData>()
    Set<LicenseFileData> licenseFiles = new TreeSet<LicenseFileData>()
    Set<PomData> poms = new TreeSet<PomData>()

    boolean isEmpty() { manifests.isEmpty() && poms.isEmpty() && licenseFiles.isEmpty() }
}

@Sortable(includes = ["name", "version"])
@Canonical
class ManifestData {
    String name, version, description, vendor, url
    Set<License> licenses = new TreeSet<License>()
    boolean hasPackagedLicense

    /** @deprecated Since 3.1.0, for removal. Use {@link #licenses} instead. Returns the name of the first license for backward compatibility. */
    @Deprecated String getLicense() { licenses.find { true }?.name }
    /** @deprecated Since 3.1.0, for removal. Use {@link #licenses} instead. Returns the URL of the first license for backward compatibility. */
    @Deprecated String getLicenseUrl() { licenses.find { true }?.url }
}

@Canonical
@Sortable(includes = ["name", "description", "projectUrl", "inceptionYear"])
class PomData {
    String name, description, projectUrl, inceptionYear
    Set<License> licenses = new TreeSet<License>()
    PomOrganization organization
    Set<PomDeveloper> developers = new TreeSet<PomDeveloper>()
}

@Sortable
@Canonical
class PomOrganization {
    String name, url
}

@Sortable
@Canonical
class PomDeveloper {
    String name, email, url
}

@Sortable(includes = "name")
@Canonical
class License {
    String name, url
}

@Canonical
@Sortable(excludes = "fileDetails")
class LicenseFileData {
    Collection<LicenseFileDetails> fileDetails = new TreeSet<LicenseFileDetails>()
}

@Sortable
@Canonical
class LicenseFileDetails {
    String file
    String license
    String licenseUrl
}

@Canonical
class ImportedModuleBundle {
    String name
    Collection<ImportedModuleData> modules = new TreeSet<ImportedModuleData>()
}

@Sortable
@Canonical
class ImportedModuleData {
    String name
    String version
    String projectUrl
    String license
    String licenseUrl
}
