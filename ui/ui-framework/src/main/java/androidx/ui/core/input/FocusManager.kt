/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.core.input

/**
 * Manages the input focus
 *
 * TODO(nona): make this public?
 */
internal open class FocusManager {

    /**
     * An interface for focusable object
     */
    interface FocusNode {
        /** Called when this component gained the focus */
        fun onFocus()

        /** Called when this component is about to lose the focus */
        fun onBlur()
    }

    private var focusedClient: FocusNode? = null

    /**
     * Request the input focus
     */
    open fun requestFocus(client: FocusNode) {
        val currentFocus = focusedClient
        if (currentFocus == client) {
            return // focus in to the same component. Do nothing.
        }

        if (currentFocus != null) {
            focusOut(currentFocus)
        }

        focusedClient = client
        focusIn(client)
    }

    private fun focusIn(client: FocusNode) {
        client.onFocus()
    }

    private fun focusOut(client: FocusNode) {
        client.onBlur()
    }
}