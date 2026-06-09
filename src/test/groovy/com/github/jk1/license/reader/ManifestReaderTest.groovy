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

import com.github.jk1.license.ManifestData
import org.gradle.api.artifacts.ResolvedArtifact
import spock.lang.Specification
import spock.lang.TempDir

class ManifestReaderTest extends Specification {

    @TempDir
    File tempDir

    ManifestReader reader = new ManifestReader(null)

    def "reads bundle attributes from a standalone .mf manifest file"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Example Bundle
            Bundle-SymbolicName: com.example.bundle
            Bundle-Version: 1.2.3
            Bundle-Description: An example OSGi bundle
            Bundle-Vendor: Example Inc.
            Bundle-DocURL: https://example.com/docs
            Bundle-License: Apache-2.0
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.name == "Example Bundle"
        data.version == "1.2.3"
        data.description == "An example OSGi bundle"
        data.vendor == "Example Inc."
        data.url == "https://example.com/docs"
        data.licenses.size() == 1
        data.licenses.first().name == "Apache-2.0"
        data.licenses.first().url == null
        !data.hasPackagedLicense
    }

    def "falls back to Implementation-Title and Implementation-Version when Bundle-* are missing"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Implementation-Title: Impl Title
            Implementation-Version: 4.5.6
            Implementation-Vendor: Impl Vendor
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.name == "Impl Title"
        data.version == "4.5.6"
        data.vendor == "Impl Vendor"
    }

    def "falls back to Bundle-SymbolicName and Specification-Version as last resort"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-SymbolicName: com.example.symbolic
            Specification-Version: 7.8.9
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.name == "com.example.symbolic"
        data.version == "7.8.9"
    }

    def "prefers Bundle-* attributes over Implementation-* fallbacks"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Bundle Name
            Implementation-Title: Impl Title
            Bundle-Version: 1.0.0
            Implementation-Version: 2.0.0
            Bundle-Vendor: Bundle Vendor
            Implementation-Vendor: Impl Vendor
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.name == "Bundle Name"
        data.version == "1.0.0"
        data.vendor == "Bundle Vendor"
    }

    def "parses Bundle-License URL with description parameter"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Licensed Bundle
            Bundle-License: https://www.apache.org/licenses/LICENSE-2.0.txt;description=Apache-2.0
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == "Apache-2.0"
        data.licenses.first().url == "https://www.apache.org/licenses/LICENSE-2.0.txt"
    }

    def "prefers link parameter as license url when both name and link are URLs"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Licensed Bundle
            Bundle-License: https://canonical.example.com/license;link=https://example.com/license.txt
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == null
        data.licenses.first().url == "https://example.com/license.txt"
    }

    def "uses link as license url and non-URL name as license identifier"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Licensed Bundle
            Bundle-License: Apache-2.0;link=https://www.apache.org/licenses/LICENSE-2.0.txt
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == "Apache-2.0"
        data.licenses.first().url == "https://www.apache.org/licenses/LICENSE-2.0.txt"
    }

    def "falls back to name when link parameter is not a URL"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Licensed Bundle
            Bundle-License: https://www.apache.org/licenses/LICENSE-2.0.txt;link=not-a-url
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == null
        data.licenses.first().url == "https://www.apache.org/licenses/LICENSE-2.0.txt"
    }

    def "parses Bundle-License URL without a description parameter"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Licensed Bundle
            Bundle-License: http://www.apache.org/licenses/LICENSE-2.0
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == null
        data.licenses.first().url == "http://www.apache.org/licenses/LICENSE-2.0"
    }

    def "treats non-URL Bundle-License value as the license identifier"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Plain License Bundle
            Bundle-License: MIT
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == "MIT"
        data.licenses.first().url == null
    }

    def "returns null when the artifact file has no extension"() {
        given:
        File noExt = new File(tempDir, "MANIFEST")
        noExt.text = "Manifest-Version: 1.0\n"

        expect:
        reader.readManifestData(artifactWith(noExt)) == null
    }

    def "returns null for unsupported file extensions"() {
        given:
        File txt = new File(tempDir, "something.txt")
        txt.text = "Manifest-Version: 1.0\n"

        expect:
        reader.readManifestData(artifactWith(txt)) == null
    }

    def "treats commas inside quoted Bundle-License clauses as part of the value, not separators"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Dual
            Bundle-License: "Apache License, Version 2.0";link="https://apache.org/license", "BSD 3-Clause";link="https://opensource.org/BSD-3-Clause"
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 2
        data.licenses*.name.toSorted() == ["Apache License, Version 2.0", "BSD 3-Clause"]
        data.licenses*.url.toSorted() == ["https://apache.org/license", "https://opensource.org/BSD-3-Clause"]
    }

    def "treats semicolons inside quoted Bundle-License values as part of the value, not parameter separators"() {
        given:
        File mf = manifestFile("""\
            Manifest-Version: 1.0
            Bundle-Name: Semi Quoted
            Bundle-License: "Apache-2.0; with modifier";link="https://apache.org/license"
            """.stripIndent())

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == "Apache-2.0; with modifier"
        data.licenses.first().url == "https://apache.org/license"
    }

    def "ignores splitting of Bundle-License if things don't 'look right'"() {
        given:
        File mf = manifestFile("""\
Manifest-Version: 1.0
Bundle-Name: Non Quoted with semicolons; not strictly formatted
Bundle-License: Apache License, Version 2.0; see: http://www.apache.or
 g/licenses/LICENSE-2.0.txt
""")

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.licenses.size() == 1
        data.licenses.first().name == "Apache License, Version 2.0; see: http://www.apache.org/licenses/LICENSE-2.0.txt"
        data.licenses.first().url == null
    }

    def "matches the .mf extension case-insensitively"() {
        given:
        File mf = new File(tempDir, "MANIFEST.MF")
        mf.text = """\
            Manifest-Version: 1.0
            Bundle-Name: Mixed Case Extension
            """.stripIndent()

        when:
        ManifestData data = reader.readManifestData(artifactWith(mf))

        then:
        data.name == "Mixed Case Extension"
    }

    private File manifestFile(String contents) {
        File mf = new File(tempDir, "MANIFEST.mf")
        mf.text = contents
        return mf
    }

    private ResolvedArtifact artifactWith(File file) {
        ResolvedArtifact artifact = Mock()
        artifact.getFile() >> file
        return artifact
    }
}
