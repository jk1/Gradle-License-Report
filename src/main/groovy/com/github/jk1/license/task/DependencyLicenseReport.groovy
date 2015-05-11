package com.github.jk1.license.task;

import com.github.jk1.license.render.DetailedHtmlRenderer;
import com.github.jk1.license.render.ReportRenderer;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class DependencyLicenseReport extends DefaultTask {

    private volatile File outputFile = null;

    private volatile Iterable<String> reportedConfigurations = new LinkedList<String>(Arrays.asList("runtime"));

    private volatile ReportRenderer renderer = new DetailedHtmlRenderer();

    public Iterable<String> getReportedConfigurations() {
        return reportedConfigurations;
    }

    public void setReportedConfiguration(String reportedConfiguration) {
        setReportedConfigurations(reportedConfiguration);
    }

    public void setReportedConfigurations(String reportedConfiguration) {
        Objects.requireNonNull(reportedConfiguration, "configuration to report");
        this.reportedConfigurations = ImmutableList.of(reportedConfiguration);
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
        this.reportedConfigurations = Iterables.transform(
                reportedConfigurations, new Function<Object, String>() {
            @Override
            public String apply(Object input) {
                Objects.requireNonNull(input,
                        "reportedConfigurations element");
                if (input instanceof Configuration) {
                    return ((Configuration) input).getName();
                } else {
                    return input.toString();
                }
            }
        });
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

        getLogger().info("Writing out project header");
        renderer.startProject(this);

        // Get the configurations matching the name: that's our base set
        final Set<Configuration> toReport = new HashSet<Configuration>(getProject()
                .getConfigurations().matching(new Spec<Configuration>() {
            @Override
            public boolean isSatisfiedBy(Configuration configuration) {
                for (String configurationName : reportedConfigurations) {
                    if (configuration.getName().equalsIgnoreCase(
                            configurationName)) {
                        return true;
                    }
                }
                return false;
            }
        }));

        // Now, keep adding extensions until we don't change the set size
        for (int previousRoundSize = 0; toReport.size() != previousRoundSize; previousRoundSize = toReport
                .size()) {
            for (Configuration configuration : new ArrayList<Configuration>(toReport)) {
                toReport.addAll(configuration.getExtendsFrom());
            }
        }

        getLogger().info("Configurations: " + StringUtils.join(toReport, ", "));

        for (Configuration configuration : toReport) {
            getLogger().info("Writing out configuration: " + configuration);
            reportConfiguration(configuration);

            getLogger().debug(
                    "Linking project to configuration: " + configuration);
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

    protected final LoadingCache<Map<String, String>, Collection<ResolvedArtifact>> resolvedArtifactCache = CacheBuilder
            .newBuilder()
            .concurrencyLevel(1)
            .build(new CacheLoader<Map<String, String>, Collection<ResolvedArtifact>>() {
        @Override
        public Collection<ResolvedArtifact> load(Map<String, String> key)
                throws Exception {
            Collection<ResolvedArtifact> artifacts = doResolveArtifact(
                    DependencyLicenseReport.this, key);
            if (artifacts != null) {
                // Exercise #getFile() to download the file and catch exceptions here
                for (ResolvedArtifact artifact : artifacts) {
                    artifact.getFile();
                }
            }
            return artifacts;
        }
    });

    static Collection<ResolvedArtifact> doResolveArtifact(DependencyLicenseReport report, Object spec) {
        Project project = report.getProject();
        Thread.sleep(2L) // Ensures a unique name below
        String configName = "dependencyLicenseReport${Long.toHexString(System.currentTimeMillis())}"
        project.configurations.create("$configName")
        project.dependencies."$configName"(spec)
        Configuration config = project.configurations.getByName(configName)
        return config.resolvedConfiguration.resolvedArtifacts
    }

    protected Collection<ResolvedArtifact> resolveArtifacts(Map<String, String> spec) {
        try {
            return resolvedArtifactCache.getUnchecked(ImmutableMap.copyOf(spec));
        } catch (Exception e) {
            if (getLogger().isInfoEnabled()) {
                getLogger().info("Failure to retrieve artifacts for " + spec, e);
            } else {
                getLogger().warn(
                        "Could not retrieve artifacts for "
                                + spec
                                + " -- "
                                + StringUtils.defaultIfBlank(e.getMessage(), e
                                .getClass().getSimpleName()));
            }
            return Collections.emptyList();
        }
    }

    public void reportConfiguration(Configuration configuration) {
        getLogger().info("Writing out configuration header for: " + configuration);
        renderer.startConfiguration(this, configuration);

        // TODO Make this a TreeSet to get a nice ordering to the print-out
        Set<ResolvedDependency> dependencies = new HashSet<ResolvedDependency>();
        for (ResolvedDependency dependency : configuration
                .getResolvedConfiguration().getFirstLevelModuleDependencies()) {
            dependencies.add(dependency);
            dependencies.addAll(dependency.getChildren());
        }

        getLogger().info(
                "Processing dependencies for configuration[" + configuration
                        + "]: " + StringUtils.join(dependencies, ", "));

        for (ResolvedDependency dependency : dependencies) {
            getLogger().debug("Processing dependency: " + dependency);
            renderer.addDependency(this, configuration, dependency);
        }

        getLogger().info(
                "Writing out configuration footer for: " + configuration);
        renderer.completeConfiguration(this, configuration);
    }
}
