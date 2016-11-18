package com.github.jk1.license.render

import com.github.jk1.license.ProjectData


interface ReportRenderer {
    void render(ProjectData data)
}