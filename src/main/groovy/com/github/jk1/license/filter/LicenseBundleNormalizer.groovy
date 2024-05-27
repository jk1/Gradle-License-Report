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
import com.github.jk1.license.task.ReportTask
import groovy.json.JsonSlurper
import groovy.json.JsonParserType
import groovy.transform.CompileStatic
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input

import java.util.regex.Pattern

class LicenseBundleNormalizer implements DependencyFilter {

    private static Logger LOGGER = Logging.getLogger(ReportTask.class)

    protected String bundlePath
    protected InputStream bundleStream
    protected boolean createDefaultTransformationRules
    protected boolean isInitialized

    // following properties will only exist after the class is initialized
    protected String filterConfig = ""
    protected ReduceDuplicateLicensesFilter duplicateFilter = new ReduceDuplicateLicensesFilter()
    protected LicenseReportExtension config
    protected  LicenseBundleNormalizerConfig normalizerConfig = new LicenseBundleNormalizerConfig(
        bundles: new ArrayList<NormalizerLicenseBundle>(),
        transformationRules: new ArrayList<NormalizerTransformationRule>()
    )
    protected  Map<String, NormalizerLicenseBundle> bundleMap

    LicenseBundleNormalizer(Map params = ["bundlePath": null, "createDefaultTransformationRules": true]) {
        this(params.bundlePath as String, params.get("createDefaultTransformationRules", true))
    }

    LicenseBundleNormalizer(String bundlePath, boolean createDefaultTransformationRules) {
        this.bundlePath = bundlePath
        this.createDefaultTransformationRules = createDefaultTransformationRules
    }

    LicenseBundleNormalizer(InputStream bundleStream, boolean createDefaultTransformationRules) {
        this.bundleStream = bundleStream
        this.createDefaultTransformationRules = createDefaultTransformationRules
    }

    synchronized void init() {
        if (isInitialized) {
            return
        }
        isInitialized = true
        LOGGER.debug("This build has requested module license bundle normalization")

        filterConfig += "bundlePath = $bundlePath\n"
        filterConfig += "createDefaultTransformationRules = $createDefaultTransformationRules\n"

        if (bundlePath != null) {
            applyBundleFrom(new File(bundlePath).text)
        }
        if (bundleStream != null) {
            applyBundleFrom(bundleStream.text)
        }

        if (createDefaultTransformationRules) {
            applyDefaultNormalizerBundleFile()
            applyBundleNamesAndUrlsAsExactMatchRules()
        }

        LOGGER.debug("Bundle normalizer initialized (Bundles: ${normalizerConfig.bundles.size()}, Rules: ${normalizerConfig.transformationRules.size()})")
    }

    private def applyBundleFrom(String text) {
        filterConfig += "normalizerText = $text\n"
        LOGGER.debug("Using supplied normalizer bundle: {}", text)
        def config = toConfig(new JsonSlurper().setType(JsonParserType.LAX).parse(text.chars))
        mergeConfigIntoGlobalConfig(config)
    }

    @Input
    String getFilterConfigForCache() { return this.filterConfig }

    @Override
    ProjectData filter(ProjectData data) {
        init()
        LOGGER.debug("Performing module license normalization")
        config = data.project.licenseReport

        List<NormalizerTransformationRuleMatcher> transformationRuleMatchers = makeNormalizerTransformationRuleMatchers(normalizerConfig.transformationRules)

        LOGGER.debug("Normalizing pom.xml license section...")
        data.configurations*.dependencies.flatten().forEach { normalizePoms(transformationRuleMatchers, it) }
        LOGGER.debug("Normalizing JAR manifest licenses...")
        data.configurations*.dependencies.flatten().forEach { normalizeManifest(transformationRuleMatchers, it) }
        LOGGER.debug("Normalizing embedded license files...")
        data.configurations*.dependencies.flatten().forEach { normalizeLicenseFileDetails(transformationRuleMatchers, it) }
        data.importedModules.forEach { normalizeImportedModuleBundle(transformationRuleMatchers, it) }
        LOGGER.debug("Modules normalized, removing duplicates...")
        data = duplicateFilter.filter(data)
        LOGGER.debug("Module license normalization complete")
        return data
    }

    protected def applyDefaultNormalizerBundleFile() {
        LOGGER.debug("Applying default normalizer bundles")

        def normalizerTextStream = getClass().getResourceAsStream("/default-license-normalizer-bundle.json")
        def defaultConfig = toConfig(new JsonSlurper().setType(JsonParserType.LAX).parse(normalizerTextStream))

        mergeConfigIntoGlobalConfig(defaultConfig)
    }

    protected def applyBundleNamesAndUrlsAsExactMatchRules() {
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

    protected void mergeConfigIntoGlobalConfig(LicenseBundleNormalizerConfig mergeIn) {
        def existingBundleNames = normalizerConfig.bundles*.bundleName

        mergeIn.bundles.each { NormalizerLicenseBundle bundle ->
            if (!existingBundleNames.contains(bundle.bundleName)) {
                normalizerConfig.bundles << bundle
            }
        }

        normalizerConfig.transformationRules.addAll(mergeIn.transformationRules)

        bundleMap = normalizerConfig.bundles.collectEntries {
            [it.bundleName, it]
        }
    }

    protected def normalizePoms(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                              ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {} (normalize pom)", module)
        dependency.poms.forEach { pom ->
            List<License> normalizedLicense = pom.licenses.collect { normalizePomLicense(transformationRuleMatchers, it, module) }.flatten()
            pom.licenses.clear()
            pom.licenses.addAll(normalizedLicense)
        }
    }
    protected def normalizeManifest(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                  ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {} (normalize manifest)", module)
        List<ManifestData> normalizedManifests = dependency.manifests.collect {
            normalizeManifestLicense(transformationRuleMatchers, it, module)
        }.flatten()
        dependency.manifests.clear()
        dependency.manifests.addAll(normalizedManifests)
    }
    protected def normalizeLicenseFileDetails(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
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

    protected def normalizeImportedModuleBundle(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                              ImportedModuleBundle importedModuleBundle) {
        List<ModuleData> normalizedModuleData = importedModuleBundle.modules.collect {
            normalizeModuleData(transformationRuleMatchers, it)
        }.flatten()
        importedModuleBundle.modules.clear()
        importedModuleBundle.modules.addAll(normalizedModuleData)
    }

    protected Collection<License> normalizePomLicense(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                    License license,
                                                    String module) {
        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers,
                module, license.name, license.url, {null})

        LOGGER.debug("License {} ({}) matches the following rules: [{}]", license.name, license.url, rules.join(","))

        if (rules.isEmpty()) return [license]

        rules.collect { normalizeSinglePomLicense(it, license) }
    }

    protected Collection<ManifestData> normalizeManifestLicense(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                              ManifestData manifest,
                                                              String module) {
        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers,
                module, manifest.license, manifest.licenseUrl, {null})

        LOGGER.debug("License {} ({}) (via manifest data, module {}) matches the following rules: [{}]",
                manifest.name, manifest.url,
                module,
                rules.join(","))

        if (rules.isEmpty()) return [manifest]

        rules.collect { normalizeSingleManifestLicense(it, manifest) }
    }

    protected Collection<LicenseFileDetails> normalizeLicenseFileDetailsLicense(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                                              LicenseFileDetails licenseFileDetails,
                                                                              String module) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return [licenseFileDetails]

        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers,
                module, licenseFileDetails.license, licenseFileDetails.licenseUrl,
                { new File("${config.absoluteOutputDir}/$licenseFileDetails.file").text }.memoize())

        LOGGER.debug("License {} ({}) (via license file details, module {}) matches the following rules: [{}]",
                licenseFileDetails.license, licenseFileDetails.licenseUrl,
                module,
                rules.join(","))

        if (rules.isEmpty()) return [licenseFileDetails]

        rules.collect { normalizeSingleLicenseFileDetailsLicense(it, licenseFileDetails) }
    }

    protected Collection<ImportedModuleData> normalizeModuleData(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                               ImportedModuleData importedModuleData) {
        String module = importedModuleData.name + ':' + importedModuleData.version

        List<NormalizerTransformationRule> rules = transformationRulesFor(transformationRuleMatchers,
                module, importedModuleData.license, importedModuleData.licenseUrl, {null})

        LOGGER.debug("License {} ({}) (via imported module data {}:{}) matches the following rules: [{}]",
                importedModuleData.license, importedModuleData.licenseUrl,
                importedModuleData.name, importedModuleData.version,
                rules.join(","))

        if (rules.isEmpty()) return [importedModuleData]

        rules.collect { normalizeSingleModuleDataLicense(it, importedModuleData) }
    }

    @CompileStatic
    protected NormalizerLicenseBundle findBundleForRule(NormalizerTransformationRule rule) {
        return bundleMap[rule?.bundleName]
    }

    @CompileStatic
    protected License normalizeSinglePomLicense(NormalizerTransformationRule rule, License license) {
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
    protected ManifestData normalizeSingleManifestLicense(NormalizerTransformationRule rule, ManifestData manifest) {
        ManifestData normalized = new ManifestData(
            name: manifest.name,
            version: manifest.version,
            description: manifest.description,
            vendor: manifest.vendor,
            license: manifest.license,
            licenseUrl: manifest.licenseUrl,
            url: manifest.url,
            hasPackagedLicense: manifest.hasPackagedLicense
        )

        normalizeWithBundle(rule) { NormalizerLicenseBundle bundle ->
            if (rule.transformName)
                normalized.license = bundle.licenseName
            if (rule.transformUrl)
                normalized.licenseUrl = bundle.licenseUrl
        }
        normalized
    }

    @CompileStatic
    protected LicenseFileDetails normalizeSingleLicenseFileDetailsLicense(NormalizerTransformationRule rule,
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
    protected ImportedModuleData normalizeSingleModuleDataLicense(NormalizerTransformationRule rule, ImportedModuleData importedModuleData) {
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
    protected def normalizeWithBundle(NormalizerTransformationRule rule, Closure block) {
        def bundle = findBundleForRule(rule)
        if (bundle == null) {
            LOGGER.warn("No bundle found for bundle-name: ${rule?.bundleName}")
            return
        }

        block(bundle)
    }

    protected static def toConfig(Object slurpResult) {
        def config = new LicenseBundleNormalizerConfig()
        config.bundles = slurpResult.bundles.collect { new NormalizerLicenseBundle(it) }
        config.transformationRules = slurpResult.transformationRules.collect { new NormalizerTransformationRule(it) }
        config
    }

    /**
     * Central function that performs the actual pattern matching using the
     * {@link NormalizerTransformationRuleMatcher} instances computed from the model's
     * {@link NormalizerTransformationRule}.
     *
     * @param transformationRuleMatchers the matcher instances
     * @param module Artifact coordinates in the form {@code group:name:version}
     * @param license License name(s), may contain newlines, spaces (unfortunately). May also contain multiple license names (undefined separator)
     * @param licenseUrl License URL(s), may contain newlines, spaces (unfortunately). May also contain multiple license names (undefined separator)
     * @param licenseContent License content text. May contain multiple licenses.
     * @return list of matching rules
     */
    @CompileStatic
    static List<NormalizerTransformationRule> transformationRulesFor(List<NormalizerTransformationRuleMatcher> transformationRuleMatchers,
                                                                     String module, String license, String licenseUrl,
                                                                     Closure<String> licenseContent) {
        List<NormalizerTransformationRule> rules = new ArrayList<>()
        for (NormalizerTransformationRuleMatcher matcher : transformationRuleMatchers) {
            if (matcher.moduleMatches(module) ||
                    matcher.licenseNameMatches(license) ||
                    matcher.licenseUrlMatches(licenseUrl) ||
                    matcher.licenseFileContentMatches(licenseContent)) {
                rules.add(matcher.rule)
            }
        }
        return rules
    }

    static List<NormalizerTransformationRuleMatcher> makeNormalizerTransformationRuleMatchers(List<NormalizerTransformationRule> normalizerTransformationRules) {
        // Make java.util.Pattern out of the regular expressions, so the pattern matching is quicker
        normalizerTransformationRules.collect{
            NormalizerTransformationRule rule -> new NormalizerTransformationRuleMatcher(rule)
        }
    }

    /**
     * Takes a {@link NormalizerTransformationRule}, builds {@link Pattern} instances from the {@code *Pattern} fields
     * in {@code NormalizerTransformationRule} used to do the actual regex pattern matching (if the exact string
     * matching fails).
     *
     * Matching on a specific field (license name, license URL, license file content, module name) is only
     * performed, when the pattern is not {@code null} and not empty.
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
            def options =  Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE
            licenseNameRegex = rule.licenseNamePattern != null && !rule.licenseNamePattern.isEmpty() ? Pattern.compile(rule.licenseNamePattern, options) : null
            licenseUrlRegex = rule.licenseUrlPattern != null && !rule.licenseUrlPattern.isEmpty() ? Pattern.compile(rule.licenseUrlPattern, options) : null
            licenseFileContentRegex = rule.licenseFileContentPattern != null && !rule.licenseFileContentPattern.isEmpty() ? Pattern.compile(rule.licenseFileContentPattern, options) : null
            moduleRegex = rule.modulePattern != null && !rule.modulePattern.isEmpty() ? Pattern.compile(rule.modulePattern) : null
        }

        boolean licenseNameMatches(String name) {
            if (name == null || licenseNameRegex == null)
                return false
            name = name.trim()
            if (name.isEmpty())
                return false
            return rule.licenseNamePattern == name || licenseNameRegex.matcher(name).matches()
        }

        boolean licenseUrlMatches(String url) {
            if (url == null || licenseUrlRegex == null)
                return false
            url = url.trim()
            if (url.isEmpty())
                return false
            return rule.licenseUrlPattern == url || licenseUrlRegex.matcher(url).matches()
        }

        boolean licenseFileContentMatches(Closure<String> contentClosure) {
            if (contentClosure == null || licenseFileContentRegex == null)
                return false
            String content = contentClosure()
            if (content == null)
                return false
            content = content.trim()
            if (content.isEmpty())
                return false
            return rule.licenseFileContentPattern == content || licenseFileContentRegex.matcher(content).matches()
        }

        boolean moduleMatches(String module) {
            if (module == null || moduleRegex == null)
                return false
            module = module.trim()
            if (module.isEmpty())
                return false
            return rule.modulePattern == module || moduleRegex.matcher(module).matches()
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
