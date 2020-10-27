#!/bin/bash
#
#  Copyright (C) 2020 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

set -e

function usage() {
  echo 'NAME'
  echo '  simplify-build-failure.sh'
  echo
  echo 'SYNOPSIS'
  echo "  $0 (--task <gradle task> <error message> [--clean] | --command <shell command> ) [--continue] [--limit-to-path <file path>] [--check-lines-in <subfile path>] [--num-jobs <count>]"
  echo
  echo DESCRIPTION
  echo '  Searches for a minimal set of files and/or lines required to reproduce a given build failure'
  echo
  echo OPTIONS
  echo
  echo '  --task <gradle task> <error message>`'
  echo '    Specifies that `./gradlew <gradle task>` must fail with error message <error message>'
  echo
  echo '  --command <shell command>'
  echo '    Specifies that <shell command> must succeed.'
  echo
  echo '  --continue'
  echo '    Attempts to pick up from a previous invocation of simplify-build-failure.sh'
  echo
  echo '  --limit-to-path <limitPath>'
  echo '    Will check only <limitPath> (plus subdirectories, if present) for possible simplications. This can make the simplification process faster if there are paths that you know are'
  echo '    uninteresting to you'
  echo
  echo '  --check-lines-in <subfile path>'
  echo '    Specifies that individual lines in files in <subfile path> will be considered for removal, too'
  echo
  echo '  --num-jobs <count>'
  echo '    Specifies the number of jobs to run at once'
  echo
  echo '  --clean'
  echo '    Specifies that each build should start from a consistent state'
  exit 1
}

function notify() {
  echo simplify-build-failure.sh $1
  notify-send simplify-build-failure.sh $1
}

function failed() {
  notify failed
  exit 1
}

gradleTasks=""
errorMessage=""
gradleCommand=""
grepCommand=""
testCommand=""
resume=false
subfilePath=""
limitToPath=""
numJobs="1"
clean="false"

export ALLOW_MISSING_PROJECTS=true # so that if we delete entire projects then the AndroidX build doesn't think we made a spelling mistake

workingDir="$(pwd)"
cd "$(dirname $0)"
scriptPath="$(pwd)"
cd ../..
supportRoot="$(pwd)"
checkoutRoot="$(cd $supportRoot/../.. && pwd)"
tempDir="$checkoutRoot/simplify-tmp"

# If the this script was run from a subdirectory, then we run our test command from the same subdirectory
commandSubdir="$(echo $workingDir | sed "s|^$supportRoot|.|g")"

if [ ! -e "$workingDir/gradlew" ]; then
  echo "Error; ./gradlew does not exist. Must cd to a dir containing a ./gradlew first"
  # so that this script knows which gradlew to use (in frameworks/support or frameworks/support/ui)
  exit 1
fi

while [ "$1" != "" ]; do
  arg="$1"
  shift
  if [ "$arg" == "--continue" ]; then
    resume=true
    continue
  fi
  if [ "$arg" == "--task" ]; then
    gradleTasks="$1"
    shift
    errorMessage="$1"
    shift
    if [ "$gradleTasks" == "" ]; then
      usage
    fi

    if [ "$errorMessage" == "" ]; then
      usage
    fi

    gradleCommand="OUT_DIR=out ./gradlew $gradleTasks > log 2>&1"
    grepCommand="$scriptPath/impl/grepOrTail.sh \"$errorMessage\" log"
    continue
  fi
  if [ "$arg" == "--command" ]; then
    if [ "$1" == "" ]; then
      usage
    fi
    testCommand="cd $commandSubdir && $1"
    shift
    gradleCommand=""
    grepCommand=""
    if echo "$testCommand" | grep -v OUT_DIR 2>/dev/null; then
      echo "Error: must set OUT_DIR in the test command to prevent concurrent Gradle executions from interfering with each other"
      exit 1
    fi
    continue
  fi
  if [ "$arg" == "--check-lines-in" ]; then
    subfilePath="$1"
    shift
    continue
  fi
  if [ "$arg" == "--limit-to-path" ]; then
    limitToPath="$1"
    shift
    continue
  fi
  if [ "$arg" == "--num-jobs" ]; then
    numJobs="$1"
    shift
    continue
  fi
  if [ "$arg" == "--clean" ]; then
    clean=true
    continue
  fi
  echo "Unrecognized argument '$arg'"
  usage
done

if [ "$gradleCommand" == "" ]; then
  if [ "$clean" == "true" ]; then
    echo "Option --clean requires option --task"
    usage
  fi
  if [ "$testCommand" == "" ]; then
    usage
  fi
fi

# delete temp dir if not resuming
if [ "$resume" == "true" ]; then
  if [ -d "$tempDir" ]; then
    echo "Not deleting temp dir $tempDir"
  fi
else
  echo "Removing temp dir $tempDir"
  rm "$tempDir" -rf
fi

# backup code so user can keep editing
if [ ! -e "$referenceFailingDir" ]; then
  echo backup up frameworks/support into "$referenceFailingDir" in case you want to continue to make modifications or run other builds
  rm "$referenceFailingDir" -rf
  cp -rT . "$referenceFailingDir"
  # remove some unhelpful settings
  sed -i 's/.*Werror.*//' "$referenceFailingDir/buildSrc/build.gradle"
  sed -i 's/.*Exception.*cannot include.*//' "$referenceFailingDir/settings.gradle"
  # remove some generated files that we don't want diff-filterer.py to track
  rm -rf "$referenceFailingDir/.gradle" "$referenceFailingDir/buildSrc/.gradle" "$referenceFailingDir/out"
fi

# if Gradle tasks are specified, then determine the appropriate shell command
if [ "$gradleCommand" != "" ]; then
  # if --clean is specified, then determine whether we can at least start with a minimal out/ dir
  startingOutDir="$tempDir/failing-out"
  outTestDir="$tempDir/out-test"
  if [ "$clean" != "true" ]; then
    if [ "$resume" == "true" ]; then
      if [ -e "$startingOutDir" ]; then
        echo Will clean before each build because that was the previously computed setting
        clean=true
      else
        echo Will not clean before each build because that was the previously computed setting
      fi
    else
      echo Determining whether we must clean before each build
      rm "$outTestDir" -rf
      mkdir -p "$tempDir"
      cp -r "$referenceFailingDir" "$outTestDir"
      echo Doing first test build
      if bash -c "cd "$outTestDir/$commandSubdir" && $gradleCommand; $grepCommand"; then
        echo Reproduced the problem
      else
        echo Failed to reproduce the problem
        exit 1
      fi
      echo Doing another test build to determine if cleaning between builds is necessary
      if bash -c "cd "$outTestDir/$commandSubdir" && $gradleCommand; $grepCommand"; then
        echo Reproduced the problem even when not starting from a clean out/ dir
      else
        echo Did not reproduce the problem when starting from previous out/ dir
        echo Will have to clean the out/ dir before each build
        clean=true
      fi
    fi
  fi
  if [ "$clean" == "true" ]; then
    if [ -e "$startingOutDir" ]; then
      echo Reusing existing base out dir of "$startingOutDir"
    else
      echo Checking whether we can prepopulate a minimal out/ dir for faster execution
      rm "$outTestDir" -rf
      mkdir -p "$tempDir"
      cp -r "$supportRoot" "$outTestDir"
      if bash -c "cd "$outTestDir/$commandSubdir" && OUT_DIR=out ./gradlew projects --no-daemon && $gradleCommand; $grepCommand"; then
        echo Will reuse base out dir of "$startingOutDir"
        cp -r "$outTestDir/$commandSubdir/out" "$startingOutDir"
      else
        echo Will start subsequent builds from empty out dir
        mkdir -p "$startingOutDir"
      fi
    fi
    gradleCommand="rm out .gradle buildSrc/.gradle -rf && cp -r $startingOutDir out && $gradleCommand --no-daemon"
  fi
  # Sleep in case Gradle fails very quickly
  # We don't want to run too many Gradle commands in a row or else the daemons might get confused
  testCommand="cd $commandSubdir && $gradleCommand; sleep 2; $grepCommand"
  if [ "$clean" == true ]; then
    # reset the out/ dir
    gradle_prepareState_command="rm out .gradle buildSrc/.gradle -rf && cp -r $startingOutDir out"
    # update the timestamps on all files in case they affect anything
    gradle_prepareState_command="$gradle_prepareState_command && find -type f | xargs touch || true"

    gradleCommand="$(echo "$gradleCommand" | sed 's/gradlew/gradlew --no-daemon/')"
  else
    gradle_prepareState_command=""
  fi
fi


filtererStep1Work="$tempDir"
filtererStep1Output="$filtererStep1Work/bestResults"
fewestFilesOutputPath="$tempDir/fewestFiles"
if echo "$resume" | grep "true" >/dev/null && stat "$fewestFilesOutputPath" >/dev/null 2>/dev/null; then
  echo "Skipping asking diff-filterer for a minimal set of files, $fewestFilesOutputPath already exists"
else
  if [ "$resume" == "true" ]; then
    if stat "$filtererStep1Output" >/dev/null 2>/dev/null; then
      echo "Reusing $filtererStep1Output to resume asking diff-filterer for a minimal set of files"
      # Copy the previous results to resume from
      rm "$referenceFailingDir" -rf
      cp -rT "$filtererStep1Output" "$referenceFailingDir"
    else
      echo "Cannot resume previous execution; neither $fewestFilesOutputPath nor $filtererStep1Output exists"
      exit 1
    fi
  fi
  echo Running diff-filterer.py once to identify the minimal set of files needed to reproduce the error
  if ./development/file-utils/diff-filterer.py --assume-no-side-effects --work-path $filtererStep1Work --num-jobs "$numJobs" "$referenceFailingDir" "$referencePassingDir" "$testCommand"; then
    echo diff-filterer completed successfully
  else
    failed
  fi
fi


if [ "$subfilePath" == "" ]; then
  echo Splitting files into individual lines was not enabled. Done. See results at $filtererStep1Work/bestResults
else
  if [ "$subfilePath" == "." ]; then
    subfilePath=""
  fi
  if echo "$resume" | grep true >/dev/null && stat $fewestFilesOutputPath >/dev/null 2>/dev/null; then
    echo "Skipping recopying $filtererStep1Output to $fewestFilesOutputPath"
  else
    echo Copying minimal set of files into $fewestFilesOutputPath
    rm -rf "$fewestFilesOutputPath"
    cp -rT "$filtererStep1Output" "$fewestFilesOutputPath"
  fi

  echo Creating working directory for identifying individually smallest files
  noFunctionBodies_Passing="$tempDir/noFunctionBodies_Passing"
  noFunctionBodies_goal="$tempDir/noFunctionBodies_goal"
  noFunctionBodies_work="work"
  noFunctionBodies_sandbox="$noFunctionBodies_work/$subfilePath"
  noFunctionBodies_output="$tempDir/noFunctionBodies_output"

  # set up command for running diff-filterer against diffs within files
  filtererOptions="--num-jobs $numJobs"
  if echo $subfilePath | grep -v buildSrc >/dev/null 2>/dev/null; then
    # If we're not making changes in buildSrc, then we want to keep the gradle caches around for more speed
    # If we are making changes in buildSrc, then Gradle doesn't necessarily do up-to-date checks correctly, and we want to clear the caches between builds
    filtererOptions="$filtererOptions --assume-no-side-effects"
  fi

  if echo "$resume" | grep true >/dev/null && stat "$noFunctionBodies_output" >/dev/null 2>/dev/null; then
    echo "Skipping asking diff-filterer to remove function bodies because $noFunctionBodies_output already exists"
  else
    echo Splitting files into smaller pieces
    rm -rf "$noFunctionBodies_Passing" "$noFunctionBodies_goal"
    mkdir -p "$noFunctionBodies_Passing" "$noFunctionBodies_goal"
    cd "$noFunctionBodies_Passing"
    cp -rT "$fewestFilesOutputPath" "$noFunctionBodies_work"
    cp -rT "$noFunctionBodies_Passing" "$noFunctionBodies_goal"

    splitsPath="${subfilePath}.split"
    "${scriptPath}/impl/split.sh" --consolidate-leaves "$noFunctionBodies_sandbox" "$splitsPath"
    rm "$noFunctionBodies_sandbox" -rf

    echo Removing deepest lines
    cd "$noFunctionBodies_goal"
    "${scriptPath}/impl/split.sh" --remove-leaves "$noFunctionBodies_sandbox" "$splitsPath"
    rm "$noFunctionBodies_sandbox" -rf

    # TODO: maybe we should make diff-filterer.py directly support checking individual line differences within files rather than first running split.sh and asking diff-filterer.py to run join.sh
    # It would be harder to implement in diff-filterer.py though because diff-filterer.py would also need to support comparing against nonempty files too
    echo Running diff-filterer.py again to identify which function bodies can be removed
    if "$supportRoot/development/file-utils/diff-filterer.py" --assume-input-states-are-correct $filtererOptions --work-path "$(cd $supportRoot/../.. && pwd)" "$noFunctionBodies_Passing" "$noFunctionBodies_goal" "${scriptPath}/impl/join.sh ${splitsPath} ${noFunctionBodies_sandbox} && cd ${noFunctionBodies_work} && $testCommand"; then
      echo diff-filterer completed successfully
    else
      failed
    fi

    echo Re-joining the files
    rm -rf "${noFunctionBodies_output}"
    cp -rT "$(cd $supportRoot/../../bestResults && pwd)" "${noFunctionBodies_output}"
    cd "${noFunctionBodies_output}"
    "${scriptPath}/impl/join.sh" "${splitsPath}" "${noFunctionBodies_sandbox}"
  fi

  # prepare for another invocation of diff-filterer, to remove other code that is now unused
  smallestFilesInput="$tempDir/smallestFilesInput"
  smallestFilesGoal="$tempDir/smallestFilesGoal"
  smallestFilesWork="work"
  smallestFilesSandbox="$smallestFilesWork/$subfilePath"

  rm -rf "$smallestFilesInput" "$smallestFilesGoal"
  mkdir -p "$smallestFilesInput"
  cp -rT "${noFunctionBodies_output}" "$smallestFilesInput"

  echo Splitting files into individual lines
  cd "$smallestFilesInput"
  splitsPath="${subfilePath}.split"
  "${scriptPath}/impl/split.sh" "$smallestFilesSandbox" "$splitsPath"
  rm "$smallestFilesSandbox" -rf

  # Make a dir holding the destination file state
  if [ "$limitToPath" != "" ]; then
    # The user said they were only interested in trying to delete files under a certain path
    # So, our target state is the original state minus that path (and its descendants)
    mkdir -p "$smallestFilesGoal"
    cp -rT "$smallestFilesInput/$smallestFilesWork" "$smallestFilesGoal/$smallestFilesWork"
    cd "$smallestFilesGoal/$smallestFilesWork"
    rm "$limitToPath" -rf
    cd -
  else
    # The user didn't request to limit the search to a specific path, so we try to delete as many
    # files as possible
    mkdir -p "$smallestFilesGoal"
  fi

  echo Running diff-filterer.py again to identify the minimal set of lines needed to reproduce the error
  if "$supportRoot/development/file-utils/diff-filterer.py" $filtererOptions --work-path "$(cd $supportRoot/../.. && pwd)" "$smallestFilesInput" "$smallestFilesGoal" "${scriptPath}/impl/join.sh ${splitsPath} ${smallestFilesSandbox} && cd ${smallestFilesWork} && $testCommand"; then
    echo diff-filterer completed successfully
  else
    failed
  fi

  echo Re-joining the files
  smallestFilesOutput="$tempDir/smallestFilesOutput"
  rm -rf "$smallestFilesOutput"
  cp -rT "$(cd $supportRoot/../../bestResults && pwd)" "${smallestFilesOutput}"
  cd "${smallestFilesOutput}"
  "${scriptPath}/impl/join.sh" "${splitsPath}" "${smallestFilesSandbox}"

  echo "Done. See simplest discovered reproduction test case at ${smallestFilesOutput}"
fi
notify succeeded
