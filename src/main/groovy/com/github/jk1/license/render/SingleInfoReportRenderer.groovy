package com.github.jk1.license.render

import com.github.jk1.license.ModuleData

import static com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension

/**
 * Abstract class for renderers using only one license per module
 */
abstract class SingleInfoReportRenderer implements ReportRenderer {

    /**
     * @deprecated Use {@link LicenseDataCollector#singleModuleLicenseInfo} instead
     */
    @Deprecated
    protected List<String> moduleLicenseInfo(LicenseReportExtension config, ModuleData data) {
        return LicenseDataCollector.singleModuleLicenseInfo(config, data)
    }
}
