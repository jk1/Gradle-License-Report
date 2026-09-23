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
package com.github.jk1.license.task

import com.github.jk1.license.check.LicenseChecker
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class CheckLicenseTask extends DefaultTask {

    final static String PROJECT_JSON_FOR_LICENSE_CHECKING_FILE = "project-licenses-for-check-license-task.json"
    final static String NOT_PASSED_DEPENDENCIES_FILE = "dependencies-without-allowed-license.json"

    private static Logger LOGGER = Logging.getLogger(CheckLicenseTask.class)

    CheckLicenseTask() {
        group = 'Checking'
        description = 'Check if License could be used'
    }

    /** The allowed licenses file, when {@code licenseReport.allowedLicensesFile} refers to a local file. */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getAllowedLicenseFile()

    /** The allowed licenses URL, when {@code licenseReport.allowedLicensesFile} refers to a remote file. */
    @Optional
    @Input
    abstract Property<String> getAllowedLicenseUrl()

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract RegularFileProperty getProjectDependenciesData()

    @OutputFile
    abstract RegularFileProperty getNotPassedDependenciesFile()

    @TaskAction
    void checkLicense() {
        LOGGER.info("Check licenses if they are allowed to use.")
        new LicenseChecker().checkAllDependencyLicensesAreAllowed(
            allowedLicenses(), projectDependenciesData.get().asFile, notPassedDependenciesFile.get().asFile)
    }

    private Object allowedLicenses() {
        if (allowedLicenseFile.present) {
            return allowedLicenseFile.get().asFile
        }
        if (allowedLicenseUrl.present) {
            return new URL(allowedLicenseUrl.get())
        }
        throw new InvalidUserDataException("No allowed licenses file configured, please set licenseReport.allowedLicensesFile")
    }
}
