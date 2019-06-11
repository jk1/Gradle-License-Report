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

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.License
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.ReportTask
import groovy.json.JsonSlurper
import groovy.json.JsonParserType
import groovy.transform.CompileStatic
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input

import java.util.regex.Pattern

class LicenseBundleNormalizer implements DependencyFilter {
    private Logger LOGGER = Logging.getLogger(ReportTask.class)

    private String filterConfig = ""
    ReduceDuplicateLicensesFilter duplicateFilter = new ReduceDuplicateLicensesFilter()
    LicenseReportExtension config
    LicenseBundleNormalizerConfig normalizerConfig = new LicenseBundleNormalizerConfig(
        bundles: new ArrayList<NormalizerLicenseBundle>(),
        transformationRules: new ArrayList<NormalizerTransformationRule>()
    )
    Map<String, NormalizerLicenseBundle> bundleMap

    LicenseBundleNormalizer(Map params = ["bundlePath": null, "createDefaultTransformationRules": true]) {
        this(params.bundlePath as String, params.get("createDefaultTransformationRules", true))
    }

    LicenseBundleNormalizer(String bundlePath, boolean createDefaultTransformationRules) {
        LOGGER.debug("This build has requested module license bundle normalization")

        filterConfig += "bundlePath = $bundlePath\n"
        filterConfig += "createDefaultTransformationRules = $createDefaultTransformationRules\n"

        if (bundlePath != null) {
            def normalizerText = new File(bundlePath).text
            filterConfig += "normalizerText = $normalizerText\n"

            LOGGER.debug("Using supplied normalizer bundle from {}: {}", bundlePath, normalizerText)

            def config = toConfig(new JsonSlurper().setType(JsonParserType.LAX).parse(normalizerText.chars))

            mergeConfigIntoGlobalConfig(config)
        }

        if (createDefaultTransformationRules) {
            applyDefaultNormalizerBundleFile()
            applyBundleNamesAndUrlsAsExactMatchRules()
        }

        LOGGER.debug("Bundle normalizer initialized (Bundles: ${normalizerConfig.bundles.size()}, Rules: ${normalizerConfig.transformationRules.size()})")
    }

    @Input
    private String getFilterConfigForCache() { return this.filterConfig }

    @Override
    ProjectData filter(ProjectData data) {
        LOGGER.debug("Performing module license normalization")
        config = data.project.licenseReport

        // Make java.util.Pattern out of the regular expressions, so the pattern matching is quicker
        List<NormalizerTransformationRuleMatcher> transformationRuleMatchers = normalizerConfig.transformationRules.collect{
            NormalizerTransformationRule rule -> new NormalizerTransformationRuleMatcher(rule)
        }

        LOGGER.debug("Normalizing pom.xml license section...")
        data.configurations*.dependencies.flatten().forEach { normalizePoms(transformationRuleMatchers, it) }
        LOGGER.debug("Normalizing JAR manifest licenses...")
        data.configurations*.dependencies.flatten().forEach { normalizeManifest(transformationRuleMatchers, it) }
        LOGGER.debug("Normalizing embeded license files...")
        data.configurations*.dependencies.flatten().forEach { normalizeLicenseFileDetails(transformationRuleMatchers, it) }
        data.importedModules.forEach { normalizeImportedModuleBundle(transformationRuleMatchers, it) }
        LOGGER.debug("Modules normalized, removing duplicates...")
        data = duplicateFilter.filter(data)
        LOGGER.debug("Module license normalization complete")
        return data
    }

    private def applyDefaultNormalizerBundleFile() {
        LOGGER.debug("Applying default normalizer bundles")

        def normalizerTextStream = getClass().getResourceAsStream("/default-license-normalizer-bundle.json")
        def defaultConfig = toConfig(new JsonSlurper().setType(JsonParserType.LAX).parse(normalizerTextStream))

        mergeConfigIntoGlobalConfig(defaultConfig)
    }

    private def applyBundleNamesAndUrlsAsExactMatchRules() {
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

    private void mergeConfigIntoGlobalConfig(LicenseBundleNormalizerConfig mergeIn) {
        def existingBungleNames = normalizerConfig.bundles*.bundleName

        mergeIn.bundles.each { NormalizerLicenseBundle bundle ->
            if (!existingBungleNames.contains(bundle.bundleName)) {
                normalizerConfig.bundles << bundle
            }
        }

        normalizerConfig.transformationRules.addAll(mergeIn.transformationRules)

        bundleMap = normalizerConfig.bundles.collectEntries {
            [it.bundleName, it]
        }
    }

    private def normalizePoms(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                              ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {} (normalize pom)", module)
        dependency.poms.forEach { pom ->
            List<License> normalizedLicense = pom.licenses.collect { normalizePomLicense(transformationRuleMatchers, it, module) }.flatten()
            pom.licenses.clear()
            pom.licenses.addAll(normalizedLicense)
        }
    }
    private def normalizeManifest(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                  ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {} (normalize manifest)", module)
        List<ManifestData> normalizedManifests = dependency.manifests.collect {
            normalizeManifestLicense(transformationRuleMatchers, it, module)
        }.flatten()
        dependency.manifests.clear()
        dependency.manifests.addAll(normalizedManifests)
    }
    private def normalizeLicenseFileDetails(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                            ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {} (normalize license file details)", module)
        dependency.licenseFiles.forEach { licenseFile ->
            List<LicenseFileDetails> normalizedDetails =
                licenseFile.fileDetails.collect { normalizeLicenseFileDetailsLicense(transformationRuleMatchers, it, module) }.flatten()
            licenseFile.fileDetails.clear()
            licenseFile.fileDetails.addAll(normalizedDetails)
        }
    }

    private def normalizeImportedModuleBundle(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                              ImportedModuleBundle importedModuleBundle) {
        List<ModuleData> normalizedModuleData = importedModuleBundle.modules.collect {
            normalizeModuleData(transformationRuleMatchers, it)
        }.flatten()
        importedModuleBundle.modules.clear()
        importedModuleBundle.modules.addAll(normalizedModuleData)
    }

    @CompileStatic
    private Collection<License> normalizePomLicense(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                    License license,
                                                    String module) {
        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers, module, license.name, license.url)

        LOGGER.debug("License {} ({}) matches the following rules: [{}]", license.name, license.url, rules.join(","))

        if (rules.isEmpty()) return [license]

        List<License> normalized = new ArrayList<>()
        for (NormalizerTransformationRule rule : rules) {
            normalized.add(normalizeSinglePomLicense(rule, license))
        }
        return normalized
    }

    @CompileStatic
    private Collection<ManifestData> normalizeManifestLicense(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                              ManifestData manifest,
                                                              String module) {
        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers, module, manifest.license, manifest.license)

        LOGGER.debug("License {} ({}) (via manifest data, module {}) matches the following rules: [{}]",
                module,
                manifest.name, manifest.url,
                rules.join(","))

        if (rules.isEmpty()) return [manifest]

        List<ManifestData> normalized = new ArrayList<>()
        for (NormalizerTransformationRule rule : rules) {
            normalized.add(normalizeSingleManifestLicense(rule, manifest))
        }
        return normalized
    }

    @CompileStatic
    private Collection<LicenseFileDetails> normalizeLicenseFileDetailsLicense(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                                              LicenseFileDetails licenseFileDetails,
                                                                              String module) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return [licenseFileDetails]

        // Only read the license text file once
        def licenseFileContent = { new File("$config.outputDir/$licenseFileDetails.file").text }.memoize()

        List<NormalizerTransformationRule> rules = new ArrayList<>()
        for (NormalizerTransformationRuleMatcher matcher : transformationRuleMatchers) {
            if (matcher.moduleMatches(module) ||
                    matcher.licenseNameMatches(licenseFileDetails.license) ||
                    matcher.licenseUrlMatches(licenseFileDetails.licenseUrl) ||
                    matcher.licenseFileContentMatches(licenseFileContent())) {
                rules.add(matcher.rule)
            }
        }

        LOGGER.debug("License {} ({}) (via license file details, module {}) matches the following rules: [{}]",
                module,
                licenseFileDetails.license, licenseFileDetails.licenseUrl,
                rules.join(","))

        if (rules.isEmpty()) return [licenseFileDetails]

        List<LicenseFileDetails> normalized = new ArrayList<>()
        for (NormalizerTransformationRule rule : rules) {
            normalized.add(normalizeSingleLicenseFileDetailsLicense(rule, licenseFileDetails))
        }
        return normalized
    }

    @CompileStatic
    private Collection<ImportedModuleData> normalizeModuleData(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                               ImportedModuleData importedModuleData) {
        String module = importedModuleData.name + ':' + importedModuleData.version

        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers, module, importedModuleData.license, importedModuleData.licenseUrl)

        LOGGER.debug("License {} ({}) (via imported module data {}:{}) matches the following rules: [{}]",
                importedModuleData.license, importedModuleData.licenseUrl,
                importedModuleData.name, importedModuleData.version,
                rules.join(","))

        if (rules.isEmpty()) return [importedModuleData]

        List<ImportedModuleData> normalized = new ArrayList<>()
        for (NormalizerTransformationRule rule : rules) {
            normalized.add(normalizeSingleModuleDataLicense(rule, importedModuleData))
        }
        return normalized
    }

    @CompileStatic
    private static List<NormalizerTransformationRule> transformationRulesFor(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                                             String module, String license, String licenseUrl) {
        List<NormalizerTransformationRule> rules = new ArrayList<>()
        for (NormalizerTransformationRuleMatcher matcher : transformationRuleMatchers) {
            if (matcher.moduleMatches(module) ||
                    matcher.licenseNameMatches(license) ||
                    matcher.licenseUrlMatches(licenseUrl)) {
                rules.add(matcher.rule)
            }
        }
        return rules
    }

    @CompileStatic
    private NormalizerLicenseBundle findBundleForRule(NormalizerTransformationRule rule) {
        return bundleMap[rule?.bundleName]
    }

    @CompileStatic
    private License normalizeSinglePomLicense(NormalizerTransformationRule rule, License license) {
        License normalized = new License(
            name: license.name,
            url:  license.url
        )

        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) normalized.name = bundle.licenseName
            if (rule.transformUrl) normalized.url = bundle.licenseUrl
        }
        normalized
    }

    @CompileStatic
    private ManifestData normalizeSingleManifestLicense(NormalizerTransformationRule rule, ManifestData manifest) {
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

    @CompileStatic
    private LicenseFileDetails normalizeSingleLicenseFileDetailsLicense(NormalizerTransformationRule rule,
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

    @CompileStatic
    private ImportedModuleData normalizeSingleModuleDataLicense(NormalizerTransformationRule rule, ImportedModuleData importedModuleData) {
        ImportedModuleData normalized = new ImportedModuleData(
            name: importedModuleData.name,
            version: importedModuleData.version,
            projectUrl: importedModuleData.projectUrl,
            license: importedModuleData.license,
            licenseUrl: importedModuleData.licenseUrl
        )

        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName) normalized.license = bundle.licenseName
            if (rule.transformUrl) normalized.licenseUrl = bundle.licenseUrl
        }
        normalized
    }

    @CompileStatic
    private def normalizeWithBundle(NormalizerTransformationRule rule, Closure block) {
        def bundle = findBundleForRule(rule)
        if (bundle == null) {
            LOGGER.warn("No bundle found for bundle-name: ${rule?.bundleName}")
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

    /**
     * Takes a {@link NormalizerTransformationRule}, builds {@link Pattern} instances from the {@code *Pattern} fields
     * in {@code NormalizerTransformationRule} used to do the actual regex pattern matching (if the exact string
     * matching fails).
     *
     * This class is only used during the actual filtering process but not kept around in the model.
     */
    @CompileStatic
    static class NormalizerTransformationRuleMatcher {
        final NormalizerTransformationRule rule
        private final Pattern licenseNameRegex
        private final Pattern licenseUrlRegex
        private final Pattern licenseFileContentRegex
        private final Pattern moduleRegex

        NormalizerTransformationRuleMatcher(NormalizerTransformationRule rule) {
            this.rule = rule
            licenseNameRegex = rule.licenseNamePattern != null ? Pattern.compile(rule.licenseNamePattern) : null
            licenseUrlRegex = rule.licenseUrlPattern != null ? Pattern.compile(rule.licenseUrlPattern) : null
            licenseFileContentRegex = rule.licenseFileContentPattern != null ? Pattern.compile(rule.licenseFileContentPattern) : null
            moduleRegex = rule.modulePattern != null ? Pattern.compile(rule.modulePattern) : null
        }

        boolean licenseNameMatches(String name) {
            return name != null && licenseNameRegex != null && (rule.licenseNamePattern == name || licenseNameRegex.matcher(name).matches())
        }

        boolean licenseUrlMatches(String url) {
            return url != null && licenseUrlRegex != null && (rule.licenseUrlPattern == url || licenseUrlRegex.matcher(url).matches())
        }

        boolean licenseFileContentMatches(String content) {
            return content != null && licenseFileContentRegex != null && (rule.licenseFileContentPattern == content || licenseFileContentRegex.matcher(content).matches())
        }

        boolean moduleMatches(String module) {
            return module != null && moduleRegex != null && (rule.modulePattern == module || moduleRegex.matcher(module).matches())
        }
    }

    @CompileStatic
    static class LicenseBundleNormalizerConfig {
        List<NormalizerLicenseBundle> bundles
        List<NormalizerTransformationRule> transformationRules
    }
    @CompileStatic
    static class NormalizerLicenseBundle {
        String bundleName
        String licenseName
        String licenseUrl
    }
    @CompileStatic
    static class NormalizerTransformationRule {
        String licenseNamePattern
        String licenseUrlPattern
        String licenseFileContentPattern
        String modulePattern
        String bundleName
        boolean transformName = true
        boolean transformUrl = true

        @Override
        String toString() {
            "$bundleName:name=[$licenseNamePattern],url=[$licenseUrlPattern],content=[$licenseFileContentPattern]"
        }
    }
}
