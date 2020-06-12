#!/bin/bash
set -e

WORKING_DIR="$(pwd)"
SCRIPT_DIR="$(cd $(dirname $0) && pwd)"
cd "$(dirname $0)/../../.."
echo "Script running from $(pwd)"

# resolve DIST_DIR
if [ -z "$DIST_DIR" ]; then
  DIST_DIR="$WORKING_DIR/out/dist"
fi
mkdir -p "$DIST_DIR"

export OUT_DIR=$(pwd)/out
export DIST_DIR="$DIST_DIR"

if [ "$STUDIO_DIR" == "" ]; then
  STUDIO_DIR="$WORKING_DIR"
else
  STUDIO_DIR="$(cd $STUDIO_DIR && pwd)"
fi

TOOLS_DIR=$STUDIO_DIR/tools
gw=$TOOLS_DIR/gradlew

JAVA_HOME="$STUDIO_DIR/prebuilts/studio/jdk/linux" $gw -p $TOOLS_DIR publishLocal --stacktrace

export GRADLE_PLUGIN_VERSION=`grep -oP "(?<=buildVersion = ).*" $TOOLS_DIR/buildSrc/base/version.properties`
export GRADLE_PLUGIN_REPO="$STUDIO_DIR/out/repo:$STUDIO_DIR/prebuilts/tools/common/m2/repository"
export JAVA_HOME="prebuilts/jdk/jdk11/linux-x86/"
export JAVA_TOOLS_JAR="$JAVA_HOME/lib/tools.jar"
export LINT_PRINT_STACKTRACE=true

$gw -p frameworks/support --no-daemon bOS --stacktrace -Pandroidx.allWarningsAsErrors
DIST_SUBDIR="/ui" $gw -p frameworks/support/ui --no-daemon bOS --stacktrace -Pandroidx.allWarningsAsErrors
