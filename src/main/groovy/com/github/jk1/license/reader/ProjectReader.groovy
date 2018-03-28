package com.github.jk1.license.reader

import com.github.jk1.license.ProjectData
import com.github.jk1.license.ReportTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * todo: rewrite me in an idiomatic way
 */
class ProjectReader {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)
    private ConfigurationReader configurationReader = new ConfigurationReader()

    ProjectData read(Project project) {
        ProjectData data = new ProjectData()
        data.project = project

        final Set<Configuration> configurationsToScan = findConfigurationsToScan(project)

        configurationsToScan.addAll(getAllExtendedConfigurations(configurationsToScan))

        LOGGER.info("Configurations: " + configurationsToScan.join(','))
        for (Configuration configuration : configurationsToScan) {
            LOGGER.info("Reading configuration: " + configuration)
            data.configurations.add(configurationReader.read(project, configuration))
        }
        return data
    }

    private static Set<Configuration> getAllExtendedConfigurations(Set<Configuration> configurationsToScan) {
        configurationsToScan.collect { it.extendsFrom }.flatten()
    }

    private static Set<Configuration> findConfigurationsToScan(Project project) {
        project.configurations.findAll { config ->
            isConfigured(config, project) && isResolvable(config)
        }
    }

    private static boolean isConfigured(Configuration config, Project project) {
        def configurationsToScan = project.licenseReport.configurations ?: project.configurations.collect {
            it.name
        }
        config.name in configurationsToScan
    }

    private static boolean isResolvable(Configuration config) {
        final def unresolvableConfigurations = ['pomResolver',
                                                'apiElements',
                                                'implementation',
                                                'runtimeElements',
                                                'runtimeOnly',
                                                'testImplementation',
                                                'testRuntimeOnly']
        !(config.name in unresolvableConfigurations)
    }
}
