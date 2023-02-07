#!/bin/bash
set -e

function runGradle() {
  kmpArgs="-Pandroidx.compose.multiplatformEnabled=true -Pandroidx.enabled.kmp.target.platforms=+native"
  echo running ./gradlew $kmpArgs "$@"
  if ./gradlew $kmpArgs "$@"; then
    echo succeeded: ./gradlew $kmpArgs "$@"
  else
    echo failed: ./gradlew $kmpArgs "$@"
    return 1
  fi
}

# This script regenerates signature-related information (dependency-verification-metadata and keyring)
function regenerateVerificationMetadata() {
  echo "regenerating verification metadata and keyring"
  # regenerate metadata
  # Need to run a clean build, https://github.com/gradle/gradle/issues/19228
  runGradle --write-verification-metadata pgp,sha256 --export-keys --dry-run --clean bOS
  # extract and keep only the <trusted-keys> section
  WORK_DIR=gradle/update-keys-temp
  rm -rf "$WORK_DIR"
  mkdir -p "$WORK_DIR"

  # extract the middle of the new file, https://github.com/gradle/gradle/issues/18569
  grep -B 10000 "<trusted-keys>" gradle/verification-metadata.dryrun.xml > "$WORK_DIR/new.head"
  grep -A 10000 "</trusted-keys>" gradle/verification-metadata.dryrun.xml > "$WORK_DIR/new.tail"
  numTopLines="$(cat "$WORK_DIR/new.head" | wc -l)"
  numTopLinesPlus1="$(($numTopLines + 1))"
  numBottomLines="$(cat "$WORK_DIR/new.tail" | wc -l)"
  numLines="$(cat gradle/verification-metadata.dryrun.xml | wc -l)"
  numMiddleLines="$(($numLines - $numTopLines - $numBottomLines))"
  # also remove 'version=' lines, https://github.com/gradle/gradle/issues/20192
  cat gradle/verification-metadata.dryrun.xml | tail -n "+$numTopLinesPlus1" | head -n "$numMiddleLines" | sed 's/ version="[^"]*"//' > "$WORK_DIR/new.middle"

  # extract the top and bottom of the old file
  grep -B 10000 "<trusted-keys>" gradle/verification-metadata.xml > "$WORK_DIR/old.head"
  grep -A 10000 "</trusted-keys>" gradle/verification-metadata.xml > "$WORK_DIR/old.tail"

  # update verification metadata file
  cat "$WORK_DIR/old.head" "$WORK_DIR/new.middle" "$WORK_DIR/old.tail" > gradle/verification-metadata.xml

  # remove temporary files
  rm -rf "$WORK_DIR"
  rm -f gradle/verification-keyring-dryrun.gpg
  rm -f gradle/verification-keyring-dryrun.keys
  rm -f gradle/verification-metadata.dryrun.xml
}
regenerateVerificationMetadata

echo
echo "Done. Please check that these changes look correct ('git diff')"
