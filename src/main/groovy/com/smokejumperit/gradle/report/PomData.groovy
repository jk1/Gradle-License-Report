package com.smokejumperit.gradle.report

class PomData {
	
	String name, description, projectUrl
	Collection<Developer> developers = []
	Collection<License> licenses = []
	
	static class License {
		String name, url, distribution, comments
	}
	
	static class Developer {
		String name, email, organization
		Collection<String> roles = []
	}

}
