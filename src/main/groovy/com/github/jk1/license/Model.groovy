package com.github.jk1.license

import groovy.transform.Canonical
import org.gradle.api.Project

@Canonical
class ProjectData {
    Project project
    Set<ConfigurationData> configurations = new HashSet<ConfigurationData>()
}

@Canonical
class ConfigurationData {
    String name
    Set<ModuleData> dependencies = new HashSet<ModuleData>()
}

@Canonical
class ModuleData {
    String group, name, version
    Set<ManifestData> manifests = new HashSet<ManifestData>()
    Set<LicenseFileData> licenseFiles = new HashSet<LicenseFileData>()
    Set<PomData> poms = new HashSet<PomData>()

    boolean isEmpty() { manifests.isEmpty() && poms.isEmpty() && licenseFiles.isEmpty()}
}

@Canonical
class ManifestData {
    String name, version, description, vendor, license, url
    boolean hasPackagedLicense
}

@Canonical
class PomData {
    String name, description, projectUrl
    Set<License> licenses =  new HashSet<License>()
}

@Canonical
class License {
    String name, url, distribution, comments

    @Override
    boolean equals(Object other) {
        name.equals(other.name)
    }
}

@Canonical
class LicenseFileData {
    Collection<String> files = []
}
