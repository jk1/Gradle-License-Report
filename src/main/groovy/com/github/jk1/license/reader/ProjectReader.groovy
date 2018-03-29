package com.github.jk1.license.reader

import com.github.jk1.license.ConfigurationData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ReportTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ProjectReader {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)
    private ConfigurationReader configurationReader = new ConfigurationReader()

    ProjectData read(Project project) {
        ProjectData data = new ProjectData()
        data.project = project

        final Set<Configuration> configurationsToScan = findConfigurationsToScan(project)

        configurationsToScan.addAll(getAllExtendedConfigurations(configurationsToScan))

        LOGGER.info("Configurations: " + configurationsToScan.join(','))
        data.configurations = readConfigurationData(configurationsToScan, project)
        return data
    }

    private static Set<Configuration> findConfigurationsToScan(Project project) {
        Set<Configuration> toScan = findConfigured(project)
        Set<Configuration> unresolvable = findUnresolvable(toScan)
        if (unresolvable) {
            throw new UnresolvableConfigurationException("Unable to resolve configurations: $unresolvable")
        }
        if (!toScan) {
            toScan = project.configurations.findAll { config -> isResolvable(config) }
        }
        toScan
    }

    private static Set<Configuration> getAllExtendedConfigurations(Set<Configuration> configurationsToScan) {
        configurationsToScan.collect { it.extendsFrom }.flatten().findAll { config -> isResolvable(config) }
    }

    private List<ConfigurationData> readConfigurationData(Set<Configuration> configurationsToScan, Project project) {
        configurationsToScan.collect { config ->
            LOGGER.info("Reading configuration: " + config)
            configurationReader.read(project, config)
        }
    }

    private static Set<Configuration> findConfigured(Project project) {
        project.configurations.findAll { config -> config.name in project.licenseReport.configurations }
    }

    private static Set<Configuration> findUnresolvable(Set<Configuration> toScan) {
        toScan.findAll { config -> !isResolvable(config) }
    }

    private static boolean isResolvable(Configuration config) {
        config.hasProperty("canBeResolved") && config.canBeResolved
    }
}
