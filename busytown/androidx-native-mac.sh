#!/bin/bash
set -e
cd "$(dirname $0)"

export ANDROIDX_PROJECTS=KMP

# disable GCP cache, these machines don't have credentials.
export USE_ANDROIDX_REMOTE_BUILD_CACHE=false

impl/build.sh buildOnServer allTests :docs-kmp:zipCombinedKmpDocs --no-configuration-cache -Pandroidx.displayTestOutput=false

# run a separate createArchive task to prepare a repository
# folder in DIST.
# This cannot be merged with the buildOnServer run because
# snapshot version is not a proper release version.
DIST_DIR=$DIST_DIR/snapshots SNAPSHOT=true impl/build.sh createArchive --no-configuration-cache
