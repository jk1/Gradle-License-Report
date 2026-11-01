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

import com.github.jk1.license.reader.ConfigurationScanner
import com.github.jk1.license.task.CheckLicensePreparationTask
import com.github.jk1.license.task.CheckLicenseTask
import com.github.jk1.license.task.CollectDependenciesTask
import com.github.jk1.license.task.CollectDependenciesTask.ScannedConfiguration
import com.github.jk1.license.task.ReportTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

// registers the deprecated CheckLicensePreparationTask for backwards compatibility
@SuppressWarnings('GrDeprecatedAPIUsage')
class LicenseReportPlugin implements Plugin<Project> {

    final def MINIMUM_REQUIRED_GRADLE_VERSION = "7.0"

    @Override
    void apply(Project project) {
        assertCompatibleGradleVersion()

        def extension = project.extensions.create('licenseReport', LicenseReportExtension, project)

        // Everything read from the extension is read lazily via providers, which Gradle evaluates once the build is
        // configured (and when storing the configuration cache), so tasks never touch the Project at execution time.
        def preparationTask = project.tasks.register("checkLicensePreparation", CheckLicensePreparationTask)
        def generateLicenseReportTask = project.tasks.register('generateLicenseReport', ReportTask) { ReportTask task ->
            task.shouldRunAfter(preparationTask)
            task.projectInfo.set(project.provider { ProjectInfo.of(extension.reportedProject) })
            task.settings.set(project.provider { LicenseReportSettings.of(extension) })
            task.renderers.set(project.provider { listOf(extension.renderers) })
            task.importers.set(project.provider { listOf(extension.importers) })
            task.filters.set(project.provider { listOf(extension.filters) })
            task.outputDir.set(project.layout.dir(project.provider { new File(extension.absoluteOutputDir) }))
        }
        project.tasks.register('checkLicense', CheckLicenseTask) { CheckLicenseTask task ->
            task.dependsOn(preparationTask, generateLicenseReportTask)
            task.allowedLicenseFile.set(project.layout.file(project.provider { allowedLicensesLocalFile(project, extension.allowedLicensesFile) }))
            task.allowedLicenseUrl.set(project.provider { allowedLicensesRemoteUrl(extension.allowedLicensesFile)?.toString() })
            task.projectDependenciesData.set(generateLicenseReportTask.flatMap {
                it.outputDir.file(CheckLicenseTask.PROJECT_JSON_FOR_LICENSE_CHECKING_FILE)
            })
            // Next to, rather than inside the report directory: tasks must not share output locations to be cacheable
            task.notPassedDependenciesFile.set(project.layout.file(project.provider {
                File reportDir = new File(extension.absoluteOutputDir)
                new File(new File(reportDir.parentFile, "${reportDir.name}-check"), CheckLicenseTask.NOT_PASSED_DEPENDENCIES_FILE)
            }))
        }

        // The covered projects are only known once all build scripts have configured the extension
        project.gradle.projectsEvaluated {
            registerDependencyCollectors(project, extension, generateLicenseReportTask)
        }
    }

    /**
     * Registers a {@link CollectDependenciesTask} in each project covered by the report: configurations must be
     * resolved in the context of the project owning them to be configuration-cache and parallel-execution safe.
     */
    private static void registerDependencyCollectors(Project owner, LicenseReportExtension extension,
                                                     TaskProvider<ReportTask> reportTask) {
        List<Project> projects = (extension.projects ?: []).toList()
        List<Project> buildScriptProjects = (extension.buildScriptProjects ?: []).toList()

        List<Project> targets = (projects + buildScriptProjects).unique { it.path }
        List<TaskProvider<CollectDependenciesTask>> collectors = targets.collect { Project target ->
            registerDependencyCollector(target, owner, extension, targets.first(), target in projects, target in buildScriptProjects)
        }

        reportTask.configure { ReportTask task ->
            task.dependencyData.from(collectors.collect { collector -> collector.flatMap { it.outputFile } })
            task.dependencyCoordinates.from(collectors.collect { collector -> collector.flatMap { it.coordinatesFile } })
        }
    }

    private static TaskProvider<CollectDependenciesTask> registerDependencyCollector(
            Project target, Project owner, LicenseReportExtension extension, Project firstTarget,
            boolean scanProject, boolean scanBuildScript) {
        String taskName = 'collectLicenseReportDependencies' + (owner == target ? '' : 'For' + taskNameSuffix(owner))
        target.tasks.register(taskName, CollectDependenciesTask) { CollectDependenciesTask task ->
            def scanner = new ConfigurationScanner(target)
            task.scannedConfigurations.set(target.provider {
                String[] configurations = extension.configurations != null
                    ? extension.configurations
                    : ConfigurationScanner.defaultConfigurations(firstTarget)
                List<ScannedConfiguration> scanned = []
                if (scanProject) scanned.addAll(scanner.scan(configurations, false))
                if (scanBuildScript) scanned.addAll(scanner.scan(configurations, true))
                scanned
            })
            task.pomFiles.set(target.provider { scanner.resolvePoms(task.scannedConfigurations.get()) })
            task.outputFile.set(target.layout.buildDirectory.file("tmp/${taskName}/dependencies.json"))
            task.coordinatesFile.set(target.layout.buildDirectory.file("tmp/${taskName}/coordinates.txt"))
        }
    }

    private static <T> List<T> listOf(T[] values) {
        values == null ? [] : values.toList()
    }

    private static String taskNameSuffix(Project project) {
        project.path == ':' ? 'Root' : project.path.split(':').findAll().collect { it.capitalize() }.join('')
    }

    private static File allowedLicensesLocalFile(Project project, Object allowedLicensesFile) {
        if (allowedLicensesFile == null || allowedLicensesRemoteUrl(allowedLicensesFile) != null) {
            return null
        }
        if (allowedLicensesFile instanceof TextResource) {
            return allowedLicensesFile.asFile()
        }
        return project.file(allowedLicensesFile)
    }

    private static URL allowedLicensesRemoteUrl(Object allowedLicensesFile) {
        URL url = null
        if (allowedLicensesFile instanceof URL) {
            url = allowedLicensesFile
        } else if (allowedLicensesFile instanceof URI && allowedLicensesFile.absolute) {
            url = allowedLicensesFile.toURL()
        } else if (allowedLicensesFile instanceof CharSequence) {
            try {
                url = new URL(allowedLicensesFile.toString())
            } catch (MalformedURLException ignored) {
                // a local path
            }
        }
        return url != null && url.protocol != 'file' ? url : null
    }

    private void assertCompatibleGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_REQUIRED_GRADLE_VERSION)) {
            throw new GradleException("License Report Plugin requires Gradle $MINIMUM_REQUIRED_GRADLE_VERSION. ${GradleVersion.current()} detected.")
        }
    }
}
