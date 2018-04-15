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
                maven { url "https://dl.bintray.com/realm/maven" }
            }

            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "$outputDir.absolutePath"
                renderer = new com.github.jk1.license.render.RawProjectDataJsonRenderer()
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
]"""
    }


    static final def DEPENDENCY_REALM_ANDROID = "io.realm:realm-android:0.82.2"
    static final def EXPECTED_ARTIFACT_MISMATCH_REALM_ANDROID = "Artifact: io.realm:realm-android / Pom: com.squareup:javawriter)"
    static final def EXPECTED_CONTENT_REALM_ANDROID = """[
    {
        "inceptionYear": "",
        "projectUrl": "http://realm.io",
        "description": "Realm is a mobile database: a replacement for SQLite & ORMs.",
        "name": "realm-android",
        "organization": null,
        "licenses": [
            {
                "comments": "",
                "distribution": "repo",
                "url": "http://www.apache.org/licenses/LICENSE-2.0.txt",
                "name": "The Apache Software License, Version 2.0"
            }
        ]
    }
]"""

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
                "comments": "",
                "distribution": "repo",
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
                "comments": "",
                "distribution": "repo",
                "url": "http://www.opensource.org/licenses/mit-license.php",
                "name": "MIT License"
            }
        ]
    }
]"""

    @Unroll
    def "it reads the correct project url for #dependency"(String dependency, String expectedContent,
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
        DEPENDENCY_REALM_ANDROID    | EXPECTED_CONTENT_REALM_ANDROID    | EXPECTED_ARTIFACT_MISMATCH_REALM_ANDROID
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
                "comments": "",
                "distribution": "repo",
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

        configurationsString == """[
    {
        "dependencies": [
            {
                "group": "org.ehcache",
                "manifests": [
                    {
                        "vendor": "Terracotta Inc., a wholly-owned subsidiary of Software AG USA, Inc.",
                        "hasPackagedLicense": true,
                        "version": "3.3.1",
                        "license": "LICENSE",
                        "description": "Ehcache is an open-source caching library, compliant with the JSR-107 standard.",
                        "url": "ehcache-3.3.1.jar/LICENSE.html",
                        "name": "ehcache 3"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "ehcache-3.3.1.jar/LICENSE",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "ehcache-3.3.1.jar/NOTICE",
                                "license": null
                            }
                        ],
                        "files": [
                            "ehcache-3.3.1.jar/LICENSE",
                            "ehcache-3.3.1.jar/NOTICE"
                        ]
                    }
                ],
                "empty": false,
                "name": "ehcache"
            },
            {
                "group": "aopalliance",
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
                                "comments": "",
                                "distribution": "",
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
                "group": "org.slf4j",
                "manifests": [
                    {
                        "vendor": "SLF4J.ORG",
                        "hasPackagedLicense": false,
                        "version": "1.7.7",
                        "license": null,
                        "description": "The slf4j API",
                        "url": null,
                        "name": "slf4j-api"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": "3.2.3.RELEASE",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "spring-core"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-core-3.2.3.RELEASE.jar/META-INF/notice.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-core-3.2.3.RELEASE.jar/META-INF/license.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "spring-core-3.2.3.RELEASE.jar/META-INF/notice.txt",
                            "spring-core-3.2.3.RELEASE.jar/META-INF/license.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "spring-core"
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
            },
            {
                "group": "org.springframework",
                "manifests": [
                    {
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": "3.2.3.RELEASE",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "spring-beans"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-beans-3.2.3.RELEASE.jar/META-INF/notice.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-beans-3.2.3.RELEASE.jar/META-INF/license.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "spring-beans-3.2.3.RELEASE.jar/META-INF/notice.txt",
                            "spring-beans-3.2.3.RELEASE.jar/META-INF/license.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "spring-beans"
            },
            {
                "group": "commons-logging",
                "manifests": [
                    {
                        "vendor": "Apache Software Foundation",
                        "hasPackagedLicense": false,
                        "version": "1.1.1",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "Jakarta Commons Logging"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "commons-logging-1.1.1.jar/META-INF/LICENSE",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": null,
                                "file": "commons-logging-1.1.1.jar/META-INF/NOTICE",
                                "license": null
                            }
                        ],
                        "files": [
                            "commons-logging-1.1.1.jar/META-INF/LICENSE",
                            "commons-logging-1.1.1.jar/META-INF/NOTICE"
                        ]
                    }
                ],
                "empty": false,
                "name": "commons-logging"
            },
            {
                "group": "org.springframework",
                "manifests": [
                    {
                        "vendor": null,
                        "hasPackagedLicense": false,
                        "version": "3.2.3.RELEASE",
                        "license": null,
                        "description": null,
                        "url": null,
                        "name": "spring-tx"
                    }
                ],
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
                                "comments": "",
                                "distribution": "repo",
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
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-tx-3.2.3.RELEASE.jar/META-INF/notice.txt",
                                "license": "Apache License, Version 2.0"
                            },
                            {
                                "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0",
                                "file": "spring-tx-3.2.3.RELEASE.jar/META-INF/license.txt",
                                "license": "Apache License, Version 2.0"
                            }
                        ],
                        "files": [
                            "spring-tx-3.2.3.RELEASE.jar/META-INF/notice.txt",
                            "spring-tx-3.2.3.RELEASE.jar/META-INF/license.txt"
                        ]
                    }
                ],
                "empty": false,
                "name": "spring-tx"
            }
        ],
        "name": "forTesting"
    }
]"""
    }


    private static void removeDevelopers(Map rawFile) {
        rawFile.configurations*.dependencies.flatten().poms.flatten().each { it.remove("developers") }
    }
}
