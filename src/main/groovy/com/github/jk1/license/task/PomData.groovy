package com.github.jk1.license.task

import groovy.transform.Canonical

@Canonical
class PomData {

	String name, description, projectUrl
	Collection<License> licenses = []

	@Canonical
	static class License {
		String name, url, distribution, comments
	}
}
