package com.github.jk1.license

import com.github.jk1.license.task.DependencyLicenseReport;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import com.google.common.collect.ImmutableMap;

public class DependencyLicensePlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
        project.extensions.create("licenseReport", LicenseReportExtension)
		project.task(ImmutableMap.of("type", DependencyLicenseReport.class), "licenseReport");
	}
}

class LicenseReportExtension{

}