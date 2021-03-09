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

scriptDir="$(cd $(dirname $0) && pwd)"
checkoutDir="$(cd $scriptDir/../.. && pwd)"
export OUT_DIR="$checkoutDir/out"
function removeCaches() {
  echo removing caches
  rm -rf .gradle
  rm -rf buildSrc/.gradle
  rm -f local.properties
  # We move the Gradle cache (via the OUT_DIR) variable during this build to prevent
  # Other Gradle builds in other directories from sharing it, to be extra-sure that the
  # build will be clean. However, if the user subsequently runs `./gradlew`, it will use
  # ~/.gradle as the Gradle cache dir, which could surprise users because it might hold
  # different state. So, we preemptively remove ~/.gradle too, just in case the user
  # is going to want that for their following build
  rm -rf ~/.gradle
  # AGP should (also) do this automatically (b/170640263)
  rm -rf appsearch/appsearch/.cxx
  rm -rf appsearch/local-backend/.cxx
  rm -rf appsearch/local-storage/.cxx
  rm -rf $OUT_DIR
}
removeCaches

echo running build
./gradlew --no-daemon $goals
