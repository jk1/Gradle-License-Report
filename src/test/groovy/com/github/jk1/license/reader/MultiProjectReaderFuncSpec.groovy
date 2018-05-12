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

import static com.github.jk1.license.reader.ProjectReaderFuncSpec.removeDevelopers

class MultiProjectReaderFuncSpec  extends AbstractGradleRunnerFunctionalSpec {

    File settingsGradle

    private File newSubBuildFile(String subFolderName) {
        File subFolder = new File(testProjectDir.root, subFolderName)
        subFolder.mkdirs()
        settingsGradle << "include '${subFolderName.replace('/', ':')}'"

        File buildFile = new File(subFolder, "build.gradle")
        buildFile.createNewFile()
        buildFile
    }

    def setup() {
        settingsGradle = testProjectDir.newFile("settings.gradle")

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting1
            }
            repositories {
                mavenCentral()
                maven { url "https://dl.bintray.com/realm/maven" }
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "$outputDir.absolutePath"
                renderer = new com.github.jk1.license.render.RawProjectDataJsonRenderer()
                configurations = ['forTesting']
            }
            dependencies {
                forTesting1 "org.apache.commons:commons-lang3:3.7"
            }
        """
    }

    def "same dependencies of the same configuration are merged"() {
        setup:
        newSubBuildFile("sub1") << """
            configurations {
                forTesting1
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                forTesting1 "org.apache.commons:commons-lang3:3.7"
                forTesting1 "org.jetbrains:annotations:16.0.1"
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
                "group": "org.jetbrains",
                "manifests": [
                    {
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": null,
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": null
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
                                "url": "http://www.apache.org/license/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    
                ],
                "empty": false,
                "name": "annotations"
            },
            {
                "group": "org.apache.commons",
                "manifests": [
                    {
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                            "commons-lang3-3.7.jar/META-INF/LICENSE.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "forTesting1"
    }
]"""
    }

    def "different configurations are kept"() {
        setup:
        newSubBuildFile("sub1") << """
            configurations {
                forTesting2
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                forTesting2 "org.apache.commons:commons-lang3:3.7"
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
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                            "commons-lang3-3.7.jar/META-INF/LICENSE.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "forTesting1"
    },
    {
        "dependencies": [
            {
                "group": "org.apache.commons",
                "manifests": [
                    {
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                            "commons-lang3-3.7.jar/META-INF/LICENSE.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "forTesting2"
    }
]"""
    }

    def "project filtering is respected"() {
        setup:
        buildFile.newWriter().withWriter { w ->
            w << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting1
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "$outputDir.absolutePath"
                projects = [project]
                renderer = new com.github.jk1.license.render.RawProjectDataJsonRenderer()
                configurations = ['forTesting']
            }
            dependencies {
                forTesting1 "org.apache.commons:commons-lang3:3.7"
            }
        """
        }

        newSubBuildFile("sub1") << """
            configurations {
                forTesting2
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                forTesting2 "org.apache.commons:commons-lang3:3.7"
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
                        "vendor": "The Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "3.7.0",
                        "license": "https://www.apache.org/licenses/LICENSE-2.0.txt",
                        "description": "Apache Commons Lang, a package of Java utility classes for the  classes that are in java.lang's hierarchy, or are considered to be so  standard as to justify existence in java.lang.",
                        "url": "http://commons.apache.org/proper/commons-lang/",
                        "name": "Apache Commons Lang"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": null,
                                "file": "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                                "license": null
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-lang3-3.7.jar/META-INF/LICENSE.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "commons-lang3-3.7.jar/META-INF/NOTICE.txt",
                            "commons-lang3-3.7.jar/META-INF/LICENSE.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-lang3"
            }
        ],
        "name": "forTesting1"
    }
]"""
    }
}
