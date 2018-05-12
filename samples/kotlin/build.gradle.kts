import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.filter.LicenseBundleNormalizer

plugins {
    id("com.github.jk1.dependency-license-report") version "0.9"
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compile("org.springframework:spring-tx:3.2.3.RELEASE")
    compile("com.sun.mail:javax.mail:1.5.4")
    compile("org.ehcache:ehcache:3.3.1")
    compile("org.apache.geronimo.specs:geronimo-jta_1.0.1B_spec:1.0.1")
}

licenseReport {
    renderers = arrayOf(InventoryHtmlReportRenderer("report.html","Backend"))
    filters = arrayOf(LicenseBundleNormalizer())
}