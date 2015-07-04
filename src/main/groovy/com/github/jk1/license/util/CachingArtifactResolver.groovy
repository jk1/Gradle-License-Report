package com.github.jk1.license.util

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.concurrent.atomic.AtomicInteger

class CachingArtifactResolver {

    private Logger LOGGER = Logging.getLogger(Task.class)

    private static AtomicInteger counter = new AtomicInteger()
    private Map<Map<String, String>, Collection<ResolvedArtifact>> cache =
            new HashMap<Map<String, String>, Collection<ResolvedArtifact>>()
    private Project project

    public CachingArtifactResolver(Project project) {
        this.project = project
    }

    public Collection<ResolvedArtifact> resolveArtifacts(Map<String, String> spec) {
        try {
            Map<String, String> copy = new HashMap<String, String>(spec)
            if (!cache.containsKey(copy)){
                cache.put(copy, doResolveArtifact(copy))
            }
            return cache.get(copy)
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve artifacts for " + spec, e)
            return Collections.emptyList()
        }
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
