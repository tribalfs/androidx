#!/bin/bash
set -e

cd $(dirname $0)

impl/build.sh --no-daemon assembleDebug assembleAndroidTest -PuseMaxDepVersions --offline
