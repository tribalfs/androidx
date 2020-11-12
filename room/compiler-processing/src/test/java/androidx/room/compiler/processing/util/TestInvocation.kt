/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.room.compiler.processing.util

import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.javac.JavacProcessingEnv
import androidx.room.compiler.processing.ksp.KspProcessingEnv
import com.google.devtools.ksp.processing.Resolver
import javax.lang.model.util.Elements

class TestInvocation(
    val processingEnv: XProcessingEnv
) {
    val isKsp = processingEnv is KspProcessingEnv

    val kspResolver: Resolver
        get() = (processingEnv as KspProcessingEnv).resolver

    val javaElementUtils: Elements
        get() = (processingEnv as JavacProcessingEnv).elementUtils
}
