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
            List<License> normalizedLicense = pom.licenses.collect { normalizePomLicense(it) }.flatten()
            pom.licenses.clear()
            pom.licenses.addAll(normalizedLicense)
        }
    }
    private def normalizeManifest(ModuleData dependency) {
        List<ManifestData> normalizedManifests = dependency.manifests.collect { normalizeManifestLicense(it) }.flatten()
        dependency.manifests.clear()
        dependency.manifests.addAll(normalizedManifests)
    }
    private def normalizeLicenseFileDetailsLicense(ModuleData dependency) {
        dependency.licenseFiles.forEach { licenseFiles ->
            List<LicenseFileDetails> normalizedDetails =
                licenseFiles.fileDetails.collect { normalizeLicenseFileDetailsLicense(it) }.flatten()
            licenseFiles.fileDetails.clear()
            licenseFiles.fileDetails.addAll(normalizedDetails)
        }
    }

    private Collection<License> normalizePomLicense(License license) {
        List<NormalizerTransformationRule> rules = [ ]

        rules += findMatchingRulesForName(license.name)
        rules += findMatchingRulesForUrl(license.url)

        if (rules.isEmpty()) return [license]

        rules.collect { normalizePomLicense(it, license) }
    }
    private Collection<ManifestData> normalizeManifestLicense(ManifestData manifest) {
        List<NormalizerTransformationRule> rules = [ ]

        rules += findMatchingRulesForName(manifest.license)
        rules += findMatchingRulesForUrl(manifest.license)

        if (rules.isEmpty()) return [manifest]

        rules.collect { normalizeManifestLicense(it, manifest) }
    }
    private Collection<LicenseFileDetails> normalizeLicenseFileDetailsLicense(LicenseFileDetails licenseFileDetails) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return [licenseFileDetails]

        List<NormalizerTransformationRule> rules = [ ]

        String licenseFileContent = new File("$config.outputDir/$licenseFileDetails.file").text

        rules += findMatchingRulesForContentPattern(licenseFileContent)
        rules += findMatchingRulesForName(licenseFileDetails.license)
        rules += findMatchingRulesForUrl(licenseFileDetails.licenseUrl)

        if (rules.isEmpty()) return [licenseFileDetails]

        rules.collect { normalizeLicenseFileDetailsLicense(it, licenseFileDetails) }
    }

    private List<NormalizerTransformationRule> findMatchingRulesForName(String name) {
        return normalizerConfig.transformationRules
            .findAll { it.licenseNamePattern && name =~ it.licenseNamePattern }
    }
    private List<NormalizerTransformationRule> findMatchingRulesForUrl(String url) {
        return normalizerConfig.transformationRules
            .findAll { it.licenseUrlPattern && url =~ it.licenseUrlPattern }
    }
    private List<NormalizerTransformationRule> findMatchingRulesForContentPattern(String content) {
        return normalizerConfig.transformationRules
            .findAll { it.licenseFileContentPattern  && content =~ it.licenseFileContentPattern }
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

    private static def toConfig(Object slurpResult) {
        def config = new LicenseBundleNormalizerConfig()
        config.bundles = slurpResult.bundles.collect { new NormalizerLicenseBundle(it) }
        config.transformationRules = slurpResult.transformationRules.collect { new NormalizerTransformationRule(it) }
        config
    }

    static class LicenseBundleNormalizerConfig {
        List<NormalizerLicenseBundle> bundles
        List<NormalizerTransformationRule> transformationRules
    }
    static class NormalizerLicenseBundle {
        String bundleName
        String licenseName
        String licenseUrl
    }
    static class NormalizerTransformationRule {
        String licenseNamePattern
        String licenseUrlPattern
        String licenseFileContentPattern
        String bundleName
        boolean transformName = true
        boolean transformUrl = true
    }
}
