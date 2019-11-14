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

package androidx.ui.core.selection

import androidx.compose.Ambient

/**
 *  An interface allowing a Text composable to "register" and "unregister" itself with the class
 *  implementing the interface.
 */
interface SelectionRegistrar {
    fun subscribe(handler: TextSelectionHandler): TextSelectionHandler

    fun unsubscribe(key: TextSelectionHandler)
}

/**
 * Ambient of SelectionRegistrar. Composables that implement selection logic can use this ambient
 * to get a [SelectionRegistrar] in order to subscribe and unsubscribe to [SelectionRegistrar].
 */
val SelectionRegistrarAmbient = Ambient.of<SelectionRegistrar>()
