package com.github.jk1.license.render

import com.github.jk1.license.ProjectData


interface ReportRenderer {
    def void render(ProjectData data)
}