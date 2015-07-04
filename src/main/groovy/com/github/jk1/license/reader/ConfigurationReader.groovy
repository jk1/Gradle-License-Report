package com.github.jk1.license.reader

import com.github.jk1.license.ConfigurationData
import com.github.jk1.license.LicenseReportPlugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class ConfigurationReader {

    private Logger LOGGER = Logging.getLogger(Task.class)
    private ModuleReader dependencyReader = new ModuleReader()
    private LicenseReportPlugin.LicenseReportExtension config

    ConfigurationData read(Project project, Configuration configuration) {
        config = project.licenseReport
        configuration.resolvedConfiguration // force configuration resolution
        ConfigurationData data = new ConfigurationData()
        Set<ResolvedDependency> dependencies = new TreeSet<ResolvedDependency>(new ResolvedDependencyComparator())
        for (ResolvedDependency dependency : configuration.resolvedConfiguration.getFirstLevelModuleDependencies()) {
            collectDependencies(dependencies, dependency)
        }
        LOGGER.info("Processing dependencies for configuration[$configuration]: " + dependencies.join(','))
        for (ResolvedDependency dependency : dependencies) {
            LOGGER.debug("Processing dependency: $dependency")
            data.dependencies.add(dependencyReader.read(project, dependency))
        }
        return data
    }

    private Set<ResolvedDependency> collectDependencies(Set<ResolvedDependency> accumulator, ResolvedDependency root){
        if (!config.excludeGroups.contains(root.moduleGroup)) {
            accumulator.add(root)
        }
        root.children.each {collectDependencies(accumulator, it)}
        accumulator
    }

    private static class ResolvedDependencyComparator implements Comparator<ResolvedDependency>{
        @Override
        int compare(ResolvedDependency first, ResolvedDependency second) {
            int result = first.moduleGroup.compareTo(second.moduleGroup)
            result == 0 ? first.moduleName.compareTo(second.moduleName) : result
        }
    }
}
