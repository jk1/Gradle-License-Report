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
import java.util.stream.Collectors

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
        this(params.bundlePath, params.get("createDefaultTransformationRules", true))
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
        LOGGER.debug("Normalizing pom.xml license section...")
        data.configurations*.dependencies.flatten().forEach { normalizePoms(it) }
        LOGGER.debug("Normalizing JAR manifest licenses...")
        data.configurations*.dependencies.flatten().forEach { normalizeManifest(it) }
        LOGGER.debug("Normalizing embeded license files...")
        data.configurations*.dependencies.flatten().forEach { normalizeLicenseFileDetails(it) }
        data.importedModules.forEach { normalizeImportedModuleBundle(it) }
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

    private def normalizePoms(ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {}", module)
        dependency.poms.forEach { pom ->
            List<License> normalizedLicense = pom.licenses.collect { normalizePomLicense(it, module) }.flatten()
            pom.licenses.clear()
            pom.licenses.addAll(normalizedLicense)
        }
    }
    private def normalizeManifest(ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {}", module)
        List<ManifestData> normalizedManifests = dependency.manifests.collect {
            normalizeManifestLicense(it, module)
        }.flatten()
        dependency.manifests.clear()
        dependency.manifests.addAll(normalizedManifests)
    }
    private def normalizeLicenseFileDetails(ModuleData dependency) {
        String module = dependency.group + ':' + dependency.name + ':' + dependency.version
        LOGGER.debug("Checking module {}", module)
        dependency.licenseFiles.forEach { licenseFile ->
            List<LicenseFileDetails> normalizedDetails =
                licenseFile.fileDetails.collect { normalizeLicenseFileDetailsLicense(it, module) }.flatten()
            licenseFile.fileDetails.clear()
            licenseFile.fileDetails.addAll(normalizedDetails)
        }
    }

    private def normalizeImportedModuleBundle(ImportedModuleBundle importedModuleBundle) {
        List<ModuleData> normalizedModuleData = importedModuleBundle.modules.collect {
            normalizeModuleData(it)
        }.flatten()
        importedModuleBundle.modules.clear()
        importedModuleBundle.modules.addAll(normalizedModuleData)
    }

    @CompileStatic
    private Collection<License> normalizePomLicense(License license,
                                                    String module) {
        List<NormalizerTransformationRule> rules = transformationRulesFor(module, license.name, license.url)

        LOGGER.debug("License {} ({}) matches the following rules: [{}]", license.name, license.url, rules.join(","))

        if (rules.isEmpty()) return [license]

        rules.collect { normalizePomLicense(it, license) }
    }

    @CompileStatic
    private Collection<ManifestData> normalizeManifestLicense(ManifestData manifest,
                                                              String module) {
        List<NormalizerTransformationRule> rules = transformationRulesFor(module, null, manifest.license)

        LOGGER.debug("License {} ({}) matches the following rules: [{}]", manifest.name, manifest.url, rules.join(","))

        if (rules.isEmpty()) return [manifest]

        rules.collect { normalizeManifestLicense(it, manifest) }
    }

    @CompileStatic
    private Collection<LicenseFileDetails> normalizeLicenseFileDetailsLicense(LicenseFileDetails licenseFileDetails,
                                                                              String module) {
        if (licenseFileDetails.file == null || licenseFileDetails.file.isEmpty()) return [licenseFileDetails]

        def licenseFileContent = { new File("$config.outputDir/$licenseFileDetails.file").text }.memoize()

        List<NormalizerTransformationRule> rules = normalizerConfig.transformationRules.stream()
                .filter({
                    it.moduleMatches(module) ||
                            it.licenseNameMatches(licenseFileDetails.license) ||
                            it.licenseUrlMatches(licenseFileDetails.licenseUrl) ||
                            it.licenseFileContentMatches(licenseFileContent())
                })
                .collect(Collectors.toList())

        LOGGER.debug("License {} ({}) matches the following rules: [{}]", licenseFileDetails.license, licenseFileDetails.licenseUrl, rules.join(","))

        if (rules.isEmpty()) return [licenseFileDetails]

        rules.collect { normalizeLicenseFileDetailsLicense(it, licenseFileDetails) }
    }

    @CompileStatic
    private Collection<ImportedModuleData> normalizeModuleData(ImportedModuleData importedModuleData) {
        String module = importedModuleData.name + ':' + importedModuleData.version

        List<NormalizerTransformationRule> rules = transformationRulesFor(module, importedModuleData.license, importedModuleData.licenseUrl)

        if (rules.isEmpty()) return [importedModuleData]

        rules.collect { normalizeModuleDataLicense(it, importedModuleData) }
    }

    @CompileStatic
    private List<NormalizerTransformationRule> transformationRulesFor(String module, String license, String licenseUrl) {
        return normalizerConfig.transformationRules.stream()
                .filter({
                    it.moduleMatches(module) ||
                            it.licenseNameMatches(license) ||
                            it.licenseUrlMatches(licenseUrl)
                })
                .collect(Collectors.toList())
    }

    @CompileStatic
    private NormalizerLicenseBundle findBundleForRule(NormalizerTransformationRule rule) {
        return bundleMap[rule?.bundleName]
    }

    @CompileStatic
    private License normalizePomLicense(NormalizerTransformationRule rule, License license) {
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

    @CompileStatic
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

    @CompileStatic
    private ImportedModuleData normalizeModuleDataLicense(NormalizerTransformationRule rule, ImportedModuleData importedModuleData) {
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
        private Pattern licenseNameRegex
        private Pattern licenseUrlRegex
        private Pattern licenseFileContentRegex
        private Pattern moduleRegex

        boolean licenseNameMatches(String name) {
            if (name != null && licenseNamePattern != null) {
                if (licenseNamePattern == name) {
                    return true
                }
                if (licenseNameRegex == null) {
                    licenseNameRegex = Pattern.compile(licenseNamePattern)
                }
                if (licenseNameRegex.matcher(name).matches())
                    return true
            }
            return false
        }

        boolean licenseUrlMatches(String url) {
            if (url != null && licenseUrlPattern != null) {
                if (licenseUrlPattern == url) {
                    return true
                }
                if (licenseUrlRegex == null) {
                    licenseUrlRegex = Pattern.compile(licenseUrlPattern)
                }
                if (licenseUrlRegex.matcher(url).matches()) {
                    return true
                }
            }
            return false
        }

        boolean licenseFileContentMatches(String content) {
            if (content != null && licenseFileContentPattern != null) {
                if (licenseFileContentPattern == content) {
                    return true
                }
                if (licenseFileContentRegex == null) {
                    licenseFileContentRegex = Pattern.compile(licenseFileContentPattern)
                }
                if (licenseFileContentRegex.matcher(content).matches()) {
                    return true
                }
            }
            return false
        }

        boolean moduleMatches(String module) {
            if (module != null && modulePattern != null) {
                if (modulePattern == module) {
                    return true
                }
                if (moduleRegex == null) {
                    moduleRegex = Pattern.compile(modulePattern)
                }
                if (moduleRegex.matcher(module).matches()) {
                    return true
                }
            }
            return false
        }

        @Override
        String toString() {
            "$bundleName:name=[$licenseNamePattern],url=[$licenseUrlPattern],content=[$licenseFileContentPattern]"
        }
    }
}
