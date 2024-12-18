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

package androidx.wear.compose.material3.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.test.filters.LargeTest
import androidx.wear.compose.material3.macrobenchmark.common.AndroidDialogBenchmark
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** This benchmark tests androidx.app.Dialog implementation for performance issues */
@LargeTest
@RunWith(Parameterized::class)
class AndroidDialogBenchmarkTest(compilationMode: CompilationMode) :
    BenchmarkTestBase(
        compilationMode = compilationMode,
        macrobenchmarkScreen = AndroidDialogBenchmark,
        actionSuffix = "ANDROID_DIALOG_ACTIVITY"
    )
