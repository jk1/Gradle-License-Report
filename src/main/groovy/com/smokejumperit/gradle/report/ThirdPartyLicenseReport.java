package com.smokejumperit.gradle.report;

import static com.google.common.base.Preconditions.checkNotNull;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Prints out an HTML report of third party license information.
 */
public class ThirdPartyLicenseReport implements Plugin<Project> {

	@Override
	public void apply(final Project project) {
		checkNotNull(project, "project");
		project.getTasks().add(new DependencyLicenseReport());
	}

}
