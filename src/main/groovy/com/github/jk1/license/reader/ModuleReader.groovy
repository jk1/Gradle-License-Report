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

import com.github.jk1.license.GradleProject
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.task.ReportTask
import com.github.jk1.license.util.CachingPomResolver
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

interface ModuleReader {
    ModuleData read(GradleProject project, ResolvedDependency dependency)
}

class ModuleReaderImpl implements ModuleReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private LicenseReportExtension config
    private PomReader pomReader
    private ManifestReader manifestReader
    private LicenseFilesReader filesReader

    ModuleReaderImpl(LicenseReportExtension config) {
        this.config = config
        this.pomReader = new PomReader(config)
        this.manifestReader = new ManifestReader(config)
        this.filesReader = new LicenseFilesReader(config)
    }

    ModuleData read(GradleProject project, ResolvedDependency dependency) {
        ModuleData moduleData = new ModuleData(dependency.moduleGroup, dependency.moduleName, dependency.moduleVersion)
        CachingPomResolver pomResolver = new CachingPomResolver(project)
        dependency.moduleArtifacts.each { ResolvedArtifact artifact ->
            LOGGER.info("Processing artifact: $artifact ($artifact.file)")
            moduleData.hasArtifactFile = artifact.file.exists()
            if (moduleData.hasArtifactFile) {
                def pom = pomReader.withResolver(pomResolver).readPomData(artifact)
                def manifest = manifestReader.readManifestData(artifact)
                def licenseFile = filesReader.read(artifact)

                if (pom) moduleData.poms << pom
                if (manifest) moduleData.manifests << manifest
                if (licenseFile) moduleData.licenseFiles << licenseFile
            } else {
                LOGGER.info("Skipping artifact file $artifact.file as it does not exist")
            }
        }
        if (dependency.moduleArtifacts.isEmpty()) {
            def extraPomResults = pomResolver.resolveArtifacts(dependency.moduleGroup, dependency.moduleName, dependency.moduleVersion)
            extraPomResults.each { ResolvedArtifactResult artifact ->
                LOGGER.info("Processing artifact: $artifact ($artifact.file)")
                if (artifact.file.exists()) {
                    def pom = pomReader.withResolver(pomResolver).readPomData(artifact)
                    if (pom) moduleData.poms << pom
                } else {
                    LOGGER.info("Skipping artifact file $artifact.file as it does not exist")
                }
            }
        }
        return moduleData
    }

}

class CachedModuleReader implements ModuleReader {
    private Map<String, ModuleData> moduleDataCache = [:]
    private ModuleReader actualReader

    CachedModuleReader(LicenseReportExtension config) {
        this.actualReader = new ModuleReaderImpl(config)
    }

    ModuleData read(GradleProject project, ResolvedDependency dependency) {
        String dataName = "${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}"
        return moduleDataCache.computeIfAbsent(dataName) {
            actualReader.read(project, dependency)
        }
    }
}
