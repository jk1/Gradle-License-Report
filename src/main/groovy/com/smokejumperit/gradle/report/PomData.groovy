package com.smokejumperit.gradle.report

import groovy.transform.Canonical

@Canonical
class PomData {

	String name, description, projectUrl
	Collection<Developer> developers = []
	Collection<License> licenses = []

	@Canonical
	static class License {
		String name, url, distribution, comments
	}

	@Canonical
	static class Developer {
		String name, email, organization
		Collection<String> roles = []
	}
}
