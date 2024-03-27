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
# move OUT_DIR and DIST_DIR into subdirectories so that if anything deletes them, it doesn't interfere with any files generated by buildbot code
export OUT_DIR="$OUT_DIR/incremental"

# Given a file containing a date as text, echos which week number it is
# Examples: input "2024-01-01" should give output "0", input "2024-01-07" should give output "1", input "2024-01-14" should give output "2"
function getWeekNumber() {
  text="$1"
  dayOfYearWithPrecedingZeros="$(date --date="$text" +"%j")"
  dayOfYear="$(echo $dayOfYearWithPrecedingZeros | sed 's/^0*//')"
  if [ "$dayOfYear" == "" ]; then
    # There is an error that we will catch later
    echo
  else
    echo "$(($dayOfYear / 7))"
  fi
}

function deleteOldOutDir() {
  # file telling when the out dir was created
  createdAtFile=$OUT_DIR/created_at.txt
  # file telling when the out dir was last updated
  updatedAtFile=$OUT_DIR/updated_at.txt
  now="$(date)"

  # if this directory was created a long time ago, delete it
  if [ -e "$createdAtFile" ]; then
    createdAt="$(cat "$createdAtFile")"
    # out dir knows when it was created
    createdWeekNumber="$(getWeekNumber "$createdAt" || true)"
    if [ "$createdWeekNumber" == "" ]; then
      echo "Failed to parse $createdAtFile with text $createdAt" >&2
      rm -f "$createdAtFile"
      exit 1
    fi
    updatedWeekNumber="$(getWeekNumber "$now")"

    if [ "$createdWeekNumber" != "$updatedWeekNumber" ]; then
      echo "Deleting $OUT_DIR because it was created at $createdAt week $createdWeekNumber whereas now is $now week $updatedWeekNumber"
      rm -rf "$OUT_DIR"
    fi
  fi
  mkdir -p "$OUT_DIR"

  # record that this directory was updated
  echo "$now" > "$updatedAtFile"

  # if we haven't recorded when this directory was created, do that too
  if [ ! -e "$createdAtFile" ]; then
    cp "$updatedAtFile" "$createdAtFile"
  fi
}
deleteOldOutDir

mkdir -p "$OUT_DIR"
export DIST_DIR="$DIST_DIR/incremental"
mkdir -p "$DIST_DIR"

# Before we start the build, remove temporary directory contents, needs to match gradlew TMPDIR
rm -fr "$OUT_DIR/tmp"

# Temporary workaround for b/331200399. Remove once buildbots snapshot the new out directory
bash -c "cd $OUT_DIR && echo fake_bin* | xargs --no-run-if-empty rm -rf"

if echo "$BUILD_NUMBER" | grep "P" >/dev/null; then
  PRESUBMIT=true
else
  PRESUBMIT=false
fi

export USE_ANDROIDX_REMOTE_BUILD_CACHE=gcp

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
