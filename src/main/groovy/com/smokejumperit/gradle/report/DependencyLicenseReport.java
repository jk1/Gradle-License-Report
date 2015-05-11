package com.smokejumperit.gradle.report;

import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.completeConfiguration;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.completeProject;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.doResolveArtifact;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.linkProjectToConfiguration;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.reportDependency;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.startConfiguration;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.startProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
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

	private volatile Iterable<String> reportedConfigurations = new LinkedList<String>(
			Arrays.asList("runtime"));

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
		this.outputFile = getProject().file(outputFile);
		if (this.outputFile.isDirectory()
				|| !fileNameLooksLikeHtml(this.outputFile)) {
			this.outputFile = new File(this.outputFile, "index.html");
		}
		getLogger().debug("Setting output file to " + this.outputFile);
	}

	private static boolean fileNameLooksLikeHtml(File file) {
		String fileName = file.getName();
		if (StringUtils.endsWithIgnoreCase(fileName, ".html")) {
			return true;
		}
		if (StringUtils.endsWithIgnoreCase(fileName, ".htm")) {
			return true;
		}
		return false;
	}

	@TaskAction
	public void report() {
		ensureOutputFile();
		getLogger().info("Writing report index file to " + this.outputFile);

		getLogger().info("Writing out project header");
		startProject(this);

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
			linkProjectToConfiguration(this, configuration);
		}

		getLogger().info("Writing out project footer");
		completeProject(this);
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
						// Exercise #getFile() to download the file and catch
						// exceptions here
						for (ResolvedArtifact artifact : artifacts) {
							artifact.getFile();
						}
					}
					return artifacts;
				}
			});

	protected Collection<ResolvedArtifact> resolveArtifacts(
			Map<String, String> spec) {
		try {
			return resolvedArtifactCache
					.getUnchecked(ImmutableMap.copyOf(spec));
		} catch (Exception e) {
			if (getLogger().isInfoEnabled()) {
				getLogger()
						.info("Failure to retrieve artifacts for " + spec, e);
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
		getLogger().info(
				"Writing out configuration header for: " + configuration);
		startConfiguration(this, configuration);

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
			reportDependency(this, configuration, dependency);
		}

		getLogger().info(
				"Writing out configuration footer for: " + configuration);
		completeConfiguration(this, configuration);
	}
}
