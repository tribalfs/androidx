/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.build.LibraryGroups
import androidx.build.LibraryVersions
import androidx.build.dependencies.ESPRESSO_CORE_TMP
import androidx.build.dependencies.ESPRESSO_EXCLUDE
import androidx.build.dependencies.TEST_RUNNER_TMP

plugins {
    id("SupportAndroidLibraryPlugin")
}

dependencies {
    api(project(":fragment"))
    api(project(":recyclerview"))

    androidTestImplementation(TEST_RUNNER_TMP, ESPRESSO_EXCLUDE)
    androidTestImplementation(ESPRESSO_CORE_TMP, ESPRESSO_EXCLUDE)
    androidTestImplementation(project(":internal-testutils")) {
        exclude(group = "androidx.viewpager2", module = "viewpager2")
    }
}

supportLibrary {
    name = "AndroidX Widget ViewPager2"
    publish = false
    mavenVersion = LibraryVersions.SUPPORT_LIBRARY
    mavenGroup = LibraryGroups.VIEWPAGER2
    inceptionYear = "2017"
    description = "AndroidX Widget ViewPager2"
}
