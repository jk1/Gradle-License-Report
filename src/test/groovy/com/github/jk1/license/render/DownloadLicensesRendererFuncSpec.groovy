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
package com.github.jk1.license.render

import com.github.jk1.license.AbstractGradleRunnerFunctionalSpec
import org.gradle.testkit.runner.TaskOutcome

class DownloadLicensesRendererFuncSpec extends AbstractGradleRunnerFunctionalSpec {
    def setup() {
        buildFile << """
            import com.github.jk1.license.render.*

            plugins {
                id 'com.github.jk1.dependency-license-report'
            }
            
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }

            licenseReport {
                outputDir = "$outputDir.absolutePath"
                renderers = new DownloadLicensesRenderer()
            }
        """
    }

    def "it stores the licenses of a jar-file and download licenses from manifest or pom into the output-dir"() {
        buildFile << """
            dependencies {
                compile "org.apache.commons:commons-lang3:3.7" // has NOTICE.txt and LICENSE.txt and downloaded licenses files
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        new File(outputDir, "org.apache.commons_commons-lang3_3.7/META-INF/NOTICE.txt").exists()
        new File(outputDir, "org.apache.commons_commons-lang3_3.7/META-INF/LICENSE.txt").exists()
        new File(outputDir, "org.apache.commons_commons-lang3_3.7/DOWNLOADED-MANIFEST-LICENSES/LICENSE_2_0_txt.txt").exists()
        new File(outputDir, "org.apache.commons_commons-lang3_3.7/DOWNLOADED-POM-LICENSES/Apache_License_Version_2_0.txt").exists()
        new File(outputDir, "automatic-included-license-files-report.json").text ==
        """{
    "noLicenseFileDependencies": [
        
    ],
    "noLicenseFileImportedModules": [
        
    ],
    "downloadedHtmlLicenseFileDirectories": [
        
    ],
    "downloadedTextLicenseFileDirectories": [
        "org.apache.commons_commons-lang3_3.7/DOWNLOADED-MANIFEST-LICENSES/LICENSE_2_0_txt.txt",
        "org.apache.commons_commons-lang3_3.7/DOWNLOADED-POM-LICENSES/Apache_License_Version_2_0.txt"
    ],
    "embeddedLicenseFileDirectories": [
        "org.apache.commons_commons-lang3_3.7/META-INF/LICENSE.txt",
        "org.apache.commons_commons-lang3_3.7/META-INF/NOTICE.txt"
    ]
}"""
        new File(outputDir, "automatic-included-license-html-files-contents.html").text ==
        """"""


    }

    def "it stores the licenses of a jar-file and embedded manifest license into the output-dir"() {
        buildFile << """
            dependencies {
                compile 'org.ehcache:ehcache:3.3.1' // has NOTICE.txt and LICENSE.txt and embedded manifest license
            }
        """

        when:
        def runResult = runGradleBuild()

        then:
        runResult.task(":generateLicenseReport").outcome == TaskOutcome.SUCCESS

        new File(outputDir, "org.ehcache_ehcache_3.3.1/EMBEDDED-MANIFEST-LICENSES/LICENSE").exists()
        new File(outputDir, "org.ehcache_ehcache_3.3.1/LICENSE").exists()
        new File(outputDir, "org.ehcache_ehcache_3.3.1/NOTICE").exists()
        new File(outputDir, "org.slf4j_slf4j-api_1.7.7/DOWNLOADED-POM-LICENSES/MIT_License.html").exists()
        new File(outputDir, "automatic-included-license-files-report.json").text ==
        """{
    "noLicenseFileDependencies": [
        
    ],
    "noLicenseFileImportedModules": [
        
    ],
    "downloadedHtmlLicenseFileDirectories": [
        "org.ehcache_ehcache_3.3.1/DOWNLOADED-MANIFEST-LICENSES/LICENSE.html",
        "org.slf4j_slf4j-api_1.7.7/DOWNLOADED-POM-LICENSES/MIT_License.html"
    ],
    "downloadedTextLicenseFileDirectories": [
        "org.ehcache_ehcache_3.3.1/DOWNLOADED-POM-LICENSES/The_Apache_Software_License_Version_2_0.txt"
    ],
    "embeddedLicenseFileDirectories": [
        "org.ehcache_ehcache_3.3.1/EMBEDDED-MANIFEST-LICENSES/LICENSE",
        "org.ehcache_ehcache_3.3.1/LICENSE",
        "org.ehcache_ehcache_3.3.1/NOTICE"
    ]
}"""
        new File(outputDir, "automatic-included-license-html-files-contents.html").text ==
        """<html><body><div><a href = "LICENSE">LICENSE</a></div></body></html>
<html><body><div><a href = "http://www.opensource.org/licenses/mit-license.php">http://www.opensource.org/licenses/mit-license.php</a></div></body></html>
"""

    }

}
