#!/bin/bash

echo "/** Begin preview constants"
echo " * autogenerated by previewconstants.sh */"
echo "package android.support.previewsdk;"
echo "class PreviewConstants {"
echo "    public static final int PREVIEW_SDK_VERSION = $PLATFORM_PREVIEW_SDK_VERSION;"
echo "}"
