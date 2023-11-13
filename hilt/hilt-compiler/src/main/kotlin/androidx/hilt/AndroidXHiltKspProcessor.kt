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

package androidx.hilt

import androidx.hilt.work.WorkerStep
import androidx.room.compiler.processing.ksp.KspBasicAnnotationProcessor
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class AndroidXHiltKspProcessor(
    environment: SymbolProcessorEnvironment
) : KspBasicAnnotationProcessor(
    symbolProcessorEnvironment = environment,
    config = WorkerStep.ENV_CONFIG
) {
    override fun processingSteps() = listOf(WorkerStep())

    class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return AndroidXHiltKspProcessor(environment)
        }
    }
}
