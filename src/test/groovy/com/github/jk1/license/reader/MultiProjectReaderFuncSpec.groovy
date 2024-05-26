/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license.reader

import com.github.jk1.license.AbstractGradleRunnerFunctionalSpec
import org.gradle.testkit.runner.TaskOutcome

class MultiProjectReaderFuncSpec  extends AbstractGradleRunnerFunctionalSpec {

    def setup() {
        settingsGradle = new File(testProjectDir, "settings.gradle")
        settingsGradle << """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = []
            }
            dependencies {
                mainConfig "org.apache.commons:commons-lang3:3.7"
            }
        """
    }

    def "same dependencies of the same configuration are merged"() {
        setup:
        newSubBuildFile("sub1") << """
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                mainConfig "org.apache.commons:commons-lang3:3.7"
                mainConfig "org.jetbrains:annotations:16.0.1"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        removeLicenseFiles(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        configurationsString == """[
    {
        "dependencies": [
            {
                "group": "org.apache.commons",
                "manifests": [
                    {
                        "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": null,
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.7",
                "poms": [
                    {
                        "inceptionYear": "2001",
                        "projectUrl": "http://commons.apache.org/proper/commons-lang/",
                        "description": "\\n  Apache Commons Lang, a package of Java utility classes for the\\n  classes that are in java.lang's hierarchy, or are considered to be so\\n  standard as to justify existence in java.lang.\\n  ",
                        "name": "Apache Commons Lang",
                        "organization": {
                            "url": "https://www.apache.org/",
                            "name": "The Apache Software Foundation"
                        },
                        "licenses": [
                            {
                                "url": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            },
            {
                "group": "org.jetbrains",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": null,
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": null
                    }
                ],
                "hasArtifactFile": true,
                "version": "16.0.1",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "https://github.com/JetBrains/java-annotations",
                        "description": "A set of annotations used for code inspection support and code documentation.",
                        "name": "JetBrains Java Annotations",
                        "organization": null,
                        "licenses": [
                            {
                                "url": "http://www.apache.org/license/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "annotations"
            }
        ],
        "name": "mainConfig"
    }
]"""
    }

    def "different configurations are kept"() {
        setup:
        newSubBuildFile("sub1") << """
            configurations {
                subConfig
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                subConfig "org.apache.commons:commons-lang3:3.7"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        configurationsString == """[
    {
        "dependencies": [
            {
                "group": "org.apache.commons",
                "manifests": [
                    {
                        "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": null,
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.7",
                "poms": [
                    {
                        "inceptionYear": "2001",
                        "projectUrl": "http://commons.apache.org/proper/commons-lang/",
                        "description": "\\n  Apache Commons Lang, a package of Java utility classes for the\\n  classes that are in java.lang's hierarchy, or are considered to be so\\n  standard as to justify existence in java.lang.\\n  ",
                        "name": "Apache Commons Lang",
                        "organization": {
                            "url": "https://www.apache.org/",
                            "name": "The Apache Software Foundation"
                        },
                        "licenses": [
                            {
                                "url": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "mainConfig"
    },
    {
        "dependencies": [
            {
                "group": "org.apache.commons",
                "manifests": [
                    {
                        "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": null,
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.7",
                "poms": [
                    {
                        "inceptionYear": "2001",
                        "projectUrl": "http://commons.apache.org/proper/commons-lang/",
                        "description": "\\n  Apache Commons Lang, a package of Java utility classes for the\\n  classes that are in java.lang's hierarchy, or are considered to be so\\n  standard as to justify existence in java.lang.\\n  ",
                        "name": "Apache Commons Lang",
                        "organization": {
                            "url": "https://www.apache.org/",
                            "name": "The Apache Software Foundation"
                        },
                        "licenses": [
                            {
                                "url": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "subConfig"
    }
]"""
    }

    def "project filtering is respected"() {
        setup:
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                projects = [project]
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = []
            }
            dependencies {
                mainConfig "org.apache.commons:commons-lang3:3.7"
            }
        """

        newSubBuildFile("sub1") << """
            configurations {
                subConfig
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                subConfig "org.apache.commons:commons-lang3:3.7"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        configurationsString == """[
    {
        "dependencies": [
            {
                "group": "org.apache.commons",
                "manifests": [
                    {
                        "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": null,
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.7",
                "poms": [
                    {
                        "inceptionYear": "2001",
                        "projectUrl": "http://commons.apache.org/proper/commons-lang/",
                        "description": "\\n  Apache Commons Lang, a package of Java utility classes for the\\n  classes that are in java.lang's hierarchy, or are considered to be so\\n  standard as to justify existence in java.lang.\\n  ",
                        "name": "Apache Commons Lang",
                        "organization": {
                            "url": "https://www.apache.org/",
                            "name": "The Apache Software Foundation"
                        },
                        "licenses": [
                            {
                                "url": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "mainConfig"
    }
]"""
    }

    def "repositories of the sub-projects are used"() {
        setup:
        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = []
            }
        """

        newSubBuildFile("sub1") << """
            configurations {
                subConfig
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                subConfig "org.jetbrains:annotations:16.0.1"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        removeLicenseFiles(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        configurationsString == """[
    {
        "dependencies": [
            {
                "group": "org.jetbrains",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": null,
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": null
                    }
                ],
                "hasArtifactFile": true,
                "version": "16.0.1",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "https://github.com/JetBrains/java-annotations",
                        "description": "A set of annotations used for code inspection support and code documentation.",
                        "name": "JetBrains Java Annotations",
                        "organization": null,
                        "licenses": [
                            {
                                "url": "http://www.apache.org/license/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "annotations"
            }
        ],
        "name": "subConfig"
    }
]"""
    }

    def "only defined configurations (and their extended forms) are considered"() {
        setup:
        newSubBuildFile("sub1") << """
            configurations {
                subConfig
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                subConfig "org.jetbrains:annotations:16.0.1"
            }
        """

        buildFile.text = """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = ['mainConfig']
            }
            dependencies {
                mainConfig "org.apache.commons:commons-lang3:3.7"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        configurationsGPath.name == ["mainConfig"]
    }

    def "use all configurations if none are defined"() {
        setup:
        newSubBuildFile("sub1") << """
            configurations {
                subConfig
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                subConfig "org.jetbrains:annotations:16.0.1"
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        configurationsGPath.name == ["mainConfig", "subConfig"]
    }

    /*
    Test kotlin multiplatform project structure, including subproject reports and custom
    output dirs
     */
    def "Test Kotlin Multiplatform"() {
        setup:
        def properties = new File(testProjectDir, "gradle.properties")
        properties << "android.useAndroidX = true"

        newSubBuildFile("shared") << """
            plugins {
                id "com.android.library" version "7.4.0"
                id "org.jetbrains.kotlin.multiplatform" version "1.9.20"
                id "com.github.jk1.dependency-license-report"
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
                google()
            }
            kotlin {
                androidTarget()
                
                sourceSets {
                    commonMain.dependencies {
                        api("io.ktor:ktor-client-core:2.3.6")
                        implementation("com.benasher44:uuid:0.7.0")
                    }
            
                    androidMain.dependencies {
                        implementation("app.cash.sqldelight:android-driver:2.0.0")
                        implementation("io.ktor:ktor-client-android:2.3.6")
                    }
                }
            }
            android {
                namespace "com.example.shared"
                compileSdk 34
                defaultConfig {
                    minSdk 26
                }
            }
            // shared folder reports are useful for iOS license reports
            licenseReport {
                // test relative paths
                outputDir = "sharedBuild/reports/license-test/"
                renderers = [new com.github.jk1.license.render.JsonReportRenderer()]
                configurations = [
                        "commonMainCompileOnlyDependenciesMetadata",
                        "iosMainCompileOnlyDependenciesMetadata"
                ]
                
                println("Output shared: " + outputDir)
            }
        """

        newSubBuildFile("android") << """
            plugins {
                id "com.android.application" version "7.4.0"
                id "org.jetbrains.kotlin.android" version "1.9.20"
                id "com.github.jk1.dependency-license-report"
            }
            configurations {
                mainConfig
            }
            repositories {
                mavenCentral()
                google()
            }
            android {
                namespace "com.example.android"
                compileSdk 34
                defaultConfig {
                    applicationId "com.example.android"
                    minSdk 26
                    targetSdk 34
                }
                dependencies {
                    implementation project(":shared")
                }
            }
            licenseReport {
                // test relative paths
                outputDir = "androidBuild/reports/license-test/"
                renderers = [new com.github.jk1.license.render.JsonReportRenderer()]
                configurations = ["releaseRuntimeClasspath"]
                
                println("Output android: " + outputDir)
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":android:generateLicenseReport").outcome == TaskOutcome.SUCCESS
        runResult.task(":shared:generateLicenseReport").outcome == TaskOutcome.SUCCESS
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        println(testProjectDir.list())
    }

    static void removeDevelopers(Map rawFile) {
        rawFile.configurations*.dependencies.flatten().poms.flatten().each { it.remove("developers") }
    }

    static void removeLicenseFiles(Map rawFile) {
        rawFile.configurations*.dependencies.flatten().each { it.remove("licenseFiles") }
    }
}
