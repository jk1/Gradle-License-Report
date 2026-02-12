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
package com.github.jk1.license.filter

import com.github.jk1.license.AbstractGradleRunnerFunctionalSpec
import org.gradle.testkit.runner.TaskOutcome

import static com.github.jk1.license.reader.ProjectReaderFuncSpec.removeDevelopers

class LicenseBundleNormalizerFuncSpec extends AbstractGradleRunnerFunctionalSpec {

    File licenseResultJsonFile
    File normalizerFile

    def setup() {
        licenseResultJsonFile = new File(outputDir, "index.json")

        normalizerFile = new File(testProjectDir, 'test-normalizer-config.json')

        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*
            import com.github.jk1.license.reader.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(outputDir.absolutePath)}"
                filters = new LicenseBundleNormalizer(bundlePath: "${fixPathForBuildFile(normalizerFile.absolutePath)}", createDefaultTransformationRules: false)
                renderers = [new JsonReportRenderer(onlyOneLicensePerModule: false), new RawProjectDataJsonRenderer()]
                configurations = ['forTesting']
            }
        """

        normalizerFile << """
            {
              "bundles" : [
                { "bundleName" : "apache1", "licenseName" : "Apache Software License, Version 1.1", "licenseUrl" : "https://www.apache.org/licenses/LICENSE-1.1" },
                { "bundleName" : "apache2", "licenseName" : "Apache License, Version 2.0", "licenseUrl" : "https://www.apache.org/licenses/LICENSE-2.0" },
                { "bundleName" : "cddl1", "licenseName" : "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0", "licenseUrl" : "https://opensource.org/licenses/CDDL-1.0" },
                { "bundleName" : "gpl2", "licenseName" : "GNU GENERAL PUBLIC LICENSE, Version 2", "licenseUrl" : "https://www.gnu.org/licenses/gpl-2.0" }
              ],
        """
    }

    def "normalizes dependencies by configured license name"() {
        buildFile << """
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-name: "The Apache Software License, Version 2.0"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-name: "Apache License, Version 2.0"
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseNamePattern" : ".*The Apache Software License, Version 2.0.*" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["https://www.apache.org/licenses/LICENSE-2.0", "http://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    def "normalizes dependencies by configured license url"() {
        buildFile << """
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-url: "http://www.apache.org/licenses/LICENSE-2.0.txt"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-url: "http://www.apache.org/licenses/LICENSE-2.0"
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseUrlPattern" : ".*www.apache.org/licenses/LICENSE-2.0.*" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies.size() == 2
        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["https://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    def "normalizes manifest with pom data"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.7" // has manifest and pom information
            }
        """
        normalizerFile << """
              "transformationRules" : [
                   { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2.*" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        def result = jsonSlurper.parse(licenseResultJsonFile)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        result.dependencies*.moduleLicenses.flatten()*.moduleLicense.toSet() == ["Apache License, Version 2.0"].toSet()
        result.dependencies*.moduleLicenses.flatten()*.moduleLicenseUrl.toSet() == ["https://www.apache.org/licenses/LICENSE-2.0"].toSet()
    }

    def "an error is raised when a normalizer file is specified but not available"() {
        normalizerFile.delete()

        when:
        runGradleBuild()

        then:
        thrown(Exception)
    }

    def "default filter file is used when nothing else specified"() {
        buildFile.setText("") // clear the file first
        buildFile << """
            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            configurations {
                forTesting
            }
            repositories {
                mavenCentral()
            }

            import com.github.jk1.license.filter.*
            import com.github.jk1.license.render.*
            licenseReport {
                outputDir = "${fixPathForBuildFile(licenseResultJsonFile.parentFile.absolutePath)}"
                filters = new LicenseBundleNormalizer()
                renderers = [new JsonReportRenderer(onlyOneLicensePerModule: false)]
                configurations = ['forTesting']
            }
            dependencies {
                forTesting "org.jetbrains:annotations:13.0"     // license-url: "http://www.apache.org/licenses/LICENSE-2.0.txt"
                forTesting "io.netty:netty-common:4.1.17.Final" // license-url: "http://www.apache.org/licenses/LICENSE-2.0"
                forTesting "org.apache.commons:commons-lang3:3.7"
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseResultJsonFile.text == """{
    "dependencies": [
        {
            "moduleName": "io.netty:netty-common",
            "moduleVersion": "4.1.17.Final",
            "moduleUrls": [
                "http://netty.io/"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        },
        {
            "moduleName": "org.apache.commons:commons-lang3",
            "moduleVersion": "3.7",
            "moduleUrls": [
                "http://commons.apache.org/proper/commons-lang/"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        },
        {
            "moduleName": "org.jetbrains:annotations",
            "moduleVersion": "13.0",
            "moduleUrls": [
                "http://www.jetbrains.org"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        }
    ],
    "importedModules": [
        
    ]
}"""
    }

    def "licenseFileDetails are extended with the license information"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.9"
                forTesting "org.apache.commons:commons-lang3:3.7"
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseFileContentPattern" : ".*Apache License, Version 2.0.*" }
              ]
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
                "file": "joda-time-2.9.9.jar/META-INF/LICENSE.txt",
                "license": "Apache License, Version 2.0"
            },
            {
                "licenseUrl": null,
                "file": "joda-time-2.9.9.jar/META-INF/NOTICE.txt",
                "license": null
            }
        ]
    },
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

    def "normalizes dependencies by configured license text and exports result to json"() {
        buildFile << """
            dependencies {
                forTesting "joda-time:joda-time:2.9.9"
                forTesting "org.apache.commons:commons-lang3:3.7"
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "apache2", "licenseFileContentPattern" : ".*Apache License, Version 2.0.*" }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        licenseResultJsonFile.text == """{
    "dependencies": [
        {
            "moduleName": "joda-time:joda-time",
            "moduleVersion": "2.9.9",
            "moduleUrls": [
                "http://www.joda.org/joda-time/"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache 2",
                    "moduleLicenseUrl": "http://www.apache.org/licenses/LICENSE-2.0.txt"
                },
                {
                    "moduleLicense": "Apache 2.0",
                    "moduleLicenseUrl": null
                },
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0"
                }
            ]
        },
        {
            "moduleName": "org.apache.commons:commons-lang3",
            "moduleVersion": "3.7",
            "moduleUrls": [
                "http://commons.apache.org/proper/commons-lang/"
            ],
            "moduleLicenses": [
                {
                    "moduleLicense": "Apache License, Version 2.0",
                    "moduleLicenseUrl": "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            ]
        }
    ],
    "importedModules": [
        
    ]
}"""
    }

    def "multiple license-details are added to the pom, when one pom-license contains multiple license infos"() {
        buildFile << """
            dependencies {
                forTesting "javax.annotation:javax.annotation-api:1.3.2" // CDDL + GPL
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "cddl1", "licenseNamePattern" : "CDDL . GPLv2 with classpath exception", "transformUrl" : false },
                { "bundleName" : "gpl2", "licenseNamePattern" : "CDDL . GPLv2 with classpath exception", "transformUrl" : false }
              ]
            }
        """

        when:
        def runResult = runGradleBuild()
        def resultFileGPath = jsonSlurper.parse(rawJsonFile)
        removeDevelopers(resultFileGPath)
        def pomGPath = resultFileGPath.configurations*.dependencies.flatten().poms.flatten().licenses.flatten()
        def pomsString = prettyPrintJson(pomGPath)

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        pomsString == """[
    {
        "url": "https://github.com/javaee/javax.annotation/blob/master/LICENSE",
        "name": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0"
    },
    {
        "url": "https://github.com/javaee/javax.annotation/blob/master/LICENSE",
        "name": "GNU GENERAL PUBLIC LICENSE, Version 2"
    }
]"""
    }

    def "multiple license-details are added to the files-structure, when one license-file contains multiple licenses"() {
        buildFile << """
            dependencies {
                forTesting "javax.annotation:javax.annotation-api:1.3.2" // CDDL + GPL
            }
        """
        normalizerFile << """
              "transformationRules" : [
                { "bundleName" : "cddl1", "licenseFileContentPattern" : ".*COMMON DEVELOPMENT AND DISTRIBUTION LICENSE .CDDL. Version 1.0.*" },
                { "bundleName" : "gpl2", "licenseFileContentPattern" : ".*The GNU General Public License .GPL. Version 2, June 1991.*" }
              ]
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
                "licenseUrl": "https://opensource.org/licenses/CDDL-1.0",
                "file": "javax.annotation-api-1.3.2.jar/META-INF/LICENSE.txt",
                "license": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0"
            },
            {
                "licenseUrl": "https://www.gnu.org/licenses/gpl-2.0",
                "file": "javax.annotation-api-1.3.2.jar/META-INF/LICENSE.txt",
                "license": "GNU GENERAL PUBLIC LICENSE, Version 2"
            }
        ]
    }
]"""
    }

    def "check modulePattern matching"() {
        def rules = [
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "empty", modulePattern: ""), // must never match
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "matchOrgGroup", modulePattern: "org[.]group:.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "matchModule", modulePattern: ".*:module:.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "matchOrgGroupModule", modulePattern: "org[.]group:module:.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "matchOtherName", modulePattern: "other:name:.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "catchAll", modulePattern: ".*")
        ]

        when:
        def matchers = LicenseBundleNormalizer.makeNormalizerTransformationRuleMatchers(rules)

        def orgGroup_Name_Matches = LicenseBundleNormalizer.transformationRulesFor(matchers, "org.group:name:1.0", null, null, {null})
        def orgGroup_Module_Matches = LicenseBundleNormalizer.transformationRulesFor(matchers, "org.group:module:1.0", null, null, {null})
        def orgFoo_Name_Matches = LicenseBundleNormalizer.transformationRulesFor(matchers, "org.foo:name:1.0", null, null, {null})
        def other_Name_Matches = LicenseBundleNormalizer.transformationRulesFor(matchers, "other:name:1.0", null, null, {null})

        then:
        orgGroup_Name_Matches.collect{it.bundleName} == ["matchOrgGroup", "catchAll"]
        orgGroup_Module_Matches.collect{it.bundleName} == ["matchOrgGroup", "matchModule", "matchOrgGroupModule", "catchAll"]
        orgFoo_Name_Matches.collect{it.bundleName} == ["catchAll"]
        other_Name_Matches.collect{it.bundleName} == ["matchOtherName", "catchAll"]
    }

    def "check licenseNamePattern matching"() {
        def rules = [
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "empty", licenseNamePattern: ""), // must never match
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "cddl", licenseNamePattern: ".*CDDL.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "dog", licenseNamePattern: ".*DOG.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "bear", licenseNamePattern: ".*BEAR.*")
        ]

        when:
        def matchers = LicenseBundleNormalizer.makeNormalizerTransformationRuleMatchers(rules)

        def just_cddl_1 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, "CDDL", null, {null})
        def just_cddl_2 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, "   \n    CDDL   \t\n", null, {null})
        def dog_and_bear = LicenseBundleNormalizer.transformationRulesFor(matchers, null, "   \n    DOG   \t\n   BEAR", null, {null})

        then:
        just_cddl_1.collect{it.bundleName} == ["cddl"]
        just_cddl_2.collect{it.bundleName} == ["cddl"]
        dog_and_bear.collect{it.bundleName} == ["dog", "bear"]
    }

    def "check licenseContentPattern matching"() {
        def rules = [
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "empty", licenseFileContentPattern: {""}), // must never match
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "cddl", licenseFileContentPattern: ".*CDDL.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "dog", licenseFileContentPattern: ".*DOG.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "bear", licenseFileContentPattern: ".*BEAR.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "sneaky", licenseFileContentPattern: "SNEAKY[LICENSE]")  // wouldn't match with Pattern, but substring works
        ]

        when:
        def matchers = LicenseBundleNormalizer.makeNormalizerTransformationRuleMatchers(rules)

        def just_cddl_1 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, null, {"CDDL"})
        def just_cddl_2 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, null, {"   \n    CDDL   \t\n"})
        def dog_and_bear = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, null, {"   \n    DOG   \t\n   BEAR"})

        then:
        just_cddl_1.collect{it.bundleName} == ["cddl"]
        just_cddl_2.collect{it.bundleName} == ["cddl"]
        dog_and_bear.collect{it.bundleName} == ["dog", "bear"]
    }

    def "check licenseUrlPattern matching"() {
        def rules = [
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "empty", licenseUrlPattern: ""), // must never match
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "cddl", licenseUrlPattern: ".*opensource\\.org/CDDL.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "dog", licenseUrlPattern: ".*opensource\\.org/DOG.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "bear", licenseUrlPattern: ".*http://opensource\\.org/BEAR.txt.*"),
                new LicenseBundleNormalizer.NormalizerTransformationRule(bundleName: "non_greedy", licenseUrlPattern: "http://opensource\\.org/BEAR.txt")
        ]

        when:
        def matchers = LicenseBundleNormalizer.makeNormalizerTransformationRuleMatchers(rules)

        def just_cddl_1 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, "https://www.opensource.org/CDDL-1.0.txt", {null})
        def just_cddl_2 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, "https://www.opensource.org/CDDL", {null})
        def just_cddl_3 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, "opensource.org/CDDL", {null})
        def just_cddl_4 = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, "   \n    opensource.org/CDDL   \t\n", {null})
        def dog_and_bear = LicenseBundleNormalizer.transformationRulesFor(matchers, null, null, "   \n    opensource.org/DOG   \t\n   http://opensource.org/BEAR.txt iwoefjoie fpokqwdofp kwpod kwpoqdkpowq dpoiwqjoipdjwqoi", {null})

        then:
        just_cddl_1.collect{it.bundleName} == ["cddl"]
        just_cddl_2.collect{it.bundleName} == ["cddl"]
        just_cddl_3.collect{it.bundleName} == ["cddl"]
        just_cddl_4.collect{it.bundleName} == ["cddl"]
        dog_and_bear.collect{it.bundleName} == ["dog", "bear"]
    }
}
