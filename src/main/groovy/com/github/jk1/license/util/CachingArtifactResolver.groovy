package com.github.jk1.license.util

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact

import java.util.concurrent.atomic.AtomicInteger

class CachingArtifactResolver {

    private static AtomicInteger counter = new AtomicInteger()
    private Map<Map<String, String>, Collection<ResolvedArtifact>> cache =
            new HashMap<Map<String, String>, Collection<ResolvedArtifact>>()
    private Project project

    CachingArtifactResolver(Project project) {
        this.project = project
    }

    Collection<ResolvedArtifact> resolveArtifacts(Map<String, String> spec) {
        Map<String, String> copy = new HashMap<String, String>()
        spec.each { copy.put(it.key.trim(), it.value.trim()) }
        if (!cache.containsKey(copy)) {
            cache.put(copy, doResolveArtifact(copy))
        }
        return cache.get(copy)
    }

    private Collection<ResolvedArtifact> doResolveArtifact(Object spec) {
        String configName = "dependencyLicenseReport${counter.incrementAndGet()}"
        project.configurations.create("$configName")
        project.dependencies."$configName"(spec)
        Configuration config = project.configurations.getByName(configName)
        Collection<ResolvedArtifact> artifacts = config.resolvedConfiguration.resolvedArtifacts
        if (artifacts != null) {
            // Exercise #getFile() to download the file and catch exceptions here
            for (ResolvedArtifact artifact : artifacts) {
                artifact.getFile()
            }
        }
        return artifacts
    }
}
