package com.github.jk1.license.filter

import com.github.jk1.license.License
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import groovy.json.JsonSlurper


class LicenseBundleNormalizer implements DependencyFilter {

    ReduceDuplicateLicensesFilter duplicateFilter = new ReduceDuplicateLicensesFilter()
    LicenseReportExtension config
    LicenseBundleNormalizerConfig normalizerConfig
    Map<String, NormalizerLicenseBundle> bundleMap

    LicenseBundleNormalizer(String bundlePath = null) {
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
    }

    @Override
    ProjectData filter(ProjectData data) {
        config = data.project.licenseReport

        data.configurations*.dependencies.flatten().forEach { normalizePoms(it) }
        data.configurations*.dependencies.flatten().forEach { normalizeManifest(it) }
        data.configurations*.dependencies.flatten().forEach { normalizeLicenseFileDetails(it) }

        data = duplicateFilter.filter(data)

        return data
    }

    private def normalizePoms(ModuleData dependency) {
        dependency.poms*.licenses.flatten().forEach { normalizePomLicense(it) }
    }
    private def normalizeManifest(ModuleData dependency) {
        dependency.manifests.forEach { normalizeManifestLicense(it) }
    }
    private def normalizeLicenseFileDetails(ModuleData dependency) {
        dependency.licenseFiles*.fileDetails.flatten().forEach { normalizeLicenseFileDetailsLicense(it) }
    }

    private def normalizePomLicense(License license) {
        def bundle = findMatchingBundleForName(license.name)
        if (bundle == null) bundle = findMatchingBundleForUrl(license.url)
        if (bundle == null) return
        applyBundleToLicense(bundle, license)
    }
    private def normalizeManifestLicense(ManifestData manifest) {
        def bundle = findMatchingBundleForName(manifest.license)
        if (bundle == null) bundle = findMatchingBundleForUrl(manifest.license)
        if (bundle == null) return
        applyBundleToManifest(bundle, manifest)
    }
    private def normalizeLicenseFileDetailsLicense(LicenseFileDetails licenseFileDetails) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return

        String licenseFileContent = new File("$config.outputDir/$licenseFileDetails.file").text

        def bundle = findMatchingBundleForContentPattern(licenseFileContent)
        if (bundle == null && licenseFileDetails.license) bundle = findMatchingBundleForName(licenseFileDetails.license)
        if (bundle == null && licenseFileDetails.licenseUrl) bundle = findMatchingBundleForUrl(licenseFileDetails.licenseUrl)
        if (bundle == null) return
        applyBundleToLicenseFileDetails(bundle, licenseFileDetails)
    }

    private def findMatchingBundleForName(String name) {
        def transformToBundleName = normalizerConfig.transformationRules
                .find { it.licenseNamePattern  && name =~ it.licenseNamePattern }?.bundleName
        return bundleMap[transformToBundleName]
    }
    private def findMatchingBundleForUrl(String url) {
        def transformToBundleName = normalizerConfig.transformationRules
                .find { it.licenseUrlPattern && url =~ it.licenseUrlPattern }?.bundleName
        return bundleMap[transformToBundleName]
    }
    private def findMatchingBundleForContentPattern(String content) {
        def transformToBundleName = normalizerConfig.transformationRules
            .find { it.licenseFileContentPattern  && content =~ it.licenseFileContentPattern }?.bundleName
        return bundleMap[transformToBundleName]
    }

    private def applyBundleToLicense(NormalizerLicenseBundle bundle, License license) {
        license.name = bundle.licenseName
        license.url = bundle.licenseUrl
    }
    private def applyBundleToManifest(NormalizerLicenseBundle bundle, ManifestData manifest) {
        manifest.license = bundle.licenseName
    }
    private def applyBundleToLicenseFileDetails(NormalizerLicenseBundle bundle, LicenseFileDetails details) {
        details.license = bundle.licenseName
        details.licenseUrl = bundle.licenseUrl
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
    }
}
