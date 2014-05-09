Gradle License Report
=====================

A plugin for generating reports about the licenses of the dependencies for your Gradle project.

Usage
-------

First, look up the most recent version [here](http://jcenter.bintray.com/com/smokejumperit/gradle/).

Then add this to your `build.gradle` file:
```
buildscript {
    repositories {
      jcenter()
    }   
    dependencies {
			// Replace $version with the current version
			classpath "com.smokejumperit.gradle.license:Gradle-License-Report:$version"
    }   
}

apply plugin:com.smokejumperit.gradle.report.DependencyLicensePlugin
```

Then run `gradle dependencyLicenseReport` to generate your report in `build/reports/dependency-license`.
