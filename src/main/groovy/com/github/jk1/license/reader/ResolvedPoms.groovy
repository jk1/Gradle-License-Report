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
package com.github.jk1.license.reader

/** POM files resolved by {@link PomResolver}, keyed by {@code group:name:version}. */
class ResolvedPoms {
    private final Map<String, List<File>> poms

    ResolvedPoms(Map<String, List<File>> poms) {
        this.poms = poms
    }

    Collection<File> find(String group, String name, String version) {
        poms.get("${group.trim()}:${name.trim()}:${version.trim()}".toString()) ?: []
    }
}
