package com.github.jk1.license.filter

import com.github.jk1.license.ProjectData

interface DependencyFilter {

    ProjectData filter(ProjectData source)
}