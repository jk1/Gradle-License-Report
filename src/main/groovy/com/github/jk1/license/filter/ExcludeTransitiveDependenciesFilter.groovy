package com.github.jk1.license.filter

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import org.gradle.api.artifacts.ResolvedDependency
import java.util.stream.Collectors

class ExcludeTransitiveDependenciesFilter implements DependencyFilter {

    @Override
    public ProjectData filter(ProjectData source) {

        Set<ResolvedDependency> firstLevelDependencies = source.getConfigurations()
            .stream()
            .flatMap({ c ->
                source.getProject().getConfigurations().getByName(c.getName())
                    .getResolvedConfiguration().getFirstLevelModuleDependencies()
                    .stream()
            })
            .collect(Collectors.toSet());

        Set<ModuleData> moduleDataSet = source.getAllDependencies()
            .stream()
            .filter({ md ->
                firstLevelDependencies
                    .stream()
                    .anyMatch({ dependency ->
                        md.getName() == dependency.getModuleName() &&
                            md.getGroup() == dependency.getModuleGroup() &&
                            md.getVersion() == dependency.getModuleVersion()
                    })
            })
            .collect(Collectors.toSet())

        return new ProjectData(source.getProject(), source.getConfigurations(), source.getImportedModules()) {
            @Override
            Set<ModuleData> getAllDependencies() {
                return moduleDataSet
            }
        };
    }
}
