package com.github.jk1.license.filter

import com.github.jk1.license.LicenseFileData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData

class ReduceDuplicateLicensesFilter implements DependencyFilter {
    
    @Override
    ProjectData filter(ProjectData projectData) {
        // remove pom duplicates
        projectData.allDependencies*.poms.flatten().forEach { PomData pom ->
            pom.licenses = pom.licenses.unique()
        }

        // remove license-file duplicates
        projectData.allDependencies*.licenseFiles.flatten().forEach { LicenseFileData files ->
            files.fileDetails = files.fileDetails.unique()
            files.files = files.files.unique()
        }

        return projectData
    }
}
