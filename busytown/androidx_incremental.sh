#!/bin/bash
set -e

echo "Starting $0 at $(date)"

cd "$(dirname $0)"

CHECKOUT_DIR="$(cd ../../.. && pwd)"
OUT_DIR="$CHECKOUT_DIR/out"
if [ "$DIST_DIR" == "" ]; then
  DIST_DIR="$OUT_DIR/dist"
fi
if [ "$MANIFEST" == "" ]; then
  export MANIFEST="$DIST_DIR/manifest_${BUILD_NUMBER}.xml"
fi
# move OUT_DIR and DIST_DIR into subdirectories so that if diagnose-build-failure deletes them, it doesn't interfere with any files generated by buildbot code
export OUT_DIR="$OUT_DIR/incremental"
mkdir -p "$OUT_DIR"
export DIST_DIR="$DIST_DIR/incremental"
mkdir -p "$DIST_DIR"

if echo "$BUILD_NUMBER" | grep "P" >/dev/null; then
  PRESUBMIT=true
else
  PRESUBMIT=false
fi

export USE_ANDROIDX_REMOTE_BUILD_CACHE=gcp

# hash the files in the out dir in case we want to confirm which files changed during the build
function hashOutDir() {
  hashFile=out.hashes
  echo "hashing out dir and saving into $DIST_DIR/$hashFile"
  # We hash files in parallel for more performance (-P <number>)
  # We limit the number of files hashed by any one process (-n <number>) to lower the risk of one
  # process having to do much more work than the others.
  # We do allow each process to hash multiple files (also -n <number>) to avoid spawning too many processes
  # It would be nice to copy all files, but that takes a while
  (cd $OUT_DIR && find -type f | grep -v "$hashFile" | xargs --no-run-if-empty -P 32 -n 64 sha1sum > $DIST_DIR/$hashFile)
  echo "done hashing out dir"
}
# disable temporarily b/276812697
# hashOutDir

# If we encounter a failure in postsubmit, we try a few things to determine if the failure is
# reproducible
DIAGNOSE_ARG=""
if [ "$PRESUBMIT" == "false" ]; then
  if [ "$BUILD_NUMBER" == "" ]; then
    # This is a local build so we can diagnose without a timeout. The user can cancel it when they're satisfied.
    DIAGNOSE_ARG="--diagnose"
  else
    # This is running on the build server so we should not spend long trying to diagnose it
    DIAGNOSE_ARG="--diagnose --diagnose-timeout 600"
  fi
fi

EXIT_VALUE=0

# Validate translation exports, if present
if ! impl/check_translations.sh; then
  echo check_translations failed
  EXIT_VALUE=1
else
    # Run Gradle
    if impl/build.sh $DIAGNOSE_ARG buildOnServer checkExternalLicenses listTaskOutputs exportSboms \
        "$@"; then
    echo build succeeded
    EXIT_VALUE=0
    else
    echo build failed
    EXIT_VALUE=1
    fi

    # Parse performance profile reports (generated with the --profile option) and re-export the metrics in an easily machine-readable format for tracking
    impl/parse_profile_data.sh
fi

echo "Completing $0 at $(date) with exit value $EXIT_VALUE"

exit "$EXIT_VALUE"
