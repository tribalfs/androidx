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

package androidx.ui.core

import androidx.compose.Composable
import androidx.ui.semantics.Semantics
import androidx.ui.semantics.SemanticsPropertyReceiver
import androidx.ui.semantics.testTag

/**
 * Applies a tag to allow this element to be found in tests.
 *
 * This is a convenience method for a [Semantics] that sets [SemanticsPropertyReceiver.testTag].
 *
 * Note that this does not, by itself, create a semantics boundary. If the element you are
 * setting this on does not already create a boundary, you will need to create one.
 */
@Composable
fun TestTag(tag: String, children: @Composable() () -> Unit) {
    Semantics(properties = { testTag = tag }, children = children)
}