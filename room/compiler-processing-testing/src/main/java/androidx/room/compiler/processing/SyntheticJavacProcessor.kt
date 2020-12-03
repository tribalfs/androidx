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

package androidx.room.compiler.processing

import androidx.room.compiler.processing.util.RecordingXMessager
import androidx.room.compiler.processing.util.XTestInvocation
import javax.lang.model.SourceVersion

class SyntheticJavacProcessor(
    val handler: (XTestInvocation) -> Unit,
) : JavacTestProcessor(), SyntheticProcessor {
    override val invocationInstances = mutableListOf<XTestInvocation>()
    private var result: Result<Unit>? = null
    override val messageWatcher = RecordingXMessager()

    override fun doProcess(annotations: Set<XTypeElement>, roundEnv: XRoundEnv): Boolean {
        val xEnv = XProcessingEnv.create(processingEnv)
        xEnv.messager.addMessageWatcher(messageWatcher)
        result = kotlin.runCatching {
            handler(
                XTestInvocation(
                    processingEnv = xEnv
                ).also {
                    invocationInstances.add(it)
                }
            )
        }
        return true
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    override fun getSupportedAnnotationTypes() = setOf("*")

    override fun throwIfFailed() {
        val result = checkNotNull(result) {
            "did not compile"
        }
        if (result.isFailure) {
            // throw AssertionError instead of re-throwing the same error to keep the stack trace
            // of the failure in the exception's cause field. We cannot throw it as is since stack
            // traces do not match.
            throw AssertionError(result.exceptionOrNull()!!)
        }
    }
}