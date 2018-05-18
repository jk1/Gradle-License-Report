/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license.filter

import com.github.jk1.license.License
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ReportTask
import groovy.json.JsonSlurper
import groovy.json.JsonParserType
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class LicenseBundleNormalizer implements DependencyFilter {

    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    ReduceDuplicateLicensesFilter duplicateFilter = new ReduceDuplicateLicensesFilter()
    LicenseReportExtension config
    LicenseBundleNormalizerConfig normalizerConfig
    Map<String, NormalizerLicenseBundle> bundleMap

    LicenseBundleNormalizer(Map params = ["bundlePath": null, "createDefaultTransformationRules": true]) {
        this(params.bundlePath, params.get("createDefaultTransformationRules", true))
    }

    LicenseBundleNormalizer(String bundlePath, boolean createDefaultTransformationRules) {
        InputStream inputStream
        if (bundlePath == null) {
            inputStream = getClass().getResourceAsStream("/default-license-normalizer-bundle.json")
        } else {
            inputStream = new FileInputStream(new File(bundlePath))
        }

        normalizerConfig = toConfig(new JsonSlurper().setType(JsonParserType.LAX).parse(inputStream))

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
        dependency.poms.forEach { pom ->
            List<License> normalizedLicense = pom.licenses.collect { normalizePomLicense(it) }
            pom.licenses.clear()
            pom.licenses.addAll(normalizedLicense)
        }
    }
    private def normalizeManifest(ModuleData dependency) {
        List<ManifestData> normalizedManifests = dependency.manifests.collect { normalizeManifestLicense(it) }
        dependency.manifests.clear()
        dependency.manifests.addAll(normalizedManifests)
    }
    private def normalizeLicenseFileDetailsLicense(ModuleData dependency) {
        dependency.licenseFiles.forEach { licenseFiles ->
            List<LicenseFileDetails> normalizedDetails =
                licenseFiles.fileDetails.collect { normalizeLicenseFileDetailsLicense(it) }
            licenseFiles.fileDetails.clear()
            licenseFiles.fileDetails.addAll(normalizedDetails)
        }
    }

    private License normalizePomLicense(License license) {
        def rule = findMatchingRuleForName(license.name)
        if (rule == null) rule = findMatchingRuleForUrl(license.url)
        if (rule == null) return license

        normalizePomLicense(rule, license)
    }
    private ManifestData normalizeManifestLicense(ManifestData manifest) {
        def rule = findMatchingRuleForName(manifest.license)
        if (rule == null) rule = findMatchingRuleForUrl(manifest.license)
        if (rule == null) return manifest

        normalizeManifestLicense(rule, manifest)
    }
    private LicenseFileDetails normalizeLicenseFileDetailsLicense(LicenseFileDetails licenseFileDetails) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return

        String licenseFileContent = new File("$config.outputDir/$licenseFileDetails.file").text

        def rule = findMatchingRuleForContentPattern(licenseFileContent)
        if (rule == null && licenseFileDetails.license) rule = findMatchingRuleForName(licenseFileDetails.license)
        if (rule == null && licenseFileDetails.licenseUrl) rule = findMatchingRuleForUrl(licenseFileDetails.licenseUrl)
        if (rule == null) return licenseFileDetails
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

    private License normalizePomLicense(NormalizerTransformationRule rule, License license) {
        License normalized = new License(
            name: license.name,
            url:  license.url,
            distribution:  license.distribution,
            comments:  license.comments
        )

        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) normalized.name = bundle.licenseName
            if (rule.transformUrl) normalized.url = bundle.licenseUrl
        }
        normalized
    }
    private ManifestData normalizeManifestLicense(NormalizerTransformationRule rule, ManifestData manifest) {
        ManifestData normalized = new ManifestData(
            name: manifest.name,
            version: manifest.version,
            description: manifest.description,
            vendor: manifest.vendor,
            license: manifest.license,
            url: manifest.url,
            hasPackagedLicense: manifest.hasPackagedLicense
        )

        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) normalized.license = bundle.licenseName
        }
        normalized
    }
    private LicenseFileDetails normalizeLicenseFileDetailsLicense(NormalizerTransformationRule rule,
                                                                  LicenseFileDetails details) {
        LicenseFileDetails normalized = new LicenseFileDetails(
            file: details.file,
            license: details.license,
            licenseUrl: details.licenseUrl
        )

        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) normalized.license = bundle.licenseName
            if (rule.transformUrl) normalized.licenseUrl = bundle.licenseUrl
        }
        normalized
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
