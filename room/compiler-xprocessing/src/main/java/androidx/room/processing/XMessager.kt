/*
 * Copyright (C) 2020 The Android Open Source Project
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

package androidx.room.processing

import javax.tools.Diagnostic

/**
 * Logging interface for the processor
 */
interface XMessager {
    /**
     * Prints the given [msg] to the logs while also associating it with the given [element].
     *
     * @param kind Kind of the message
     * @param msg The actual message to report to the compiler
     * @param element The element with whom the message should be associated with
     */
    fun printMessage(kind: Diagnostic.Kind, msg: String, element: XElement? = null)
}
