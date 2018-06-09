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
package com.github.jk1.license.reader

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ReportTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ModuleReader {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private PomReader pomReader = new PomReader()
    private ManifestReader manifestReader = new ManifestReader()
    private LicenseFilesReader filesReader = new LicenseFilesReader()

    ModuleData read(Project project, ResolvedDependency dependency){
        ModuleData data = new ModuleData(dependency.moduleGroup, dependency.moduleName, dependency.moduleVersion)
        dependency.moduleArtifacts.each { ResolvedArtifact artifact ->
            LOGGER.info("Processing artifact: $artifact ($artifact.file)")
            if (artifact.file.exists()){
                def pom = pomReader.readPomData(project, artifact)
                def manifest = manifestReader.readManifestData(project, artifact)
                def licenseFile = filesReader.read(project, artifact)

                if (pom) data.poms << pom
                if (manifest) data.manifests << manifest
                if (licenseFile) data.licenseFiles << licenseFile
            } else {
                LOGGER.info("Skipping artifact file $artifact.file as it does not exist")
            }
        }
        data
    }
}
