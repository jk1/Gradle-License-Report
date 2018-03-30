package com.github.jk1.license.reader

import org.gradle.api.GradleException

class UnresolvableConfigurationException extends GradleException {

    UnresolvableConfigurationException(String message) {
        super(message)
    }
}
