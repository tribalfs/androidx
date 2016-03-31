# Copyright (C) 2015 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

# Build the resources using the latest applicable SDK version.
# We do this here because the final static library must be compiled with an older
# SDK version than the resources.  The resources library and the R class that it
# contains will not be linked into the final static library.
include $(CLEAR_VARS)
LOCAL_MODULE := android-support-v17-preference-leanback-res
LOCAL_SDK_VERSION := $(SUPPORT_CURRENT_SDK_VERSION)
LOCAL_SRC_FILES := $(call all-java-files-under, dummy)
LOCAL_RESOURCE_DIR := \
        frameworks/support/v7/appcompat/res \
        frameworks/support/v7/recyclerview/res \
        frameworks/support/v7/preference/res \
        frameworks/support/v14/preference/res \
        frameworks/support/v17/leanback/res \
        $(LOCAL_PATH)/res
LOCAL_AAPT_FLAGS := \
        --auto-add-overlay
LOCAL_JAR_EXCLUDE_FILES := none
LOCAL_JAVA_LANGUAGE_VERSION := 1.7
include $(BUILD_STATIC_JAVA_LIBRARY)

support_module_src_files := $(LOCAL_SRC_FILES)

# -----------------------------------------------------------------------

#  A helper sub-library that makes direct use of API 21.
include $(CLEAR_VARS)
LOCAL_MODULE := android-support-v17-preference-leanback-api21
LOCAL_SDK_VERSION := 21
LOCAL_SRC_FILES := $(call all-java-files-under, api21)
LOCAL_JAVA_LIBRARIES := android-support-v17-preference-leanback-res \
        android-support-v17-leanback
LOCAL_JAVA_LANGUAGE_VERSION := 1.7
include $(BUILD_STATIC_JAVA_LIBRARY)

support_module_src_files += $(LOCAL_SRC_FILES)

# Here is the final static library that apps can link against.
# The R class is automatically excluded from the generated library.
# Applications that use this library must specify LOCAL_RESOURCE_DIR
# in their makefiles to include the resources in their package.
include $(CLEAR_VARS)
LOCAL_MODULE := android-support-v17-preference-leanback
LOCAL_SDK_VERSION := 17
LOCAL_SRC_FILES := $(call all-java-files-under,src)
LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-v17-preference-leanback-api21
LOCAL_JAVA_LIBRARIES := \
        android-support-v4 \
        android-support-v7-appcompat \
        android-support-v7-recyclerview \
        android-support-v7-preference \
        android-support-v14-preference \
        android-support-v17-leanback \
        android-support-annotations \
        android-support-v17-preference-leanback-res
LOCAL_JAVA_LANGUAGE_VERSION := 1.7
include $(BUILD_STATIC_JAVA_LIBRARY)

support_module_src_files += $(LOCAL_SRC_FILES)

# API Check
# ---------------------------------------------
support_module := $(LOCAL_MODULE)
support_module_api_dir := $(LOCAL_PATH)/api
support_module_java_libraries := $(LOCAL_JAVA_LIBRARIES)
support_module_java_packages := android.support.v17.preference
include $(SUPPORT_API_CHECK)
