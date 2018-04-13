package com.github.jk1.license.filter

import com.github.jk1.license.License
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ReportTask
import groovy.json.JsonSlurper
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class LicenseBundleNormalizer implements DependencyFilter {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    ReduceDuplicateLicensesFilter duplicateFilter = new ReduceDuplicateLicensesFilter()
    LicenseReportExtension config
    LicenseBundleNormalizerConfig normalizerConfig
    Map<String, NormalizerLicenseBundle> bundleMap

    LicenseBundleNormalizer(Map params = ["bundlePath": null, "createDefaultTransformationRules": true]) {
        String bundlePath = params.bundlePath
        boolean createDefaultTransformationRules = params.get("createDefaultTransformationRules", true)

        InputStream inputStream
        if (bundlePath == null) {
            inputStream = getClass().getResourceAsStream("/default-license-normalizer-bundle.json")
        } else {
            inputStream = new FileInputStream(new File(bundlePath))
        }

        normalizerConfig = toConfig(new JsonSlurper().parse(inputStream))

        bundleMap = normalizerConfig.bundles.collectEntries {
            [it.bundleName, it]
        }

        if (createDefaultTransformationRules) {
            initializeDefaultTransformationRules()
        }
    }

    private def initializeDefaultTransformationRules() {
        Set<String> rulePatternNames = normalizerConfig.transformationRules*.licenseNamePattern as Set
        Set<String> rulePatternUrls = normalizerConfig.transformationRules*.licenseUrlPattern as Set

        normalizerConfig.bundles.each { NormalizerLicenseBundle bundle ->
            if (!rulePatternNames.contains(bundle.licenseName)) {
                normalizerConfig.transformationRules <<
                    new NormalizerTransformationRule(bundleName: bundle.bundleName, licenseNamePattern: bundle.licenseName)
            }
            if (!rulePatternUrls.contains(bundle.licenseUrl)) {
                normalizerConfig.transformationRules <<
                    new NormalizerTransformationRule(bundleName: bundle.bundleName, licenseUrlPattern: bundle.licenseUrl)
            }
        }
    }

    @Override
    ProjectData filter(ProjectData data) {
        config = data.project.licenseReport

        data.configurations*.dependencies.flatten().forEach { normalizePoms(it) }
        data.configurations*.dependencies.flatten().forEach { normalizeManifest(it) }
        data.configurations*.dependencies.flatten().forEach { normalizeLicenseFileDetailsLicense(it) }

        data = duplicateFilter.filter(data)

        return data
    }

    private def normalizePoms(ModuleData dependency) {
        dependency.poms*.licenses.flatten().forEach { normalizePomLicense(it) }
    }
    private def normalizeManifest(ModuleData dependency) {
        dependency.manifests.forEach { normalizeManifestLicense(it) }
    }
    private def normalizeLicenseFileDetailsLicense(ModuleData dependency) {
        dependency.licenseFiles*.fileDetails.flatten().forEach { normalizeLicenseFileDetailsLicense(it) }
    }

    private def normalizePomLicense(License license) {
        def rule = findMatchingRuleForName(license.name)
        if (rule == null) rule = findMatchingRuleForUrl(license.url)
        if (rule == null) return

        normalizePomLicense(rule, license)
    }
    private def normalizeManifestLicense(ManifestData manifest) {
        def rule = findMatchingRuleForName(manifest.license)
        if (rule == null) rule = findMatchingRuleForUrl(manifest.license)
        if (rule == null) return

        normalizeManifestLicense(rule, manifest)
    }
    private def normalizeLicenseFileDetailsLicense(LicenseFileDetails licenseFileDetails) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return

        String licenseFileContent = new File("$config.outputDir/$licenseFileDetails.file").text

        def rule = findMatchingRuleForContentPattern(licenseFileContent)
        if (rule == null && licenseFileDetails.license) rule = findMatchingRuleForName(licenseFileDetails.license)
        if (rule == null && licenseFileDetails.licenseUrl) rule = findMatchingRuleForUrl(licenseFileDetails.licenseUrl)
        if (rule == null) return
        normalizeLicenseFileDetailsLicense(rule, licenseFileDetails)
    }

    private NormalizerTransformationRule findMatchingRuleForName(String name) {
        return normalizerConfig.transformationRules
            .find { it.licenseNamePattern && name =~ it.licenseNamePattern }
    }
    private NormalizerTransformationRule findMatchingRuleForUrl(String url) {
        return normalizerConfig.transformationRules
            .find { it.licenseUrlPattern && url =~ it.licenseUrlPattern }
    }
    private NormalizerTransformationRule findMatchingRuleForContentPattern(String content) {
        return normalizerConfig.transformationRules
            .find { it.licenseFileContentPattern  && content =~ it.licenseFileContentPattern }
    }
    private NormalizerLicenseBundle findBundleForRule(NormalizerTransformationRule rule) {
        return bundleMap[rule?.bundleName]
    }

    private def normalizePomLicense(NormalizerTransformationRule rule, License license) {
        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) license.name = bundle.licenseName
            if (rule.transformUrl) license.url = bundle.licenseUrl
        }
    }
    private def normalizeManifestLicense(NormalizerTransformationRule rule, ManifestData manifest) {
        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) manifest.license = bundle.licenseName
        }
    }
    private def normalizeLicenseFileDetailsLicense(NormalizerTransformationRule rule, LicenseFileDetails details) {
        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) details.license = bundle.licenseName
            if (rule.transformUrl) details.licenseUrl = bundle.licenseUrl
        }
    }

    private def normalizeWithBundle(NormalizerTransformationRule rule, Closure block) {
        def bundle = findBundleForRule(rule)
        if (bundle == null) {
            LOGGER.info("No bundle found for bundle-name: ${rule?.bundleName}")
            return
        }

        block(bundle)
    }

    private def toConfig(Object slurpResult) {
        def config = new LicenseBundleNormalizerConfig()
        config.bundles = slurpResult.bundles.collect { new NormalizerLicenseBundle(it) }
        config.transformationRules = slurpResult.transformationRules.collect { new NormalizerTransformationRule(it) }
        config
    }

    class LicenseBundleNormalizerConfig {
        List<NormalizerLicenseBundle> bundles
        List<NormalizerTransformationRule> transformationRules
    }
    class NormalizerLicenseBundle {
        String bundleName
        String licenseName
        String licenseUrl
    }
    class NormalizerTransformationRule {
        String licenseNamePattern
        String licenseUrlPattern
        String licenseFileContentPattern
        String bundleName
        boolean transformName = true
        boolean transformUrl = true
    }
}
