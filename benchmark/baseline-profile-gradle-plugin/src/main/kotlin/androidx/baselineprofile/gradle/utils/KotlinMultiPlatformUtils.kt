/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.baselineprofile.gradle.utils

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Utility methods for kotlin multiplatform projects. Note that this class references symbols
 * existing only if the KMP plugin has been applied.
 */
internal object KotlinMultiPlatformUtils {

    fun androidTargetName(project: Project) =
        project.extensions
            .findByType(KotlinMultiplatformExtension::class.java)
            ?.targets
            ?.firstOrNull { it.platformType == KotlinPlatformType.androidJvm }
            ?.name ?: ""
}
