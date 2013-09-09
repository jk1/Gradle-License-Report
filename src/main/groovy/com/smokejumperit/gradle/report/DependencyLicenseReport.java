package com.smokejumperit.gradle.report;

import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.completeConfiguration;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.completeProject;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.linkProjectToConfiguration;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.reportDependency;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.startConfiguration;
import static com.smokejumperit.gradle.report.DependencyLicenseReportSupport.startProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DependencyLicenseReport extends DefaultTask {

	private volatile File outputFile = null;

	private volatile Iterable<String> reportedConfigurations = new LinkedList<>(
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

		startProject(this);

		// Get the configurations matching the name: that's our base set
		final Set<Configuration> toReport = new HashSet<>(getProject()
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
			for (Configuration configuration : new ArrayList<>(toReport)) {
				toReport.addAll(configuration.getExtendsFrom());
			}
		}

		for (Configuration configuration : toReport) {
			reportConfiguration(configuration);
			linkProjectToConfiguration(this, configuration);
		}

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

	public void reportConfiguration(Configuration configuration) {
		startConfiguration(this, configuration);

		// TODO Make this a TreeSet to get a nice ordering to the print-out
		Set<ResolvedDependency> dependencies = new HashSet<>();
		for (ResolvedDependency dependency : configuration
				.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
			dependencies.add(dependency);
			dependencies.addAll(dependency.getChildren());
		}

		for (ResolvedDependency dependency : dependencies) {
			reportDependency(this, configuration, dependency);
		}

		completeConfiguration(this, configuration);
	}
}
