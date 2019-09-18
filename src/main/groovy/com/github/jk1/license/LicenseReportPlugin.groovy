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

import com.github.jk1.license.task.CacheableReportTask
import com.github.jk1.license.task.CheckLicensePreparationTask
import com.github.jk1.license.task.CheckLicenseTask
import com.github.jk1.license.task.ReportTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class LicenseReportPlugin implements Plugin<Project> {

    final def MINIMUM_REQUIRED_GRADLE_VERSION = "3.3"

    @Override
    void apply(Project project) {
        assertCompatibleGradleVersion()

        project.extensions.create('licenseReport', LicenseReportExtension, project)
        def preparationTask = project.tasks.create("checkLicensePreparation", CheckLicensePreparationTask)
        def taskClass = project.getPlugins().hasPlugin('com.android.application') ? ReportTask : CacheableReportTask
        def generateLicenseReportTask = project.tasks.create('generateLicenseReport', taskClass) {
            it.shouldRunAfter(preparationTask)
        }
        project.tasks.create('checkLicense', CheckLicenseTask) {
            it.dependsOn(preparationTask, generateLicenseReportTask)
        }
    }

    private void assertCompatibleGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_REQUIRED_GRADLE_VERSION)) {
            throw new GradleException("License Report Plugin requires Gradle $MINIMUM_REQUIRED_GRADLE_VERSION. ${GradleVersion.current()} detected.")
        }
    }
}
