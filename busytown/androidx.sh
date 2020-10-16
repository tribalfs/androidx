#!/bin/bash
set -e

echo "Starting $0 at $(date)"

cd "$(dirname $0)"

# Run Gradle
impl/build.sh --no-daemon listTaskOutputs -Pandroidx.validateNoUnrecognizedMessages "$@"
impl/build.sh allProperties "$@" >/dev/null
impl/build.sh --no-daemon buildOnServer -Pandroidx.validateNoUnrecognizedMessages checkExternalLicenses \
    -PverifyUpToDate \
    -Pandroidx.coverageEnabled=true \
    -Pandroidx.enableAffectedModuleDetection \
    -Pandroidx.allWarningsAsErrors --profile "$@"

# Parse performance profile reports (generated with the --profile option above) and re-export the metrics in an easily machine-readable format for tracking
impl/parse_profile_htmls.sh

echo "Completing $0 at $(date)"
