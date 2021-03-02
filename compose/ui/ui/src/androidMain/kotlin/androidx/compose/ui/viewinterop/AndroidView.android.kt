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

package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.materialize
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Ref
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Composes an Android [View] obtained from [factory]. The [factory] block will be called
 * exactly once to obtain the [View] to be composed, and it is also guaranteed to be invoked on
 * the UI thread. Therefore, in addition to creating the [factory], the block can also be used
 * to perform one-off initializations and [View] constant properties' setting.
 * The [update] block can be run multiple times (on the UI thread as well) due to recomposition,
 * and it is the right place to set [View] properties depending on state. When state changes,
 * the block will be reexecuted to set the new properties. Note the block will also be ran once
 * right after the [factory] block completes.
 *
 * [AndroidView] is commonly needed for using Views that are infeasible to be reimplemented in
 * Compose and there is no corresponding Compose API. Common examples for the moment are
 * WebView, SurfaceView, AdView, etc.
 *
 * [AndroidView] will clip its content to the layout bounds, as being clipped is a common
 * assumption made by [View]s - keeping clipping disabled might lead to unexpected drawing behavior.
 * Note this deviates from Compose's practice of keeping clipping opt-in, disabled by default.
 *
 * @sample androidx.compose.ui.samples.AndroidViewSample
 *
 * @param factory The block creating the [View] to be composed.
 * @param modifier The modifier to be applied to the layout.
 * @param update The callback to be invoked after the layout is inflated.
 */
@Composable
fun <T : View> AndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = NoOpUpdate
) {
    val context = LocalContext.current
    val materialized = currentComposer.materialize(modifier)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val parentReference = rememberCompositionContext()
    val viewBlockHolderRef = remember { Ref<ViewFactoryHolder<T>>() }
    ComposeNode<LayoutNode, UiApplier>(
        factory = {
            val viewFactoryHolder = ViewFactoryHolder<T>(context, parentReference)
            viewFactoryHolder.factory = factory
            viewBlockHolderRef.value = viewFactoryHolder
            viewFactoryHolder.layoutNode
        },
        update = {
            set(materialized) { viewBlockHolderRef.value!!.modifier = it }
            set(density) { viewBlockHolderRef.value!!.density = it }
            set(update) { viewBlockHolderRef.value!!.updateBlock = it }
            set(layoutDirection) {
                viewBlockHolderRef.value!!.layoutDirection = when (it) {
                    LayoutDirection.Ltr -> android.util.LayoutDirection.LTR
                    LayoutDirection.Rtl -> android.util.LayoutDirection.RTL
                }
            }
        }
    )
}

/**
 * An empty update block used by [AndroidView].
 */
val NoOpUpdate: View.() -> Unit = {}

internal class ViewFactoryHolder<T : View>(
    context: Context,
    parentContext: CompositionContext? = null
) : AndroidViewHolder(context, parentContext) {

    private var typedView: T? = null

    var factory: ((Context) -> T)? = null
        set(value) {
            field = value
            if (value != null) {
                typedView = value(context)
                view = typedView
            }
        }

    var updateBlock: (T) -> Unit = NoOpUpdate
        set(value) {
            field = value
            update = { typedView?.apply(updateBlock) }
        }
}
