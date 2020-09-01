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
package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.FrameManager
import androidx.compose.runtime.Providers
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.compositionFor
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode

@OptIn(ExperimentalComposeApi::class)
fun DesktopOwner.setContent(content: @Composable () -> Unit): Composition {
    FrameManager.ensureStarted()

    val composition = compositionFor(root, DesktopUiApplier(root), Recomposer.current(), null)
    composition.setContent {
        ProvideDesktopAmbients(this) {
            DesktopSelectionContainer(content)
        }
    }

    return composition
}

@Composable
private fun ProvideDesktopAmbients(owner: DesktopOwner, content: @Composable () -> Unit) {
    Providers(
        DesktopOwnersAmbient provides owner.container
    ) {
        ProvideCommonAmbients(
            owner = owner,
            animationClock = owner.container.animationClock,
            uriHandler = DesktopUriHandler(),
            content = content
        )
    }
}

@OptIn(ExperimentalComposeApi::class, ExperimentalLayoutNodeApi::class)
internal actual fun actualSubcomposeInto(
    container: LayoutNode,
    recomposer: Recomposer,
    parent: CompositionReference?,
    composable: @Composable () -> Unit
): Composition = compositionFor(
    container,
    DesktopUiApplier(container),
    recomposer,
    parent
).apply {
    setContent(composable)
}