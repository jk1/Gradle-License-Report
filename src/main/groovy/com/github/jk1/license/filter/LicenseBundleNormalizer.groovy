package com.github.jk1.license.filter

import com.github.jk1.license.License
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import groovy.json.JsonSlurper


class LicenseBundleNormalizer implements DependencyFilter {

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
        data.configurations*.dependencies.flatten().forEach { normalizeDependency(it) }
        return data
    }

    private def normalizeDependency(ModuleData dependency) {
        dependency.poms
                .collect { it.licenses }.flatten()
                .forEach { normalizeLicense(it) }
    }

    private def normalizeLicense(License license) {
        def bundle = findMatchingBundleForName(license.name)
        if (bundle == null) bundle = findMatchingBundleForUrl(license.url)
        if (bundle == null) return
        applyBundleToLicense(bundle, license)
    }

    private def findMatchingBundleForName(String name) {
        def transformToBundleName = normalizerConfig.transformationRules
                .find { it.licenseNamePattern  && name ==~ it.licenseNamePattern }?.bundleName
        return bundleMap[transformToBundleName]
    }
    private def findMatchingBundleForUrl(String url) {
        def transformToBundleName = normalizerConfig.transformationRules
                .find { it.licenseUrlPattern && url ==~ it.licenseUrlPattern }?.bundleName
        return bundleMap[transformToBundleName]
    }

    private def applyBundleToLicense(NormalizerLicenseBundle bundle, License license) {
        license.name = bundle.licenseName
        license.url = bundle.licenseUrl
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
        String bundleName
    }
}

