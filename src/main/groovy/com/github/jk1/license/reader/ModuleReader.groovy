package com.github.jk1.license.reader

import com.github.jk1.license.ModuleData
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ModuleReader {

    private Logger LOGGER = Logging.getLogger(Task.class)

    private PomReader pomReader = new PomReader()
    private ManifestReader manifestReader = new ManifestReader()
    private LicenseFilesReader filesReader = new LicenseFilesReader()

    ModuleData read(Project project, ResolvedDependency dependency){
        ModuleData data = new ModuleData(dependency.moduleGroup, dependency.moduleName, dependency.moduleVersion)
        dependency.moduleArtifacts.each { ResolvedArtifact artifact ->
            LOGGER.info("Processing artifact: $artifact ($artifact.file)")
            if (artifact.file.exists()){
                data.poms << pomReader.readPomData(project, artifact)
                data.manifests << manifestReader.readManifestData(project, artifact)
                data.licenseFiles << filesReader.read(project, artifact)
            } else {
                LOGGER.info("Skipping artifact file $artifact.file as it does not exist")
            }
        }
        data.poms = data.poms - null
        data.manifests = data.manifests - null
        data.licenseFiles = data.licenseFiles - null
        data
    }
}
