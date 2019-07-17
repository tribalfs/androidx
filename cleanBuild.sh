#!/bin/bash
set -e

echo "IF THIS SCRIPT FIXES YOUR BUILD; OPEN A BUG."
echo "In nearly all cases, it should not be necessary to run a clean build."
echo
echo "You may be more interested in running:"
echo
echo "  ./development/diagnose-build-failure/diagnose-build-failure.sh $*"
echo
echo "which attempts to diagnose more details about build failures"
# one case where it is convenient to have a clean build is for double-checking that a build failure isn't due to an incremental build failure
# another case where it is convenient to have a clean build is for performance testing
# another case where it is convenient to have a clean build is when you're modifying the build and may have introduced some errors but haven't shared your changes yet (at which point you should have fixed the errors)
echo

DO_PROMPT=true
if [ "$1" == "-y" ]; then
  DO_PROMPT=false
  shift
fi

goals="$@"

function usage() {
  echo
  echo "Usage: $0 [-y] <tasks>"
  echo "Runs a clean build of <tasks>"
  echo
  echo
  echo "For example:"
  echo
  echo "  $0 assembleDebug # or any other arguments you would normally give to ./gradlew"
  echo
  echo
  echo "-y"
  echo "    Don't prompt the user to confirm that they want to run a clean build"
  exit 1
}

if [ "$goals" == "" ]; then
  usage
fi

if [ ! -e "./gradlew" ]; then
  echo "Error; ./gradlew does not exist. Must cd to a dir containing a ./gradlew first"
  # so that this script knows which gradlew to use (in frameworks/support or frameworks/support/ui)
  exit 1
fi

function confirm() {
  # Confirm whether the user wants to run this script instead of diagnose-build-failure.sh
  # Recall that we already mentioned the existence of diagnose-build-failure.sh above
  echo
  echo "Press <Enter> to run a clean build or Ctrl-C to cancel"
  if [ "$DO_PROMPT" == "true" ]; then
    read response
  fi
}
confirm

export OUT_DIR=../../out
function removeCaches() {
  echo removing caches
  rm -rf .gradle
  rm -rf buildSrc/.gradle
  rm -f local.properties
  rm -rf ../../out
}
removeCaches

echo running build
GRADLE_USER_HOME=../../out ./gradlew --no-daemon $goals
