/*
 * Copyright 2024 The Android Open Source Project
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

@file:JvmName("LocalLifecycleOwnerKt")

package androidx.savedstate.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.jvm.JvmName

public actual val LocalSavedStateRegistryOwner:
    ProvidableCompositionLocal<SavedStateRegistryOwner> =
    staticCompositionLocalOf {
        error("CompositionLocal LocalSavedStateRegistryOwner not present")
    }
