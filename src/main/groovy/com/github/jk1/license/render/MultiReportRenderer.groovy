package com.github.jk1.license.render

import com.github.jk1.license.ProjectData

/**
 * @deprecated use the {@link com.github.jk1.license.LicenseReportPlugin.LicenseReportExtension#renderers} array instead
 */
@Deprecated
public class MultiReportRenderer implements ReportRenderer {

    private ReportRenderer[] renderers = {}

    public MultiReportRenderer(ReportRenderer... renderers) {
        this.renderers = renderers
    }

    public void render(ProjectData data) {
        if (renderers == null || renderers.length == 0) return;

        for (ReportRenderer renderer : renderers) {
            renderer.render(data)
        }
    }
}
