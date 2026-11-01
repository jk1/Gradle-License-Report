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

/**
 * Configuration-cache compatible snapshot of the {@link LicenseReportExtension}, available to readers, filters and
 * renderers at task execution time via {@link ProjectData#getExtension()}.
 */
@Canonical
class LicenseReportSettings implements Serializable {
    private static final long serialVersionUID = 1L

    boolean unionParentPomLicenses
    boolean excludeOwnGroup
    boolean excludeBoms
    List<String> ownGroups = []
    List<String> excludeGroups = []
    List<String> excludes = []

    /**
     * Absolute path of the report output directory. Transient, so that it does not become part of the (relocatable)
     * task input fingerprint; it is set at execution time via {@link #withOutputDir(String)}.
     */
    transient String absoluteOutputDir

    static LicenseReportSettings of(LicenseReportExtension extension) {
        new LicenseReportSettings(
            unionParentPomLicenses: extension.unionParentPomLicenses,
            excludeOwnGroup: extension.excludeOwnGroup,
            excludeBoms: extension.excludeBoms,
            ownGroups: (extension.projects ?: []).collect { String.valueOf(it.group) }.unique(),
            excludeGroups: (extension.excludeGroups ?: []).toList(),
            excludes: (extension.excludes ?: []).toList(),
            absoluteOutputDir: extension.absoluteOutputDir
        )
    }

    LicenseReportSettings withOutputDir(String absoluteOutputDir) {
        new LicenseReportSettings(
            unionParentPomLicenses: unionParentPomLicenses,
            excludeOwnGroup: excludeOwnGroup,
            excludeBoms: excludeBoms,
            ownGroups: ownGroups,
            excludeGroups: excludeGroups,
            excludes: excludes,
            absoluteOutputDir: absoluteOutputDir
        )
    }

    /** Same as {@link #getAbsoluteOutputDir()}, for renderers written against {@link LicenseReportExtension}. */
    String getOutputDir() {
        absoluteOutputDir
    }

    boolean isExcluded(String group, String name, boolean hasArtifacts) {
        return shouldExcludeOwnGroup(group) ||
            shouldExcludeGroup(group) ||
            shouldExcludeBom(name, hasArtifacts) ||
            shouldExcludeArtifact(group, name)
    }

    private boolean shouldExcludeOwnGroup(String group) {
        excludeOwnGroup && ownGroups.contains(group)
    }

    private boolean shouldExcludeGroup(String group) {
        excludeGroups.contains(group) || excludeGroups.any { group.matches(it) }
    }

    private boolean shouldExcludeBom(String name, boolean hasArtifacts) {
        excludeBoms && (name.endsWith("-bom") || name == "bom") && !hasArtifacts
    }

    private boolean shouldExcludeArtifact(String group, String name) {
        def coordinates = "$group:$name"
        excludes.contains(coordinates) || excludes.any { coordinates.matches(it) }
    }
}
