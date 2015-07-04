package com.github.jk1.license

import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import com.github.jk1.license.task.ReportTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

public class LicenseReportPlugin implements Plugin<Project> {

    private Logger LOGGER = Logging.getLogger(Plugin.class)

	@Override
	public void apply(Project project) {
		LicenseReportExtension ext = new LicenseReportExtension()
        project.extensions.add('licenseReport', ext)
		project.task(['type': ReportTask.class], "generateLicenseReport")
		project.afterEvaluate {
            ext.afterEvaluate(project)
        }
	}

    static class LicenseReportExtension {

        private Logger LOGGER = Logging.getLogger(Plugin.class)

        String outputDir
        ReportRenderer renderer
        String[] configurations
        String[] excludeGroups

        void afterEvaluate(Project project){
            if (outputDir == null){
                outputDir = "${project.buildDir}/reports/dependency-license"
            }
            LOGGER.debug("Using dependency license report output dir: $outputDir")
            if (renderer == null){
                renderer = new SimpleHtmlReportRenderer()
            }
            if (configurations == null){
                configurations = ['runtime']
            }
            if (excludeGroups == null){
                excludeGroups = [project.group]
            }
        }

        void beforeExecute(){
            new File(outputDir).mkdirs()
        }
    }
}