/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license

import org.gradle.api.JavaVersion
import spock.util.environment.Jvm

class GradleTestVersion {
    /**
     * The versions of Gradle supported by the plugin which should be tested against, alongside their supported
     * Java versions.
     *
     * See https://endoflife.date/gradle and/or https://docs.gradle.org/current/userguide/compatibility.html
     */
    final static def supportedVersions = [
            new GradleTestVersion(version: '7.6.6',  minJdk: 8,  maxJdk: 19),
            new GradleTestVersion(version: '8.14.5', minJdk: 8,  maxJdk: 25),
            new GradleTestVersion(version: '9.5.0',  minJdk: 17, maxJdk: 26),
    ]

    /**
     * The versions of Gradle explicitly not supported by the plugin which should be tested against, alongside their supported
     * Java versions.
     */
    private final static def unsupportedVersions = [
            new GradleTestVersion(version: '5.6.4', minJdk: 8, maxJdk: 12),
            new GradleTestVersion(version: '6.9.4', minJdk: 8, maxJdk: 16),
    ]

    final static def supportedVersionsForCurrentJvm =
            supportedVersions.findAll { it.isSupportedOnCurrentJvm() }

    final static def unsupportedVersionsForCurrentJvm =
            unsupportedVersions.findAll { it.isSupportedOnCurrentJvm() }

    static def supportedVersionsForCurrentJvmFrom(int majorVersion) {
        supportedVersionsForCurrentJvm.findAll { it.isAtLeast(majorVersion) }
    }

    String version
    int minJdk
    int maxJdk

    def isSupportedOnCurrentJvm() {
        (minJdk..maxJdk).contains(JavaVersion.toVersion(Jvm.current.javaSpecificationVersion).majorVersion.toInteger())
    }

    def isAtLeast(int majorVersion) {
        version.split("\\.").first().toInteger() >= majorVersion
    }

    @Override
    String toString() {
        version
    }
}
