#!/bin/bash
set -e

cd "$(dirname $0)"

SNAPSHOT=true impl/build.sh --no-daemon createArchive --offline "$@"
