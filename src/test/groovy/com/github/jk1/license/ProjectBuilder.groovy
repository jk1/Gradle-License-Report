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

import com.github.jk1.license.util.Files
import groovy.json.JsonBuilder

import static com.github.jk1.license.ProjectDataFixture.GRADLE_PROJECT

class ProjectBuilder extends BuilderSupport {

    @Override
    protected void setParent(Object name, Object o1) { }

    @Override
    protected Object createNode(Object name) {
        switch(name) {
            case "project": return createProject()
            case "licenseFiles": return addLicenseFiles()
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
        switch(name) {
            case "license": return addLicense(null, map)
            case "importedModule": return addImportedModule(map)
            case "licenseFileDetails": return addLicenseFileDetails(map)
            default: throw new IllegalArgumentException("Invalid keyword $name")
        }
    }

    @Override
    protected Object createNode(Object name, Map map, Object id) {
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

    // allows to create multiple configurations with only defining the child-structure once.
    def configurations(List<String> configNames, Closure<ConfigurationData> block) {
        configNames.forEach { name ->
            block.delegate = this
            block(name)
        }
    }

    private ConfigurationData addConfiguration(String id) {
        ProjectData projectData = (ProjectData)current

        def config = new ConfigurationData(
            name: id
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
            group: "dummy-group", name: id, version: "0.0.1"
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
            )
        )

        module.poms << pom
        pom
    }

    private ManifestData addManifest(String id, String url = "http://dummy-mani-url") {
        ModuleData module = (ModuleData)current

        def manifest = new ManifestData(
            name: id,
            version: "dummy-mani-version",
            description: "dummy-mani-desc",
            vendor: "dummy-mani-vendor",
            license: "Apache 2.0",
            url: url
        )

        module.manifests << manifest
        manifest
    }

    private LicenseFileData addLicenseFiles() {
        ModuleData module = (ModuleData)current

        def licenseFiles = new LicenseFileData()

        module.licenseFiles << licenseFiles
        licenseFiles
    }

    private LicenseFileDetails addLicenseFileDetails(Map map) {
        LicenseFileData licenseFileData = (LicenseFileData)current

        def details = new LicenseFileDetails(map)

        licenseFileData.fileDetails << details
        details
    }

    private def addLicense(Object license, Map map) {
        if (current instanceof PomData) {
            addPomLicense(license, map)
        } else if (current instanceof ManifestData) {
            if (license != null)
                addManifestLicense(license)
            else {
                if (map['name'])
                    current.license = map['name']
                if (map['url'])
                    current.licenseUrl = map['url']
            }
        } else {
            throw new IllegalAccessException("current must be PomData or ManifestData not $current")
        }
    }

    private def setHasArtifactFile(boolean value) {
        current.hasArtifactFile = value
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
        if (Files.maybeLicenseUrl(licenseText)) {
            manifest.license = null // initialized to "Apache 2.0" above
            manifest.licenseUrl = licenseText
        }
        else {
            manifest.license = licenseText
        }
    }

    private static def enhanceLicenseAboutValues(License license, Map map) {
        license.name = map.name ?: license.name
        license.url = map.url ?: license.url
    }


    static License cloneLicense(License license) {
        new License(
            name: license.name,
            url: license.url
        )
    }

    static String json(ProjectData data) {
        def configurationsString = new JsonBuilder(data.configurations).toPrettyString()
        def importedModulesString = new JsonBuilder(data.importedModules).toPrettyString()

        """{
"configurations": [
$configurationsString
],
"importedModules": [
        $importedModulesString
    ]
}"""
    }

    static String json(Object data) {
        new JsonBuilder(data).toPrettyString()
    }
}
