package com.github.jk1.license

import groovy.json.JsonBuilder

import static com.github.jk1.license.ProjectDataFixture.GRADLE_PROJECT

class ProjectBuilder extends BuilderSupport {
    @Override
    protected void setParent(Object name, Object o1) { }


    @Override
    protected Object createNode(Object name) {
        switch(name) {
            case "project": return createProject()
            default: throw new IllegalArgumentException("Invalid keyword $name")
        }
    }

    @Override
    protected Object createNode(Object name, Object id) {
        switch(name) {
            case "configuration": return addConfiguration(id)
            case "module": return addModule(id)
            case "pom": return addPom(id)
            case "manifest": return addManifest(id)
            case "license": return addLicense(id, null)
            case "importedModulesBundle": return addImportedModulesBundle(id)
            default: throw new IllegalArgumentException("Invalid keyword $name")
        }
    }

    @Override
    protected Object createNode(Object name, Map map) {
        println("Create node $name - $map -- $current")
        switch(name) {
            case "license": return addLicense(null, map)
            case "importedModule": return addImportedModule(map)
            case "licenseFile": return addLicenseFiles(map)
            default: throw new IllegalArgumentException("Invalid keyword $name")
        }
    }

    @Override
    protected Object createNode(Object name, Map map, Object id) {
        println("Create node $name - $id - $map -- $current")
        switch(name) {
            case "license": return addLicense(id, map)
            default: throw new IllegalArgumentException("Invalid keyword $name")
        }
    }


    private ProjectData createProject() {
        new ProjectData(
            project: GRADLE_PROJECT()
        )
    }
    private ConfigurationData addConfiguration(String id) {
        ProjectData projectData = (ProjectData)current

        def config = new ConfigurationData(
            name: id,
            dependencies: [ ]
        )
        projectData.configurations << config
        config
    }
    private ImportedModuleBundle addImportedModulesBundle(String id) {
        ProjectData projectData = (ProjectData)current

        def bundle = new ImportedModuleBundle(
            name: id
        )

        projectData.importedModules << bundle
        return bundle
    }

    private ImportedModuleData addImportedModule(Map map) {
        ImportedModuleBundle bundle = (ImportedModuleBundle)current

        def module = new ImportedModuleData(
            name: map.name ?: "some-name",
            version: map.version ?: "some-version",
            projectUrl: map.projectUrl ?: "some-projectUrl",
            license: map.license ?: "some-license",
            licenseUrl: map.licenseUrl ?: "some-licenseUrl"
        )

        bundle.modules << module
        module
    }

    private ModuleData addModule(String id) {
        ConfigurationData configurationData = (ConfigurationData)current

        def module = new ModuleData(
            group: "dummy-group", name: id, version: "0.0.1",
            manifests: [],
            licenseFiles: [],
            poms: []
        )

        configurationData.dependencies << module
        module
    }
    private PomData addPom(String id) {
        ModuleData module = (ModuleData)current

        def pom = new PomData(
            name: id,
            description: "dummy-pom-description",
            projectUrl: "http://dummy-pom-project-url",
            inceptionYear: "dummy-pom-inception-year",
            organization: new PomOrganization(
                name: "dummy-pom-org-name", url: "http://dummy-pom-org-url"
            ),
            licenses: [],
            developers: []
        )

        module.poms << pom
        pom
    }
    private ManifestData addManifest(String id) {
        ModuleData module = (ModuleData)current

        def manifest = new ManifestData(
            name: id,
            version: "dummy-mani-version",
            description: "dummy-mani-desc",
            vendor: "dummy-mani-vendor",
            license: "Apache 2.0",
            url: "http://dummy-mani-url"
        )

        module.manifests << manifest
        manifest
    }

    private LicenseFileData addLicenseFiles(Map map) {
        ModuleData module = (ModuleData)current

        def licenseFiles = new LicenseFileData(
            files: [map.file],
            fileDetails: [new LicenseFileDetails(map)]
        )

        module.licenseFiles << licenseFiles
        licenseFiles
    }

    private def addLicense(Object license, Map map) {
        if (current instanceof PomData) {
            addPomLicense(license, map)
        } else if (current instanceof ManifestData) {
            addManifestLicense(license)
        } else {
            throw new IllegalAccessException("current must be PomData or ManifestData not $current")
        }
    }

    private License addPomLicense(Object license, Map map) {
        PomData pom = (PomData)current

        License licenseToUse

        if (license == null) {
            licenseToUse = new License()
        } else if (license instanceof License) {
            licenseToUse = cloneLicense((License)license)
        } else {
            throw new IllegalAccessException("license must be from type License. Use name: or url: to specify details")
        }

        if (map != null) {
            enhanceLicenseAboutValues(licenseToUse, map)
        }

        pom.licenses << licenseToUse
        licenseToUse
    }
    private String addManifestLicense(Object license) {
        ManifestData manifest = (ManifestData)current
        String licenseText

        if (license instanceof License) {
            licenseText = ((License)license).name
        } else if (license instanceof String) {
            licenseText = license.toString()
        } else {
            throw new IllegalArgumentException("license must be a License or a String but is $license")
        }

        manifest.license = licenseText
    }

    private static def enhanceLicenseAboutValues(License license, Map map) {
        license.name = map.name ?: license.name
        license.url = map.url ?: license.url
    }


    static License cloneLicense(License license) {
        new License(
            name: license.name,
            url: license.url,
            distribution: license.distribution,
            comments: license.comments
        )
    }

    static String json(ProjectData data) {
        def project = data.project
        data.project = null
        def str = new JsonBuilder(data).toPrettyString()
        data.project = project
        str
    }
}
