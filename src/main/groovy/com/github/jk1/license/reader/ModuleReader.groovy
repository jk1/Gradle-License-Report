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

import com.github.jk1.license.LicenseReportSettings
import com.github.jk1.license.ModuleData
import com.github.jk1.license.task.ReportTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

interface ModuleReader {
    ModuleData read(CollectedModule module)
}

class ModuleReaderImpl implements ModuleReader {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private ResolvedPoms poms
    private PomReader pomReader
    private ManifestReader manifestReader
    private LicenseFilesReader filesReader

    ModuleReaderImpl(LicenseReportSettings settings, ResolvedPoms poms) {
        this.poms = poms
        this.pomReader = new PomReader(settings, poms)
        this.manifestReader = new ManifestReader(settings)
        this.filesReader = new LicenseFilesReader(settings)
    }

    ModuleData read(CollectedModule module) {
        ModuleData moduleData = new ModuleData(module.group, module.name, module.version)
        module.artifacts.each { String path ->
            File artifact = new File(path)
            LOGGER.info("Processing artifact: ${module.coordinates} ($artifact)")
            if (artifact.exists()) {
                moduleData.hasArtifactFile = true
                def pom = pomReader.readPomData(module, artifact)
                def manifest = manifestReader.readManifestData(artifact)
                def licenseFile = filesReader.read(artifact)

                if (pom) moduleData.poms << pom
                if (manifest) moduleData.manifests << manifest
                if (licenseFile) moduleData.licenseFiles << licenseFile
            } else {
                LOGGER.info("Skipping artifact file $artifact as it does not exist")
            }
        }
        if (module.artifacts.isEmpty()) {
            poms.find(module.group, module.name, module.version).each { File pomFile ->
                LOGGER.info("Processing POM: ${module.coordinates} ($pomFile)")
                if (pomFile.exists()) {
                    def pom = pomReader.readPomData(pomFile)
                    if (pom) moduleData.poms << pom
                } else {
                    LOGGER.info("Skipping POM file $pomFile as it does not exist")
                }
            }
        }
        return moduleData
    }

}

class CachedModuleReader implements ModuleReader {
    private Map<String, ModuleData> moduleDataCache = [:]
    private ModuleReader actualReader

    CachedModuleReader(LicenseReportSettings settings, ResolvedPoms poms) {
        this.actualReader = new ModuleReaderImpl(settings, poms)
    }

    ModuleData read(CollectedModule module) {
        return moduleDataCache.computeIfAbsent(module.coordinates) {
            actualReader.read(module)
        }
    }
}
