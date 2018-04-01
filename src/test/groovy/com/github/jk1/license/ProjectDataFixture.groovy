package com.github.jk1.license

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class ProjectDataFixture {
    private static Project project = null
    static def GRADLE_PROJECT() {
        if (project == null) {
            project = ProjectBuilder.builder().withName("my-project").build()
            project.pluginManager.apply 'com.github.jk1.dependency-license-report'
        }
        project
    }

    static License APACHE2_LICENSE() {
        new License(
            name: "Apache License, Version 2.0",
            url: "https://www.apache.org/licenses/LICENSE-2.0",
            distribution: "repo",
            comments: "A business-friendly OSS license"
        )
    }
    static License MIT_LICENSE() {
        new License(
            name: "MIT License",
            url: "https://opensource.org/licenses/MIT",
            distribution: "repo",
            comments: "A short and simple permissive license"
        )
    }
    static License LGPL_LICENSE() {
        new License(
            name: "GNU LESSER GENERAL PUBLIC LICENSE, Version 3",
            url: "https://www.gnu.org/licenses/lgpl-3.0",
            distribution: "repo",
            comments: "A weak copyleft license"
        )
    }
}
