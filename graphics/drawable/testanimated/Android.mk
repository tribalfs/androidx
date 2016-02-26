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

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SDK_VERSION := current

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR = \
        $(LOCAL_PATH)/res \
        frameworks/support/graphics/drawable/res \

LOCAL_PACKAGE_NAME := AndroidAnimatedVectorDrawableTests

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v11-animatedvectordrawable android-support-v4

LOCAL_AAPT_FLAGS += --auto-add-overlay \
        --extra-packages android.support.graphics.drawable \
        --no-version-vectors

LOCAL_JAVA_LANGUAGE_VERSION := 1.7
include $(BUILD_PACKAGE)
