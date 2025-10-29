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
