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
import spock.lang.Unroll

class ProjectReaderFuncSpec extends AbstractGradleRunnerFunctionalSpec {

    def setup() {
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
                maven { url "https://oss.jfrog.org/artifactory/oss-snapshot-local" }
                maven { url "https://maven.repository.redhat.com/ga" }
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                renderers = [new com.github.jk1.license.render.RawProjectDataJsonRenderer()]
                configurations = ['forTesting']
            }
        """
    }

    def "it stores the licenses of a jar-file into the output-dir"() {
        buildFile << """
            dependencies {
                forTesting "org.apache.commons:commons-lang3:3.7" // has NOTICE.txt and LICENSE.txt
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        new File(outputDir, "commons-lang3-3.7.jar/META-INF/NOTICE.txt").exists()
        new File(outputDir, "commons-lang3-3.7.jar/META-INF/LICENSE.txt").exists()
    }

    def "the project-data contains the license-file information"() {
        buildFile << """
            dependencies {
                forTesting "org.apache.commons:commons-lang3:3.7" // has NOTICE.txt and LICENSE.txt
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        def licenseFilesGPath = resultFileGPath.configurations*.dependencies.flatten().licenseFiles.flatten()
        def licenseFileString = prettyPrintJson(licenseFilesGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseFileString == """[
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
]"""
    }


    static final def DEPENDENCY_EHCACHE = "org.ehcache:ehcache:3.3.1"
    static final def EXPECTED_ARTIFACT_MISMATCH_EHCACHE = "Artifact: org.ehcache:ehcache / Pom: org.ehcache:sizeof)"
    static final def EXPECTED_CONTENT_EHCACHE = """[
    {
        "inceptionYear": "",
        "projectUrl": "http://ehcache.org",
        "description": "End-user ehcache3 jar artifact",
        "name": "Ehcache",
        "organization": {
            "url": "http://terracotta.org",
            "name": "Terracotta Inc., a wholly-owned subsidiary of Software AG USA, Inc."
        },
        "licenses": [
            {
                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                "name": "The Apache Software License, Version 2.0"
            }
        ]
    },
    {
        "inceptionYear": "",
        "projectUrl": "http://www.slf4j.org",
        "description": "The slf4j API",
        "name": "SLF4J API Module",
        "organization": {
            "url": "http://www.qos.ch",
            "name": "QOS.ch"
        },
        "licenses": [
            {
                "url": "http://www.opensource.org/licenses/mit-license.php",
                "name": "MIT License"
            }
        ]
    }
]"""

    @Unroll
    def "it reads the correct project url for #dependency"(String dependency,
                                                           String expectedContent,
                                                           String expectedMismatchMessage) {
        buildFile << """
            dependencies {
                forTesting "$dependency"
            }
        """

        when:
        def runResult = runGradleBuild(["--debug"])
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def pomGPath = resultFileGPath.configurations*.dependencies.flatten().poms.flatten()
        def pomsString = prettyPrintJson(pomGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        runResult.output.contains("Use remote pom because the found pom seems not to represent artifact. $expectedMismatchMessage")
        pomsString == expectedContent

        where:
        dependency                  | expectedContent                   | expectedMismatchMessage
        DEPENDENCY_EHCACHE          | EXPECTED_CONTENT_EHCACHE          | EXPECTED_ARTIFACT_MISMATCH_EHCACHE
    }


    static final def DEPENDENCY_COMMONS_LANG3 = "org.apache.commons:commons-lang3:3.7"
    static final def EXPECTED_CONTENT_COMMONS_LANG3 = """[
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
]"""

    @Unroll
    def "it reads the correct project group from its parent when not available locally for #dependency"(
        String dependency, String expectedContent) {
        buildFile << """
            dependencies {
                forTesting "$dependency"
            }
        """

        when:
        def runResult = runGradleBuild(["--debug"])
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def pomGPath = resultFileGPath.configurations*.dependencies.flatten().poms.flatten()

        def pomsString = prettyPrintJson(pomGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        !runResult.output.contains("Use remote pom because the found pom seems not to represent artifact.")
        pomsString == expectedContent

        where:
        dependency                  | expectedContent
        DEPENDENCY_COMMONS_LANG3    | EXPECTED_CONTENT_COMMONS_LANG3
    }

    def "it ignores POM parsing errors"(){
        buildFile << """
            dependencies {
                forTesting "org.codehaus.woodstox:stax2-api:3.1.3.redhat-1" // has invalid pom.xml
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
    }

    def "it fetches remote POM if packaged one contains no license"(){
        buildFile << """
            dependencies {
                forTesting("org.opensaml:opensaml:2.6.4") {
                    transitive = false
                }
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def configurationsGPath = resultFileGPath.configurations[0].dependencies[0].poms
        def configurationsString = prettyPrintJson(configurationsGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS
        configurationsString == """[
    {
        "inceptionYear": "2006",
        "projectUrl": "http://opensaml.org/",
        "description": "\\n        The OpenSAML-J library provides tools to support developers working with the Security Assertion Markup Language\\n        (SAML).\\n    ",
        "name": "OpenSAML-J",
        "organization": {
            "url": "http://www.internet2.edu/",
            "name": "Internet2"
        },
        "licenses": [
            {
                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                "name": "Apache 2"
            }
        ]
    }
]"""
    }


    def "it reads dependencies correctly"() {
        buildFile << """
            dependencies {
                forTesting "org.springframework:spring-tx:3.2.3.RELEASE"
                forTesting "org.ehcache:ehcache:3.3.1"
                forTesting "org.apache.commons:commons-lang3:3.7"
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

        configurationsString == prettyPrintJson(jsonSlurper.parse("""[
    {
        "dependencies": [
            {
                "group": "aopalliance",
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
                "version": "1.0",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "http://aopalliance.sourceforge.net",
                        "description": "AOP Alliance",
                        "name": "AOP alliance",
                        "organization": null,
                        "licenses": [
                            {
                                "url": "",
                                "name": "Public Domain"
                            }
                        ]
                    }
                ],
                "licenseFiles": [

                ],
                "empty": false,
                "name": "aopalliance"
            },
            {
                "group": "commons-logging",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": "Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "1.1.1",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "Jakarta Commons Logging"
                    }
                ],
                "hasArtifactFile": true,
                "version": "1.1.1",
                "poms": [
                    {
                        "inceptionYear": "2001",
                        "projectUrl": "http://commons.apache.org/logging",
                        "description": "Commons Logging is a thin adapter allowing configurable bridging to other,\\n    well known logging systems.",
                        "name": "Commons Logging",
                        "organization": {
                            "url": "http://www.apache.org/",
                            "name": "The Apache Software Foundation"
                        },
                        "licenses": [
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-logging-1.1.1.jar/META-INF/LICENSE",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "commons-logging-1.1.1.jar/META-INF/NOTICE",
                                "license": null
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-logging"
            },
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
            },
            {
                "group": "org.ehcache",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": "Terracotta Inc., a wholly-owned subsidiary of Software AG USA, Inc.",
                        "hasPackagedLicense": true,
                        "version": "3.3.1",
                        "license": "LICENSE",
                        "description": "Ehcache is an open-source caching library, compliant with the JSR-107 standard.",
                        "url": "ehcache-3.3.1.jar/LICENSE.html",
                        "name": "ehcache 3"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.3.1",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "http://ehcache.org",
                        "description": "End-user ehcache3 jar artifact",
                        "name": "Ehcache",
                        "organization": {
                            "url": "http://terracotta.org",
                            "name": "Terracotta Inc., a wholly-owned subsidiary of Software AG USA, Inc."
                        },
                        "licenses": [
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "ehcache-3.3.1.jar/LICENSE",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "ehcache-3.3.1.jar/NOTICE",
                                "license": null
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "ehcache"
            },
            {
                "group": "org.slf4j",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": "SLF4J.ORG",
                        "hasPackagedLicense": false,
                        "version": "1.7.7",
                        "license": null,
                        "description": "The slf4j API",
                        "url": null,
                        "name": "slf4j-api"
                    }
                ],
                "hasArtifactFile": true,
                "version": "1.7.7",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "http://www.slf4j.org",
                        "description": "The slf4j API",
                        "name": "SLF4J API Module",
                        "organization": {
                            "url": "http://www.qos.ch",
                            "name": "QOS.ch"
                        },
                        "licenses": [
                            {
                                "url": "http://www.opensource.org/licenses/mit-license.php",
                                "name": "MIT License"
                            }
                        ]
                    }
                ],
                "licenseFiles": [

                ],
                "empty": false,
                "name": "slf4j-api"
            },
            {
                "group": "org.springframework",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": "3.2.3.RELEASE",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "spring-beans"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.2.3.RELEASE",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "https://github.com/SpringSource/spring-framework",
                        "description": "Spring Beans",
                        "name": "Spring Beans",
                        "organization": {
                            "url": "http://springsource.org/spring-framework",
                            "name": "SpringSource"
                        },
                        "licenses": [
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-beans-3.2.3.RELEASE.jar/META-INF/license.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-beans-3.2.3.RELEASE.jar/META-INF/notice.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "spring-beans"
            },
            {
                "group": "org.springframework",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": "3.2.3.RELEASE",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "spring-core"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.2.3.RELEASE",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "https://github.com/SpringSource/spring-framework",
                        "description": "Spring Core",
                        "name": "Spring Core",
                        "organization": {
                            "url": "http://springsource.org/spring-framework",
                            "name": "SpringSource"
                        },
                        "licenses": [
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-core-3.2.3.RELEASE.jar/META-INF/license.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-core-3.2.3.RELEASE.jar/META-INF/notice.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "spring-core"
            },
            {
                "group": "org.springframework",
                "manifests": [
                    {
                        "licenseUrl": null,
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": "3.2.3.RELEASE",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "spring-tx"
                    }
                ],
                "hasArtifactFile": true,
                "version": "3.2.3.RELEASE",
                "poms": [
                    {
                        "inceptionYear": "",
                        "projectUrl": "https://github.com/SpringSource/spring-framework",
                        "description": "Spring Transaction",
                        "name": "Spring Transaction",
                        "organization": {
                            "url": "http://springsource.org/spring-framework",
                            "name": "SpringSource"
                        },
                        "licenses": [
                            {
                                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                                "name": "The Apache Software License, Version 2.0"
                            }
                        ]
                    }
                ],
                "licenseFiles": [
                    {
                        "fileDetails": [
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-tx-3.2.3.RELEASE.jar/META-INF/license.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-tx-3.2.3.RELEASE.jar/META-INF/notice.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ]
                    }
                ],
                "empty": false,
                "name": "spring-tx"
            }
        ],
        "name": "forTesting"
    }
]""".toCharArray()))
    }


    static void removeDevelopers(Map rawFile) {
        rawFile.configurations*.dependencies.flatten().poms.flatten().each { it.remove("developers") }
    }
}
