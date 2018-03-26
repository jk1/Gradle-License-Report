package com.github.jk1.license

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class ProjectDataFixture {
    private static Project project = null
    static def GRADLE_PROJECT() {
        if (project == null) {
            project = ProjectBuilder.builder().withName("my-project").build()
            project.pluginManager.apply 'com.github.jk1.dependency-license-report'
        }
        project
    }

    static PomDeveloper POM_DEV_JODA() {
        new PomDeveloper(name: "joda", email: "joda@star.wars", url: "http://joda")
    }
    static PomDeveloper POM_DEV_LUKE() {
        new PomDeveloper(name: "luke", email: "luke@star.wars", url: "http://luke")
    }
    static License APACHE2_LICENSE() {
        new License(
            name: "Apache License, Version 2.0",
            url: "https://www.apache.org/licenses/LICENSE-2.0",
            distribution: "repo",
            comments: "A business-friendly OSS license"
        )
    }
    static License MIT_LICENSE() {
        new License(
            name: "MIT License",
            url: "https://opensource.org/licenses/MIT",
            distribution: "repo",
            comments: "A short and simple permissive license"
        )
    }
    static License LGPL_LICENSE() {
        new License(
            name: "GNU LESSER GENERAL PUBLIC LICENSE, Version 3",
            url: "https://www.gnu.org/licenses/lgpl-3.0",
            distribution: "repo",
            comments: "A weak copyleft license"
        )
    }

    static ManifestData MANIFEST_APACHE2_BY_NAME() {
        new ManifestData(
            name: "dummy-mani1-name",
            version: "dummy-mani1-version",
            description: "dummy-mani1-desc",
            vendor: "dummy-mani1-vendor",
            license: "Apache 2.0",
            url: "http://dummy-mani1-url")
    }
    static ManifestData MANIFEST_APACHE2_BY_URL() {
        new ManifestData(
            name: "dummy-mani2-name",
            version: "dummy-mani2-version",
            description: "dummy-mani2-desc",
            vendor: "dummy-mani2-vendor",
            license: "http://www.apache.org/licenses/LICENSE-2.0.txt",
            url: "http://dummy-mani2-url"
        )
    }
    static ManifestData MANIFEST_MIT_BY_NAME() {
        new ManifestData(
            name: "dummy-mani3-name",
            version: "dummy-mani3-version",
            description: "dummy-mani3-desc",
            vendor: "dummy-mani3-vendor",
            license: "The MIT License",
            url: "http://dummy-mani3-url"
        )
    }
    static PomData POM_APACHE2() {
        new PomData(
            name: "dummy-pom2-name",
            description: "dummy-pom2-description",
            projectUrl: "http://dummy-pom2-project-url",
            inceptionYear: "dummy-pom2-inception-year",
            organization: new PomOrganization(
                name: "dummy-pom2-org-name", url: "http://dummy-pom2-org-url"
            ),
            licenses: [APACHE2_LICENSE()],
            developers: [POM_DEV_JODA()]
        )
    }
    static PomData POM_APACHE2_MIT() {
        new PomData(
            name: "dummy-pom1-name",
            description: "dummy-pom1-description",
            projectUrl: "http://dummy-pom1-project-url",
            inceptionYear: "dummy-pom1-inception-year",
            organization: new PomOrganization(
                name: "dummy-pom1-org-name", url: "http://dummy-pom1-org-url"
            ),
            licenses: [APACHE2_LICENSE(), MIT_LICENSE()],
            developers: [POM_DEV_JODA(), POM_DEV_LUKE()]
        )
    }
    static PomData POM_APACHT2_LGPL() {
        new PomData(
            name: "foo-parent-pom-name",
            description: "foo-parent-pom-description",
            projectUrl: "http://foo-parent-pom-project-url",
            inceptionYear: "foo-parent-pom-inception-year",
            organization: new PomOrganization(
                name: "foo-pom-org-name", url: "http://foo-pom-org-url"
            ),
            licenses: [APACHE2_LICENSE(), LGPL_LICENSE()],
            developers: [POM_DEV_JODA()]
        )
    }

    static LicenseFileData LICENSE_FILE_APACHE2() {
        new LicenseFileData(
            files: ["apache2-license.txt"]
        )
    }

    static def PROJECT_DATA_TWO_MODULES_AND_IMPORTED_MODULES() {
        new ProjectData(
            project: GRADLE_PROJECT(),
            configurations: [
                new ConfigurationData(
                    name: "runtime",
                    dependencies: [
                        moduleDataWith(
                            poms: [POM_APACHE2()],
                            licenseFiles: [LICENSE_FILE_APACHE2()],
                            manifests: [MANIFEST_APACHE2_BY_NAME()]),
                        moduleDataWith(
                            poms: [POM_APACHE2(), POM_APACHE2_MIT(), POM_APACHE2_MIT()],
                            licenseFiles: [LICENSE_FILE_APACHE2()],
                            manifests: [MANIFEST_APACHE2_BY_NAME()])
                    ]
                )
            ],
            importedModules: [
                new ImportedModuleBundle(
                    name: "foo-module-bundle-name",
                    modules: [
                        new ImportedModuleData(
                            name: "foo-module-data-name",
                            version: "foo-module-data-version",
                            projectUrl: "http://foo-module-data-url",
                            license: "foo-module-data-license",
                            licenseUrl: "http://foo-module-data-license-url"
                        )
                    ]
                )
            ]
        )
    }

    static ProjectData projectDataWith(Map map = [poms: [], licenseFiles: [], manifests: []])
    {
        return new ProjectData(
            project: GRADLE_PROJECT(),
            configurations: [
                new ConfigurationData(
                    name: "runtime",
                    dependencies: [
                        moduleDataWith(map)
                    ]
                )
            ]
        )
    }

    static ModuleData moduleDataWith(Map map = [poms: [], licenseFiles: [], manifests: []])
    {
        return new ModuleData(
            group: "dummy1-group", name: "dummy1-name", version: "0.0.1",
            manifests: map.manifests ?: [],
            licenseFiles: map.licenseFiles ?: [],
            poms: map.poms ?: []
        )
    }
}
