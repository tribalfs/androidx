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

# Needs to keep the classes implementing the vendor library interfaces in the extensions module.
# Otherwise, it will cause AbstractMethodError if proguard is enabled.
-keep class androidx.camera.extensions.ExtensionsManager$** {*;}
-keep class androidx.camera.extensions.internal.sessionprocessor.AdvancedSessionProcessor$** {*;}
-keep class androidx.camera.extensions.internal.sessionprocessor.StillCaptureProcessor** {*;}
-keep class androidx.camera.extensions.internal.sessionprocessor.PreviewProcessor** {*;}
