#!/bin/bash
set -e

echo "Starting $0 at $(date)"

cd "$(dirname $0)"

impl/build.sh --no-daemon --offline assembleDebug assembleAndroidTest \
    -Pandroidx.useMaxDepVersions \
    -Pandroidx.validateNoUnrecognizedMessages "$@"

echo "Completing $0 at $(date)"
