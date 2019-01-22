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
package com.github.jk1.license.util

import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.ModuleData
import org.gradle.api.artifacts.ResolvedArtifact

class Paths {
    static String createPathNameFrom(ResolvedArtifact artifact) {
        return artifact.id.componentIdentifier.displayName.replace(":", "_")
    }

    static String createPathNameFrom(ModuleData dependency, String customizeDirectory = "") {
        return "${dependency.group}_${dependency.name}_${dependency.version}/${customizeDirectory}"
    }

    static String createPathNameFrom(ImportedModuleData moduleData, String customizeDirectory = "") {
        return "${moduleData.name}_${moduleData.version}/${customizeDirectory}"
    }

    static String createFileNameFromLicenseName(String licenseName, String fileType) {
        if (licenseName == null) return "LICENSE${fileType}"
        else if (licenseName.isAllWhitespace() || licenseName.isEmpty()) return "LICENSE${fileType}"
        return licenseName.split("/").last().split(/([ .,-])/).findAll { it != "" }.join("_") + fileType
    }

    static List<String> allRelativePathBetween(Collection<String> originalPaths, String parentPath) {
        return originalPaths.collect { relativePathBetween(it, parentPath) }.sort()
    }

    static String relativePathBetween(String originalPath, String parentPath) {
        return originalPath.replace(parentPath + "/", "")
    }
}
