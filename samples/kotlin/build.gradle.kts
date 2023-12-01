import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.filter.ExcludeDependenciesWithoutArtifactsFilter

plugins {
    id("com.github.jk1.dependency-license-report") version "2.5"
    id("java")
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
        InventoryHtmlReportRenderer("report.html","Backend"),
        JsonReportRenderer("report.json", true),
        )
    filters = arrayOf<DependencyFilter>(
        LicenseBundleNormalizer(),
        ExcludeDependenciesWithoutArtifactsFilter(),
    )
}
