package com.smokejumperit.gradle.report;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import com.google.common.collect.ImmutableMap;

public class DependencyLicensePlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.task(ImmutableMap.of("type", DependencyLicenseReport.class),
				"dependencyLicenseReport");
	}
}
