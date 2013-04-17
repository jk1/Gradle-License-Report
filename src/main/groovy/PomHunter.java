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

import java.net.*;
import java.util.*;
import java.io.*;

import com.google.common.collect.*;
import com.google.common.cache.*;
import static com.google.common.base.Preconditions.*;

import static org.gradle.logging.StyledTextOutput.Style.Header;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.repositories.*;

/**
* Tries to find a {@code *.pom} file for a given artifact.
*/
public class PomHunter {

	private final Collection<MavenArtifactRepository> mavenRepos;
	private final Collection<FlatDirectoryArtifactRepository> flatDirRepos;

	LoadingCache<ModuleVersionIdentifier,Collection<String>> foundPoms =
		CacheBuilder.newBuilder().softValues().build(
			new CacheLoader<ModuleVersionIdentifier,Iterable<String>>() {
				public Iterable<String> load(ModuleVersionIdentifier id) {
					return ImmutableSet.builder()
						.addAll(findInMaven(id))
						.addAll(findInFlatDir(id))
					.build();
				}
			}
		);

	public PomHunter(Project project) {
		mavenRepos = project.getRepositories().withType(MavenArtifactRepository.class);
		flatDirRepos = project.getRepositories().withType(FlatDirectoryArtifactRepository.class);
	}

	public PomData forModuleVersion(ModuleVersionIdentifier id) {
		Iterable<String> found = foundPoms.get(id);
		if(found.isEmpty()) return null;

		return new PomData(found, parentsOf(found));
	}

	public Iterable<String> findInMaven(ModuleVersionIdentifier id) {
		ImmutableSet.Builder<String> found = ImmutableSet.builder();
		for(MavenArtifactRepository mvn : mavenRepos) {
			found.addAll(findInMaven(id, mvn.getUrl()));
			for(URI uri : mvn.getArtifactUrls()) {
				found.addAll(findInMaven(id, uri));
			}
		}
		return found.build();
	}

	public Iterable<String> findInMaven(ModuleVersionIdentifier id, URI uri) {
		if(id.getGroup() != null && !("".equals(id.getGroup()))) {
			final String slashyGroup = id.getGroup().replace('.', '/');
			uri = uri.resolve("/" + slashyGroup);
		}

		uri = uri.resolve("./" + id.getName() + "/" + id.getVersion());

		try {
			return Collections.singleton(IOUtils.toString(uri.resolve("./" + id.getName() + "-" + id.getVersion() + ".pom")));
		} catch(IOException ioe) { /* Ignore */ }
		return Collections.emptySet();
	}

	public Iterable<String> findInFlatDir(ModuleVersionIdentifier id) {
		ImmutableSet.Builder<String> found = ImmutableSet.builder();
		for(FlatDirectoryArtifactRepository repo : flatDirRepos) {
			for(File dir : repo.getDirs()) {
				found.addAll(findInFlatDir(id, dir));
			}
		}
		return found.build();
	}

	public Iterable<String> findInFlatDir(ModuleVersionIdentifier id, File dir) {
		IOFileFilter fileFilter = new OrFileFilter(
			new NameFileFilter(id.getName() + "-" + id.getVersion() + ".pom"),
			new NameFileFilter(id.getName() + ".pom")
		);
		IOFileFilter dirFilter = TrueFileFilter.INSTANCE;

		ImmutableSet.Builder<String> found = ImmutableSet.builder();
		for(File file : FileUtils.listFiles(dir, fileFilter, dirFilter)) {
			found.add(FileUtils.readFileToString(file));
		}
		return found.build();
	}

	public Iterable<String> parentsOf(Iterable<String> poms) {
		ImmutableSet.Builder found = ImmutableSet.builder();
		for(String pom : poms) {
			ModuleVersionIdentifier parent = readParent(pom);
			if(parent != null) found.addAll(foundPoms.get(parent));
		}
		return found.build();
	}

}

