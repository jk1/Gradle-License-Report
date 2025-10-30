# Release Management

## Overview

This project uses [Semantic Versioning][1] and relies on [Conventional Commits][2] to determine the next release version.

[Release Please][3] is configured to use the Java snapshot release process and manages 2 PRs in succession:

- One for the release, tracking changes on the mainline branch, active most of the time collecting the changes for the release.
- One for the next snapshot, preparing the repo for the next development phase.

Once a release PR is merged and the release created, Release Please creates the snapshot PR, this should be merged immediately in preparation for the next development phase.

<!-- References -->

[1]: https://semver.org/
[2]: https://www.conventionalcommits.org/en/v1.0.0/
[3]: https://github.com/googleapis/release-please
