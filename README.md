# Gradle License Report

[![Build Status](https://travis-ci.org/jk1/Gradle-License-Report.svg?branch=master)](https://travis-ci.org/jk1/Gradle-License-Report)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A plugin for generating reports about the licenses of the dependencies for your Gradle project.

This plugin will resolve all your dependencies, and then scour them for anything that looks like relevant licensing information. The theory is
to automatically generate a report that you can hand to corporate IP lawyers in order to keep them busy.

## Usage

Add this to your `build.gradle` file for Gradle 7+:

```groovy
plugins {
    id 'com.github.jk1.dependency-license-report' version '3.0.2-SNAPSHOT'  // x-release-please-version
}
```

Please note, for Gradle 9+ it is necessary to specify `--no-parallel` for the moment, to avoid errors like:

> Resolution of the configuration ':<something>:runtimeClasspath' was attempted without an exclusive lock. This is unsafe and not allowed

For Gradle 6.X stick to 1.X plugin versions:

```groovy
plugins {
    id 'com.github.jk1.dependency-license-report' version '1.17'
}
```

Then run `gradle generateLicenseReport` to generate your report in `build/reports/dependency-license`.

## Configuration

Configuration described below is entirely optional.

```groovy
import com.github.jk1.license.render.*
import com.github.jk1.license.importer.*

licenseReport {
    // By default this plugin will collect the union of all licenses from
    // the immediate pom and the parent poms. If your legal team thinks this
    // is too liberal, you can restrict collected licenses to only include the
    // those found in the immediate pom file
    // Defaults to: true
    unionParentPomLicenses = false

    // Set output directory for the report data.
    // Defaults to ${project.buildDir}/reports/dependency-license.
    outputDir = project.layout.buildDirectory.dir("licenses").get().asFile.path

    // Select projects to examine for dependencies.
    // Defaults to current project and all its subprojects
    projects = [project] + project.subprojects

    // Select projects to examine their buildScripts / plugins for dependencies.
    // Defaults to nothing. Could be configured like [project] + project.subprojects
    buildScriptProjects = []

    // Adjust the configurations to fetch dependencies. Default is 'runtimeClasspath'
    // For Android projects use 'releaseRuntimeClasspath' or 'yourFlavorNameReleaseRuntimeClasspath'
    // Use 'ALL' to dynamically resolve all configurations:
    // configurations = ALL
    configurations = ['runtimeClasspath']

    // List the groups ids to exclude from dependency report. Supports regular expressions.
    // For finer granularity, see: excludes.
    excludeGroups = ['do.not.want']

    // List the ids (in module:name format) to exclude from dependency report. Supports regular expressions.
    // By default excludes is empty.
    excludes = ['moduleGroup:moduleName']

    // Don't include artifacts of project's own group into the report
    excludeOwnGroup = true

    // Don't exclude bom dependencies.
    // If set to true, then all boms will be excluded from the report
    excludeBoms = false

    // Set custom report renderer, implementing ReportRenderer.
    // Yes, you can write your own to support any format necessary.
    renderers = [new XmlReportRenderer('third-party-libs.xml', 'Back-End Libraries')]

    // Set importers to import any external dependency information, i.e. from npm.
    // Custom importer should implement DependencyDataImporter interface.
    importers = [new XmlReportImporter('Frontend dependencies', file(frontend_libs.xml))]

    // This is for the allowed-licenses-file in checkLicense Task
    // Accepts File, URL or String path to local or remote file
    allowedLicensesFile = project.layout.projectDirectory.file("config/allowed-licenses.json").asFile
}
```

## My report is empty or contains wrong dependencies. Is it a plugin bug?

The plugin discovers project dependencies from certain [Gradle configurations](https://docs.gradle.org/current/userguide/declaring_dependencies.html).
To put it (overly) simple, a configuration is a set of dependencies used for a particular purpose: compilation, testing, runtime, you name it.
The plugin lets you configure which configurations you'd like to report dependencies from. Although it assumes reasonable defaults, for complex builds they may not always suffice.
Custom configurations may come from the other plugins, build flavors and dimensions. One can even define their own configurations right in the build script.

If unsure, check out `gradlew dependencies` task output to see what configurations you project has.

## Kotlin script support

Plugin is compatible with build scripts written in kotlin. Configuration syntax is slightly different from groovy though.
Consider the following sample:

```kotlin
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer

plugins {
    id("com.github.jk1.dependency-license-report") version "2.0"
}

licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("report.html", "Backend"))
    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
}

```

## Renderers

Renderers define how a final dependency report will look like. Plugin comes with
[a number of predefined renderers](https://github.com/jk1/Gradle-License-Report/tree/master/src/main/groovy/com/github/jk1/license/render)
for text, html, xml and other popular presentation formats. It's also possible to create a custom renderer for the report.

All the renderers support report file name customization via constructor parameter:

```groovy
import com.github.jk1.license.render.*

licenseReport {
    renderers = [new XmlReportRenderer('third-party-libs.xml', 'Back-End Libraries')]
}
```

To get the report generated in multiple formats just list them:

```groovy
import com.github.jk1.license.render.*

licenseReport {
    renderers = [new XmlReportRenderer(), new CsvReportRenderer()]
}
```

### InventoryHtmlReportRender

The InventoryHtmlReportRender renders a report grouped by license type, so you can more easily identify which dependencies
share the same license. This makes it easier to know the individual licenses you need to verify with your legal department.
To use this report you simply add it to the configuration:

```groovy
import com.github.jk1.license.render.*

licenseReport {
    renderers = [new InventoryHtmlReportRenderer()]
}
```

This defaults to using the name of the project as the title and index.html as the name of the file it creates. You can
change this by passing additional arguments. The first argument is the filename to write out, and the 2nd is the title
to use in the report. For dependencies that don't declare their license they will be listed underneath the `Unknown`
license group. You can provide the license information for these dependencies statically using the `overridesFilename`.
The overrides file is a pipe-separated value file with the columns for `Dependency Name`,`Project URL`,`License`, and
`License URL`, respectively. Here is an example of the contents of the override file:

```
com.google.code.gson:gson:2.5|https://github.com/google/gson|The Apache Software License, Version 2.0|https://github.com/google/gson/blob/master/LICENSE
org.springframework.security:spring-security-core:3.2.9.RELEASE|https://github.com/spring-projects/spring-security|The Apache Software License, Version 2.0|https://github.com/spring-projects/spring-security/blob/master/license.txt
org.springframework.security:spring-security-acl:3.2.9.RELEASE|https://github.com/spring-projects/spring-security|The Apache Software License, Version 2.0|https://github.com/spring-projects/spring-security/blob/master/license.txt
```

There are no column headers on this file. Here is the example of how to config the InventoryHtmlReportRenderer to use
an overrides file:

```groovy
import com.github.jk1.license.render.*

licenseReport {
    renderers = [new InventoryHtmlReportRenderer('index.html', 'Some Title', new File(projectDir, '../unknown-license-details.txt'))]
}
```

## Importers

Importer adds license information from an external source to your report. Importer may come in handy if

- some modules within your application use their own means of library dependency resolution, e.g. npm registry
- your application integrates third party components or services with their own library dependencies
- joint report for a multimodule project is required

The following example demonstrates how to use an importer:

```groovy
import com.github.jk1.license.importer.*

licenseReport {
    // integrate javascript frontend dependencies into our report
    importers = [new XmlReportImporter('Front End', new File(projectDir, 'src/main/webapp/vendor/front_end.xml'))]
}
```

The expected input format for `XmlReportImporter` is as follows:

```xml
<topic>
  <chunk>
    <chapter title="Some of my favorite libraries">
      <table>
        <tr>
          <!-- every non-empty chapter must have a title row, which is stripped -->
          <th>Name</th>
          <th>Version</th>
          <th>License</th>
        </tr>
        <tr>
          <td><a href="https://url.of.project/homepage">Name of library</a></td>
          <td>1.2.3</td>
          <td><a href="http://url.of.project/license">Name of license</a></td>
        </tr>
        <!-- more libraries here... -->
      </table>
    </chapter>
    <!-- more chapters here...-->
  </chunk>
  <!-- more chunks here... -->
</topic>
```

If there is only one chapter, the outer `topic` and `chunk` tags may be omitted.

## Filters

Dependency filters transform discovered dependency data before rendering.
This may include sorting, reordering, data substitution.

### Excluding transitive dependencies

The following filter will leave only first-level dependencies in the report:

```groovy
import com.github.jk1.license.filter.*

licenseReport {
    filters = [new ExcludeTransitiveDependenciesFilter()]
}
```

### License data grouping

This feature was contributed by [Günther Grill](https://github.com/guenhter)

When multiple dependencies are analysed and displayed in a report, often
e.g. two licenses like the following one appears:

```text
The Apache Software License, Version 2.0
Apache License, Version 2.0
```

This can be avoided by providing an accurate normalisation file which contains rules
to unify such entries. The configuration file has two sections:

- license-bundles: Defines the actual licenses with their correct name and their correct url
- transformation-rules: A rule defines a reference to one license-bundle and a pattern for
  a malformed name or url. When a pattern matches the license of a dependency, the
  output license-information for that dependency will be updated with the referenced license-bundle.

```json
{
  "bundles": [
    {
      "bundleName": "apache1",
      "licenseName": "Apache Software License, Version 1.1",
      "licenseUrl": "http://www.apache.org/licenses/LICENSE-1.1"
    },
    {
      "bundleName": "apache2",
      "licenseName": "Apache License, Version 2.0",
      "licenseUrl": "http://www.apache.org/licenses/LICENSE-2.0"
    },
    {
      "bundleName": "cddl1",
      "licenseName": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0 (CDDL-1.0)",
      "licenseUrl": "http://opensource.org/licenses/CDDL-1.0"
    }
  ],
  "transformationRules": [
    {
      "bundleName": "apache2",
      "licenseNamePattern": ".*The Apache Software License, Version 2.0.*"
    },
    { "bundleName": "apache2", "licenseNamePattern": "Apache 2" },
    {
      "bundleName": "apache2",
      "licenseUrlPattern": "http://www.apache.org/licenses/LICENSE-2.0.txt"
    },
    {
      "bundleName": "apache2",
      "licenseNamePattern": "Special Apache",
      "transformUrl": false
    },
    {
      "bundleName": "apache2",
      "licenseNamePattern": "Keep this name",
      "transformName": false
    }
  ]
}
```

So dependencies with license-name `The Apache Software License, Version 2.0` / `Apache 2` or license-url `http://www.apache.org/licenses/LICENSE-2.0.txt`
are changed to license-name `Apache License, Version 2.0` and license-url `http://www.apache.org/licenses/LICENSE-2.0`

The normalizer can be enabled via a filter.

```groovy
import com.github.jk1.license.filter.*

licenseReport {
    // LicenseBundleNormalizer also accepts bundle stream as a parameter
    filters = [new LicenseBundleNormalizer(bundlePath: "$projectDir/config/license-normalizer-bundle.json")]
}
```

If no bundle-file is specified, a default file is used containing some common rules. You are encouraged to create your own bundle-file
and contribute back useful rules.

### SPDX support

Normalizers are also capable of mapping licenses to SPDX identifiers. The code

```groovy
import com.github.jk1.license.filter.*

licenseReport {
    filters = [new SpdxLicenseBundleNormalizer()]
}
```

replaces string license names in the report with the corresponding [SPDX IDs](https://spdx.org/licenses/)

## Writing custom renderers, importers and filters

It's also possible to implement a custom importer to support any dependency data format necessary. To do so put custom
importer implementation inside `buildSrc` folder:

```java
package org.sample;

import com.github.jk1.license.ImportedModuleBundle;
import com.github.jk1.license.importer.DependencyDataImporter;
import java.util.Collection;

public class CustomImporter implements DependencyDataImporter {

    public String getImporterName() {
        return "Custom importer";
    }


    public Collection<ImportedModuleBundle> doImport() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
```

with `buildSrc/build.gradle` defined as follows to get all the imports resolved:

```groovy
apply plugin: 'java'

repositories {
    maven {
        url 'https://plugins.gradle.org/m2/'
    }
}

dependencies {
    compile 'com.github.jk1:gradle-license-report:2.0'
}

```

Now you can use your custom importer in the main build:

```groovy
import org.sample.CustomImporter

licenseReport {
    importers = [new CustomImporter()]
}

```

The same technique can be used to create a filter or a renderer to support custom report formats.

## Check Dependency Licenses

This task is for checking dependencies/imported modules if their licenses are allowed to be used.

```shell
./gradlew checkLicense
```

If there are not allowed licenses, the task will fail and a report like the following
will be generated under `$outputDir` which you specified in the configuration:

```json
{
  "dependenciesWithoutAllowedLicenses": [
    {
      "moduleLicense": "Apache License, Version 2.0",
      "moduleName": "org.jetbrains.kotlin:kotlin-stdlib",
      "moduleVersion": "1.2.51"
    },
    {
      "moduleLicense": "Apache License, Version 2.0",
      "moduleName": "org.jetbrains.kotlin:kotlin-stdlib-common",
      "moduleVersion": "1.2.51"
    }
  ]
}
```

### Allowed licenses file

Defines which licenses are allowed to be used:

```json
{
  "allowedLicenses": [
    {
      "moduleLicense": "Apache License, Version 2.0",
      "moduleName": "org.jetbrains.kotlin:kotlin-stdlib",
      "moduleVersion": "1.2.60"
    },
    {
      "moduleLicense": "Apache License, Version 2.0",
      "moduleName": "org.jetbrains.kotlin*",
      "moduleVersion": ".*"
    },
    {
      "moduleLicense": "MIT License",
      "moduleName": ".*"
    },
    {
      "moduleLicense": "MIT License"
    },
    {
      "moduleLicense": "MIT License",
      "moduleName": ""
    }
  ]
}
```

Also specify the allowed license file in the configuration:

```groovy
licenseReport {
    allowedLicensesFile = new File("$projectDir/config/allowed-licenses.json")
}
```

or from a remote resource

```groovy
licenseReport {
    allowedLicensesFile = resources.text.fromUri('https://company.com/licenses/allowed-licenses.json')
}
```
