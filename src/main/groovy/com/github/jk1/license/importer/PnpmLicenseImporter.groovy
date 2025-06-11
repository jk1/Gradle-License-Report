/*
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
package com.github.jk1.license.importer

import groovy.json.JsonSlurper;

import com.github.jk1.license.ImportedModuleBundle;
import com.github.jk1.license.ImportedModuleData;
import com.github.jk1.license.importer.DependencyDataImporter;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.gradle.api.GradleException;

/**
 * Use this importer to add Javascript-Dependencies to your Gradle license report.
 * For example, if you have a Java web application with Javascript frontend
 * dependencies, you could generate a license report with seperate sections
 * for (potentially several) frontends and a backend like so:
 *
 * licenseReport {
 *     renderers = [new InventoryHtmlReportRenderer("index.html", "MyApp Backend", null)];
 *     importers = [new PnpmLicenseImporter("MyApp Frontends", ["./first-fronted", "./second-path"])];
 * }
 *
 * You need to have pnpm installed and your dependencies must be listed in package.json.
 *
 */
class PnpmLicenseImporter implements DependencyDataImporter {

    private String title;
    private List<String> paths;

    PnpmLicenseImporter(String moduleTitle, List<String> pathsToCheck) {
        this.title = moduleTitle;
        this.paths = pathsToCheck;
    }

    public String getImporterName() {
        return "PNPM License Importer";
    }

    public Collection<ImportedModuleBundle> doImport() {
        Collection<ImportedModuleData> importedModules = new ArrayList<>();
        this.paths.each { path ->
            Map licenses = scanDependencies(path);
            licenses.each { key, data ->
                data.each { module ->
                    module.versions.each { version ->
                        importedModules.add(new ImportedModuleData(
                            name: module.name,
                            version: version,
                            projectUrl: module.homepage,
                            license: module.license,
                        ));
                    };
                };
            };
        };
        def bundles = new HashSet<ImportedModuleBundle>();
        bundles.add(new ImportedModuleBundle(this.title, importedModules));
        return bundles;
    }

    private Map scanDependencies(String path) {
        def cmd = "pnpm -C " + path + " licenses list --long --json --prod";
        def proc
        if (System.properties['os.name'].toLowerCase().contains('win')) {
            proc = ["cmd", "/c", cmd].execute()
        } else {
            proc = ["/bin/sh", "-c", cmd].execute()
        }
        def pool = Executors.newFixedThreadPool(2);
        def stdoutFuture = pool.submit({ -> proc.inputStream.text} as Callable<String>);
        def stderrFuture = pool.submit({ -> proc.errorStream.text} as Callable<String>);
        proc.waitFor();
        def exitValue = proc.exitValue();
        if(exitValue != 0) {
            System.err.println(stderrFuture.get())
            throw new GradleException("Running Pnpm License List failed: $cmd returned $exitValue");
        }
        def jsonSlurper = new JsonSlurper();
        return jsonSlurper.parseText(stdoutFuture.get());
    }

}
