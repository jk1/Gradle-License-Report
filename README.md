Gradle License Report
=====================

[![Build Status](https://travis-ci.org/jk1/Gradle-License-Report.svg?branch=master)](https://travis-ci.org/jk1/Gradle-License-Report)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A plugin for generating reports about the licenses of the dependencies for your Gradle project.
This plugin is a fork of https://github.com/RobertFischer/Gradle-License-Report.

This plugin will resolve all your dependencies, and then scour them for anything that looks like relevant licensing information. The theory is
to automatically generate a report that you can hand to corporate IP lawyers in order to keep them busy.

## Usage

Add this to your `build.gradle` file:

```groovy
plugins {
  id 'com.github.jk1.dependency-license-report' version '1.3'
}
```

or via a `buildscript` block


```groovy
buildscript {
    repositories {
        maven {
            url 'https://plugins.gradle.org/m2/'
        }
    }

    dependencies {
        classpath 'gradle.plugin.com.github.jk1:gradle-license-report:1.3'
    }
}
apply plugin: 'com.github.jk1.dependency-license-report'
```

Then run `gradle generateLicenseReport` to generate your report in `build/reports/dependency-license`.

## Configuration

Configuration described below is entirely optional.

```groovy
import com.github.jk1.license.render.*
import com.github.jk1.license.importer.*

licenseReport {
    // Set output directory for the report data.
    // Defaults to ${project.buildDir}/reports/dependency-license.
    outputDir = "$projectDir/build/licenses"
    
    // List the groups ids to exclude from dependency report.
    // By default project's own group is excluded.
    // For finer granularity, see: excludes.
    excludeGroups = ['do.not.want']

    // List the ids (in module:name format) to exclude from dependency report.
    // By default excludes is empty.
    excludes = ['moduleGroup:moduleName']

    // Set custom report renderer, implementing ReportRenderer.
    // Yes, you can write your own to support any format necessary.
    renderers = [new XmlReportRenderer('third-party-libs.xml', 'Back-End Libraries')]

    // Set importers to import any external dependency information, i.e. from npm.
    // Custom importer should implement DependencyDataImporter interface.
    importers = [new XmlReportImporter('Frontend dependencies', file(frontend_libs.xml))]

    // Select projects to examine for dependencies.
    // Defaults to current project and all its subprojects
    projects = [project] + project.subprojects

    // Adjust the configurations to use, e.g. for Android projects.
    configurations = ['compile']
    // Use 'ALL' to dynamically resolve all configurations:
    // configurations = ALL
}
```

## Kotlin script support

Plugin is compatible with build scripts written in kotlin. Configuration syntax is slightly different from groovy though.
Consider the following sample:

```kotlin
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.filter.LicenseBundleNormalizer

plugins {
    id("com.github.jk1.dependency-license-report") version "1.3"
}

licenseReport {
    renderers = arrayOf(InventoryHtmlReportRenderer("report.html","Backend"))
    filters = arrayOf(LicenseBundleNormalizer())
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

The InventoryHtmlReportRender renders a report grouped by license type so you can more easily identify which dependencies
share the same license.  This makes it easier to know the individual licenses you need to verify with your legal department.
To use this report you simply add it to the configuration:

```groovy
import com.github.jk1.license.render.*

licenseReport {
    renderers = [new InventoryHtmlReportRenderer()]
}
```
This defaults to using the name of the project as the title and index.html as the name of the file it creates.  You can
change this by passing additional arguments.  The first argument is the filename to write out, and the 2nd is the title
to use in the report.  For dependencies that don't declare their license they will be listed underneath the `Unknown`
license group.  You can provide the license information for these dependencies statically using the `overridesFilename`.
The overrides file is a pipe-separated value file with the columns for `Dependency Name`,`Project URL`,`License`, and
`License URL`, respectively. Here is an example of the contents of the override file:

```
com.google.code.gson:gson:2.5|https://github.com/google/gson|The Apache Software License, Version 2.0|https://github.com/google/gson/blob/master/LICENSE
org.springframework.security:spring-security-core:3.2.9.RELEASE|https://github.com/spring-projects/spring-security|The Apache Software License, Version 2.0|https://github.com/spring-projects/spring-security/blob/master/license.txt
org.springframework.security:spring-security-acl:3.2.9.RELEASE|https://github.com/spring-projects/spring-security|The Apache Software License, Version 2.0|https://github.com/spring-projects/spring-security/blob/master/license.txt
```

There are no column headers on this file.  Here is the example of how to config the InventoryHtmlReportRenderer to use
an overrides file:

```groovy
import com.github.jk1.license.render.*

licenseReport {
    renderers = [new InventoryHtmlReportRenderer('index.html', 'Some Title', new File(projectDir,"../unknown-license-details.txt"))]
}
```

### DownloadLicensesRenderer

The DownloadLicensesRenderer will download all the license files which include url. You can specified licenseUrl to 
certain license file. When no value pass to DownloadLicenseRenderer the default value is empty Map.

```groovy
import com.github.jk1.license.render.*
Map<String, File> customizeLicenseFile = [
   "http://www.opensource.org/licenses/mit-license.php" : new File("./gradle/default_license/MIT.txt")
]

licenseReport {
    renderers = [new DownloadLicensesRenderer(customizeLicenseFile)]
}
```

After download all the license files, it will generate a report for all license files report as below. 
And also a html file containing all html file content inside, which could specified customizeLicenseFile much easier.

```json
{
  "noLicenseFileDependencies": [
    "org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.6.2"
  ],
  "noLicenseFileImportedModules": [
        
  ],
  "downloadedHtmlLicenseFileDirectories": [
    "org.projectlombok_lombok_1.16.20/DOWNLOADED-POM-LICENSES/MIT_License.html"
  ],
  "downloadedTextLicenseFileDirectories": [
    "com.couchbase.client_core-io_1.6.1/DOWNLOADED-POM-LICENSES/Apache_License_Version_2_0.txt"
  ],
  "embeddedLicenseFileDirectories": [
    "com.couchbase.client_core-io_1.5.7/META-INF/LICENSE"
  ]
}
```
```html
<html><body><div><a href = "http://www.apache.org/licenses/">http://www.apache.org/licenses/</a></div></body></html>
<html><body><div><a href = "http://www.gnu.org/software/classpath/license.html">http://www.gnu.org/software/classpath/license.html</a></div></body></html>
<html><body><div><a href = "https://github.com/javaee/javax.annotation/blob/master/LICENSE">https://github.com/javaee/javax.annotation/blob/master/LICENSE</a></div></body></html>
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
    importers = [ new XmlReportImporter("Front End", new File(projectDir,"src/main/webapp/vendor/front_end.xml") ) ]
}
```

The expected input format for `XmlReportImporter` is as follows:

```xml
<topic>
  <chunk>
    <chapter title="Some of my favorite libraries">
      <table>
        <tr>
          <!-- every non-empty chapter must have a title row, which is stripped>
          <th>Name</th>
          <th>Version</th>
          <th>License</th>
        </tr>
        <tr>
          <td><a href="https://url.of.project/homepage">Name of library</a></td>
          <td>1.2.3</td>
          <td><a href="http://url.of.project/license">Name of license</a></td>
        </tr>
        <!-- more libraries here...>
      </table>
    </chapter>
    <!-- more chapters here...>
  </chunk>
  <!-- more chunks here...>
</topic>
```

If there is only one chapter, the outer `topic` and `chunk` tags may be omitted.

## Filters
Dependency filters transform discovered dependency data before rendering.
This may include sorting, reordering, data substitution.

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

* license-bundles: Defines the actual licenses with their correct name and their correct url
* transformation-rules: A rule defines a reference to one license-bundle and a pattern for
   a malformed name or url. When a pattern matches the the license of a dependency, the
   output license-information for that dependency will be updated with the referenced license-bundle.

```json
{
  "bundles" : [
    { "bundleName" : "apache1", "licenseName" : "Apache Software License, Version 1.1", "licenseUrl" : "http://www.apache.org/licenses/LICENSE-1.1" },
    { "bundleName" : "apache2", "licenseName" : "Apache License, Version 2.0", "licenseUrl" : "http://www.apache.org/licenses/LICENSE-2.0" },
    { "bundleName" : "cddl1", "licenseName" : "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE Version 1.0 (CDDL-1.0)", "licenseUrl" : "http://opensource.org/licenses/CDDL-1.0" }
  ],
  "transformationRules" : [
    { "bundleName" : "apache2", "licenseNamePattern" : ".*The Apache Software License, Version 2.0.*" },
    { "bundleName" : "apache2", "licenseNamePattern" : "Apache 2" },
    { "bundleName" : "apache2", "licenseUrlPattern" : "http://www.apache.org/licenses/LICENSE-2.0.txt" },
    { "bundleName" : "apache2", "licenseNamePattern" : "Special Apache", "transformUrl" : false },
    { "bundleName" : "apache2", "licenseNamePattern" : "Keep this name", "transformName" : false }
  ]
}
```

So dependencies with license-name `The Apache Software License, Version 2.0` / `Apache 2` or license-url `http://www.apache.org/licenses/LICENSE-2.0.txt`
are changed to license-name `Apache License, Version 2.0` and license-url `http://www.apache.org/licenses/LICENSE-2.0`


The normalizer can be enabled via a filter.

```groovy
import com.github.jk1.license.filter.*
...
licenseReport {
    filters = new LicenseBundleNormalizer(bundlePath: "$projectDir/config/license-normalizer-bundle.json")
}
```

If no bundle-file is specified, a default file is used containing some commons rules. You are encouraged to create your own bundle-file
and contribute back useful rules.

## Writing custom renderers, importers and filters

It's also possible to implement a custom importer to support any dependency data format necessary. To do so put custom
importer implementation inside `buildSrc` folder:

```java
package org.sample;

import com.github.jk1.license.ImportedModuleBundle;
import com.github.jk1.license.importer.DependencyDataImporter;
import java.util.Collection;

public class CustomImporter implements DependencyDataImporter{

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
    compile 'gradle.plugin.com.github.jk1:gradle-license-report:1.3'
}

```

Now you can use your custom importer in the main build:

```groovy
import org.sample.CustomImporter

...

licenseReport {
    importers = [ new CustomImporter() ]
}

```

The same technique can be used to create a filter or a renderer to support custom report formats.

