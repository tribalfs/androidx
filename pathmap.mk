#
# Copyright (C) 2016 The Android Open Source Project
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
#

#
# A list of all source roots under frameworks/support.
#
FRAMEWORKS_SUPPORT_SUBDIRS := \
    annotations \
    compat \
    media-compat \
    fragment \
    core-ui \
    core-utils \
    v7/gridlayout \
    v7/cardview \
    v7/mediarouter \
    v7/palette \
    v13 \
    v17/leanback \
    design \
    percent \
    recommendation \
    transition \
    v7/preference \
    v14/preference \
    v17/preference-leanback \
    customtabs \
    exifinterface \
    dynamic-animation

#
# A version of FRAMEWORKS_SUPPORT_SUBDIRS that is expanded to full paths from
# the root of the tree.
#
FRAMEWORKS_SUPPORT_JAVA_SRC_DIRS := \
    $(addprefix frameworks/support/,$(FRAMEWORKS_SUPPORT_SUBDIRS)) \
    frameworks/support/graphics/drawable/animated \
    frameworks/support/graphics/drawable/static \
    frameworks/support/v7/appcompat/src \
    frameworks/support/v7/recyclerview/src

#
# A list of support library modules.
#
FRAMEWORKS_SUPPORT_JAVA_LIBRARIES := \
    $(foreach dir,$(FRAMEWORKS_SUPPORT_SUBDIRS),android-support-$(subst /,-,$(dir))) \
    android-support-v4 \
    android-support-vectordrawable \
    android-support-animatedvectordrawable \
    android-support-v7-appcompat \
    android-support-v7-recyclerview
