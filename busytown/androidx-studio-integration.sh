set -e
SCRIPT_PATH="$(cd $(dirname $0) && pwd)"

# Exclude lintDebug task, as lint tests are covered by the
# androidx-studio-integration-lint.sh script
$SCRIPT_PATH/impl/build-studio-and-androidx.sh -Pandroidx.allWarningsAsErrors listTaskOutputs bOS -Pandroidx.verifyUpToDate -x verifyDependencyVersions -x lintDebug --stacktrace
