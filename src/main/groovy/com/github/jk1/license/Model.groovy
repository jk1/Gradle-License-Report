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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.dsl.ScriptHandler

@Canonical
class GradleProject {
    String name
    ConfigurationContainer configurations
    DependencyHandler dependencies

    static GradleProject ofProject(Project project) {
        return new GradleProject(project.getName(), project.getConfigurations(), project.getDependencies());
    }

    static GradleProject ofScript(Project project) {
        return new GradleProject(project.name + "/buildScript", project.buildscript.getConfigurations(), project.buildscript.getDependencies());
    }
}

@Canonical
class ProjectData {
    Project project
    Set<ConfigurationData> configurations = new TreeSet<ConfigurationData>()
    List<ImportedModuleBundle> importedModules = new ArrayList<ImportedModuleBundle>()
    Set<ModuleData> getAllDependencies() {
        new TreeSet<ModuleData>(configurations*.dependencies.flatten())
    }
}

@Sortable(includes = "name")
@Canonical
class ConfigurationData {
    String name
    Set<ModuleData> dependencies = new TreeSet<ModuleData>()
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

@Sortable
@Canonical
class ManifestData {
    String name, version, description, vendor, url, license, licenseUrl
    boolean hasPackagedLicense
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
