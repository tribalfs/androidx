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
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.materialize
import androidx.compose.runtime.emit
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.platform.ContextAmbient

/**
 * Composes an Android [View] obtained from [viewBlock]. The [viewBlock] block will be called
 * exactly once to obtain the [View] to be composed, and it is also guaranteed to be invoked on
 * the UI thread. Therefore, in addition to creating the [viewBlock], the block can also be used
 * to perform one-off initializations and [View] constant properties' setting.
 * The [update] block can be run multiple times (on the UI thread as well) due to recomposition,
 * and it is the right place to set [View] properties depending on state. When state changes,
 * the block will be reexecuted to set the new properties. Note the block will also be ran once
 * right after the [viewBlock] block completes.
 *
 * @sample androidx.compose.ui.samples.AndroidViewSample
 *
 * @param viewBlock The block creating the [View] to be composed.
 * @param modifier The modifier to be applied to the layout.
 * @param update The callback to be invoked after the layout is inflated.
 */
@Composable
fun <T : View> AndroidView(
    viewBlock: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = NoOpUpdate
) {
    val context = ContextAmbient.current
    val materialized = currentComposer.materialize(modifier)
    emit<ViewBlockHolder<T>, UiApplier>(
        ctor = { ViewBlockHolder(context) },
        update = {
            set(Unit) { this.viewBlock = viewBlock }
            set(materialized) { this.modifier = it }
            set(update) { this.updateBlock = update }
        }
    )
}

/**
 * Composes an Android [View] given a layout resource [resId]. The method handles the inflation
 * of the [View] and will call the [postInflationCallback] after this happens. Note that the
 * callback will always be invoked on the main thread.
 *
 * @param resId The id of the layout resource to be inflated.
 * @param modifier The modifier to be applied to the layout.
 * @param postInflationCallback The callback to be invoked after the layout is inflated.
 */
@Composable
@Deprecated(
    "This AndroidView overload will be removed. Please use the non-deprecated one.",
    ReplaceWith(
        "AndroidView({ context ->\n" +
                "val view = LayoutInflater.from(context)" +
                ".inflate(resId, FrameLayout(context), false\n" +
                "view.let(postInflationCallback)\n" +
                "view\n" +
                "})",
        "android.view.LayoutInflater"
    )
)
fun AndroidView(
    @LayoutRes resId: Int,
    modifier: Modifier = Modifier,
    postInflationCallback: (View) -> Unit = { _ -> }
) = AndroidView(
    { context ->
        LayoutInflater.from(context)
            // The fake FrameLayout is needed for layout params creation.
            .inflate(resId, FrameLayout(context), false)
            .apply(postInflationCallback)
    },
    modifier
)

/**
 * Composes an Android [View].
 *
 * @param view The [View] to compose.
 * @param modifier The [Modifier] to be applied to the [view].
 */
@Composable
@Deprecated(
    "This AndroidView overload will be removed. Please use the non-deprecated one.",
    ReplaceWith(
        "AndroidView({ view })"
    )
)
fun AndroidView(view: View, modifier: Modifier = Modifier) = AndroidView({ view }, modifier)

/**
 * An empty update block used by [AndroidView].
 */
val NoOpUpdate: View.() -> Unit = {}

@OptIn(InternalInteropApi::class)
internal class ViewBlockHolder<T : View>(
    context: Context
) : AndroidViewHolder(context) {
    private var typedView: T? = null

    var viewBlock: ((Context) -> T)? = null
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
