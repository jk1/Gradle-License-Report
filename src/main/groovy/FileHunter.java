package com.smokejumperit.gradle.report;

import org.apache.commons.lang3.StringEscapeUtils;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.SimpleNodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.AbstractStyledTextOutput;
import org.gradle.util.GUtil;

import java.util.*;
import java.io.*;

import com.google.common.collect.ImmutableSet;
import com.google.common.cache.*;
import static com.google.common.base.Preconditions.*;

import static org.gradle.logging.StyledTextOutput.Style.Header;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

import org.gradle.api.artifacts.*;

public class FileHunter {

	private final Collection<String> namePrefixes = ImmutableSet.of("license", "copying", "notice", "readme");

	private final LoadingCache<File, Map<String,String>> artifacts =
		CacheBuilder.newBuilder().softValues().build(new CacheLoader<File, Map<String,String>>() {
			public Map<String,String> load(File file) {
				return lookForFiles(files);
			}
		});

	public Map<String,String> forArtifact(ResolvedArtifact artifact) {
		return artifacts.get(artifact.getFile());
	}

	private Map<String,String> lookForFiles(File file) {
		ImmutableMap.Builder<String,String> builder = ImmutableMap.builder();

		try(ZipFile zip = new ZipFile(file)) {
			for(ZipEntry entry : Collections.list(zip.entries())) {
				final String name = entry.getName().toLowercase();
				for(String prefix : namePrefixes) {
					if(name.startsWith(prefix) || name.contains("/" + prefix)) {
						try(InputStream stream = zip.getInputStream(entry)) {
							builder.put(entry.getName(), IOUtils.toString(stream));
						}
					}
				}
			}
		}

		return builder.build();
	}

}

