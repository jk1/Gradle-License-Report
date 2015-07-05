package com.github.jk1.license.reader

import com.github.jk1.license.ProjectData
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Spec

/**
 * todo: rewrite me in an idiomatic way
 */
class ProjectReader {

    private Logger LOGGER = Logging.getLogger(Task.class)
    private ConfigurationReader configurationReader = new ConfigurationReader()

    ProjectData read(Project project) {
        ProjectData data = new ProjectData()
        data.project = project
        // Get the configurations matching the name: that's our base set
        final Set<Configuration> toReport = new HashSet<Configuration>(project
                .getConfigurations().matching(new Spec<Configuration>() {
            @Override
            public boolean isSatisfiedBy(Configuration configuration) {
                for (String configurationName : project.licenseReport.configurations) {
                    if (configuration.getName().equalsIgnoreCase(configurationName)) {
                        return true
                    }
                }
                return false
            }
        }))

        // Now, keep adding extensions until we don't change the set size
        for (int previousRoundSize = 0; toReport.size() != previousRoundSize; previousRoundSize = toReport.size()) {
            for (Configuration configuration : new ArrayList<Configuration>(toReport)) {
                toReport.addAll(configuration.getExtendsFrom())
            }
        }
        LOGGER.info("Configurations: " + toReport.join(','))
        for (Configuration configuration : toReport) {
            LOGGER.info("Reading configuration: " + configuration)
            data.configurations.add(configurationReader.read(project, configuration))
        }
        // import external dependency information, if any
        project.licenseReport.importers.each{
            data.importedModules.addAll(it.doImport())
        }
        return data
    }
}
