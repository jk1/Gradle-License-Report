package com.github.jk1.license.task;

import com.github.jk1.license.render.DetailedHtmlRenderer;
import com.github.jk1.license.render.SimpleHtmlRenderer;
import com.github.jk1.license.render.ReportRenderer;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;


public class LicenseReportTask extends DefaultTask {

    private File outputFile = null;
    private Iterable<String> reportedConfigurations = new LinkedList<String>(Arrays.asList("runtime"));
    private ReportRenderer renderer = new SimpleHtmlRenderer();

    public Iterable<String> getReportedConfigurations() {
        return reportedConfigurations;
    }

    public void setReportedConfiguration(String reportedConfiguration) {
        setReportedConfigurations(reportedConfiguration);
    }

    public void setReportedConfigurations(String reportedConfiguration) {
        Objects.requireNonNull(reportedConfiguration, "configuration to report");
        this.reportedConfigurations = Collections.singletonList(reportedConfiguration);
    }

    public void setReportedConfiguration(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration to report");
        setReportedConfiguration(configuration.getName());
    }

    public void setReportedConfigurations(Configuration configuration) {
        setReportedConfiguration(configuration);
    }

    public void setReportedConfigurations(Iterable<?> reportedConfigurations) {
        if (reportedConfigurations == null) {
            this.reportedConfigurations = Collections.emptyList();
            return;
        }
        this.reportedConfigurations = reportedConfigurations.collect {
            it instanceof Configuration ? ((Configuration) it).getName() : it.toString()
        }
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(Object outputFile) {
        if (!this.outputFile.isFile()) {
            throw new InvalidUserDataException(this.outputFile + " is not a valid file for  report");
        }
        getLogger().debug("Setting output file to " + this.outputFile);
        this.outputFile = getProject().file(outputFile);
    }

    @TaskAction
    public void report() {
        ensureOutputFile();
        getLogger().info("Writing report index file to " + this.outputFile);

        renderer.startProject(this);

        // Get the configurations matching the name: that's our base set
        final Set<Configuration> toReport = new HashSet<Configuration>(getProject()
                .getConfigurations().matching(new Spec<Configuration>() {
            @Override
            public boolean isSatisfiedBy(Configuration configuration) {
                for (String configurationName : reportedConfigurations) {
                    if (configuration.getName().equalsIgnoreCase(configurationName)) {
                        return true;
                    }
                }
                return false;
            }
        }));

        // Now, keep adding extensions until we don't change the set size
        for (int previousRoundSize = 0; toReport.size() != previousRoundSize; previousRoundSize = toReport.size()) {
            for (Configuration configuration : new ArrayList<Configuration>(toReport)) {
                toReport.addAll(configuration.getExtendsFrom());
            }
        }
        getLogger().info("Configurations: " + toReport.join(','));
        for (Configuration configuration : toReport) {
            getLogger().info("Writing out configuration: " + configuration);
            reportConfiguration(configuration);
            getLogger().debug("Linking project to configuration: " + configuration);
            renderer.addConfiguration(this, configuration);
        }
        getLogger().info("Writing out project footer");
        renderer.completeProject(this);
    }

    private void ensureOutputFile() {
        if (getOutputFile() == null) {
            this.outputFile = new File(getProject().getBuildDir(),
                    "reports/dependency-license/index.html");
        }
        outputFile.getParentFile().mkdirs();
    }

    protected File getOutputDir() {
        return getOutputFile().getParentFile();
    }

    public void reportConfiguration(Configuration configuration) {
        getLogger().info("Writing out configuration header for: " + configuration);
        renderer.startConfiguration(this, configuration);

        // TODO Make this a TreeSet to get a nice ordering to the print-out
        Set<ResolvedDependency> dependencies = new HashSet<ResolvedDependency>();
        for (ResolvedDependency dependency : configuration.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
            dependencies.add(dependency);
            dependencies.addAll(dependency.getChildren());
        }

        getLogger().info("Processing dependencies for configuration[$configuration]: " + dependencies.join(','));
        for (ResolvedDependency dependency : dependencies) {
            getLogger().debug("Processing dependency: " + dependency);
            renderer.addDependency(this, configuration, dependency);
        }
        getLogger().info("Writing out configuration footer for: " + configuration);
        renderer.completeConfiguration(this, configuration);
    }

}
