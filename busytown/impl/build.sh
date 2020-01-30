#!/bin/bash
set -e

# This script runs frameworks/support/gradlew and frameworks/support/ui/gradlew , each with the same arguments

function echoAndDo() {
  echo "cd $(pwd)"
  echo "$*"
  if eval "$*"; then
    echo "Succeeded: $*"
  else
    echo "Failed: $*"
    return 1
  fi
}

# find script
SCRIPT_DIR="$(cd $(dirname $0) && pwd)"

# resolve DIST_DIR
if [ "$DIST_DIR" == "" ]; then
  DIST_DIR="$SCRIPT_DIR/../../../../out/dist"
fi
mkdir -p "$DIST_DIR"

# cd to checkout root
cd "$SCRIPT_DIR/../../../.."

# run gradle
echoAndDo OUT_DIR=out/ui DIST_DIR=$DIST_DIR/ui ANDROID_HOME=./prebuilts/fullsdk-linux frameworks/support/ui/gradlew -p frameworks/support/ui "$@"
echoAndDo OUT_DIR=out    DIST_DIR=$DIST_DIR    ANDROID_HOME=./prebuilts/fullsdk-linux frameworks/support/gradlew    -p frameworks/support    "$@"
