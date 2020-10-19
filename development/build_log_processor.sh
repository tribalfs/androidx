#!/bin/bash
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

usage() {
  echo "usage: $0 <command> <arguments> [options]"
  echo
  echo "Executes <command> <arguments> and then runs build_log_simplifier.py against its output"
  echo
  echo "-Pandroidx.summarizeStderr"
  echo "  Run build_log_simplifier.py on failure to produce a summary of the build output"
  echo
  echo "-Pandroidx.validateNoUnrecognizedMessages"
  echo "  Run build_log_simplifier.py --validate on success to confirm that the build generated no unrecognized messages"
  exit 1
}

if [[ "$1" == "" ]]; then
  usage
fi

summarizeOnFailure=false
if [[ " ${@} " =~ " -Pandroidx.summarizeStderr " ]]; then
  summarizeOnFailure=true
fi
validateNoUnrecognizedMessagesOnSuccess=false
validateArgument="-Pandroidx.validateNoUnrecognizedMessages"
if [[ " ${@} " =~ " $validateArgument " ]]; then
  validateNoUnrecognizedMessagesOnSuccess=true
fi

# run Gradle and save stdout and stderr into $logFile
SCRIPT_PATH="$(cd $(dirname $0) && pwd)"
CHECKOUT="$(cd "$SCRIPT_PATH/../../.." && pwd)"
if [ -n "$DIST_DIR" ]; then
  LOG_DIR="$DIST_DIR"
else
  LOG_DIR="$CHECKOUT/out/dist"
fi

mkdir -p "$LOG_DIR"
logFile="$LOG_DIR/gradle.log"
rm -f "$logFile"
# Save OUT_DIR and some other variables into the log file so build_log_simplifier.py can
# identify them later
echo "OUT_DIR=$OUT_DIR" | tee -a $logFile
if [ "$DIST_DIR" == "" ]; then
  DIST_DIR="$OUT_DIR/dist"
fi
echo "DIST_DIR=$DIST_DIR" | tee -a $logFile
echo "CHECKOUT=$CHECKOUT" | tee -a $logFile
programName="$1"
shift
if "$programName" "$@" > >(tee -a "$logFile") 2>&1; then
  if [ "$validateNoUnrecognizedMessagesOnSuccess" == "true" ]; then
    if $SCRIPT_PATH/build_log_simplifier.py --validate $logFile >&2; then
      echo No unrecognized messages found in build log
    else
      echo >&2
      echo "Build log validation, enabled by the argument $validateArgument, failed" >&2
      exit 1
    fi
  fi
else
  echo >&2
  echo "############################################################################" >&2
  echo "Attempting to locate the relevant error messages via build_log_simplifier.py" >&2
  echo "############################################################################" >&2
  echo >&2
  # Try to identify the most relevant lines of output, and put them at the bottom of the
  # output where they will also be placed into the build failure email.
  # TODO: We may be able to stop cleaning up Gradle's output after Gradle can do this on its own:
  # https://github.com/gradle/gradle/issues/1005
  # and https://github.com/gradle/gradle/issues/13090
  summaryLog="$LOG_DIR/error_summary.log"
  $SCRIPT_PATH/build_log_simplifier.py $logFile | tail -n 100 | tee "$summaryLog" >&2
  exit 1
fi
