# Changelog

## 3.1.3 (2026-05-15)

## What's Changed
* chore(master): release 3.1.3-SNAPSHOT by @github-actions[bot] in https://github.com/jk1/Gradle-License-Report/pull/391
* test: test against Gradle 9.5.0 by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/395
* build(deps): bump the actions-deps group with 3 updates by @dependabot[bot] in https://github.com/jk1/Gradle-License-Report/pull/397
* build(deps): bump com.gradle.develocity from 4.4.0 to 4.4.1 by @dependabot[bot] in https://github.com/jk1/Gradle-License-Report/pull/396
* build(deps): bump gradle to 8.14.5 by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/398

## New Contributors
* @dependabot[bot] made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/397

**Full Changelog**: https://github.com/jk1/Gradle-License-Report/compare/v3.1.2...v3.1.3

## 3.1.2 (2026-04-03)

## What's Changed
* #313 Use Artifact Resolution Query to retrieve POM files by @Vampire in https://github.com/jk1/Gradle-License-Report/pull/314
* chore: improve Maven metadata during plugin publishing by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/379
* test: sanity check built plugin via existing sample projects by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/378
* Improve snapshot testing by @Vampire in https://github.com/jk1/Gradle-License-Report/pull/380
* build: bump & pin GitHub actions for improved security posture by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/385
* test: test with Gradle 9.4.1 by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/382
* chore(master): release 3.1.2-SNAPSHOT by @github-actions[bot] in https://github.com/jk1/Gradle-License-Report/pull/377
* fix: correct gradle 9 deprecations on use of NamedDomainObjectSet.findAll by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/384
* fix: improve checkLicense failure message to include reasons by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/381
* chore: fix spotless json file copyright notice complaints by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/388
* build: simplify and use Gradle property to specify the test JDK version by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/389
* chore: mark CheckLicense tasks as not CC compatible by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/390


**Full Changelog**: https://github.com/jk1/Gradle-License-Report/compare/v3.1.1...v3.1.2

## 3.1.1 (2026-02-17)

## What's Changed
* docs: update documentation for modern Gradle usage & clarity by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/369
* fix: allow specifying an allowed license rule that matches dependencies with no known license by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/370
* fix: remove warnings from mismatched transformation rules on default bundles by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/372
* fix: improve defualt normalization bundle matching for EPL 1.0 license URLs by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/374
* fix: correct Gradle module metadata for published plugin by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/375
* chore(master): release 3.1.1-SNAPSHOT by @github-actions[bot] in https://github.com/jk1/Gradle-License-Report/pull/371


**Full Changelog**: https://github.com/jk1/Gradle-License-Report/compare/v3.1.0...v3.1.1

## 3.1.0 (2026-02-12)

## What's Changed
* chore(master): release 3.0.2-SNAPSHOT by @github-actions[bot] in https://github.com/jk1/Gradle-License-Report/pull/357
* fix: align normalizer rules between SPDX+default; fixing minor attribution issues by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/360
* feat: add GPL+Universal FOSS exception/FSL/Bouncy Castle/MIT-0 normalising rules by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/359
* Make markdown reporter more diff friendly by @suniala in https://github.com/jk1/Gradle-License-Report/pull/326
* #322 Pick the best matching pom.xml in the jar, instead of randomly choosing the first one. by @AlexanderBartash in https://github.com/jk1/Gradle-License-Report/pull/323
* fix: correct build to target Java 8; with testing across JVMs by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/366
* feat: improve SPDX bundle name matching, while making license normalization rules consistent by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/364
* Support scanning of buildScripts / used gradle plugins by @balrok in https://github.com/jk1/Gradle-License-Report/pull/279
* test: correct tests for multi project build scripts on Java 8 by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/367
* fix: allow native `TextResource` types for allowedLicensesFile; correcting 3.0 documentation by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/361
* docs: use better domain for example resource by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/368
* fix: make dependency cache key deterministic by @jparise in https://github.com/jk1/Gradle-License-Report/pull/363

## New Contributors
* @suniala made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/326
* @AlexanderBartash made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/323

**Full Changelog**: https://github.com/jk1/Gradle-License-Report/compare/v3.0.1...v3.1.0

## 3.0.1 (2025-11-01)

## What's Changed
* chore(master): release 3.0.1-SNAPSHOT by @github-actions[bot] in https://github.com/jk1/Gradle-License-Report/pull/355


**Full Changelog**: https://github.com/jk1/Gradle-License-Report/compare/v3.0.0...v3.0.1

## 3.0.0 (2025-10-30)

## What's Changed
* Update SPDX normalizations by @patricklucas in https://github.com/jk1/Gradle-License-Report/pull/336
* chore(ci): tidy by @bdellegrazie in https://github.com/jk1/Gradle-License-Report/pull/345
* feat(ci): add release-please by @bdellegrazie in https://github.com/jk1/Gradle-License-Report/pull/346
* chore(master): release 2.9.1-SNAPSHOT by @github-actions[bot] in https://github.com/jk1/Gradle-License-Report/pull/347
* feat!: Upgrade Gradle to 8.14.3, JDK 17 by @bdellegrazie in https://github.com/jk1/Gradle-License-Report/pull/349
* Track allowedLicensesFile as task input file by @jparise in https://github.com/jk1/Gradle-License-Report/pull/341
* feat(pnpm): add Pnpm licenses importer by @bdellegrazie in https://github.com/jk1/Gradle-License-Report/pull/353
* Fix deprecation warnings when plugin runs on Gradle 9 by @chadlwilson in https://github.com/jk1/Gradle-License-Report/pull/340
* chore: docs and build update by @bdellegrazie in https://github.com/jk1/Gradle-License-Report/pull/354

## New Contributors
* @patricklucas made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/336
* @bdellegrazie made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/345
* @github-actions[bot] made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/347
* @jparise made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/341
* @chadlwilson made their first contribution in https://github.com/jk1/Gradle-License-Report/pull/340

**Full Changelog**: https://github.com/jk1/Gradle-License-Report/compare/v2.9.0...v3.0.0
