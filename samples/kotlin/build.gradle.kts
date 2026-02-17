import com.github.jk1.license.render.*
import com.github.jk1.license.filter.*
import com.github.jk1.license.importer.*

plugins {
    id("com.github.jk1.dependency-license-report") version "3.1.1"  // x-release-please-version
    id("groovy")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework:spring-tx:3.2.3.RELEASE")
    implementation("com.sun.mail:javax.mail:1.5.4")
    implementation("org.ehcache:ehcache:3.3.1")
    implementation("org.apache.geronimo.specs:geronimo-jta_1.0.1B_spec:1.0.1")
    implementation("io.ktor:ktor-io:2.3.6")
}

licenseReport {
    renderers = arrayOf<ReportRenderer>(
        InventoryHtmlReportRenderer("report.html", "Backend"),
        JsonReportRenderer("report.json", true),
    )
    filters = arrayOf<DependencyFilter>(
        LicenseBundleNormalizer(),
        ExcludeDependenciesWithoutArtifactsFilter(),
    )
    importers = arrayOf<DependencyDataImporter>(
        XmlReportImporter("Front End", layout.projectDirectory.file("../configs/externalDependencies.xml").asFile)
    )

    allowedLicensesFile = layout.projectDirectory.file("../configs/allow-mit-sample.json")
}

tasks.register("printDependencies") {
    doLast {
        configurations.implementation.get().allDependencies.forEach { dep ->
            println("${dep.javaClass.simpleName}${dep.javaClass.interfaces.map { it.simpleName }} group[${dep.group}] name[${dep.name}] $dep")
        }
    }
}

tasks.register("printResolvedFiles") {
    doLast {
        configurations.runtimeClasspath.get()
            .resolvedConfiguration
            .resolvedArtifacts
            .forEach { println("${it.name} - ${it.classifier} - ${it.type}: ${it.file}") }
    }
}
