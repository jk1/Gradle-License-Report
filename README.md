Gradle License Report
=====================

[![Build Status](https://travis-ci.org/jk1/Gradle-License-Report.svg?branch=master)](https://travis-ci.org/jk1/Gradle-License-Report)

A plugin for generating reports about the licenses of the dependencies for your Gradle project.
This plugin is a fork of https://github.com/RobertFischer/Gradle-License-Report.

This plugin will resolve all your dependencies, and then scour them for anything that looks like relevant licensing information. The theory is
to automatically generate a report that you can hand to corporate IP lawyers in order to keep them busy.

Usage
-------

Add this to your `build.gradle` file:

```groovy
plugins {
  id 'com.github.jk1.dependency-license-report' version '0.3.16'
}
```

or via a 

`buildscript` block
-------

```
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath 'gradle.plugin.com.github.jk1:gradle-license-report:0.3.16'
    }
}
apply plugin: 'com.github.jk1.dependency-license-report'
```

Then run `gradle generateLicenseReport` to generate your report in `build/reports/dependency-license`.

Configuration
-------

Configuration described below is entirely optional.

```groovy
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
    renderer = new XmlReportRenderer('third-party-libs.xml', 'Back-End Libraries')

    // Set importers to import any external dependency information, i.e. from npm.
    // Custom importer should implement DependencyDataImporter interface.
    importers = [new XmlReportImporter('Frontend dependencies', file(frontend_libs.xml))]

    // Adjust the configurations to use, e.g. for Android projects.
    configurations = ['compile']
}
```

InventoryHtmlReportRenderer
-----------------

The InventoryHtmlReportRender renders a report grouped by license type so you can more easily identify which dependencies
share the same license.  This makes it easier to know the individual licenses you need to verify with your legal department.
To use this report you simply add it to the configuration:

```groovy
licenseReport {
    renderer = new InventoryHtmlReportRenderer()
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
licenseReport {
    renderer = new InventoryHtmlReportRenderer('index.html', 'Some Title', new File(projectDir,"../unknown-license-details.txt"))
}
```

InventoryHtmlReportRenderer also can handle any importers specified in the configuration for example:

```groovy
licenseReport {
    renderer = new InventoryHtmlReportRenderer('index.html', 'Some Title', new File(projectDir,"../unknown-license-details.txt"))
     importers = [ new XmlReportImporter("Front End", new File(projectDir,"src/main/webapp/vendor/front_end.xml") ) ]
}
```

This is a great way to integrate javascript dependencies into your report so you can join all software into one report.

Included Details
-----------------

For each dependency, these details are included in the report, assuming that the information exists within the dependency archives:

* Module Name
* Module Group
* Module Version
* Manifest Name
* Manifest Description
* Manifest Project URL
* Manifest Vendor
* Manifest Version
* Manifest License(s) -- could be license names, URLs, and/or embedded files
* POM Name
* POM Description
* POM Project URL
* POM License(s) -- could be license names, URLs, and/or embedded files
** POM License(s) Distribution
** POM License(s) Comments
* POM Developer(s) -- name, e-mail, organization, role
* Packaged License Files, which is any file with the following base name:
** `license`
** `unlicense`
** `readme`
** `notice`
** `copying`
** `copying.lesser`

License
--------

This plugin is released under the Apache 2.0 license. See the `LICENSE` file for details.
