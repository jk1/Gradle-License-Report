package com.github.jk1.license.filter

import com.github.jk1.license.ProjectBuilder
import com.github.jk1.license.ProjectData
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static com.github.jk1.license.ProjectBuilder.json
import static com.github.jk1.license.ProjectDataFixture.*

class LicenseBundleNormalizerSpec extends Specification {
    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File normalizerFile
    File pluginOutputDir

    ProjectBuilder builder = new ProjectBuilder()

    def setup() {
        testProjectDir.create()
        pluginOutputDir = new File(GRADLE_PROJECT().licenseReport.outputDir)
        pluginOutputDir.mkdirs()

        normalizerFile = testProjectDir.newFile('test-normalizer-config.json')
        normalizerFile << """
            {
              "bundles" : [
                { "bundleName" : "apache2", "licenseName" : "Apache License, Version 2.0", "licenseUrl" : "https://www.apache.org/licenses/LICENSE-2.0" }
              ]"""

        // copy apache2 license file
        def apache2LicenseFile = new File(getClass().getResource("/apache2-license.txt").toURI())
        new File(pluginOutputDir, "apache2-license.txt") << apache2LicenseFile.text
    }

    def "normalizer constructor can be called with named parameters"() {
        when:
        new LicenseBundleNormalizer()
        new LicenseBundleNormalizer(bundlePath: null)
        new LicenseBundleNormalizer(createDefaultTransformationRules: false)
        new LicenseBundleNormalizer(bundlePath: null, createDefaultTransformationRules: false)

        then:
        noExceptionThrown()
    }

    def "normalize license of manifest (when stored as name)"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2.*" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache 2")
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache License, Version 2.0")
                    }
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "normalize the manifests license or to the appropriate bundle-license-name"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseUrlPattern" : "http://www.apache.org/licenses/LICENSE-2.0.*" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache License, Version 2.0")
                    }
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "all poms of all configurations are normalized"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2.*" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), name: "Apache 2")
                    }
                }
            }
            configuration("test") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), name: "Apache 2")
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                }
            }
            configuration("test") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                    }
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "all manifests of all configurations are normalized"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2.*" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache 2")
                    }
                }
            }
            configuration("test") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache 2")
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache License, Version 2.0")
                    }
                }
            }
            configuration("test") {
                module("mod1") {
                    manifest("mani1") {
                        license("Apache License, Version 2.0")
                    }
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "all license files of all configurations are normalized"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseFileContentPattern" : ".*Apache License, Version 2.0.*" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt")
                }
            }
            configuration("test") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt")
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            configuration("test") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "the licence-file is normalized by name-pattern (not by content-pattern)"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2.*" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt", license: "Apache 2")
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "the licence-file is normalized by url-pattern (not by content-pattern)"() {
        normalizerFile << """,
            "transformationRules" : [
                { "bundleName" : "apache2", "licenseUrlPattern" : "http://www.apache.org/licenses/LICENSE-2.0.txt" }
              ]
            }"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt", licenseUrl: "http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    licenseFile(file: "apache2-license.txt", license: "Apache License, Version 2.0", licenseUrl: "https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }

        when:
        def result = newNormalizer().filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "a transformation rule is created for each licence-bundle-name as exact match"() {
        normalizerFile << """}"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), url: "different url") // should be normalized because name matches the bundle-name
                        license(APACHE2_LICENSE(), name: "Apache 2.0", url: "different url")   // should stay, because name is different
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                        license(APACHE2_LICENSE(), name: "Apache 2.0", url: "different url")
                    }
                }
            }
        }

        when:
        def result = newNormalizer(true).filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "a transformation rule is created for each licence-bundle-url as exact match"() {
        normalizerFile << """}"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), name: "different name") // should be normalized because url matches the bundle-url
                        license(APACHE2_LICENSE(), name: "Apache 2.0", url: "different url")   // should stay, because url is different
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE())
                        license(APACHE2_LICENSE(), name: "Apache 2.0", url: "different url")
                    }
                }
            }
        }

        when:
        def result = newNormalizer(true).filter(projectData)

        then:
        json(result) == json(expected)
    }

    def "not creating default transformation rules when turned off"() {
        normalizerFile << """}"""

        ProjectData projectData = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), url: "different url")
                        license(APACHE2_LICENSE(), name: "different name")
                        license(APACHE2_LICENSE(), name: "Apache 2.0", url: "different url")
                    }
                }
            }
        }
        ProjectData expected = builder.project {
            configuration("runtime") {
                module("mod1") {
                    pom("pom1") {
                        license(APACHE2_LICENSE(), url: "different url")
                        license(APACHE2_LICENSE(), name: "different name")
                        license(APACHE2_LICENSE(), name: "Apache 2.0", url: "different url")
                    }
                }
            }
        }

        when:
        def result = newNormalizer(false).filter(projectData)

        then:
        json(result) == json(expected)
    }

    private LicenseBundleNormalizer newNormalizer(boolean createDefaultTransformationRules = false) {
        new LicenseBundleNormalizer(bundlePath: normalizerFile.absolutePath, createDefaultTransformationRules: createDefaultTransformationRules)
    }
}
