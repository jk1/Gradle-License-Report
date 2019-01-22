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
package com.github.jk1.license.render

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.LicenseFileDetails
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ManifestData
import com.github.jk1.license.ModuleData
import com.github.jk1.license.PomData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.util.Paths
import groovy.json.JsonOutput

class DownloadLicensesRenderer implements ReportRenderer {
    private Map<String, LicenseFileData> licenseUrlsToFileTextCache = [:]
    private List<File> allLicenseFiles = []
    private List<File> downloadedHtmlLicenseFiles = []
    private List<File> downLoadedTextLicenseFiles = []
    private List<File> embeddedLicenseFiles = []
    private String htmlFileType = ".html"
    private String textFileType = ".txt"

    DownloadLicensesRenderer(Map<String, File> customizeLicenseUrlToLicenseFile = [:]) {
        customizeLicenseUrlToLicenseFile.each {
            licenseUrlsToFileTextCache.put(it.key, new LicenseFileData(textFileType, it.value.text))
        }
    }

    void render(ProjectData projectData) {
        LicenseReportExtension config= projectData.project.licenseReport
        String outputDir = config.outputDir
        downloadStoreAndReportDependenciesLicenses(projectData, outputDir)
        downloadStoreAndReportImportedModulesLicenses(projectData, outputDir)
        generateLicenseFileReport(projectData, outputDir)
        generateAllHtmlUrlFile(outputDir)
    }

    private void downloadStoreAndReportDependenciesLicenses(ProjectData projectData, String outputDir) {
        projectData.allDependencies.each { downloadStoreAndReportPomsLicenses(it, outputDir) }
        projectData.allDependencies.each { downloadStoreAndReportManifestsLicenses(it, outputDir) }
        projectData.allDependencies.each { reportEmbeddedManifestsLicenses(it, outputDir) }
        projectData.allDependencies.each { reportEmbeddedLicenseFiles(it, outputDir) }
    }

    private void downloadStoreAndReportImportedModulesLicenses(ProjectData projectData, String outputDir) {
        projectData.importedModules.each { downloadStoreAndReportImportedModuleLicenses(it, outputDir) }
    }

    private void generateLicenseFileReport(ProjectData projectData, String outputDir) {
        File file = new File("${outputDir}/automatic-included-license-files-report.json")
        file.createNewFile()
        finalizeReportData()
        file.text = JsonOutput.prettyPrint(JsonOutput.toJson([
            "noLicenseFileDependencies": findAllNoLicenseFilesDependencies(projectData),
            "noLicenseFileImportedModules": findAllNoLicenseFilesImportedModules(projectData),
            "downloadedHtmlLicenseFileDirectories": Paths.allRelativePathBetween(downloadedHtmlLicenseFiles.path, outputDir),
            "downloadedTextLicenseFileDirectories": Paths.allRelativePathBetween(downLoadedTextLicenseFiles.path, outputDir),
            "embeddedLicenseFileDirectories": Paths.allRelativePathBetween(embeddedLicenseFiles.path, outputDir)
        ]))
    }

    private void generateAllHtmlUrlFile(String outputDir) {
        File file = new File("${outputDir}/automatic-included-license-html-files-contents.html")
        file.createNewFile()
        file.text = downloadedHtmlLicenseFiles.text.sort().join()
    }

    private void downloadStoreAndReportPomsLicenses(ModuleData dependency, String outputDir) {
        String filePath = Paths.createPathNameFrom(dependency, "DOWNLOADED-POM-LICENSES")
        dependency.poms.each { downloadStoreAndReportPomLicenses(it, outputDir, filePath) }
    }

    private void downloadStoreAndReportManifestsLicenses(ModuleData dependency, String outputDir) {
        String filePath = Paths.createPathNameFrom(dependency, "DOWNLOADED-MANIFEST-LICENSES")
        dependency.manifests.findAll { it.license }.each { downloadStoreAndReportManifestLicenses(it, outputDir, filePath) }
    }

    private void reportEmbeddedManifestsLicenses(ModuleData dependency, String outputDir) {
        dependency.manifests.findAll { it.hasPackagedLicense }.each { reportEmbeddedLicenses(it.url, outputDir) }
    }

    private void reportEmbeddedLicenseFiles(ModuleData dependency, String outputDir) {
        dependency.licenseFiles.each { reportEmbeddedFileDetailsLicenses(it.fileDetails, outputDir) }
    }

    private void downloadStoreAndReportImportedModuleLicenses(ImportedModuleBundle importedModule, String outputDir) {
        importedModule.modules.findAll {it.licenseUrl}.each {
            String filePath = Paths.createPathNameFrom(it, "DOWNLOADED-MODULE_LICENSES")
            downloadStoreAndReportLicenses(it.licenseUrl, it.license, outputDir, filePath)
        }
    }

    private void finalizeReportData() {
        downloadedHtmlLicenseFiles = allLicenseFiles.findAll { it.name.contains(htmlFileType) }
        downLoadedTextLicenseFiles = allLicenseFiles.findAll { it.path.contains("DOWNLOADED") && it.name.contains(textFileType)}
        embeddedLicenseFiles = allLicenseFiles.findAll { !it.path.contains("DOWNLOADED") }
    }

    private List<String> findAllNoLicenseFilesDependencies(ProjectData projectData) {
        projectData.allDependencies.findAll { isDependencyHasNoLicenseFile(it) }
            .collect { "${it.group}:${it.name}:${it.version}".toString() }.sort()
    }

    private List<String> findAllNoLicenseFilesImportedModules(ProjectData projectData) {
        projectData.importedModules.modules.flatten().findAll { isImportedModuleHasNoLicenseFile(it) }
            .collect { "${it.name}:${it.version}".toString() }.sort()
    }

    private void downloadStoreAndReportPomLicenses(PomData pom, String outputDir, String filePath) {
        pom.licenses.findAll { it.url }.each { downloadStoreAndReportLicenses(it.url, it.name, outputDir, filePath) }
    }

    private void downloadStoreAndReportManifestLicenses(ManifestData manifest, String outputDir, String filePath) {
        List<String> licenseUrls = manifest.license.split(/([,])/)*.trim().findAll { !it.contains(" ") }
        licenseUrls.each { downloadStoreAndReportLicenses(it, it, outputDir, filePath) }
    }

    private void reportEmbeddedFileDetailsLicenses(Collection<LicenseFileDetails> fileDetails, String outputDir) {
        fileDetails.collect { it.file }.findAll { it }.each { reportEmbeddedLicenses(it, outputDir) }
    }

    private boolean isDependencyHasNoLicenseFile(ModuleData dependency) {
        allLicenseFiles.path.findAll { it.contains(Paths.createPathNameFrom(dependency)) }.empty
    }

    private boolean isImportedModuleHasNoLicenseFile(ImportedModuleData importedModuleData) {
        allLicenseFiles.path.findAll { it.contains(Paths.createPathNameFrom(importedModuleData)) }.empty
    }

    private void downloadStoreAndReportLicenses(String licenseUrl, String licenseName, String outputDir, String filePath) {
        LicenseFileData fileData = downloadFileTextIfNotInCache(licenseUrl)
        File newFile = createLicenseFile("${outputDir}/${filePath}", licenseName, fileData.fileType, fileData.fileText)
        allLicenseFiles.push(newFile)
    }

    private void reportEmbeddedLicenses(String url, String outputDir) {
        File localFile = new File("${outputDir}/${url}")
        allLicenseFiles.push(localFile)
    }

    private LicenseFileData downloadFileTextIfNotInCache(String url) {
        return licenseUrlsToFileTextCache.computeIfAbsent(url) { downloadLicenseFileData(url) }
    }

    private File createLicenseFile(String filePath, String licenseName, String fileType, String fileText) {
        String fileName = Paths.createFileNameFromLicenseName(licenseName, fileType)
        File file = new File(filePath, fileName)
        if (file.exists() && file.text != fileText) file = new File(filePath, fileName.replace(fileType, "_1${fileType}"))
        file.parentFile.mkdirs()
        file.createNewFile()
        file.text = fileText
        return file
    }

    private LicenseFileData downloadLicenseFileData(String url) {
        try {
            URLConnection urlConnection = new URL(url).openConnection()
            urlConnection.addRequestProperty("Accept", "text/plain")
            if (urlConnection.contentType) {
                if (urlConnection.contentType.contains("text/plain")) {
                    return new LicenseFileData(textFileType, urlConnection.inputStream.text)
                }
            }
            return new LicenseFileData(htmlFileType, urlToHtmlText(url))
        } catch (IOException e) {
            return new LicenseFileData(htmlFileType, urlToHtmlText(url))
        }
    }

    private String urlToHtmlText(String url) {
        return "<html><body><div><a href = \"${url}\">${url}</a></div></body></html>\n"
    }

    /**this could replace the github url to raw files*/
    /*private String replaceGitHubLicenseUrl(String licenseUrl) {
        if (licenseUrl.contains("https://github.com/") && licenseUrl.contains("blob/")) {
            licenseUrl = licenseUrl.replace("https://github.com", "https://raw.githubusercontent.com")
            .replace("blob/", "")
        }
        return licenseUrl
    }*/

    /**this could replace the gnu url to txt files*/
    /*private String replaceGnuLicenseUrl(String licenseUrl) {
        if (licenseUrl.contains("www.gnu.org") && !licenseUrl.contains("classpath")) {
            licenseUrl = licenseUrl.replace(".html", ".txt")
        }
        return licenseUrl
    }*/


    /**this could add default License Text to certain url*/
    /*private void addDefaultLicenseText(Map<String, String> customizeLicenseText) {
        customizeLicenseText.each {
            licenseUrlsToFileTextCache.put(it.key, it.value)
        }
    }*/

    private static class LicenseFileData {
        String fileType, fileText

        LicenseFileData(String fileType, String fileText) {
            this.fileType = fileType
            this.fileText = fileText
        }
    }
}
