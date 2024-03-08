package com.github.jk1.license.filter

import com.github.jk1.license.task.ReportTask
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class SpdxLicenseBundleNormalizer extends LicenseBundleNormalizer {

    private static Logger LOGGER = Logging.getLogger(ReportTask.class)

    def SpdxLicenseBundleNormalizer(Map params) {
        super(params)
    }

    def SpdxLicenseBundleNormalizer() {
        super()
    }

    @Override
    protected applyDefaultNormalizerBundleFile() {
        LOGGER.debug("Applying default normalizer bundles")

        def normalizerTextStream = getClass().getResourceAsStream("/spdx-license-normalizer-bundle.json")
        def defaultConfig = toConfig(new JsonSlurper().setType(JsonParserType.LAX).parse(normalizerTextStream))

        mergeConfigIntoGlobalConfig(defaultConfig)
    }
}
